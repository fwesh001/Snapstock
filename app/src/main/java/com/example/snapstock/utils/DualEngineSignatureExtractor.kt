package com.example.snapstock.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.core.RunningMode
import org.tensorflow.lite.task.vision.embedder.ImageEmbedder

object DualEngineSignatureExtractor {
    private const val MODEL_ASSET_PATH = "models/mobilenet_v2_1.0_224.tflite"

    @Volatile
    private var embedder: ImageEmbedder? = null

    private val embedderLock = Any()

    suspend fun extractFromImagePath(context: Context, imagePath: String): DualEngineSignature? = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
        extractFromBitmap(context, bitmap)
    }

    suspend fun extractFromBitmap(context: Context, bitmap: Bitmap): DualEngineSignature = coroutineScope {
        val ocrDeferred = async(Dispatchers.Default) {
            OcrExtractor.extractTextBundle(bitmap)
        }
        val embeddingDeferred = async(Dispatchers.Default) {
            extractEmbedding(context, bitmap)
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

    private fun extractEmbedding(context: Context, bitmap: Bitmap): FloatArray? {
        val imageEmbedder = getOrCreateEmbedder(context) ?: return null
        return runCatching {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val result = imageEmbedder.embed(tensorImage)
            result.embeddingResult().embeddings().firstOrNull()?.featureVector()
        }.getOrNull()
    }

    private fun getOrCreateEmbedder(context: Context): ImageEmbedder? {
        embedder?.let { return it }
        synchronized(embedderLock) {
            embedder?.let { return it }
            val created = runCatching {
                val options = ImageEmbedder.ImageEmbedderOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setNumThreads(2)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .build()
                ImageEmbedder.createFromFileAndOptions(context, MODEL_ASSET_PATH, options)
            }.getOrNull()
            embedder = created
            return created
        }
    }
}
