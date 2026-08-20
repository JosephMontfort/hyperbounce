package dev.joseph.hyperbounce.ui.glass

/*
 * Provided once at the app root (see MainActivity.kt) from SettingsRepository.globalGlass,
 * and read by every structural glass surface (CircularGlassBackButton, GlassCard,
 * SegmentedGlassPicker, ExpandableGlassMenu, NavBarPreview) so the master switch and
 * blur/refraction/chromatic-aberration sliders on the Home screen affect all of them
 * without threading one more parameter through every screen. LiquidToggle/LiquidSlider
 * deliberately never read this — see GlobalGlassSettings' kdoc in SettingsModels.kt.
 */

import androidx.compose.runtime.compositionLocalOf
import dev.joseph.hyperbounce.ui.state.GlobalGlassSettings

val LocalGlassSettings = compositionLocalOf { GlobalGlassSettings() }
