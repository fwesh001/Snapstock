package com.example.snapstock.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.pow

object ImageSharpness {
    fun score(bitmap: Bitmap): Double {
        val resized = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        val width = resized.width
        val height = resized.height
        if (width < 3 || height < 3) return 0.0

        val grayscale = IntArray(width * height)
        resized.getPixels(grayscale, 0, width, 0, 0, width, height)
        val grayValues = IntArray(grayscale.size)

        for (index in grayscale.indices) {
            val pixel = grayscale[index]
            grayValues[index] = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        }

        val laplacian = DoubleArray((width - 2) * (height - 2))
        var lapIndex = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = grayValues[y * width + x]
                val left = grayValues[y * width + (x - 1)]
                val right = grayValues[y * width + (x + 1)]
                val top = grayValues[(y - 1) * width + x]
                val bottom = grayValues[(y + 1) * width + x]
                laplacian[lapIndex++] = (4 * center - left - right - top - bottom).toDouble()
            }
        }

        if (laplacian.isEmpty()) return 0.0
        val mean = laplacian.average()
        var variance = 0.0
        for (value in laplacian) {
            variance += (value - mean).pow(2)
        }
        return variance / laplacian.size
    }
}
