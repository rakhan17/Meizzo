package com.meizzo.client

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONObject
import org.webrtc.DataChannel
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

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
    private var outgoingSequence = 0
    private var outgoingBytes = 0L
    private var incomingBytes = 0L
    private var incomingExpectedSequence = 0
    private var outgoingDigest: MessageDigest? = null
    private var incomingDigest: MessageDigest? = null
    private var waitingForAck = false
    private var pendingName = ""
    private var pendingSize = 0L

    init {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) onStatus("Secure file channel ready")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                handleMessage(String(bytes, Charsets.UTF_8))
            }
        })
    }

    fun sendFile(uri: Uri) {
        if (channel.state() != DataChannel.State.OPEN) {
            onStatus("File channel is not connected yet")
            return
        }
        closeOutgoing()
        val metadata = queryMetadata(uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android could not open the selected file")
        outgoingInput = input
        outgoingSequence = 0
        outgoingBytes = 0L
        outgoingDigest = MessageDigest.getInstance("SHA-256")
        waitingForAck = true
        send(JSONObject().apply {
            put("type", "file_offer")
            put("name", metadata.first)
            put("size", metadata.second)
            put("mimeType", context.contentResolver.getType(uri) ?: "application/octet-stream")
        })
        onStatus("Waiting for the receiver to accept ${metadata.first}")
    }

    fun acceptIncoming(uri: Uri) {
        incomingOutput = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Android could not create the destination file")
        incomingUri = uri
        incomingBytes = 0L
        incomingExpectedSequence = 0
        incomingDigest = MessageDigest.getInstance("SHA-256")
        send(JSONObject().put("type", "file_accept"))
        onStatus("Receiving $pendingName")
    }

    fun rejectIncoming() {
        send(JSONObject().put("type", "file_reject"))
        pendingName = ""
        pendingSize = 0L
    }

    fun close() {
        closeOutgoing()
        discardIncoming()
        try { channel.unregisterObserver() } catch (_: Exception) { }
    }

    private fun handleMessage(raw: String) {
        val message = try { JSONObject(raw) } catch (_: Exception) { return }
        when (message.optString("type")) {
            "file_offer" -> {
                pendingName = message.optString("name").take(128)
                pendingSize = message.optLong("size", -1L)
                val mime = message.optString("mimeType", "application/octet-stream")
                if (pendingName.isBlank() || pendingSize < -1L || pendingSize > MAX_FILE_SIZE) {
                    send(JSONObject().put("type", "file_reject"))
                    onStatus("Rejected invalid or oversized file offer")
                    return
                }
                onIncomingOffer(pendingName, pendingSize, mime)
            }
            "file_accept" -> {
                waitingForAck = false
                sendNextChunk()
            }
            "file_reject" -> {
                closeOutgoing()
                discardIncoming()
                onStatus("The other device declined or could not complete the file")
            }
            "file_chunk" -> {
                val sequence = message.optInt("sequence", -1)
                val encoded = message.optString("data")
                if (sequence < 0 || encoded.isBlank() || incomingOutput == null) return
                if (sequence != incomingExpectedSequence) {
                    discardIncoming()
                    send(JSONObject().put("type", "file_reject"))
                    onStatus("File transfer stopped · unexpected chunk sequence")
                    return
                }
                try {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    incomingBytes += bytes.size
                    if (incomingBytes > MAX_FILE_SIZE) {
                        discardIncoming()
                        send(JSONObject().put("type", "file_reject"))
                        onStatus("Incoming file exceeded the 50 MiB transfer limit")
                        return
                    }
                    incomingDigest?.update(bytes)
                    incomingOutput?.write(bytes)
                    incomingExpectedSequence += 1
                    send(JSONObject().put("type", "file_ack").put("sequence", sequence))
                } catch (error: Exception) {
                    discardIncoming()
                    send(JSONObject().put("type", "file_reject"))
                    onStatus("File write failed: ${error.message ?: "storage error"}")
                }
            }
            "file_ack" -> {
                if (message.optInt("sequence", -1) == outgoingSequence) {
                    outgoingSequence++
                    waitingForAck = false
                    sendNextChunk()
                }
            }
            "file_end" -> {
                val expectedHash = message.optString("sha256")
                val actualHash = incomingDigest?.digest()?.toHex().orEmpty()
                val sizeMatches = pendingSize < 0L || incomingBytes == pendingSize
                val hashMatches = sizeMatches && expectedHash.isNotBlank() &&
                    actualHash.equals(expectedHash, ignoreCase = true)
                if (hashMatches) closeIncoming() else discardIncoming()
                if (!hashMatches) {
                    send(JSONObject().put("type", "file_reject"))
                    onStatus(if (sizeMatches) "File integrity check failed for $pendingName" else "File size mismatch for $pendingName")
                } else {
                    send(JSONObject().put("type", "file_complete"))
                    onStatus("Received $pendingName · SHA-256 verified")
                }
                pendingName = ""
                pendingSize = 0L
            }
            "file_complete" -> {
                closeOutgoing()
                onStatus("File sent successfully")
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
                send(JSONObject().put("type", "file_end").put("sha256", hash))
                waitingForAck = true
                return
            }
            outgoingBytes += count
            outgoingDigest?.update(chunk, 0, count)
            if (outgoingBytes > MAX_FILE_SIZE) {
                closeOutgoing()
                send(JSONObject().put("type", "file_reject"))
                onStatus("File exceeds the 50 MiB transfer limit")
                return
            }
            val encoded = Base64.encodeToString(chunk.copyOf(count), Base64.NO_WRAP)
            send(JSONObject().put("type", "file_chunk")
                .put("sequence", outgoingSequence)
                .put("data", encoded))
            waitingForAck = true
        } catch (error: Exception) {
            closeOutgoing()
            send(JSONObject().put("type", "file_reject"))
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
        if (channel.state() != DataChannel.State.OPEN) return
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(message.toString().toByteArray(Charsets.UTF_8)), false))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun closeOutgoing() {
        try { outgoingInput?.close() } catch (_: Exception) { }
        outgoingInput = null
        outgoingDigest = null
        waitingForAck = false
    }

    private fun closeIncoming() {
        try { incomingOutput?.flush(); incomingOutput?.close() } catch (_: Exception) { }
        incomingOutput = null
        incomingUri = null
        incomingBytes = 0L
        incomingDigest = null
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
    }
}
