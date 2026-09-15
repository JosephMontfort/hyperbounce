package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.LockIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Lock Screen Sheet",
        subtitle = "Liquid Glass 2.0 Lens Pipeline",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = sheet::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Lock Screen Liquid Glass Sheet",
                    subtitle = if (sheet.enabled) "Liquid Glass 2.0 lens refraction & dispersion active" else "Disabled (Stock)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = sheet.enabled,
                    onCheckedChange = sheet::setEnabled,
                    icon = { LockIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("Optics & Refraction (Liquid Glass 2.0)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Side Edges Refraction",
                    subtitle = "Inward lens magnification and curvature along left and right borders",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.sideRefraction,
                    onValueChange = sheet::setSideRefraction,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 32.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Refraction",
                    subtitle = "Upward lens refraction along bottom rim (calibrated against stretching)",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomRefraction,
                    onValueChange = sheet::setBottomRefraction,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 18.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Chromatic Dispersion",
                    subtitle = "Physical R/G/B spectral separation along surface normals",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.dispersionStrength,
                    onValueChange = sheet::setDispersionStrength,
                    valueRange = 0.0f..0.30f,
                    defaultValue = 0.09f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Caustic Rim Softness",
                    subtitle = "Normal-directed 3-tap sample smoothing radius along glass bevels",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.rimSoftness,
                    onValueChange = sheet::setRimSoftness,
                    valueRange = 0.0f..8.0f,
                    defaultValue = 2.0f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Vibrancy Saturation Boost",
                    subtitle = "Color vibrancy multiplication inside glass bevels",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.saturationFactor,
                    onValueChange = sheet::setSaturationFactor,
                    valueRange = 50.0f..250.0f,
                    defaultValue = 125.0f,
                    valueLabel = { String.format(Locale.US, "%.0f %%", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Frosted Glass Blur",
                    subtitle = "Optional blur layer (0 = crystal clear true glass)",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.blurRadius,
                    onValueChange = sheet::setBlurRadius,
                    valueRange = 0.0f..35.0f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Corners Radius",
                    subtitle = "Smooth curvature for bottom-left and bottom-right corners",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.cornerRadius,
                    onValueChange = sheet::setCornerRadius,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 38.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Refracts underlying wallpapers with crystal-clear 100% transparent interior.",
                colors
            )
        }

        item {
            SectionHeader("Glass Lighting & Specular Highlights", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Specular Highlight Intensity",
                    subtitle = "Master surface lighting rim brightness",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.specularStrength,
                    onValueChange = sheet::setSpecularStrength,
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.95f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Perimeter Hairline Brightness",
                    subtitle = "Crisp Apple-style white edge boundary reflection along all 3 rims",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.hairlineStrength,
                    onValueChange = sheet::setHairlineStrength,
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.75f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Glow Band Intensity",
                    subtitle = "Soft refractive inner light diffusion inside bevel slopes",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.innerGlowStrength,
                    onValueChange = sheet::setInnerGlowStrength,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.20f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Shadow Depth",
                    subtitle = "Bevel inner groove shadow depth factor",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.innerShadow,
                    onValueChange = sheet::setInnerShadow,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.45f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Simulates directional specular gleam and omnidirectional rim reflection across all glass edges.",
                colors
            )
        }

        item {
            SectionHeader("Per-Edge Glass Thickness", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Left Edge Glass Bevel",
                    subtitle = "Thickness of glass refraction band on left border",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.leftBevelWidth,
                    onValueChange = sheet::setLeftBevelWidth,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 38.0f,
                    valueLabel = { if (it <= 0.5f) "Off" else String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Right Edge Glass Bevel",
                    subtitle = "Thickness of glass refraction band on right border",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.rightBevelWidth,
                    onValueChange = sheet::setRightBevelWidth,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 38.0f,
                    valueLabel = { if (it <= 0.5f) "Off" else String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Glass Bevel",
                    subtitle = "Thickness of glass refraction band on bottom border",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomBevelWidth,
                    onValueChange = sheet::setBottomBevelWidth,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 38.0f,
                    valueLabel = { if (it <= 0.5f) "Off" else String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Each edge's glass thickness can be adjusted independently or turned completely off (0 dp). Center of the screen remains 100% transparent wallpaper.",
                colors
            )
        }

        item {
            SectionHeader("Home Indicator Pill", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Pill Width",
                    subtitle = "Horizontal size of the bottom home indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillWidth,
                    onValueChange = sheet::setPillWidth,
                    valueRange = 40.0f..280.0f,
                    defaultValue = 156.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Pill Height",
                    subtitle = "Vertical thickness of the indicator bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillHeight,
                    onValueChange = sheet::setPillHeight,
                    valueRange = 2.0f..16.0f,
                    defaultValue = 6.0f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance from screen bottom to indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillBottomMargin,
                    onValueChange = sheet::setPillBottomMargin,
                    valueRange = 0.0f..35.0f,
                    defaultValue = 9.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
        }
    }
}
