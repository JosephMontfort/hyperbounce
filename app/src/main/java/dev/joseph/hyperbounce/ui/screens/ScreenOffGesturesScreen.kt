package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.CameraIcon
import dev.joseph.hyperbounce.ui.components.FlashlightIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun ScreenOffGesturesScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val gestures = repository.screenOffGestures

    ScreenScaffold(
        title = "Screen-Off Gestures",
        subtitle = "Hardware Shape Actions · 0% Idle Battery Drain",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = gestures::reloadFromDefaults
    ) { _ ->
        // 1. MASTER SWITCH
        item {
            SectionHeader("MASTER SWITCH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Screen-Off Gestures",
                    subtitle = if (gestures.enabled) "Hardware touch gesture engine active" else "Gestures paused",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = gestures.enabled,
                    onCheckedChange = gestures::setEnabled,
                    icon = { PowerIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        // 2. PRIMARY GESTURES
        item {
            SectionHeader("PRIMARY GESTURES", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Draw 'O' for Flashlight",
                    subtitle = "Instantly toggle torch mode with zero delay",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = gestures.gestureOEnabled,
                    onCheckedChange = gestures::setGestureOEnabled,
                    icon = { FlashlightIcon(color = Color.White, size = 16.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Draw 'C' for Camera",
                    subtitle = "Wakes screen and launches secure camera instantly",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = gestures.gestureCEnabled,
                    onCheckedChange = gestures::setGestureCEnabled,
                    icon = { CameraIcon(color = Color.White, size = 16.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Draw 'V' for Flashlight",
                    subtitle = "Alternative shape toggle for flashlight",
                    colors = colors,
                    iconTint = colors.accentYellow,
                    backdrop = backdrop,
                    checked = gestures.gestureVEnabled,
                    onCheckedChange = gestures::setGestureVEnabled,
                    icon = { FlashlightIcon(color = Color.White, size = 16.dp) }
                )
            }
        }

        // 3. MEDIA & SYSTEM CONTROLS
        item {
            SectionHeader("MEDIA & VOLUME", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Draw 'M' for Play / Pause",
                    subtitle = "Toggles music playback in current active player",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = gestures.gestureMEnabled,
                    onCheckedChange = gestures::setGestureMEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Draw 'W' for Next Track",
                    subtitle = "Skips forward to the next media track",
                    colors = colors,
                    iconTint = colors.accentPink,
                    backdrop = backdrop,
                    checked = gestures.gestureWEnabled,
                    onCheckedChange = gestures::setGestureWEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Swipe Controls",
                    subtitle = "Left/Right: Prev/Next track · Up/Down: Volume",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = gestures.gestureSwipesEnabled,
                    onCheckedChange = gestures::setGestureSwipesEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        // 4. FEEDBACK
        item {
            SectionHeader("TACTILE FEEDBACK", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Haptic Vibration",
                    subtitle = "Crisp vibration feedback when gesture is recognized",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = gestures.hapticFeedback,
                    onCheckedChange = gestures::setHapticFeedback,
                    icon = { PowerIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        // 5. HARDWARE ENGINE ARCHITECTURE
        item {
            SectionHeader("HARDWARE ENGINE", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    BasicText(
                        text = "FocalTech FT5672 TDDI Hardware Gestures",
                        style = HyperBounceType.bodyMedium.copy(
                            color = colors.textPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    )
                    BasicText(
                        text = "\n• Recognition occurs on-chip in the touch controller's low-power scan DSP while the CPU is in deep sleep.\n• IRQ 304 wakes system_server only when a gesture matches, triggering the action with zero latency.\n• Idle CPU drain: 0.00% · Battery drain: Identical to Double-Tap-to-Wake (0.00% extra).",
                        style = HyperBounceType.bodySmall.copy(
                            color = colors.textSecondary,
                            lineHeight = androidx.compose.ui.unit.sp(18)
                        )
                    )
                }
            }
        }
    }
}
