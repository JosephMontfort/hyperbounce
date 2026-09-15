package dev.joseph.hyperbounce.lockscreen

import android.content.SharedPreferences

data class LockScreenSheetConfig(
    var enabled: Boolean = true,
    var enableOptimization: Boolean = true,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = 68.0f,
    var sideRefraction: Float = 32.0f,
    var bottomRefraction: Float = 18.0f,
    var bevelWidth: Float = 38.0f,
    var leftBevelWidth: Float = 38.0f,
    var rightBevelWidth: Float = 38.0f,
    var bottomBevelWidth: Float = 38.0f,
    var dispersionStrength: Float = 0.09f,
    var specularStrength: Float = 0.95f,
    var hairlineStrength: Float = 0.75f,
    var innerGlowStrength: Float = 0.20f,
    var innerShadow: Float = 0.45f,
    var saturationFactor: Float = 125.0f,
    var rimSoftness: Float = 2.0f,
    var blurRadius: Float = 0.0f,
    var cornerRadius: Float = 38.0f,
    // Home indicator pill
    var pillWidth: Float = 156.0f,
    var pillHeight: Float = 6.0f,
    var pillBottomMargin: Float = 9.0f
)

data class LockScreenKeypadConfig(
    var enabled: Boolean = true,
    // Liquid Glass 2.0 Button Optics
    var buttonRefraction: Float = 48.0f,
    var buttonBevel: Float = 24.0f,
    var buttonDispersion: Float = 0.09f,
    var buttonSpecular: Float = 0.95f,
    var buttonInnerShadow: Float = 0.40f,
    var buttonBlur: Float = 0.0f,
    var buttonSaturation: Float = 125.0f,
    var circleDiameter: Float = 82.0f,
    var digitTextSize: Float = 36.0f,
    var showKlondike: Boolean = false,
    var pressLiquidScale: Float = 0.88f,
    var pressLiquidAmp: Float = 0.60f,
    // PIN Pad Grid Layout & Spacing
    var pinpadLift: Float = 0.0f,
    var row0Offset: Float = -115.0f,
    var rowSpacing: Float = 30.0f,
    var columnSpacing: Float = 0.0f,
    // Legacy fine-tune offsets
    var row1Offset: Float = -90.0f,
    var row2Offset: Float = -60.0f,
    var row3Offset: Float = -30.0f,
    var col1Offset: Float = 0.0f,
    var col3Offset: Float = 0.0f,
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
        // Sheet configs (Liquid Glass 2.0)
        sheet.enabled = prefs.getBoolean("lock_sheet_enabled", true)
        sheet.enableOptimization = prefs.getBoolean("lock_sheet_enable_optimization", true)
        sheet.refractionHeight = prefs.getFloat("lock_card_refraction_height", 68.0f)
        sheet.sideRefraction = prefs.getFloat("lock_card_side_refraction", 32.0f)
        sheet.bottomRefraction = prefs.getFloat("lock_card_bottom_refraction", 18.0f)
        sheet.bevelWidth = prefs.getFloat("lock_card_bevel_width", 38.0f)
        sheet.leftBevelWidth = prefs.getFloat("lock_card_left_bevel", 38.0f)
        sheet.rightBevelWidth = prefs.getFloat("lock_card_right_bevel", 38.0f)
        sheet.bottomBevelWidth = prefs.getFloat("lock_card_bottom_bevel", 38.0f)
        sheet.dispersionStrength = prefs.getFloat("lock_card_dispersion", 0.09f)
        sheet.specularStrength = prefs.getFloat("lock_card_specular", 0.95f)
        sheet.hairlineStrength = prefs.getFloat("lock_card_hairline", 0.75f)
        sheet.innerGlowStrength = prefs.getFloat("lock_card_inner_glow", 0.20f)
        sheet.innerShadow = prefs.getFloat("lock_card_inner_shadow", 0.45f)
        sheet.saturationFactor = prefs.getFloat("lock_card_saturation", 125.0f)
        sheet.rimSoftness = prefs.getFloat("lock_card_rim_softness", 2.0f)
        sheet.blurRadius = prefs.getFloat("lock_card_blur_radius", 0.0f)
        sheet.cornerRadius = prefs.getFloat("lock_card_corner_radius", 38.0f)
        sheet.pillWidth = prefs.getFloat("lock_pill_width", 156.0f)
        sheet.pillHeight = prefs.getFloat("lock_pill_height", 6.0f)
        sheet.pillBottomMargin = prefs.getFloat("lock_pill_bottom_margin", 9.0f)

        // Keypad configs (Liquid Glass 2.0 Buttons)
        keypad.enabled = prefs.getBoolean("engine_keypad_enabled", true)
        keypad.buttonRefraction = prefs.getFloat("pin_btn_refraction", 48.0f)
        keypad.buttonBevel = prefs.getFloat("pin_btn_bevel", 24.0f)
        keypad.buttonDispersion = prefs.getFloat("pin_btn_dispersion", 0.09f)
        keypad.buttonSpecular = prefs.getFloat("pin_btn_specular", 0.95f)
        keypad.buttonInnerShadow = prefs.getFloat("pin_btn_inner_shadow", 0.40f)
        keypad.buttonBlur = prefs.getFloat("pin_btn_blur", 0.0f)
        keypad.buttonSaturation = prefs.getFloat("pin_btn_saturation", 125.0f)
        keypad.circleDiameter = prefs.getFloat("pin_circle_diameter", 82.0f)
        keypad.digitTextSize = prefs.getFloat("pin_digit_text_size", 36.0f)
        keypad.showKlondike = prefs.getBoolean("pin_show_klondike", false)
        keypad.pressLiquidScale = prefs.getFloat("pin_press_liquid_scale", 0.88f)
        keypad.pressLiquidAmp = prefs.getFloat("pin_press_liquid_amp", 0.60f)

        keypad.pinpadLift = prefs.getFloat("pin_pad_lift", 0.0f)
        keypad.row0Offset = prefs.getFloat("pin_row0_offset", -115.0f)
        keypad.rowSpacing = prefs.getFloat("pin_row_spacing", 30.0f)
        keypad.columnSpacing = prefs.getFloat("pin_col_spacing", 0.0f)
        keypad.row1Offset = prefs.getFloat("pin_row1_offset", -90.0f)
        keypad.row2Offset = prefs.getFloat("pin_row2_offset", -60.0f)
        keypad.row3Offset = prefs.getFloat("pin_row3_offset", -30.0f)
        keypad.col1Offset = prefs.getFloat("pin_col1_offset", 0.0f)
        keypad.col3Offset = prefs.getFloat("pin_col3_offset", 0.0f)

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
