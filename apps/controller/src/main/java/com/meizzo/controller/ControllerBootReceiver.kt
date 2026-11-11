package com.meizzo.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ControllerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("connection_enabled", true)) return
        val service = Intent(context, ControllerConnectionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service)
        else context.startService(service)
    }
}
