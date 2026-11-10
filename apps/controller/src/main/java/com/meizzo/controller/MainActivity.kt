package com.meizzo.controller

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val preferences by lazy { getPreferences(MODE_PRIVATE) }
    private val secureStore by lazy { SecureStore(this) }
    private val controllerId by lazy {
        preferences.getString("controller_id", null) ?: "controller-${UUID.randomUUID()}".also {
            preferences.edit().putString("controller_id", it).apply()
        }
    }
    private lateinit var codeInput: EditText
    private lateinit var connectionLabel: TextView
    private lateinit var summaryLabel: TextView
    private lateinit var devicesLayout: LinearLayout
    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, JSONObject>()
    private val approvedInteractionSessions = mutableMapOf<String, String>()
    private val updateManager by lazy { ControllerUpdateManager(this) }
    private var pendingUpdateApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(Color.rgb(8, 8, 10))
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.meizzo_logo)
            contentDescription = "MEIZZO logo"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(-1, dp(76)))
        content.addView(TextView(this).apply {
            text = "MEIZZO CONTROLLER"
            textSize = 24f
            letterSpacing = 0.1f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(18), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Your connected devices, at a glance."
            textSize = 14f
            setTextColor(Color.rgb(155, 155, 163))
        })

        val overview = uiCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        overview.addView(uiEyebrow("CONNECTION"))
        connectionLabel = TextView(this).apply {
            text = "● Connecting to MEIZZO"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
            setTextColor(Color.rgb(220, 190, 120))
        }
        overview.addView(connectionLabel)
        summaryLabel = TextView(this).apply {
            text = "Loading device inventory…"
            textSize = 13f
            setTextColor(Color.rgb(165, 165, 173))
        }
        overview.addView(summaryLabel)
        content.addView(overview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        val pairing = uiCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        pairing.addView(uiEyebrow("PAIR A DEVICE"))
        codeInput = EditText(this).apply {
            hint = "Enter six-digit pairing code"
            inputType = 2
            maxLines = 1
            textSize = 16f
            setHintTextColor(Color.rgb(115, 115, 123))
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(10))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(85, 85, 94))
        }
        pairing.addView(codeInput)
        pairing.addView(uiActionButton("Add device", true) { requestPairing() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        content.addView(pairing, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        content.addView(uiActionButton("Check for updates", false) { checkForUpdate() },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })

        content.addView(uiEyebrow("YOUR DEVICES").apply {
            setPadding(dp(2), dp(28), 0, dp(8))
        })
        devicesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(devicesLayout)
        content.addView(TextView(this).apply {
            text = "MEIZZO  ·  SECURE DEVICE MANAGEMENT"
            textSize = 10f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 105, 113))
            setPadding(0, dp(28), 0, dp(8))
        })
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            setBackgroundColor(Color.rgb(8, 8, 10))
        })
        renderDevices()
        connect()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun uiCard(): LinearLayout = LinearLayout(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(20, 20, 24))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(42, 42, 48))
        }
    }

    private fun uiEyebrow(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 10f
        letterSpacing = 0.16f
        setTextColor(Color.rgb(145, 145, 154))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun uiActionButton(label: String, primary: Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.rgb(12, 12, 14) else Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (primary) Color.WHITE else Color.rgb(27, 27, 32))
                cornerRadius = dp(14).toFloat()
                if (!primary) setStroke(dp(1), Color.rgb(55, 55, 62))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    override fun onResume() {
        super.onResume()
        val pending = pendingUpdateApk ?: return
        if (updateManager.canInstallPackages()) {
            AlertDialog.Builder(this)
                .setTitle("Update ready to install")
                .setMessage("MEIZZO has permission to start the Android installer. Confirm installation to continue.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Install") { _, _ ->
                    pendingUpdateApk = null
                    updateManager.install(pending)
                }
                .show()
        } else {
            setConnection("Install permission is required to continue.", false)
            AlertDialog.Builder(this)
                .setTitle("Allow app updates")
                .setMessage("Enable 'Allow from this source' for MEIZZO in Android settings, then return here.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Open settings") { _, _ -> updateManager.requestInstallPermission() }
                .show()
        }
    }

    private fun connect() {
        connectionLabel.text = "● Connecting to MEIZZO"
        connectionLabel.setTextColor(Color.rgb(238, 190, 100))
        socket = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
            .newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    reconnectScheduled = false
                    runOnUiThread {
                        connectionLabel.text = "● Connected to MEIZZO"
                        connectionLabel.setTextColor(Color.rgb(120, 210, 170))
                    }
                    authenticateExisting(webSocket)
                    requestDeviceList(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "controller.status" -> setConnection("Connected", true)
                        "controller.pairing_pending" -> runOnUiThread {
                            connectionLabel.text = "● Connected · waiting for Client approval"
                        }
                        "controller.pairing_result" -> handlePairingResult(message)
                        "auth.connected" -> requestDeviceList(webSocket)
                        "controller.device_list", "device.status" -> {
                            if (message.optString("type") == "controller.device_list") {
                                val list = message.optJSONArray("devices") ?: return
                                for (index in 0 until list.length()) updateDevice(list.getJSONObject(index))
                            } else updateDevice(message)
                            renderDevices()
                        }
                        "session.status" -> handleSessionStatus(message)
                        "device.result" -> {
                            val id = message.optString("clientId")
                            val error = message.optString("error")
                            if (error.isNotBlank() && error != "null") {
                                setConnection("Device command failed · $error", false)
                            } else if (message.optJSONObject("result") != null) {
                                val current = devices[id] ?: JSONObject().put("clientId", id)
                                val metadata = current.optJSONObject("metadata") ?: JSONObject()
                                val telemetry = metadata.optJSONObject("telemetry") ?: JSONObject()
                                val result = message.getJSONObject("result")
                                val keys = result.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    telemetry.put(key, result.get(key))
                                }
                                metadata.put("telemetry", telemetry)
                                current.put("metadata", metadata)
                                devices[id] = current
                                renderDevices()
                            }
                        }
                        "device.revoked" -> {
                            val clientId = message.optString("clientId")
                            secureStore.remove("token_$clientId")
                            preferences.edit().remove("paired_$clientId").apply()
                            devices.remove(clientId)
                            approvedInteractionSessions.remove(clientId)
                            renderDevices()
                        }
                        "error" -> runOnUiThread {
                            connectionLabel.text = "● Error · ${message.optString("message")}"
                            connectionLabel.setTextColor(Color.rgb(240, 120, 120))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    setConnection(connectionFailureLabel(t), false)
                    scheduleReconnect()
                }
            })
    }

    private fun connectionFailureLabel(error: Throwable): String {
        val detail = error.message.orEmpty().lowercase()
        return when {
            detail.contains("cleartext") -> "Cleartext blocked · use debug or WSS"
            detail.contains("unable to resolve") || detail.contains("unknownhost") ->
                "Backend host not found"
            detail.contains("failed to connect") || detail.contains("refused") ->
                "Backend unreachable"
            detail.contains("ssl") || detail.contains("certificate") ->
                "TLS validation failed"
            else -> "Backend connection failed · retrying"
        }
    }

    private fun setConnection(label: String, connected: Boolean) = runOnUiThread {
        connectionLabel.text = "● $label"
        connectionLabel.setTextColor(if (connected) Color.rgb(120, 210, 170) else Color.rgb(238, 190, 100))
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled) return
        reconnectScheduled = true
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        reconnectHandler.postDelayed({ connect() }, (1L shl reconnectAttempt) * 1000L)
    }

    private fun authenticateExisting(webSocket: WebSocket) {
        preferences.all.keys.filter { it.startsWith("paired_") }.forEach { key ->
            val clientId = key.removePrefix("paired_")
            secureStore.get("token_$clientId")?.let { token ->
                webSocket.send(JSONObject().apply {
                    put("type", "auth.connect"); put("role", "controller")
                    put("clientId", clientId); put("authToken", token)
                }.toString())
            }
        }
    }

    private fun requestDeviceList(webSocket: WebSocket) {
        webSocket.send(JSONObject().put("type", "controller.list_devices").toString())
    }

    private fun requestPairing() {
        val code = codeInput.text.toString()
        if (!Regex("\\d{6}").matches(code)) {
            connectionLabel.text = "● Enter exactly six digits"; return
        }
        socket?.send(JSONObject().apply {
            put("type", "controller.request_pairing"); put("controllerId", controllerId); put("pairingCode", code)
        }.toString())
        connectionLabel.text = "● Pairing request sent"
    }

    private fun handlePairingResult(message: JSONObject) {
        val clientId = message.optString("clientId")
        if (message.optString("status") == "paired" && clientId.isNotBlank()) {
            val token = message.optString("authToken")
            secureStore.put("token_$clientId", token)
            preferences.edit().putString("paired_$clientId", message.toString()).apply()
            socket?.send(JSONObject().apply {
                put("type", "auth.connect"); put("role", "controller")
                put("clientId", clientId); put("authToken", token)
            }.toString())
            updateDevice(message)
            runOnUiThread { connectionLabel.text = "● Pairing complete · Client is awaiting presence" }
            renderDevices()
        } else runOnUiThread { connectionLabel.text = "● Pairing ${message.optString("status")}: ${message.optString("reason")}" }
    }

    private fun updateDevice(message: JSONObject) {
        val id = message.optString("clientId")
        if (id.isBlank()) return
        val current = devices[id] ?: JSONObject().put("clientId", id)
        if (message.has("status")) current.put("status", message.optString("status"))
        if (message.has("lastSeen")) current.put("lastSeen", message.optLong("lastSeen"))
        if (message.optJSONObject("metadata") != null) current.put("metadata", message.getJSONObject("metadata"))
        devices[id] = current
    }

    private fun renderDevices() = runOnUiThread {
        devicesLayout.removeAllViews()
        val online = devices.values.count { it.optString("status") == "online" }
        summaryLabel.text = "${devices.size} paired · $online online · ${devices.size - online} offline"
        if (devices.isEmpty()) {
            devicesLayout.addView(TextView(this).apply {
                text = "No paired Clients yet.\nEnter the six-digit code shown on a Client to get started."
                textSize = 17f; setTextColor(Color.LTGRAY); setPadding(0, 28, 0, 0)
            })
            return@runOnUiThread
        }
        devices.values.forEach { addDeviceCard(it) }
    }

    private fun addDeviceCard(device: JSONObject) {
        val id = device.optString("clientId")
        val metadata = device.optJSONObject("metadata") ?: JSONObject()
        val telemetry = metadata.optJSONObject("telemetry") ?: JSONObject()
        val online = device.optString("status") == "online"
        val card = uiCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val name = metadata.optString("alias").takeUnless { it.isBlank() }
            ?: metadata.optString("model").takeUnless { it.isBlank() } ?: id
        card.addView(TextView(this).apply {
            text = "$name  ·  ${if (online) "Online" else "Offline"}"
            textSize = 20f; setTextColor(if (online) Color.rgb(120, 210, 170) else Color.LTGRAY)
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            val battery = if (telemetry.has("batteryPercent")) "${telemetry.optInt("batteryPercent")}% battery" else "Battery unavailable"
            val network = telemetry.optString("network", "Network unavailable")
            text = "${metadata.optString("platform", "Device")} · $battery · $network\n$id"
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(0, 8, 0, 8)
        })
        card.addView(Button(this).apply { text = "Open device details"; setOnClickListener { showDetails(device) } })
        val interactionSession = approvedInteractionSessions[id]
        card.addView(uiActionButton(
            if (interactionSession == null) "Request interaction access" else "End interaction session",
            interactionSession == null
        ) {
            if (interactionSession == null) requestInteraction(id) else endInteraction(id, interactionSession)
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        if (interactionSession != null) {
            card.addView(uiActionButton("Flashlight on", false) { sendDeviceCommand(id, "flashlight", true) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            card.addView(uiActionButton("Flashlight off", false) { sendDeviceCommand(id, "flashlight", false) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Alarm on", false) { sendDeviceCommand(id, "alarm", true) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Alarm off", false) { sendDeviceCommand(id, "alarm", false) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        }
        devicesLayout.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) })
    }

    private fun showDetails(device: JSONObject) {
        val id = device.optString("clientId")
        val metadata = device.optJSONObject("metadata") ?: JSONObject()
        val telemetry = metadata.optJSONObject("telemetry") ?: JSONObject()
        val lastSeen = device.optLong("lastSeen", 0L)
        val battery = if (telemetry.has("batteryPercent")) {
            "${telemetry.optInt("batteryPercent").coerceIn(0, 100)}%"
        } else "Unavailable"
        val storage = formatBytes(telemetry.optLong("storageAvailableBytes", -1L))
        val uptime = formatDuration(telemetry.optLong("uptimeMs", -1L))
        val manufacturer = metadata.optString("manufacturer").takeUnless { it.isBlank() }
        val model = metadata.optString("model").takeUnless { it.isBlank() }
        val deviceName = listOfNotNull(manufacturer, model).joinToString(" ").ifBlank { "Unavailable" }
        val text = buildString {
            append("CONNECTION\n")
            append("Status: ${device.optString("status", "offline").replaceFirstChar { it.uppercase() }}\n")
            append("Last seen: ${if (lastSeen > 0L) android.text.format.DateFormat.format("dd MMM yyyy, HH:mm", lastSeen) else "Never"}\n\n")
            append("DEVICE\n")
            append("Model: $deviceName\n")
            append("Platform: ${metadata.optString("platform").ifBlank { "Unavailable" }}\n")
            append("OS version: ${metadata.optString("androidVersion").ifBlank { "Unavailable" }}\n\n")
            append("TELEMETRY\n")
            append("Battery: $battery\n")
            append("Charging: ${telemetry.optString("charging").ifBlank { "Unavailable" }}\n")
            append("Network: ${telemetry.optString("network").ifBlank { "Unavailable" }}\n")
            append("Available storage: $storage\n")
            append("Uptime: $uptime")
        }
        AlertDialog.Builder(this).setTitle(metadata.optString("alias").ifBlank { id }).setMessage(text)
            .setPositiveButton("Refresh telemetry") { _, _ -> sendSnapshot(id) }
            .setNeutralButton("Rename") { _, _ -> rename(id, metadata.optString("alias")) }
            .setNegativeButton("Close", null).show()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unavailable"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    private fun formatDuration(milliseconds: Long): String {
        if (milliseconds < 0L) return "Unavailable"
        val totalMinutes = milliseconds / 60_000L
        val days = totalMinutes / 1_440L
        val hours = (totalMinutes % 1_440L) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> "${days}d ${hours}h"
            hours > 0L -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun rename(clientId: String, current: String) {
        val input = EditText(this).apply { setText(current); hint = "Device name" }
        AlertDialog.Builder(this).setTitle("Rename device").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                socket?.send(JSONObject().apply {
                    put("type", "controller.rename_device"); put("clientId", clientId)
                    put("alias", input.text.toString().trim())
                }.toString())
            }.show()
    }

    private fun handleSessionStatus(message: JSONObject) = runOnUiThread {
        val clientId = message.optString("clientId")
        val sessionId = message.optString("sessionId")
        if (clientId.isBlank() || sessionId.isBlank() ||
            message.optString("sessionType") != "interaction") return@runOnUiThread
        when (message.optString("state")) {
            "pending" -> setConnection("Waiting for Client approval", false)
            "approved" -> {
                approvedInteractionSessions[clientId] = sessionId
                setConnection("Interaction approved · controls unlocked", true)
            }
            "failed", "rejected", "ended" -> {
                if (approvedInteractionSessions[clientId] == sessionId) {
                    approvedInteractionSessions.remove(clientId)
                }
                setConnection("Interaction session ${message.optString("state")}", false)
            }
        }
        renderDevices()
    }

    private fun requestInteraction(clientId: String) {
        socket?.send(JSONObject().apply {
            put("type", "controller.start_session")
            put("clientId", clientId)
            put("sessionType", "interaction")
        }.toString())
        setConnection("Waiting for Client approval", false)
    }

    private fun endInteraction(clientId: String, sessionId: String) {
        socket?.send(JSONObject().apply {
            put("type", "controller.end_session")
            put("sessionId", sessionId)
            put("reason", "controller_ended")
        }.toString())
    }

    private fun sendDeviceCommand(clientId: String, command: String, enabled: Boolean) {
        if (approvedInteractionSessions[clientId].isNullOrBlank()) {
            setConnection("Request Client approval before sending commands", false)
            return
        }
        socket?.send(JSONObject().apply {
            put("type", "controller.command")
            put("clientId", clientId)
            put("command", command)
            put("parameters", JSONObject().put("enabled", enabled))
        }.toString())
        setConnection("Command sent · $command ${if (enabled) "on" else "off"}", true)
    }

    private fun sendSnapshot(clientId: String) {
        socket?.send(JSONObject().apply { put("type", "controller.request_snapshot"); put("clientId", clientId) }.toString())
    }

    private fun formatUpdateSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun setUpdateStatus(message: String) { setConnection(message, false) }

    private fun checkForUpdate() {
        setUpdateStatus("Checking for updates…")
        updateManager.check { result ->
            runOnUiThread {
                result.onFailure { error ->
                    setUpdateStatus("Update check failed · ${error.message ?: "network error"}")
                }.onSuccess { available ->
                    if (available == null) {
                        setUpdateStatus("Up to date · ${BuildConfig.VERSION_NAME}")
                        return@onSuccess
                    }
                    val details = "Installed: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\n" +
                        "Available: ${available.versionName} (code ${available.versionCode})\n" +
                        "Channel: ${if (available.prerelease) "Beta" else "Stable"}\n" +
                        "APK size: ${formatUpdateSize(available.assetSize)}\n" +
                        "Published: ${updateManager.formatDate(available.publishedAt)}\n\n" +
                        available.notes
                    AlertDialog.Builder(this)
                        .setTitle("MEIZZO update · ${available.tag}")
                        .setMessage(details)
                        .setNegativeButton("Later") { _, _ ->
                            setUpdateStatus("Update available · ${available.versionName}")
                        }
                        .setPositiveButton("Download") { _, _ -> downloadUpdate(available) }
                        .show()
                }
            }
        }
    }

    private fun downloadUpdate(release: ControllerReleaseInfo) {
        setUpdateStatus("Downloading update · 0%")
        updateManager.download(release, { progress ->
            runOnUiThread { setUpdateStatus("Downloading update · $progress%") }
        }) { result ->
            runOnUiThread {
                result.onFailure { error ->
                    setUpdateStatus("Update failed · ${error.message ?: "verification error"}")
                }.onSuccess { apk ->
                    setUpdateStatus("Update verified · awaiting installation")
                    AlertDialog.Builder(this)
                        .setTitle("Install ${release.versionName}?")
                        .setMessage(
                            "Package ID, version, file size, SHA-256, and signing certificate were verified. " +
                                "Android will show the final installation confirmation."
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Install") { _, _ ->
                            pendingUpdateApk = apk
                            updateManager.install(apk)
                            if (updateManager.canInstallPackages()) pendingUpdateApk = null
                        }
                        .show()
                }
            }
        }
    }
}
