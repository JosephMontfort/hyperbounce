package dev.joseph.hyperbounce.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.PowerManager
import android.util.Log

/**
 * Receives `dev.joseph.hyperbounce.GESTURE_DETECTED` broadcasts from the screen-off gesture daemon
 * and dispatches system actions.
 */
class ScreenOffGestureReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_GESTURE_DETECTED = "dev.joseph.hyperbounce.GESTURE_DETECTED"
        const val EXTRA_GESTURE = "gesture"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_TIMESTAMP = "timestamp"
        private const val TAG = "ScreenOffGestureRx"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_GESTURE_DETECTED) return

        val gesture = intent.getStringExtra(EXTRA_GESTURE) ?: return
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0.0f)
        Log.d(TAG, "Screen-off gesture received: $gesture (confidence=$confidence)")

        when (gesture) {
            "O" -> handleGestureO(context)
            "V" -> handleGestureV(context)
            "E" -> handleGestureE(context)
        }
    }

    private fun handleGestureO(context: Context) {
        // Wake device screen
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HyperBounce:GestureWake"
            )
            wakeLock?.acquire(3000L)
        } catch (_: Throwable) {
            try {
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", "224"))
            } catch (_: Throwable) {}
        }
    }

    private fun handleGestureV(context: Context) {
        // Toggle Flashlight
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cm != null) {
                val cameraId = cm.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    // Toggle via shell command or direct torch
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd statusbar toggle-flashlight 2>/dev/null || input keyevent 26"))
                }
            }
        } catch (_: Throwable) {}
    }

    private fun handleGestureE(context: Context) {
        // Launch Camera
        try {
            val cameraIntent = Intent("android.media.action.STILL_IMAGE_CAMERA").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(cameraIntent)
        } catch (_: Throwable) {}
    }
}
