package dev.hyperios.app.ui.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object RootUtils {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun restartSystemUI(context: Context) {
        runCommand(
            context = context,
            command = "killall -9 com.android.systemui || pkill -9 -f com.android.systemui",
            label = "System UI"
        )
    }

    fun restartLauncher(context: Context) {
        runCommand(
            context = context,
            command = "killall -9 com.miui.home || pkill -9 -f com.miui.home",
            label = "System Launcher"
        )
    }

    fun rebootDevice(context: Context) {
        runCommand(
            context = context,
            command = "svc power reboot || reboot || /system/bin/reboot",
            label = "Device"
        )
    }

    fun restartApp(context: Context, packageName: String, label: String) {
        runCommand(
            context = context,
            command = "killall $packageName || pkill -f $packageName || am force-stop $packageName",
            label = label
        )
    }

    fun restartScopedApps(context: Context, packages: List<String>) {
        if (packages.isEmpty()) {
            Toast.makeText(context, "No scoped apps found", Toast.LENGTH_SHORT).show()
            return
        }
        val cmd = packages.joinToString(" ; ") { pkg ->
            "killall $pkg || pkill -f $pkg || am force-stop $pkg"
        }
        runCommand(
            context = context,
            command = cmd,
            label = "All Scoped Apps (${packages.size} apps)"
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
