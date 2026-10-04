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
            keypad.setPinpadLift(0.0f)
            keypad.setRow0Offset(-115.0f)
            keypad.setRowSpacing(30.0f)
            keypad.setColumnSpacing(0.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
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
            SectionFooter(
                "Adjust the vertical position and spacing grid of the PIN pad lockscreen layout.",
                colors
            )
        }
    }
}
