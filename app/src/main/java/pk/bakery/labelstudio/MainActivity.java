package pk.bakery.labelstudio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 41;
    private static final int REQ_PERMS = 42;

    WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraPhotoUri;
    final ExecutorService io = Executors.newSingleThreadExecutor();
    Printing printing;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        printing = new Printing(this);

        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                openImagePicker();
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    /* ---------- photo picker (gallery + camera) ---------- */
    private void openImagePicker() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        Intent chooser = Intent.createChooser(pick, "Photo");
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                File f = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");
                cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
            } catch (Exception e) { cameraPhotoUri = null; }
        } else {
            cameraPhotoUri = null;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
        }
        try { startActivityForResult(chooser, REQ_FILE); }
        catch (Exception e) { if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; } }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
                else if (cameraPhotoUri != null) result = new Uri[]{cameraPhotoUri};
            }
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        IntentResult r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (r != null) {
            if (r.getContents() != null) js("window.onNativeScan && window.onNativeScan(" + JSONObject.quote(r.getContents()) + ")");
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.appBack && window.appBack()) ? 'y' : 'n'", v -> {
            if (v == null || !v.contains("y")) MainActivity.super.onBackPressed();
        });
    }

    void js(final String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    void done(String cb, boolean ok, String msg) {
        js("window.onNativeResult && window.onNativeResult(" + JSONObject.quote(cb) + "," + ok + "," + JSONObject.quote(msg == null ? "" : msg) + ")");
    }

    void toast(final String t) { runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_SHORT).show()); }

    /* ---------- JS bridge ---------- */
    class Bridge {
        @JavascriptInterface public String info() { return "{\"platform\":\"android\",\"sdk\":" + Build.VERSION.SDK_INT + "}"; }

        @JavascriptInterface public void toast(String t) { MainActivity.this.toast(t); }

        @JavascriptInterface public void scan() {
            runOnUiThread(() -> {
                IntentIntegrator ii = new IntentIntegrator(MainActivity.this);
                ii.setPrompt("Barcode ko frame ke andar rakhein");
                ii.setBeepEnabled(true);
                ii.setOrientationLocked(false);
                ii.initiateScan();
            });
        }

        @JavascriptInterface public void saveFile(String name, String b64, String mime, String cb) {
            io.execute(() -> {
                try {
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    Uri shareUri;
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BakeryLabels");
                        Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (u == null) throw new Exception("MediaStore insert failed");
                        try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(bytes); }
                        shareUri = u;
                    } else {
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        File f = new File(dir, name);
                        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(bytes); }
                        shareUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    }
                    final Uri su = shareUri;
                    runOnUiThread(() -> {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime);
                        send.putExtra(Intent.EXTRA_STREAM, su);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivity(Intent.createChooser(send, name)); } catch (Exception ignored) { }
                    });
                    done(cb, true, name);
                } catch (Exception e) {
                    done(cb, false, e.getMessage());
                }
            });
        }

        @JavascriptInterface public void printPdf(String jobName, String b64, String cb) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                runOnUiThread(() -> {
                    try { printing.systemPrintPdf(jobName, bytes); done(cb, true, "ok"); }
                    catch (Exception e) { done(cb, false, e.getMessage()); }
                });
            } catch (Exception e) { done(cb, false, e.getMessage()); }
        }

        @JavascriptInterface public String pairedPrinters() { return printing.pairedJson(); }

        @JavascriptInterface public void btPrint(String address, String b64, String cb) {
            io.execute(() -> {
                try { printing.bluetooth(address, Base64.decode(b64, Base64.DEFAULT)); done(cb, true, "ok"); }
                catch (Exception e) { done(cb, false, "Bluetooth: " + e.getMessage()); }
            });
        }

        @JavascriptInterface public void tcpPrint(String host, String port, String b64, String cb) {
            io.execute(() -> {
                try { printing.tcp(host, Integer.parseInt(port), Base64.decode(b64, Base64.DEFAULT)); done(cb, true, "ok"); }
                catch (Exception e) { done(cb, false, "WiFi: " + e.getMessage()); }
            });
        }

        @JavascriptInterface public void usbPrint(String b64, String cb) {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            runOnUiThread(() -> printing.usb(bytes, (ok, msg) -> done(cb, ok, msg)));
        }
    }
}
