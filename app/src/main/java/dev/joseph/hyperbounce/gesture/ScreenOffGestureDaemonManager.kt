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

    fun startDaemon(context: Context, threshold: Float = 0.80f, onComplete: ((Boolean) -> Unit)? = null) {
        Thread {
            var success = false
            try {
                // If already running, don't spawn duplicate
                if (isDaemonRunning()) {
                    success = true
                } else {
                    val script = """
                        APK_PATH=${'$'}(pm path dev.joseph.hyperbounce | cut -d: -f2 | head -n 1)
                        if [ -n "${'$'}APK_PATH" ]; then
                            nohup sh -c "CLASSPATH=${'$'}APK_PATH app_process /system/bin --nice-name=$DAEMON_PROCESS_NAME dev.joseph.hyperbounce.gesture.ScreenOffGestureDaemon --threshold $threshold" >/dev/null 2>&1 &
                        fi
                    """.trimIndent()

                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                    proc.waitFor()

                    // Verify it spawned
                    Thread.sleep(600)
                    success = isDaemonRunning()
                }
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
                Thread.sleep(300)
                success = !isDaemonRunning()
            } catch (_: Throwable) {
                success = false
            }

            onComplete?.invoke(success)
        }.start()
    }

    fun restartDaemon(context: Context, threshold: Float = 0.80f, onComplete: ((Boolean) -> Unit)? = null) {
        stopDaemon {
            startDaemon(context, threshold, onComplete)
        }
    }
}
