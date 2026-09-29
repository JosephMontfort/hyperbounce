package dev.joseph.hyperbounce.gestures

import android.content.SharedPreferences

/**
 * In-memory configuration holder for Screen-Off Gestures in system_server.
 * Loaded instantly when PhoneWindowManager starts and updated live when
 * preferences change.
 */
object ScreenOffGestureConfigHolder {
    @Volatile var enabled: Boolean = true
    @Volatile var hapticFeedback: Boolean = true
    @Volatile var gestureOEnabled: Boolean = true // Flashlight toggle
    @Volatile var gestureCEnabled: Boolean = true // Camera launch
    @Volatile var gestureVEnabled: Boolean = true // Flashlight toggle
    @Volatile var gestureMEnabled: Boolean = true // Play / Pause music
    @Volatile var gestureWEnabled: Boolean = true // Next track
    @Volatile var gestureSwipesEnabled: Boolean = true // Swipe track & volume

    fun updateFromPrefs(prefs: SharedPreferences) {
        enabled = prefs.getBoolean("screen_off_gestures_enabled", true)
        hapticFeedback = prefs.getBoolean("screen_off_gestures_haptic", true)
        gestureOEnabled = prefs.getBoolean("screen_off_gesture_o_enabled", true)
        gestureCEnabled = prefs.getBoolean("screen_off_gesture_c_enabled", true)
        gestureVEnabled = prefs.getBoolean("screen_off_gesture_v_enabled", true)
        gestureMEnabled = prefs.getBoolean("screen_off_gesture_m_enabled", true)
        gestureWEnabled = prefs.getBoolean("screen_off_gesture_w_enabled", true)
        gestureSwipesEnabled = prefs.getBoolean("screen_off_gesture_swipes_enabled", true)
    }
}
