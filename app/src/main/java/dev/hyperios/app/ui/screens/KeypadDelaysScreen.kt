package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

/**
 * KeypadDelaysScreen: Fine-tune individual start delays for keys 0-9 and action buttons.
 *
 * NOTE FOR FUTURE AGENTS / DEVELOPERS:
 * This screen and its backend preferences (`pin_delay_key1` .. `pin_delay_cancel`) are fully preserved.
 * The navigation link from KeypadScreen was removed to declutter the UI per user request, while
 * the calibrated delays (Key 1: 0ms, Key 2: 25ms, ..., Cancel/Emergency: 270ms) are enforced as defaults.
 *
 * If you ever need to re-expose this screen in the UI, simply re-add a SettingsNavRow to KeypadScreen.kt:
 * ```kotlin
 * SettingsNavRow(
 *     title = "Custom Key Delays",
 *     subtitle = "Fine-tune individual start delays for keys 0-9",
 *     colors = colors,
 *     iconTint = colors.accentOrange,
 *     onClick = { onNavigate(Destination.KeypadDelays) },
 *     icon = { SlidersIcon(color = Color.White, size = 15.dp) }
 * )
 * ```
 */
@Composable
fun KeypadDelaysScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Per-Key Delay Tuning",
        subtitle = "Custom sequential entry timing offsets",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setDelayKey1(0f)
            keypad.setDelayKey2(25f)
            keypad.setDelayKey3(50f)
            keypad.setDelayKey4(55f)
            keypad.setDelayKey5(80f)
            keypad.setDelayKey6(105f)
            keypad.setDelayKey7(110f)
            keypad.setDelayKey8(135f)
            keypad.setDelayKey9(160f)
            keypad.setDelayKey0(215f)
            keypad.setDelayEmergency(270f)
            keypad.setDelayCancel(270f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Numeric Keys (Row 1)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Key 1 Delay",
                    subtitle = "Entrance delay for Key 1",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey1,
                    onValueChange = keypad::setDelayKey1,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 2 Delay",
                    subtitle = "Entrance delay for Key 2",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey2,
                    onValueChange = keypad::setDelayKey2,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 25.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 3 Delay",
                    subtitle = "Entrance delay for Key 3",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey3,
                    onValueChange = keypad::setDelayKey3,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 50.0f,
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
                    value = keypad.delayKey4,
                    onValueChange = keypad::setDelayKey4,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 55.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 5 Delay",
                    subtitle = "Entrance delay for Key 5",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey5,
                    onValueChange = keypad::setDelayKey5,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 80.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 6 Delay",
                    subtitle = "Entrance delay for Key 6",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey6,
                    onValueChange = keypad::setDelayKey6,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 105.0f,
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
                    value = keypad.delayKey7,
                    onValueChange = keypad::setDelayKey7,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 110.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 8 Delay",
                    subtitle = "Entrance delay for Key 8",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey8,
                    onValueChange = keypad::setDelayKey8,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 135.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Key 9 Delay",
                    subtitle = "Entrance delay for Key 9",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayKey9,
                    onValueChange = keypad::setDelayKey9,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 160.0f,
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
                    value = keypad.delayKey0,
                    onValueChange = keypad::setDelayKey0,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 215.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Cancel / Delete Button Delay",
                    subtitle = "Entrance delay for Cancel / Delete action",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayCancel,
                    onValueChange = keypad::setDelayCancel,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 270.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Emergency Call Button Delay",
                    subtitle = "Entrance delay for Emergency Call button",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.delayEmergency,
                    onValueChange = keypad::setDelayEmergency,
                    valueRange = 0.0f..500.0f,
                    defaultValue = 270.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }
    }
}
