package com.meizzo.client

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints

/**
 * Android media publisher. Screen capture always uses Android's system consent
 * token; camera capture is started only after runtime camera permission is granted.
 */
class RtcMediaClient(
    private val context: Context,
    private val sendSignal: (JSONObject) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onDataChannel: (DataChannel) -> Unit = {}
) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var capturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var sessionId: String = ""
    private var remoteDescriptionSet = false
    private val queuedCandidates = mutableListOf<IceCandidate>()

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

    fun start(sessionId: String, kind: String, projectionData: Intent? = null) {
        this.sessionId = sessionId
        val capture = when (kind) {
            "screen" -> {
                if (projectionData == null) {
                    onFailure("Android screen-capture consent is required")
                    return
                }
                ScreenCapturerAndroid(projectionData, object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() {
                        onFailure("Android stopped screen capture")
                    }
                })
            }
            "camera" -> createCameraCapturer()
            else -> {
                onFailure("Unsupported media source: $kind")
                return
            }
        } ?: run {
            onFailure("No compatible camera is available")
            return
        }
        capturer = capture
        textureHelper = SurfaceTextureHelper.create("MEIZZO-Capture", egl.eglBaseContext)
        videoSource = factory.createVideoSource(capture.isScreencast)
        capture.initialize(textureHelper, context, videoSource!!.capturerObserver)
        if (kind == "screen") {
            val metrics = context.resources.displayMetrics
            val maxDimension = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
            val scale = minOf(1.0, 1280.0 / maxDimension.toDouble())
            val width = ((metrics.widthPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val height = ((metrics.heightPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            capture.startCapture(width, height, 15)
        } else {
            capture.startCapture(960, 540, 15)
        }

        if (!createPeerConnection()) return
        if (kind == "screen") {
            val metrics = context.resources.displayMetrics
            sendSignal(JSONObject().apply {
                put("type", "stream_info")
                put("width", metrics.widthPixels)
                put("height", metrics.heightPixels)
            })
        }
        val track = factory.createVideoTrack("MEIZZO_VIDEO_$sessionId", videoSource)
        track.setEnabled(true)
        peer?.addTrack(track, listOf("MEIZZO_STREAM_$sessionId"))
    }

    fun startFiles(sessionId: String) {
        this.sessionId = sessionId
        createPeerConnection()
    }

    private fun createPeerConnection(): Boolean {
        if (peer != null) return true
        val configuration = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) onFailure("WebRTC ICE connection failed")
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
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) = Unit
        })
        if (peer == null) {
            onFailure("Unable to create WebRTC peer connection")
            return false
        }
        return true
    }

    fun onSignal(signal: JSONObject) {
        when (signal.optString("type")) {
            "offer" -> {
                val sdp = signal.optString("sdp")
                if (sdp.isBlank()) return onFailure("Controller sent an empty WebRTC offer")
                val description = SessionDescription(SessionDescription.Type.OFFER, sdp)
                peer?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        synchronized(queuedCandidates) {
                            queuedCandidates.forEach { peer?.addIceCandidate(it) }
                            queuedCandidates.clear()
                        }
                        peer?.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null) return onFailure("WebRTC could not create an answer")
                                peer?.setLocalDescription(object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        sendSignal(JSONObject().put("type", "answer").put("sdp", answer.description))
                                    }
                                    override fun onCreateFailure(error: String?) = onFailure(error ?: "Failed to set local answer")
                                    override fun onSetFailure(error: String?) = onFailure(error ?: "Failed to set local answer")
                                }, answer)
                            }
                            override fun onCreateFailure(error: String?) = onFailure(error ?: "Failed to create WebRTC answer")
                        }, MediaConstraints())
                    }
                    override fun onSetFailure(error: String?) = onFailure(error ?: "Failed to apply WebRTC offer")
                }, description)
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
            "error" -> onFailure(signal.optString("message", "Remote media error"))
        }
    }

    fun close() {
        try { capturer?.stopCapture() } catch (_: Exception) { }
        capturer?.dispose()
        capturer = null
        videoSource?.dispose()
        videoSource = null
        textureHelper?.dispose()
        textureHelper = null
        peer?.close()
        peer?.dispose()
        peer = null
        factory.dispose()
        egl.release()
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val preferred = names.firstOrNull { enumerator.isBackFacing(it) } ?: names.firstOrNull()
        return preferred?.let { enumerator.createCapturer(it, null) }
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
