package com.syhung.androidaiautomation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AutomationAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var steps: List<WorkflowStep> = emptyList()
    private var workflowQueue: List<List<WorkflowStep>> = emptyList()
    private var queueIndex = 0
    private var stepIndex = 0
    private var repeatLimit = 1
    private var repeatIndex = 0
    private var startRunnable: Runnable? = null
    private var sequenceRunnable: Runnable? = null
    private var pointPickerRunnable: Runnable? = null
    private var pointPickerView: View? = null
    private var pointPickerTarget: String? = null
    private var ocrCaptureRunnable: Runnable? = null
    private var cloudAnalysisRunnable: Runnable? = null
    private var conditionPollRunnable: Runnable? = null
    private var conditionDeadline = 0L
    private var conditionCaptureInFlight = false
    private var workflowGeneration = 0L
    private var paused = false
    private var dashboardView: View? = null
    private var dashboardStatus: TextView? = null
    private var watchdogRunnable: Runnable? = null
    private var lastScreenHash: Long? = null
    private var stagnantSamples = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopSequence()
        cancelPointPicker()
        cancelOcrCapture()
    }

    override fun onDestroy() {
        stopSequence()
        cancelPointPicker()
        cancelOcrCapture()
        cancelGeminiAnalysis()
        removeDashboard()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun executeNextStep() {
        if (!running || paused) return
        if (stepIndex >= steps.size) {
            repeatIndex++
            if (repeatIndex >= repeatLimit) {
                if (queueIndex + 1 >= workflowQueue.size) {
                    stopSequence()
                    Toast.makeText(this, "Profile queue completed.", Toast.LENGTH_LONG).show()
                    return
                }
                queueIndex++
                steps = workflowQueue[queueIndex]
                stepIndex = 0
                repeatIndex = 0
                Toast.makeText(this, "Starting profile ${queueIndex + 1} of ${workflowQueue.size}.", Toast.LENGTH_SHORT).show()
                scheduleNextStep(STEP_GAP_MS)
                return
            }
            stepIndex = 0
            scheduleNextStep(STEP_GAP_MS)
            return
        }

        when (val step = steps[stepIndex]) {
            is WorkflowStep.Wait -> {
                stepIndex++
                scheduleNextStep(step.durationMs)
            }
            is WorkflowStep.WaitForText -> beginTextCondition(step)
            is WorkflowStep.WaitForImage -> beginImageCondition(step)
            is WorkflowStep.IfText -> evaluateTextBranch(step)
            is WorkflowStep.IfImage -> evaluateImageBranch(step)
            WorkflowStep.ElseBranch -> {
                stepIndex = findEndIf(stepIndex) + 1
                scheduleNextStep(STEP_GAP_MS)
            }
            WorkflowStep.EndIf -> {
                stepIndex++
                scheduleNextStep(STEP_GAP_MS)
            }
            is WorkflowStep.Tap -> dispatchStroke(
                step.x, step.y, step.x, step.y, step.durationMs
            )
            is WorkflowStep.Swipe -> dispatchStroke(
                step.startX, step.startY, step.endX, step.endY, step.durationMs
            )
        }
    }

    private fun beginTextCondition(condition: WorkflowStep.WaitForText) {
        conditionDeadline = SystemClock.elapsedRealtime() + condition.timeoutMs
        conditionCaptureInFlight = false
        captureForTextCondition(condition)
    }

    private fun captureForTextCondition(condition: WorkflowStep.WaitForText) {
        if (!running || conditionCaptureInFlight) return
        if (SystemClock.elapsedRealtime() >= conditionDeadline) {
            stopSequence()
            Toast.makeText(this, "Text condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
            return
        }
        conditionCaptureInFlight = true
        val captureGeneration = workflowGeneration
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {
                        null
                    } finally {
                        buffer.close()
                    }
                    if (bitmap == null) {
                        if (!running || captureGeneration != workflowGeneration) return
                        conditionCaptureInFlight = false
                        stopSequence()
                        Toast.makeText(this@AutomationAccessibilityService, "Text condition could not read this screen. Workflow stopped.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { result ->
                            if (!running || captureGeneration != workflowGeneration) return@addOnSuccessListener
                            conditionCaptureInFlight = false
                            if (SystemClock.elapsedRealtime() >= conditionDeadline) {
                                stopSequence()
                                Toast.makeText(this@AutomationAccessibilityService, "Text condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
                            } else if (result.text.contains(condition.text, ignoreCase = true)) {
                                stepIndex++
                                conditionPollRunnable?.let(handler::removeCallbacks)
                                conditionPollRunnable = null
                                scheduleNextStep(STEP_GAP_MS)
                            } else {
                                scheduleConditionPoll(condition)
                            }
                        }
                        .addOnFailureListener {
                            if (!running || captureGeneration != workflowGeneration) return@addOnFailureListener
                            conditionCaptureInFlight = false
                            if (running) {
                                stopSequence()
                                Toast.makeText(this@AutomationAccessibilityService, "Text recognition failed. Workflow stopped.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnCompleteListener {
                            bitmap.recycle()
                            recognizer.close()
                        }
                }

                override fun onFailure(errorCode: Int) {
                    if (!running || captureGeneration != workflowGeneration) return
                    conditionCaptureInFlight = false
                    if (running) {
                        stopSequence()
                        Toast.makeText(this@AutomationAccessibilityService, "Screen capture unavailable for text condition (error $errorCode).", Toast.LENGTH_LONG).show()
                    }
                }
            })
        } catch (_: Exception) {
            conditionCaptureInFlight = false
            stopSequence()
            Toast.makeText(this, "Unable to capture screen for text condition.", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleConditionPoll(condition: WorkflowStep.WaitForText) {
        if (!running || paused) return
        if (SystemClock.elapsedRealtime() >= conditionDeadline) {
            stopSequence()
            Toast.makeText(this, "Text condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
            return
        }
        conditionPollRunnable?.let(handler::removeCallbacks)
        conditionPollRunnable = Runnable {
            conditionPollRunnable = null
            captureForTextCondition(condition)
        }
        handler.postDelayed(conditionPollRunnable!!, condition.pollIntervalMs)
    }

    private fun beginImageCondition(condition: WorkflowStep.WaitForImage) {
        conditionDeadline = SystemClock.elapsedRealtime() + condition.timeoutMs
        conditionCaptureInFlight = false
        captureForImageCondition(condition)
    }

    private fun captureForImageCondition(condition: WorkflowStep.WaitForImage) {
        if (!running || conditionCaptureInFlight) return
        if (SystemClock.elapsedRealtime() >= conditionDeadline) {
            stopSequence()
            Toast.makeText(this, "Image condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
            return
        }
        conditionCaptureInFlight = true
        val captureGeneration = workflowGeneration
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val screen = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {
                        null
                    } finally {
                        buffer.close()
                    }
                    if (screen == null) {
                        if (captureGeneration == workflowGeneration) {
                            conditionCaptureInFlight = false
                            stopSequence()
                            Toast.makeText(this@AutomationAccessibilityService, "Could not read screen for image condition.", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    val template = TemplateStore.load(this@AutomationAccessibilityService, condition.template)
                    if (template == null) {
                        screen.recycle()
                        if (!running || captureGeneration != workflowGeneration) return
                        conditionCaptureInFlight = false
                        stopSequence()
                        Toast.makeText(this@AutomationAccessibilityService, "Saved image template is missing or invalid.", Toast.LENGTH_LONG).show()
                        return
                    }
                    Thread {
                        val result = try {
                            LocalImageMatcher.match(screen, template, condition.threshold)
                        } catch (_: Exception) {
                            LocalImageMatcher.Result(false, 0f)
                        } finally {
                            screen.recycle()
                            template.recycle()
                        }
                        handler.post {
                            if (!running || captureGeneration != workflowGeneration) return@post
                            conditionCaptureInFlight = false
                            if (SystemClock.elapsedRealtime() >= conditionDeadline) {
                                stopSequence()
                                Toast.makeText(this@AutomationAccessibilityService, "Image condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
                            } else if (result.matched) {
                                stepIndex++
                                conditionPollRunnable?.let(handler::removeCallbacks)
                                conditionPollRunnable = null
                                scheduleNextStep(STEP_GAP_MS)
                            } else {
                                scheduleImagePoll(condition)
                            }
                        }
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    if (!running || captureGeneration != workflowGeneration) return
                    conditionCaptureInFlight = false
                    stopSequence()
                    Toast.makeText(this@AutomationAccessibilityService, "Screen capture unavailable for image condition ($errorCode).", Toast.LENGTH_LONG).show()
                }
            })
        } catch (_: Exception) {
            conditionCaptureInFlight = false
            stopSequence()
            Toast.makeText(this, "Unable to capture screen for image condition.", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleImagePoll(condition: WorkflowStep.WaitForImage) {
        if (!running) return
        if (SystemClock.elapsedRealtime() >= conditionDeadline) {
            stopSequence()
            Toast.makeText(this, "Image condition timed out. Workflow stopped.", Toast.LENGTH_LONG).show()
            return
        }
        conditionPollRunnable?.let(handler::removeCallbacks)
        conditionPollRunnable = Runnable {
            conditionPollRunnable = null
            captureForImageCondition(condition)
        }
        handler.postDelayed(conditionPollRunnable!!, condition.pollIntervalMs)
    }

    private fun evaluateTextBranch(condition: WorkflowStep.IfText) {
        if (conditionCaptureInFlight) return
        conditionCaptureInFlight = true
        val generation = workflowGeneration
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { buffer.close() }
                    if (bitmap == null) {
                        if (running && generation == workflowGeneration) {
                            conditionCaptureInFlight = false
                            stopSequence()
                            Toast.makeText(this@AutomationAccessibilityService, "IF text screenshot unavailable; workflow stopped.", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { result ->
                            if (!running || generation != workflowGeneration) return@addOnSuccessListener
                            conditionCaptureInFlight = false
                            applyBranchResult(result.text.contains(condition.text, ignoreCase = true))
                        }
                        .addOnFailureListener {
                            if (running && generation == workflowGeneration) {
                                conditionCaptureInFlight = false
                                stopSequence()
                                Toast.makeText(this@AutomationAccessibilityService, "IF text recognition failed.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnCompleteListener { bitmap.recycle(); recognizer.close() }
                }

                override fun onFailure(errorCode: Int) {
                    if (!running || generation != workflowGeneration) return
                    conditionCaptureInFlight = false
                    stopSequence()
                    Toast.makeText(this@AutomationAccessibilityService, "IF text screenshot blocked ($errorCode).", Toast.LENGTH_LONG).show()
                }
            })
        } catch (_: Exception) {
            conditionCaptureInFlight = false
            stopSequence()
        }
    }

    private fun evaluateImageBranch(condition: WorkflowStep.IfImage) {
        if (conditionCaptureInFlight) return
        conditionCaptureInFlight = true
        val generation = workflowGeneration
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val screen = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { buffer.close() }
                    if (screen == null) {
                        if (running && generation == workflowGeneration) {
                            conditionCaptureInFlight = false
                            stopSequence()
                        }
                        return
                    }
                    val template = TemplateStore.load(this@AutomationAccessibilityService, condition.template)
                    if (template == null) {
                        screen.recycle()
                        if (running && generation == workflowGeneration) {
                            conditionCaptureInFlight = false
                            stopSequence()
                            Toast.makeText(this@AutomationAccessibilityService, "IF image template is missing.", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    Thread {
                        val matched = try {
                            val native = OpenCvImageMatcher.match(screen, template, condition.threshold)
                            native?.confidence?.let { it >= condition.threshold }
                                ?: LocalImageMatcher.match(screen, template, condition.threshold).matched
                        } catch (_: Exception) {
                            try { LocalImageMatcher.match(screen, template, condition.threshold).matched } catch (_: Exception) { false }
                        } finally {
                            screen.recycle()
                            template.recycle()
                        }
                        handler.post {
                            if (!running || generation != workflowGeneration) return@post
                            conditionCaptureInFlight = false
                            applyBranchResult(matched)
                        }
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    if (!running || generation != workflowGeneration) return
                    conditionCaptureInFlight = false
                    stopSequence()
                    Toast.makeText(this@AutomationAccessibilityService, "IF image screenshot blocked ($errorCode).", Toast.LENGTH_LONG).show()
                }
            })
        } catch (_: Exception) {
            conditionCaptureInFlight = false
            stopSequence()
        }
    }

    private fun applyBranchResult(matched: Boolean) {
        if (paused) return
        val current = stepIndex
        stepIndex = if (matched) current + 1 else findFalseBranchTarget(current)
        scheduleNextStep(STEP_GAP_MS)
    }

    private fun findFalseBranchTarget(ifIndex: Int): Int {
        var depth = 0
        for (i in ifIndex + 1 until steps.size) {
            when (steps[i]) {
                is WorkflowStep.IfText, is WorkflowStep.IfImage -> depth++
                WorkflowStep.EndIf -> if (depth == 0) return i + 1 else depth--
                WorkflowStep.ElseBranch -> if (depth == 0) return i + 1
                else -> Unit
            }
        }
        return steps.size
    }

    private fun findEndIf(elseIndex: Int): Int {
        var depth = 0
        for (i in elseIndex + 1 until steps.size) {
            when (steps[i]) {
                is WorkflowStep.IfText, is WorkflowStep.IfImage -> depth++
                WorkflowStep.EndIf -> if (depth == 0) return i else depth--
                else -> Unit
            }
        }
        return steps.lastIndex
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
                updateDashboard()
                if (!paused) scheduleNextStep(STEP_GAP_MS)
                scheduleWatchdog()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                stopSequence()
            }
        }, null)

        if (!accepted) stopSequence()
    }

    private fun scheduleNextStep(delayMs: Long) {
        sequenceRunnable?.let(handler::removeCallbacks)
        sequenceRunnable = Runnable {
            sequenceRunnable = null
            executeNextStep()
        }
        if (!paused) handler.postDelayed(sequenceRunnable!!, delayMs)
    }

    private fun stopSequence() {
        workflowGeneration++
        running = false
        paused = false
        watchdogRunnable?.let(handler::removeCallbacks)
        watchdogRunnable = null
        lastScreenHash = null
        stagnantSamples = 0
        updateDashboard()
        startRunnable?.let(handler::removeCallbacks)
        startRunnable = null
        sequenceRunnable?.let(handler::removeCallbacks)
        sequenceRunnable = null
        conditionPollRunnable?.let(handler::removeCallbacks)
        conditionPollRunnable = null
        conditionCaptureInFlight = false
        conditionDeadline = 0L
        steps = emptyList()
        workflowQueue = emptyList()
        queueIndex = 0
        stepIndex = 0
        repeatIndex = 0
    }


    private fun showDashboard() {
        if (dashboardView != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xEE202938.toInt())
        }
        dashboardStatus = TextView(this).apply {
            text = "Automation ready"
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        panel.addView(dashboardStatus)
        panel.addView(Button(this).apply {
            text = "Pause / Resume"
            setOnClickListener {
                if (paused) resumeSequence() else pauseSequence()
            }
        })
        panel.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                stopSequence()
                Toast.makeText(this@AutomationAccessibilityService, "Workflow stopped.", Toast.LENGTH_SHORT).show()
            }
        })
        panel.addView(Button(this).apply {
            text = "Hide dashboard"
            setOnClickListener { removeDashboard() }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(panel, params)
            dashboardView = panel
            updateDashboard()
        } catch (_: Exception) {
            dashboardView = null
            dashboardStatus = null
            Toast.makeText(this, "Floating dashboard could not be shown on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDashboard() {
        dashboardStatus?.text = when {
            !running -> "Automation stopped"
            paused -> "Automation paused"
            else -> "Running profile ${queueIndex + 1}/${workflowQueue.size.coerceAtLeast(1)} • step ${stepIndex + 1}/${steps.size}"
        }
    }

    private fun removeDashboard() {
        val view = dashboardView ?: return
        dashboardView = null
        dashboardStatus = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) { }
    }

    private fun pauseSequence() {
        if (!running || paused) return
        paused = true
        sequenceRunnable?.let(handler::removeCallbacks)
        sequenceRunnable = null
        conditionPollRunnable?.let(handler::removeCallbacks)
        conditionPollRunnable = null
        watchdogRunnable?.let(handler::removeCallbacks)
        watchdogRunnable = null
        updateDashboard()
        Toast.makeText(this, "Workflow paused. Use the dashboard to resume.", Toast.LENGTH_SHORT).show()
    }

    private fun resumeSequence() {
        if (!running || !paused) return
        paused = false
        updateDashboard()
        scheduleNextStep(0L)
        scheduleWatchdog()
    }

    private fun scheduleWatchdog() {
        watchdogRunnable?.let(handler::removeCallbacks)
        if (!running || paused) return
        watchdogRunnable = Runnable {
            watchdogRunnable = null
            if (!running || paused) return@Runnable
            captureWatchdogFrame()
        }
        handler.postDelayed(watchdogRunnable!!, WATCHDOG_INTERVAL_MS)
    }

    private fun captureWatchdogFrame() {
        if (!running || paused) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { buffer.close() }
                    if (bitmap == null) {
                        scheduleWatchdog()
                        return
                    }
                    Thread {
                        val hash = try { PerceptualHash.compute(bitmap) } catch (_: Exception) { null }
                        bitmap.recycle()
                        handler.post {
                            if (!running || paused || hash == null) return@post
                            val previous = lastScreenHash
                            if (previous != null && PerceptualHash.distance(previous, hash) <= WATCHDOG_HASH_DISTANCE) {
                                stagnantSamples++
                            } else {
                                stagnantSamples = 0
                            }
                            lastScreenHash = hash
                            if (stagnantSamples >= WATCHDOG_STAGNANT_LIMIT) {
                                pauseSequence()
                                Toast.makeText(this@AutomationAccessibilityService, "Screen appears unchanged. Workflow paused for review.", Toast.LENGTH_LONG).show()
                            } else {
                                scheduleWatchdog()
                            }
                        }
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    scheduleWatchdog()
                }
            })
        } catch (_: Exception) {
            scheduleWatchdog()
        }
    }

    private fun captureScreenForOcr() {
        ocrCaptureRunnable = null
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {
                        null
                    } finally {
                        hardwareBuffer.close()
                    }

                    if (bitmap == null) {
                        saveOcrResult("Unable to prepare the screenshot on this device.")
                        return
                    }

                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { result ->
                            val recognized = result.text.trim()
                            saveOcrResult(
                                if (recognized.isEmpty()) "No readable text was found on the captured screen."
                                else recognized
                            )
                        }
                        .addOnFailureListener { error ->
                            saveOcrResult("Text recognition failed: ${error.localizedMessage ?: "unknown error"}")
                        }
                        .addOnCompleteListener {
                            bitmap.recycle()
                            recognizer.close()
                        }
                }

                override fun onFailure(errorCode: Int) {
                    saveOcrResult(
                        "Screen capture was blocked or unavailable (error $errorCode). " +
                            "Some secure or protected screens cannot be captured."
                    )
                }
            })
        } catch (error: Exception) {
            saveOcrResult("Screen capture failed: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    private fun captureScreenForGeminiAnalysis() {
        cloudAnalysisRunnable = null
        val key = try { CloudKeyStore.read(this) } catch (_: Exception) { null }
        if (key.isNullOrBlank()) {
            saveGeminiAnalysis("No Gemini API key is configured.")
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { buffer.close() }
                    if (bitmap == null) {
                        saveGeminiAnalysis("Could not prepare the screenshot. It was not uploaded.")
                        return
                    }
                    Thread {
                        val result = try {
                            GeminiCloudClient.analyzeImage(
                                this@AutomationAccessibilityService,
                                key,
                                "Analyze this user-authorized Android screenshot. Return concise JSON with keys summary, visible_controls, possible_blocker, suggested_next_step. Describe only visible UI. Do not invent details, do not provide coordinates or gesture commands, and do not claim to have changed the device.",
                                bitmap
                            ).text
                        } catch (error: Exception) {
                            "Gemini analysis unavailable: ${error.localizedMessage ?: "request failed"}"
                        } finally {
                            bitmap.recycle()
                        }
                        saveGeminiAnalysis(result)
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    saveGeminiAnalysis("Screenshot blocked or unavailable ($errorCode). Nothing was uploaded.")
                }
            })
        } catch (error: Exception) {
            saveGeminiAnalysis("Screenshot capture failed: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    private fun saveGeminiAnalysis(text: String) {
        getSharedPreferences(GEMINI_RESULT_PREFS, MODE_PRIVATE).edit()
            .putString("result", text.take(12_000))
            .putBoolean("ready", true)
            .apply()
        handler.post {
            Toast.makeText(this, "Gemini visual analysis finished. Return to the app to review it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelGeminiAnalysis() {
        cloudAnalysisRunnable?.let(handler::removeCallbacks)
        cloudAnalysisRunnable = null
    }

    private fun saveOcrResult(text: String) {
        getSharedPreferences(OCR_PREFS, MODE_PRIVATE).edit()
            .putString("result", text.take(MAX_OCR_RESULT_CHARS))
            .putBoolean("ready", true)
            .apply()
        Toast.makeText(this, "Offline OCR finished. Return to Android AI Automation to view the result.", Toast.LENGTH_LONG).show()
    }

    private fun cancelOcrCapture() {
        ocrCaptureRunnable?.let(handler::removeCallbacks)
        ocrCaptureRunnable = null
    }

    private fun showPointPicker() {
        pointPickerRunnable = null
        if (pointPickerTarget == null || pointPickerView != null) return

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            isClickable = true
        }

        val instruction = TextView(this).apply {
            text = "POINT PICKER\nTap the desired screen location. Press Cancel to exit."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xEE202938.toInt())
        }
        root.addView(
            instruction,
            FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val cancel = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                cancelPointPicker()
                Toast.makeText(this@AutomationAccessibilityService, "Point selection cancelled", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(
            cancel,
            FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val target = pointPickerTarget
                if (target != null) {
                    getSharedPreferences(POINT_PICKER_PREFS, MODE_PRIVATE).edit()
                        .putFloat("selected_x", event.rawX)
                        .putFloat("selected_y", event.rawY)
                        .putString("selected_target", target)
                        .putBoolean("selection_ready", true)
                        .apply()
                    removePointPickerView()
                    pointPickerTarget = null
                    Toast.makeText(
                        this,
                        "Point saved. Return to Android AI Automation to review it.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, params)
            pointPickerView = root
        } catch (_: Exception) {
            pointPickerTarget = null
            Toast.makeText(this, "Unable to show point picker on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removePointPickerView() {
        val view = pointPickerView ?: return
        pointPickerView = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
            // The accessibility overlay may already have been removed by the system.
        }
    }

    private fun cancelPointPicker() {
        pointPickerRunnable?.let(handler::removeCallbacks)
        pointPickerRunnable = null
        pointPickerTarget = null
        removePointPickerView()
    }

    companion object {
        private const val START_DELAY_MS = 5_000L
        private const val STEP_GAP_MS = 250L
        private const val POINT_PICKER_DELAY_MS = 10_000L
        private const val POINT_PICKER_PREFS = "point_picker"
        private const val OCR_PREFS = "ocr_results"
        private const val GEMINI_RESULT_PREFS = "gemini_visual_result"
        private const val OCR_CAPTURE_DELAY_MS = 8_000L
        private const val MAX_OCR_RESULT_CHARS = 30_000
        private const val WATCHDOG_INTERVAL_MS = 12_000L
        private const val WATCHDOG_STAGNANT_LIMIT = 3
        private const val WATCHDOG_HASH_DISTANCE = 2

        @Volatile private var instance: AutomationAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun start(workflow: List<WorkflowStep>, repeatCount: Int): Boolean =
            startQueue(listOf(workflow), repeatCount)

        fun startQueue(workflows: List<List<WorkflowStep>>, repeatCount: Int): Boolean {
            val service = instance ?: return false
            if (workflows.isEmpty() || workflows.size > 20 || repeatCount !in 1..20 ||
                workflows.any { !WorkflowStep.validateStructure(it) }
            ) return false

            service.cancelPointPicker()
            service.cancelOcrCapture()
            service.stopSequence()
            service.workflowQueue = workflows.map { it.toList() }
            service.queueIndex = 0
            service.steps = service.workflowQueue.first()
            service.stepIndex = 0
            service.repeatLimit = repeatCount
            service.repeatIndex = 0
            service.running = true
            service.paused = false
            service.showDashboard()
            service.updateDashboard()
            service.scheduleWatchdog()
            service.startRunnable = Runnable {
                service.startRunnable = null
                if (service.running) service.executeNextStep()
            }
            service.handler.postDelayed(service.startRunnable!!, START_DELAY_MS)
            return true
        }

        fun armGeminiAnalysis(): Boolean {
            val service = instance ?: return false
            val key = try { CloudKeyStore.read(service) } catch (_: Exception) { null }
            if (key.isNullOrBlank()) return false
            service.cancelPointPicker()
            service.cancelOcrCapture()
            service.cancelGeminiAnalysis()
            service.stopSequence()
            service.getSharedPreferences(GEMINI_RESULT_PREFS, MODE_PRIVATE).edit().clear().apply()
            Toast.makeText(service, "Switch to the screen you explicitly approved for cloud analysis. One screenshot will be sent to Gemini in 8 seconds.", Toast.LENGTH_LONG).show()
            service.cloudAnalysisRunnable = Runnable {
                service.cloudAnalysisRunnable = null
                service.captureScreenForGeminiAnalysis()
            }
            service.handler.postDelayed(service.cloudAnalysisRunnable!!, OCR_CAPTURE_DELAY_MS)
            return true
        }

        fun armOcrCapture(): Boolean {
            val service = instance ?: return false
            service.cancelPointPicker()
            service.cancelOcrCapture()
            service.stopSequence()
            service.getSharedPreferences(OCR_PREFS, MODE_PRIVATE).edit()
                .remove("result")
                .putBoolean("ready", false)
                .apply()
            Toast.makeText(
                service,
                "Switch to the screen you want to read. A single screenshot will be captured in 8 seconds and processed on this device.",
                Toast.LENGTH_LONG
            ).show()
            service.ocrCaptureRunnable = Runnable {
                service.ocrCaptureRunnable = null
                service.captureScreenForOcr()
            }
            service.handler.postDelayed(service.ocrCaptureRunnable!!, OCR_CAPTURE_DELAY_MS)
            return true
        }

        fun armPointPicker(target: String): Boolean {
            val service = instance ?: return false
            if (target !in setOf("tap", "swipe_start", "swipe_end")) return false
            service.cancelPointPicker()
            service.cancelOcrCapture()
            service.stopSequence()
            service.pointPickerTarget = target
            Toast.makeText(
                service,
                "Switch to the target app now. Point picker opens in 10 seconds.",
                Toast.LENGTH_LONG
            ).show()
            service.pointPickerRunnable = Runnable { service.showPointPicker() }
            service.handler.postDelayed(service.pointPickerRunnable!!, POINT_PICKER_DELAY_MS)
            return true
        }

        fun pauseOrResume(): Boolean {
            val service = instance ?: return false
            if (!service.running) return false
            if (service.paused) service.resumeSequence() else service.pauseSequence()
            return true
        }

        fun stop() {
            instance?.let {
                it.stopSequence()
                it.cancelPointPicker()
                it.cancelOcrCapture()
                it.cancelGeminiAnalysis()
            }
        }
    }
}
