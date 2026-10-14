package dev.hyperios.app.common

import android.os.Build

/**
 * Device and OS version detection utility for backward compatibility.
 * Provides fast, cached determination of whether the host environment
 * is HyperOS 4+ (Android 16+/17+, native Flutter/Rust launcher) or
 * HyperOS 3/legacy (Android 14/15, DEX-based launcher).
 */
object DeviceUtils {

    val isHyperOS4: Boolean by lazy {
        try {
            val codeStr = getSystemProperty("ro.mi.os.version.code")
            val code = codeStr.toIntOrNull()
            if (code != null) {
                return@lazy code >= 4
            }

            val name = getSystemProperty("ro.mi.os.version.name")
            if (name.startsWith("OS4", ignoreCase = true)) {
                return@lazy true
            }

            val incremental = getSystemProperty("ro.build.version.incremental")
            if (incremental.startsWith("OS4", ignoreCase = true)) {
                return@lazy true
            }

            Build.VERSION.SDK_INT >= 36
        } catch (_: Throwable) {
            Build.VERSION.SDK_INT >= 36
        }
    }

    val isHyperOS3: Boolean
        get() = !isHyperOS4

    fun getSystemProperty(key: String, def: String = ""): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val m = c.getMethod("get", String::class.java, String::class.java)
            (m.invoke(null, key, def) as? String) ?: def
        } catch (_: Throwable) {
            def
        }
    }
}
