package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PhoneIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.utils.RootUtils
import kotlin.math.roundToInt

@Composable
fun DialerRecentsSettings(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val dialer = repository.dialerRecents
    val context = LocalContext.current

    ScreenScaffold(
        title = "Dialer Recents",
        subtitle = "iOS-style swipe to call and swipe to delete",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = dialer::reloadFromDefaults,
        restartAppTitle = "Restart Contacts & Dialer",
        restartAppSubtitle = "Restart com.android.contacts",
        onRestartApp = { RootUtils.restartApp(context, "com.android.contacts", "Contacts & Dialer") }
    ) {
        item {
            SectionHeader("MASTER SWITCH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Recents Swipe",
                    subtitle = "Swipe right to call, swipe left to reveal delete button",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = dialer.enabled,
                    onCheckedChange = dialer::setEnabled,
                    icon = { PhoneIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("SWIPE TO CALL (RIGHT)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Call Trigger Threshold",
                    subtitle = "Swipe distance required to automatically place call",
                    colors = colors,
                    backdrop = backdrop,
                    value = dialer.callThreshold,
                    onValueChange = dialer::setCallThreshold,
                    valueRange = 70f..140f,
                    defaultValue = 96f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = dialer.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Call Morph Duration",
                    subtitle = "Speed of full-screen green card expansion animation",
                    colors = colors,
                    backdrop = backdrop,
                    value = dialer.morphDuration,
                    onValueChange = dialer::setMorphDuration,
                    valueRange = 250f..1200f,
                    defaultValue = 550f,
                    valueLabel = { "${it.roundToInt()}ms" },
                    enabled = dialer.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Haptic Feedback",
                    subtitle = "Tactile tick when crossing call trigger threshold",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = dialer.hapticsEnabled,
                    onCheckedChange = dialer::setHapticsEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) },
                    enabled = dialer.enabled
                )
            }
        }

        item {
            SectionHeader("SWIPE TO DELETE (LEFT)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Delete Button Reveal Width",
                    subtitle = "Width of revealed red Delete action",
                    colors = colors,
                    backdrop = backdrop,
                    value = dialer.deleteWidth,
                    onValueChange = dialer::setDeleteWidth,
                    valueRange = 64f..110f,
                    defaultValue = 84f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = dialer.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Confirm Before Deleting",
                    subtitle = "Invoke stock confirmation dialog before deleting all call logs",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = dialer.confirmDelete,
                    onCheckedChange = dialer::setConfirmDelete,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) },
                    enabled = dialer.enabled
                )
            }
        }
    }
}
