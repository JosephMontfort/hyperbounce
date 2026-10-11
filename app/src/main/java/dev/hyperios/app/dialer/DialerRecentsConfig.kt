package dev.hyperios.app.dialer

import android.content.SharedPreferences
import dev.hyperios.app.common.ModuleDefaults

data class DialerRecentsConfig(
    val enabled: Boolean = ModuleDefaults.MasterToggles.DIALER_RECENTS_SWIPE_ENABLED,
    val callThresholdDp: Float = ModuleDefaults.DialerRecents.CALL_THRESHOLD,
    val deleteWidthDp: Float = ModuleDefaults.DialerRecents.DELETE_WIDTH,
    val confirmDelete: Boolean = ModuleDefaults.DialerRecents.CONFIRM_DELETE,
    val hapticsEnabled: Boolean = true,
    val morphDurationMs: Long = ModuleDefaults.DialerRecents.MORPH_DURATION.toLong()
)

object DialerRecentsConfigHolder {
    @Volatile
    var config: DialerRecentsConfig = DialerRecentsConfig()
        private set

    fun updateFromPrefs(prefs: SharedPreferences) {
        val rawMorph = prefs.getFloat("dialer_morph_duration", ModuleDefaults.DialerRecents.MORPH_DURATION)
        val validMorph = if (rawMorph <= 50f) ModuleDefaults.DialerRecents.MORPH_DURATION.toLong() else rawMorph.toLong().coerceIn(200L, 2000L)
        config = DialerRecentsConfig(
            enabled = prefs.getBoolean("dialer_recents_swipe_enabled", ModuleDefaults.MasterToggles.DIALER_RECENTS_SWIPE_ENABLED),
            callThresholdDp = prefs.getFloat("dialer_call_threshold", ModuleDefaults.DialerRecents.CALL_THRESHOLD),
            deleteWidthDp = prefs.getFloat("dialer_delete_width", ModuleDefaults.DialerRecents.DELETE_WIDTH),
            confirmDelete = prefs.getBoolean("dialer_confirm_delete", ModuleDefaults.DialerRecents.CONFIRM_DELETE),
            hapticsEnabled = prefs.getBoolean("dialer_haptics_enabled", true),
            morphDurationMs = validMorph
        )
    }
}
