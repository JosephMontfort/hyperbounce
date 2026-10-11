package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
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
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setCircleDiameter(ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER)
            keypad.setPressLiquidScale(ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE)
            keypad.setDigitTextSize(ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE)
            keypad.setShowKlondike(ModuleDefaults.LockScreenKeypad.SHOW_KLONDIKE)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
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
                    valueRange = ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE,
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
