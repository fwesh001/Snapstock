package com.example.snapstock.utils

import kotlin.math.sqrt

data class DualEngineSignature(
    val embedding: FloatArray? = null,
    val ocrText: String = "",
    val ocrTokens: Set<String> = emptySet(),
    val signatureVersion: Int = CURRENT_SIGNATURE_VERSION
)

const val CURRENT_SIGNATURE_VERSION = 2

object SignatureCodec {
    fun encodeEmbedding(embedding: FloatArray?): String? {
        if (embedding == null || embedding.isEmpty()) return null
        return embedding.joinToString(separator = ",") { value -> value.toString() }
    }

    fun decodeEmbedding(encoded: String?): FloatArray? {
        if (encoded.isNullOrBlank()) return null
        val values = encoded.split(',')
            .mapNotNull { value -> value.toFloatOrNull() }
        if (values.isEmpty()) return null
        return values.toFloatArray()
    }

    fun encodeTokens(tokens: Set<String>): String? {
        if (tokens.isEmpty()) return null
        return tokens.sorted().joinToString(separator = " ")
    }

    fun decodeTokens(encoded: String?): Set<String> {
        if (encoded.isNullOrBlank()) return emptySet()
        return encoded.split(Regex("\\s+"))
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() }
            .toSet()
    }

    fun cosineSimilarity(left: FloatArray?, right: FloatArray?): Float {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0f
        val size = minOf(left.size, right.size)
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0

        for (index in 0 until size) {
            val leftValue = left[index].toDouble()
            val rightValue = right[index].toDouble()
            dot += leftValue * rightValue
            leftNorm += leftValue * leftValue
            rightNorm += rightValue * rightValue
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) return 0f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat().coerceIn(0f, 1f)
    }

    fun tokenSimilarity(left: Set<String>, right: Set<String>): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        val intersection = left.intersect(right)
        if (intersection.isEmpty()) return 0f
        val unionSize = (left union right).size
        if (unionSize == 0) return 0f
        val jaccard = intersection.size.toFloat() / unionSize.toFloat()
        val hasNumericOverlap = intersection.any { token -> token.any { ch -> ch.isDigit() } }
        val numericBoost = if (hasNumericOverlap) 0.2f else 0f
        return (jaccard + numericBoost).coerceIn(0f, 1f)
    }
}
