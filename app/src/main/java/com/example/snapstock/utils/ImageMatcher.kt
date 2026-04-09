package com.example.snapstock.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.min

data class ImageSignature(
    val dominantColor: Int,
    val averageHash: Long
)

object ImageMatcher {

    fun buildSignature(bitmap: Bitmap): ImageSignature {
        val normalized = centerCropSquare(bitmap)
        val dominant = extractDominantColor(normalized)
        val hash = computeAverageHash(normalized)
        return ImageSignature(dominantColor = dominant, averageHash = hash)
    }

    fun buildSignature(imagePath: String): ImageSignature? {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return null
        return buildSignature(bitmap)
    }

    fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    fun parseColorHex(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
    }

    fun colorDistance(colorA: Int, colorB: Int): Int {
        val ar = android.graphics.Color.red(colorA)
        val ag = android.graphics.Color.green(colorA)
        val ab = android.graphics.Color.blue(colorA)
        val br = android.graphics.Color.red(colorB)
        val bg = android.graphics.Color.green(colorB)
        val bb = android.graphics.Color.blue(colorB)
        return abs(ar - br) + abs(ag - bg) + abs(ab - bb)
    }

    fun hammingDistance(hashA: Long, hashB: Long): Int = java.lang.Long.bitCount(hashA xor hashB)

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        return palette.dominantSwatch?.rgb ?: android.graphics.Color.GRAY
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        if (bitmap.width == size && bitmap.height == size) return bitmap

        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, left, top, size, size)
    }

    private fun computeAverageHash(source: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(source, 8, 8, true)
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)

        var total = 0L
        val grays = IntArray(64)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val gray = (
                (android.graphics.Color.red(pixel) * 299) +
                    (android.graphics.Color.green(pixel) * 587) +
                    (android.graphics.Color.blue(pixel) * 114)
                ) / 1000
            grays[index] = gray
            total += gray
        }

        val average = total / 64.0
        var hash = 0L
        for (index in grays.indices) {
            if (grays[index] >= average) {
                hash = hash or (1L shl index)
            }
        }

        return hash
    }
}
