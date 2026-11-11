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
                NotificationChannel(CHANNEL_ID, "MEIZZO screen sharing", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("MEIZZO screen sharing")
            .setContentText("Your screen is being shared with an approved Controller")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "meizzo_screen_sharing"
        private const val NOTIFICATION_ID = 3901

        fun start(context: Context) {
            val intent = Intent(context, MediaProjectionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }
}
