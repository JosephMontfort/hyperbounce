package dev.joseph.hyperbounce.notification

import android.content.SharedPreferences

data class NotificationSheetConfig(
    var enabled: Boolean = true,
    var lockWallpaperEnabled: Boolean = false,
    var lockWallpaperHomescreenOnly: Boolean = false,
    var depthWallpaperEnabled: Boolean = false,
    var depthCustomSubject: Boolean = false,
    var glassEffectsEnabled: Boolean = false,
    var glassOnlyWithoutWallpaper: Boolean = false,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = 24.0f,
    var bevelWidth: Float = 28.0f,
    var leftBevelWidth: Float = 28.0f,
    var rightBevelWidth: Float = 28.0f,
    var bottomBevelWidth: Float = 28.0f,
    var dispersionStrength: Float = 0.025f,
    var specularStrength: Float = 0.90f,
    var innerShadow: Float = 0.0f,
    var saturationFactor: Float = 120.0f,
    var blurRadius: Float = 0.0f,
    var cornerRadius: Float = 36.0f,
    // Notification item horizontal margin inset
    var sideMargin: Float = 14.0f,
    // Home indicator pill
    var pillWidth: Float = 156.0f,
    var pillHeight: Float = 6.0f,
    var pillBottomMargin: Float = 9.0f
)

object NotificationSheetConfigHolder {
    val sheet = NotificationSheetConfig()

    fun updateFromPrefs(prefs: SharedPreferences) {
        sheet.enabled = prefs.getBoolean("notif_sheet_enabled", true)
        sheet.lockWallpaperEnabled = prefs.getBoolean("notif_sheet_lock_wallpaper_enabled", false)
        sheet.lockWallpaperHomescreenOnly = prefs.getBoolean("notif_sheet_lock_wallpaper_homescreen_only", false)
        sheet.depthWallpaperEnabled = prefs.getBoolean("notif_sheet_depth_wallpaper_enabled", false)
        sheet.depthCustomSubject = prefs.getBoolean("notif_sheet_depth_custom_subject", false)
        sheet.glassEffectsEnabled = prefs.getBoolean("notif_sheet_glass_effects_enabled", false)
        sheet.glassOnlyWithoutWallpaper = prefs.getBoolean("notif_sheet_glass_only_without_wallpaper", false)
        sheet.refractionHeight = prefs.getFloat("notif_card_refraction_height", 24.0f)
        sheet.bevelWidth = prefs.getFloat("notif_card_bevel_width", 28.0f)
        sheet.leftBevelWidth = prefs.getFloat("notif_card_left_bevel", 28.0f)
        sheet.rightBevelWidth = prefs.getFloat("notif_card_right_bevel", 28.0f)
        sheet.bottomBevelWidth = prefs.getFloat("notif_card_bottom_bevel", 28.0f)
        sheet.dispersionStrength = prefs.getFloat("notif_card_dispersion", 0.025f)
        sheet.specularStrength = prefs.getFloat("notif_card_specular", 0.90f)
        sheet.innerShadow = prefs.getFloat("notif_card_inner_shadow", 0.0f)
        sheet.saturationFactor = prefs.getFloat("notif_card_saturation", 120.0f)
        sheet.blurRadius = prefs.getFloat("notif_card_blur_radius", 0.0f)
        sheet.cornerRadius = prefs.getFloat("notif_card_corner_radius", 36.0f)
        sheet.sideMargin = prefs.getFloat("notif_side_margin", 14.0f)
        sheet.pillWidth = prefs.getFloat("notif_pill_width", 156.0f)
        sheet.pillHeight = prefs.getFloat("notif_pill_height", 6.0f)
        sheet.pillBottomMargin = prefs.getFloat("notif_pill_bottom_margin", 9.0f)
    }
}
