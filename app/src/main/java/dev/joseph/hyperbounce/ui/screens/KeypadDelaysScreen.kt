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
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun KeypadDelaysScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad

    ScreenScaffold(
        title = "Per-Key Delay Tuning",
        subtitle = "Custom sequential entry timing offsets",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        SectionHeader("Numeric Keys (Row 1)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Key 1 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey1 >= 0f) keypad.delayKey1 else (0 * keypad.rowDelayStep + 0 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey1,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 2 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey2 >= 0f) keypad.delayKey2 else (0 * keypad.rowDelayStep + 1 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey2,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 3 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey3 >= 0f) keypad.delayKey3 else (0 * keypad.rowDelayStep + 2 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey3,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
        }

        SectionHeader("Numeric Keys (Row 2)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Key 4 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey4 >= 0f) keypad.delayKey4 else (1 * keypad.rowDelayStep + 0 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey4,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 5 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey5 >= 0f) keypad.delayKey5 else (1 * keypad.rowDelayStep + 1 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey5,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 6 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey6 >= 0f) keypad.delayKey6 else (1 * keypad.rowDelayStep + 2 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey6,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
        }

        SectionHeader("Numeric Keys (Row 3)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Key 7 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey7 >= 0f) keypad.delayKey7 else (2 * keypad.rowDelayStep + 0 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey7,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 8 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey8 >= 0f) keypad.delayKey8 else (2 * keypad.rowDelayStep + 1 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey8,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Key 9 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey9 >= 0f) keypad.delayKey9 else (2 * keypad.rowDelayStep + 2 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey9,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
        }

        SectionHeader("Bottom Row & Action Buttons", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Key 0 Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayKey0 >= 0f) keypad.delayKey0 else (3 * keypad.rowDelayStep + 1 * keypad.colDelayStep),
                onValueChange = keypad::setDelayKey0,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Cancel / Delete Button Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayCancel >= 0f) keypad.delayCancel else (3 * keypad.rowDelayStep + 2 * keypad.colDelayStep),
                onValueChange = keypad::setDelayCancel,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Emergency Call Button Delay",
                colors = colors,
                backdrop = backdrop,
                value = if (keypad.delayEmergency >= 0f) keypad.delayEmergency else (keypad.rowDelayStep * 4),
                onValueChange = keypad::setDelayEmergency,
                valueRange = 0.0f..500.0f,
                valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                enabled = keypad.enabled
            )
        }
        SectionFooter(
            "Individual key delay overrides supersede row and column delay steps.",
            colors
        )
    }
}
