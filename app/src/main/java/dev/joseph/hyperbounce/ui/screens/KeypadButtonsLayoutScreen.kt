package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
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
fun KeypadButtonsLayoutScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Buttons Layout",
        subtitle = "Key diameter, liquid press scale & typography",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setCircleDiameter(82.0f)
            keypad.setPressLiquidScale(1.15f)
            keypad.setDigitTextSize(36.0f)
            keypad.setShowKlondike(false)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
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
                    backdrop = backdrop,
                    checked = keypad.showKlondike,
                    onCheckedChange = keypad::setShowKlondike,
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Customize individual button dimensions, touch feedback scaling, and numeric/subtext typography.",
                colors
            )
        }
    }
}
