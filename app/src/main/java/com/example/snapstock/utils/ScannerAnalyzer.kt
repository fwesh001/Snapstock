package com.example.snapstock.utils

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class ScannerAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val onTextDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var isProcessing = false

    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessing = true
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // Try Barcode First
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val rawValue = barcodes.first().rawValue
                        if (rawValue != null && rawValue.isNotBlank()) {
                            onBarcodeDetected(rawValue)
                        }
                    } else {
                        // Fallback to text recognition if no barcode
                        textRecognizer.process(image)
                            .addOnSuccessListener { text ->
                                if (text.text.isNotBlank()) {
                                    onTextDetected(text.text)
                                }
                            }
                            .addOnCompleteListener { }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                    isProcessing = false
                }
        } else {
            imageProxy.close()
        }
    }
}
