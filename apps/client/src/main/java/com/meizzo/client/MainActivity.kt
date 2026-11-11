package com.meizzo.client

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.StatFs
import android.os.SystemClock
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {
    private val preferences by lazy { getPreferences(MODE_PRIVATE) }
    private val secureStore by lazy { SecureStore(this) }
    private val clientId by lazy {
        preferences.getString("client_id", null) ?: "client-${UUID.randomUUID()}".also {
            preferences.edit().putString("client_id", it).apply()
        }
    }
    private val pairingCode by lazy { "%06d".format(SecureRandom().nextInt(1_000_000)) }
    private lateinit var status: TextView
    private var socket: WebSocket? = null
    private var heartbeatSocket: WebSocket? = null
    private var torchEnabled = false
    private var alarm: ToneGenerator? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private val updateManager by lazy { ClientUpdateManager(this) }
    private var pendingUpdateApk: File? = null
    private val pendingSessionDialogs = mutableMapOf<String, AlertDialog>()
    private var pendingTorchCommand: Pair<WebSocket, JSONObject>? = null
    private val cameraPermissionRequestCode = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(8, 8, 10))
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.meizzo_logo)
            contentDescription = "MEIZZO logo"
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(-1, dp(88)))
        content.addView(TextView(this).apply {
            text = "MEIZZO CLIENT"
            textSize = 25f
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(20), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Your device, connected on your terms."
            textSize = 14f
            setTextColor(Color.rgb(155, 155, 163))
        })

        val pairingCard = uiCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        pairingCard.addView(uiEyebrow("PAIRING CODE"))
        pairingCard.addView(TextView(this).apply {
            text = pairingCode
            textSize = 42f
            letterSpacing = 0.18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        })
        pairingCard.addView(TextView(this).apply {
            text = "Share this temporary code with your MEIZZO Controller. It expires automatically."
            textSize = 13f
            setTextColor(Color.rgb(160, 160, 168))
            gravity = Gravity.CENTER
        })
        content.addView(pairingCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })

        val connectionCard = uiCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        connectionCard.addView(uiEyebrow("DEVICE CONNECTION"))
        status = TextView(this).apply {
            text = "Connecting…"
            textSize = 15f
            setTextColor(Color.rgb(220, 190, 120))
            setPadding(0, dp(8), 0, 0)
        }
        connectionCard.addView(status)
        content.addView(connectionCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        content.addView(uiActionButton("Stop pairing", false) {
            socket?.close(1000, "Pairing stopped")
            stopService(android.content.Intent(this, ConnectionForegroundService::class.java))
            status.text = "Pairing stopped"
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        content.addView(uiActionButton(
            if (preferences.getBoolean("find_my_location_enabled", false)) "Disable Find My location" else "Enable Find My location",
            false
        ) { toggleFindMyLocation() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        content.addView(uiActionButton("Check for updates", true) {
            checkForUpdate()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        content.addView(TextView(this).apply {
            text = "PRIVATE BY DESIGN  ·  YOU CONTROL ACCESS"
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
        startConnectionForegroundService()
        connect()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4103)
        }
    }

    private fun toggleFindMyLocation() {
        if (preferences.getBoolean("find_my_location_enabled", false)) {
            preferences.edit().putBoolean("find_my_location_enabled", false).apply()
            status.text = "Find My location disabled"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Enable Find My location?")
            .setMessage(
                "When enabled, MEIZZO includes Android's last-known location in device telemetry visible to your paired Controllers. " +
                    "It does not continuously request GPS updates. Coordinates can be approximate or stale, and Android may not have a cached location."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    4104
                )
            }
            .show()
    }

    private fun startConnectionForegroundService() {
        val intent = android.content.Intent(this, ConnectionForegroundService::class.java)
        startForegroundService(intent)
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
            status.text = "Install permission is required to continue."
            AlertDialog.Builder(this)
                .setTitle("Allow app updates")
                .setMessage("Enable 'Allow from this source' for MEIZZO in Android settings, then return here.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Open settings") { _, _ -> updateManager.requestInstallPermission() }
                .show()
        }
    }

    private fun connect() {
        socket = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
            .newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    reconnectScheduled = false
                    // Re-authenticate before refreshing an offer for an already-paired Client.
                    secureStore.get("auth_token")?.let { authenticate(webSocket, it) }
                    registerPairingOffer(webSocket)
                    runOnUiThread { status.text = "Connected · registering device" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "auth.connected" -> {
                            startHeartbeat(webSocket)
                            runOnUiThread { status.text = "Online · waiting for Controller" }
                        }
                        "client.pairing_request" -> runOnUiThread {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Approve Controller?")
                                .setMessage("A Controller wants to manage this device.")
                                .setNegativeButton("Reject") { _, _ -> decide(webSocket, message, false) }
                                .setPositiveButton("Approve") { _, _ -> decide(webSocket, message, true) }
                                .setOnCancelListener { decide(webSocket, message, false) }
                                .show()
                        }
                        "client.session_status" -> {
                            val sessionId = message.optString("sessionId")
                            val state = message.optString("state")
                            val sessionType = message.optString("sessionType").ifBlank { "device" }
                            when (state) {
                                "pending" -> runOnUiThread {
                                    if (sessionId.isBlank() || pendingSessionDialogs.containsKey(sessionId)) return@runOnUiThread
                                    val dialog = AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Allow $sessionType session?")
                                        .setMessage("A paired Controller is requesting permission to start a $sessionType session. Nothing starts until you approve.")
                                        .setNegativeButton("Deny") { _, _ ->
                                            pendingSessionDialogs.remove(sessionId)
                                            decideSession(webSocket, sessionId, false)
                                        }
                                        .setPositiveButton("Allow") { _, _ ->
                                            pendingSessionDialogs.remove(sessionId)
                                            decideSession(webSocket, sessionId, true)
                                        }
                                        .setOnCancelListener {
                                            pendingSessionDialogs.remove(sessionId)
                                            decideSession(webSocket, sessionId, false)
                                        }
                                        .create()
                                    pendingSessionDialogs[sessionId] = dialog
                                    dialog.show()
                                }
                                "approved" -> runOnUiThread { status.text = "$sessionType session approved" }
                                "rejected", "failed", "ended" -> runOnUiThread {
                                    pendingSessionDialogs.remove(sessionId)?.dismiss()
                                    stopActiveOutputs()
                                    status.text = if (state == "rejected") "Session request declined" else "Session $state"
                                }
                            }
                        }
                        "client.pairing_completed" -> {
                            val token = message.optString("authToken")
                            secureStore.put("auth_token", token)
                            authenticate(webSocket, token)
                            runOnUiThread { status.text = "Connected · Controller authorized" }
                        }
                        "client.snapshot_request" -> {
                            webSocket.send(JSONObject().apply {
                                put("type", "client.result")
                                put("requestId", message.optString("requestId"))
                                put("result", deviceMetrics())
                            }.toString())
                        }
                        "client.command" -> handleCommand(webSocket, message)
                        "device.revoked" -> runOnUiThread {
                            secureStore.remove("auth_token")
                            registerPairingOffer(webSocket)
                            status.text = "Access revoked · ready for a new Controller pairing"
                        }
                        "error" -> runOnUiThread {
                            if (message.optString("code") == "unauthorized") secureStore.remove("auth_token")
                            status.text = "Error: ${message.optString("message")}"
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (heartbeatSocket === webSocket) heartbeatSocket = null
                    runOnUiThread {
                        pendingSessionDialogs.values.forEach { it.dismiss() }
                        pendingSessionDialogs.clear()
                        stopActiveOutputs()
                        status.text = connectionFailureMessage(t)
                    }
                    scheduleReconnect()
                }
            })
    }

    private fun connectionFailureMessage(error: Throwable): String {
        val detail = error.message.orEmpty().lowercase()
        return when {
            detail.contains("cleartext") ->
                "Backend rejected cleartext · install a debug build or use WSS"
            detail.contains("unable to resolve") || detail.contains("unknownhost") ->
                "Backend host not found · check the configured endpoint"
            detail.contains("failed to connect") || detail.contains("refused") ->
                "Backend unreachable · check host, port, and server status"
            detail.contains("ssl") || detail.contains("certificate") ->
                "TLS validation failed · check the WSS certificate"
            else -> "Backend connection failed · retrying"
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        if (heartbeatSocket === webSocket) return
        heartbeatSocket = webSocket
        Thread {
            while (socket === webSocket && heartbeatSocket === webSocket) {
                try {
                    Thread.sleep(15_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (socket === webSocket && heartbeatSocket === webSocket) {
                    webSocket.send(JSONObject().apply {
                        put("type", "heartbeat")
                        put("telemetry", deviceMetrics())
                    }.toString())
                }
            }
        }.start()
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled) return
        reconnectScheduled = true
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        val delay = (1L shl reconnectAttempt) * 1000L
        reconnectHandler.postDelayed({ connect() }, delay)
    }

    private fun registerPairingOffer(webSocket: WebSocket) {
        webSocket.send(JSONObject().apply {
            put("type", "client.register_pairing_offer")
            put("clientId", clientId)
            put("pairingCode", pairingCode)
            put("expiresAtMs", System.currentTimeMillis() + 600_000)
            put("pairingAcceptanceEnabled", true)
            put("metadata", deviceMetadata())
        }.toString())
    }

    private fun authenticate(webSocket: WebSocket, token: String) {
        webSocket.send(JSONObject().apply {
            put("type", "auth.connect")
            put("role", "client")
            put("clientId", clientId)
            put("authToken", token)
        }.toString())
    }

    private fun decide(webSocket: WebSocket, request: JSONObject, approved: Boolean) {
        webSocket.send(JSONObject().apply {
            put("type", "client.pairing_decision")
            put("requestId", request.optString("requestId"))
            put("approved", approved)
        }.toString())
    }

    private fun decideSession(webSocket: WebSocket, sessionId: String, approved: Boolean) {
        webSocket.send(JSONObject().apply {
            put("type", "client.session_decision")
            put("sessionId", sessionId)
            put("approved", approved)
        }.toString())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4104) {
            val granted = permissions.indices.any { index ->
                (permissions[index] == android.Manifest.permission.ACCESS_FINE_LOCATION ||
                    permissions[index] == android.Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            }
            preferences.edit().putBoolean("find_my_location_enabled", granted).apply()
            status.text = if (granted) "Find My location enabled · last-known location may be approximate or stale"
                else "Find My location permission denied"
            return
        }
        if (requestCode != cameraPermissionRequestCode) return
        val pending = pendingTorchCommand
        pendingTorchCommand = null
        if (pending == null) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            handleCommand(pending.first, pending.second)
        } else {
            sendCommandFailure(pending.first, pending.second, "Camera permission was denied; flashlight control is unavailable")
        }
    }

    private fun stopActiveOutputs() {
        alarm?.stopTone()
        alarm?.release()
        alarm = null
        if (torchEnabled) {
            try {
                val manager = getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null && checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    manager.setTorchMode(cameraId, false)
                }
            } catch (_: Exception) {
                // The session is already ending; keep local state safe even if torch shutdown fails.
            }
            torchEnabled = false
        }
    }

    private fun sendCommandFailure(webSocket: WebSocket, message: JSONObject, reason: String) {
        webSocket.send(JSONObject().apply {
            put("type", "client.result")
            put("requestId", message.optString("requestId"))
            put("result", JSONObject())
            put("error", reason)
        }.toString())
    }

    private fun handleCommand(webSocket: WebSocket, message: JSONObject) {
        if (message.optString("command") == "flashlight" &&
            checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            runOnUiThread {
                if (pendingTorchCommand != null) {
                    sendCommandFailure(webSocket, message, "Another flashlight permission request is already pending")
                    return@runOnUiThread
                }
                pendingTorchCommand = webSocket to message
                AlertDialog.Builder(this)
                    .setTitle("Allow flashlight control?")
                    .setMessage("MEIZZO needs Android's camera permission to operate the flashlight. This permission is used for the torch only, not to capture photos or video.")
                    .setNegativeButton("Not now") { _, _ ->
                        pendingTorchCommand = null
                        sendCommandFailure(webSocket, message, "Flashlight permission was not granted")
                    }
                    .setPositiveButton("Continue") { _, _ ->
                        requestPermissions(
                            arrayOf(android.Manifest.permission.CAMERA),
                            cameraPermissionRequestCode
                        )
                    }
                    .setOnCancelListener {
                        pendingTorchCommand = null
                        sendCommandFailure(webSocket, message, "Flashlight permission request was cancelled")
                    }
                    .show()
            }
            return
        }
        val result = JSONObject()
        val parameters = message.optJSONObject("parameters") ?: JSONObject()
        var error: String? = null
        when (message.optString("command")) {
            "flashlight" -> {
                val enabled = parameters.optBoolean("enabled")
                try {
                    if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("Camera permission is required for flashlight control")
                    }
                    val manager = getSystemService(CameraManager::class.java)
                    val cameraId = manager.cameraIdList.firstOrNull {
                        manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: throw IllegalStateException("No flashlight hardware available")
                    manager.setTorchMode(cameraId, enabled)
                    torchEnabled = enabled
                    result.put("enabled", enabled)
                } catch (exception: Exception) {
                    error = exception.message ?: "Flashlight unavailable"
                }
            }
            "alarm" -> {
                val enabled = parameters.optBoolean("enabled")
                if (enabled) {
                    alarm?.release()
                    alarm = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    alarm?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30_000)
                } else {
                    alarm?.release()
                    alarm = null
                }
                result.put("enabled", enabled)
            }
            "emergency_message" -> {
                val emergencyText = parameters.optString("message").trim()
                if (emergencyText.isBlank() || emergencyText.length > 240) {
                    error = "Emergency message must contain 1–240 characters"
                } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    error = "Notification permission is required on this Client"
                } else {
                    val channelId = "meizzo_emergency_message"
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        val channel = android.app.NotificationChannel(
                            channelId, "Emergency phone messages",
                            android.app.NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Messages sent by the paired MEIZZO Controller"
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                        }
                        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
                    }
                    val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Emergency message")
                        .setContentText(emergencyText)
                        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(emergencyText))
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .build()
                    androidx.core.app.NotificationManagerCompat.from(this).notify(2601, notification)
                    result.put("shown", true)
                    result.put("visibility", "Android lock-screen settings apply")
                }
            }
            else -> error = "Unsupported command"
        }
        val response = JSONObject().apply {
            put("type", "client.result")
            put("requestId", message.optString("requestId"))
            put("result", result)
            if (error != null) put("error", error)
        }
        webSocket.send(response.toString())
    }

    private fun deviceMetadata() = JSONObject().apply {
        put("manufacturer", android.os.Build.MANUFACTURER)
        put("model", android.os.Build.MODEL)
        put("androidVersion", android.os.Build.VERSION.RELEASE)
        put("clientVersion", BuildConfig.VERSION_NAME)
        put("platform", "Android")
        put("deviceId", clientId)
    }

    private fun deviceMetrics() = JSONObject(deviceMetadata().toString()).apply {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        put("charging", battery.isChargingCompat())
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        put("batteryTemperatureC", (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0)
        put("batteryHealth", batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1)
        val storage = StatFs(filesDir.absolutePath)
        put("storageAvailableBytes", storage.availableBytes)
        put("storageTotalBytes", storage.totalBytes)
        put("uptimeMs", SystemClock.elapsedRealtime())
        put("network", networkType())
        put("timestamp", System.currentTimeMillis())
        put("flashlightEnabled", torchEnabled)
        val memory = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        memory.getMemoryInfo(memoryInfo)
        put("ramTotalBytes", memoryInfo.totalMem)
        put("ramAvailableBytes", memoryInfo.availMem)
        if (preferences.getBoolean("find_my_location_enabled", false)) {
            val fineGranted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                try {
                    val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
                    val lastLocation = locationManager.getProviders(true).mapNotNull { provider ->
                        try { locationManager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
                    }.maxByOrNull { it.time }
                    if (lastLocation != null) {
                        put("lastKnownLocation", JSONObject().apply {
                            put("latitude", lastLocation.latitude)
                            put("longitude", lastLocation.longitude)
                            put("accuracyMeters", if (lastLocation.hasAccuracy()) lastLocation.accuracy else JSONObject.NULL)
                            put("capturedAtMs", lastLocation.time)
                            put("provider", lastLocation.provider ?: "unknown")
                        })
                    }
                } catch (_: Exception) {
                    // Location is optional; telemetry continues without it.
                }
            }
        }
    }

    private fun BatteryManager.isChargingCompat(): Boolean {
        val intent = this@MainActivity.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status in
            intArrayOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun networkType(): String {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun formatUpdateSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun setUpdateStatus(message: String) { status.text = message }

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

    private fun downloadUpdate(release: ClientReleaseInfo) {
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
