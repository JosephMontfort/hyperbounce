package dev.joseph.hyperbounce.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.io.File

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
        Log.i(TAG, "Screen-off gesture received: $gesture (confidence=$confidence)")

        when (gesture) {
            "O", "DOUBLE_TAP" -> handleGestureO(context)
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
        // Direct Hardware Torch Toggle via Sysfs
        val torch0 = File("/sys/class/leds/led:torch_0/brightness")
        val torch1 = File("/sys/class/leds/led:torch_1/brightness")
        if (torch0.exists() && torch0.canWrite()) {
            try {
                val curr = torch0.readText().trim().toIntOrNull() ?: 0
                val target = if (curr > 0) 0 else 100
                torch0.writeText("$target\n")
                if (torch1.exists() && torch1.canWrite()) {
                    torch1.writeText("$target\n")
                }
                return
            } catch (_: Throwable) {}
        }

        // Fallback via shell
        try {
            Runtime.getRuntime().exec(arrayOf("cmd", "statusbar", "toggle-flashlight"))
        } catch (_: Throwable) {}
    }

    private fun handleGestureE(context: Context) {
        // Launch Camera securely
        try {
            val cameraIntent = Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(cameraIntent)
        } catch (_: Throwable) {
            try {
                val normalCamera = Intent("android.media.action.STILL_IMAGE_CAMERA").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(normalCamera)
            } catch (_: Throwable) {}
        }
    }
}
