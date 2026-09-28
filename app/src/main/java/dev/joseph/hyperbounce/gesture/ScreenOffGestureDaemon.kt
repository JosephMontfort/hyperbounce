package dev.joseph.hyperbounce.gesture

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Highly optimized, standalone Kotlin root daemon for screen-off touch gesture recognition.
 *
 * Runs via root shell:
 * `CLASSPATH=$(pm path dev.joseph.hyperbounce | cut -d: -f2 | head -n 1) app_process /system/bin --nice-name=hb_gesture_daemon dev.joseph.hyperbounce.gesture.ScreenOffGestureDaemon`
 *
 * Dual-Engine Architecture:
 * 1. Hardware LPWG Gesture Engine: The FocalTech digitizer MCU detects unistroke gestures
 *    in sleep and reports EV_KEY events (KEY_O, KEY_V, KEY_E, KEY_W, KEY_M, KEY_C, KEY_Z, KEY_S, etc.).
 * 2. Raw Coordinate $1 Unistroke Engine: In case raw multi-touch coordinates are streamed
 *    (EV_ABS), coordinates are resampled and matched against mathematical templates.
 */
class ScreenOffGestureDaemon(
    private val confidenceThreshold: Float = 0.50f
) {

    companion object {
        private const val TAG = "HBGestureDaemon"
        private const val WAKE_LOCK_TAG = "hb_screenoff_gesture"
        private const val BUFFER_EVENTS = 32
        private const val BUFFER_SIZE = InputEventConstants.STRUCT_SIZE * BUFFER_EVENTS
        private const val MAX_STROKE_POINTS = 4096

        private fun log(msg: String) {
            println("[$TAG] $msg")
            try {
                Log.i(TAG, msg)
            } catch (_: Throwable) {}
        }

        @JvmStatic
        fun main(args: Array<String>) {
            log("Starting HyperBounce Screen-Off Gesture Daemon...")
            var threshold = 0.50f

            for (i in args.indices) {
                if (args[i] == "--threshold" && i + 1 < args.size) {
                    threshold = args[i + 1].toFloatOrNull() ?: 0.80f
                }
            }

            val daemon = ScreenOffGestureDaemon(threshold)
            daemon.runLoop()
        }
    }

    private val recognizer = UnistrokeDollarRecognizer()

    // Fixed primitive coordinate arrays (0 heap allocation in touch loop)
    private val strokeX = FloatArray(MAX_STROKE_POINTS)
    private val strokeY = FloatArray(MAX_STROKE_POINTS)
    private var strokeCount = 0

    // Stroke tracking state
    private var inStroke = false
    private var strokeStartTime = 0L
    private var currentSlot = 0
    private var maxActiveSlots = 0
    private var maxTouchMajor = 0
    private var sumTouchMajor = 0L
    private var touchMajorSamples = 0

    private var currentX = -1.0f
    private var currentY = -1.0f
    private var currentTouchMajor = 0
    private var hasNewCoordinates = false

    // Sysfs WakeLock handles
    private var wakeLockFile: FileOutputStream? = null
    private var wakeUnlockFile: FileOutputStream? = null

    init {
        try {
            val wl = File("/sys/power/wake_lock")
            if (wl.exists() && wl.canWrite()) {
                wakeLockFile = FileOutputStream(wl)
            }
            val wul = File("/sys/power/wake_unlock")
            if (wul.exists() && wul.canWrite()) {
                wakeUnlockFile = FileOutputStream(wul)
            }
        } catch (t: Throwable) {
            log("Warning: Unable to open /sys/power/wake_lock directly: ${t.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            wakeLockFile?.apply {
                write("$WAKE_LOCK_TAG\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
        } catch (_: Throwable) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeUnlockFile?.apply {
                write("$WAKE_LOCK_TAG\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
        } catch (_: Throwable) {}
    }

    private fun ensureFocaltechGestureMode() {
        val paths = arrayOf(
            "/sys/devices/platform/soc/a8c000.spi/spi_master/spi1/spi1.0/fts_gesture_mode",
            "/sys/class/touch/touch_dev/suspend_state"
        )
        for (path in paths) {
            val f = File(path)
            if (f.exists() && f.canWrite()) {
                try {
                    f.writeText("1\n")
                    log("Configured $path = 1")
                } catch (t: Throwable) {
                    log("Failed to write to $path: ${t.message}")
                }
            }
        }
    }

    fun runLoop() {
        ensureFocaltechGestureMode()

        while (true) {
            val nodePath = FocaltechNodeFinder.findNode()
            log("Resolved FocalTech event node: $nodePath")

            val nodeFile = File(nodePath)
            if (!nodeFile.exists() || !nodeFile.canRead()) {
                log("Error: Cannot read $nodePath. Retrying in 3 seconds...")
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) {}
                continue
            }

            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(nodeFile)
                val rawBytes = ByteArray(BUFFER_SIZE)
                val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                val event = MutableInputEvent()

                log("Daemon listening on $nodePath (blocking I/O, 0% CPU idle)...")

                while (true) {
                    val bytesRead = fis.read(rawBytes)
                    if (bytesRead <= 0) break

                    byteBuffer.position(0)
                    byteBuffer.limit(bytesRead)

                    var offset = 0
                    while (offset + InputEventConstants.STRUCT_SIZE <= bytesRead) {
                        event.parseFrom(byteBuffer, offset)
                        processEvent(event)
                        offset += InputEventConstants.STRUCT_SIZE
                    }
                }
            } catch (t: Throwable) {
                log("Event stream disconnected ($nodePath): ${t.message}. Reconnecting...")
            } finally {
                try {
                    fis?.close()
                } catch (_: Throwable) {}
                releaseWakeLock()
            }

            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {}
        }
    }

    private fun processEvent(event: MutableInputEvent) {
        when (event.type) {
            InputEventConstants.EV_KEY -> {
                // 1. Direct Hardware LPWG Gestures from FocalTech MCU Firmware
                if (event.value == 1) { // Key down event
                    val hwGesture = when (event.code) {
                        InputEventConstants.KEY_O -> "O"
                        InputEventConstants.KEY_V -> "V"
                        InputEventConstants.KEY_E -> "E"
                        InputEventConstants.KEY_W -> "W"
                        InputEventConstants.KEY_M -> "M"
                        InputEventConstants.KEY_C -> "C"
                        InputEventConstants.KEY_Z -> "Z"
                        InputEventConstants.KEY_S -> "S"
                        InputEventConstants.KEY_UP -> "SWIPE_UP"
                        InputEventConstants.KEY_DOWN -> "SWIPE_DOWN"
                        InputEventConstants.KEY_LEFT -> "SWIPE_LEFT"
                        InputEventConstants.KEY_RIGHT -> "SWIPE_RIGHT"
                        InputEventConstants.KEY_WAKEUP -> "DOUBLE_TAP"
                        else -> null
                    }
                    if (hwGesture != null) {
                        log("Hardware LPWG gesture detected: $hwGesture (key code ${event.code})")
                        acquireWakeLock()
                        try {
                            dispatchGesture(hwGesture, 1.0f)
                        } finally {
                            releaseWakeLock()
                        }
                    }
                }

                // 2. Track raw touch stream (BTN_TOUCH down / up)
                if (event.code == InputEventConstants.BTN_TOUCH) {
                    if (event.value == 1 && !inStroke) {
                        startStroke()
                    } else if (event.value == 0 && inStroke) {
                        finishStroke()
                    }
                }
            }

            InputEventConstants.EV_ABS -> {
                when (event.code) {
                    InputEventConstants.ABS_MT_SLOT -> {
                        currentSlot = event.value
                        if (currentSlot > maxActiveSlots) {
                            maxActiveSlots = currentSlot
                        }
                    }
                    InputEventConstants.ABS_MT_TRACKING_ID -> {
                        if (event.value >= 0) {
                            if (currentSlot == 0 && !inStroke) {
                                startStroke()
                            }
                        } else {
                            if (currentSlot == 0 && inStroke) {
                                finishStroke()
                            }
                        }
                    }
                    InputEventConstants.ABS_MT_POSITION_X -> {
                        if (currentSlot == 0) {
                            currentX = event.value.toFloat()
                            hasNewCoordinates = true
                        }
                    }
                    InputEventConstants.ABS_MT_POSITION_Y -> {
                        if (currentSlot == 0) {
                            currentY = event.value.toFloat()
                            hasNewCoordinates = true
                        }
                    }
                    InputEventConstants.ABS_MT_TOUCH_MAJOR -> {
                        if (currentSlot == 0) {
                            currentTouchMajor = event.value
                            if (currentTouchMajor > maxTouchMajor) {
                                maxTouchMajor = currentTouchMajor
                            }
                            sumTouchMajor += currentTouchMajor
                            touchMajorSamples++
                        }
                    }
                }
            }

            InputEventConstants.EV_SYN -> {
                if (event.code == InputEventConstants.SYN_REPORT) {
                    if (inStroke && currentSlot == 0 && hasNewCoordinates) {
                        if (currentX >= 0f && currentY >= 0f) {
                            if (strokeCount == 0 ||
                                strokeX[strokeCount - 1] != currentX ||
                                strokeY[strokeCount - 1] != currentY
                            ) {
                                if (strokeCount < MAX_STROKE_POINTS) {
                                    strokeX[strokeCount] = currentX
                                    strokeY[strokeCount] = currentY
                                    strokeCount++
                                }
                            }
                        }
                        hasNewCoordinates = false
                    }
                }
            }
        }
    }

    private fun startStroke() {
        acquireWakeLock()
        inStroke = true
        strokeStartTime = System.currentTimeMillis()
        strokeCount = 0
        currentSlot = 0
        maxActiveSlots = 0
        maxTouchMajor = 0
        sumTouchMajor = 0L
        touchMajorSamples = 0
        currentX = -1.0f
        currentY = -1.0f
        hasNewCoordinates = false
    }

    private fun finishStroke() {
        inStroke = false
        val durationMs = System.currentTimeMillis() - strokeStartTime
        val count = strokeCount

        try {
            if (count >= 2) {
                val avgTouchMajor = if (touchMajorSamples > 0) {
                    sumTouchMajor.toFloat() / touchMajorSamples
                } else 0.0f

                // 1. Run Touch-Geometry Pocket Prevention Filter
                val filterResult = PocketTouchFilter.evaluateStroke(
                    xPoints = strokeX,
                    yPoints = strokeY,
                    count = count,
                    durationMs = durationMs,
                    maxTouchMajor = maxTouchMajor,
                    avgTouchMajor = avgTouchMajor,
                    maxActiveSlots = maxActiveSlots
                )

                if (!filterResult.accepted) {
                    log("Pocket Filter: ${filterResult.reason}")
                    return
                }

                // 2. Run $1 Unistroke Recognizer
                val match = recognizer.recognize(strokeX, strokeY, count)
                val confidencePct = (match.confidence * 100).toInt()

                log("Recognized raw stroke '${match.name}' with confidence $confidencePct% (threshold: ${(confidenceThreshold * 100).toInt()}%)")

                if (match.confidence >= confidenceThreshold && match.name != "UNKNOWN") {
                    dispatchGesture(match.name, match.confidence)
                }
            }
        } finally {
            strokeCount = 0
            releaseWakeLock()
        }
    }

    private fun dispatchGesture(gesture: String, confidence: Float) {
        log(">>> GESTURE TRIGGERED: $gesture (Confidence: ${(confidence * 100).toInt()}%) <<<")

        // 1. Broadcast Intent
        try {
            val cmd = arrayOf(
                "am", "broadcast",
                "-a", "dev.joseph.hyperbounce.GESTURE_DETECTED",
                "--es", "gesture", gesture,
                "--ef", "confidence", confidence.toString(),
                "--es", "source", "daemon",
                "--el", "timestamp", System.currentTimeMillis().toString()
            )
            Runtime.getRuntime().exec(cmd)
        } catch (t: Throwable) {
            log("Error broadcasting intent: ${t.message}")
        }

        // 2. Direct Hardware Actions
        when (gesture) {
            "O", "DOUBLE_TAP" -> {
                // Wake screen
                try {
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "224")) // KEYCODE_WAKEUP
                } catch (_: Throwable) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26")) // KEYCODE_POWER
                    } catch (_: Throwable) {}
                }
            }
            "V" -> {
                // Toggle Flashlight directly via sysfs
                toggleFlashlight()
            }
            "E" -> {
                // Launch Camera: Wake screen first so activity displays immediately on lockscreen
                try {
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "224"))
                } catch (_: Throwable) {}
                try {
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "27")) // KEYCODE_CAMERA
                } catch (_: Throwable) {}
                try {
                    Runtime.getRuntime().exec(arrayOf(
                        "am", "start",
                        "-n", "com.android.camera/com.android.camera.Camera",
                        "-a", "android.media.action.STILL_IMAGE_CAMERA_SECURE",
                        "--ez", "android.intent.extra.CAMERA_OPEN_ONLY", "true"
                    ))
                } catch (_: Throwable) {}
            }
        }
    }

    private fun toggleFlashlight() {
        val torch0 = File("/sys/class/leds/led:torch_0/brightness")
        val torch1 = File("/sys/class/leds/led:torch_1/brightness")
        if (torch0.exists() && torch0.canWrite()) {
            try {
                val curr = torch0.readText().trim().toIntOrNull() ?: 0
                val target = if (curr > 0) 0 else 200
                torch0.writeText("$target\n")
                if (torch1.exists() && torch1.canWrite()) {
                    torch1.writeText("$target\n")
                }
                log("Flashlight toggled to brightness: $target")
                return
            } catch (t: Throwable) {
                log("Error toggling sysfs torch: ${t.message}")
            }
        }

        // Fallback via cmd statusbar
        try {
            Runtime.getRuntime().exec(arrayOf("cmd", "statusbar", "toggle-flashlight"))
        } catch (_: Throwable) {}
    }
}
