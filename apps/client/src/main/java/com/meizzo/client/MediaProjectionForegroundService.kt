package com.meizzo.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class MediaProjectionForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MEIZZO remote media", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val screenCapture = intent?.getBooleanExtra(EXTRA_SCREEN_CAPTURE, false) ?: false
        val camera = intent?.getBooleanExtra(EXTRA_CAMERA, false) ?: false
        val microphone = intent?.getBooleanExtra(EXTRA_MICROPHONE, false) ?: false
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("MEIZZO remote media")
            .setContentText(
                when {
                    screenCapture && camera && microphone -> "Screen, camera, and microphone are shared with an approved Controller"
                    screenCapture && microphone -> "Screen and microphone are shared with an approved Controller"
                    screenCapture -> "Your screen is shared with an approved Controller"
                    camera && microphone -> "Camera and microphone are shared with an approved Controller"
                    camera -> "Your camera is shared with an approved Controller"
                    microphone -> "Your microphone is shared with an approved Controller"
                    else -> "An approved MEIZZO media session is active"
                }
            )
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            var serviceTypes = 0
            if (screenCapture) serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (camera) serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (microphone) serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else if (Build.VERSION.SDK_INT >= 29 && screenCapture) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "meizzo_screen_sharing"
        private const val NOTIFICATION_ID = 3901
        private const val EXTRA_SCREEN_CAPTURE = "screen_capture"
        private const val EXTRA_CAMERA = "camera"
        private const val EXTRA_MICROPHONE = "microphone"

        fun start(context: Context, screenCapture: Boolean, camera: Boolean, microphone: Boolean) {
            val intent = Intent(context, MediaProjectionForegroundService::class.java)
                .putExtra(EXTRA_SCREEN_CAPTURE, screenCapture)
                .putExtra(EXTRA_CAMERA, camera)
                .putExtra(EXTRA_MICROPHONE, microphone)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }
}
