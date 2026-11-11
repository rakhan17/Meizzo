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
    private var outgoingInput: InputStream? = null
    private var outgoingSequence = 0
    private var outgoingBytes = 0L
    private var incomingBytes = 0L
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
        incomingBytes = 0L
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
        closeIncoming()
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
                onStatus("The other device declined the file")
            }
            "file_chunk" -> {
                val sequence = message.optInt("sequence", -1)
                val encoded = message.optString("data")
                if (sequence < 0 || encoded.isBlank() || incomingOutput == null) return
                try {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    incomingBytes += bytes.size
                    if (incomingBytes > MAX_FILE_SIZE) {
                        closeIncoming()
                        send(JSONObject().put("type", "file_reject"))
                        onStatus("Incoming file exceeded the 50 MiB transfer limit")
                        return
                    }
                    incomingOutput?.write(bytes)
                    send(JSONObject().put("type", "file_ack").put("sequence", sequence))
                } catch (error: Exception) {
                    closeIncoming()
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
                closeIncoming()
                send(JSONObject().put("type", "file_complete"))
                onStatus("Received $pendingName successfully")
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
                send(JSONObject().put("type", "file_end"))
                waitingForAck = true
                return
            }
            outgoingBytes += count
            if (outgoingBytes > MAX_FILE_SIZE) {
                closeOutgoing()
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

    private fun closeOutgoing() {
        try { outgoingInput?.close() } catch (_: Exception) { }
        outgoingInput = null
        waitingForAck = false
    }

    private fun closeIncoming() {
        try { incomingOutput?.flush(); incomingOutput?.close() } catch (_: Exception) { }
        incomingOutput = null
        incomingBytes = 0L
    }

    companion object {
        private const val CHUNK_SIZE = 12 * 1024
        private const val MAX_FILE_SIZE = 50L * 1024L * 1024L
    }
}
