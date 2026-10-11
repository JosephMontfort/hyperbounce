package dev.hyperios.app.caller

import android.content.SharedPreferences
import dev.hyperios.app.common.ModuleDefaults

/**
 * Configuration for the iOS-style full screen incoming caller interface.
 */
data class IosCallerConfig(
    val enabled: Boolean = ModuleDefaults.MasterToggles.CALLER_SCREEN_ENABLED,
    val buttonSize: Float = ModuleDefaults.CallerScreen.BUTTON_SIZE,
    val bottomOffset: Float = ModuleDefaults.CallerScreen.BOTTOM_OFFSET,
    val hapticsEnabled: Boolean = true,
    val shimmerEffect: Boolean = true,
    val danceEnabled: Boolean = ModuleDefaults.CallerScreen.DANCE_ENABLED,
    val slideThreshold: Float = 0.75f
)

object IosCallerConfigHolder {
    var config: IosCallerConfig = IosCallerConfig()

    fun updateFromPrefs(prefs: SharedPreferences?) {
        if (prefs == null) return
        config = IosCallerConfig(
            enabled = prefs.getBoolean("caller_screen_enabled", ModuleDefaults.MasterToggles.CALLER_SCREEN_ENABLED),
            buttonSize = prefs.getFloat("caller_button_size", ModuleDefaults.CallerScreen.BUTTON_SIZE),
            bottomOffset = prefs.getFloat("caller_bottom_offset", ModuleDefaults.CallerScreen.BOTTOM_OFFSET),
            hapticsEnabled = prefs.getBoolean("caller_haptics_enabled", true),
            shimmerEffect = prefs.getBoolean("caller_shimmer_enabled", true),
            danceEnabled = prefs.getBoolean("caller_dance_enabled", ModuleDefaults.CallerScreen.DANCE_ENABLED),
            slideThreshold = prefs.getFloat("caller_slide_threshold", 0.75f)
        )
    }
}
