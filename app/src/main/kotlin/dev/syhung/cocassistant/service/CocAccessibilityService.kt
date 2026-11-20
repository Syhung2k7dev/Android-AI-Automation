package dev.syhung.cocassistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class CocAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CocAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    fun dispatchGesture(path: Path, duration: Long) {
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        dispatchGesture(builder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("CocAccessibilityService", "Event: ${event?.eventType}")
    }

    override fun onInterrupt() {
        instance = null
        Log.d("CocAccessibilityService", "Interrupt")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
