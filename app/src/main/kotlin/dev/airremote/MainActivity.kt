// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/** One-button controls. The temporary Bluetooth session can interrupt Google Health syncing. */
class MainActivity : Activity() {
    private val ink = Color.rgb(235, 242, 249)
    private val muted = Color.rgb(154, 172, 190)
    private val accent = Color.rgb(104, 230, 196)
    private val panel = Color.rgb(22, 35, 50)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var sendButton: Button
    private var activeSource: String? = null
    private var pendingCommand: Intent? = null
    private val poll = object : Runnable {
        override fun run() {
            refreshResult()
            handler.postDelayed(this, 600)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 20, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(11, 20, 31)
        window.navigationBarColor = Color.rgb(11, 20, 31)
        activeSource = state?.getString("active_source")
        buildScreen()
        if (activeSource != null) showSending()
        if (state == null && intent.hasExtra("pattern")) execute(intent)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(34), dp(24), dp(32))
            setBackgroundColor(Color.rgb(11, 20, 31))
        }
        root.addView(label("FITBIT AIR", 12f, accent, true).apply { letterSpacing = 0.18f })
        space(root, 10)
        root.addView(label("Air Remote", 34f, ink, true))
        space(root, 8)
        root.addView(label("Tap to vibrate your Fitbit Air.", 16f, muted))
        space(root, 28)
        val device = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = shape(panel)
        }
        device.addView(label("YOUR BAND", 11f, muted, true))
        space(device, 8)
        device.addView(label("Google Fitbit Air", 20f, ink, true))
        space(device, 5)
        device.addView(label("Uses your paired Fitbit over Bluetooth.", 14f, muted))
        root.addView(device)
        space(root, 30)
        sendButton =
            Button(this).apply {
                text = "Vibrate"
                isAllCaps = false
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(10, 31, 29))
                background = shape(accent, 24)
                stateListAnimator = null
                setOnClickListener { execute(Intent().putExtra("pattern", "single")) }
            }
        root.addView(sendButton, LinearLayout.LayoutParams(-1, dp(110)))
        space(root, 24)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = shape(panel)
        }
        statusTitle = label("Ready when you are", 17f, accent, true)
        statusBody =
            label("Keep your Fitbit nearby, then tap Vibrate.", 14f, muted).apply {
                setPadding(0, dp(6), 0, 0)
            }
        card.addView(statusTitle)
        card.addView(statusBody)
        root.addView(card)
        space(root, 24)
        root.addView(label("A command may briefly interrupt Google Health syncing.", 13f, muted))
        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(root)
                setOnApplyWindowInsetsListener { _, insets ->
                    root.setPadding(
                        dp(24),
                        insets.systemWindowInsetTop + dp(24),
                        dp(24),
                        insets.systemWindowInsetBottom + dp(24)
                    )
                    insets
                }
            }
        )
    }

    override fun onNewIntent(command: Intent) {
        super.onNewIntent(command)
        setIntent(command)
        if (command.hasExtra("pattern")) execute(command)
    }
    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }
    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("active_source", activeSource)
        super.onSaveInstanceState(state)
    }
    private fun showSending() {
        statusTitle.text = "Sending your vibration…"
        statusTitle.setTextColor(accent)
        statusBody.text = "Keep your band nearby. Waiting for confirmation."
        sendButton.isEnabled = false
        sendButton.alpha = 0.65f
        sendButton.text = "Sending…"
    }
    private fun execute(command: Intent) {
        try {
            val name = command.getStringExtra("pattern") ?: return
            val pattern =
                VibrationPattern.fromWireName(
                    name
                ) ?: throw IllegalArgumentException("Choose a valid pattern.")
            if (!BluetoothPermissions.hasAll(this)) {
                pendingCommand = command
                statusTitle.text = "Allow Bluetooth access"
                statusBody.text = "Nearby device access lets Air Remote connect to your band."
                BluetoothPermissions.request(this, 401)
                return
            }
            val adapter = getSystemService(BluetoothManager::class.java).adapter
            require(adapter.isEnabled) { "Turn on Bluetooth, then try again." }
            val targets = adapter.bondedDevices.filter { it.name == "Google Fitbit Air" }
            require(targets.size == 1) { "Pair your Google Fitbit Air with this phone first." }
            DeviceStore.saveDevice(this, targets.single())
            val requestId =
                command.getStringExtra(
                    "request_id"
                ) ?: UUID.randomUUID().toString().replace("-", "")
            require(requestId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid request ID." }
            val source = "local.command.$requestId"
            check(VibrationService.enqueue(this, VibrationCommand(source, pattern))) {
                "Could not start the command. Try again."
            }
            activeSource = source
            showSending()
            Log.i("AirDirectCommand", "Queued request=$requestId pattern=$name")
        } catch (error: Exception) {
            showFinished(
                false,
                error.message ?: "Keep your band nearby and try again."
            )
            Log.e("AirDirectCommand", "Command failed", error)
        }
    }
    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(code, permissions, results)
        val command = pendingCommand
        pendingCommand = null
        if (code == 401 && command != null && BluetoothPermissions.hasAll(this)) {
            execute(command)
        } else if (code == 401) {
            showFinished(
                false,
                "Allow Nearby devices in Android Settings to connect to your Fitbit."
            )
        }
    }
    private fun refreshResult() {
        val source = activeSource ?: return
        val result = DeviceStore.lastResult(this)
        if (result.startsWith("success source=$source ")) {
            showFinished(true, "Your Fitbit vibrated. Tap again whenever you like.")
        } else if (result.startsWith("failure source=$source:")) {
            showFinished(
                false,
                "The band may be busy or out of range. Wait a moment and try again."
            )
        }
    }
    private fun showFinished(success: Boolean, detail: String) {
        activeSource = null
        statusTitle.text =
            if (success) "Vibration sent" else "Couldn’t send the vibration"
        statusTitle.setTextColor(if (success) accent else Color.rgb(255, 186, 161))
        statusBody.text = detail
        sendButton.isEnabled = true
        sendButton.alpha = 1f
        sendButton.text = "Vibrate"
    }
}
