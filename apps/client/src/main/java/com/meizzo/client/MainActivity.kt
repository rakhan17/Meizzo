package com.meizzo.client

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getPreferences(MODE_PRIVATE) }
    private val secureStore by lazy { SecureStore(this) }
    private val clientId by lazy {
        preferences.getString("client_id", null) ?: "client-${UUID.randomUUID()}".also {
            preferences.edit().putString("client_id", it).apply()
        }
    }
    private val pairingCode by lazy { "%06d".format(SecureRandom().nextInt(1_000_000)) }
    private lateinit var status: TextView
    private val httpClient by lazy { OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build() }
    private var socket: WebSocket? = null
    private var heartbeatSocket: WebSocket? = null
    private var torchEnabled = false
    private var alarm: ToneGenerator? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private var shouldMaintainConnection = true
    private val updateManager by lazy { ClientUpdateManager(this) }
    private var pendingUpdateApk: File? = null
    private val pendingSessionDialogs = mutableMapOf<String, AlertDialog>()
    private var pendingTorchCommand: Pair<WebSocket, JSONObject>? = null
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingTorchCommand
        pendingTorchCommand = null
        if (pending == null) return@registerForActivityResult
        if (granted) {
            try {
                handleCommand(pending.first, pending.second)
            } catch (error: Exception) {
                sendCommandFailure(pending.first, pending.second, error.message ?: "Flashlight could not be enabled")
            }
        } else {
            sendCommandFailure(pending.first, pending.second, "Camera permission was denied; flashlight control is unavailable")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Connecting…"
            textSize = 14f
            setTextColor(Color.rgb(35, 35, 42))
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        setContent {
            ClientLiquidGlassScreen(
                pairingCode = pairingCode,
                statusView = status,
                onStopPairing = {
                    shouldMaintainConnection = false
                    reconnectHandler.removeCallbacksAndMessages(null)
                    reconnectScheduled = false
                    socket?.close(1000, "Pairing stopped")
                    socket = null
                    status.text = "Pairing stopped"
                },
                onCheckUpdates = { checkForUpdate() }
            )
        }
        startConnectionKeepAlive()
        connect()
    }

    private fun startConnectionKeepAlive() {
        startForegroundService(Intent(this, ConnectionForegroundService::class.java))
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
        socket = httpClient.newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    reconnectScheduled = false
                    // Re-authenticate before refreshing an offer for an already-paired Client.
                    secureStore.get("auth_token")?.let { authenticate(webSocket, it) }
                    registerPairingOffer(webSocket)
                    runOnUiThread { status.text = "Connected · registering device" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = try {
                        JSONObject(text)
                    } catch (_: Exception) {
                        runOnUiThread { status.text = "Received an invalid server message · connection remains protected" }
                        return
                    }
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
                        "client.command" -> try {
                            handleCommand(webSocket, message)
                        } catch (error: Exception) {
                            sendCommandFailure(webSocket, message, error.message ?: "Command could not be completed")
                        }
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

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (heartbeatSocket === webSocket) heartbeatSocket = null
                    if (socket === webSocket && shouldMaintainConnection) {
                        runOnUiThread { status.text = "Connection closed · reconnecting" }
                        scheduleReconnect()
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
                    if (socket === webSocket && shouldMaintainConnection) scheduleReconnect()
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
        if (!shouldMaintainConnection || reconnectScheduled) return
        reconnectScheduled = true
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        val delay = (1L shl reconnectAttempt) * 1000L
        reconnectHandler.postDelayed({
            reconnectScheduled = false
            if (shouldMaintainConnection) connect()
        }, delay)
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
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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


@Composable
private fun ClientLiquidGlassScreen(
    pairingCode: String,
    statusView: TextView,
    onStopPairing: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val ink = ComposeColor(0xFF111116)
    val muted = ComposeColor(0xFF777781)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).background(
                Brush.linearGradient(
                    listOf(ComposeColor(0xFF44444D), ComposeColor(0xFF101014), ComposeColor(0xFF272730))
                )
            )
        ) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 18.dp).size(190.dp)
                .clip(CircleShape).background(ComposeColor(0xFF858591).copy(alpha = 0.32f)))
            Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 90.dp).size(220.dp)
                .clip(CircleShape).background(ComposeColor(0xFF62626D).copy(alpha = 0.28f)))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("MEIZZO", color = ComposeColor.White, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("Your device.\nYour control.", color = ComposeColor.White, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
            Text("A private connection, on your terms.", color = ComposeColor(0xFFBEBEC7), fontSize = 14.sp)
            ClientGlassPanel(backdrop) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PAIRING CODE", color = muted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Text(pairingCode, color = ink, fontSize = 38.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold)
                    Text("Enter this code in MEIZZO Controller", color = muted, fontSize = 12.sp)
                }
            }
            Text("CONNECTION", color = ComposeColor(0xFFD6D6DE), fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            ClientGlassPanel(backdrop) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(ComposeColor(0xFF5DAD7B)))
                    AndroidView(factory = { statusView }, modifier = Modifier.weight(1f))
                }
            }
            ClientGlassButton(backdrop, "Stop pairing", false, onStopPairing)
            ClientGlassButton(backdrop, "Check for updates", true, onCheckUpdates)
            Spacer(Modifier.height(8.dp))
            Text("PRIVATE BY DESIGN  ·  YOU CONTROL ACCESS", color = ComposeColor(0xFF92929C), fontSize = 9.sp, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun ClientGlassPanel(backdrop: Backdrop, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(25.dp) },
            effects = { vibrancy(); blur(18.dp.toPx()); lens(15.dp.toPx(), 26.dp.toPx()) },
            onDrawSurface = { drawRect(ComposeColor.White.copy(alpha = 0.60f)) }
        ).padding(19.dp)
    ) { content() }
}

@Composable
private fun ClientGlassButton(backdrop: Backdrop, label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(18.dp) },
            effects = { vibrancy(); blur(16.dp.toPx()); lens(12.dp.toPx(), 22.dp.toPx()) },
            onDrawSurface = { drawRect(ComposeColor.White.copy(alpha = if (primary) 0.84f else 0.24f)) }
        ).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) ComposeColor(0xFF111116) else ComposeColor.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
