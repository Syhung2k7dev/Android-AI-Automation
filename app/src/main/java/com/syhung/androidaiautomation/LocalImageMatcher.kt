package com.syhung.androidaiautomation

import android.graphics.Bitmap
import kotlin.math.sqrt

object LocalImageMatcher {
    data class Result(val matched: Boolean, val confidence: Float)

    fun match(screen: Bitmap, template: Bitmap, threshold: Float): Result {
        require(threshold in 0.50f..0.99f)
        val scale = minOf(1f, 360f / screen.width.toFloat())
        val screenWidth = (screen.width * scale).toInt().coerceAtLeast(1)
        val screenHeight = (screen.height * scale).toInt().coerceAtLeast(1)
        val scaledScreen = Bitmap.createScaledBitmap(screen, screenWidth, screenHeight, true)
        val scaledTemplate = Bitmap.createScaledBitmap(
            template,
            (template.width * scale).toInt().coerceAtLeast(1),
            (template.height * scale).toInt().coerceAtLeast(1),
            true
        )
        try {
            if (scaledTemplate.width > scaledScreen.width || scaledTemplate.height > scaledScreen.height) {
                return Result(false, 0f)
            }
            val sw = scaledScreen.width
            val sh = scaledScreen.height
            val tw = scaledTemplate.width
            val th = scaledTemplate.height
            val screenPixels = IntArray(sw * sh)
            val templatePixels = IntArray(tw * th)
            scaledScreen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh)
            scaledTemplate.getPixels(templatePixels, 0, tw, 0, 0, tw, th)
            val sampleStep = if (tw * th > 10_000) 3 else 2
            val templateValues = ArrayList<Float>()
            var templateSum = 0.0
            for (y in 0 until th step sampleStep) {
                for (x in 0 until tw step sampleStep) {
                    val p = templatePixels[y * tw + x]
                    val gray = (0.299 * ((p shr 16) and 255) +
                        0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)).toFloat()
                    templateValues.add(gray)
                    templateSum += gray
                }
            }
            if (templateValues.size < 16) return Result(false, 0f)
            val meanT = templateSum / templateValues.size
            var varianceT = 0.0
            for (v in templateValues) varianceT += (v - meanT) * (v - meanT)
            if (varianceT < 1.0) return Result(false, 0f)

            var best = -1f
            val stride = maxOf(1, minOf(tw, th) / 12)
            for (top in 0..(sh - th) step stride) {
                for (left in 0..(sw - tw) step stride) {
                    var sumS = 0.0
                    var index = 0
                    var n = 0
                    for (y in 0 until th step sampleStep) {
                        for (x in 0 until tw step sampleStep) {
                            val p = screenPixels[(top + y) * sw + left + x]
                            sumS += 0.299 * ((p shr 16) and 255) +
                                0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)
                            index++
                            n++
                        }
                    }
                    val meanS = sumS / n
                    var numerator = 0.0
                    var varianceS = 0.0
                    index = 0
                    for (y in 0 until th step sampleStep) {
                        for (x in 0 until tw step sampleStep) {
                            val p = screenPixels[(top + y) * sw + left + x]
                            val gray = (0.299 * ((p shr 16) and 255) +
                                0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)).toFloat()
                            val a = templateValues[index++] - meanT
                            val b = gray - meanS
                            numerator += a * b
                            varianceS += b * b
                        }
                    }
                    if (varianceS > 1.0) {
                        val score = (numerator / sqrt(varianceT * varianceS)).toFloat().coerceIn(-1f, 1f)
                        if (score > best) best = score
                        if (best >= threshold) return Result(true, best)
                    }
                }
            }
            return Result(best >= threshold, best.coerceAtLeast(0f))
        } finally {
            scaledScreen.recycle()
            scaledTemplate.recycle()
        }
    }
}
