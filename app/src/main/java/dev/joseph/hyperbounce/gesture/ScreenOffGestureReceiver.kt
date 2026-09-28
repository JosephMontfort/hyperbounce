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
        val source = intent.getStringExtra("source") ?: "external"
        Log.i(TAG, "Screen-off gesture received: $gesture (confidence=$confidence, source=$source)")

        // If the gesture originated directly from the root daemon, it already executed the hardware action
        if (source == "daemon") {
            Log.d(TAG, "Hardware action already executed by root daemon for $gesture")
            return
        }

        // External or UI test trigger: execute via root shell
        when (gesture) {
            "O", "DOUBLE_TAP" -> handleGestureO()
            "V" -> handleGestureV()
            "E" -> handleGestureE()
        }
    }

    private fun runRootCommand(command: String) {
        Thread {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command)).waitFor()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to run root command: $command", t)
            }
        }.start()
    }

    private fun handleGestureO() {
        runRootCommand("input keyevent 224")
    }

    private fun handleGestureV() {
        val script = "curr=\$(cat /sys/class/leds/led:torch_0/brightness 2>/dev/null || echo 0); " +
                "if [ \"\$curr\" -gt 0 ]; then target=0; else target=200; fi; " +
                "echo \$target > /sys/class/leds/led:torch_0/brightness 2>/dev/null; " +
                "echo \$target > /sys/class/leds/led:torch_1/brightness 2>/dev/null"
        runRootCommand(script)
    }

    private fun handleGestureE() {
        val script = "input keyevent 224 && " +
                "am start -n com.android.camera/com.android.camera.Camera -a android.media.action.STILL_IMAGE_CAMERA_SECURE --ez android.intent.extra.CAMERA_OPEN_ONLY true"
        runRootCommand(script)
    }
}

