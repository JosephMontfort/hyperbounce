package dev.joseph.hyperbounce.safemode

import android.os.SystemProperties
import android.util.Log

/**
 * Maps packages to crash aliases and manages the system property `persist.hyperbounce.safemode`.
 * Follows the Hyperceiler safe mode design.
 */
object CrashScope {
    private const val TAG = "HyperBounce_CrashScope"
    const val PROP_SAFE_MODE = "persist.hyperbounce.safemode"

    private val packageToAliasMap = mapOf(
        "com.android.systemui" to "systemui",
        "miui.systemui.plugin" to "systemui",
        "com.miui.home" to "home",
        "com.instagram.android" to "instagram",
        "com.whatsapp" to "whatsapp",
        "com.google.android.youtube" to "youtube",
        "android" to "framework"
    )

    private val aliasToPackageMap = mapOf(
        "systemui" to "com.android.systemui",
        "home" to "com.miui.home",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "framework" to "android"
    )

    fun getAlias(packageName: String): String? {
        if (packageToAliasMap.containsKey(packageName)) {
            return packageToAliasMap[packageName]
        }
        for ((key, value) in packageToAliasMap) {
            if (packageName.contains(value)) {
                return value
            }
        }
        return null
    }

    fun getPackageName(alias: String): String? = aliasToPackageMap[alias]

    /**
     * Returns the set of active crashing aliases from the system property `persist.hyperbounce.safemode`.
     */
    fun getCrashingAliases(): Set<String> {
        val raw = try {
            SystemProperties.get(PROP_SAFE_MODE, "")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read system property $PROP_SAFE_MODE", t)
            ""
        }
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun isAliasInSafeModeProp(alias: String): Boolean {
        val aliases = getCrashingAliases()
        return aliases.contains("all") || aliases.contains(alias.lowercase())
    }

    fun isPackageInSafeModeProp(packageName: String): Boolean {
        val aliases = getCrashingAliases()
        if (aliases.contains("all")) return true
        val alias = getAlias(packageName) ?: return aliases.contains(packageName.lowercase())
        return aliases.contains(alias.lowercase())
    }
}
