package dev.joseph.hyperbounce.gesture

import java.io.File

/**
 * Dynamically resolves the Linux event node corresponding to the FocalTech touchscreen
 * controller (`focaltech_ts`) on Qualcomm/Xiaomi devices.
 *
 * Avoids hardcoding `/dev/input/event7` because kernel reboots or connected peripherals
 * (USB-C OTG, Bluetooth controllers) can shift event indices.
 */
object FocaltechNodeFinder {

    private const val TARGET_DEVICE_NAME = "focaltech_ts"
    private const val FALLBACK_NODE = "/dev/input/event7"

    fun findNode(): String {
        // Strategy 1: Sysfs enumeration (/sys/class/input/event*/device/name)
        try {
            val inputDir = File("/sys/class/input")
            if (inputDir.exists() && inputDir.isDirectory) {
                val eventDirs = inputDir.listFiles { file -> file.name.startsWith("event") }
                if (eventDirs != null) {
                    for (eventDir in eventDirs) {
                        val nameFile = File(eventDir, "device/name")
                        if (nameFile.exists() && nameFile.canRead()) {
                            val name = nameFile.readText().trim()
                            if (name.equals(TARGET_DEVICE_NAME, ignoreCase = true)) {
                                val devNode = "/dev/input/${eventDir.name}"
                                if (File(devNode).exists()) {
                                    return devNode
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Strategy 2: Parse /proc/bus/input/devices
        try {
            val procDevices = File("/proc/bus/input/devices")
            if (procDevices.exists() && procDevices.canRead()) {
                val lines = procDevices.readLines()
                var isTarget = false
                val handlerRegex = Regex("""event\d+""")
                for (line in lines) {
                    if (line.startsWith("N: Name=\"$TARGET_DEVICE_NAME\"", ignoreCase = true) ||
                        line.contains(TARGET_DEVICE_NAME, ignoreCase = true)) {
                        isTarget = true
                    } else if (isTarget && line.startsWith("H: Handlers=")) {
                        val match = handlerRegex.find(line)
                        if (match != null) {
                            val devNode = "/dev/input/${match.value}"
                            if (File(devNode).exists()) {
                                return devNode
                            }
                        }
                    } else if (line.trim().isEmpty()) {
                        isTarget = false
                    }
                }
            }
        } catch (_: Throwable) {}

        // Strategy 3: Check fallback known node
        if (File(FALLBACK_NODE).exists()) {
            return FALLBACK_NODE
        }

        return FALLBACK_NODE
    }
}
