package dev.joseph.hyperbounce.ui

import androidx.compose.runtime.Immutable

@Immutable
data class AppBackdropSettings(
    val enabled: Boolean = true,
    val blur: Float = 8f,
    val refractionHeight: Float = 18f,
    val refractionAmount: Float = 18f,
    val chromaticAberration: Boolean = false,
    val vibrancy: Float = 1f,
    val highlight: Float = 0.65f,
    val surfaceAlpha: Float = 0.40f,
)

@Immutable
data class AppSettings(
    val instagram: AppBackdropSettings = AppBackdropSettings(),
    val youtube: AppBackdropSettings = AppBackdropSettings(),
    val whatsapp: AppBackdropSettings = AppBackdropSettings(),
)

@Immutable
data class UiSettings(
    val liquidEffectsEnabled: Boolean = true,
    val animationSpeed: Float = 1f,
    val containerBulge: Float = 0.395f,
    val contentBulge: Float = 0.15f,
    val contentPopX: Float = 0.03f,
    val contentPopY: Float = 0.01f,
    val dragJellyTension: Float = 0.05f,
    val motionBlurPeak: Float = 25f,
    val arcYOffset: Float = 75f,
    val menuCornerRadius: Float = 30f,
    val menuBlurRadius: Float = 10f,
    val menuRefractionHeight: Float = 16f,
    val menuRefractionAmount: Float = 20f,
    val menuChromaticAberration: Boolean = false,
    val menuHorizontalOffset: Float = 0f,
    val menuVerticalOffset: Float = 30f,
    val menuHaptics: Boolean = true,
    val selectedMenuPreset: String = "Bouncy",
    val apps: AppSettings = AppSettings(),
)
