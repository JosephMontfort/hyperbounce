package dev.joseph.hyperbounce.lockscreen

import android.content.SharedPreferences

data class LockScreenSheetConfig(
    var enabled: Boolean = true,
    var borderWidth: Float = 2.5f,
    var borderAlpha: Float = 0.47f,
    var refractionHeight: Float = 24.0f,
    var refractionAmount: Float = 32.0f,
    var depthEffect: Float = 0.12f,
    var pillWidth: Float = 156.0f,
    var pillHeight: Float = 6.0f,
    var pillBottomMargin: Float = 9.0f,
    var lipHeight: Float = 28.0f
)

data class LockScreenKeypadConfig(
    var enabled: Boolean = true,
    // Geometry & Typography
    var circleDiameter: Float = 82.0f,
    var fillAlpha: Float = 0.20f,
    var strokeWidth: Float = 1.5f,
    var strokeAlpha: Float = 0.47f,
    var showKlondike: Boolean = false,
    var digitTextSize: Float = 36.0f,
    // Vertical Spacing
    var row0Offset: Float = -115.0f,
    var row1Offset: Float = -90.0f,
    var row2Offset: Float = -60.0f,
    var row3Offset: Float = -30.0f,
    // Jelly Squish & Glow Physics
    var tapSquishScale: Float = 0.88f,
    var releasePlunge: Float = 0.10f,
    var jellyMaxStretch: Float = 0.16f,
    var jellyElasticLimit: Float = 0.40f,
    var springStiffness: Float = 50.0f,
    var springDamping: Float = 5.5f,
    var pressGlowAlpha: Float = 0.35f,
    var glowRadius: Float = 1.4f,
    // Sequential Glide Entry
    var entryTranslationY: Float = 90.0f,
    var entryInitialScale: Float = 0.50f,
    var entryDuration: Float = 420.0f,
    var rowDelayStep: Float = 55.0f,
    var colDelayStep: Float = 25.0f,
    var entryOvershoot: Float = 1.8f,
    // Per-Key Custom Delays (ms) - negative means use staggered formula
    var delayKey1: Float = -1f,
    var delayKey2: Float = -1f,
    var delayKey3: Float = -1f,
    var delayKey4: Float = -1f,
    var delayKey5: Float = -1f,
    var delayKey6: Float = -1f,
    var delayKey7: Float = -1f,
    var delayKey8: Float = -1f,
    var delayKey9: Float = -1f,
    var delayKey0: Float = -1f,
    var delayEmergency: Float = -1f,
    var delayCancel: Float = -1f
) {
    fun getKeyDelay(keyName: String, row: Int, col: Int): Long {
        val custom = when (keyName) {
            "key1" -> delayKey1
            "key2" -> delayKey2
            "key3" -> delayKey3
            "key4" -> delayKey4
            "key5" -> delayKey5
            "key6" -> delayKey6
            "key7" -> delayKey7
            "key8" -> delayKey8
            "key9" -> delayKey9
            "key0" -> delayKey0
            "emergency_call_button" -> delayEmergency
            "delete_button", "cancel_button" -> delayCancel
            else -> -1f
        }
        return if (custom >= 0f) custom.toLong() else ((row * rowDelayStep) + (col * colDelayStep)).toLong()
    }
}

object LockScreenConfigHolder {
    val sheet = LockScreenSheetConfig()
    val keypad = LockScreenKeypadConfig()

    fun updateFromPrefs(prefs: SharedPreferences) {
        // Sheet configs
        sheet.enabled = prefs.getBoolean("lock_sheet_enabled", true)
        sheet.borderWidth = prefs.getFloat("lock_card_border_width", 2.5f)
        sheet.borderAlpha = prefs.getFloat("lock_card_border_alpha", 0.47f)
        sheet.refractionHeight = prefs.getFloat("lock_card_refraction_height", 24.0f)
        sheet.refractionAmount = prefs.getFloat("lock_card_refraction_amount", 32.0f)
        sheet.depthEffect = prefs.getFloat("lock_card_depth_effect", 0.12f)
        sheet.pillWidth = prefs.getFloat("lock_pill_width", 156.0f)
        sheet.pillHeight = prefs.getFloat("lock_pill_height", 6.0f)
        sheet.pillBottomMargin = prefs.getFloat("lock_pill_bottom_margin", 9.0f)
        sheet.lipHeight = prefs.getFloat("lock_lip_height", 28.0f)

        // Keypad configs
        keypad.enabled = prefs.getBoolean("engine_keypad_enabled", true)
        keypad.circleDiameter = prefs.getFloat("pin_circle_diameter", 82.0f)
        keypad.fillAlpha = prefs.getFloat("pin_circle_fill_alpha", 0.20f)
        keypad.strokeWidth = prefs.getFloat("pin_circle_stroke_width", 1.5f)
        keypad.strokeAlpha = prefs.getFloat("pin_circle_stroke_alpha", 0.47f)
        keypad.showKlondike = prefs.getBoolean("pin_show_klondike", false)
        keypad.digitTextSize = prefs.getFloat("pin_digit_text_size", 36.0f)
        keypad.row0Offset = prefs.getFloat("pin_row0_offset", -115.0f)
        keypad.row1Offset = prefs.getFloat("pin_row1_offset", -90.0f)
        keypad.row2Offset = prefs.getFloat("pin_row2_offset", -60.0f)
        keypad.row3Offset = prefs.getFloat("pin_row3_offset", -30.0f)
        keypad.tapSquishScale = prefs.getFloat("pin_tap_squish_scale", 0.88f)
        keypad.releasePlunge = prefs.getFloat("pin_release_plunge", 0.10f)
        keypad.jellyMaxStretch = prefs.getFloat("pin_jelly_max_stretch", 0.16f)
        keypad.jellyElasticLimit = prefs.getFloat("pin_jelly_elastic_limit", 0.40f)
        keypad.springStiffness = prefs.getFloat("pin_spring_stiffness", 50.0f)
        keypad.springDamping = prefs.getFloat("pin_spring_damping", 5.5f)
        keypad.pressGlowAlpha = prefs.getFloat("pin_press_glow_alpha", 0.35f)
        keypad.glowRadius = prefs.getFloat("pin_glow_radius", 1.4f)
        keypad.entryTranslationY = prefs.getFloat("pin_entry_translation_y", 90.0f)
        keypad.entryInitialScale = prefs.getFloat("pin_entry_initial_scale", 0.50f)
        keypad.entryDuration = prefs.getFloat("pin_entry_duration", 420.0f)
        keypad.rowDelayStep = prefs.getFloat("pin_row_delay_step", 55.0f)
        keypad.colDelayStep = prefs.getFloat("pin_col_delay_step", 25.0f)
        keypad.entryOvershoot = prefs.getFloat("pin_entry_overshoot", 1.8f)

        keypad.delayKey1 = prefs.getFloat("pin_delay_key1", -1f)
        keypad.delayKey2 = prefs.getFloat("pin_delay_key2", -1f)
        keypad.delayKey3 = prefs.getFloat("pin_delay_key3", -1f)
        keypad.delayKey4 = prefs.getFloat("pin_delay_key4", -1f)
        keypad.delayKey5 = prefs.getFloat("pin_delay_key5", -1f)
        keypad.delayKey6 = prefs.getFloat("pin_delay_key6", -1f)
        keypad.delayKey7 = prefs.getFloat("pin_delay_key7", -1f)
        keypad.delayKey8 = prefs.getFloat("pin_delay_key8", -1f)
        keypad.delayKey9 = prefs.getFloat("pin_delay_key9", -1f)
        keypad.delayKey0 = prefs.getFloat("pin_delay_key0", -1f)
        keypad.delayEmergency = prefs.getFloat("pin_delay_emergency", -1f)
        keypad.delayCancel = prefs.getFloat("pin_delay_cancel", -1f)
    }
}
