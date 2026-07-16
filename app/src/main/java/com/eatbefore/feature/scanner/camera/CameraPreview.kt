package com.eatbefore.feature.scanner.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * CameraX preview bound to an ML Kit barcode analyzer. Fully on-device. Torch is driven by
 * [torchEnabled]; detections are reported through [onCode]. The analyzer executor and the
 * ML Kit client are released when the composable leaves composition.
 */
@Composable
fun CameraPreview(
    torchEnabled: Boolean,
    onCode: (ScannedCode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scanner: BarcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_CODE_128,
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    // Camera control is captured after binding so the torch effect can toggle it.
    val cameraHolder = remember { arrayOfNulls<androidx.camera.core.Camera>(1) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, BarcodeAnalyzer(scanner, onCode)) }

        // Binding can fail on devices without a suitable camera; fail gracefully rather
        // than crashing — the user can still enter a code manually.
        runCatching {
            cameraProvider.unbindAll()
            cameraHolder[0] = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            cameraHolder[0]?.cameraControl?.enableTorch(torchEnabled)
        }
    }

    LaunchedEffect(torchEnabled) {
        cameraHolder[0]?.cameraControl?.enableTorch(torchEnabled)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        { continuation.resume(future.get()) },
        ContextCompat.getMainExecutor(this),
    )
}
