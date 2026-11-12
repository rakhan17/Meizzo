package com.meizzo.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Raises the Client process importance while its device connection is active.
 *
 * The WebSocket is still owned by the existing Client connection layer. Moving
 * that owner into this service is a separate step; this service is not a claim
 * that Android can guarantee survival after force-stop or OEM process eviction.
 */
class ConnectionForegroundService : Service() {
    private var alarmTrack: AudioTrack? = null
    private var alarmActive = false
    private var connectionActive = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart has no live WebSocket owner to restore yet. Do not
        // show a misleading foreground notification after process recreation.
        if (intent == null) {
            stopAlarm()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_CONNECTION_STOP) {
            connectionActive = false
            if (alarmActive) {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification())
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        connectionActive = true
        var alarmRequestId: String? = null
        var alarmError: String? = null
        when (intent.action) {
            ACTION_ALARM_ON -> {
                alarmRequestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                try {
                    startAlarm()
                } catch (exception: Exception) {
                    stopAlarm()
                    alarmError = exception.message ?: "Android could not start alarm audio"
                }
            }
            ACTION_ALARM_OFF -> {
                alarmRequestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                stopAlarm()
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (alarmActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!alarmRequestId.isNullOrBlank()) {
            sendBroadcast(Intent(ACTION_ALARM_RESULT).setPackage(packageName).apply {
                putExtra(EXTRA_REQUEST_ID, alarmRequestId)
                putExtra(EXTRA_ALARM_ENABLED, alarmActive)
                putExtra(EXTRA_ALARM_ERROR, alarmError)
            })
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startAlarm() {
        if (alarmTrack?.state == AudioTrack.STATE_INITIALIZED && alarmActive) return
        stopAlarm()
        val sampleRate = 22_050
        val sampleCount = sampleRate
        val samples = ShortArray(sampleCount) { index ->
            val time = index.toDouble() / sampleRate
            val pulse = if ((time % 0.8) < 0.55) 1.0 else 0.18
            (kotlin.math.sin(2.0 * Math.PI * 880.0 * time) * 18_000.0 * pulse).toInt().toShort()
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("Android could not initialize alarm audio")
        }
        val written = track.write(samples, 0, samples.size)
        if (written != samples.size) {
            track.release()
            throw IllegalStateException("Android could not prepare alarm audio")
        }
        track.setLoopPoints(0, samples.size, -1)
        track.play()
        alarmTrack = track
        alarmActive = true
    }

    private fun stopAlarm() {
        alarmActive = false
        alarmTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
                // Audio may already have been stopped by the platform.
            } finally {
                track.release()
            }
        }
        alarmTrack = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MEIZZO device connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when MEIZZO keeps a paired device connection available."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (alarmActive) android.R.drawable.ic_lock_idle_alarm else android.R.drawable.stat_notify_sync)
            .setContentTitle(
                when {
                    alarmActive -> "MEIZZO remote alarm is ringing"
                    connectionActive -> "MEIZZO Client is active"
                    else -> "MEIZZO alarm is active"
                }
            )
            .setContentText(
                when {
                    alarmActive && connectionActive -> "Alarm is playing · tap Stop alarm to silence this device"
                    alarmActive -> "Alarm continues until stopped locally or by the Controller"
                    else -> "MEIZZO is active in the background"
                }
            )
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (alarmActive) {
            val stopAlarm = PendingIntent.getService(
                this,
                1,
                Intent(this, ConnectionForegroundService::class.java).setAction(ACTION_ALARM_OFF),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Stop alarm", stopAlarm)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_CONNECTION_STOP = "com.meizzo.client.action.CONNECTION_STOP"
        const val ACTION_ALARM_ON = "com.meizzo.client.action.ALARM_ON"
        const val ACTION_ALARM_OFF = "com.meizzo.client.action.ALARM_OFF"
        const val ACTION_ALARM_RESULT = "com.meizzo.client.action.ALARM_RESULT"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_ALARM_ENABLED = "alarm_enabled"
        const val EXTRA_ALARM_ERROR = "alarm_error"
        private const val CHANNEL_ID = "meizzo_device_connection"
        private const val NOTIFICATION_ID = 2501
    }
}
