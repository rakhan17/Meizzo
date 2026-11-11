package com.meizzo.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Owns the Controller signaling socket independently of the UI Activity. */
class ControllerConnectionForegroundService : Service() {
    private val preferences by lazy { getSharedPreferences("MainActivity", Context.MODE_PRIVATE) }
    private val secureStore by lazy { SecureStore(this) }
    private val client by lazy { OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build() }
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var reconnectPending = false
    private var maintainConnection = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                maintainConnection = false
                handler.removeCallbacksAndMessages(null)
                activeSocket?.close(1000, "Controller connection stopped")
                activeSocket = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> activeSocket?.send(JSONObject().put("type", "controller.list_devices").toString())
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
            .setContentTitle("MEIZZO Controller is active")
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
                preferences.all.keys.filter { it.startsWith("paired_") }.forEach { key ->
                    val id = key.removePrefix("paired_")
                    secureStore.get("token_$id")?.takeIf { it.isNotBlank() }?.let { token ->
                        webSocket.send(JSONObject().apply {
                            put("type", "auth.connect"); put("role", "controller")
                            put("clientId", id); put("authToken", token)
                        }.toString())
                    }
                }
                webSocket.send(JSONObject().put("type", "controller.list_devices").toString())
                broadcastState("Connected to MEIZZO", true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeSocket !== webSocket) return
                broadcastMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket === webSocket) {
                    activeSocket = null
                    broadcastState("Connection closed · reconnecting", false)
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (activeSocket === webSocket) {
                    activeSocket = null
                    broadcastState("Connection unavailable · reconnecting", false)
                    scheduleReconnect()
                }
            }
        })
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

    private fun broadcastMessage(message: String) {
        sendBroadcast(Intent(ACTION_MESSAGE).setPackage(packageName).putExtra(EXTRA_MESSAGE, message))
    }

    private fun broadcastState(state: String, connected: Boolean) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra(EXTRA_STATE, state).putExtra(EXTRA_CONNECTED, connected))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Controller connection", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps the Controller signaling connection available in the background."
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        activeSocket?.close(1001, "Connection service stopped")
        activeSocket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_MESSAGE = "com.meizzo.controller.CONNECTION_MESSAGE"
        const val ACTION_STATE = "com.meizzo.controller.CONNECTION_STATE"
        const val ACTION_STOP = "com.meizzo.controller.CONNECTION_STOP"
        const val ACTION_REFRESH = "com.meizzo.controller.CONNECTION_REFRESH"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATE = "state"
        const val EXTRA_CONNECTED = "connected"
        private const val CHANNEL_ID = "meizzo_controller_connection"
        private const val NOTIFICATION_ID = 2502
        @Volatile var activeSocket: WebSocket? = null
            private set
    }
}
