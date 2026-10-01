package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun KeypadScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad Liquid Buttons",
        subtitle = "Liquid Glass 2.0 lens buttons & sequential entry",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = keypad::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass PIN Pad",
                    subtitle = if (keypad.enabled) "Liquid Glass 2.0 lens buttons & layout physics" else "Disabled (Stock)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = keypad.enabled,
                    onCheckedChange = repository::setKeypadEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "PIN Pad Liquid Dynamics & Physics",
                    subtitle = "Inertial mass transfer, pull elasticity & release squish",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    onClick = { onNavigate(Destination.KeypadPhysics) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

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
        }

        item {
            SectionHeader("Button Geometry & Touch Interaction", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Button Circle Diameter",
                    subtitle = "Outer diameter of numeric key circles",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.circleDiameter,
                    onValueChange = keypad::setCircleDiameter,
                    valueRange = 60.0f..96.0f,
                    defaultValue = 82.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Liquid Press Scale",
                    subtitle = "Target scale ratio during touch down",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.pressLiquidScale,
                    onValueChange = keypad::setPressLiquidScale,
                    valueRange = 0.80f..1.50f,
                    defaultValue = 1.15f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Digit Font Size",
                    subtitle = "Typeface size for numbers 0-9",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.digitTextSize,
                    onValueChange = keypad::setDigitTextSize,
                    valueRange = 18.0f..48.0f,
                    defaultValue = 36.0f,
                    valueLabel = { String.format(Locale.US, "%.0f sp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Show Klondike Letters",
                    subtitle = "Display ABC, DEF subtitle letters below digits",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = keypad.showKlondike,
                    onCheckedChange = keypad::setShowKlondike,
                    icon = { SlidersIcon(color = Color.White, size = 14.dp) }
                )
            }
        }

        item {
            SectionHeader("PIN Pad Layout & Grid Spacing", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "PIN Pad Vertical Lift",
                    subtitle = "Lifts entire keypad upwards to position bottom row (0)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.pinpadLift,
                    onValueChange = keypad::setPinpadLift,
                    valueRange = 0.0f..200.0f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "PIN Dots Y-Offset",
                    subtitle = "Vertical position of password dots indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.row0Offset,
                    onValueChange = keypad::setRow0Offset,
                    valueRange = -180.0f..0.0f,
                    defaultValue = -115.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Row Spacing",
                    subtitle = "Uniform vertical distance between number rows",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.rowSpacing,
                    onValueChange = keypad::setRowSpacing,
                    valueRange = 0.0f..60.0f,
                    defaultValue = 30.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Column Spacing",
                    subtitle = "Uniform horizontal distance between number columns",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.columnSpacing,
                    onValueChange = keypad::setColumnSpacing,
                    valueRange = -30.0f..60.0f,
                    defaultValue = 0.0f,
                    valueLabel = { if (it > 0f) String.format(Locale.US, "+%.0f dp", it) else String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
            }
        }
    }
}
