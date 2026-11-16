package com.syhung.androidaiautomation

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCvImageMatcher {
    data class Match(val confidence: Float, val centerX: Int, val centerY: Int)

    @Volatile private var initialized = false

    fun match(screen: Bitmap, template: Bitmap, threshold: Float): Match? {
        require(threshold in 0.50f..0.99f)
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    if (!OpenCVLoader.initLocal()) return null
                    initialized = true
                }
            }
        }

        val screenRgba = Mat()
        val templateRgba = Mat()
        val screenGray = Mat()
        val templateGray = Mat()
        val result = Mat()
        try {
            Utils.bitmapToMat(screen, screenRgba)
            Utils.bitmapToMat(template, templateRgba)
            Imgproc.cvtColor(screenRgba, screenGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(templateRgba, templateGray, Imgproc.COLOR_RGBA2GRAY)

            val scale = minOf(1.0, 480.0 / screenGray.cols().toDouble())
            if (scale < 1.0) {
                Imgproc.resize(screenGray, screenGray, Size(), scale, scale, Imgproc.INTER_AREA)
                Imgproc.resize(templateGray, templateGray, Size(), scale, scale, Imgproc.INTER_AREA)
            }
            if (templateGray.cols() > screenGray.cols() || templateGray.rows() > screenGray.rows()) {
                return Match(0f, -1, -1)
            }
            Imgproc.matchTemplate(screenGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
            val minMax = Core.minMaxLoc(result)
            val center = Point(
                minMax.maxLoc.x + templateGray.cols() / 2.0,
                minMax.maxLoc.y + templateGray.rows() / 2.0
            )
            return Match(
                minMax.maxVal.toFloat().coerceIn(-1f, 1f),
                (center.x / scale).toInt(),
                (center.y / scale).toInt()
            )
        } finally {
            screenRgba.release()
            templateRgba.release()
            screenGray.release()
            templateGray.release()
            result.release()
        }
    }
}
