package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetGlassTuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Liquid Glass Tuning",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setSideRefraction(32.0f)
            sheet.setBottomRefraction(18.0f)
            sheet.setDispersionStrength(0.09f)
            sheet.setRimSoftness(2.0f)
            sheet.setSaturationFactor(125.0f)
            sheet.setBlurRadius(0.0f)
            sheet.setCornerRadius(38.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Optics & Refraction", colors)
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
    }
}
