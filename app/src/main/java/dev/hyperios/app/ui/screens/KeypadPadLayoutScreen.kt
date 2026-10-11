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
fun KeypadPadLayoutScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad Layout",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setPinpadLift(ModuleDefaults.LockScreenKeypad.PAD_LIFT)
            keypad.setRow0Offset(ModuleDefaults.LockScreenKeypad.ROW0_OFFSET)
            keypad.setRowSpacing(ModuleDefaults.LockScreenKeypad.ROW_SPACING)
            keypad.setColumnSpacing(ModuleDefaults.LockScreenKeypad.COL_SPACING)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
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
                    valueRange = ModuleDefaults.LockScreenKeypad.PAD_LIFT_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.PAD_LIFT,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.ROW0_OFFSET_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.ROW0_OFFSET,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.ROW_SPACING_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.ROW_SPACING,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.COL_SPACING_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.COL_SPACING,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Adjust the vertical position and spacing grid of the PIN pad lockscreen layout.",
                colors
            )
        }
    }
}
