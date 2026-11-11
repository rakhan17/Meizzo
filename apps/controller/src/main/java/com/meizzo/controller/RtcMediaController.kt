package com.meizzo.controller

import android.content.Context
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class RtcMediaController(
    context: Context,
    private val sendSignal: (JSONObject) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onDataChannel: (DataChannel) -> Unit = {}
) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var renderer: SurfaceViewRenderer? = null
    private var remoteDescriptionSet = false
    private val queuedCandidates = mutableListOf<IceCandidate>()
    private var attachedTrackId: String? = null
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
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) onFailure("WebRTC connection failed. Check network or TURN availability.")
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
                (receiver?.track() as? VideoTrack)?.let(::attachTrack)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                (transceiver?.receiver?.track() as? VideoTrack)?.let(::attachTrack)
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

    fun startFiles() {
        val configuration = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) onFailure("WebRTC data connection failed")
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

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private val FACTORY_LOCK = Any()
        @Volatile private var factoryInitialized = false
    }
}
