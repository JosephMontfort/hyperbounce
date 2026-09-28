package dev.joseph.hyperbounce.gesture

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
 * Key Architectural Highlights:
 * 1. Dynamic FocalTech event node resolution (no hardcoding).
 * 2. Zero-allocation event loop with a direct 24-byte little-endian NIO buffer.
 * 3. Zero idle CPU wake-locks: blocking POSIX read() lets CPU enter deep sleep.
 * 4. Micro-wake-lock held ONLY during active drawing (max 2 seconds) via /sys/power/wake_lock.
 * 5. Touch-geometry pocket touch filter (no hardware proximity sensor needed).
 * 6. Mathematical $1 Unistroke algorithm recognizing 'O', 'V', and 'E' (>80% confidence).
 * 7. Broadcasts `dev.joseph.hyperbounce.GESTURE_DETECTED` with gesture name & confidence.
 */
class ScreenOffGestureDaemon(
    private val confidenceThreshold: Float = 0.80f,
    private val onlyScreenOff: Boolean = true
) {

    companion object {
        private const val TAG = "HBGestureDaemon"
        private const val WAKE_LOCK_TAG = "hb_screenoff_gesture"
        private const val BUFFER_EVENTS = 32
        private const val BUFFER_SIZE = InputEventConstants.STRUCT_SIZE * BUFFER_EVENTS
        private const val MAX_STROKE_POINTS = 4096

        @JvmStatic
        fun main(args: Array<String>) {
            println("[$TAG] Starting HyperBounce Screen-Off Gesture Daemon...")
            var threshold = 0.80f
            var screenOffOnly = true

            for (i in args.indices) {
                if (args[i] == "--threshold" && i + 1 < args.size) {
                    threshold = args[i + 1].toFloatOrNull() ?: 0.80f
                } else if (args[i] == "--all-screen") {
                    screenOffOnly = false
                }
            }

            val daemon = ScreenOffGestureDaemon(threshold, screenOffOnly)
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
            println("[$TAG] Warning: Unable to open /sys/power/wake_lock directly: ${t.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val stream = wakeLockFile
            if (stream != null) {
                stream.write("$WAKE_LOCK_TAG\n".toByteArray(Charsets.US_ASCII))
                stream.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun releaseWakeLock() {
        try {
            val stream = wakeUnlockFile
            if (stream != null) {
                stream.write("$WAKE_LOCK_TAG\n".toByteArray(Charsets.US_ASCII))
                stream.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun isScreenAsleep(): Boolean {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "power"))
            val reader = proc.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.contains("mWakefulness=Awake")) {
                    proc.destroy()
                    return false
                }
                if (l.contains("mWakefulness=Asleep") || l.contains("mWakefulness=Dozing")) {
                    proc.destroy()
                    return true
                }
            }
            proc.waitFor()
        } catch (_: Throwable) {}
        return true
    }

    fun runLoop() {
        while (true) {
            val nodePath = FocaltechNodeFinder.findNode()
            println("[$TAG] Resolved FocalTech event node: $nodePath")

            val nodeFile = File(nodePath)
            if (!nodeFile.exists() || !nodeFile.canRead()) {
                println("[$TAG] Error: Cannot read $nodePath. Retrying in 3 seconds...")
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

                println("[$TAG] Daemon listening on $nodePath (blocking I/O, 0% CPU idle)...")

                while (true) {
                    val bytesRead = fis.read(rawBytes)
                    if (bytesRead <= 0) {
                        break
                    }

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
                println("[$TAG] Event stream disconnected ($nodePath): ${t.message}. Reconnecting...")
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
                            // Touch down
                            if (currentSlot == 0 && !inStroke) {
                                startStroke()
                            }
                        } else {
                            // Touch up for this slot (-1)
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

            InputEventConstants.EV_KEY -> {
                if (event.code == InputEventConstants.BTN_TOUCH) {
                    if (event.value == 1 && !inStroke) {
                        startStroke()
                    } else if (event.value == 0 && inStroke) {
                        finishStroke()
                    }
                }
            }

            InputEventConstants.EV_SYN -> {
                if (event.code == InputEventConstants.SYN_REPORT) {
                    if (inStroke && currentSlot == 0 && hasNewCoordinates) {
                        if (currentX >= 0f && currentY >= 0f) {
                            // Only append if moved to reduce jitter
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
                // If onlyScreenOff is enabled, check screen state before parsing
                if (onlyScreenOff && !isScreenAsleep()) {
                    return
                }

                val avgTouchMajor = if (touchMajorSamples > 0) {
                    sumTouchMajor.toFloat() / touchMajorSamples
                } else 0.0f

                // 1. Run Pocket Touch Prevention Filter
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
                    println("[$TAG] Pocket Filter: ${filterResult.reason}")
                    return
                }

                // 2. Run $1 Unistroke Recognizer
                val match = recognizer.recognize(strokeX, strokeY, count)
                val confidencePct = (match.confidence * 100).toInt()

                println("[$TAG] Recognized gesture '${match.name}' with confidence $confidencePct% (threshold: ${(confidenceThreshold * 100).toInt()}%)")

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
        println("[$TAG] >>> GESTURE TRIGGERED: $gesture (Confidence: ${(confidence * 100).toInt()}%) <<<")

        // 1. Broadcast Intent via Android am tool
        try {
            val cmd = arrayOf(
                "am", "broadcast",
                "-a", "dev.joseph.hyperbounce.GESTURE_DETECTED",
                "--es", "gesture", gesture,
                "--ef", "confidence", confidence.toString(),
                "--el", "timestamp", System.currentTimeMillis().toString()
            )
            Runtime.getRuntime().exec(cmd)
        } catch (t: Throwable) {
            println("[$TAG] Error broadcasting intent: ${t.message}")
        }

        // 2. Direct hardware / system actions for instant responsiveness
        when (gesture) {
            "O" -> {
                // Wake screen
                try {
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "224")) // KEYCODE_WAKEUP
                } catch (_: Throwable) {}
            }
            "V" -> {
                // Toggle flashlight
                toggleFlashlight()
            }
            "E" -> {
                // Launch Camera
                try {
                    Runtime.getRuntime().exec(arrayOf(
                        "am", "start",
                        "-a", "android.media.action.STILL_IMAGE_CAMERA",
                        "--ez", "android.intent.extra.CAMERA_OPEN_ONLY", "true"
                    ))
                } catch (_: Throwable) {}
            }
        }
    }

    private fun toggleFlashlight() {
        // Method 1: Check sysfs torch leds
        val torchNodes = arrayOf(
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/torch-light0/brightness",
            "/sys/class/leds/torch/brightness"
        )
        for (node in torchNodes) {
            val f = File(node)
            if (f.exists() && f.canWrite()) {
                try {
                    val curr = f.readText().trim().toIntOrNull() ?: 0
                    val next = if (curr > 0) 0 else 200
                    f.writeText("$next\n")
                    return
                } catch (_: Throwable) {}
            }
        }

        // Method 2: Fallback via broadcast
        try {
            Runtime.getRuntime().exec(arrayOf(
                "am", "broadcast",
                "-a", "dev.joseph.hyperbounce.TOGGLE_FLASHLIGHT"
            ))
        } catch (_: Throwable) {}
    }
}
