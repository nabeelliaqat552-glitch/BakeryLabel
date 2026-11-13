package pk.bakery.labelstudio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;

class Printing {
    interface Done { void result(boolean ok, String msg); }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String USB_ACTION = "pk.bakery.labelstudio.USB_PERMISSION";
    private final MainActivity act;

    Printing(MainActivity a) { act = a; }

    /* ---------- Android system print (WiFi / USB office printers via Mopria etc.) ---------- */
    void systemPrintPdf(String jobName, final byte[] pdf) {
        PrintManager pm = (PrintManager) act.getSystemService(Context.PRINT_SERVICE);
        pm.print(jobName, new PrintDocumentAdapter() {
            @Override
            public void onLayout(PrintAttributes oldA, PrintAttributes newA, CancellationSignal cs, LayoutResultCallback cb, Bundle extras) {
                if (cs.isCanceled()) { cb.onLayoutCancelled(); return; }
                cb.onLayoutFinished(new PrintDocumentInfo.Builder("labels.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true);
            }
            @Override
            public void onWrite(PageRange[] pages, ParcelFileDescriptor dest, CancellationSignal cs, WriteResultCallback cb) {
                try (FileOutputStream out = new FileOutputStream(dest.getFileDescriptor())) {
                    out.write(pdf);
                    cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) { cb.onWriteFailed(e.getMessage()); }
            }
        }, null);
    }

    /* ---------- Bluetooth classic (SPP) ---------- */
    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    private boolean hasBtPerm() {
        if (Build.VERSION.SDK_INT < 31) return true;
        if (act.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) return true;
        act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 43));
        return false;
    }

    @SuppressLint("MissingPermission")
    String pairedJson() {
        try {
            if (!hasBtPerm()) return "{\"error\":\"perm\"}";
            BluetoothAdapter a = adapter();
            if (a == null || !a.isEnabled()) return "{\"error\":\"off\"}";
            JSONArray arr = new JSONArray();
            Set<BluetoothDevice> set = a.getBondedDevices();
            for (BluetoothDevice d : set) {
                JSONObject o = new JSONObject();
                o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                o.put("address", d.getAddress());
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) { return "{\"error\":\"fail\"}"; }
    }

    @SuppressLint("MissingPermission")
    void bluetooth(String address, byte[] data) throws Exception {
        if (!hasBtPerm()) throw new Exception("permission needed — allow and try again");
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) throw new Exception("Bluetooth is off");
        BluetoothDevice dev = a.getRemoteDevice(address);
        try { a.cancelDiscovery(); } catch (Exception ignored) { }
        BluetoothSocket s = null;
        try {
            s = dev.createRfcommSocketToServiceRecord(SPP);
            s.connect();
        } catch (Exception first) {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
            Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
            s = (BluetoothSocket) m.invoke(dev, 1);
            s.connect();
        }
        try {
            OutputStream os = s.getOutputStream();
            for (int i = 0; i < data.length; i += 1024) {
                os.write(data, i, Math.min(1024, data.length - i));
                os.flush();
                Thread.sleep(15);
            }
            Thread.sleep(600);
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    /* ---------- WiFi raw (port 9100) ---------- */
    void tcp(String host, int port, byte[] data) throws Exception {
        try (Socket so = new Socket()) {
            so.connect(new InetSocketAddress(host, port), 6000);
            OutputStream os = so.getOutputStream();
            os.write(data);
            os.flush();
            Thread.sleep(300);
        }
    }

    /* ---------- USB OTG ---------- */
    void usb(final byte[] data, final Done done) {
        final UsbManager um = (UsbManager) act.getSystemService(Context.USB_SERVICE);
        UsbDevice found = null;
        for (UsbDevice d : um.getDeviceList().values()) {
            if (findOut(d) != null) { found = d; if (isPrinter(d)) break; }
        }
        if (found == null) { done.result(false, "USB: no printer found — connect with OTG cable"); return; }
        final UsbDevice dev = found;
        if (um.hasPermission(dev)) { usbSend(um, dev, data, done); return; }
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(act, 0, new Intent(USB_ACTION).setPackage(act.getPackageName()), flags);
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                try { act.unregisterReceiver(this); } catch (Exception ignored) { }
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) usbSend(um, dev, data, done);
                else done.result(false, "USB: permission denied");
            }
        };
        if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(r, new IntentFilter(USB_ACTION), Context.RECEIVER_NOT_EXPORTED);
        else act.registerReceiver(r, new IntentFilter(USB_ACTION));
        um.requestPermission(dev, pi);
    }

    private boolean isPrinter(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) if (d.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) return true;
        return false;
    }

    private Object[] findOut(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface itf = d.getInterface(i);
            for (int e = 0; e < itf.getEndpointCount(); e++) {
                UsbEndpoint ep = itf.getEndpoint(e);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.getDirection() == UsbConstants.USB_DIR_OUT) return new Object[]{itf, ep};
            }
        }
        return null;
    }

    private void usbSend(UsbManager um, UsbDevice dev, byte[] data, Done done) {
        act.io.execute(() -> {
            Object[] f = findOut(dev);
            UsbDeviceConnection c = um.openDevice(dev);
            if (c == null || f == null) { done.result(false, "USB: cannot open device"); return; }
            UsbInterface itf = (UsbInterface) f[0];
            UsbEndpoint ep = (UsbEndpoint) f[1];
            try {
                c.claimInterface(itf, true);
                int chunk = 16384;
                for (int i = 0; i < data.length; i += chunk) {
                    int n = Math.min(chunk, data.length - i);
                    byte[] part = new byte[n];
                    System.arraycopy(data, i, part, 0, n);
                    int w = c.bulkTransfer(ep, part, n, 5000);
                    if (w < 0) throw new Exception("write failed");
                }
                done.result(true, "ok");
            } catch (Exception e) {
                done.result(false, "USB: " + e.getMessage());
            } finally {
                try { c.releaseInterface(itf); } catch (Exception ignored) { }
                c.close();
            }
        });
    }
}
