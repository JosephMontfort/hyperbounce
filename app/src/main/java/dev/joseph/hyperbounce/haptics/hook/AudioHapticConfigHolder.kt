package dev.joseph.hyperbounce.haptics.hook

import android.content.SharedPreferences

data class AudioHapticConfig(
    val enabled: Boolean = false,
    val gamesOnly: Boolean = true,
    val sensitivity: Float = 0.75f,
    val refractoryDebounceMs: Long = 35L,
    val tickDurationMs: Long = 12L
)

object AudioHapticConfigHolder {
    var config: AudioHapticConfig = AudioHapticConfig()

    fun updateFromPrefs(prefs: SharedPreferences) {
        config = AudioHapticConfig(
            enabled = prefs.getBoolean("audio_haptics_realtime_enabled", false),
            gamesOnly = prefs.getBoolean("audio_haptics_games_only", true),
            sensitivity = prefs.getFloat("audio_haptics_sensitivity", 0.75f),
            refractoryDebounceMs = prefs.getLong("audio_haptics_refractory_ms", 35L),
            tickDurationMs = prefs.getLong("audio_haptics_tick_ms", 12L)
        )
    }
}
