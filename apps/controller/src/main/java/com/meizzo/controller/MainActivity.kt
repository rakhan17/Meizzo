package com.meizzo.controller

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
    private lateinit var status: TextView
    private lateinit var devicesLayout: LinearLayout
    private var socket: WebSocket? = null
    private val devices = linkedMapOf<String, JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        root.addView(TextView(this).apply { text = "MEIZZO CONTROLLER"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Pair multiple Clients, then manage each device independently."
            textSize = 16f
            setPadding(0, 16, 0, 8)
        })
        codeInput = EditText(this).apply { hint = "Six-digit pairing code"; inputType = 2; maxLines = 1 }
        root.addView(codeInput)
        root.addView(Button(this).apply { text = "Add device"; setOnClickListener { requestPairing() } })
        status = TextView(this).apply { text = "Offline"; textSize = 16f; setPadding(0, 16, 0, 16) }
        root.addView(status)
        devicesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(devicesLayout) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        connect()
    }

    private fun connect() {
        socket = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
            .newWebSocket(Request.Builder().url(BuildConfig.SIGNALING_URL).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    runOnUiThread { status.text = "Online · dashboard ready" }
                    authenticateExisting(webSocket)
                    requestDeviceList(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "controller.pairing_pending" -> runOnUiThread {
                            status.text = "Pairing request sent · approve it on the Client"
                        }
                        "controller.pairing_result" -> handlePairingResult(message)
                        "controller.device_list" -> {
                            for (index in 0 until message.optJSONArray("devices").length()) {
                                val device = message.getJSONArray("devices").getJSONObject(index)
                                devices[device.optString("clientId")] = device
                            }
                            renderDevices()
                        }
                        "device.status" -> {
                            devices[message.optString("clientId")] = message
                            renderDevices()
                        }
                        "device.result" -> runOnUiThread {
                            status.text = "${message.optString("clientId")}: ${message.optJSONObject("result") ?: message.optString("error")}"
                        }
                        "device.revoked" -> {
                            devices.remove(message.optString("clientId"))
                            renderDevices()
                        }
                        "error" -> runOnUiThread { status.text = "Error: ${message.optString("message")}" }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    runOnUiThread { status.text = "Offline · ${t.message ?: "connection failed"}" }
                }
            })
    }

    private fun authenticateExisting(webSocket: WebSocket) {
        preferences.all.keys
            .filter { it.startsWith("paired_") }
            .map { it.removePrefix("paired_") }
            .forEach { clientId ->
                secureStore.get("token_$clientId")?.let { token ->
                webSocket.send(JSONObject().apply {
                    put("type", "auth.connect")
                    put("role", "controller")
                    put("clientId", clientId)
                    put("authToken", token)
                }.toString())
                }
            }
    }

    private fun requestDeviceList(webSocket: WebSocket = socket ?: return) {
        webSocket.send(JSONObject().put("type", "controller.list_devices").toString())
    }

    private fun requestPairing() {
        val code = codeInput.text.toString()
        if (!Regex("\\d{6}").matches(code)) {
            status.text = "Enter exactly six digits"
            return
        }
        socket?.send(JSONObject().apply {
            put("type", "controller.request_pairing")
            put("controllerId", controllerId)
            put("pairingCode", code)
        }.toString())
        status.text = "Checking pairing code…"
    }

    private fun handlePairingResult(message: JSONObject) {
        val clientId = message.optString("clientId")
        if (message.optString("status") == "paired") {
            secureStore.put("token_$clientId", message.optString("authToken"))
            val stored = JSONObject(message.toString()).apply { remove("authToken") }
            preferences.edit().putString("paired_$clientId", stored.toString()).apply()
            devices[clientId] = message
            runOnUiThread { status.text = "Paired $clientId"; renderDevices() }
        } else {
            runOnUiThread { status.text = "Pairing ${message.optString("status")}: ${message.optString("reason")}" }
        }
    }

    private fun renderDevices() {
        runOnUiThread {
            devicesLayout.removeAllViews()
            if (devices.isEmpty()) {
                devicesLayout.addView(TextView(this).apply { text = "No paired devices"; textSize = 18f })
                return@runOnUiThread
            }
            devices.forEach { (clientId, device) ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 16, 0, 16)
                }
                card.addView(TextView(this).apply {
                    val metadata = device.optJSONObject("metadata")
                    val displayName = metadata?.optString("alias").takeUnless { it.isNullOrBlank() }
                        ?: metadata?.optString("model").takeUnless { it.isNullOrBlank() }
                        ?: clientId
                    text = "$displayName\n$clientId\nStatus: ${device.optString("status", "online")}"
                    textSize = 17f
                })
                card.addView(Button(this).apply {
                    text = "Rename device"
                    setOnClickListener {
                        val input = EditText(this@MainActivity).apply {
                            hint = "Device name"
                            setText(device.optJSONObject("metadata")?.optString("alias", ""))
                        }
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Rename device")
                            .setView(input)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ ->
                                socket?.send(JSONObject().apply {
                                    put("type", "controller.rename_device")
                                    put("clientId", clientId)
                                    put("alias", input.text.toString().trim())
                                }.toString())
                            }.show()
                    }
                })
                card.addView(Button(this).apply { text = "Refresh metrics"; setOnClickListener { sendSnapshot(clientId) } })
                card.addView(Button(this).apply { text = "Toggle flashlight"; setOnClickListener { sendCommand(clientId, "flashlight", JSONObject().put("enabled", true)) } })
                card.addView(Button(this).apply { text = "Start alarm"; setOnClickListener { sendCommand(clientId, "alarm", JSONObject().put("enabled", true)) } })
                card.addView(Button(this).apply { text = "Revoke access"; setOnClickListener { revoke(clientId) } })
                devicesLayout.addView(card)
            }
        }
    }

    private fun sendSnapshot(clientId: String) {
        socket?.send(JSONObject().apply { put("type", "controller.request_snapshot"); put("clientId", clientId) }.toString())
    }

    private fun sendCommand(clientId: String, command: String, parameters: JSONObject) {
        socket?.send(JSONObject().apply {
            put("type", "controller.command")
            put("clientId", clientId)
            put("command", command)
            put("parameters", parameters)
        }.toString())
    }

    private fun revoke(clientId: String) {
        socket?.send(JSONObject().apply { put("type", "controller.revoke_device"); put("clientId", clientId) }.toString())
    }
}
