package com.meizzo.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationCompat

/**
 * Raises the Client process importance while its device connection is active.
 *
 * Owns the Client WebSocket, reconnect policy, and heartbeat independently of
 * the Activity. Android may still stop it after force-stop or OEM process eviction.
 */
class ConnectionForegroundService : Service() {
    private var alarmTrack: AudioTrack? = null
    private var alarmActive = false
    private var connectionActive = true
    private val socketHandler = Handler(Looper.getMainLooper())
    private val httpClient by lazy {
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }
    private var socket: WebSocket? = null
    private var socketOpenResponse: Response? = null
    private var socketUrl: String? = null
    private var clientListener: WebSocketListener? = null
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private val reconnectTask = Runnable {
        reconnectScheduled = false
        if (socket == null) connectSocket()
    }
    private var heartbeatSocket: WebSocket? = null
    private val queuedMessages = ArrayDeque<String>()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            socketHandler.post {
                reconnectAttempt = 0
                if (socket == null) {
                    socketHandler.removeCallbacks(reconnectTask)
                    reconnectScheduled = false
                    connectSocket()
                }
            }
        }
        override fun onLost(network: android.net.Network) {
            socketHandler.post {
                socket?.cancel()
                socket = null
                socketOpenResponse = null
                heartbeatSocket = null
                scheduleSocketReconnect()
            }
        }
    }

    inner class LocalBinder : android.os.Binder() {
        fun service(): ConnectionForegroundService = this@ConnectionForegroundService
    }
    private val binder = LocalBinder()

    fun attachSocketListener(url: String, listener: WebSocketListener) {
        clientListener = listener
        socketUrl = url
        val activeSocket = socket
        val openResponse = socketOpenResponse
        if (activeSocket != null && openResponse != null) listener.onOpen(activeSocket, openResponse)
        while (queuedMessages.isNotEmpty()) {
            val message = queuedMessages.removeFirst()
            socket?.let { listener.onMessage(it, message) }
        }
        if (socket == null) connectSocket()
    }

    fun detachSocketListener() {
        clientListener = null
    }

    fun currentSocket(): WebSocket? = socket

    private fun connectSocket() {
        val url = socketUrl ?: return
        if (socket != null || reconnectScheduled) return
        reconnectScheduled = false
        socket = httpClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket !== webSocket) { webSocket.cancel(); return }
                reconnectAttempt = 0
                reconnectScheduled = false
                socketOpenResponse = response
                clientListener?.onOpen(webSocket, response)
                startSocketHeartbeat(webSocket)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket) return
                val listener = clientListener
                if (listener != null) listener.onMessage(webSocket, text)
                else if (!handleDetachedCommand(webSocket, text)) {
                    if (queuedMessages.size >= MAX_QUEUED_MESSAGES) queuedMessages.removeFirst()
                    queuedMessages.addLast(text)
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                clientListener?.onClosed(webSocket, code, reason)
                socket = null
                socketOpenResponse = null
                heartbeatSocket = null
                scheduleSocketReconnect()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                clientListener?.onFailure(webSocket, t, response)
                socket = null
                socketOpenResponse = null
                heartbeatSocket = null
                scheduleSocketReconnect()
            }
        })
    }

    private fun handleDetachedCommand(webSocket: WebSocket, raw: String): Boolean {
        val message = try { JSONObject(raw) } catch (_: Exception) { return false }
        if (message.optString("type") != "client.command") return false
        if (message.optString("command") == "alarm") return handleDetachedAlarmCommand(webSocket, raw)
        val command = message.optString("command")
        val parameters = message.optJSONObject("parameters") ?: JSONObject()
        val result = JSONObject()
        var error: String? = null
        val accessibility = RemoteAccessibilityService.instance
        when (command) {
            "volume_up", "volume_down", "volume_mute", "volume_unmute" -> {
                val adjustment = when (command) {
                    "volume_up" -> AudioManager.ADJUST_RAISE
                    "volume_down" -> AudioManager.ADJUST_LOWER
                    "volume_mute" -> AudioManager.ADJUST_MUTE
                    else -> AudioManager.ADJUST_UNMUTE
                }
                getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustment, AudioManager.FLAG_SHOW_UI)
                result.put("accepted", true)
            }
            "back", "home", "recents", "notifications", "quick_settings", "power_dialog" -> {
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (!accessibility.navigate(command)) error = "Android rejected the navigation action"
                else result.put("accepted", true)
            }
            "lock_screen" -> {
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (!accessibility.lockScreen()) error = "Screen lock is unavailable"
                else result.put("locked", true)
            }
            "tap" -> {
                val x = parameters.optDouble("x", Double.NaN)
                val y = parameters.optDouble("y", Double.NaN)
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) error = "Invalid tap coordinates"
                else if (!accessibility.tap(x, y)) error = "Android rejected the remote tap"
                else result.put("accepted", true)
            }
            "swipe" -> {
                val sx = parameters.optDouble("startX", Double.NaN)
                val sy = parameters.optDouble("startY", Double.NaN)
                val ex = parameters.optDouble("endX", Double.NaN)
                val ey = parameters.optDouble("endY", Double.NaN)
                val valid = listOf(sx, sy, ex, ey).all { it.isFinite() && it in 0.0..1.0 }
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (!valid) error = "Invalid swipe coordinates"
                else if (!accessibility.swipe(sx, sy, ex, ey, parameters.optLong("durationMs", 350L))) error = "Android rejected the remote swipe"
                else result.put("accepted", true)
            }
            "scroll" -> {
                val dx = parameters.optDouble("deltaX", Double.NaN)
                val dy = parameters.optDouble("deltaY", Double.NaN)
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (!dx.isFinite() || !dy.isFinite() || dx !in -0.35..0.35 || dy !in -0.35..0.35) error = "Invalid scroll delta"
                else if (!accessibility.scroll(dx, dy)) error = "Android rejected the remote scroll"
                else result.put("accepted", true)
            }
            "text_input" -> {
                val value = parameters.optString("text")
                if (accessibility == null) error = "Enable MEIZZO in Android Accessibility settings first"
                else if (value.isEmpty() || value.length > 2_000) error = "Text input must contain 1–2,000 characters"
                else if (!accessibility.appendText(value)) error = "No editable non-password field is focused"
                else result.put("charactersInserted", value.length)
            }
            else -> return false
        }
        webSocket.send(JSONObject().apply {
            put("type", "client.result")
            put("requestId", message.optString("requestId"))
            put("result", result)
            if (!error.isNullOrBlank()) put("error", error)
        }.toString())
        return true
    }
    private fun handleDetachedAlarmCommand(webSocket: WebSocket, raw: String): Boolean {
        val message = try { JSONObject(raw) } catch (_: Exception) { return false }
        if (message.optString("type") != "client.command" || message.optString("command") != "alarm") return false
        val requestId = message.optString("requestId")
        val enabled = message.optJSONObject("parameters")?.optBoolean("enabled", false) ?: false
        socketHandler.post {
            var error: String? = null
            if (requestId.isBlank()) {
                error = "Alarm command is missing its request identifier"
            } else {
                try {
                    if (enabled) startAlarm() else stopAlarm()
                    val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                            if (alarmActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                    } else 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, buildNotification(), serviceTypes)
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                } catch (exception: Exception) {
                    error = exception.message ?: "Unable to control the remote alarm"
                    stopAlarm()
                }
            }
            if (socket === webSocket) {
                webSocket.send(JSONObject().apply {
                    put("type", "client.result")
                    put("requestId", requestId)
                    put("result", JSONObject().put("enabled", alarmActive))
                    if (!error.isNullOrBlank()) put("error", error)
                }.toString())
            }
        }
        return true
    }

    private fun startSocketHeartbeat(webSocket: WebSocket) {
        if (heartbeatSocket === webSocket) return
        heartbeatSocket = webSocket
        socketHandler.postDelayed(object : Runnable {
            override fun run() {
                if (socket !== webSocket || heartbeatSocket !== webSocket) return
                webSocket.send(JSONObject().put("type", "heartbeat").toString())
                socketHandler.postDelayed(this, SOCKET_HEARTBEAT_MS)
            }
        }, SOCKET_HEARTBEAT_MS)
    }

    private fun scheduleSocketReconnect() {
        if (reconnectScheduled || socketUrl.isNullOrBlank()) return
        reconnectScheduled = true
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        socketHandler.removeCallbacks(reconnectTask)
        socketHandler.postDelayed(reconnectTask, (1L shl reconnectAttempt) * 1000L)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val connectivity = getSystemService(ConnectivityManager::class.java)
        try { connectivity.registerDefaultNetworkCallback(networkCallback) } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart has no live WebSocket owner to restore yet. Do not
        // show a misleading foreground notification after process recreation.
        if (intent == null) {
            stopAlarm()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_CONNECTION_STOP) {
            connectionActive = false
            socketUrl = null
            reconnectScheduled = false
            socketHandler.removeCallbacksAndMessages(null)
            reconnectScheduled = false
            socket?.close(1000, "Connection stopped")
            socket = null
            socketOpenResponse = null
            heartbeatSocket = null
            queuedMessages.clear()
            if (alarmActive) {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification())
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        connectionActive = true
        var alarmRequestId: String? = null
        var alarmError: String? = null
        when (intent.action) {
            ACTION_ALARM_ON -> {
                alarmRequestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                try {
                    startAlarm()
                } catch (exception: Exception) {
                    stopAlarm()
                    alarmError = exception.message ?: "Android could not start alarm audio"
                }
            }
            ACTION_ALARM_OFF -> {
                alarmRequestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                stopAlarm()
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (alarmActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!alarmRequestId.isNullOrBlank()) {
            val activeSocket = socket
            if (clientListener == null && activeSocket != null) {
                activeSocket.send(JSONObject().apply {
                    put("type", "client.result")
                    put("requestId", alarmRequestId)
                    put("result", JSONObject().put("enabled", alarmActive))
                    if (!alarmError.isNullOrBlank()) put("error", alarmError)
                }.toString())
            } else {
                sendBroadcast(Intent(ACTION_ALARM_RESULT).setPackage(packageName).apply {
                    putExtra(EXTRA_REQUEST_ID, alarmRequestId)
                    putExtra(EXTRA_ALARM_ENABLED, alarmActive)
                    putExtra(EXTRA_ALARM_ERROR, alarmError)
                })
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        socketHandler.removeCallbacksAndMessages(null)
        socket?.close(1000, "Connection service destroyed")
        socket = null
        socketOpenResponse = null
        heartbeatSocket = null
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
        stopAlarm()
        super.onDestroy()
    }

    private fun startAlarm() {
        if (alarmTrack?.state == AudioTrack.STATE_INITIALIZED && alarmActive) return
        stopAlarm()
        val sampleRate = 22_050
        val sampleCount = sampleRate
        val samples = ShortArray(sampleCount) { index ->
            val time = index.toDouble() / sampleRate
            val pulse = if ((time % 0.8) < 0.55) 1.0 else 0.18
            (kotlin.math.sin(2.0 * Math.PI * 880.0 * time) * 18_000.0 * pulse).toInt().toShort()
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("Android could not initialize alarm audio")
        }
        val written = track.write(samples, 0, samples.size)
        if (written != samples.size) {
            track.release()
            throw IllegalStateException("Android could not prepare alarm audio")
        }
        track.setLoopPoints(0, samples.size, -1)
        track.play()
        alarmTrack = track
        alarmActive = true
    }

    private fun stopAlarm() {
        alarmActive = false
        alarmTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
                // Audio may already have been stopped by the platform.
            } finally {
                track.release()
            }
        }
        alarmTrack = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MEIZZO device connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when MEIZZO keeps a paired device connection available."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (alarmActive) android.R.drawable.ic_lock_idle_alarm else android.R.drawable.stat_notify_sync)
            .setContentTitle(
                when {
                    alarmActive -> "MEIZZO remote alarm is ringing"
                    connectionActive -> "MEIZZO Client is active"
                    else -> "MEIZZO alarm is active"
                }
            )
            .setContentText(
                when {
                    alarmActive && connectionActive -> "Alarm is playing · tap Stop alarm to silence this device"
                    alarmActive -> "Alarm continues until stopped locally or by the Controller"
                    else -> "MEIZZO is active in the background"
                }
            )
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (alarmActive) {
            val stopAlarm = PendingIntent.getService(
                this,
                1,
                Intent(this, ConnectionForegroundService::class.java).setAction(ACTION_ALARM_OFF),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Stop alarm", stopAlarm)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_CONNECTION_STOP = "com.meizzo.client.action.CONNECTION_STOP"
        const val ACTION_ALARM_ON = "com.meizzo.client.action.ALARM_ON"
        const val ACTION_ALARM_OFF = "com.meizzo.client.action.ALARM_OFF"
        const val ACTION_ALARM_RESULT = "com.meizzo.client.action.ALARM_RESULT"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_ALARM_ENABLED = "alarm_enabled"
        const val EXTRA_ALARM_ERROR = "alarm_error"
        private const val CHANNEL_ID = "meizzo_device_connection"
        private const val NOTIFICATION_ID = 2501
        private const val SOCKET_HEARTBEAT_MS = 15_000L
        private const val MAX_QUEUED_MESSAGES = 100
    }
}
