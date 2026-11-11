package com.meizzo.controller

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONObject
import org.webrtc.DataChannel
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Stop-and-wait file transfer over an already consented WebRTC data channel.
 * A receiver acknowledges each 12 KiB chunk, bounding memory and channel buffering.
 */
class FileTransferCoordinator(
    private val context: Context,
    private val channel: DataChannel,
    private val onIncomingOffer: (String, Long, String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var incomingOutput: OutputStream? = null
    private var incomingUri: Uri? = null
    private var outgoingInput: InputStream? = null
    private val outgoingQueue = ArrayDeque<Uri>()
    private var outgoingSequence = 0
    private var outgoingBytes = 0L
    private var incomingBytes = 0L
    private var lastOutgoingProgressBytes = 0L
    private var lastIncomingProgressBytes = 0L
    private var incomingExpectedSequence = 0
    private var pendingChunkMessage: JSONObject? = null
    private var chunkRetryCount = 0
    private val retryHandler = Handler(Looper.getMainLooper())
    private val chunkRetryTask = object : Runnable {
        override fun run() {
            val pending = pendingChunkMessage ?: return
            if (!waitingForAck) return
            if (chunkRetryCount >= MAX_CHUNK_RETRIES) {
                val transferId = outgoingTransferId
                pendingChunkMessage = null
                closeOutgoing()
                send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                outgoingQueue.clear()
                onStatus("File transfer stopped · chunk acknowledgement timed out")
                return
            }
            chunkRetryCount += 1
            send(pending)
            retryHandler.postDelayed(this, CHUNK_ACK_TIMEOUT_MS)
        }
    }
    private var outgoingDigest: MessageDigest? = null
    private var incomingDigest: MessageDigest? = null
    private var outgoingTransferId: String? = null
    private var incomingTransferId: String? = null
    private var lastCompletedIncomingTransferId: String? = null
    private var waitingForAck = false
    @Volatile private var closed = false
    private var pendingName = ""
    private var pendingSize = 0L

    init {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                retryHandler.post {
                    if (closed) return@post
                    when (channel.state()) {
                        DataChannel.State.OPEN -> onStatus("Secure file channel ready")
                        DataChannel.State.CLOSING, DataChannel.State.CLOSED -> {
                            closed = true
                            retryHandler.removeCallbacksAndMessages(null)
                            cancelChunkRetry()
                            closeOutgoing()
                            outgoingQueue.clear()
                            discardIncoming()
                            pendingName = ""
                            pendingSize = 0L
                            try { channel.unregisterObserver() } catch (_: Exception) { }
                            onStatus("File channel closed · transfer stopped")
                        }
                        else -> Unit
                    }
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val raw = String(bytes, Charsets.UTF_8)
                retryHandler.post { if (!closed) handleMessage(raw) }
            }
        })
    }

    fun isReady(): Boolean = !closed && channel.state() == DataChannel.State.OPEN

    fun sendFile(uri: Uri) = sendFiles(listOf(uri))

    fun sendFiles(uris: List<Uri>) {
        if (closed) {
            throw IllegalStateException("File transfer session has ended")
        }
        if (channel.state() != DataChannel.State.OPEN) {
            throw IllegalStateException("File channel is not connected yet")
        }
        if (uris.isEmpty()) return
        if (outgoingInput != null || incomingOutput != null || pendingName.isNotBlank()) {
            throw IllegalStateException("Finish or cancel the current file transfer before starting another")
        }
        outgoingQueue.clear()
        outgoingQueue.addAll(uris)
        try {
            startNextFile()
        } catch (error: Exception) {
            outgoingQueue.clear()
            throw error
        }
    }

    private fun startNextFile() {
        val uri = outgoingQueue.pollFirst() ?: return
        val metadata = queryMetadata(uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android could not open the selected file")
        outgoingInput = input
        outgoingSequence = 0
        outgoingBytes = 0L
        lastOutgoingProgressBytes = 0L
        outgoingDigest = MessageDigest.getInstance("SHA-256")
        outgoingTransferId = UUID.randomUUID().toString()
        waitingForAck = true
        send(JSONObject().apply {
            put("type", "file_offer")
            put("transferId", outgoingTransferId)
            put("name", metadata.first)
            put("size", metadata.second)
            put("mimeType", context.contentResolver.getType(uri) ?: "application/octet-stream")
        })
        onStatus("Waiting for the receiver to accept ${metadata.first}")
    }

    fun acceptIncoming(uri: Uri) {
        check(!closed) { "File transfer session has ended" }
        incomingOutput = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Android could not create the destination file")
        incomingUri = uri
        incomingBytes = 0L
        lastIncomingProgressBytes = 0L
        incomingExpectedSequence = 0
        incomingDigest = MessageDigest.getInstance("SHA-256")
        val transferId = incomingTransferId ?: throw IllegalStateException("Incoming file offer has no transfer identifier")
        send(JSONObject().put("type", "file_accept").put("transferId", transferId))
        onStatus("Receiving $pendingName")
    }

    fun rejectIncoming() {
        send(JSONObject().put("type", "file_reject").put("transferId", incomingTransferId))
        pendingName = ""
        pendingSize = 0L
        discardIncoming()
    }

    fun cancelTransfer() {
        if (closed) return
        if (outgoingInput == null && incomingOutput == null && pendingName.isBlank()) {
            onStatus("No active file transfer to cancel")
            return
        }
        val transferId = outgoingTransferId ?: incomingTransferId
        send(JSONObject().put("type", "file_reject").put("transferId", transferId))
        closeOutgoing()
        outgoingQueue.clear()
        discardIncoming()
        pendingName = ""
        pendingSize = 0L
        onStatus("File transfer cancelled")
    }

    fun close() {
        if (closed) return
        closed = true
        closeOutgoing()
        outgoingQueue.clear()
        discardIncoming()
        retryHandler.removeCallbacksAndMessages(null)
        try { channel.unregisterObserver() } catch (_: Exception) { }
    }

    private fun handleMessage(raw: String) {
        val message = try { JSONObject(raw) } catch (_: Exception) { return }
        when (message.optString("type")) {
            "file_offer" -> {
                val transferId = message.optString("transferId")
                if (transferId.isBlank() || transferId.length > 64) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    onStatus("Rejected file offer with an invalid transfer identifier")
                    return
                }
                if (outgoingInput != null || incomingOutput != null || pendingName.isNotBlank()) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    onStatus("Another file transfer is already active")
                    return
                }
                incomingTransferId = transferId
                pendingName = message.optString("name").take(128)
                pendingSize = message.optLong("size", -1L)
                val mime = message.optString("mimeType", "application/octet-stream")
                if (pendingName.isBlank() || pendingSize < -1L || pendingSize > MAX_FILE_SIZE) {
                    send(JSONObject().put("type", "file_reject").put("transferId", incomingTransferId))
                    incomingTransferId = null
                    pendingName = ""
                    pendingSize = 0L
                    onStatus("Rejected invalid or oversized file offer")
                    return
                }
                onIncomingOffer(pendingName, pendingSize, mime)
            }
            "file_accept" -> {
                if (message.optString("transferId") == outgoingTransferId) {
                    waitingForAck = false
                    sendNextChunk()
                }
            }
            "file_reject" -> {
                val transferId = message.optString("transferId")
                when {
                    transferId.isNotBlank() && transferId == outgoingTransferId -> {
                        cancelChunkRetry()
                        closeOutgoing()
                        outgoingQueue.clear()
                        onStatus("The other device declined or could not complete the file")
                    }
                    transferId.isNotBlank() && transferId == incomingTransferId -> {
                        discardIncoming()
                        pendingName = ""
                        pendingSize = 0L
                        onStatus("The other device cancelled the incoming file")
                    }
                    else -> Unit
                }
            }
            "file_chunk" -> {
                val sequence = message.optInt("sequence", -1)
                val encoded = message.optString("data")
                val transferId = message.optString("transferId")
                if (transferId.isBlank() || transferId != incomingTransferId ||
                    sequence < 0 || encoded.isBlank() || incomingOutput == null
                ) return
                if (sequence == incomingExpectedSequence - 1) {
                    send(JSONObject().put("type", "file_ack").put("transferId", transferId).put("sequence", sequence))
                    return
                }
                if (sequence != incomingExpectedSequence) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    discardIncoming()
                    onStatus("File transfer stopped · unexpected chunk sequence")
                    return
                }
                try {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    incomingBytes += bytes.size
                    if (incomingBytes > MAX_FILE_SIZE) {
                        send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                        discardIncoming()
                        onStatus("Incoming file exceeded the 50 MiB transfer limit")
                        return
                    }
                    incomingDigest?.update(bytes)
                    incomingOutput?.write(bytes)
                    incomingExpectedSequence += 1
                    if (incomingBytes - lastIncomingProgressBytes >= PROGRESS_INTERVAL_BYTES) {
                        lastIncomingProgressBytes = incomingBytes
                        onStatus("Receiving file · ${incomingBytes / MEBIBYTE} MiB received")
                    }
                    send(JSONObject().put("type", "file_ack").put("transferId", transferId).put("sequence", sequence))
                } catch (error: Exception) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    discardIncoming()
                    onStatus("File write failed: ${error.message ?: "storage error"}")
                }
            }
            "file_ack" -> {
                if (message.optString("transferId") == outgoingTransferId &&
                    message.optInt("sequence", -1) == outgoingSequence
                ) {
                    cancelChunkRetry()
                    pendingChunkMessage = null
                    chunkRetryCount = 0
                    outgoingSequence++
                    waitingForAck = false
                    sendNextChunk()
                }
            }
            "file_end" -> {
                val transferId = message.optString("transferId")
                val expectedHash = message.optString("sha256")
                if (transferId.isBlank()) return
                if (incomingOutput == null) {
                    if (transferId == lastCompletedIncomingTransferId) {
                        send(JSONObject().put("type", "file_complete").put("transferId", transferId))
                    }
                    return
                }
                if (transferId != incomingTransferId) return
                val actualHash = incomingDigest?.digest()?.toHex().orEmpty()
                val sizeMatches = pendingSize < 0L || incomingBytes == pendingSize
                val hashMatches = sizeMatches && expectedHash.isNotBlank() &&
                    actualHash.equals(expectedHash, ignoreCase = true)
                if (hashMatches) {
                    lastCompletedIncomingTransferId = transferId
                    closeIncoming()
                } else {
                    discardIncoming()
                }
                if (!hashMatches) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    onStatus(if (sizeMatches) "File integrity check failed for $pendingName" else "File size mismatch for $pendingName")
                } else {
                    send(JSONObject().put("type", "file_complete").put("transferId", transferId))
                    onStatus("Received $pendingName · SHA-256 verified")
                }
                pendingName = ""
                pendingSize = 0L
            }
            "file_complete" -> {
                if (message.optString("transferId") != outgoingTransferId) return
                cancelChunkRetry()
                closeOutgoing()
                if (outgoingQueue.isNotEmpty()) {
                    try {
                        startNextFile()
                    } catch (error: Exception) {
                        outgoingQueue.clear()
                        onStatus("Next file could not be opened · ${error.message ?: "storage error"}")
                    }
                } else {
                    onStatus("File batch sent successfully")
                }
            }
        }
    }

    private fun sendNextChunk() {
        if (waitingForAck) return
        val input = outgoingInput ?: return
        try {
            val chunk = ByteArray(CHUNK_SIZE)
            val count = input.read(chunk)
            if (count < 0) {
                val hash = outgoingDigest?.digest()?.toHex().orEmpty()
                val endMessage = JSONObject().put("type", "file_end")
                    .put("transferId", outgoingTransferId)
                    .put("sha256", hash)
                pendingChunkMessage = endMessage
                chunkRetryCount = 0
                waitingForAck = true
                send(endMessage)
                retryHandler.postDelayed(chunkRetryTask, CHUNK_ACK_TIMEOUT_MS)
                return
            }
            outgoingBytes += count
            outgoingDigest?.update(chunk, 0, count)
            if (outgoingBytes - lastOutgoingProgressBytes >= PROGRESS_INTERVAL_BYTES) {
                lastOutgoingProgressBytes = outgoingBytes
                onStatus("Sending file · ${outgoingBytes / MEBIBYTE} MiB sent")
            }
            if (outgoingBytes > MAX_FILE_SIZE) {
                val transferId = outgoingTransferId
                closeOutgoing()
                outgoingQueue.clear()
                send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                onStatus("File exceeds the 50 MiB transfer limit")
                return
            }
            val encoded = Base64.encodeToString(chunk.copyOf(count), Base64.NO_WRAP)
            val chunkMessage = JSONObject().put("type", "file_chunk")
                .put("transferId", outgoingTransferId)
                .put("sequence", outgoingSequence)
                .put("data", encoded)
            pendingChunkMessage = chunkMessage
            chunkRetryCount = 0
            waitingForAck = true
            send(chunkMessage)
            retryHandler.postDelayed(chunkRetryTask, CHUNK_ACK_TIMEOUT_MS)
        } catch (error: Exception) {
            val transferId = outgoingTransferId
            closeOutgoing()
            outgoingQueue.clear()
            send(JSONObject().put("type", "file_reject").put("transferId", transferId))
            onStatus("File read failed: ${error.message ?: "storage error"}")
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        if (size > MAX_FILE_SIZE) throw IllegalArgumentException("File must be smaller than 50 MiB")
        return name.take(128) to size
    }

    private fun send(message: JSONObject) {
        if (closed || channel.state() != DataChannel.State.OPEN) return
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(message.toString().toByteArray(Charsets.UTF_8)), false))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun cancelChunkRetry() {
        retryHandler.removeCallbacks(chunkRetryTask)
        pendingChunkMessage = null
        chunkRetryCount = 0
    }

    private fun closeOutgoing() {
        cancelChunkRetry()
        try { outgoingInput?.close() } catch (_: Exception) { }
        outgoingInput = null
        outgoingDigest = null
        outgoingTransferId = null
        waitingForAck = false
    }

    private fun closeIncoming() {
        try { incomingOutput?.flush(); incomingOutput?.close() } catch (_: Exception) { }
        incomingOutput = null
        incomingUri = null
        incomingBytes = 0L
        incomingDigest = null
        incomingTransferId = null
    }

    private fun discardIncoming() {
        val destination = incomingUri
        closeIncoming()
        if (destination != null) {
            try { context.contentResolver.delete(destination, null, null) } catch (_: Exception) { }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 12 * 1024
        private const val MAX_FILE_SIZE = 50L * 1024L * 1024L
        private const val MEBIBYTE = 1024L * 1024L
        private const val PROGRESS_INTERVAL_BYTES = MEBIBYTE
        private const val MAX_CHUNK_RETRIES = 3
        private const val CHUNK_ACK_TIMEOUT_MS = 5_000L
    }
}
