package com.meizzo.controller

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Local telemetry alerts for paired devices visible to this Controller. */
class AutoAlertManager(context: Context) {
    data class Settings(
        val batteryPercent: Int,
        val temperatureC: Float,
        val offlineMinutes: Int,
        val chargerUnplugged: Boolean
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("meizzo_auto_alerts", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val offlineTasks = mutableMapOf<String, Runnable>()

    init { createNotificationChannel() }

    fun settings() = Settings(
        preferences.getInt("battery_threshold", 15).coerceIn(1, 100),
        preferences.getFloat("temperature_threshold", 42f).coerceIn(25f, 80f),
        preferences.getInt("offline_minutes", 5).coerceIn(1, 1440),
        preferences.getBoolean("charger_unplugged", true)
    )

    fun updateSettings(settings: Settings) {
        preferences.edit()
            .putInt("battery_threshold", settings.batteryPercent.coerceIn(1, 100))
            .putFloat("temperature_threshold", settings.temperatureC.coerceIn(25f, 80f))
            .putInt("offline_minutes", settings.offlineMinutes.coerceIn(1, 1440))
            .putBoolean("charger_unplugged", settings.chargerUnplugged)
            .apply()
    }

    fun evaluate(device: JSONObject) {
        val clientId = device.optString("clientId")
        if (clientId.isBlank()) return
        val metadata = device.optJSONObject("metadata") ?: JSONObject()
        val label = metadata.optString("alias").takeIf { it.isNotBlank() }
            ?: metadata.optString("model").takeIf { it.isNotBlank() } ?: clientId

        if (device.optString("status") == "offline") {
            scheduleOfflineAlert(clientId, label)
            return
        }
        clearOfflineAlert(clientId)

        val telemetry = metadata.optJSONObject("telemetry") ?: return
        val config = settings()
        val battery = telemetry.optInt("batteryPercent", -1)
        if (battery in 0..100) {
            val key = "low_battery_active_$clientId"
            val active = preferences.getBoolean(key, false)
            if (battery < config.batteryPercent && !active) {
                if (notify(clientId, "battery", "Low battery · $label",
                    "Battery is at ${battery}%, below your ${config.batteryPercent}% threshold.")) {
                    preferences.edit().putBoolean(key, true).apply()
                }
            } else if (battery >= (config.batteryPercent + 3).coerceAtMost(100)) {
                preferences.edit().putBoolean(key, false).apply()
            }
        }

        val temperature = telemetry.optDouble("batteryTemperatureC", -1.0)
        if (temperature in 0.0..100.0) {
            val key = "high_temperature_active_$clientId"
            val active = preferences.getBoolean(key, false)
            if (temperature > config.temperatureC && !active) {
                val value = String.format(java.util.Locale.US, "%.1f", temperature)
                if (notify(clientId, "temperature", "Battery temperature · $label",
                    "Battery temperature is ${value}°C, above your ${config.temperatureC}°C threshold.")) {
                    preferences.edit().putBoolean(key, true).apply()
                }
            } else if (temperature <= config.temperatureC - 2f) {
                preferences.edit().putBoolean(key, false).apply()
            }
        }

        if (telemetry.has("charging")) {
            val charging = telemetry.optBoolean("charging")
            val key = "charging_previous_$clientId"
            if (config.chargerUnplugged && preferences.contains(key) &&
                preferences.getBoolean(key, false) && !charging) {
                notify(clientId, "charger", "Charger disconnected · $label",
                    "The Client reports that external power was disconnected.")
            }
            preferences.edit().putBoolean(key, charging).apply()
        }
    }

    private fun scheduleOfflineAlert(clientId: String, label: String) {
        val key = "offline_since_$clientId"
        val now = System.currentTimeMillis()
        val since = preferences.getLong(key, 0L).let { if (it == 0L) now else it }
        if (!preferences.contains(key)) preferences.edit().putLong(key, since).apply()
        offlineTasks.remove(clientId)?.let(handler::removeCallbacks)
        val delay = (settings().offlineMinutes * 60_000L - (now - since)).coerceAtLeast(0L)
        val task = Runnable {
            if (preferences.getLong(key, 0L) == since) {
                notify(clientId, "offline", "Device offline · $label",
                    "This Client has been offline for at least ${settings().offlineMinutes} minutes.")
            }
            offlineTasks.remove(clientId)
        }
        offlineTasks[clientId] = task
        handler.postDelayed(task, delay)
    }

    private fun clearOfflineAlert(clientId: String) {
        preferences.edit().remove("offline_since_$clientId").apply()
        offlineTasks.remove(clientId)?.let(handler::removeCallbacks)
    }

    private fun notify(clientId: String, type: String, title: String, message: String): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val id = 31 * clientId.hashCode() + type.hashCode()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(id, notification)
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "MEIZZO device alerts", NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = "Battery, temperature, charger, and connection alerts for paired Clients."
                setShowBadge(true)
            }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object { private const val CHANNEL_ID = "meizzo_auto_alerts" }
}
