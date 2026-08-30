package dev.joseph.hyperbounce.ui.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object RootUtils {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun restartSystemUI(context: Context) {
        runCommand(
            context = context,
            command = "killall com.android.systemui || pkill -f com.android.systemui",
            label = "System UI"
        )
    }

    fun restartLauncher(context: Context) {
        runCommand(
            context = context,
            command = "killall com.miui.home || pkill -f com.miui.home",
            label = "System Launcher"
        )
    }

    fun rebootDevice(context: Context) {
        runCommand(
            context = context,
            command = "reboot || /system/bin/reboot",
            label = "Device"
        )
    }

    private fun runCommand(context: Context, command: String, label: String) {
        Thread {
            var success = false
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val exitCode = proc.waitFor()
                success = (exitCode == 0)
            } catch (t: Throwable) {
                success = false
            }

            mainHandler.post {
                if (success) {
                    Toast.makeText(context, "Restarting $label...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Cannot restart scoped app: Root missing?", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
