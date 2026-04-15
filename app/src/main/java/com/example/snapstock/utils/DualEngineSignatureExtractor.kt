package com.example.snapstock.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object DualEngineSignatureExtractor {
    private const val COLOR_FEATURE_COUNT = 4
    private const val HASH_BUCKET_COUNT = 8
    private const val EMBEDDING_FEATURE_COUNT = COLOR_FEATURE_COUNT + (HASH_BUCKET_COUNT * 2)

    suspend fun extractFromImagePath(context: Context, imagePath: String): DualEngineSignature? = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
        extractFromBitmap(context, bitmap)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun extractFromBitmap(context: Context, bitmap: Bitmap): DualEngineSignature = coroutineScope {
        val ocrDeferred = async(Dispatchers.Default) {
            OcrExtractor.extractTextBundle(bitmap)
        }
        val embeddingDeferred = async(Dispatchers.Default) {
            buildVisualEmbedding(bitmap)
        }

        val ocrBundle = ocrDeferred.await()
        val embedding = embeddingDeferred.await()

        DualEngineSignature(
            embedding = embedding,
            ocrText = ocrBundle.fullText,
            ocrTokens = ocrBundle.tokens,
            signatureVersion = CURRENT_SIGNATURE_VERSION
        )
    }

    private fun buildVisualEmbedding(bitmap: Bitmap): FloatArray {
        val signature = ImageMatcher.buildSignature(bitmap)
        val color = signature.dominantColor
        val features = FloatArray(EMBEDDING_FEATURE_COUNT)

        features[0] = Color.red(color) / 255f
        features[1] = Color.green(color) / 255f
        features[2] = Color.blue(color) / 255f
        features[3] = ((features[0] + features[1] + features[2]) / 3f)

        fillHashFeatures(features, 4, signature.averageHash)
        fillHashFeatures(features, 12, signature.perceptualHash)

        normalize(features)
        return features
    }

    private fun fillHashFeatures(target: FloatArray, offset: Int, hash: Long) {
        if (offset >= target.size) return

        val bucketCount = minOf(HASH_BUCKET_COUNT, target.size - offset)
        for (bucket in 0 until bucketCount) {
            val chunk = ((hash ushr (bucket * 8)) and 0xFFL).toInt()
            target[offset + bucket] = Integer.bitCount(chunk) / 8f
        }
    }

    private fun normalize(values: FloatArray) {
        val magnitude = kotlin.math.sqrt(values.sumOf { value -> (value * value).toDouble() })
        if (magnitude == 0.0) return
        for (index in values.indices) {
            values[index] = (values[index] / magnitude.toFloat())
        }
    }
}
