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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 24)
            setBackgroundColor(Color.rgb(8, 8, 10))
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.meizzo_logo)
            contentDescription = "MEIZZO logo"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(-1, 120))
        root.addView(TextView(this).apply {
            text = "MEIZZO CONTROLLER"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        connectionLabel = TextView(this).apply {
            text = "● Connecting to MEIZZO"; textSize = 15f; setPadding(0, 10, 0, 8)
            setTextColor(Color.rgb(238, 190, 100))
        }
        root.addView(connectionLabel)
        summaryLabel = TextView(this).apply { textSize = 15f; setTextColor(Color.LTGRAY); setPadding(0, 0, 0, 16) }
        root.addView(summaryLabel)
        codeInput = EditText(this).apply {
            hint = "Six-digit pairing code"; inputType = 2; maxLines = 1
            setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
        }
        root.addView(codeInput)
        root.addView(Button(this).apply {
            text = "Add device"; setOnClickListener { requestPairing() }
        })
        devicesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(devicesLayout) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderDevices()
        connect()
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
                        "device.result" -> {
                            val id = message.optString("clientId")
                            if (message.optJSONObject("result") != null) {
                                val current = devices[id] ?: JSONObject().put("clientId", id)
                                current.put("metadata", current.optJSONObject("metadata") ?: JSONObject())
                                current.getJSONObject("metadata").put("telemetry", message.getJSONObject("result"))
                                devices[id] = current
                                renderDevices()
                            }
                        }
                        "device.revoked" -> {
                            devices.remove(message.optString("clientId")); renderDevices()
                        }
                        "error" -> runOnUiThread {
                            connectionLabel.text = "● Error · ${message.optString("message")}"
                            connectionLabel.setTextColor(Color.rgb(240, 120, 120))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    setConnection("Reconnecting", false)
                    scheduleReconnect()
                }
            })
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(25, 25, 29))
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
        devicesLayout.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) })
    }

    private fun showDetails(device: JSONObject) {
        val id = device.optString("clientId")
        val metadata = device.optJSONObject("metadata") ?: JSONObject()
        val telemetry = metadata.optJSONObject("telemetry") ?: JSONObject()
        val text = buildString {
            append("Status: ${device.optString("status", "offline").replaceFirstChar { it.uppercase() }}\n")
            append("Last seen: ${device.optLong("lastSeen", 0)}\n\n")
            append("Overview\n${metadata.optString("manufacturer", "Unavailable")} ${metadata.optString("model", "")}\n")
            append("Platform: ${metadata.optString("platform", "Android")}\n")
            append("OS: ${metadata.optString("androidVersion", "Unavailable")}\n\n")
            append("Battery: ${telemetry.optString("batteryPercent", "Unavailable")}%\n")
            append("Charging: ${telemetry.optString("charging", "Unavailable")}\n")
            append("Network: ${telemetry.optString("network", "Unavailable")}\n")
            append("Storage available: ${telemetry.optString("storageAvailableBytes", "Unavailable")} bytes\n")
            append("Uptime: ${telemetry.optString("uptimeMs", "Unavailable")} ms")
        }
        AlertDialog.Builder(this).setTitle(metadata.optString("alias").ifBlank { id }).setMessage(text)
            .setPositiveButton("Refresh telemetry") { _, _ -> sendSnapshot(id) }
            .setNeutralButton("Rename") { _, _ -> rename(id, metadata.optString("alias")) }
            .setNegativeButton("Close", null).show()
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

    private fun sendSnapshot(clientId: String) {
        socket?.send(JSONObject().apply { put("type", "controller.request_snapshot"); put("clientId", clientId) }.toString())
    }
}
