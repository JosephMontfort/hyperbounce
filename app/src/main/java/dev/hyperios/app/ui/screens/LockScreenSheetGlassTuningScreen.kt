package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
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
            sheet.setSideRefraction(ModuleDefaults.LockScreenSheet.SIDE_REFRACTION)
            sheet.setBottomRefraction(ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION)
            sheet.setDispersionStrength(ModuleDefaults.LockScreenSheet.DISPERSION_STRENGTH)
            sheet.setRimSoftness(ModuleDefaults.LockScreenSheet.RIM_SOFTNESS)
            sheet.setSaturationFactor(ModuleDefaults.LockScreenSheet.SATURATION_FACTOR)
            sheet.setBlurRadius(ModuleDefaults.LockScreenSheet.BLUR_RADIUS)
            sheet.setCornerRadius(ModuleDefaults.LockScreenSheet.CORNER_RADIUS)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
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
                    valueRange = ModuleDefaults.LockScreenSheet.SIDE_REFRACTION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.SIDE_REFRACTION,
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
                    valueRange = ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION,
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
                    valueRange = ModuleDefaults.LockScreenSheet.DISPERSION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.DISPERSION_STRENGTH,
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
                    valueRange = ModuleDefaults.LockScreenSheet.RIM_SOFTNESS_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.RIM_SOFTNESS,
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
                    valueRange = ModuleDefaults.LockScreenSheet.SATURATION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.SATURATION_FACTOR,
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
                    valueRange = ModuleDefaults.LockScreenSheet.BLUR_RADIUS_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.BLUR_RADIUS,
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
                    valueRange = ModuleDefaults.LockScreenSheet.CORNER_RADIUS_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.CORNER_RADIUS,
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
