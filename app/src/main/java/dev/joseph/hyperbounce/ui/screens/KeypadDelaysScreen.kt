package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale
import kotlin.math.hypot

private fun defaultBloomDelay(row: Int, col: Int, waveStagger: Float): Float {
    val relCol = (col - 1).toDouble()
    val relRow = (row - 1).toDouble()
    return (hypot(relCol, relRow).toFloat() * waveStagger)
}

@Composable
fun KeypadDelaysScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current
    val stagger = keypad.iosBloomWaveStagger

    ScreenScaffold(
        title = "Per-Key Delay Tuning",
        subtitle = "Radial shockwave timing offsets from central Key 5",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setDelayKey1(-1f)
            keypad.setDelayKey2(-1f)
            keypad.setDelayKey3(-1f)
            keypad.setDelayKey4(-1f)
            keypad.setDelayKey5(-1f)
            keypad.setDelayKey6(-1f)
            keypad.setDelayKey7(-1f)
            keypad.setDelayKey8(-1f)
            keypad.setDelayKey9(-1f)
            keypad.setDelayKey0(-1f)
            keypad.setDelayEmergency(-1f)
            keypad.setDelayCancel(-1f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Numeric Keys (Row 1)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Key 1 Delay",
                    subtitle = "Entrance delay for Key 1",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey1 >= 0f) keypad.delayKey1 else defaultBloomDelay(0, 0, stagger),
                    onValueChange = keypad::setDelayKey1,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 2 Delay",
                    subtitle = "Entrance delay for Key 2",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey2 >= 0f) keypad.delayKey2 else defaultBloomDelay(0, 1, stagger),
                    onValueChange = keypad::setDelayKey2,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 3 Delay",
                    subtitle = "Entrance delay for Key 3",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey3 >= 0f) keypad.delayKey3 else defaultBloomDelay(0, 2, stagger),
                    onValueChange = keypad::setDelayKey3,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Numeric Keys (Row 2)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Key 4 Delay",
                    subtitle = "Entrance delay for Key 4",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey4 >= 0f) keypad.delayKey4 else defaultBloomDelay(1, 0, stagger),
                    onValueChange = keypad::setDelayKey4,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 5 Delay (Center)",
                    subtitle = "Entrance delay for center Key 5 (Cluster Origin)",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey5 >= 0f) keypad.delayKey5 else defaultBloomDelay(1, 1, stagger),
                    onValueChange = keypad::setDelayKey5,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 6 Delay",
                    subtitle = "Entrance delay for Key 6",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey6 >= 0f) keypad.delayKey6 else defaultBloomDelay(1, 2, stagger),
                    onValueChange = keypad::setDelayKey6,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Numeric Keys (Row 3)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Key 7 Delay",
                    subtitle = "Entrance delay for Key 7",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey7 >= 0f) keypad.delayKey7 else defaultBloomDelay(2, 0, stagger),
                    onValueChange = keypad::setDelayKey7,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 8 Delay",
                    subtitle = "Entrance delay for Key 8",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey8 >= 0f) keypad.delayKey8 else defaultBloomDelay(2, 1, stagger),
                    onValueChange = keypad::setDelayKey8,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 9 Delay",
                    subtitle = "Entrance delay for Key 9",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey9 >= 0f) keypad.delayKey9 else defaultBloomDelay(2, 2, stagger),
                    onValueChange = keypad::setDelayKey9,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Bottom Row & Action Buttons", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Key 0 Delay",
                    subtitle = "Entrance delay for Key 0",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayKey0 >= 0f) keypad.delayKey0 else defaultBloomDelay(3, 1, stagger),
                    onValueChange = keypad::setDelayKey0,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Cancel / Delete Button Delay",
                    subtitle = "Entrance delay for Cancel / Delete action",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayCancel >= 0f) keypad.delayCancel else defaultBloomDelay(3, 2, stagger),
                    onValueChange = keypad::setDelayCancel,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Emergency Call Button Delay",
                    subtitle = "Entrance delay for Emergency Call button",
                    colors = colors,
                    backdrop = backdrop,
                    value = if (keypad.delayEmergency >= 0f) keypad.delayEmergency else defaultBloomDelay(3, 0, stagger),
                    onValueChange = keypad::setDelayEmergency,
                    valueRange = 0.0f..500.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }
    }
}
