package com.foodlabel.studio.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.ui.theme.Fls
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ScannedCode(val type: BarcodeType, val value: String)

/** Decodes camera frames with ZXing on the phone itself: no internet and no Google services needed. */
private class FrameDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.QR_CODE,
            ),
            DecodeHintType.TRY_HARDER to true,
        ))
    }

    fun decode(image: ImageProxy): ScannedCode? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val w = image.width; val h = image.height; val stride = plane.rowStride
        if (data.size < stride * (h - 1) + w) return null
        // The sensor image is usually sideways compared with the phone, and 1D barcodes only read
        // across their bars, so try the frame as it is and turned by 90 degrees.
        tryDecode(PlanarYUVLuminanceSource(data, stride, h, 0, 0, w, h, false))?.let { return it }
        val rotated = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * stride
            for (x in 0 until w) rotated[x * h + (h - 1 - y)] = data[row + x]
        }
        return tryDecode(PlanarYUVLuminanceSource(rotated, h, w, 0, 0, h, w, false))
    }

    private fun tryDecode(source: PlanarYUVLuminanceSource): ScannedCode? = try {
        val r = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        val type = when (r.barcodeFormat) {
            BarcodeFormat.EAN_13 -> BarcodeType.EAN_13
            BarcodeFormat.EAN_8 -> BarcodeType.EAN_8
            BarcodeFormat.UPC_A -> BarcodeType.UPC_A
            BarcodeFormat.UPC_E -> BarcodeType.UPC_E
            BarcodeFormat.CODE_128 -> BarcodeType.CODE_128
            BarcodeFormat.CODE_39 -> BarcodeType.CODE_39
            BarcodeFormat.QR_CODE -> BarcodeType.QR_CODE
            else -> null
        }
        if (type != null && !r.text.isNullOrBlank()) ScannedCode(type, r.text) else null
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/**
 * Full-screen camera that reads one barcode and returns it. Asks for the camera permission when opened.
 * Nothing is recorded or stored: each frame is checked for a barcode and thrown away.
 */
@Composable
fun BarcodeScannerDialog(onResult: (ScannedCode) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                DisposableEffect(Unit) {
                    val executor = Executors.newSingleThreadExecutor()
                    val done = AtomicBoolean(false)
                    val decoder = FrameDecoder()
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    val future = ProcessCameraProvider.getInstance(context)
                    var provider: ProcessCameraProvider? = null
                    future.addListener({
                        try {
                            val p = future.get()
                            provider = p
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { image ->
                                try {
                                    if (!done.get()) {
                                        val code = decoder.decode(image)
                                        if (code != null && done.compareAndSet(false, true)) mainExecutor.execute { onResult(code) }
                                    }
                                } finally {
                                    image.close()
                                }
                            }
                            p.unbindAll()
                            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) {
                            problem = "The camera could not be opened. Close other camera apps and try again."
                        }
                    }, mainExecutor)
                    onDispose {
                        runCatching { provider?.unbindAll() }
                        executor.shutdown()
                    }
                }
                // Aiming frame
                Box(Modifier.align(Alignment.Center).fillMaxWidth(0.82f).height(170.dp).border(3.dp, Fls.Gold, RoundedCornerShape(16.dp)))
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scan barcode", color = Color.White, fontFamily = Fls.Display, fontSize = 26.sp)
                Text(
                    when {
                        problem != null -> problem!!
                        granted -> "Hold the product's barcode inside the gold frame. It is added as soon as it is read."
                        asked -> "Camera permission was not given. Allow Camera for FoodLabel Studio in the phone's Settings › Apps, or type the number by hand."
                        else -> "Allow the camera to scan the barcode."
                    },
                    color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                    SecondaryButtonOnDark(if (torch) "Light off" else "Light on", Modifier.weight(1f)) {
                        torch = !torch
                        runCatching { camera?.cameraControl?.enableTorch(torch) }
                    }
                }
                SecondaryButtonOnDark("Cancel", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

@Composable
private fun SecondaryButtonOnDark(text: String, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = Fls.Card, modifier = modifier.height(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontWeight = FontWeight.Bold, color = Fls.Ink, fontSize = 16.sp) }
    }
}
