package com.meizzo.client

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.ServiceConnection
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.StatFs
import android.os.SystemClock
import android.os.IBinder
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray
import java.security.SecureRandom
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper

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
    private var connectionService: ConnectionForegroundService? = null
    private val socket: WebSocket? get() = connectionService?.currentSocket()
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connectionService = (binder as? ConnectionForegroundService.LocalBinder)?.service()
            connect()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
        }
    }
    private var serviceBound = false
    private var torchEnabled = false
    private val updateManager by lazy { ClientUpdateManager(this) }
    private var pendingUpdateApk: File? = null
    private var darkMode by mutableStateOf(true)
    private val pendingSessionDialogs = mutableMapOf<String, ClientGlassDialogRequest>()
    private var glassDialog by mutableStateOf<ClientGlassDialogRequest?>(null)
    private var pendingTorchCommand: Pair<WebSocket, JSONObject>? = null
    private var pendingRemoteFileBrowse: Pair<WebSocket, JSONObject>? = null
    private val pendingAlarmCommands = ConcurrentHashMap<String, WebSocket>()
    private val alarmResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val requestId = intent?.getStringExtra(ConnectionForegroundService.EXTRA_REQUEST_ID) ?: return
            val webSocket = pendingAlarmCommands.remove(requestId) ?: return
            val error = intent.getStringExtra(ConnectionForegroundService.EXTRA_ALARM_ERROR)
            val enabled = intent.getBooleanExtra(ConnectionForegroundService.EXTRA_ALARM_ENABLED, false)
            webSocket.send(JSONObject().apply {
                put("type", "client.result")
                put("requestId", requestId)
                put("result", JSONObject().put("enabled", enabled))
                if (!error.isNullOrBlank()) put("error", error)
            }.toString())
        }
    }
    private val cameraPermissionRequestCode = 4102
    private val mediaCameraPermissionRequestCode = 4107
    private val screenCaptureRequestCode = 4106
    private val mediaAudioPermissionRequestCode = 4110
    private val filePickRequestCode = 4108
    private val fileSaveRequestCode = 4109
    private val remoteFileTreeRequestCode = 4111
    private var pendingIncomingFileName: String? = null
    private var pendingIncomingFileMime: String = "application/octet-stream"
    private var fileTransferCoordinator: FileTransferCoordinator? = null
    private var pendingMediaSessionId: String? = null
    private var pendingMediaKind: String? = null
    private var pendingAudioSessionId: String? = null
    private var pendingAudioKind: String? = null
    private var pendingAudioProjectionData: android.content.Intent? = null
    private var activeMediaSessionId: String? = null
    private var activeMediaKind: String? = null
    private var rtcMediaClient: RtcMediaClient? = null
    private val pendingMediaSignals = mutableMapOf<String, MutableList<JSONObject>>()
    private val iceServersBySession = mutableMapOf<String, JSONArray>()

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
        content.addView(uiActionButton("Send file to Controller", false) {
            if (fileTransferCoordinator == null) {
                status.text = "Request and approve a file-transfer session first"
            } else {
                startActivityForResult(
                    android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    },
                    filePickRequestCode
                )
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        content.addView(uiActionButton("Enable remote interaction", false) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        content.addView(uiActionButton("Stop alarm on this device", false) {
            startForegroundService(
                Intent(this, ConnectionForegroundService::class.java)
                    .setAction(ConnectionForegroundService.ACTION_ALARM_OFF)
            )
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
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
        (status.parent as? ViewGroup)?.removeView(status)
        darkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = if (darkMode) Color.rgb(8, 8, 12) else Color.rgb(245, 245, 248)
        window.navigationBarColor = window.statusBarColor
        setContentView(ComposeView(this).apply {
            setContent {
                ClientGlassScreen(
                    pairingCode = pairingCode,
                    statusView = status,
                    darkMode = darkMode,
                    onCheckForUpdates = { checkForUpdate() },
                    dialog = glassDialog,
                    onDialogConfirm = {
                        val request = glassDialog
                        glassDialog = null
                        request?.onConfirm?.invoke()
                    },
                    onDialogDismiss = {
                        val request = glassDialog
                        glassDialog = null
                        request?.onDismiss?.invoke()
                    }
                )
            }
        })
        ContextCompat.registerReceiver(
            this,
            alarmResultReceiver,
            IntentFilter(ConnectionForegroundService.ACTION_ALARM_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startConnectionForegroundService()
        serviceBound = bindService(
            Intent(this, ConnectionForegroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
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
        showGlassPrompt(
            title = "Enable Find My location?",
            message = "When enabled, MEIZZO includes Android's last-known location in device telemetry visible to your paired Controllers. It does not continuously request GPS updates. Coordinates can be approximate or stale, and Android may not have a cached location.",
            dismissLabel = "Cancel",
            confirmLabel = "Continue",
            onConfirm = {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    4104
                )
            }
        )
    }

    private fun startConnectionForegroundService() {
        val intent = android.content.Intent(this, ConnectionForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun showGlassPrompt(
        title: String,
        message: String,
        dismissLabel: String = "Cancel",
        confirmLabel: String = "Continue",
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit
    ) {
        runOnUiThread {
            val id = UUID.randomUUID().toString()
            glassDialog = ClientGlassDialogRequest(
                id = id,
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                onConfirm = {
                    if (glassDialog?.id == id) glassDialog = null
                    onConfirm()
                },
                onDismiss = {
                    if (glassDialog?.id == id) glassDialog = null
                    onDismiss()
                }
            )
        }
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
            showGlassPrompt(
                title = "Update ready to install",
                message = "MEIZZO has permission to start the Android installer. Confirm installation to continue.",
                dismissLabel = "Later",
                confirmLabel = "Install",
                onConfirm = {
                    pendingUpdateApk = null
                    updateManager.install(pending)
                }
            )
        } else {
            status.text = "Install permission is required to continue."
            showGlassPrompt(
                title = "Allow app updates",
                message = "Enable 'Allow from this source' for MEIZZO in Android settings, then return here.",
                dismissLabel = "Later",
                confirmLabel = "Open settings",
                onConfirm = { updateManager.requestInstallPermission() }
            )
        }
    }

    private fun connect() {
        if (isFinishing) return
        connectionService?.attachSocketListener(BuildConfig.SIGNALING_URL, createSocketListener())
    }

    private fun createSocketListener(): WebSocketListener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (socket !== webSocket) {
                        webSocket.cancel()
                        return
                    }
                    // Re-authenticate before refreshing an offer for an already-paired Client.
                    secureStore.get("auth_token")?.let { authenticate(webSocket, it) }
                    registerPairingOffer(webSocket)
                    runOnUiThread { status.text = "Connected · registering device" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "auth.connected" -> {
                            runOnUiThread { status.text = "Online · waiting for Controller" }
                        }
                        "client.pairing_request" -> runOnUiThread {
                            glassDialog = ClientGlassDialogRequest(
                                id = "pairing:${message.optString("requestId")}",
                                title = "Approve Controller?",
                                message = "A Controller wants to manage this device. Only approve if you initiated this pairing.",
                                confirmLabel = "Approve",
                                dismissLabel = "Reject",
                                onConfirm = { decide(webSocket, message, true) },
                                onDismiss = { decide(webSocket, message, false) }
                            )
                        }
                        "client.session_status" -> {
                            val sessionId = message.optString("sessionId")
                            val state = message.optString("state")
                            val sessionType = message.optString("sessionType").ifBlank { "device" }
                            when (state) {
                                "pending" -> runOnUiThread {
                                    if (sessionId.isBlank() || pendingSessionDialogs.containsKey(sessionId)) return@runOnUiThread
                                    val dialog = ClientGlassDialogRequest(
                                        id = "session:$sessionId",
                                        title = "Allow $sessionType session?",
                                        message = "A paired Controller is requesting permission to start a $sessionType session. Nothing starts until you approve.",
                                        confirmLabel = "Allow",
                                        dismissLabel = "Deny",
                                        onConfirm = {
                                            pendingSessionDialogs.remove(sessionId)
                                            if (glassDialog?.id == "session:$sessionId") glassDialog = null
                                            decideSession(webSocket, sessionId, true)
                                        },
                                        onDismiss = {
                                            pendingSessionDialogs.remove(sessionId)
                                            if (glassDialog?.id == "session:$sessionId") glassDialog = null
                                            decideSession(webSocket, sessionId, false)
                                        }
                                    )
                                    pendingSessionDialogs[sessionId] = dialog
                                    glassDialog = dialog
                                }
                                "approved" -> runOnUiThread {
                                    status.text = "$sessionType session approved"
                                    when (sessionType) {
                                        "screen", "camera" -> beginMediaSession(sessionId, sessionType)
                                        "files" -> beginFileTransferSession(sessionId)
                                    }
                                }
                                "rejected", "failed", "ended" -> runOnUiThread {
                                    pendingSessionDialogs.remove(sessionId)
                                    if (glassDialog?.id == "session:$sessionId") glassDialog = null
                                    iceServersBySession.remove(sessionId)
                                    if (sessionId == activeMediaSessionId || sessionId == pendingMediaSessionId) {
                                        stopActiveOutputs()
                                    } else if (sessionType == "interaction") {
                                        stopPeripheralOutputs()
                                    }
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
                        "media.ice_servers" -> {
                            val sessionId = message.optString("sessionId")
                            message.optJSONArray("iceServers")?.let { iceServersBySession[sessionId] = it }
                        }
                        "media.signal" -> {
                            val sessionId = message.optString("sessionId")
                            val signal = message.optJSONObject("signal")
                            if (signal != null && (sessionId == activeMediaSessionId || sessionId == pendingMediaSessionId)) {
                                val media = rtcMediaClient
                                if (media != null && sessionId == activeMediaSessionId) media.onSignal(signal)
                                else pendingMediaSignals.getOrPut(sessionId) { mutableListOf() }.add(signal)
                            }
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

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (socket !== webSocket) return
                    runOnUiThread {
                        if (!isFinishing) status.text = "Connection closed · reconnecting"
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (socket !== webSocket) return
                    runOnUiThread {
                        pendingSessionDialogs.clear()
                        glassDialog = null
                        stopActiveOutputs()
                        status.text = connectionFailureMessage(t)
                    }
                }
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

    private fun beginFileTransferSession(sessionId: String) {
        fileTransferCoordinator?.close()
        fileTransferCoordinator = null
        pendingMediaSignals.clear()
        rtcMediaClient?.close()
        rtcMediaClient = null
        activeMediaSessionId = sessionId
        activeMediaKind = "files"
        rtcMediaClient = RtcMediaClient(
            this,
            { signal ->
                socket?.send(JSONObject().apply {
                    put("type", "media.signal")
                    put("sessionId", sessionId)
                    put("signal", signal)
                }.toString())
            },
            { reason ->
                runOnUiThread {
                    status.text = "File-transfer session error · $reason"
                    terminateMediaSession(sessionId, "media_error")
                    stopMediaPublisher()
                }
            },
            onDataChannel = { channel ->
                runOnUiThread {
                    fileTransferCoordinator?.close()
                    fileTransferCoordinator = FileTransferCoordinator(
                        this,
                        channel,
                        { name, size, mime ->
                            runOnUiThread {
                                pendingIncomingFileName = name
                                pendingIncomingFileMime = mime
                                showGlassPrompt(
                                    title = "Receive file?",
                                    message = "$name · ${if (size >= 0) "${size / 1024} KB" else "size unknown"}\nSave this file to this device?",
                                    dismissLabel = "Decline",
                                    confirmLabel = "Choose destination",
                                    onDismiss = { fileTransferCoordinator?.rejectIncoming() },
                                    onConfirm = {
                                        startActivityForResult(
                                            android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                                type = pendingIncomingFileMime
                                                putExtra(android.content.Intent.EXTRA_TITLE, pendingIncomingFileName)
                                            },
                                            fileSaveRequestCode
                                        )
                                    }
                                )
                            }
                        },
                        { message -> runOnUiThread { status.text = message } }
                    )
                }
            },
            iceServersJson = iceServersBySession.remove(sessionId)
        )
        rtcMediaClient?.startFiles(sessionId)
        flushPendingMediaSignals(sessionId)
        status.text = "Secure file-transfer session · waiting for Controller"
    }

    private fun beginMediaSession(sessionId: String, kind: String) {
        pendingMediaSessionId = sessionId
        pendingMediaKind = kind
        if (kind == "screen") {
            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            startActivityForResult(manager.createScreenCaptureIntent(), screenCaptureRequestCode)
            status.text = "Waiting for Android screen-sharing consent"
            return
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), mediaCameraPermissionRequestCode)
        } else {
            startMediaPublisher(sessionId, kind, null)
        }
    }

    private fun startMediaPublisher(sessionId: String, kind: String, projectionData: android.content.Intent?) {
        showGlassPrompt(
            title = "Share microphone audio?",
            message = "MEIZZO can include live microphone audio in this approved screen or camera session. This is microphone capture, not system-app audio. Choose video only if you do not want to share microphone sound.",
            dismissLabel = "Video only",
            confirmLabel = "Allow audio",
            onDismiss = { startMediaPublisherInternal(sessionId, kind, projectionData, false) },
            onConfirm = {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startMediaPublisherInternal(sessionId, kind, projectionData, true)
                } else {
                    pendingAudioSessionId = sessionId
                    pendingAudioKind = kind
                    pendingAudioProjectionData = projectionData
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), mediaAudioPermissionRequestCode)
                }
            }
        )
    }

    private fun startMediaPublisherInternal(
        sessionId: String,
        kind: String,
        projectionData: android.content.Intent?,
        includeAudio: Boolean
    ) {
        rtcMediaClient?.close()
        activeMediaSessionId = sessionId
        activeMediaKind = kind
        rtcMediaClient = RtcMediaClient(
            this,
            { signal ->
                socket?.send(JSONObject().apply {
                    put("type", "media.signal")
                    put("sessionId", sessionId)
                    put("signal", signal)
                }.toString())
            },
            { reason ->
                runOnUiThread {
                    status.text = "Media session error · $reason"
                    terminateMediaSession(sessionId, "media_error")
                    stopMediaPublisher()
                }
            },
            iceServersJson = iceServersBySession.remove(sessionId)
        )
        if (kind == "screen" || kind == "camera") {
            MediaProjectionForegroundService.start(this, kind == "screen", kind == "camera", includeAudio)
        }
        if (kind == "screen") {
            Handler(Looper.getMainLooper()).postDelayed({
                if (activeMediaSessionId == sessionId) {
                    rtcMediaClient?.start(sessionId, kind, projectionData, includeAudio)
                    flushPendingMediaSignals(sessionId)
                }
            }, 450)
        } else {
            rtcMediaClient?.start(sessionId, kind, projectionData, includeAudio)
            flushPendingMediaSignals(sessionId)
        }
        status.text = "Publishing $kind stream · waiting for Controller"
    }

    private fun flushPendingMediaSignals(sessionId: String) {
        pendingMediaSignals.remove(sessionId)?.forEach { rtcMediaClient?.onSignal(it) }
    }

    private fun terminateMediaSession(sessionId: String, reason: String) {
        socket?.send(JSONObject().apply {
            put("type", "client.end_session")
            put("sessionId", sessionId)
            put("reason", reason)
        }.toString())
    }

    private fun stopMediaPublisher() {
        pendingMediaSignals.clear()
        fileTransferCoordinator?.close()
        fileTransferCoordinator = null
        rtcMediaClient?.close()
        rtcMediaClient = null
        activeMediaSessionId = null
        activeMediaKind = null
        pendingMediaSessionId = null
        pendingMediaKind = null
        pendingAudioSessionId = null
        pendingAudioKind = null
        pendingAudioProjectionData = null
        MediaProjectionForegroundService.stop(this)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            screenCaptureRequestCode -> {
                val sessionId = pendingMediaSessionId
                val kind = pendingMediaKind
                if (resultCode == RESULT_OK && data != null && sessionId != null && kind == "screen") {
                    startMediaPublisher(sessionId, "screen", data)
                } else {
                    status.text = "Screen sharing was not allowed"
                    if (sessionId != null) terminateMediaSession(sessionId, "screen_capture_consent_denied")
                    pendingMediaSessionId = null
                    pendingMediaKind = null
                }
            }
            filePickRequestCode -> {
                if (resultCode == RESULT_OK && data?.data != null) {
                    try {
                        val selectedUri = data.data!!
                        val persistableFlags = (data.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (persistableFlags != 0) {
                            try { contentResolver.takePersistableUriPermission(selectedUri, persistableFlags) } catch (_: SecurityException) { }
                        }
                        fileTransferCoordinator?.sendFile(selectedUri)
                    } catch (error: Exception) {
                        status.text = "Could not send file · ${error.message ?: "storage error"}"
                    }
                }
            }
            remoteFileTreeRequestCode -> {
                val pending = pendingRemoteFileBrowse
                pendingRemoteFileBrowse = null
                val treeUri = data?.data
                if (resultCode == RESULT_OK && treeUri != null && pending != null) {
                    try {
                        val grantFlags = (data?.flags ?: 0) and (
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        if ((grantFlags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
                            throw SecurityException("Android did not grant read access to the selected folder")
                        }
                        contentResolver.takePersistableUriPermission(treeUri, grantFlags)
                        val fileAccessPreferences = getSharedPreferences("meizzo_remote_file_access", MODE_PRIVATE)
                        val previousTree = fileAccessPreferences.getString("tree_uri", null)
                        if (!previousTree.isNullOrBlank() && previousTree != treeUri.toString()) {
                            try {
                                val previousUri = android.net.Uri.parse(previousTree)
                                val previousGrant = contentResolver.persistedUriPermissions
                                    .firstOrNull { it.uri == previousUri }
                                if (previousGrant != null) {
                                    var previousFlags = 0
                                    if (previousGrant.isReadPermission) {
                                        previousFlags = previousFlags or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    if (previousGrant.isWritePermission) {
                                        previousFlags = previousFlags or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    }
                                    if (previousFlags != 0) {
                                        contentResolver.releasePersistableUriPermission(previousUri, previousFlags)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        fileAccessPreferences.edit().putString("tree_uri", treeUri.toString()).apply()
                        val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                        val requestedId = pending.second.optJSONObject("parameters")
                            ?.optString("documentId")?.takeIf { it.isNotBlank() } ?: rootId
                        sendRemoteFileListing(pending.first, pending.second, treeUri, requestedId)
                    } catch (exception: Exception) {
                        sendCommandFailure(pending.first, pending.second, exception.message ?: "Unable to access the selected folder")
                    }
                } else if (pending != null) {
                    sendCommandFailure(pending.first, pending.second, "No folder was selected")
                }
            }
            fileSaveRequestCode -> {
                val destination = data?.data
                if (resultCode == RESULT_OK && destination != null) {
                    try {
                        val persistableFlags = (data?.flags ?: 0) and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        if (persistableFlags != 0) {
                            try { contentResolver.takePersistableUriPermission(destination, persistableFlags) } catch (_: SecurityException) { }
                        }
                        fileTransferCoordinator?.acceptIncoming(destination)
                    } catch (error: Exception) {
                        fileTransferCoordinator?.rejectIncoming()
                        status.text = "Could not save file · ${error.message ?: "storage error"}"
                    }
                } else {
                    fileTransferCoordinator?.rejectIncoming()
                }
                pendingIncomingFileName = null
                pendingIncomingFileMime = "application/octet-stream"
            }
        }
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
        permissions: Array<String>,
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
        if (requestCode == mediaAudioPermissionRequestCode) {
            val sessionId = pendingAudioSessionId
            val kind = pendingAudioKind
            val projectionData = pendingAudioProjectionData
            pendingAudioSessionId = null
            pendingAudioKind = null
            pendingAudioProjectionData = null
            if (sessionId != null && kind != null) {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                startMediaPublisherInternal(sessionId, kind, projectionData, granted)
                if (!granted) status.text = "Microphone permission denied · continuing video only"
            }
            return
        }
        if (requestCode == mediaCameraPermissionRequestCode) {
            val sessionId = pendingMediaSessionId
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
                sessionId != null && pendingMediaKind == "camera") {
                startMediaPublisher(sessionId, "camera", null)
            } else {
                status.text = "Camera streaming permission was denied"
                sessionId?.let { terminateMediaSession(it, "camera_permission_denied") }
                pendingMediaSessionId = null
                pendingMediaKind = null
            }
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
        stopMediaPublisher()
        stopPeripheralOutputs()
    }

    private fun stopPeripheralOutputs() {
        // Alarm playback is owned by ConnectionForegroundService and is
        // stopped there when the app terminates or the user explicitly stops it.
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

    private fun resolveSharedDocumentUri(documentId: String): android.net.Uri {
        if (documentId.isBlank() || documentId.length > 512) {
            throw IllegalArgumentException("Invalid remote file identifier")
        }
        val treeUri = getSharedPreferences("meizzo_remote_file_access", MODE_PRIVATE)
            .getString("tree_uri", null)?.let(android.net.Uri::parse)
            ?: throw IllegalStateException("Choose a shared folder before managing remote files")
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        if (documentId == rootId) throw SecurityException("The shared folder root cannot be modified")
        val rootDocument = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        if (!android.provider.DocumentsContract.isChildDocument(contentResolver, rootDocument, documentUri)) {
            throw SecurityException("Requested file is outside the shared directory")
        }
        return documentUri
    }

    private fun queryRemoteDocumentInfo(uri: android.net.Uri): JSONObject {
        val info = JSONObject()
        info.put("name", uri.lastPathSegment?.substringAfterLast('/') ?: "Remote file")
        info.put("mimeType", contentResolver.getType(uri) ?: "application/octet-stream")
        info.put("size", -1L)
        info.put("flags", 0L)
        contentResolver.query(
            uri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_SIZE,
                android.provider.DocumentsContract.Document.COLUMN_FLAGS
            ),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                val flagsIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_FLAGS)
                if (nameIndex >= 0) info.put("name", cursor.getString(nameIndex) ?: info.optString("name"))
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) info.put("size", cursor.getLong(sizeIndex))
                if (flagsIndex >= 0 && !cursor.isNull(flagsIndex)) info.put("flags", cursor.getLong(flagsIndex))
            }
        } ?: throw IllegalStateException("Remote file metadata could not be read")
        return info
    }

    private fun sendRemoteFileListing(
        webSocket: WebSocket,
        message: JSONObject,
        treeUri: android.net.Uri,
        documentId: String
    ) {
        if (documentId.isBlank() || documentId.length > 512) {
            throw IllegalArgumentException("Invalid remote folder identifier")
        }
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocument = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val requestedDocument = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        if (documentId != rootId &&
            !android.provider.DocumentsContract.isChildDocument(contentResolver, rootDocument, requestedDocument)
        ) {
            throw SecurityException("Requested folder is outside the shared directory")
        }
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.DocumentsContract.Document.COLUMN_SIZE,
            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            android.provider.DocumentsContract.Document.COLUMN_FLAGS
        )
        val writeAccess = contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        val directoryFlags = queryRemoteDocumentInfo(requestedDocument).optLong("flags")
        val entries = org.json.JSONArray()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_FLAGS)
            while (cursor.moveToNext() && entries.length() < 40) {
                val childId = if (idIndex >= 0) cursor.getString(idIndex).orEmpty() else ""
                if (childId.isBlank() || childId.length > 512) continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else childId
                val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L
                val documentFlags = if (flagsIndex >= 0 && !cursor.isNull(flagsIndex)) cursor.getLong(flagsIndex) else 0L
                entries.put(JSONObject().apply {
                    put("documentId", childId)
                    put("name", name.take(120))
                    put("mimeType", mime.take(100))
                    put("size", size)
                    put("modifiedAt", modified)
                    put("flags", documentFlags)
                    put("isDirectory", mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                })
            }
        } ?: throw IllegalStateException("The selected folder could not be read")
        webSocket.send(JSONObject().apply {
            put("type", "client.result")
            put("requestId", message.optString("requestId"))
            put("result", JSONObject().apply {
                put("documentId", documentId)
                put("rootDocumentId", rootId)
                put("writeAccess", writeAccess)
                put("directoryFlags", directoryFlags)
                put("files", entries)
                put("truncated", entries.length() >= 40)
            })
        }.toString())
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
                showGlassPrompt(
                    title = "Allow flashlight control?",
                    message = "MEIZZO needs Android's camera permission to operate the flashlight. This permission is used for the torch only, not to capture photos or video.",
                    dismissLabel = "Not now",
                    confirmLabel = "Continue",
                    onDismiss = {
                        pendingTorchCommand = null
                        sendCommandFailure(webSocket, message, "Flashlight permission was not granted")
                    },
                    onConfirm = {
                        requestPermissions(
                            arrayOf(android.Manifest.permission.CAMERA),
                            cameraPermissionRequestCode
                        )
                    }
                )
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
                val requestId = message.optString("requestId")
                if (requestId.isBlank()) {
                    error = "Alarm command is missing its request identifier"
                } else {
                    try {
                        val action = if (enabled) {
                            ConnectionForegroundService.ACTION_ALARM_ON
                        } else {
                            ConnectionForegroundService.ACTION_ALARM_OFF
                        }
                        pendingAlarmCommands[requestId] = webSocket
                        startForegroundService(
                            Intent(this, ConnectionForegroundService::class.java)
                                .setAction(action)
                                .putExtra(ConnectionForegroundService.EXTRA_REQUEST_ID, requestId)
                        )
                        return
                    } catch (exception: Exception) {
                        pendingAlarmCommands.remove(requestId)
                        error = exception.message ?: "Unable to control the remote alarm"
                    }
                }
            }
            "file_create_directory" -> {
                try {
                    val parentId = parameters.optString("parentDocumentId")
                    val newName = parameters.optString("newName").trim()
                    if (parentId.isBlank() || parentId.length > 512) {
                        throw IllegalArgumentException("Invalid destination folder")
                    }
                    if (newName.isBlank() || newName.length > 128 || newName.contains('/') || newName.contains('\\')) {
                        throw IllegalArgumentException("Enter a valid folder name without path separators")
                    }
                    val treeUri = getSharedPreferences("meizzo_remote_file_access", MODE_PRIVATE)
                        .getString("tree_uri", null)?.let(android.net.Uri::parse)
                        ?: throw IllegalStateException("Choose a shared folder before creating directories")
                    val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    val parentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                    val rootUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
                    if (parentId != rootId &&
                        !android.provider.DocumentsContract.isChildDocument(contentResolver, rootUri, parentUri)
                    ) {
                        throw SecurityException("Destination folder is outside the shared directory")
                    }
                    val writeAccess = contentResolver.persistedUriPermissions.any {
                        it.uri == treeUri && it.isWritePermission
                    }
                    if (!writeAccess) throw SecurityException("The shared folder was not granted write access")
                    val parentInfo = queryRemoteDocumentInfo(parentUri)
                    val parentFlags = parentInfo.optLong("flags")
                    if ((parentFlags and android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()) == 0L) {
                        throw IllegalStateException("This folder does not support creating subfolders")
                    }
                    runOnUiThread {
                        showGlassPrompt(
                            title = "Allow folder creation?",
                            message = "The paired Controller wants to create this folder inside the shared directory:\n\n$newName",
                            dismissLabel = "Deny",
                            confirmLabel = "Create folder",
                            onDismiss = { sendCommandFailure(webSocket, message, "Folder creation was denied on the Client") },
                            onConfirm = {
                                try {
                                    android.provider.DocumentsContract.createDocument(
                                        contentResolver,
                                        parentUri,
                                        android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                                        newName
                                    ) ?: throw IllegalStateException("The storage provider could not create the folder")
                                    webSocket.send(JSONObject().apply {
                                        put("type", "client.result")
                                        put("requestId", message.optString("requestId"))
                                        put("result", JSONObject().put("filesChanged", true).put("createdFolder", newName))
                                    }.toString())
                                } catch (exception: Exception) {
                                    sendCommandFailure(webSocket, message, exception.message ?: "Unable to create the folder")
                                }
                            }
                        )
                    }
                    return
                } catch (exception: Exception) {
                    error = exception.message ?: "Remote folder creation failed"
                }
            }
            "file_rename", "file_delete" -> {
                try {
                    val documentId = parameters.optString("documentId")
                    val documentUri = resolveSharedDocumentUri(documentId)
                    val info = queryRemoteDocumentInfo(documentUri)
                    val name = info.optString("name", "Remote file")
                    val mime = info.optString("mimeType")
                    val flags = info.optLong("flags")
                    val isDirectory = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                    val command = message.optString("command")
                    val newName = parameters.optString("newName").trim()
                    if (command == "file_rename") {
                        if ((flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong()) == 0L) {
                            throw IllegalStateException("This storage provider does not support renaming this item")
                        }
                        if (newName.isBlank() || newName.length > 128 || newName.contains('/') || newName.contains('\\')) {
                            throw IllegalArgumentException("Enter a valid file or folder name without path separators")
                        }
                    } else {
                        if (isDirectory) throw IllegalArgumentException("Folder deletion is disabled for safety")
                        if ((flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong()) == 0L) {
                            throw IllegalStateException("This storage provider does not support deleting this file")
                        }
                    }
                    runOnUiThread {
                        showGlassPrompt(
                            title = if (command == "file_rename") "Allow rename?" else "Allow file deletion?",
                            message = if (command == "file_rename") "The paired Controller wants to rename:\n\n$name\n\nto:\n$newName"
                                else "The paired Controller wants to permanently delete this file:\n\n$name",
                            dismissLabel = "Deny",
                            confirmLabel = if (command == "file_rename") "Rename" else "Delete",
                            onDismiss = { sendCommandFailure(webSocket, message, "Remote file operation was denied on the Client") },
                            onConfirm = {
                                try {
                                    if (command == "file_rename") {
                                        android.provider.DocumentsContract.renameDocument(contentResolver, documentUri, newName)
                                            ?: throw IllegalStateException("The storage provider could not rename this item")
                                    } else {
                                        if (!android.provider.DocumentsContract.deleteDocument(contentResolver, documentUri)) {
                                            throw IllegalStateException("The storage provider could not delete this file")
                                        }
                                    }
                                    webSocket.send(JSONObject().apply {
                                        put("type", "client.result")
                                        put("requestId", message.optString("requestId"))
                                        put("result", JSONObject().apply {
                                            put("filesChanged", true)
                                            put("name", if (command == "file_rename") newName else name)
                                            put("deleted", command == "file_delete")
                                        })
                                    }.toString())
                                } catch (exception: Exception) {
                                    sendCommandFailure(webSocket, message, exception.message ?: "Remote file operation failed")
                                }
                            }
                        )
                    }
                    return
                } catch (exception: Exception) {
                    error = exception.message ?: "Remote file operation failed"
                }
            }
            "file_send" -> {
                val sessionId = parameters.optString("sessionId")
                val documentId = parameters.optString("documentId")
                if (activeMediaKind != "files" || activeMediaSessionId != sessionId) {
                    error = "An approved file-transfer session must be active before sending a remote file"
                } else if (documentId.isBlank() || documentId.length > 512) {
                    error = "Invalid remote file identifier"
                } else {
                    val treeUri = getSharedPreferences("meizzo_remote_file_access", MODE_PRIVATE)
                        .getString("tree_uri", null)?.let(android.net.Uri::parse)
                    if (treeUri == null) {
                        error = "Choose a shared folder before sending remote files"
                    } else {
                        try {
                            val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                            val rootDocument = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
                            val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            if (documentId == rootId ||
                                !android.provider.DocumentsContract.isChildDocument(contentResolver, rootDocument, fileUri)
                            ) {
                                throw SecurityException("Requested file is outside the shared directory")
                            }
                            val mime = contentResolver.getType(fileUri)
                                ?: "application/octet-stream"
                            if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                                throw IllegalArgumentException("Choose a file, not a folder")
                            }
                            var name = fileUri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
                            var size = -1L
                            contentResolver.query(
                                fileUri,
                                arrayOf(
                                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                    android.provider.DocumentsContract.Document.COLUMN_SIZE
                                ),
                                null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                    val sizeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                                }
                            }
                            if (size > 50L * 1024L * 1024L) {
                                throw IllegalArgumentException("File exceeds the 50 MiB transfer limit")
                            }
                            runOnUiThread {
                                showGlassPrompt(
                                    title = "Allow file transfer?",
                                    message = "The paired Controller wants to receive this file:\n\n$name\n${if (size >= 0L) "${size / 1024} KB" else "Size unknown"}\n\nThe file will be sent only after you approve.",
                                    dismissLabel = "Deny",
                                    confirmLabel = "Send file",
                                    onDismiss = { sendCommandFailure(webSocket, message, "File transfer was denied on the Client") },
                                    onConfirm = {
                                        try {
                                            val coordinator = fileTransferCoordinator
                                                ?: throw IllegalStateException("Secure file-transfer channel is not ready")
                                            coordinator.sendFile(fileUri)
                                            webSocket.send(JSONObject().apply {
                                                put("type", "client.result")
                                                put("requestId", message.optString("requestId"))
                                                put("result", JSONObject().put("accepted", true))
                                            }.toString())
                                        } catch (exception: Exception) {
                                            sendCommandFailure(webSocket, message, exception.message ?: "Unable to send the selected file")
                                        }
                                    }
                                )
                            }
                            return
                        } catch (exception: Exception) {
                            error = exception.message ?: "Unable to access the selected file"
                        }
                    }
                }
            }
            "file_browse" -> {
                if (pendingRemoteFileBrowse != null) {
                    error = "Another remote folder permission request is already pending"
                } else {
                    val savedTree = getSharedPreferences("meizzo_remote_file_access", MODE_PRIVATE)
                        .getString("tree_uri", null)?.let(android.net.Uri::parse)
                    runOnUiThread {
                        val pickerAction = {
                            val rootRequest = JSONObject(message.toString()).put("parameters", JSONObject())
                            pendingRemoteFileBrowse = webSocket to rootRequest
                            startActivityForResult(
                                android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE),
                                remoteFileTreeRequestCode
                            )
                        }
                        showGlassPrompt(
                            title = "Allow remote folder browsing?",
                            message = "The paired Controller wants to view folder and file names, types, sizes, and modification dates inside a folder you choose. If Android grants write access, it may also request individual file rename or deletion; each operation needs separate approval. Folder deletion is disabled.",
                            dismissLabel = "Deny",
                            confirmLabel = if (savedTree == null) "Choose folder" else "Browse",
                            onDismiss = { sendCommandFailure(webSocket, message, "Remote folder browsing was denied") },
                            onConfirm = {
                                if (savedTree == null) {
                                    pickerAction()
                                } else {
                                    try {
                                        val rootId = android.provider.DocumentsContract.getTreeDocumentId(savedTree)
                                        val requestedId = message.optJSONObject("parameters")
                                            ?.optString("documentId")?.takeIf { it.isNotBlank() } ?: rootId
                                        sendRemoteFileListing(webSocket, message, savedTree, requestedId)
                                    } catch (exception: Exception) {
                                        sendCommandFailure(webSocket, message, exception.message ?: "Unable to browse the selected folder")
                                    }
                                }
                            }
                        )
                    }
                    return
                }
            }
            "clipboard_read", "clipboard_write" -> {
                val command = message.optString("command")
                val clipboardText = parameters.optString("text")
                if (command == "clipboard_write" && (clipboardText.isBlank() || clipboardText.length > 10_000)) {
                    error = "Clipboard text must contain 1–10,000 characters"
                } else {
                    runOnUiThread {
                        val title = if (command == "clipboard_read") "Allow clipboard access?" else "Allow clipboard update?"
                        val detail = if (command == "clipboard_read") {
                            "The paired Controller is requesting the current text clipboard. Only text is shared, and only after you approve."
                        } else {
                            "The paired Controller wants to replace this device's text clipboard with the following content:\n\n${clipboardText.take(500)}"
                        }
                        showGlassPrompt(
                            title = title,
                            message = detail,
                            dismissLabel = "Deny",
                            confirmLabel = "Allow",
                            onDismiss = { sendCommandFailure(webSocket, message, "Clipboard access was denied on the Client") },
                            onConfirm = {
                                try {
                                    val manager = getSystemService(android.content.ClipboardManager::class.java)
                                    if (command == "clipboard_read") {
                                        val clip = manager.primaryClip
                                        val value = if (
                                            clip != null && clip.itemCount > 0 &&
                                            clip.description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                                        ) {
                                            clip.getItemAt(0).text?.toString()?.take(10_000).orEmpty()
                                        } else ""
                                        webSocket.send(JSONObject().apply {
                                            put("type", "client.result")
                                            put("requestId", message.optString("requestId"))
                                            put("result", JSONObject().put("clipboardText", value))
                                        }.toString())
                                    } else {
                                        manager.setPrimaryClip(android.content.ClipData.newPlainText("MEIZZO", clipboardText))
                                        webSocket.send(JSONObject().apply {
                                            put("type", "client.result")
                                            put("requestId", message.optString("requestId"))
                                            put("result", JSONObject().put("clipboardWritten", true))
                                        }.toString())
                                    }
                                } catch (exception: Exception) {
                                    sendCommandFailure(webSocket, message, exception.message ?: "Clipboard operation failed")
                                }
                            }
                        )
                    }
                    return
                }
            }
            "camera_flip" -> {
                if (activeMediaKind != "camera") {
                    error = "Start an approved remote camera session before switching cameras"
                } else if (rtcMediaClient?.switchCamera() != true) {
                    error = "Remote camera switching is unavailable on this device"
                } else {
                    result.put("accepted", true)
                }
            }
            "volume_up", "volume_down", "volume_mute", "volume_unmute" -> {
                try {
                    val manager = getSystemService(android.media.AudioManager::class.java)
                    val adjustment = when (message.optString("command")) {
                        "volume_up" -> android.media.AudioManager.ADJUST_RAISE
                        "volume_down" -> android.media.AudioManager.ADJUST_LOWER
                        "volume_mute" -> android.media.AudioManager.ADJUST_MUTE
                        else -> android.media.AudioManager.ADJUST_UNMUTE
                    }
                    manager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        adjustment,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    result.put("accepted", true)
                } catch (exception: Exception) {
                    error = exception.message ?: "Android volume control failed"
                }
            }
            "key_backspace", "key_enter", "key_tab", "key_escape",
            "key_left", "key_right", "key_up", "key_down" -> {
                val accessibility = RemoteAccessibilityService.instance
                val keyCode = when (message.optString("command")) {
                    "key_backspace" -> android.view.KeyEvent.KEYCODE_DEL
                    "key_enter" -> android.view.KeyEvent.KEYCODE_ENTER
                    "key_tab" -> android.view.KeyEvent.KEYCODE_TAB
                    "key_escape" -> android.view.KeyEvent.KEYCODE_ESCAPE
                    "key_left" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    "key_right" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    "key_up" -> android.view.KeyEvent.KEYCODE_DPAD_UP
                    else -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                }
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else {
                    val sent = accessibility.sendKey(keyCode)
                    val fallback = message.optString("command") == "key_backspace" && accessibility.deleteBackward()
                    if (!sent && !fallback) {
                        error = "Android 13+ active text input connection is required for this key, or the editor rejected it"
                    } else {
                        result.put("accepted", true)
                    }
                }
            }
            "key_shortcut" -> {
                val shortcut = parameters.optString("shortcut")
                val keyCode = when (shortcut) {
                    "select_all" -> android.view.KeyEvent.KEYCODE_A
                    "copy" -> android.view.KeyEvent.KEYCODE_C
                    "paste" -> android.view.KeyEvent.KEYCODE_V
                    "cut" -> android.view.KeyEvent.KEYCODE_X
                    "undo" -> android.view.KeyEvent.KEYCODE_Z
                    "redo" -> android.view.KeyEvent.KEYCODE_Y
                    else -> -1
                }
                val accessibility = RemoteAccessibilityService.instance
                if (keyCode < 0) {
                    error = "Unsupported keyboard shortcut"
                } else if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!accessibility.sendShortcut(keyCode)) {
                    error = "Android 13+ active text input connection is required for this shortcut"
                } else {
                    result.put("accepted", true)
                }
            }
            "text_input" -> {
                val text = parameters.optString("text")
                val accessibility = RemoteAccessibilityService.instance
                if (text.isEmpty() || text.length > 2_000) {
                    error = "Text input must contain 1–2,000 characters"
                } else if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!accessibility.appendText(text)) {
                    error = "No editable non-password field is focused, or Android rejected text insertion"
                } else {
                    result.put("charactersInserted", text.length)
                }
            }
            "lock_screen" -> {
                val accessibility = RemoteAccessibilityService.instance
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!accessibility.lockScreen()) {
                    error = "Screen lock action is unavailable on this Android version"
                } else {
                    result.put("locked", true)
                }
            }
            "back", "home", "recents", "notifications", "quick_settings", "power_dialog" -> {
                val accessibility = RemoteAccessibilityService.instance
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!accessibility.navigate(message.optString("command"))) {
                    error = "Android rejected the navigation action"
                } else {
                    result.put("accepted", true)
                }
            }
            "long_press" -> {
                val x = parameters.optDouble("x", Double.NaN)
                val y = parameters.optDouble("y", Double.NaN)
                val durationMs = parameters.optLong("durationMs", 650L)
                val accessibility = RemoteAccessibilityService.instance
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) {
                    error = "Long-press coordinates must be normalized values between 0 and 1"
                } else if (!accessibility.longPress(x, y, durationMs)) {
                    error = "Android rejected the remote long-press gesture"
                } else {
                    result.put("accepted", true)
                }
            }
            "tap" -> {
                val x = parameters.optDouble("x", Double.NaN)
                val y = parameters.optDouble("y", Double.NaN)
                val accessibility = RemoteAccessibilityService.instance
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) {
                    error = "Tap coordinates must be normalized values between 0 and 1"
                } else if (!accessibility.tap(x, y)) {
                    error = "Android rejected the remote tap gesture"
                } else {
                    result.put("accepted", true)
                }
            }
            "swipe" -> {
                val startX = parameters.optDouble("startX", Double.NaN)
                val startY = parameters.optDouble("startY", Double.NaN)
                val endX = parameters.optDouble("endX", Double.NaN)
                val endY = parameters.optDouble("endY", Double.NaN)
                val accessibility = RemoteAccessibilityService.instance
                val valid = listOf(startX, startY, endX, endY).all { it.isFinite() && it in 0.0..1.0 }
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!valid) {
                    error = "Swipe coordinates must be normalized values between 0 and 1"
                } else if (!accessibility.swipe(startX, startY, endX, endY, parameters.optLong("durationMs", 350L))) {
                    error = "Android rejected the remote swipe gesture"
                } else {
                    result.put("accepted", true)
                }
            }
            "scroll" -> {
                val deltaX = parameters.optDouble("deltaX", 0.0)
                val deltaY = parameters.optDouble("deltaY", 0.0)
                val accessibility = RemoteAccessibilityService.instance
                val valid = deltaX.isFinite() && deltaY.isFinite() &&
                    deltaX in -0.35..0.35 && deltaY in -0.35..0.35 &&
                    (kotlin.math.abs(deltaX) >= 0.01 || kotlin.math.abs(deltaY) >= 0.01)
                if (accessibility == null) {
                    error = "Enable MEIZZO remote interaction in Android Accessibility settings first"
                } else if (!valid) {
                    error = "Scroll deltas must be finite normalized values within -0.35 to 0.35"
                } else if (!accessibility.scroll(deltaX, deltaY)) {
                    error = "Android rejected the remote scroll gesture"
                } else {
                    result.put("accepted", true)
                }
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
                    showGlassPrompt(
                        title = "MEIZZO update · ${available.tag}",
                        message = details,
                        dismissLabel = "Later",
                        confirmLabel = "Download",
                        onDismiss = { setUpdateStatus("Update available · ${available.versionName}") },
                        onConfirm = { downloadUpdate(available) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        stopMediaPublisher()
        unregisterReceiver(alarmResultReceiver)
        pendingAlarmCommands.clear()
        connectionService?.detachSocketListener()
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) { }
            serviceBound = false
        }
        super.onDestroy()
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
                    showGlassPrompt(
                        title = "Install ${release.versionName}?",
                        message = "Package ID, version, file size, SHA-256, and signing certificate were verified. Android will show the final installation confirmation.",
                        dismissLabel = "Cancel",
                        confirmLabel = "Install",
                        onConfirm = {
                            pendingUpdateApk = apk
                            updateManager.install(apk)
                            if (updateManager.canInstallPackages()) pendingUpdateApk = null
                        }
                    )
                }
            }
        }
    }
}
