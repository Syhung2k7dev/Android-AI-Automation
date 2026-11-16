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
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var profileInput: EditText
    private lateinit var xInput: EditText
    private lateinit var yInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var countInput: EditText

    private val preferences by lazy {
        getSharedPreferences("automation_profiles", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 36, 32, 24)
        }

        root.addView(TextView(this).apply {
            text = "Android AI Automation"
            textSize = 24f
        }, matchWidth())

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 18, 0, 18)
        }
        root.addView(status, matchWidth())

        profileInput = field("Profile name", "My first profile")
        xInput = field("Tap X coordinate", "540")
        yInput = field("Tap Y coordinate", "900")
        intervalInput = field("Interval in milliseconds (minimum 500)", "1500")
        countInput = field("Maximum taps (1–1000)", "20")

        listOf(profileInput, xInput, yInput, intervalInput, countInput)
            .forEach { root.addView(it, matchWidth()) }

        root.addView(button("Save profile") {
            saveProfile()
        }, matchWidth())

        root.addView(button("Load profile") {
            loadProfile()
        }, matchWidth())

        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth())

        root.addView(button("Start tapping") {
            val values = readValues()
            if (values == null) {
                status.text = "Invalid values. Check coordinates, interval and tap count."
            } else {
                confirmStart(values)
            }
        }, matchWidth())

        root.addView(button("Stop") {
            AutomationAccessibilityService.stop()
            status.text = "Stopped. Any pending start countdown was cancelled."
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Privacy and control: profiles stay on this device. This build does not request internet access or read screen contents. Starting requires confirmation and has a 5-second switch-to-target countdown. The tap sequence is finite (1–1,000 taps); return to this app and press Stop to cancel it. Use only on screens and in apps you are authorized to control. Image recognition, OCR, visual point selection, scheduling and AI recovery are not included in this build."
            textSize = 13f
            setPadding(0, 20, 0, 0)
        }, matchWidth())

        setContentView(root)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun confirmStart(values: TapValues) {
        AlertDialog.Builder(this)
            .setTitle("Confirm tap sequence")
            .setMessage(
                "The app will send \${values.count} taps at X=\${values.x}, Y=\${values.y}, " +
                    "with \${values.interval} ms between taps. The first tap starts after 5 seconds, " +
                    "so you can switch to the intended screen. This action does not inspect screen content. " +
                    "Return to Android AI Automation and press Stop to cancel."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand — start") { _, _ ->
                if (!AutomationAccessibilityService.start(
                        values.x, values.y, values.interval, values.count
                    )) {
                    status.text = "Accessibility service is not connected. Enable it in Android Settings."
                } else {
                    status.text = "Armed. Switch to the intended screen now; the first tap starts in 5 seconds."
                }
            }
            .show()
    }

    private fun readValues(): TapValues? {
        val x = xInput.text.toString().toFloatOrNull() ?: return null
        val y = yInput.text.toString().toFloatOrNull() ?: return null
        val interval = intervalInput.text.toString().toLongOrNull() ?: return null
        val count = countInput.text.toString().toIntOrNull() ?: return null

        if (x < 0 || y < 0 || interval < 500 || count !in 1..1000) return null
        return TapValues(x, y, interval, count)
    }

    private fun saveProfile() {
        val name = profileInput.text.toString().trim()
        val values = readValues()
        if (name.isEmpty() || values == null) {
            status.text = "Enter a profile name and valid tap settings before saving."
            return
        }

        preferences.edit()
            .putFloat("\${name}.x", values.x)
            .putFloat("\${name}.y", values.y)
            .putLong("\${name}.interval", values.interval)
            .putInt("\${name}.count", values.count)
            .putString("last_profile", name)
            .apply()

        status.text = "Profile saved on this device: $name"
    }

    private fun loadProfile() {
        val name = profileInput.text.toString().trim()
        if (name.isEmpty() || !preferences.contains("\${name}.x")) {
            status.text = "Profile not found. Enter the exact name of a saved profile."
            return
        }

        xInput.setText(preferences.getFloat("\${name}.x", 540f).toString())
        yInput.setText(preferences.getFloat("\${name}.y", 900f).toString())
        intervalInput.setText(preferences.getLong("\${name}.interval", 1500L).toString())
        countInput.setText(preferences.getInt("\${name}.count", 20).toString())
        status.text = "Profile loaded: $name"
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

    private data class TapValues(
        val x: Float,
        val y: Float,
        val interval: Long,
        val count: Int
    )
}
