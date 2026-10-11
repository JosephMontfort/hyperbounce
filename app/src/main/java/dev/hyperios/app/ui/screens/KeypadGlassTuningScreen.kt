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
fun KeypadGlassTuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Liquid Glass Tuning",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setButtonRefraction(ModuleDefaults.LockScreenKeypad.BTN_REFRACTION)
            keypad.setButtonBevel(ModuleDefaults.LockScreenKeypad.BTN_BEVEL)
            keypad.setButtonDispersion(ModuleDefaults.LockScreenKeypad.BTN_DISPERSION)
            keypad.setButtonSpecular(ModuleDefaults.LockScreenKeypad.BTN_SPECULAR)
            keypad.setButtonInnerShadow(ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW)
            keypad.setButtonSaturation(ModuleDefaults.LockScreenKeypad.BTN_SATURATION)
            keypad.setButtonBlur(ModuleDefaults.LockScreenKeypad.BTN_BLUR)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Liquid Glass 2.0 Button Optics", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Lens Inward Refraction",
                    subtitle = "Real-time background refraction displacement",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonRefraction,
                    onValueChange = keypad::setButtonRefraction,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_REFRACTION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_REFRACTION,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Glass Bevel Thickness",
                    subtitle = "Width of the circular glass bevel curve",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonBevel,
                    onValueChange = keypad::setButtonBevel,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_BEVEL_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_BEVEL,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Chromatic Dispersion",
                    subtitle = "Physical R/G/B spectral separation on button bevels",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonDispersion,
                    onValueChange = keypad::setButtonDispersion,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_DISPERSION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_DISPERSION,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Specular Highlight Rim",
                    subtitle = "Directional specular reflection intensity",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonSpecular,
                    onValueChange = keypad::setButtonSpecular,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_SPECULAR_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_SPECULAR,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Shadow Depth",
                    subtitle = "Backside shadow depth on button rim",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonInnerShadow,
                    onValueChange = keypad::setButtonInnerShadow,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Vibrancy Saturation Boost",
                    subtitle = "Non-linear color boost inside glass lens",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonSaturation,
                    onValueChange = keypad::setButtonSaturation,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_SATURATION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_SATURATION,
                    valueLabel = { String.format(Locale.US, "%.0f %%", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Frosted Glass Blur",
                    subtitle = "Optional blur layer (0 = crystal clear lens)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.buttonBlur,
                    onValueChange = keypad::setButtonBlur,
                    valueRange = ModuleDefaults.LockScreenKeypad.BTN_BLUR_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BTN_BLUR,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Liquid Glass 2.0 lenses refract and reflect background content dynamically with physically-based dispersion and highlights.",
                colors
            )
        }
    }
}
