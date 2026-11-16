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
    private var steps: List<WorkflowStep> = emptyList()
    private var stepIndex = 0
    private var repeatLimit = 1
    private var repeatIndex = 0
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

    private fun executeNextStep() {
        if (!running) return
        if (stepIndex >= steps.size) {
            repeatIndex++
            if (repeatIndex >= repeatLimit) {
                stopSequence()
                return
            }
            stepIndex = 0
            handler.postDelayed({ executeNextStep() }, STEP_GAP_MS)
            return
        }

        when (val step = steps[stepIndex]) {
            is WorkflowStep.Wait -> {
                stepIndex++
                handler.postDelayed({ executeNextStep() }, step.durationMs)
            }
            is WorkflowStep.Tap -> dispatchStroke(
                step.x, step.y, step.x, step.y, step.durationMs
            )
            is WorkflowStep.Swipe -> dispatchStroke(
                step.startX, step.startY, step.endX, step.endY, step.durationMs
            )
        }
    }

    private fun dispatchStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, durationMs.coerceIn(50L, 10_000L)
                )
            )
            .build()

        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!running) return
                stepIndex++
                handler.postDelayed({ executeNextStep() }, STEP_GAP_MS)
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
        steps = emptyList()
        stepIndex = 0
        repeatIndex = 0
    }

    companion object {
        private const val START_DELAY_MS = 5_000L
        private const val STEP_GAP_MS = 250L

        @Volatile private var instance: AutomationAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun start(workflow: List<WorkflowStep>, repeatCount: Int): Boolean {
            val service = instance ?: return false
            if (workflow.isEmpty() || workflow.size > 50 || repeatCount !in 1..20) return false

            service.stopSequence()
            service.steps = workflow.toList()
            service.stepIndex = 0
            service.repeatLimit = repeatCount
            service.repeatIndex = 0
            service.running = true
            service.startRunnable = Runnable {
                service.startRunnable = null
                if (service.running) service.executeNextStep()
            }
            service.handler.postDelayed(service.startRunnable!!, START_DELAY_MS)
            return true
        }

        fun stop() {
            instance?.stopSequence()
        }
    }
}
