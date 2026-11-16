package com.syhung.androidaiautomation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var profileInput: EditText
    private lateinit var xInput: EditText
    private lateinit var yInput: EditText
    private lateinit var endXInput: EditText
    private lateinit var endYInput: EditText
    private lateinit var durationInput: EditText
    private lateinit var waitInput: EditText
    private lateinit var repeatInput: EditText
    private lateinit var conditionTextInput: EditText
    private lateinit var templateInput: EditText
    private var pendingTemplateName: String? = null
    private lateinit var stepsView: TextView
    private val steps = mutableListOf<WorkflowStep>()
    private var pendingExport: String? = null

    private val preferences by lazy {
        getSharedPreferences("automation_profiles", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 28, 28, 24)
        }

        root.addView(TextView(this).apply {
            text = "Android AI Automation"
            textSize = 24f
        }, matchWidth())

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }
        root.addView(status, matchWidth())

        profileInput = field("Profile name", "My workflow")
        xInput = field("Start / tap X coordinate", "540")
        yInput = field("Start / tap Y coordinate", "900")
        endXInput = field("Swipe end X coordinate", "540")
        endYInput = field("Swipe end Y coordinate", "500")
        durationInput = field("Tap / swipe duration in ms (50–10000)", "300")
        waitInput = field("Wait duration in ms (100–60000)", "1000")
        repeatInput = field("Repeat workflow (1–20)", "1")
        conditionTextInput = field("Wait until this text appears (max 80 characters)", "Ready")
        templateInput = field("Image template name", "target_button")

        listOf(
            profileInput, xInput, yInput, endXInput, endYInput,
            durationInput, waitInput, repeatInput, conditionTextInput, templateInput
        ).forEach { root.addView(it, matchWidth()) }

        root.addView(button("Select tap point visually") { armPointSelection("tap") }, matchWidth())
        root.addView(button("Select swipe start visually") { armPointSelection("swipe_start") }, matchWidth())
        root.addView(button("Select swipe end visually") { armPointSelection("swipe_end") }, matchWidth())
        root.addView(button("Add tap step") { addTap() }, matchWidth())
        root.addView(button("Add swipe step") { addSwipe() }, matchWidth())
        root.addView(button("Add wait step") { addWait() }, matchWidth())
        root.addView(button("Add wait-for-text condition") { addWaitForText() }, matchWidth())
        root.addView(button("Add IF text condition") { addIfText() }, matchWidth())
        root.addView(button("Add IF image condition") { addIfImage() }, matchWidth())
        root.addView(button("Add ELSE branch") { addElseBranch() }, matchWidth())
        root.addView(button("Add END IF") { addEndIf() }, matchWidth())
        root.addView(button("Import image template") { importImageTemplate() }, matchWidth())
        root.addView(button("Add wait-for-image condition") { addWaitForImage() }, matchWidth())
        root.addView(button("Manage image templates") { manageImageTemplates() }, matchWidth())
        root.addView(button("Learn template from screen region") { confirmTemplateLearning() }, matchWidth())
        root.addView(button("Manage / reorder steps") { manageStep() }, matchWidth())
        root.addView(button("Clear workflow") {
            if (steps.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("Remove all ${steps.size} steps from the current workflow?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ ->
                        steps.clear()
                        renderSteps()
                        status.text = "Current workflow cleared."
                    }
                    .show()
            }
        }, matchWidth())

        stepsView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 14, 0, 14)
        }
        root.addView(stepsView, matchWidth())
        renderSteps()

        root.addView(button("Save workflow on this device") { saveProfile() }, matchWidth())
        root.addView(button("Load workflow") { loadProfile() }, matchWidth())
        root.addView(button("Saved workflow library") { manageSavedWorkflows() }, matchWidth())
        root.addView(button("Run saved profile queue") { confirmProfileQueue() }, matchWidth())
        root.addView(button("Export workflow file") { exportWorkflow() }, matchWidth())
        root.addView(button("Import workflow file") { importWorkflow() }, matchWidth())
        root.addView(button("Read screen text offline") { confirmOcrCapture() }, matchWidth())
        root.addView(button("Gemini cloud settings") { configureGemini() }, matchWidth())
        root.addView(button("Check Gemini connection") { testGeminiConnection() }, matchWidth())
        root.addView(button("Gemini token / RPM monitor") { showGeminiUsage() }, matchWidth())
        root.addView(button("Analyze current screen with Gemini") { confirmGeminiVisualAnalysis() }, matchWidth())
        root.addView(button("Generate workflow draft with Gemini") { requestGeminiWorkflowDraft() }, matchWidth())
        root.addView(button("Schedule cooldown reminder") { scheduleCooldownReminder() }, matchWidth())
        root.addView(button("Cancel cooldown reminder") { cancelCooldownReminder() }, matchWidth())
        root.addView(button("Clear OCR result") {
            getSharedPreferences("ocr_results", MODE_PRIVATE).edit().clear().apply()
            status.text = "Stored OCR result cleared."
        }, matchWidth())
        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth())
        root.addView(button("Run workflow") { confirmStart() }, matchWidth())
        root.addView(button("Pause / Resume") {
            if (!AutomationAccessibilityService.pauseOrResume()) status.text = "No running workflow to pause or resume."
        }, matchWidth())
        root.addView(button("Stop") {
            AutomationAccessibilityService.stop()
            status.text = "Stopped. Pending start and remaining steps were cancelled."
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Local workflow editor: create, reorder, save, import and export tap, swipe, wait, OCR text conditions, and local image conditions. Use visual point selection to capture coordinates from an authorized screen. The picker opens after a 10-second switch window and temporarily intercepts one tap to record its position. Workflows stay on this device unless you explicitly export a file. Each run requires confirmation and has a 5-second countdown. Limit: 50 steps and 20 repeats. Screen text recognition and image matching run locally. Optional Gemini text requests require a saved API key and a separate user action; this build does not automatically upload screenshots or execute AI-generated actions."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        }, matchWidth())

        setContentView(ScrollView(this).apply { addView(root) })
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            applyPickedPoint()
            showPendingOcrResult()
            showPendingGeminiAnalysis()
            handleScheduledProfilePrompt()
            refreshStatus()
        }
    }

    private fun handleScheduledProfilePrompt() {
        val name = intent.getStringExtra("scheduled_profile_name") ?: return
        intent.removeExtra("scheduled_profile_name")
        AlertDialog.Builder(this)
            .setTitle("Scheduled profile is ready")
            .setMessage("The reminder for '$name' has arrived. Review the saved workflow before deciding whether to run it. The schedule never starts automation automatically.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Review profile") { _, _ ->
                profileInput.setText(name)
                loadProfile()
            }
            .show()
    }


    private fun configureGemini() {
        val currentKey = CloudKeyStore.read(this).orEmpty()
        val input = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(currentKey)
        }
        AlertDialog.Builder(this)
            .setTitle("Gemini cloud settings")
            .setMessage(
                "The API key is stored locally using Android Keystore-backed encrypted preferences. " +
                    "Cloud requests are sent only when you explicitly test or request them. Screen images are not sent by this setting."
            )
            .setView(input)
            .setNeutralButton("Remove key") { _, _ ->
                CloudKeyStore.clear(this)
                status.text = "Gemini API key removed. Local-only mode remains available."
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                try {
                    CloudKeyStore.save(this, key)
                    status.text = "Gemini API key saved in encrypted local storage."
                } catch (_: Exception) {
                    status.text = "Invalid API key format. Check the key and try again."
                }
            }
            .show()
    }

    private fun testGeminiConnection() {
        val key = try {
            CloudKeyStore.read(this)
        } catch (_: Exception) {
            null
        }
        if (key.isNullOrBlank()) {
            status.text = "Add a Gemini API key in Gemini cloud settings first."
            return
        }
        status.text = "Testing Gemini connection…"
        Thread {
            try {
                val reply = GeminiCloudClient.ask(
                    this,
                    key,
                    "Reply with exactly: Gemini connection successful."
                )
                runOnUiThread {
                    status.text = "Gemini connected. " + GeminiCloudClient.usage(this) +
                        " | Response: " + reply.text.take(100)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Gemini unavailable; local-only mode remains active. " +
                        (error.localizedMessage ?: "Request failed.")
                }
            }
        }.start()
    }

    private fun showGeminiUsage() {
        val usage = GeminiCloudClient.usage(this)
        val circuit = getSharedPreferences("gemini_resilience", MODE_PRIVATE)
        val openUntil = circuit.getLong("open_until", 0L)
        val remaining = ((openUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
        AlertDialog.Builder(this)
            .setTitle("Gemini usage and quota")
            .setMessage(
                "$usage\n\nCircuit breaker: " +
                    if (remaining > 0L) "OPEN — local-only mode for about $remaining seconds."
                    else "CLOSED — cloud requests available when explicitly requested."
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmOcrCapture() {
        AlertDialog.Builder(this)
            .setTitle("Read text from another screen?")
            .setMessage(
                "The app will capture one screenshot after an 8-second switch window. " +
                    "The image is processed on this device using the bundled offline text-recognition model. " +
                    "The screenshot is not saved or uploaded; recognized text is temporarily stored locally until you view or clear it. " +
                    "Do not use this on private or sensitive screens."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                if (AutomationAccessibilityService.armOcrCapture()) {
                    status.text = "Switch to the screen to read within 8 seconds."
                } else {
                    status.text = "Enable the app's Accessibility service before using OCR."
                }
            }
            .show()
    }

    private fun showPendingOcrResult() {
        val resultStore = getSharedPreferences("ocr_results", MODE_PRIVATE)
        if (!resultStore.getBoolean("ready", false)) return
        val result = resultStore.getString("result", "No result available").orEmpty()
        resultStore.edit().clear().apply()

        val resultView = TextView(this).apply {
            text = result
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(20, 16, 20, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Offline OCR result")
            .setView(ScrollView(this).apply { addView(resultView) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun armPointSelection(target: String) {
        if (!AutomationAccessibilityService.armPointPicker(target)) {
            status.text = "Enable the app's Accessibility service before selecting a point."
            return
        }
        status.text = "Switch to the target app within 10 seconds. The next tap will be recorded as a coordinate."
    }

    private fun applyPickedPoint() {
        val picked = getSharedPreferences("point_picker", MODE_PRIVATE)
        if (!picked.getBoolean("selection_ready", false)) return

        val x = picked.getFloat("selected_x", -1f)
        val y = picked.getFloat("selected_y", -1f)
        val target = picked.getString("selected_target", null)
        if (x < 0f || y < 0f || target == null) {
            picked.edit().putBoolean("selection_ready", false).apply()
            status.text = "Point selection result was invalid."
            return
        }

        when (target) {
            "tap" -> {
                xInput.setText(x.toInt().toString())
                yInput.setText(y.toInt().toString())
            }
            "swipe_start" -> {
                xInput.setText(x.toInt().toString())
                yInput.setText(y.toInt().toString())
            }
            "swipe_end" -> {
                endXInput.setText(x.toInt().toString())
                endYInput.setText(y.toInt().toString())
            }
        }
        picked.edit().putBoolean("selection_ready", false).apply()
        status.text = "Point selected: X=${x.toInt()}, Y=${y.toInt()}. Review the coordinates before adding a step."
    }

    private fun addTap() {
        val point = readPoint() ?: return invalidInput()
        val duration = durationInput.text.toString().toLongOrNull()
            ?.takeIf { it in 50L..10_000L } ?: return invalidInput()
        addStep(WorkflowStep.Tap(point.first, point.second, duration))
    }

    private fun addSwipe() {
        val start = readPoint() ?: return invalidInput()
        val endX = endXInput.text.toString().toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..20_000f } ?: return invalidInput()
        val endY = endYInput.text.toString().toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..20_000f } ?: return invalidInput()
        val duration = durationInput.text.toString().toLongOrNull()
            ?.takeIf { it in 50L..10_000L } ?: return invalidInput()
        addStep(WorkflowStep.Swipe(start.first, start.second, endX, endY, duration))
    }

    private fun addWait() {
        val duration = waitInput.text.toString().toLongOrNull()
            ?.takeIf { it in 100L..60_000L } ?: return invalidInput()
        addStep(WorkflowStep.Wait(duration))
    }

    private fun addWaitForText() {
        val textToFind = conditionTextInput.text.toString().trim()
        if (textToFind.isEmpty() || textToFind.length > 80 || textToFind.contains('|') || textToFind.contains('\n')) {
            status.text = "Enter text up to 80 characters without separator characters."
            return
        }
        addStep(WorkflowStep.WaitForText(textToFind, 30_000L, 2_000L))
    }

    private fun cancelCooldownReminder() {
        val alarm = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val pending = android.app.PendingIntent.getBroadcast(
            this,
            SCHEDULER_REQUEST,
            Intent(this, SchedulerReceiver::class.java),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarm.cancel(pending)
            pending.cancel()
            getSharedPreferences("scheduler_state", MODE_PRIVATE).edit().clear().apply()
            status.text = "Scheduled reminder cancelled."
        } else {
            status.text = "There is no pending cooldown reminder."
        }
    }

    private fun addIfText() {
        val textToFind = conditionTextInput.text.toString().trim()
        if (textToFind.isEmpty() || textToFind.length > 80 || textToFind.contains('|')) {
            status.text = "Enter a valid condition text (1–80 characters)."
            return
        }
        addStep(WorkflowStep.IfText(textToFind))
    }

    private fun addIfImage() {
        val name = templateInput.text.toString().trim()
        if (name !in TemplateStore.names(this)) {
            status.text = "Import or select an image template first."
            return
        }
        addStep(WorkflowStep.IfImage(name, 0.85f))
    }

    private fun addElseBranch() {
        val stack = mutableListOf<Boolean>()
        for (step in steps) when (step) {
            is WorkflowStep.IfText, is WorkflowStep.IfImage -> stack.add(false)
            WorkflowStep.ElseBranch -> if (stack.isNotEmpty()) stack[stack.lastIndex] = true
            WorkflowStep.EndIf -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            else -> Unit
        }
        if (stack.isEmpty() || stack.last()) {
            status.text = "ELSE requires an open IF block without an existing ELSE."
            return
        }
        addStep(WorkflowStep.ElseBranch)
    }

    private fun addEndIf() {
        var depth = 0
        for (step in steps) when (step) {
            is WorkflowStep.IfText, is WorkflowStep.IfImage -> depth++
            WorkflowStep.EndIf -> depth--
            else -> Unit
        }
        if (depth <= 0) {
            status.text = "END IF has no matching open IF."
            return
        }
        addStep(WorkflowStep.EndIf)
    }

    private fun confirmTemplateLearning() {
        val name = templateInput.text.toString().trim()
        val x1 = xInput.text.toString().toIntOrNull()
        val y1 = yInput.text.toString().toIntOrNull()
        val x2 = endXInput.text.toString().toIntOrNull()
        val y2 = endYInput.text.toString().toIntOrNull()
        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}")) ||
            x1 == null || y1 == null || x2 == null || y2 == null ||
            x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 ||
            kotlin.math.abs(x2 - x1) < 8 || kotlin.math.abs(y2 - y1) < 8
        ) {
            status.text = "Enter a valid template name and two screen coordinates defining a region at least 8×8 pixels."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Save a local image template?")
            .setMessage("The app will capture one screen after 8 seconds and save only the rectangle between the two coordinate points in private app storage. The image will not be uploaded.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save template") { _, _ ->
                if (AutomationAccessibilityService.armTemplateLearning(name, x1, y1, x2, y2)) {
                    status.text = "Template capture armed. Switch to the intended screen."
                } else {
                    status.text = "Accessibility service is not connected or the crop coordinates are invalid."
                }
            }
            .show()
    }

    private fun requestGeminiWorkflowDraft() {
        val key = try { CloudKeyStore.read(this) } catch (_: Exception) { null }
        if (key.isNullOrBlank()) {
            status.text = "Save a Gemini API key before generating a workflow draft."
            return
        }
        val goalInput = EditText(this).apply {
            hint = "Describe the workflow goal"
            minLines = 2
            setText("Open the target screen, wait until Ready appears, then continue.")
        }
        AlertDialog.Builder(this)
            .setTitle("Generate a workflow draft")
            .setMessage("Gemini will generate a JSON draft from your text description. It will not run automatically. Review all coordinates and steps before saving or starting.")
            .setView(goalInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Generate") { _, _ ->
                val goal = goalInput.text.toString().trim()
                if (goal.isBlank() || goal.length > 1_000) {
                    status.text = "Enter a goal between 1 and 1000 characters."
                    return@setPositiveButton
                }
                status.text = "Generating and validating a workflow draft…"
                Thread {
                    try {
                        val draft = GeminiCloudClient.generateWorkflowDraft(this, key, goal)
                        runOnUiThread {
                            val preview = draft.mapIndexed { index, step -> "${index + 1}. ${step.displayText()}" }.joinToString("\n")
                            AlertDialog.Builder(this)
                                .setTitle("Review generated workflow")
                                .setMessage(preview)
                                .setNegativeButton("Discard", null)
                                .setPositiveButton("Load draft") { _, _ ->
                                    replaceSteps(draft)
                                    status.text = "Draft loaded. Review every coordinate and condition before saving or running."
                                }
                                .show()
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            status.text = "Workflow draft rejected: ${error.localizedMessage ?: "Gemini or validation error"}"
                        }
                    }
                }.start()
            }
            .show()
    }

    private fun confirmGeminiVisualAnalysis() {
        if (!CloudKeyStore.hasKey(this)) {
            status.text = "Save a Gemini API key before requesting visual analysis."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Upload one screenshot to Gemini?")
            .setMessage(
                "After an 8-second switch window, one screenshot of the current screen will be sent to Google's Gemini API for visual analysis. " +
                    "Do not include passwords, private messages, payment details, or other sensitive information. " +
                    "Gemini suggestions are informational only; the app will not execute them."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I agree — analyze once") { _, _ ->
                if (AutomationAccessibilityService.armGeminiAnalysis()) {
                    status.text = "One screenshot will be captured and sent to Gemini in 8 seconds."
                } else {
                    status.text = "Accessibility service is not connected or Gemini key is unavailable."
                }
            }
            .show()
    }

    private fun showPendingGeminiAnalysis() {
        val store = getSharedPreferences("gemini_visual_result", MODE_PRIVATE)
        if (!store.getBoolean("ready", false)) return
        val result = store.getString("result", "No analysis available").orEmpty()
        val screenWidth = store.getInt("screen_width", 0)
        val screenHeight = store.getInt("screen_height", 0)
        store.edit().clear().apply()
        val candidates = try {
            val root = org.json.JSONObject(result)
            val array = root.optJSONArray("template_candidates") ?: org.json.JSONArray()
            (0 until minOf(array.length(), 5)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val bounds = item.optJSONArray("bounds") ?: return@mapNotNull null
                if (bounds.length() != 4) return@mapNotNull null
                val values = (0..3).map { bounds.optInt(it, -1) }
                if (values.any { it !in 0..1000 } ||
                    values[2] - values[0] < 8 || values[3] - values[1] < 8
                ) return@mapNotNull null
                item
            }
        } catch (_: Exception) { emptyList() }
        val view = TextView(this).apply {
            text = result
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(20, 16, 20, 16)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Gemini visual analysis")
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton("Close", null)
        if (candidates.isNotEmpty() && screenWidth > 0 && screenHeight > 0) {
            dialog.setNeutralButton("Review template suggestions") { _, _ ->
                reviewTemplateCandidates(candidates, screenWidth, screenHeight)
            }
        }
        dialog.show()
    }

    private fun reviewTemplateCandidates(
        candidates: List<org.json.JSONObject>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val labels = candidates.mapIndexed { index, item ->
            "${index + 1}. ${item.optString("label", "Visible UI element").take(60)}"
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a template to learn")
            .setMessage("Gemini only suggests approximate image regions. Select one to review its crop; nothing is saved until you confirm.")
            .setItems(labels.toTypedArray()) { _, selected ->
                val item = candidates[selected]
                val bounds = item.optJSONArray("bounds") ?: return@setItems
                val left = (bounds.optInt(0) * screenWidth / 1000f).toInt().coerceIn(0, screenWidth)
                val top = (bounds.optInt(1) * screenHeight / 1000f).toInt().coerceIn(0, screenHeight)
                val right = (bounds.optInt(2) * screenWidth / 1000f).toInt().coerceIn(0, screenWidth)
                val bottom = (bounds.optInt(3) * screenHeight / 1000f).toInt().coerceIn(0, screenHeight)
                if (right - left < 8 || bottom - top < 8) {
                    status.text = "Suggested crop is too small. Select the region manually instead."
                    return@setItems
                }
                val nameInput = EditText(this).apply {
                    hint = "Local template name"
                    setSingleLine(true)
                    setText(item.optString("label", "learned_template")
                        .replace(Regex("[^A-Za-z0-9_-]"), "_").take(40))
                }
                AlertDialog.Builder(this)
                    .setTitle("Confirm local template")
                    .setMessage(
                        "Suggested region: left $left, top $top, right $right, bottom $bottom. " +
                            "Return to the same screen before capture. The crop is stored only on this device and is not uploaded."
                    )
                    .setView(nameInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Capture and save") { _, _ ->
                        val name = nameInput.text.toString().trim()
                        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}"))) {
                            status.text = "Use 1–40 letters, numbers, underscores or hyphens for the template name."
                        } else if (AutomationAccessibilityService.armTemplateLearning(
                                name, left, top, right, bottom
                            )
                        ) {
                            status.text = "Template suggestion accepted. Return to the same screen; a local crop will be captured after 8 seconds."
                        } else {
                            status.text = "Accessibility service is not connected or the suggested crop is invalid."
                        }
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleCooldownReminder() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7303)
            status.text = "Allow notifications, then schedule the reminder again."
            return
        }
        val input = EditText(this).apply {
            hint = "Minutes from now / repeat interval"
            setSingleLine(true)
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val recurring = CheckBox(this).apply {
            text = "Repeat reminder (minimum 15 minutes, maximum 7 days)"
            isChecked = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(input, matchWidth())
            addView(recurring, matchWidth())
        }
        AlertDialog.Builder(this)
            .setTitle("Schedule profile reminder")
            .setMessage("A reminder never starts automation. When it arrives, review the saved profile and explicitly confirm any run. Repeating reminders are inexact and may be delayed by Android battery management.")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Schedule") { _, _ ->
                val minutes = input.text.toString().toLongOrNull()
                val isRecurring = recurring.isChecked
                val validMinutes = minutes != null && if (isRecurring) {
                    minutes in 15L..10_080L
                } else {
                    minutes in 1L..1_440L
                }
                val profileName = profileInput.text.toString().trim()
                if (!validMinutes || profileName.isBlank() ||
                    preferences.getString("workflow.$profileName", null) == null
                ) {
                    status.text = if (isRecurring) {
                        "Enter a 15–10080 minute interval and an existing saved profile name."
                    } else {
                        "Enter 1–1440 minutes and the exact name of an existing saved profile."
                    }
                    return@setPositiveButton
                }
                val alarm = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                val intent = Intent(this, SchedulerReceiver::class.java)
                    .putExtra("profile_name", profileName)
                    .putExtra("recurring", isRecurring)
                val pending = android.app.PendingIntent.getBroadcast(
                    this, SCHEDULER_REQUEST, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + minutes!! * 60_000L
                val state = getSharedPreferences("scheduler_state", MODE_PRIVATE)
                state.edit()
                    .putBoolean("enabled", true)
                    .putBoolean("recurring", isRecurring)
                    .putLong("interval_minutes", if (isRecurring) minutes else 0L)
                    .putString("profile_name", profileName)
                    .putLong("trigger_at", triggerAt)
                    .apply()
                if (isRecurring) {
                    alarm.setInexactRepeating(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        minutes * 60_000L,
                        pending
                    )
                } else {
                    alarm.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pending
                    )
                }
                status.text = if (isRecurring) {
                    "Repeating reminder set every $minutes minutes for '$profileName'. It will be restored after reboot."
                } else {
                    "One-shot reminder scheduled for '$profileName' in about $minutes minutes."
                }
            }
            .show()
    }

    private fun addStep(step: WorkflowStep) {
        if (steps.size >= 50) {
            status.text = "Workflow limit reached: 50 steps."
            return
        }
        steps.add(step)
        renderSteps()
        status.text = "Step added."
    }

    private fun readPoint(): Pair<Float, Float>? {
        val x = xInput.text.toString().toFloatOrNull() ?: return null
        val y = yInput.text.toString().toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite() || x !in 0f..20_000f || y !in 0f..20_000f) return null
        return x to y
    }

    private fun manageStep() {
        if (steps.isEmpty()) {
            status.text = "There are no steps to manage."
            return
        }
        val labels = steps.mapIndexed { index, step -> "${index + 1}. ${step.displayText()}" }
        AlertDialog.Builder(this)
            .setTitle("Select a step")
            .setItems(labels.toTypedArray()) { _, index -> showStepActions(index) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showStepActions(index: Int) {
        val actions = mutableListOf<String>()
        val actionIds = mutableListOf<Int>()
        if (index > 0) {
            actions.add("Move up")
            actionIds.add(0)
        }
        if (index < steps.lastIndex) {
            actions.add("Move down")
            actionIds.add(1)
        }
        actions.add("Delete step")
        actionIds.add(2)

        AlertDialog.Builder(this)
            .setTitle(steps[index].displayText())
            .setItems(actions.toTypedArray()) { _, selected ->
                when (actionIds[selected]) {
                    0 -> {
                        val item = steps.removeAt(index)
                        steps.add(index - 1, item)
                        status.text = "Step moved up."
                    }
                    1 -> {
                        val item = steps.removeAt(index)
                        steps.add(index + 1, item)
                        status.text = "Step moved down."
                    }
                    2 -> {
                        steps.removeAt(index)
                        status.text = "Step deleted."
                    }
                }
                renderSteps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmStart() {
        if (!WorkflowStep.validateStructure(steps)) {
            status.text = "Complete every IF block with a matching END IF before running."
            return
        }
        val repeat = repeatInput.text.toString().toIntOrNull()
            ?.takeIf { it in 1..20 } ?: return invalidInput()
        val description = steps.mapIndexed { index, step ->
            "${index + 1}. ${step.displayText()}"
        }.joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("Confirm workflow")
            .setMessage(
                "Run ${steps.size} steps, repeated ${repeat} time(s)?\n\n" +
                    description +
                    "\n\nThe first action starts after 5 seconds. Press Stop in this app to cancel."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm and run") { _, _ ->
                if (AutomationAccessibilityService.start(steps.toList(), repeat)) {
                    status.text = "Workflow armed. Switch to the intended screen; it starts in 5 seconds."
                } else {
                    status.text = "Service is not connected or the workflow settings are invalid."
                }
            }
            .show()
    }

    private fun saveProfile() {
        val name = profileInput.text.toString().trim()
        if (name.isEmpty() || steps.isEmpty()) {
            status.text = "Enter a profile name and add at least one step."
            return
        }
        if (!WorkflowStep.validateStructure(steps)) {
            status.text = "Complete every IF block with a matching END IF before saving."
            return
        }
        val encoded = steps.joinToString("\n") { it.encode() }
        preferences.edit().putString("workflow.$name", encoded).apply()
        status.text = "Workflow saved locally: $name"
    }

    private fun loadProfile() {
        val name = profileInput.text.toString().trim()
        val encoded = preferences.getString("workflow.$name", null)
        if (name.isEmpty() || encoded == null) {
            status.text = "Workflow not found. Enter the exact saved profile name."
            return
        }
        val decoded = decodeWorkflow(encoded)
        if (decoded == null) {
            status.text = "Saved workflow is invalid. The current workflow was not changed."
            return
        }
        replaceSteps(decoded)
        status.text = "Workflow loaded: $name"
    }


    private fun confirmProfileQueue() {
        val names = preferences.all.keys
            .filter { it.startsWith("workflow.") }
            .map { it.removePrefix("workflow.") }
            .filter { it.isNotBlank() }
            .sorted()
        if (names.isEmpty()) {
            status.text = "Save at least one workflow profile before starting a queue."
            return
        }
        if (names.size > 20) {
            status.text = "Profile queue is limited to 20 workflows. Remove or export extra profiles first."
            return
        }
        val queue = names.mapNotNull { name ->
            preferences.getString("workflow.$name", null)?.let(::decodeWorkflow)
        }
        if (queue.size != names.size || queue.any { it.isEmpty() }) {
            status.text = "One or more saved profiles are invalid. Review them before running the queue."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Confirm profile queue")
            .setMessage(
                "Run all ${names.size} saved profiles in alphabetical order, one after another?\n\n" +
                    names.joinToString("\n") { "• $it" } +
                    "\n\nThe queue starts after a 5-second delay. You can stop it at any time."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start queue") { _, _ ->
                val repeat = repeatInput.text.toString().toIntOrNull()
                    ?.takeIf { it in 1..20 } ?: 1
                if (AutomationAccessibilityService.startQueue(queue, repeat)) {
                    status.text = "Profile queue armed: ${names.size} workflows. Switch to the intended app."
                } else {
                    status.text = "Accessibility service is not connected or the queue is invalid."
                }
            }
            .show()
    }

    private fun manageSavedWorkflows() {
        val names = preferences.all.keys
            .filter { it.startsWith("workflow.") }
            .map { it.removePrefix("workflow.") }
            .filter { it.isNotBlank() }
            .sorted()

        if (names.isEmpty()) {
            status.text = "No saved workflows on this device yet."
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Saved workflows (${names.size})")
            .setItems(names.toTypedArray()) { _, index ->
                val selected = names[index]
                AlertDialog.Builder(this)
                    .setTitle(selected)
                    .setItems(arrayOf("Load", "Delete")) { _, action ->
                        when (action) {
                            0 -> {
                                profileInput.setText(selected)
                                loadProfile()
                            }
                            1 -> confirmDeleteWorkflow(selected)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmDeleteWorkflow(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete saved workflow?")
            .setMessage("Delete '$name' from this device? This does not affect exported files.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                preferences.edit().remove("workflow.$name").apply()
                status.text = "Deleted saved workflow: $name"
            }
            .show()
    }

    private fun exportWorkflow() {
        if (steps.isEmpty()) {
            status.text = "Add at least one step before exporting."
            return
        }
        if (!WorkflowStep.validateStructure(steps)) {
            status.text = "Workflow has an unmatched IF / ELSE / END IF block. Fix it before exporting."
            return
        }
        pendingExport = WorkflowJsonCodec.encode(steps)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "${safeFileName(profileInput.text.toString())}.aiaworkflow")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun importWorkflow() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Uses document picker result for compatibility with the current minimum SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) {
            pendingExport = null
            pendingTemplateName = null
            return
        }
        val uri = data.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> writeWorkflow(uri)
            REQUEST_IMPORT -> readWorkflow(uri)
            REQUEST_TEMPLATE -> importTemplateFromUri(uri)
        }
    }

    private fun importImageTemplate() {
        val name = templateInput.text.toString().trim()
        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}"))) {
            status.text = "Use a template name containing 1–40 letters, digits, underscores, or hyphens."
            return
        }
        pendingTemplateName = name
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_TEMPLATE)
    }

    private fun importTemplateFromUri(uri: Uri) {
        val name = pendingTemplateName ?: return
        pendingTemplateName = null
        try {
            val saved = TemplateStore.import(this, uri, name)
            status.text = "Image template saved locally: $saved"
        } catch (error: Exception) {
            status.text = "Image template import failed: ${error.localizedMessage ?: "invalid image"}"
        }
    }

    private fun manageImageTemplates() {
        val names = TemplateStore.names(this)
        if (names.isEmpty()) {
            status.text = "No image templates saved yet."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Local image templates")
            .setItems(names.toTypedArray()) { _, index ->
                val name = names[index]
                AlertDialog.Builder(this)
                    .setTitle(name)
                    .setItems(arrayOf("Use for image condition", "Delete template")) { _, action ->
                        when (action) {
                            0 -> {
                                templateInput.setText(name)
                                status.text = "Selected image template: $name"
                            }
                            1 -> AlertDialog.Builder(this)
                                .setMessage("Delete local image template '$name'? Existing workflows using it will stop if the template is missing.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ ->
                                    TemplateStore.delete(this, name)
                                    status.text = "Deleted image template: $name"
                                }
                                .show()
                        }
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addWaitForImage() {
        val name = templateInput.text.toString().trim()
        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}"))) {
            status.text = "Enter a valid image template name."
            return
        }
        if (name !in TemplateStore.names(this)) {
            status.text = "Import this image template before adding its condition."
            return
        }
        addStep(WorkflowStep.WaitForImage(name, 30_000L, 2_000L, 0.85f))
    }

    private fun writeWorkflow(uri: Uri) {
        val payload = pendingExport ?: return
        pendingExport = null
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
                ?: throw IllegalStateException("Unable to open destination")
            status.text = "Workflow exported. Keep the file somewhere you can find it."
        } catch (error: Exception) {
            status.text = "Export failed: ${error.localizedMessage ?: "storage error"}"
        }
    }

    private fun readWorkflow(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                readBounded(reader, MAX_IMPORT_CHARS)
            } ?: throw IllegalStateException("Unable to open selected file")
            if (content.length > MAX_IMPORT_CHARS) {
                status.text = "Import rejected: file is too large."
                return
            }
            val decoded = decodeWorkflow(content)
            if (decoded == null) {
                status.text = "Import rejected: unsupported or invalid workflow file."
                return
            }
            replaceSteps(decoded)
            status.text = "Workflow imported successfully. Review the steps before running."
        } catch (error: Exception) {
            status.text = "Import failed: ${error.localizedMessage ?: "file error"}"
        }
    }

    private fun readBounded(reader: java.io.Reader, maxChars: Int): String {
        val output = StringBuilder()
        val buffer = CharArray(1024)
        while (output.length <= maxChars) {
            val remaining = maxChars + 1 - output.length
            if (remaining <= 0) break
            val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun decodeWorkflow(content: String): List<WorkflowStep>? {
        if (content.trimStart().startsWith("{")) return WorkflowJsonCodec.decode(content)
        val lines = content.trim().lines()
        if (lines.size !in 2..51 || lines.first() != "AIA_WORKFLOW_V1") return null
        val decoded = lines.drop(1).map { WorkflowStep.decode(it) ?: return null }
        return decoded.takeIf { WorkflowStep.validateStructure(it) }
    }

    private fun replaceSteps(newSteps: List<WorkflowStep>) {
        steps.clear()
        steps.addAll(newSteps)
        renderSteps()
    }

    private fun safeFileName(value: String): String {
        val cleaned = value.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return cleaned.ifEmpty { "workflow" }.take(40)
    }

    private fun renderSteps() {
        stepsView.text = if (steps.isEmpty()) {
            "Workflow steps: none"
        } else {
            "Workflow steps (${steps.size}/50):\n" +
                steps.mapIndexed { index, step -> "${index + 1}. ${step.displayText()}" }
                    .joinToString("\n")
        }
    }

    private fun invalidInput() {
        status.text = "Invalid input. Check coordinate and duration limits."
    }

    private fun refreshStatus() {
        status.text = if (AutomationAccessibilityService.isConnected()) {
            "Accessibility service: connected"
        } else {
            "Accessibility service: not connected"
        }
    }

    private fun field(hintText: String, initial: String) = EditText(this).apply {
        hint = hintText
        setText(initial)
        setSingleLine(true)
        textSize = 16f
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val SCHEDULER_REQUEST = 7301
        private const val REQUEST_EXPORT = 4101
        private const val REQUEST_IMPORT = 4102
        private const val REQUEST_TEMPLATE = 4103
        private const val MAX_IMPORT_CHARS = 20_000
    }
}
