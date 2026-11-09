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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    private var torchEnabled = false
    private var alarm: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        layout.addView(TextView(this).apply { text = "MEIZZO CLIENT"; textSize = 28f })
        layout.addView(TextView(this).apply {
            text = "Pair this device with a Controller using the temporary code below."
            textSize = 16f
        })
        layout.addView(TextView(this).apply {
            text = pairingCode
            textSize = 44f
            setPadding(0, 24, 0, 24)
        })
        status = TextView(this).apply { text = "Connecting…"; textSize = 16f }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Stop pairing"
            setOnClickListener {
                socket?.close(1000, "Pairing stopped")
                status.text = "Pairing stopped"
            }
        })
        setContentView(layout)
        connect()
    }

    private fun connect() {
        socket = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
            .newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(JSONObject().apply {
                        put("type", "client.register_pairing_offer")
                        put("clientId", clientId)
                        put("pairingCode", pairingCode)
                        put("expiresAtMs", System.currentTimeMillis() + 600_000)
                        put("pairingAcceptanceEnabled", true)
                        put("metadata", deviceMetadata())
                    }.toString())
                    secureStore.get("auth_token")?.let { authenticate(webSocket, it) }
                    startHeartbeat(webSocket)
                    runOnUiThread { status.text = "Online · waiting for Controller approval" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "client.pairing_request" -> runOnUiThread {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Approve Controller?")
                                .setMessage("A Controller wants to manage this device.")
                                .setNegativeButton("Reject") { _, _ -> decide(webSocket, message, false) }
                                .setPositiveButton("Approve") { _, _ -> decide(webSocket, message, true) }
                                .setOnCancelListener { decide(webSocket, message, false) }
                                .show()
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
                        "device.revoked" -> runOnUiThread { status.text = "Access revoked by Controller" }
                        "error" -> runOnUiThread { status.text = "Error: ${message.optString("message")}" }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    runOnUiThread { status.text = "Offline · ${t.message ?: "connection failed"}" }
                }
            })
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        Thread {
            while (socket != null) {
                Thread.sleep(15_000)
                if (socket != null) webSocket.send(JSONObject().apply { put("type", "heartbeat") }.toString())
            }
        }.start()
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

    private fun handleCommand(webSocket: WebSocket, message: JSONObject) {
        val result = JSONObject()
        var error: String? = null
        when (message.optString("command")) {
            "flashlight" -> {
                val enabled = message.optBoolean("enabled")
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
                val enabled = message.optBoolean("enabled")
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
    }

    private fun deviceMetrics() = JSONObject(deviceMetadata().toString()).apply {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        put("charging", battery.isChargingCompat())
        val storage = StatFs(filesDir.absolutePath)
        put("storageAvailableBytes", storage.availableBytes)
        put("storageTotalBytes", storage.totalBytes)
        put("uptimeMs", SystemClock.elapsedRealtime())
        put("network", networkType())
        put("timestamp", System.currentTimeMillis())
        put("flashlightEnabled", torchEnabled)
    }

    private fun BatteryManager.isChargingCompat(): Boolean {
        val intent = this@MainActivity.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in
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
}
