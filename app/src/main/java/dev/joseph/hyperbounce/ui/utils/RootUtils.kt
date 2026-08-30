package dev.joseph.hyperbounce.ui.utils

import android.content.Context
import android.widget.Toast

object RootUtils {
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
            label = "Device Reboot"
        )
    }

    private fun runCommand(context: Context, command: String, label: String) {
        Toast.makeText(context, "Restarting $label...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                proc.waitFor()
            } catch (t: Throwable) {
                try {
                    Runtime.getRuntime().exec(command)
                } catch (ignored: Throwable) {}
            }
        }.start()
    }
}
