package dev.joseph.hyperbounce.gesture

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Controller to start, stop, monitor, and query the standalone root screen-off gesture daemon.
 */
object ScreenOffGestureDaemonManager {

    private const val DAEMON_PROCESS_NAME = "hb_gesture_daemon"

    fun isDaemonRunning(): Boolean {
        val pid = getDaemonPid()
        return pid != null && pid > 0
    }

    fun getDaemonPid(): Int? {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pgrep -f $DAEMON_PROCESS_NAME | head -n 1"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val line = reader.readLine()?.trim()
            proc.waitFor()
            if (!line.isNullOrEmpty()) {
                return line.toIntOrNull()
            }
        } catch (_: Throwable) {}
        return null
    }

    fun startDaemon(context: Context, threshold: Float = 0.50f, onComplete: ((Boolean) -> Unit)? = null) {
        Thread {
            var success = false
            try {
                // Terminate any previous instance to avoid duplicates
                Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -f $DAEMON_PROCESS_NAME")).waitFor()
                Thread.sleep(300)

                val cmd = "APK_PATH=\$(pm path dev.joseph.hyperbounce | cut -d: -f2 | head -n 1) && " +
                        "export CLASSPATH=\$APK_PATH && " +
                        "/system/bin/setsid /system/bin/app_process /system/bin --nice-name=$DAEMON_PROCESS_NAME dev.joseph.hyperbounce.gesture.ScreenOffGestureDaemon --threshold $threshold </dev/null >/data/local/tmp/hb_gesture.log 2>&1 &"

                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                proc.waitFor()

                // Wait and verify spawn
                Thread.sleep(800)
                success = isDaemonRunning()
            } catch (_: Throwable) {
                success = false
            }

            onComplete?.invoke(success)
        }.start()
    }

    fun stopDaemon(onComplete: ((Boolean) -> Unit)? = null) {
        Thread {
            var success = false
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -f $DAEMON_PROCESS_NAME"))
                proc.waitFor()
                Thread.sleep(400)
                success = !isDaemonRunning()
            } catch (_: Throwable) {
                success = false
            }

            onComplete?.invoke(success)
        }.start()
    }

    fun restartDaemon(context: Context, threshold: Float = 0.50f, onComplete: ((Boolean) -> Unit)? = null) {
        stopDaemon {
            Thread.sleep(200)
            startDaemon(context, threshold, onComplete)
        }
    }
}
