package com.example.snapstock.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrResult(
    val extractedName: String? = null,
    val extractedPrice: String? = null
)

object OcrExtractor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromImage(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val lines = fullText.split("\n")

                    // Simple heuristic: first non-empty line could be a name,
                    // and any line containing currency symbols or numbers could be price
                    var extractedName: String? = null
                    var extractedPrice: String? = null

                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            // Look for price-like patterns: $ or numbers with decimals
                            if (trimmed.contains(Regex("[$£€]|\\d+\\.\\d+|\\d+\\s*(?:USD|MXN|EUR)")) && extractedPrice == null) {
                                extractedPrice = trimmed
                            } else if (extractedName == null && trimmed.length > 2 && trimmed.length < 80) {
                                // Assume first reasonably-sized line is the name
                                extractedName = trimmed
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(OcrResult(extractedName, extractedPrice))
                    }
                }
                .addOnFailureListener { _ ->
                    if (continuation.isActive) {
                        continuation.resume(OcrResult())
                    }
                }
        } catch (_: Exception) {
            if (continuation.isActive) {
                continuation.resume(OcrResult())
            }
        }
    }
}


