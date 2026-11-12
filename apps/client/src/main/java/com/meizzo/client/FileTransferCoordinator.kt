package com.meizzo.client

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
    private val resumePrefs by lazy { context.getSharedPreferences("meizzo_file_transfer_resume", Context.MODE_PRIVATE) }
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
                        DataChannel.State.OPEN -> { onStatus("Secure file channel ready"); resumeOutgoingOfferIfPresent() }
                        DataChannel.State.CLOSING, DataChannel.State.CLOSED -> {
                            closed = true
                            retryHandler.removeCallbacksAndMessages(null)
                            cancelChunkRetry()
                            pauseActiveTransfer()
                            outgoingQueue.clear()
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
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        resumePrefs.edit().putString("outgoing_id", outgoingTransferId).putString("outgoing_uri", uri.toString())
            .putString("outgoing_name", metadata.first).putLong("outgoing_size", metadata.second)
            .putString("outgoing_mime", mimeType).putLong("outgoing_offset", 0L).commit()
        waitingForAck = true
        send(JSONObject().apply {
            put("type", "file_offer"); put("transferId", outgoingTransferId)
            put("name", metadata.first); put("size", metadata.second); put("mimeType", mimeType); put("offset", 0L)
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
        resumePrefs.edit().putString("incoming_id", transferId).putString("incoming_uri", uri.toString())
            .putLong("incoming_offset", 0L).putString("incoming_name", pendingName).putLong("incoming_size", pendingSize).commit()
        send(JSONObject().put("type", "file_accept").put("transferId", transferId).put("offset", 0L))
        onStatus("Receiving $pendingName")
    }

    fun rejectIncoming() {
        send(JSONObject().put("type", "file_reject").put("transferId", incomingTransferId))
        pendingName = ""
        pendingSize = 0L
        clearIncomingCheckpoint()
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
        clearOutgoingCheckpoint()
        outgoingQueue.clear()
        clearIncomingCheckpoint()
        discardIncoming()
        pendingName = ""
        pendingSize = 0L
        onStatus("File transfer cancelled")
    }

    fun close() {
        if (closed) return
        closed = true
        pauseActiveTransfer()
        outgoingQueue.clear()
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
                if (restoreIncomingCheckpoint(transferId)) return
                onIncomingOffer(pendingName, pendingSize, mime)
            }
            "file_accept" -> {
                val transferId = message.optString("transferId")
                val offset = message.optLong("offset", 0L)
                if (transferId == outgoingTransferId) {
                    if (offset > 0L && offset != outgoingBytes && !restoreOutgoingCheckpoint(transferId, offset)) {
                        send(JSONObject().put("type", "file_reject").put("transferId", transferId)); return
                    }
                    waitingForAck = false; sendNextChunk()
                } else if (restoreOutgoingCheckpoint(transferId, offset)) {
                    waitingForAck = false; sendNextChunk()
                }
            }
            "file_reject" -> {
                val transferId = message.optString("transferId")
                when {
                    transferId.isNotBlank() && transferId == outgoingTransferId -> {
                        cancelChunkRetry()
                        closeOutgoing()
                        clearOutgoingCheckpoint()
                        outgoingQueue.clear()
                        onStatus("The other device declined or could not complete the file")
                    }
                    transferId.isNotBlank() && transferId == incomingTransferId -> {
                        clearIncomingCheckpoint()
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
                if (encoded.length > MAX_ENCODED_CHUNK_SIZE) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    discardIncoming()
                    pendingName = ""
                    pendingSize = 0L
                    onStatus("File transfer stopped · incoming chunk is oversized")
                    return
                }
                if (sequence == incomingExpectedSequence - 1) {
                    send(JSONObject().put("type", "file_ack").put("transferId", transferId).put("sequence", sequence))
                    return
                }
                if (sequence != incomingExpectedSequence) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    discardIncoming()
                    pendingName = ""
                    pendingSize = 0L
                    onStatus("File transfer stopped · unexpected chunk sequence")
                    return
                }
                try {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    if (bytes.isEmpty() || bytes.size > CHUNK_SIZE ||
                        incomingBytes + bytes.size > MAX_FILE_SIZE ||
                        (pendingSize >= 0L && incomingBytes + bytes.size > pendingSize)
                    ) {
                        send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                        discardIncoming()
                        pendingName = ""
                        pendingSize = 0L
                        onStatus("Incoming file chunk exceeded its declared size or transfer limits")
                        return
                    }
                    incomingBytes += bytes.size
                    incomingDigest?.update(bytes)
                    incomingOutput?.write(bytes)
                    incomingOutput?.flush()
                    incomingExpectedSequence += 1
                    resumePrefs.edit().putLong("incoming_offset", incomingBytes).commit()
                    if (incomingBytes - lastIncomingProgressBytes >= PROGRESS_INTERVAL_BYTES) {
                        lastIncomingProgressBytes = incomingBytes
                        onStatus("Receiving file · ${incomingBytes / MEBIBYTE} MiB received")
                    }
                    send(JSONObject().put("type", "file_ack").put("transferId", transferId).put("sequence", sequence))
                } catch (error: Exception) {
                    send(JSONObject().put("type", "file_reject").put("transferId", transferId))
                    discardIncoming()
                    pendingName = ""
                    pendingSize = 0L
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
                    resumePrefs.edit().putLong("outgoing_offset", outgoingBytes).commit()
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
                    clearIncomingCheckpoint()
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
                clearOutgoingCheckpoint()
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

    private fun resumeOutgoingOfferIfPresent() {
        val id = resumePrefs.getString("outgoing_id", null) ?: return
        if (outgoingTransferId == id || outgoingInput != null) return
        send(JSONObject().apply {
            put("type", "file_offer"); put("transferId", id)
            put("name", resumePrefs.getString("outgoing_name", "shared-file"))
            put("size", resumePrefs.getLong("outgoing_size", -1L))
            put("mimeType", resumePrefs.getString("outgoing_mime", "application/octet-stream"))
            put("offset", resumePrefs.getLong("outgoing_offset", 0L)); put("resume", true)
        })
        onStatus("Reconnecting file transfer · waiting for receiver checkpoint")
    }

    private fun restoreOutgoingCheckpoint(transferId: String, requestedOffset: Long): Boolean {
        if (transferId.isBlank() || requestedOffset < 0L || requestedOffset > MAX_FILE_SIZE) return false
        if (resumePrefs.getString("outgoing_id", null) != transferId) return false
        val rawUri = resumePrefs.getString("outgoing_uri", null) ?: return false
        val input = try { context.contentResolver.openInputStream(Uri.parse(rawUri)) } catch (_: Exception) { null } ?: return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var skipped = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            while (skipped < requestedOffset) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), requestedOffset - skipped).toInt())
                if (count <= 0) throw IllegalStateException("Source file is shorter than its saved resume offset")
                digest.update(buffer, 0, count); skipped += count
            }
            try { outgoingInput?.close() } catch (_: Exception) { }
            outgoingInput = input; outgoingDigest = digest; outgoingTransferId = transferId
            outgoingBytes = requestedOffset; outgoingSequence = (requestedOffset / CHUNK_SIZE).toInt()
            lastOutgoingProgressBytes = requestedOffset; waitingForAck = false
            onStatus("Resuming file transfer · " + (requestedOffset / MEBIBYTE) + " MiB verified")
            true
        } catch (_: Exception) { try { input.close() } catch (_: Exception) { }; false }
    }

    private fun restoreIncomingCheckpoint(transferId: String): Boolean {
        if (resumePrefs.getString("incoming_id", null) != transferId) return false
        val rawUri = resumePrefs.getString("incoming_uri", null) ?: return false
        val uri = Uri.parse(rawUri); val offset = resumePrefs.getLong("incoming_offset", 0L)
        if (offset < 0L || offset > MAX_FILE_SIZE) return false
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Saved destination is unreadable")
            val digest = MessageDigest.getInstance("SHA-256"); var restored = 0L; val buffer = ByteArray(CHUNK_SIZE)
            input.use { stream ->
                while (restored < offset) {
                    val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), offset - restored).toInt())
                    if (count <= 0) throw IllegalStateException("Saved destination is shorter than its checkpoint")
                    digest.update(buffer, 0, count); restored += count
                }
            }
            if (restored != offset) throw IllegalStateException("Saved destination checkpoint is invalid")
            incomingOutput = context.contentResolver.openOutputStream(uri, "wa") ?: throw IllegalStateException("Saved destination cannot be reopened for append")
            incomingUri = uri; incomingBytes = offset; incomingExpectedSequence = (offset / CHUNK_SIZE).toInt()
            incomingDigest = digest; incomingTransferId = transferId; lastIncomingProgressBytes = offset
            send(JSONObject().put("type", "file_accept").put("transferId", transferId).put("offset", offset))
            onStatus("Resuming " + pendingName + " · " + (offset / MEBIBYTE) + " MiB verified")
            true
        } catch (_: Exception) { onStatus("Saved transfer could not resume · choose the destination again"); clearIncomingCheckpoint(); false }
    }

    private fun clearOutgoingCheckpoint() {
        resumePrefs.edit().remove("outgoing_id").remove("outgoing_uri").remove("outgoing_name").remove("outgoing_size").remove("outgoing_mime").remove("outgoing_offset").commit()
    }
    private fun clearIncomingCheckpoint() {
        resumePrefs.edit().remove("incoming_id").remove("incoming_uri").remove("incoming_offset").remove("incoming_name").remove("incoming_size").commit()
    }
    private fun pauseActiveTransfer() {
        cancelChunkRetry(); try { outgoingInput?.close() } catch (_: Exception) { }
        outgoingInput = null; outgoingDigest = null; waitingForAck = false
        try { incomingOutput?.flush(); incomingOutput?.close() } catch (_: Exception) { }
        incomingOutput = null; incomingDigest = null; incomingUri = null
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
        private const val MAX_ENCODED_CHUNK_SIZE = ((CHUNK_SIZE + 2) / 3) * 4
        private const val MAX_FILE_SIZE = 50L * 1024L * 1024L
        private const val MEBIBYTE = 1024L * 1024L
        private const val PROGRESS_INTERVAL_BYTES = MEBIBYTE
        private const val MAX_CHUNK_RETRIES = 3
        private const val CHUNK_ACK_TIMEOUT_MS = 5_000L
    }
}
