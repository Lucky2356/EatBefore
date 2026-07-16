package com.eatbefore.feature.ocr.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * CameraX preview with a bound [ImageCapture]. The capture use case is hoisted via
 * [onCaptureReady] so the hosting screen's shutter button can trigger [takePhoto].
 */
@Composable
fun OcrCameraView(
    onCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(Unit) {
        val provider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            onCaptureReady(imageCapture)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Captures a JPEG into the app cache and returns its [Uri] via [onSaved]; [onError] on
 * failure. The file lives in cacheDir so it is transient (OCR images aren't kept).
 */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (Uri) -> Unit,
    onError: () -> Unit,
) {
    val file = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(output.savedUri ?: Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                onError()
            }
        },
    )
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        { continuation.resume(future.get()) },
        ContextCompat.getMainExecutor(this),
    )
}
