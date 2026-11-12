package com.meizzo.client

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
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
    private val onDataChannel: (DataChannel) -> Unit = {},
    private val iceServersJson: JSONArray? = null
) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var capturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var sessionId: String = ""
    private var remoteDescriptionSet = false
    private val queuedCandidates = mutableListOf<IceCandidate>()
    private var videoSender: RtpSender? = null
    private val adaptationHandler = Handler(Looper.getMainLooper())
    private var adaptationActive = false
    private var currentMaxBitrateBps = 1_500_000
    private var healthyStatsSamples = 0
    private val adaptationPoll = object : Runnable {
        override fun run() {
            if (!adaptationActive) return
            collectAndAdaptBitrate()
            adaptationHandler.postDelayed(this, 3_000)
        }
    }

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

    fun start(sessionId: String, kind: String, projectionData: Intent? = null, includeAudio: Boolean = false) {
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
        videoSender = peer?.addTrack(track, listOf("MEIZZO_STREAM_$sessionId"))
        if (videoSender == null) {
            onFailure("Unable to attach the video track to WebRTC")
            return
        }
        if (includeAudio) {
            val source = factory.createAudioSource(MediaConstraints())
            audioSource = source
            val microphoneTrack = factory.createAudioTrack("MEIZZO_AUDIO_$sessionId", source).apply {
                setEnabled(true)
            }
            audioTrack = microphoneTrack
            if (peer?.addTrack(microphoneTrack, listOf("MEIZZO_AUDIO_STREAM_$sessionId")) == null) {
                onFailure("Unable to attach the microphone audio track to WebRTC")
                return
            }
        }
        startAdaptiveBitrate()
    }

    private fun startAdaptiveBitrate() {
        adaptationActive = true
        adaptationHandler.removeCallbacks(adaptationPoll)
        adaptationHandler.postDelayed(adaptationPoll, 3_000)
    }

    /**
     * Uses WebRTC's selected candidate-pair bandwidth estimate, remote packet
     * loss, and RTT to adjust the sender cap. WebRTC congestion control remains
     * authoritative; this is a conservative application-level ceiling.
     */
    private fun collectAndAdaptBitrate() {
        val connection = peer ?: return
        connection.getStats { report ->
            val stats = report.statsMap.values
            val candidatePair = stats.firstOrNull {
                it.type == "candidate-pair" && it.members["state"] == "succeeded" &&
                    (it.members["nominated"] == true || it.members["selected"] == true)
            } ?: stats.firstOrNull {
                it.type == "candidate-pair" && it.members["state"] == "succeeded"
            }
            val bandwidth = (candidatePair?.members?.get("availableOutgoingBitrate") as? Number)
                ?.toDouble()?.takeIf { it > 0.0 }
            val rtt = (candidatePair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()
            val packetLoss = stats.firstOrNull { it.type == "remote-inbound-rtp" }
                ?.members?.get("fractionLost") as? Number
            val lossRatio = packetLoss?.toDouble()

            adaptationHandler.post {
                if (!adaptationActive || peer !== connection) return@post
                var target = currentMaxBitrateBps
                if (bandwidth != null) {
                    target = minOf(target, (bandwidth * 0.75).toInt().coerceIn(350_000, 3_500_000))
                }
                val congested = (lossRatio != null && lossRatio >= 0.08) ||
                    (rtt != null && rtt >= 0.45)
                val healthy = (lossRatio == null || lossRatio < 0.02) &&
                    (rtt == null || rtt < 0.20)
                if (congested) {
                    target = minOf(target, (currentMaxBitrateBps * 0.70).toInt())
                        .coerceAtLeast(350_000)
                    healthyStatsSamples = 0
                } else if (healthy) {
                    healthyStatsSamples += 1
                    if (healthyStatsSamples >= 2) {
                        target = minOf(3_500_000, maxOf(target, (currentMaxBitrateBps * 1.15).toInt()))
                    }
                } else {
                    healthyStatsSamples = 0
                }
                if (kotlin.math.abs(target - currentMaxBitrateBps) < 100_000) return@post
                val sender = videoSender ?: return@post
                val parameters = sender.parameters
                if (parameters.encodings.isEmpty()) return@post
                parameters.encodings.forEach { it.maxBitrateBps = target }
                if (sender.setParameters(parameters)) currentMaxBitrateBps = target
            }
        }
    }

    fun switchCamera(): Boolean {
        val cameraCapturer = capturer as? CameraVideoCapturer ?: return false
        cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) = Unit
            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e("MEIZZO_MEDIA", errorDescription ?: "Unable to switch remote camera")
            }
        })
        return true
    }

    fun startFiles(sessionId: String) {
        this.sessionId = sessionId
        createPeerConnection()
    }

    private fun createPeerConnection(): Boolean {
        if (peer != null) return true
        val configuration = PeerConnection.RTCConfiguration(
            createIceServers()
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
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                (receiver?.track() as? AudioTrack)?.let {
                    remoteAudioTrack = it
                    it.setEnabled(true)
                }
            }
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                (transceiver?.receiver?.track() as? AudioTrack)?.let {
                    remoteAudioTrack = it
                    it.setEnabled(true)
                }
            }
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
                remoteDescriptionSet = false
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
        adaptationActive = false
        adaptationHandler.removeCallbacks(adaptationPoll)
        videoSender = null
        try { capturer?.stopCapture() } catch (_: Exception) { }
        capturer?.dispose()
        capturer = null
        videoSource?.dispose()
        videoSource = null
        audioTrack?.setEnabled(false)
        audioTrack?.dispose()
        audioTrack = null
        remoteAudioTrack?.setEnabled(false)
        remoteAudioTrack = null
        audioSource?.dispose()
        audioSource = null
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
        private val FACTORY_LOCK = Any()
        @Volatile private var factoryInitialized = false
    }
}
