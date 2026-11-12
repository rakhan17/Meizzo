package com.meizzo.client

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        if (Build.VERSION.SDK_INT >= 33) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        }
        setServiceInfo(info)
        instance = this
    }

    fun sendKey(keyCode: Int): Boolean = sendKey(keyCode, 0)

    fun sendShortcut(keyCode: Int): Boolean {
        val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        return sendKey(keyCode, modifiers)
    }

    private fun sendKey(keyCode: Int, metaState: Int): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val connection = getInputMethod()?.getCurrentInputConnection() ?: return false
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        val up = KeyEvent(
            downTime,
            android.os.SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP,
            keyCode,
            0,
            metaState
        )
        val downSent = connection.sendKeyEvent(down)
        val upSent = connection.sendKeyEvent(up)
        return downSent && upSent
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

    /**
     * Converts a two-finger Controller trackpad movement into an Android scroll
     * gesture. Positive deltas represent the fingers moving toward the top/left.
     */
    fun scroll(deltaX: Double, deltaY: Double): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() ||
            deltaX !in -0.35..0.35 || deltaY !in -0.35..0.35 ||
            (kotlin.math.abs(deltaX) < 0.01 && kotlin.math.abs(deltaY) < 0.01)
        ) return false
        val startX = (0.5 + deltaX / 2.0).coerceIn(0.15, 0.85)
        val endX = (0.5 - deltaX / 2.0).coerceIn(0.15, 0.85)
        val startY = (0.5 + deltaY / 2.0).coerceIn(0.15, 0.85)
        val endY = (0.5 - deltaY / 2.0).coerceIn(0.15, 0.85)
        return swipe(startX, startY, endX, endY, 220L)
    }

    /**
     * Appends text to the currently focused, non-password input field.
     * Accessibility cannot safely or reliably type into every custom keyboard/editor;
     * report failure instead of pretending the text was entered.
     */
    fun appendText(value: String): Boolean {
        if (value.isEmpty() || value.length > 2_000) return false
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (field.isPassword || !field.isEditable || !field.isEnabled) return false
        val current = field.text?.toString().orEmpty()
        val selectionStart = field.textSelectionStart.takeIf { it in 0..current.length } ?: current.length
        val selectionEnd = field.textSelectionEnd.takeIf { it in selectionStart..current.length } ?: selectionStart
        val updated = current.substring(0, selectionStart) + value + current.substring(selectionEnd)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return false
        val cursor = selectionStart + value.length
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        return true
    }

    fun deleteBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (field.isPassword || !field.isEditable || !field.isEnabled) return false
        val current = field.text?.toString().orEmpty()
        val selectionStart = field.textSelectionStart.takeIf { it in 0..current.length } ?: current.length
        val selectionEnd = field.textSelectionEnd.takeIf { it in selectionStart..current.length } ?: selectionStart
        if (selectionStart == selectionEnd && selectionStart == 0) return true
        val deleteStart = if (selectionStart != selectionEnd) selectionStart
            else current.offsetByCodePoints(selectionStart, -1)
        val updated = current.removeRange(deleteStart, selectionEnd)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return false
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, deleteStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, deleteStart)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        return true
    }

    fun navigate(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "power_dialog" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
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
