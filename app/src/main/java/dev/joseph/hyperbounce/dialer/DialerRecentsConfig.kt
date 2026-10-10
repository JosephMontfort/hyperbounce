package dev.joseph.hyperbounce.dialer

import android.content.SharedPreferences

data class DialerRecentsConfig(
    val enabled: Boolean = true,
    val callThresholdDp: Float = 96f,
    val deleteWidthDp: Float = 84f,
    val confirmDelete: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val morphDurationMs: Long = 480L
)

object DialerRecentsConfigHolder {
    @Volatile
    var config: DialerRecentsConfig = DialerRecentsConfig()
        private set

    fun updateFromPrefs(prefs: SharedPreferences) {
        val rawMorph = prefs.getFloat("dialer_morph_duration", 480f)
        val validMorph = if (rawMorph <= 50f) 480L else rawMorph.toLong().coerceIn(200L, 2000L)
        config = DialerRecentsConfig(
            enabled = prefs.getBoolean("dialer_recents_swipe_enabled", true),
            callThresholdDp = prefs.getFloat("dialer_call_threshold", 96f),
            deleteWidthDp = prefs.getFloat("dialer_delete_width", 84f),
            confirmDelete = prefs.getBoolean("dialer_confirm_delete", true),
            hapticsEnabled = prefs.getBoolean("dialer_haptics_enabled", true),
            morphDurationMs = validMorph
        )
    }
}
