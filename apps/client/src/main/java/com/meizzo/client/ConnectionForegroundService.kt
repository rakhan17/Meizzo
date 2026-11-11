package com.meizzo.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Owns the signaling socket independently of the UI Activity. */
class ConnectionForegroundService : Service() {
    private val preferences by lazy { getSharedPreferences("MainActivity", Context.MODE_PRIVATE) }
    private val secureStore by lazy { SecureStore(this) }
    private val client by lazy { OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build() }
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var reconnectPending = false
    private var maintainConnection = true
    private var heartbeat: Runnable? = null
    private var alarm: ToneGenerator? = null
    private var torchEnabled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                maintainConnection = false
                handler.removeCallbacksAndMessages(null)
                activeSocket?.close(1000, "Pairing stopped")
                activeSocket = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                activeSocket?.send(JSONObject().put("type", "client.register_pairing_offer")
                    .put("clientId", clientId()).put("pairingCode", pairingCode())
                    .put("expiresAtMs", System.currentTimeMillis() + 600_000)
                    .put("pairingAcceptanceEnabled", true).put("metadata", deviceMetadata()).toString())
            }
        }
        startInForeground()
        maintainConnection = true
        if (activeSocket == null) connect()
        return START_STICKY
    }

    private fun startInForeground() {
        val openApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("MEIZZO Client is active")
            .setContentText("Secure connection is maintained in the background")
            .setContentIntent(openApp).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setOnlyAlertOnce(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun connect() {
        if (!maintainConnection || activeSocket != null) return
        activeSocket = client.newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (activeSocket !== webSocket) return
                reconnectAttempt = 0
                secureStore.get("auth_token")?.takeIf { it.isNotBlank() }?.let { authenticate(webSocket, it) }
                registerPairingOffer(webSocket)
                broadcastState("Connected · waiting for Controller", true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeSocket !== webSocket) return
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("type")) {
                    "auth.connected" -> startHeartbeat(webSocket)
                    "client.pairing_completed" -> {
                        val token = message.optString("authToken")
                        if (token.isNotBlank()) {
                            secureStore.put("auth_token", token)
                            authenticate(webSocket, token)
                        }
                    }
                    "device.revoked" -> secureStore.remove("auth_token")
                    "client.snapshot_request" -> {
                        val result = runCatching { deviceMetrics() }.getOrElse { JSONObject() }
                        webSocket.send(JSONObject().apply {
                            put("type", "client.result")
                            put("requestId", message.optString("requestId"))
                            put("result", result)
                        }.toString())
                    }
                    "client.command" -> handleBackgroundCommand(webSocket, message)
                    "client.pairing_request", "client.session_status" -> {
                        preferences.edit().putString("pending_server_message", text).apply()
                    }
                }
                broadcastMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket === webSocket) {
                    activeSocket = null
                    stopHeartbeat()
                    broadcastState("Connection closed · reconnecting", false)
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (activeSocket === webSocket) {
                    activeSocket = null
                    stopHeartbeat()
                    broadcastState("Connection unavailable · reconnecting", false)
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleBackgroundCommand(webSocket: WebSocket, message: JSONObject) {
        val command = message.optString("command")
        val parameters = message.optJSONObject("parameters") ?: JSONObject()
        val result = JSONObject()
        var error: String? = null
        try {
            when (command) {
                "flashlight" -> {
                    if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        throw SecurityException("Camera permission must be granted while MEIZZO is open before background flashlight control")
                    val enabled = parameters.optBoolean("enabled")
                    val manager = getSystemService(CameraManager::class.java)
                    val id = manager.cameraIdList.firstOrNull {
                        manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: throw IllegalStateException("No flashlight hardware available")
                    manager.setTorchMode(id, enabled)
                    torchEnabled = enabled
                    result.put("enabled", enabled)
                }
                "alarm" -> {
                    val enabled = parameters.optBoolean("enabled")
                    if (enabled) {
                        alarm?.release()
                        alarm = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
                            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30_000)
                        }
                    } else {
                        alarm?.stopTone(); alarm?.release(); alarm = null
                    }
                    result.put("enabled", enabled)
                }
                else -> error = "Unsupported command"
            }
        } catch (e: Exception) {
            error = e.message ?: "Command could not be completed"
        }
        webSocket.send(JSONObject().apply {
            put("type", "client.result"); put("requestId", message.optString("requestId"))
            put("result", result); if (error != null) put("error", error)
        }.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        stopHeartbeat()
        val task = object : Runnable {
            override fun run() {
                if (activeSocket !== webSocket) return
                val payload = runCatching { JSONObject().put("type", "heartbeat").put("telemetry", deviceMetrics()) }
                    .getOrElse { JSONObject().put("type", "heartbeat") }
                webSocket.send(payload.toString())
                handler.postDelayed(this, 15_000)
            }
        }
        heartbeat = task
        handler.postDelayed(task, 15_000)
    }

    private fun stopHeartbeat() {
        heartbeat?.let(handler::removeCallbacks)
        heartbeat = null
    }

    private fun scheduleReconnect() {
        if (!maintainConnection || reconnectPending) return
        reconnectPending = true
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        handler.postDelayed({
            reconnectPending = false
            if (maintainConnection && activeSocket == null) connect()
        }, (1L shl reconnectAttempt) * 1000L)
    }

    private fun authenticate(socket: WebSocket, token: String) {
        socket.send(JSONObject().apply {
            put("type", "auth.connect"); put("role", "client")
            put("clientId", clientId()); put("authToken", token)
        }.toString())
    }

    private fun registerPairingOffer(socket: WebSocket) {
        socket.send(JSONObject().apply {
            put("type", "client.register_pairing_offer")
            put("clientId", clientId()); put("pairingCode", pairingCode())
            put("expiresAtMs", System.currentTimeMillis() + 600_000)
            put("pairingAcceptanceEnabled", true); put("metadata", deviceMetadata())
        }.toString())
    }

    private fun clientId() = preferences.getString("client_id", null) ?: "client-${UUID.randomUUID()}".also {
        preferences.edit().putString("client_id", it).apply()
    }

    private fun pairingCode() = preferences.getString("pairing_code", null) ?: "%06d".format(java.security.SecureRandom().nextInt(1_000_000)).also {
        preferences.edit().putString("pairing_code", it).apply()
    }

    private fun deviceMetadata() = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL)
        put("androidVersion", Build.VERSION.RELEASE); put("clientVersion", BuildConfig.VERSION_NAME)
        put("platform", "Android"); put("deviceId", clientId())
    }

    private fun deviceMetrics() = JSONObject(deviceMetadata().toString()).apply {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        put("uptimeMs", SystemClock.elapsedRealtime()); put("timestamp", System.currentTimeMillis())
        put("flashlightEnabled", torchEnabled)
    }

    private fun broadcastMessage(message: String) {
        sendBroadcast(Intent(ACTION_MESSAGE).setPackage(packageName).putExtra(EXTRA_MESSAGE, message))
    }

    private fun broadcastState(state: String, connected: Boolean) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra(EXTRA_STATE, state).putExtra(EXTRA_CONNECTED, connected))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Client connection", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps the Client signaling connection available in the background."
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopHeartbeat()
        handler.removeCallbacksAndMessages(null)
        activeSocket?.close(1001, "Connection service stopped")
        activeSocket = null
        alarm?.stopTone(); alarm?.release(); alarm = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_MESSAGE = "com.meizzo.client.CONNECTION_MESSAGE"
        const val ACTION_STATE = "com.meizzo.client.CONNECTION_STATE"
        const val ACTION_STOP = "com.meizzo.client.CONNECTION_STOP"
        const val ACTION_REFRESH = "com.meizzo.client.CONNECTION_REFRESH"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATE = "state"
        const val EXTRA_CONNECTED = "connected"
        private const val CHANNEL_ID = "meizzo_client_connection"
        private const val NOTIFICATION_ID = 2501
        @Volatile var activeSocket: WebSocket? = null
            private set
    }
}
