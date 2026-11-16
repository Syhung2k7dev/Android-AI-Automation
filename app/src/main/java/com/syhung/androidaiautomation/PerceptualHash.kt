package com.syhung.androidaiautomation

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sqrt

object PerceptualHash {
    fun compute(bitmap: Bitmap): Long {
        val size = 32
        val small = if (bitmap.width == size && bitmap.height == size) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        try {
            val pixels = IntArray(size * size)
            small.getPixels(pixels, 0, size, 0, 0, size, size)
            val gray = DoubleArray(size * size) { index ->
                val p = pixels[index]
                0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)
            }
            val coefficients = DoubleArray(64)
            var index = 0
            for (v in 0 until 8) {
                for (u in 0 until 8) {
                    var sum = 0.0
                    for (y in 0 until size) {
                        for (x in 0 until size) {
                            sum += gray[y * size + x] *
                                cos((2 * x + 1) * u * Math.PI / (2.0 * size)) *
                                cos((2 * y + 1) * v * Math.PI / (2.0 * size))
                        }
                    }
                    coefficients[index++] = sum
                }
            }
            val sorted = coefficients.copyOfRange(1, coefficients.size).sorted()
            val median = sorted[sorted.size / 2]
            var hash = 0L
            for (i in coefficients.indices) {
                if (coefficients[i] >= median) hash = hash or (1L shl i)
            }
            return hash
        } finally {
            small.recycle()
        }
    }

    fun distance(first: Long, second: Long): Int =
        java.lang.Long.bitCount(first xor second)
}
