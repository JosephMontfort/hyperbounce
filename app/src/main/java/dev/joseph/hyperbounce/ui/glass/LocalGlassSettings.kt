package dev.joseph.hyperbounce.ui.glass

/*
 * Provided once at the app root (see MainActivity.kt) from SettingsRepository.globalGlass.
 * Per the app's aesthetic direction, real glass/backdrop is reserved for toggles, sliders,
 * and the dropdown/popup menus (ExpandableGlassMenu, DropdownPicker) — everything else
 * (cards, the back button, previews) is a flat fill and doesn't read this. LiquidToggle/
 * LiquidSlider deliberately never read this either — see GlobalGlassSettings' kdoc in
 * SettingsModels.kt.
 */

import androidx.compose.runtime.compositionLocalOf
import dev.joseph.hyperbounce.ui.state.GlobalGlassSettings

val LocalGlassSettings = compositionLocalOf { GlobalGlassSettings() }
