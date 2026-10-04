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
            keypad.setButtonRefraction(48.0f)
            keypad.setButtonBevel(24.0f)
            keypad.setButtonDispersion(0.09f)
            keypad.setButtonSpecular(0.95f)
            keypad.setButtonInnerShadow(0.40f)
            keypad.setButtonSaturation(125.0f)
            keypad.setButtonBlur(0.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
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
                    valueRange = 0.0f..120.0f,
                    defaultValue = 48.0f,
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
                    valueRange = 4.0f..50.0f,
                    defaultValue = 24.0f,
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
                    valueRange = 0.0f..0.30f,
                    defaultValue = 0.09f,
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
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.95f,
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
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.40f,
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
                    valueRange = 50.0f..250.0f,
                    defaultValue = 125.0f,
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
                    valueRange = 0.0f..30.0f,
                    defaultValue = 0.0f,
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
