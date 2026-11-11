package com.meizzo.client

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun longPress(normalizedX: Double, normalizedY: Double, durationMs: Long = 650L): Boolean {
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val x = (normalizedX.coerceIn(0.0, 1.0) * width).toFloat()
        val y = (normalizedY.coerceIn(0.0, 1.0) * height).toFloat()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, durationMs.coerceIn(500, 2_000)
                )
            )
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun tap(normalizedX: Double, normalizedY: Double): Boolean {
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val x = (normalizedX.coerceIn(0.0, 1.0) * width).toFloat()
        val y = (normalizedY.coerceIn(0.0, 1.0) * height).toFloat()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        durationMs: Long
    ): Boolean {
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val path = Path().apply {
            moveTo((startX.coerceIn(0.0, 1.0) * width).toFloat(), (startY.coerceIn(0.0, 1.0) * height).toFloat())
            lineTo((endX.coerceIn(0.0, 1.0) * width).toFloat(), (endY.coerceIn(0.0, 1.0) * height).toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100, 2000)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun navigate(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        else -> false
    }

    fun lockScreen(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set
    }
}
