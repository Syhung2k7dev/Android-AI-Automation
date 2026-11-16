package com.syhung.androidaiautomation

import android.app.Activity
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
    private lateinit var xInput: EditText
    private lateinit var yInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var countInput: EditText

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

        xInput = field("Tap X coordinate", "540")
        yInput = field("Tap Y coordinate", "900")
        intervalInput = field("Interval in milliseconds (minimum 500)", "1500")
        countInput = field("Maximum taps (1–1000)", "20")

        listOf(xInput, yInput, intervalInput, countInput).forEach { root.addView(it, matchWidth()) }

        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWidth())

        root.addView(button("Start tapping") {
            val x = xInput.text.toString().toFloatOrNull()
            val y = yInput.text.toString().toFloatOrNull()
            val interval = intervalInput.text.toString().toLongOrNull()
            val count = countInput.text.toString().toIntOrNull()
            if (x == null || y == null || interval == null || count == null ||
                x < 0 || y < 0 || interval < 500 || count !in 1..1000) {
                status.text = "Check the values. Interval must be at least 500 ms and tap count 1–1000."
            } else if (!AutomationAccessibilityService.start(x, y, interval, count)) {
                status.text = "Enable this app's Accessibility service first."
            } else {
                status.text = "Running. Use Stop to end the tap sequence."
            }
        }, matchWidth())

        root.addView(button("Stop") {
            AutomationAccessibilityService.stop()
            status.text = "Stopped."
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "This first build performs taps only at the coordinates you enter. Keep the phone visible and use it only on screens and apps you are authorized to automate. No anti-cheat bypass or hidden operation is included."
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
