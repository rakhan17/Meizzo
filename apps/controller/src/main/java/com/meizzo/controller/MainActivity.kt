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
import android.widget.FrameLayout
import org.webrtc.SurfaceViewRenderer
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
import org.json.JSONArray
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
    private val pendingInteractionSessions = mutableMapOf<String, String>()
    private val pendingCommandClients = mutableSetOf<String>()
    private val remoteFileBrowseStacks = mutableMapOf<String, MutableList<String>>()
    private val pendingMediaRequests = mutableSetOf<String>()
    private val activeMediaSessions = mutableMapOf<String, String>()
    private val iceServersBySession = mutableMapOf<String, JSONArray>()
    private val mediaViewers = mutableMapOf<String, RtcMediaController>()
    private val mediaDialogs = mutableMapOf<String, AlertDialog>()
    private val fileTransferCoordinators = mutableMapOf<String, FileTransferCoordinator>()
    private val activeFileTransferSessions = mutableMapOf<String, String>()
    private var selectedFileSessionId: String? = null
    private var incomingFileSessionId: String? = null
    private var incomingFileName: String? = null
    private var incomingFileMime: String = "application/octet-stream"
    private val filePickRequestCode = 5302
    private val fileSaveRequestCode = 5303
    private val updateManager by lazy { ControllerUpdateManager(this) }
    private val autoAlertManager by lazy { AutoAlertManager(this) }
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
        content.addView(uiActionButton("Auto-Alert settings", false) { showAutoAlertSettings() },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

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
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5301)
        }
    }


    private fun showAutoAlertSettings() {
        val current = autoAlertManager.settings()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        fun field(label: String, value: String, inputType: Int): EditText {
            form.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.LTGRAY)
                setPadding(0, dp(10), 0, dp(4))
            })
            return EditText(this).apply {
                setSingleLine(true); this.inputType = inputType; setText(value)
                setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
                form.addView(this)
            }
        }
        val battery = field("Low battery threshold (%)", current.batteryPercent.toString(), 2)
        val temperature = field("High temperature threshold (°C)", current.temperatureC.toString(), 8194)
        val offline = field("Offline duration before alert (minutes)", current.offlineMinutes.toString(), 2)
        val unplugged = android.widget.CheckBox(this).apply {
            text = "Alert when charger is disconnected"; isChecked = current.chargerUnplugged
            setTextColor(Color.WHITE); buttonTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            form.addView(this)
        }
        AlertDialog.Builder(this)
            .setTitle("Auto-Alert thresholds")
            .setMessage("Alerts are generated on this Controller while MEIZZO is running and receiving device status.")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val b = battery.text.toString().toIntOrNull()
                val t = temperature.text.toString().toFloatOrNull()
                val o = offline.text.toString().toIntOrNull()
                if (b == null || t == null || o == null || b !in 1..100 || t !in 25f..80f || o !in 1..1440) {
                    AlertDialog.Builder(this).setTitle("Invalid thresholds")
                        .setMessage("Use battery 1–100%, temperature 25–80°C, and offline duration 1–1440 minutes.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                autoAlertManager.updateSettings(AutoAlertManager.Settings(b, t, o, unplugged.isChecked))
            }
            .show()
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
                        "media.ice_servers" -> {
                            val sessionId = message.optString("sessionId")
                            message.optJSONArray("iceServers")?.let { iceServersBySession[sessionId] = it }
                        }
                        "media.signal" -> handleMediaSignal(message)
                        "device.result" -> {
                            val id = message.optString("clientId")
                            val error = message.optString("error")
                            val wasCommandPending = pendingCommandClients.remove(id)
                            if (error.isNotBlank() && error != "null") {
                                setConnection("Device command failed · $error", false)
                            } else if (message.optJSONObject("result") != null) {
                                val commandResult = message.getJSONObject("result")
                                if (commandResult.has("clipboardText")) {
                                    val clipboardText = commandResult.optString("clipboardText")
                                    runOnUiThread {
                                        AlertDialog.Builder(this)
                                            .setTitle("Client clipboard")
                                            .setMessage(if (clipboardText.isBlank()) "(The Client clipboard is empty or contains no text.)" else clipboardText)
                                            .setNegativeButton("Close", null)
                                            .setPositiveButton("Copy here") { _, _ ->
                                                getSystemService(android.content.ClipboardManager::class.java)
                                                    .setPrimaryClip(android.content.ClipData.newPlainText("MEIZZO Client", clipboardText))
                                                setConnection("Client clipboard copied to Controller", true)
                                            }
                                            .show()
                                    }
                                } else if (commandResult.has("files")) {
                                    runOnUiThread { showRemoteFileBrowser(id, commandResult) }
                                } else if (commandResult.optBoolean("filesChanged")) {
                                    val parentId = remoteFileBrowseStacks[id]?.lastOrNull()
                                    setConnection("Client files updated · refreshing folder", true)
                                    if (!parentId.isNullOrBlank()) {
                                        sendClientCommand(id, "file_browse", JSONObject().put("documentId", parentId))
                                    }
                                } else if (!commandResult.has("clipboardWritten") && !commandResult.has("accepted") && !commandResult.has("charactersInserted") && !commandResult.has("filesChanged")) {
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
                                if (wasCommandPending) setConnection("Client confirmed · command completed", true)
                            }
                        }
                        "device.revoked" -> {
                            val clientId = message.optString("clientId")
                            secureStore.remove("token_$clientId")
                            preferences.edit().remove("paired_$clientId").apply()
                            devices.remove(clientId)
                            approvedInteractionSessions.remove(clientId)
                            pendingInteractionSessions.remove(clientId)
                            pendingCommandClients.remove(clientId)
                            renderDevices()
                        }
                        "error" -> runOnUiThread {
                            connectionLabel.text = "● Error · ${message.optString("message")}"
                            connectionLabel.setTextColor(Color.rgb(240, 120, 120))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    runOnUiThread {
                        approvedInteractionSessions.clear()
                        pendingInteractionSessions.clear()
                        pendingCommandClients.clear()
                        renderDevices()
                    }
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
        autoAlertManager.evaluate(current)
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
        telemetry.optJSONObject("lastKnownLocation")?.let { location ->
            card.addView(uiActionButton("Open last known location", false) { openLastKnownLocation(location) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        }
        card.addView(uiActionButton(
            when {
                activeMediaSessions.values.contains(id) -> "Media session active"
                pendingMediaRequests.contains(id) -> "Waiting for media approval"
                else -> "Request live screen"
            },
            online && !pendingMediaRequests.contains(id) && !activeMediaSessions.values.contains(id)
        ) {
            if (online && !pendingMediaRequests.contains(id) && !activeMediaSessions.values.contains(id)) {
                requestMediaSession(id, "screen")
            }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        card.addView(uiActionButton("Request remote camera", false) {
            if (online) requestMediaSession(id, "camera")
            else setConnection("Client is offline", false)
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        card.addView(uiActionButton("Request file transfer", false) {
            if (online) requestMediaSession(id, "files")
            else setConnection("Client is offline", false)
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        val interactionSession = approvedInteractionSessions[id]
        val pendingInteraction = pendingInteractionSessions[id]
        card.addView(uiActionButton(
            when {
                interactionSession != null -> "End interaction session"
                pendingInteraction != null -> "Waiting for Client approval"
                else -> "Request interaction access"
            },
            interactionSession == null && pendingInteraction == null
        ) {
            when {
                interactionSession != null -> endInteraction(id, interactionSession)
                pendingInteraction == null -> requestInteraction(id)
            }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        if (interactionSession != null) {
            card.addView(uiActionButton("Flip remote camera", false) {
                sendInteractionAction(id, "camera_flip", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            card.addView(uiActionButton("Flashlight on", false) { sendDeviceCommand(id, "flashlight", true) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            card.addView(uiActionButton("Flashlight off", false) { sendDeviceCommand(id, "flashlight", false) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Alarm on", false) { sendDeviceCommand(id, "alarm", true) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Alarm off", false) { sendDeviceCommand(id, "alarm", false) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Volume up", false) {
                sendInteractionAction(id, "volume_up", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Volume down", false) {
                sendInteractionAction(id, "volume_down", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Mute media volume", false) {
                sendInteractionAction(id, "volume_mute", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Unmute media volume", false) {
                sendInteractionAction(id, "volume_unmute", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Send emergency message", false) { composeEmergencyMessage(id) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Send text to focused field", false) { showRemoteTextDialog(id) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Keyboard · Backspace", false) {
                sendInteractionAction(id, "key_backspace", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Keyboard · Enter", false) {
                sendInteractionAction(id, "key_enter", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Keyboard · Tab", false) {
                sendInteractionAction(id, "key_tab", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Keyboard · Escape", false) {
                sendInteractionAction(id, "key_escape", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            val keyboardArrows = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("←" to "key_left", "↑" to "key_up", "↓" to "key_down", "→" to "key_right").forEach { (label, command) ->
                    addView(uiActionButton(label, false) {
                        sendInteractionAction(id, command, JSONObject())
                    }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                        if (label != "←") leftMargin = dp(6)
                    })
                }
            }
            card.addView(keyboardArrows, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            val shortcutRows = listOf(
                listOf("Select all" to "select_all", "Copy" to "copy", "Paste" to "paste"),
                listOf("Cut" to "cut", "Undo" to "undo", "Redo" to "redo")
            )
            shortcutRows.forEach { row ->
                val shortcutLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    row.forEachIndexed { index, (label, shortcut) ->
                        addView(uiActionButton(label, false) {
                            sendInteractionAction(id, "key_shortcut", JSONObject().put("shortcut", shortcut))
                        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                            if (index > 0) leftMargin = dp(6)
                        })
                    }
                }
                card.addView(shortcutLayout, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })
            }
            card.addView(uiActionButton("Keyboard · New line", false) {
                sendInteractionAction(id, "text_input", JSONObject().put("text", "\n"))
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Read Client clipboard", false) {
                sendClientCommand(id, "clipboard_read", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Send text to Client clipboard", false) {
                showClipboardWriteDialog(id)
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Browse Client files", false) {
                remoteFileBrowseStacks.remove(id)
                sendClientCommand(id, "file_browse", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Lock Client screen", false) {
                sendInteractionAction(id, "lock_screen", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Back", false) {
                sendInteractionAction(id, "back", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Home", false) {
                sendInteractionAction(id, "home", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Recent apps", false) {
                sendInteractionAction(id, "recents", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Notification shade", false) {
                sendInteractionAction(id, "notifications", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Quick settings", false) {
                sendInteractionAction(id, "quick_settings", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Power menu", false) {
                sendInteractionAction(id, "power_dialog", JSONObject())
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Tap screen by coordinates", false) { showTapDialog(id) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            card.addView(uiActionButton("Swipe screen by coordinates", false) { showSwipeDialog(id) },
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
            val location = telemetry.optJSONObject("lastKnownLocation")
            append("\n\nFIND MY PHONE\n")
            if (location != null) {
                append("Coordinates: %.6f, %.6f\n".format(Locale.US, location.optDouble("latitude"), location.optDouble("longitude")))
                val accuracy = location.optDouble("accuracyMeters", -1.0)
                append("Accuracy: ${if (accuracy >= 0) "%.0f m".format(Locale.US, accuracy) else "Unavailable"}\n")
                append("Captured: ${if (location.optLong("capturedAtMs") > 0) android.text.format.DateFormat.format("dd MMM yyyy, HH:mm:ss", location.optLong("capturedAtMs")) else "Unknown"}")
            } else {
                append("No location shared. Enable Find My location on the Client first.")
            }
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

    private fun openLastKnownLocation(location: JSONObject) {
        val latitude = location.optDouble("latitude", Double.NaN)
        val longitude = location.optDouble("longitude", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) {
            setConnection("Last known location is unavailable", false)
            return
        }
        val uri = android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            setConnection("No map application is available", false)
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
        val sessionType = message.optString("sessionType")
        val state = message.optString("state")
        if (clientId.isBlank() || sessionId.isBlank()) return@runOnUiThread
        when (sessionType) {
            "interaction" -> when (state) {
                "pending" -> {
                    pendingInteractionSessions[clientId] = sessionId
                    setConnection("Waiting for Client approval", false)
                }
                "approved" -> {
                    pendingInteractionSessions.remove(clientId, sessionId)
                    approvedInteractionSessions[clientId] = sessionId
                    setConnection("Interaction approved · controls unlocked", true)
                }
                "failed", "rejected", "ended" -> {
                    pendingInteractionSessions.remove(clientId, sessionId)
                    if (approvedInteractionSessions[clientId] == sessionId) {
                        approvedInteractionSessions.remove(clientId)
                    }
                    setConnection("Interaction session $state", false)
                }
            }
            "screen", "camera", "files" -> when (state) {
                "pending" -> {
                    pendingMediaRequests.add(clientId)
                    setConnection("Waiting for Client $sessionType approval", false)
                }
                "approved" -> {
                    pendingMediaRequests.remove(clientId)
                    activeMediaSessions[sessionId] = clientId
                    if (sessionType == "files") openFileTransferDialog(clientId, sessionId)
                    else openMediaViewer(clientId, sessionId, sessionType)
                }
                "failed", "rejected", "ended" -> {
                    pendingMediaRequests.remove(clientId)
                    activeMediaSessions.remove(sessionId)
                    iceServersBySession.remove(sessionId)
                    closeMediaViewer(sessionId)
                    setConnection("$sessionType session $state", false)
                }
            }
        }
        renderDevices()
    }

    private fun requestMediaSession(clientId: String, kind: String) {
        if (pendingMediaRequests.contains(clientId) || activeMediaSessions.values.contains(clientId)) {
            setConnection("A media session is already pending or active for this Client", false)
            return
        }
        pendingMediaRequests.add(clientId)
        socket?.send(JSONObject().apply {
            put("type", "controller.start_session")
            put("clientId", clientId)
            put("sessionType", kind)
        }.toString())
        setConnection("Requesting $kind stream · waiting for Client approval", false)
        renderDevices()
    }

    private fun openMediaViewer(clientId: String, sessionId: String, kind: String) {
        closeMediaViewer(sessionId)
        val video = SurfaceViewRenderer(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(8, 8, 10))
            addView(TextView(this@MainActivity).apply {
                text = "LIVE $kind · $clientId"
                textSize = 12f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            })
            addView(video, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (kind == "screen") "Live screen" else "Remote camera")
            .setView(root)
            .setNegativeButton("End session") { _, _ -> endMediaSession(clientId, sessionId) }
            .setNeutralButton("Save screenshot", null)
            .setPositiveButton("Mute remote audio", null)
            .create()
        mediaDialogs[sessionId] = dialog
        val viewer = RtcMediaController(
            this,
            { signal ->
                socket?.send(JSONObject().apply {
                    put("type", "media.signal")
                    put("sessionId", sessionId)
                    put("signal", signal)
                }.toString())
            },
            { reason ->
                runOnUiThread { setConnection("Media stream error · $reason", false) }
            },
            iceServersJson = iceServersBySession.remove(sessionId)
        )
        mediaViewers[sessionId] = viewer
        var touchStartX = 0f
        var touchStartY = 0f
        var touchStartTime = 0L
        var multiTouchGesture = false
        var previousCentroidX = 0f
        var previousCentroidY = 0f
        var scrollDeltaX = 0f
        var scrollDeltaY = 0f
        video.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = event.eventTime
                    multiTouchGesture = false
                    scrollDeltaX = 0f
                    scrollDeltaY = 0f
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        multiTouchGesture = true
                        previousCentroidX = (event.getX(0) + event.getX(1)) / 2f
                        previousCentroidY = (event.getY(0) + event.getY(1)) / 2f
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (multiTouchGesture && event.pointerCount >= 2) {
                        val centroidX = (event.getX(0) + event.getX(1)) / 2f
                        val centroidY = (event.getY(0) + event.getY(1)) / 2f
                        scrollDeltaX += previousCentroidX - centroidX
                        scrollDeltaY += previousCentroidY - centroidY
                        previousCentroidX = centroidX
                        previousCentroidY = centroidY
                        val threshold = dp(18).toFloat()
                        if (kotlin.math.abs(scrollDeltaX) >= threshold ||
                            kotlin.math.abs(scrollDeltaY) >= threshold) {
                            val normalizedX = (scrollDeltaX / video.width.coerceAtLeast(1)).coerceIn(-0.35f, 0.35f)
                            val normalizedY = (scrollDeltaY / video.height.coerceAtLeast(1)).coerceIn(-0.35f, 0.35f)
                            sendInteractionAction(clientId, "scroll", JSONObject().apply {
                                put("deltaX", normalizedX.toDouble())
                                put("deltaY", normalizedY.toDouble())
                            })
                            scrollDeltaX = 0f
                            scrollDeltaY = 0f
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    multiTouchGesture = true
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (multiTouchGesture) {
                        multiTouchGesture = false
                        scrollDeltaX = 0f
                        scrollDeltaY = 0f
                        return@setOnTouchListener true
                    }
                    val aspect = if (viewer.remoteWidth > 0 && viewer.remoteHeight > 0) {
                        viewer.remoteWidth.toFloat() / viewer.remoteHeight.toFloat()
                    } else video.width.toFloat() / video.height.coerceAtLeast(1).toFloat()
                    val viewAspect = video.width.toFloat() / video.height.coerceAtLeast(1).toFloat()
                    val contentWidth: Float
                    val contentHeight: Float
                    val offsetX: Float
                    val offsetY: Float
                    if (viewAspect > aspect) {
                        contentHeight = video.height.toFloat()
                        contentWidth = contentHeight * aspect
                        offsetX = (video.width - contentWidth) / 2f
                        offsetY = 0f
                    } else {
                        contentWidth = video.width.toFloat()
                        contentHeight = contentWidth / aspect
                        offsetX = 0f
                        offsetY = (video.height - contentHeight) / 2f
                    }
                    fun normalizedX(x: Float) = ((x - offsetX) / contentWidth).coerceIn(0f, 1f).toDouble()
                    fun normalizedY(y: Float) = ((y - offsetY) / contentHeight).coerceIn(0f, 1f).toDouble()
                    if (event.x < offsetX || event.x > offsetX + contentWidth ||
                        event.y < offsetY || event.y > offsetY + contentHeight ||
                        touchStartX < offsetX || touchStartX > offsetX + contentWidth ||
                        touchStartY < offsetY || touchStartY > offsetY + contentHeight) {
                        return@setOnTouchListener true
                    }
                    val startX = normalizedX(touchStartX)
                    val startY = normalizedY(touchStartY)
                    val endX = normalizedX(event.x)
                    val endY = normalizedY(event.y)
                    if (kotlin.math.abs(event.x - touchStartX) < dp(12) &&
                        kotlin.math.abs(event.y - touchStartY) < dp(12)) {
                        val pressDuration = (event.eventTime - touchStartTime).coerceAtLeast(0L)
                        if (pressDuration >= 500L) {
                            sendInteractionAction(clientId, "long_press", JSONObject().apply {
                                put("x", endX)
                                put("y", endY)
                                put("durationMs", pressDuration.coerceAtMost(2_000L))
                            })
                        } else {
                            sendInteractionAction(clientId, "tap", JSONObject().put("x", endX).put("y", endY))
                        }
                    } else {
                        sendInteractionAction(clientId, "swipe", JSONObject().apply {
                            put("startX", startX); put("startY", startY)
                            put("endX", endX); put("endY", endY)
                            put("durationMs", 350)
                        })
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    multiTouchGesture = false
                    scrollDeltaX = 0f
                    scrollDeltaY = 0f
                    true
                }
                else -> true
            }
        }
        dialog.setOnDismissListener {
            mediaDialogs.remove(sessionId)
            mediaViewers.remove(sessionId)?.close()
            if (activeMediaSessions.containsKey(sessionId)) endMediaSession(clientId, sessionId)
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            saveRemoteScreenshot(video, clientId)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { button ->
            val enabled = viewer.toggleRemoteAudio()
            (button as? Button)?.text = if (enabled) "Mute remote audio" else "Unmute remote audio"
        }
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            (resources.displayMetrics.heightPixels * 0.82).toInt()
        )
        viewer.start(video)
        if (!approvedInteractionSessions.containsKey(clientId) &&
            !pendingInteractionSessions.containsKey(clientId)) {
            requestInteraction(clientId)
        }
    }

    private fun saveRemoteScreenshot(view: SurfaceViewRenderer, clientId: String) {
        if (view.width <= 0 || view.height <= 0) {
            android.widget.Toast.makeText(this, "The live screen is not ready yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        try {
            android.view.PixelCopy.request(view, bitmap, { result ->
                if (result != android.view.PixelCopy.SUCCESS) {
                    bitmap.recycle()
                    android.widget.Toast.makeText(
                        this, "Screenshot capture failed · code $result", android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@request
                }
                try {
                    val fileName = "MEIZZO-${clientId.takeLast(8)}-${System.currentTimeMillis()}.png"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(
                                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_PICTURES + "/MEIZZO"
                            )
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        ) ?: throw IllegalStateException("Could not create a gallery entry")
                        contentResolver.openOutputStream(uri)?.use { output ->
                            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                                throw IllegalStateException("PNG encoding failed")
                            }
                        } ?: throw IllegalStateException("Could not open the screenshot file")
                        values.clear()
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                        android.widget.Toast.makeText(
                            this, "Screenshot saved to Pictures/MEIZZO", android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val directory = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                            ?: filesDir
                        val destination = File(directory, fileName)
                        destination.outputStream().use { output ->
                            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                                throw IllegalStateException("PNG encoding failed")
                            }
                        }
                        android.widget.Toast.makeText(
                            this, "Screenshot saved in MEIZZO app storage", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (error: Exception) {
                    android.widget.Toast.makeText(
                        this, "Could not save screenshot · ${error.message ?: "storage error"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } finally {
                    bitmap.recycle()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: Exception) {
            bitmap.recycle()
            android.widget.Toast.makeText(
                this, "Could not capture screenshot · ${error.message ?: "capture error"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openFileTransferDialog(clientId: String, sessionId: String) {
        closeMediaViewer(sessionId)
        val details = TextView(this).apply {
            text = "Files are transferred directly through an encrypted WebRTC data channel after Client approval. Each transfer requires the receiving device to choose a save location."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(18), dp(8), dp(18), dp(12))
        }
        val choose = uiActionButton("Choose file to send", true) {
            selectedFileSessionId = sessionId
            startActivityForResult(
                android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                    type = "*/*"
                },
                filePickRequestCode
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(8, 8, 10))
            addView(details)
            addView(choose, LinearLayout.LayoutParams(-1, dp(46)))
            addView(uiActionButton("Cancel active transfer", false) {
                fileTransferCoordinators[sessionId]?.cancelTransfer()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Secure file transfer")
            .setView(root)
            .setNegativeButton("End session") { _, _ -> endMediaSession(clientId, sessionId) }
            .create()
        mediaDialogs[sessionId] = dialog
        activeFileTransferSessions[clientId] = sessionId
        val viewer = RtcMediaController(
            this,
            { signal ->
                socket?.send(JSONObject().apply {
                    put("type", "media.signal")
                    put("sessionId", sessionId)
                    put("signal", signal)
                }.toString())
            },
            { reason -> runOnUiThread { setConnection("File transfer error · $reason", false) } },
            onDataChannel = { channel ->
                runOnUiThread {
                    fileTransferCoordinators[sessionId]?.close()
                    fileTransferCoordinators[sessionId] = FileTransferCoordinator(
                        this,
                        channel,
                        { name, size, mime ->
                            runOnUiThread {
                                incomingFileSessionId = sessionId
                                incomingFileName = name
                                incomingFileMime = mime
                                AlertDialog.Builder(this)
                                    .setTitle("Receive file?")
                                    .setMessage("$name · ${if (size >= 0) "${size / 1024} KB" else "size unknown"}\nSave this file to the Controller?")
                                    .setNegativeButton("Decline") { _, _ ->
                                        fileTransferCoordinators[sessionId]?.rejectIncoming()
                                    }
                                    .setPositiveButton("Choose destination") { _, _ ->
                                        startActivityForResult(
                                            android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                                type = incomingFileMime
                                                putExtra(android.content.Intent.EXTRA_TITLE, incomingFileName)
                                            },
                                            fileSaveRequestCode
                                        )
                                    }
                                    .show()
                            }
                        },
                        { message -> runOnUiThread { setConnection(message, true) } }
                    )
                }
            },
            iceServersJson = iceServersBySession.remove(sessionId)
        )
        mediaViewers[sessionId] = viewer
        dialog.setOnDismissListener {
            mediaDialogs.remove(sessionId)
            if (activeFileTransferSessions[clientId] == sessionId) activeFileTransferSessions.remove(clientId)
            mediaViewers.remove(sessionId)?.close()
            fileTransferCoordinators.remove(sessionId)?.close()
            if (activeMediaSessions.containsKey(sessionId)) endMediaSession(clientId, sessionId)
        }
        dialog.show()
        viewer.startFiles()
    }

    private fun handleMediaSignal(message: JSONObject) {
        val sessionId = message.optString("sessionId")
        val signal = message.optJSONObject("signal") ?: return
        mediaViewers[sessionId]?.onSignal(signal)
    }

    private fun closeMediaViewer(sessionId: String) {
        mediaDialogs.remove(sessionId)?.dismiss()
        mediaViewers.remove(sessionId)?.close()
        fileTransferCoordinators.remove(sessionId)?.close()
    }

    private fun endMediaSession(clientId: String, sessionId: String) {
        activeMediaSessions.remove(sessionId)
        closeMediaViewer(sessionId)
        socket?.send(JSONObject().apply {
            put("type", "controller.end_session")
            put("sessionId", sessionId)
            put("reason", "controller_ended")
        }.toString())
        setConnection("Media session ended", false)
        renderDevices()
    }

    private fun requestInteraction(clientId: String) {
        if (pendingInteractionSessions.containsKey(clientId) ||
            approvedInteractionSessions.containsKey(clientId)) return
        socket?.send(JSONObject().apply {
            put("type", "controller.start_session")
            put("clientId", clientId)
            put("sessionType", "interaction")
        }.toString())
        setConnection("Waiting for Client approval", false)
    }

    private fun endInteraction(clientId: String, sessionId: String) {
        approvedInteractionSessions.remove(clientId, sessionId)
        renderDevices()
        socket?.send(JSONObject().apply {
            put("type", "controller.end_session")
            put("sessionId", sessionId)
            put("reason", "controller_ended")
        }.toString())
    }

    private fun composeEmergencyMessage(clientId: String) {
        val input = EditText(this).apply {
            hint = "This phone is lost. Please call…"
            minLines = 2
            maxLines = 4
            setPadding(dp(14), dp(12), dp(14), dp(12))
            filters = arrayOf(android.text.InputFilter.LengthFilter(240))
        }
        AlertDialog.Builder(this)
            .setTitle("Send emergency message")
            .setMessage("MEIZZO will post this as a notification on the Client. Lock-screen visibility depends on that device's Android notification settings.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                val message = input.text.toString().trim()
                if (message.isBlank()) {
                    setConnection("Emergency message cannot be empty", false)
                    return@setPositiveButton
                }
                socket?.send(JSONObject().apply {
                    put("type", "controller.command")
                    put("clientId", clientId)
                    put("command", "emergency_message")
                    put("parameters", JSONObject().put("message", message))
                }.toString())
                setConnection("Emergency message sent to Client", true)
            }
            .show()
    }

    private fun showRemoteRenameDialog(clientId: String, item: JSONObject) {
        val currentName = item.optString("name")
        val input = EditText(this).apply {
            setText(currentName)
            selectAll()
            hint = "New file or folder name"
            filters = arrayOf(android.text.InputFilter.LengthFilter(128))
        }
        AlertDialog.Builder(this)
            .setTitle("Rename remote item")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) {
                    setConnection("Enter a valid name without path separators", false)
                } else {
                    sendClientCommand(
                        clientId,
                        "file_rename",
                        JSONObject().put("documentId", item.optString("documentId")).put("newName", newName)
                    )
                }
            }
            .show()
    }

    private fun confirmRemoteDelete(clientId: String, item: JSONObject) {
        val name = item.optString("name", "this file")
        AlertDialog.Builder(this)
            .setTitle("Delete remote file?")
            .setMessage("Delete $name permanently? The Client must approve this operation too.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                sendClientCommand(
                    clientId,
                    "file_delete",
                    JSONObject().put("documentId", item.optString("documentId"))
                )
            }
            .show()
    }

    private fun showRemoteFileBrowser(clientId: String, result: JSONObject) {
        val documentId = result.optString("documentId")
        val rootDocumentId = result.optString("rootDocumentId")
        val entries = result.optJSONArray("files") ?: org.json.JSONArray()
        if (documentId.isBlank() || rootDocumentId.isBlank()) {
            setConnection("Client returned an invalid folder listing", false)
            return
        }
        val stack = remoteFileBrowseStacks.getOrPut(clientId) { mutableListOf(rootDocumentId) }
        if (stack.isEmpty() || stack.first() != rootDocumentId) {
            stack.clear()
            stack.add(rootDocumentId)
        }
        if (stack.last() != documentId) {
            if (stack.contains(documentId)) {
                while (stack.isNotEmpty() && stack.last() != documentId) stack.removeAt(stack.lastIndex)
            } else {
                stack.add(documentId)
            }
        }
        val labels = ArrayList<String>()
        val metadata = ArrayList<JSONObject>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val isDirectory = item.optBoolean("isDirectory")
            val name = item.optString("name", "Unnamed")
            val size = item.optLong("size", -1L)
            labels.add(
                (if (isDirectory) "▸  " else "   ") + name +
                    if (!isDirectory && size >= 0L) "  ·  ${size / 1024} KB" else ""
            )
            metadata.add(item)
        }
        val message = buildString {
            append("Shared folder · ")
            append(if (documentId == rootDocumentId) "shared folder root" else "subfolder")
            if (result.optBoolean("truncated")) append("\nShowing the first 40 entries")
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("Client files")
            .setMessage(message)
            .setItems(labels.toTypedArray()) { _, index ->
                val item = metadata[index]
                val name = item.optString("name", "Remote item")
                val documentId = item.optString("documentId")
                val flags = item.optLong("flags")
                val isDirectory = item.optBoolean("isDirectory")
                val canRename = (flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong()) != 0L
                val canDelete = !isDirectory &&
                    (flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong()) != 0L
                val size = item.optLong("size", -1L)
                val mime = item.optString("mimeType", "Unknown type")
                val entryDialog = AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage(
                        if (isDirectory) "Folder · open, or rename if this storage provider allows it."
                        else "$mime\n${if (size >= 0L) "${size} bytes" else "Size unavailable"}"
                    )
                if (isDirectory) {
                    entryDialog.setPositiveButton("Open") { _, _ ->
                        if (documentId.isNotBlank()) {
                            stack.add(documentId)
                            sendClientCommand(clientId, "file_browse", JSONObject().put("documentId", documentId))
                        }
                    }
                } else {
                    entryDialog.setPositiveButton("Download") { _, _ ->
                        val fileSessionId = activeFileTransferSessions[clientId]
                        val transfer = fileSessionId?.let(fileTransferCoordinators::get)
                        if (fileSessionId.isNullOrBlank()) {
                            setConnection("Start an approved file-transfer session before downloading", false)
                        } else if (transfer?.isReady() != true) {
                            setConnection("Wait until the secure file-transfer channel is ready", false)
                        } else {
                            sendClientCommand(
                                clientId,
                                "file_send",
                                JSONObject().put("documentId", documentId).put("sessionId", fileSessionId)
                            )
                        }
                    }
                }
                if (canRename) {
                    entryDialog.setNeutralButton("Rename") { _, _ -> showRemoteRenameDialog(clientId, item) }
                } else {
                    entryDialog.setNeutralButton("Close", null)
                }
                if (canDelete) {
                    entryDialog.setNegativeButton("Delete") { _, _ -> confirmRemoteDelete(clientId, item) }
                } else {
                    entryDialog.setNegativeButton("Close", null)
                }
                entryDialog.show()
            }
            .setNegativeButton("Close", null)
        if (stack.size > 1) {
            builder.setPositiveButton("Up") { _, _ ->
                stack.removeAt(stack.lastIndex)
                val parentId = stack.last()
                sendClientCommand(clientId, "file_browse", JSONObject().put("documentId", parentId))
            }
        }
        builder.show()
    }

    private fun showClipboardWriteDialog(clientId: String) {
        val input = EditText(this).apply {
            hint = "Text to place on the Client clipboard"
            minLines = 3
            maxLines = 6
            setPadding(dp(14), dp(12), dp(14), dp(12))
            filters = arrayOf(android.text.InputFilter.LengthFilter(10_000))
        }
        AlertDialog.Builder(this)
            .setTitle("Send text to Client clipboard")
            .setMessage("The Client must approve this request before its clipboard is replaced.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                val value = input.text.toString()
                if (value.isBlank()) setConnection("Clipboard text cannot be empty", false)
                else sendClientCommand(clientId, "clipboard_write", JSONObject().put("text", value))
            }
            .show()
    }

    private fun sendClientCommand(clientId: String, command: String, parameters: JSONObject) {
        if (approvedInteractionSessions[clientId].isNullOrBlank()) {
            setConnection("Request Client approval before sending remote commands", false)
            return
        }
        if (clientId in pendingCommandClients) {
            setConnection("Waiting for the previous Client command to finish", false)
            return
        }
        val webSocket = socket
        if (webSocket == null) {
            setConnection("Controller is disconnected · remote command not sent", false)
            return
        }
        pendingCommandClients.add(clientId)
        if (!webSocket.send(JSONObject().apply {
            put("type", "controller.command")
            put("clientId", clientId)
            put("command", command)
            put("parameters", parameters)
        }.toString())) {
            pendingCommandClients.remove(clientId)
            setConnection("Remote command could not be queued", false)
        } else setConnection("Waiting for Client approval · $command", true)
    }

    private fun showRemoteTextDialog(clientId: String) {
        val input = EditText(this).apply {
            hint = "Text to insert on the Client"
            minLines = 3
            maxLines = 6
            setPadding(dp(14), dp(12), dp(14), dp(12))
            filters = arrayOf(android.text.InputFilter.LengthFilter(2_000))
        }
        AlertDialog.Builder(this)
            .setTitle("Remote keyboard · text input")
            .setMessage("Place the cursor in a text field on the Client first. MEIZZO appends the text there. Password fields are blocked, and Android may reject unsupported custom editors.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Insert") { _, _ ->
                val value = input.text.toString()
                if (value.isBlank()) {
                    setConnection("Text input cannot be empty", false)
                } else {
                    sendInteractionAction(clientId, "text_input", JSONObject().put("text", value))
                }
            }
            .show()
    }

    private fun sendDeviceCommand(clientId: String, command: String, enabled: Boolean) {
        if (approvedInteractionSessions[clientId].isNullOrBlank()) {
            setConnection("Request Client approval before sending commands", false)
            return
        }
        if (clientId in pendingCommandClients) {
            setConnection("Waiting for the previous Client command to finish", false)
            return
        }
        val webSocket = socket
        if (webSocket == null) {
            setConnection("Controller is disconnected · command not sent", false)
            return
        }
        pendingCommandClients.add(clientId)
        val sent = webSocket.send(JSONObject().apply {
            put("type", "controller.command")
            put("clientId", clientId)
            put("command", command)
            put("parameters", JSONObject().put("enabled", enabled))
        }.toString())
        if (!sent) {
            pendingCommandClients.remove(clientId)
            setConnection("Command could not be queued · check connection", false)
        } else {
            setConnection("Waiting for Client confirmation · $command", true)
        }
    }

    private fun coordinateField(label: String): EditText = EditText(this).apply {
        hint = label
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
    }

    private fun showTapDialog(clientId: String) {
        val x = coordinateField("X position (0.0 to 1.0)")
        val y = coordinateField("Y position (0.0 to 1.0)")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(x)
            addView(y)
        }
        AlertDialog.Builder(this)
            .setTitle("Tap Client screen")
            .setMessage("Coordinates are normalized: (0,0) is top-left and (1,1) is bottom-right.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send tap") { _, _ ->
                val nx = x.text.toString().toDoubleOrNull()
                val ny = y.text.toString().toDoubleOrNull()
                if (nx == null || ny == null || nx !in 0.0..1.0 || ny !in 0.0..1.0) {
                    setConnection("Enter valid X and Y values from 0 to 1", false)
                } else {
                    sendInteractionAction(clientId, "tap", JSONObject().put("x", nx).put("y", ny))
                }
            }
            .show()
    }

    private fun showSwipeDialog(clientId: String) {
        val startX = coordinateField("Start X (0.0 to 1.0)")
        val startY = coordinateField("Start Y (0.0 to 1.0)")
        val endX = coordinateField("End X (0.0 to 1.0)")
        val endY = coordinateField("End Y (0.0 to 1.0)")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(startX)
            addView(startY)
            addView(endX)
            addView(endY)
        }
        AlertDialog.Builder(this)
            .setTitle("Swipe Client screen")
            .setMessage("Coordinates are normalized from 0 to 1 across the Client display.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send swipe") { _, _ ->
                val values = listOf(startX, startY, endX, endY).map { it.text.toString().toDoubleOrNull() }
                if (values.any { it == null || it !in 0.0..1.0 }) {
                    setConnection("Enter four valid coordinate values from 0 to 1", false)
                } else {
                    sendInteractionAction(clientId, "swipe", JSONObject().apply {
                        put("startX", values[0])
                        put("startY", values[1])
                        put("endX", values[2])
                        put("endY", values[3])
                        put("durationMs", 350)
                    })
                }
            }
            .show()
    }

    private fun sendInteractionAction(clientId: String, command: String, parameters: JSONObject) {
        if (approvedInteractionSessions[clientId].isNullOrBlank()) {
            setConnection("Request Client approval before sending controls", false)
            return
        }
        val webSocket = socket
        if (webSocket == null) {
            setConnection("Controller is disconnected · action not sent", false)
            return
        }
        val sent = webSocket.send(JSONObject().apply {
            put("type", "controller.command")
            put("clientId", clientId)
            put("command", command)
            put("parameters", parameters)
        }.toString())
        setConnection(
            if (sent) "Remote action queued · $command" else "Remote action could not be queued",
            sent
        )
    }

    private fun sendSnapshot(clientId: String) {
        socket?.send(JSONObject().apply { put("type", "controller.request_snapshot"); put("clientId", clientId) }.toString())
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            filePickRequestCode -> {
                val sessionId = selectedFileSessionId
                if (resultCode == RESULT_OK && data != null && sessionId != null) {
                    val selected = mutableListOf<android.net.Uri>()
                    data.data?.let(selected::add)
                    data.clipData?.let { clip ->
                        for (index in 0 until clip.itemCount) {
                            val uri = clip.getItemAt(index).uri
                            if (selected.none { it == uri }) selected.add(uri)
                        }
                    }
                    try {
                        fileTransferCoordinators[sessionId]?.sendFiles(selected)
                    } catch (error: Exception) {
                        setConnection("Could not send selected files · ${error.message ?: "storage error"}", false)
                    }
                }
                selectedFileSessionId = null
            }
            fileSaveRequestCode -> {
                val sessionId = incomingFileSessionId
                if (resultCode == RESULT_OK && data?.data != null && sessionId != null) {
                    try {
                        fileTransferCoordinators[sessionId]?.acceptIncoming(data.data!!)
                    } catch (error: Exception) {
                        fileTransferCoordinators[sessionId]?.rejectIncoming()
                        setConnection("Could not save file · ${error.message ?: "storage error"}", false)
                    }
                } else if (sessionId != null) {
                    fileTransferCoordinators[sessionId]?.rejectIncoming()
                }
                incomingFileSessionId = null
                incomingFileName = null
                incomingFileMime = "application/octet-stream"
            }
        }
    }

    override fun onDestroy() {
        mediaDialogs.values.toList().forEach { it.dismiss() }
        mediaViewers.values.toList().forEach { it.close() }
        fileTransferCoordinators.values.toList().forEach { it.close() }
        mediaDialogs.clear()
        mediaViewers.clear()
        fileTransferCoordinators.clear()
        reconnectHandler.removeCallbacksAndMessages(null)
        socket?.close(1000, "Controller activity destroyed")
        super.onDestroy()
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
