package com.eatbefore.feature.scanner.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.eatbefore.domain.model.BarcodeType
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

/** A scanned code with its detected symbology. */
data class ScannedCode(val value: String, val type: BarcodeType)

/**
 * CameraX [ImageAnalysis.Analyzer] that runs the ML Kit [BarcodeScanner] on each frame and
 * reports the first non-blank code via [onDetected]. Everything is on-device; frames never
 * leave the process. The image proxy is always closed so the pipeline keeps flowing.
 */
class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onDetected: (ScannedCode) -> Unit,
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null) {
                    onDetected(
                        ScannedCode(
                            value = first.rawValue!!.trim(),
                            type = mlkitFormatToBarcodeType(first.format),
                        ),
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
