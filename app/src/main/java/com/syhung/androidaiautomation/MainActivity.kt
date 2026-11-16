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

        listOf(
            profileInput, xInput, yInput, endXInput, endYInput,
            durationInput, waitInput, repeatInput
        ).forEach { root.addView(it, matchWidth()) }

        root.addView(button("Add tap step") { addTap() }, matchWidth())
        root.addView(button("Add swipe step") { addSwipe() }, matchWidth())
        root.addView(button("Add wait step") { addWait() }, matchWidth())
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
        root.addView(button("Export workflow file") { exportWorkflow() }, matchWidth())
        root.addView(button("Import workflow file") { importWorkflow() }, matchWidth())
        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth())
        root.addView(button("Run workflow") { confirmStart() }, matchWidth())
        root.addView(button("Stop") {
            AutomationAccessibilityService.stop()
            status.text = "Stopped. Pending start and remaining steps were cancelled."
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Local workflow editor: create, reorder, save, import and export tap, swipe and wait steps. Workflows stay on this device unless you explicitly export a file. Each run requires confirmation and has a 5-second countdown. Limit: 50 steps and 20 repeats. This build does not read screen contents, use OCR, capture the screen or connect to a cloud AI service. Use only with apps and screens you are authorized to control."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        }, matchWidth())

        setContentView(ScrollView(this).apply { addView(root) })
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
        val decoded = decodeWorkflow(encoded)
        if (decoded == null) {
            status.text = "Saved workflow is invalid. The current workflow was not changed."
            return
        }
        replaceSteps(decoded)
        status.text = "Workflow loaded: $name"
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
        pendingExport = (listOf("AIA_WORKFLOW_V1") + steps.map { it.encode() }).joinToString("\n")
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
            return
        }
        val uri = data.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> writeWorkflow(uri)
            REQUEST_IMPORT -> readWorkflow(uri)
        }
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
        val lines = content.trim().lines()
        if (lines.size !in 2..51 || lines.first() != "AIA_WORKFLOW_V1") return null
        val decoded = lines.drop(1).map { WorkflowStep.decode(it) ?: return null }
        return decoded.takeIf { it.isNotEmpty() && it.size <= 50 }
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
        private const val REQUEST_EXPORT = 4101
        private const val REQUEST_IMPORT = 4102
        private const val MAX_IMPORT_CHARS = 20_000
    }
}
