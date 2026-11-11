package com.meizzo.controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONArray
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class RtcMediaController(
    context: Context,
    private val sendSignal: (JSONObject) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onDataChannel: (DataChannel) -> Unit = {},
    private val iceServersJson: JSONArray? = null
) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var renderer: SurfaceViewRenderer? = null
    private var remoteDescriptionSet = false
    private val queuedCandidates = mutableListOf<IceCandidate>()
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var iceRestartAttempts = 0
    private var iceRestartInProgress = false
    private val iceRestartTask = Runnable { restartIceIfNeeded() }
    private var attachedTrackId: String? = null
    private var remoteAudioTrack: org.webrtc.AudioTrack? = null
    private var remoteAudioEnabled = true
    private var localAudioSource: org.webrtc.AudioSource? = null
    private var localAudioTrack: org.webrtc.AudioTrack? = null
    private var localAudioSender: RtpSender? = null
    private var localAudioEnabled = false
    var remoteWidth: Int = 0
        private set
    var remoteHeight: Int = 0
        private set

    init {
        synchronized(FACTORY_LOCK) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factoryInitialized = true
            }
        }
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun start(view: SurfaceViewRenderer) {
        renderer = view
        view.init(egl.eglBaseContext, null)
        view.setEnableHardwareScaler(true)
        view.setMirror(false)
        view.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)

        val configuration = PeerConnection.RTCConfiguration(
            createIceServers()
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> recoveryHandler.post {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        iceRestartAttempts = 0
                        iceRestartInProgress = false
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        recoveryHandler.postDelayed(iceRestartTask, 2_500)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        recoveryHandler.post(iceRestartTask)
                    }
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                sendSignal(JSONObject().apply {
                    put("type", "candidate")
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                })
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                stream?.videoTracks?.firstOrNull()?.let(::attachTrack)
            }
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) { channel?.let(onDataChannel) }
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                when (val track = receiver?.track()) {
                    is VideoTrack -> attachTrack(track)
                    is org.webrtc.AudioTrack -> {
                        remoteAudioTrack = track
                        track.setEnabled(remoteAudioEnabled)
                    }
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                when (val track = transceiver?.receiver?.track()) {
                    is VideoTrack -> attachTrack(track)
                    is org.webrtc.AudioTrack -> {
                        remoteAudioTrack = track
                        track.setEnabled(remoteAudioEnabled)
                    }
                }
            }
        }) ?: run {
            onFailure("Unable to create WebRTC viewer")
            return
        }

        val init = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        if (peer?.addTransceiver(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, init) == null) {
            onFailure("Unable to request the Client video track")
            return
        }
        if (peer?.addTransceiver(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init) == null) {
            onFailure("Unable to request the Client microphone audio track")
            return
        }
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return onFailure("Unable to create WebRTC offer")
                peer?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sendSignal(JSONObject().put("type", "offer").put("sdp", description.description))
                    }
                    override fun onSetFailure(error: String?) = onFailure(error ?: "Unable to set local offer")
                }, description)
            }
            override fun onCreateFailure(error: String?) = onFailure(error ?: "Unable to create WebRTC offer")
        }, MediaConstraints())
    }

    fun enableLocalMicrophone(): Boolean {
        val connection = peer ?: return false
        if (localAudioTrack != null) {
            localAudioTrack?.setEnabled(true)
            localAudioEnabled = true
            return true
        }
        return try {
            val source = factory.createAudioSource(MediaConstraints())
            val track = factory.createAudioTrack("MEIZZO_CONTROLLER_MIC", source)
            track.setEnabled(true)
            val existingSender = localAudioSender
            val attached = if (existingSender != null) existingSender.setTrack(track, false)
                else connection.addTrack(track, listOf("MEIZZO_CONTROLLER_AUDIO"))?.let {
                    localAudioSender = it
                    true
                } ?: false
            if (!attached) {
                track.dispose()
                source.dispose()
                return false
            }
            localAudioSource = source
            localAudioTrack = track
            localAudioEnabled = true
            remoteDescriptionSet = false
            connection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null) return onFailure("Unable to renegotiate voice audio")
                    connection.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            sendSignal(JSONObject().put("type", "offer").put("sdp", description.description))
                        }
                        override fun onSetFailure(error: String?) = onFailure(error ?: "Unable to set voice offer")
                        override fun onCreateSuccess(description: SessionDescription?) = Unit
                        override fun onCreateFailure(error: String?) = Unit
                    }, description)
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) = onFailure(error ?: "Unable to create voice offer")
                override fun onSetFailure(error: String?) = Unit
            }, MediaConstraints())
            true
        } catch (error: Exception) {
            onFailure(error.message ?: "Unable to enable Controller microphone")
            false
        }
    }

    fun disableLocalMicrophone() {
        localAudioTrack?.setEnabled(false)
        try { localAudioSender?.setTrack(null, false) } catch (_: Exception) { }
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        localAudioSender = null
        localAudioEnabled = false
    }

    fun isLocalMicrophoneEnabled(): Boolean = localAudioEnabled

    fun toggleRemoteAudio(): Boolean {
        remoteAudioEnabled = !remoteAudioEnabled
        remoteAudioTrack?.setEnabled(remoteAudioEnabled)
        return remoteAudioEnabled
    }

    private fun restartIceIfNeeded() {
        val connection = peer ?: return
        if (iceRestartInProgress) return
        val state = connection.iceConnectionState()
        if (state == PeerConnection.IceConnectionState.CONNECTED ||
            state == PeerConnection.IceConnectionState.COMPLETED
        ) return
        if (iceRestartAttempts >= MAX_ICE_RESTART_ATTEMPTS) {
            onFailure("WebRTC stream could not recover after network interruption. End the session and reconnect.")
            return
        }
        iceRestartAttempts += 1
        iceRestartInProgress = true
        remoteDescriptionSet = false
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        connection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) {
                    scheduleIceRestartRetry()
                    return
                }
                connection.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sendSignal(JSONObject().put("type", "offer").put("sdp", description.description))
                    }
                    override fun onSetFailure(error: String?) = scheduleIceRestartRetry()
                }, description)
            }
            override fun onCreateFailure(error: String?) = scheduleIceRestartRetry()
        }, constraints)
    }

    private fun scheduleIceRestartRetry() {
        iceRestartInProgress = false
        if (iceRestartAttempts >= MAX_ICE_RESTART_ATTEMPTS) {
            onFailure("WebRTC ICE restart failed. Check TURN availability and reconnect the session.")
        } else {
            recoveryHandler.removeCallbacks(iceRestartTask)
            recoveryHandler.postDelayed(iceRestartTask, 3_000)
        }
    }

    fun startFiles() {
        val configuration = PeerConnection.RTCConfiguration(
            createIceServers()
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> recoveryHandler.post {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        iceRestartAttempts = 0
                        iceRestartInProgress = false
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        recoveryHandler.postDelayed(iceRestartTask, 2_500)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        recoveryHandler.removeCallbacks(iceRestartTask)
                        recoveryHandler.post(iceRestartTask)
                    }
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                sendSignal(JSONObject().apply {
                    put("type", "candidate")
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                })
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) { channel?.let(onDataChannel) }
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
            override fun onTrack(transceiver: RtpTransceiver?) = Unit
        }) ?: run {
            onFailure("Unable to create WebRTC file-transfer peer")
            return
        }
        val channel = peer?.createDataChannel("meizzo-files", DataChannel.Init().apply { ordered = true })
        if (channel == null) {
            onFailure("Unable to create the WebRTC file channel")
            return
        }
        onDataChannel(channel)
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return onFailure("Unable to create file-transfer offer")
                peer?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sendSignal(JSONObject().put("type", "offer").put("sdp", description.description))
                    }
                    override fun onSetFailure(error: String?) = onFailure(error ?: "Unable to set file-transfer offer")
                }, description)
            }
            override fun onCreateFailure(error: String?) = onFailure(error ?: "Unable to create file-transfer offer")
        }, MediaConstraints())
    }

    fun onSignal(signal: JSONObject) {
        when (signal.optString("type")) {
            "stream_info" -> {
                remoteWidth = signal.optInt("width", 0).coerceAtLeast(0)
                remoteHeight = signal.optInt("height", 0).coerceAtLeast(0)
            }
            "answer" -> {
                val sdp = signal.optString("sdp")
                if (sdp.isBlank()) return onFailure("Client sent an empty WebRTC answer")
                peer?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        iceRestartInProgress = false
                        synchronized(queuedCandidates) {
                            queuedCandidates.forEach { peer?.addIceCandidate(it) }
                            queuedCandidates.clear()
                        }
                    }
                    override fun onSetFailure(error: String?) = onFailure(error ?: "Unable to apply Client answer")
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "candidate" -> {
                val candidate = IceCandidate(
                    signal.optString("sdpMid"),
                    signal.optInt("sdpMLineIndex"),
                    signal.optString("candidate")
                )
                if (candidate.sdp.isBlank()) return
                if (remoteDescriptionSet) peer?.addIceCandidate(candidate)
                else synchronized(queuedCandidates) { queuedCandidates.add(candidate) }
            }
            "error" -> onFailure(signal.optString("message", "Client media error"))
        }
    }

    fun close() {
        recoveryHandler.removeCallbacks(iceRestartTask)
        iceRestartInProgress = false
        remoteAudioTrack?.setEnabled(false)
        remoteAudioTrack = null
        localAudioTrack?.setEnabled(false)
        try { localAudioSender?.setTrack(null, false) } catch (_: Exception) { }
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        localAudioSender = null
        localAudioEnabled = false
        peer?.close()
        peer?.dispose()
        peer = null
        renderer?.release()
        renderer = null
        factory.dispose()
        egl.release()
    }

    private fun attachTrack(track: VideoTrack) {
        if (attachedTrackId == track.id()) return
        attachedTrackId = track.id()
        track.setEnabled(true)
        val target = renderer ?: return
        target.post { track.addSink(target) }
    }

    private fun createIceServers(): List<PeerConnection.IceServer> {
        val result = mutableListOf<PeerConnection.IceServer>()
        val source = iceServersJson
        if (source != null) {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val urls = item.optJSONArray("urls")
                val username = item.optString("username")
                val credential = item.optString("credential")
                if (urls != null) {
                    for (urlIndex in 0 until urls.length()) {
                        val url = urls.optString(urlIndex)
                        if (url.isBlank()) continue
                        val builder = PeerConnection.IceServer.builder(url)
                        if (username.isNotBlank()) builder.setUsername(username)
                        if (credential.isNotBlank()) builder.setPassword(credential)
                        result.add(builder.createIceServer())
                    }
                } else {
                    val url = item.optString("urls")
                    if (url.isNotBlank()) {
                        val builder = PeerConnection.IceServer.builder(url)
                        if (username.isNotBlank()) builder.setUsername(username)
                        if (credential.isNotBlank()) builder.setPassword(credential)
                        result.add(builder.createIceServer())
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result.add(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer())
        }
        return result
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val MAX_ICE_RESTART_ATTEMPTS = 3
        private val FACTORY_LOCK = Any()
        @Volatile private var factoryInitialized = false
    }
}
