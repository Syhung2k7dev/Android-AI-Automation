package com.syhung.androidaiautomation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AutomationAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var tapsRemaining = 0
    private var tapX = 0f
    private var tapY = 0f
    private var intervalMs = 1500L
    private var startRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopSequence()
    }

    override fun onDestroy() {
        stopSequence()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun scheduleTap() {
        if (!running || tapsRemaining <= 0) {
            stopSequence()
            return
        }

        val path = Path().apply { moveTo(tapX, tapY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()

        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!running) return
                tapsRemaining--
                if (tapsRemaining <= 0) {
                    stopSequence()
                } else {
                    handler.postDelayed({ scheduleTap() }, intervalMs)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                stopSequence()
            }
        }, null)

        if (!accepted) stopSequence()
    }

    private fun stopSequence() {
        running = false
        startRunnable?.let(handler::removeCallbacks)
        startRunnable = null
        handler.removeCallbacksAndMessages(null)
        tapsRemaining = 0
    }

    companion object {
        private const val START_DELAY_MS = 5_000L

        @Volatile private var instance: AutomationAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun start(x: Float, y: Float, interval: Long, count: Int): Boolean {
            val service = instance ?: return false
            service.stopSequence()
            service.tapX = x
            service.tapY = y
            service.intervalMs = interval.coerceAtLeast(500L)
            service.tapsRemaining = count.coerceIn(1, 1000)
            service.running = true
            service.startRunnable = Runnable {
                service.startRunnable = null
                if (service.running) service.scheduleTap()
            }
            service.handler.postDelayed(service.startRunnable!!, START_DELAY_MS)
            return true
        }

        fun stop() {
            instance?.stopSequence()
        }
    }
}
