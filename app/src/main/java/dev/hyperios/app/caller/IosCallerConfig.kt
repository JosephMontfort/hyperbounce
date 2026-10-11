package dev.hyperios.app.caller

import android.content.SharedPreferences

/**
 * Configuration for the iOS-style full screen incoming caller interface.
 */
data class IosCallerConfig(
    val enabled: Boolean = true,
    val buttonSize: Float = 76f,
    val bottomOffset: Float = 68f,
    val hapticsEnabled: Boolean = true,
    val shimmerEffect: Boolean = true,
    val danceEnabled: Boolean = true,
    val slideThreshold: Float = 0.75f
)

object IosCallerConfigHolder {
    var config: IosCallerConfig = IosCallerConfig()

    fun updateFromPrefs(prefs: SharedPreferences?) {
        if (prefs == null) return
        config = IosCallerConfig(
            enabled = prefs.getBoolean("caller_screen_enabled", true),
            buttonSize = prefs.getFloat("caller_button_size", 76f),
            bottomOffset = prefs.getFloat("caller_bottom_offset", 68f),
            hapticsEnabled = prefs.getBoolean("caller_haptics_enabled", true),
            shimmerEffect = prefs.getBoolean("caller_shimmer_enabled", true),
            danceEnabled = prefs.getBoolean("caller_dance_enabled", true),
            slideThreshold = prefs.getFloat("caller_slide_threshold", 0.75f)
        )
    }
}
