package com.syhung.androidaiautomation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

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
    private lateinit var stepsView: TextView
    private val steps = mutableListOf<WorkflowStep>()

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

        listOf(
            profileInput, xInput, yInput, endXInput, endYInput,
            durationInput, waitInput, repeatInput
        ).forEach { root.addView(it, matchWidth()) }

        root.addView(button("Add tap step") { addTap() }, matchWidth())
        root.addView(button("Add swipe step") { addSwipe() }, matchWidth())
        root.addView(button("Add wait step") { addWait() }, matchWidth())
        root.addView(button("Remove last step") {
            if (steps.isNotEmpty()) steps.removeAt(steps.lastIndex)
            renderSteps()
        }, matchWidth())
        root.addView(button("Clear workflow") {
            steps.clear()
            renderSteps()
        }, matchWidth())

        stepsView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 14, 0, 14)
        }
        root.addView(stepsView, matchWidth())
        renderSteps()

        root.addView(button("Save workflow on this device") { saveProfile() }, matchWidth())
        root.addView(button("Load workflow") { loadProfile() }, matchWidth())
        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth())
        root.addView(button("Run workflow") { confirmStart() }, matchWidth())
        root.addView(button("Stop") {
            AutomationAccessibilityService.stop()
            status.text = "Stopped. Pending start and remaining steps were cancelled."
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Local workflow editor: build a sequence of taps, swipes and waits. Workflows are stored only on this device. Each run requires confirmation and has a 5-second countdown. Limit: 50 steps and 20 repeats. Stop cancels the sequence. This build does not read screen contents, use OCR, capture the screen or connect to a cloud AI service. Use only with apps and screens you are authorized to control."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        }, matchWidth())

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
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
            ?.takeIf { it >= 0f } ?: return invalidInput()
        val endY = endYInput.text.toString().toFloatOrNull()
            ?.takeIf { it >= 0f } ?: return invalidInput()
        val duration = durationInput.text.toString().toLongOrNull()
            ?.takeIf { it in 50L..10_000L } ?: return invalidInput()
        addStep(WorkflowStep.Swipe(start.first, start.second, endX, endY, duration))
    }

    private fun addWait() {
        val duration = waitInput.text.toString().toLongOrNull()
            ?.takeIf { it in 100L..60_000L } ?: return invalidInput()
        addStep(WorkflowStep.Wait(duration))
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
        if (x < 0f || y < 0f) return null
        return x to y
    }

    private fun confirmStart() {
        if (steps.isEmpty()) {
            status.text = "Add at least one workflow step first."
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
        val decoded = encoded.lines().mapNotNull { WorkflowStep.decode(it) }
        if (decoded.isEmpty() || decoded.size > 50) {
            status.text = "Saved workflow is invalid or exceeds the 50-step limit."
            return
        }
        steps.clear()
        steps.addAll(decoded)
        renderSteps()
        status.text = "Workflow loaded: $name"
    }

    private fun renderSteps() {
        stepsView.text = if (steps.isEmpty()) {
            "Workflow steps: none"
        } else {
            "Workflow steps ( ${steps.size}/50 ):\n" +
                steps.mapIndexed { index, step -> "${index + 1}. ${step.displayText()}" }
                    .joinToString("\n")
        }
    }

    private fun invalidInput() {
        status.text = "Invalid input. Coordinates must be non-negative; check the duration limits."
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
}
