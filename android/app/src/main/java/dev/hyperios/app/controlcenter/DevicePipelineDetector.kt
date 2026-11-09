package dev.hyperios.app.controlcenter

object DevicePipelineDetector {
    @Volatile
    private var cachedIsLowEnd: Boolean? = null

    /**
     * Determines whether the native device hardware/ROM defaults to Xiaomi's low-end pipeline.
     */
    fun isNativeLowEnd(): Boolean {
        cachedIsLowEnd?.let { return it }

        // 1. Check miui.util.DeviceLevel
        try {
            val deviceLevelClass = Class.forName("miui.util.DeviceLevel")
            val isLite = deviceLevelClass.getField("IS_MIUI_LITE_VERSION").getBoolean(null)
            if (isLite) {
                cachedIsLowEnd = true
                return true
            }
            val isMid = deviceLevelClass.getField("IS_MIUI_MIDDLE_VERSION").getBoolean(null)
            if (isMid) {
                cachedIsLowEnd = true
                return true
            }
        } catch (_: Throwable) {}

        // 2. Check miuix.device.DeviceUtils
        try {
            val deviceUtilsClass = Class.forName("miuix.device.DeviceUtils")
            val isLowEndMethod = deviceUtilsClass.getMethod("isLowEndDevice")
            if (isLowEndMethod.invoke(null) == true) {
                cachedIsLowEnd = true
                return true
            }
        } catch (_: Throwable) {}

        // 3. Check miui.os.Build
        try {
            val buildClass = Class.forName("miui.os.Build")
            val isLite = buildClass.getField("IS_MIUI_LITE_VERSION").getBoolean(null)
            if (isLite) {
                cachedIsLowEnd = true
                return true
            }
        } catch (_: Throwable) {}

        // 4. Check persist.sys.computility.cpulevel / gpulevel
        try {
            val sysPropClass = Class.forName("android.os.SystemProperties")
            val getIntMethod = sysPropClass.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            val cpuLevel = getIntMethod.invoke(null, "persist.sys.computility.cpulevel", -1) as Int
            val gpuLevel = getIntMethod.invoke(null, "persist.sys.computility.gpulevel", -1) as Int
            if ((cpuLevel in 0..2) || (gpuLevel in 0..2)) {
                cachedIsLowEnd = true
                return true
            }
        } catch (_: Throwable) {}

        cachedIsLowEnd = false
        return false
    }

    /**
     * Default animation engine mode:
     * 0 = High-End Animation Engine
     * 1 = Low-End Animation Engine
     */
    fun getDefaultAnimEngineMode(): Int {
        return if (isNativeLowEnd()) 1 else 0
    }
}
