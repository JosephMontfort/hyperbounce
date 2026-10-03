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
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.utils.RootUtils
import kotlin.math.roundToInt

@Composable
fun CallerScreenSettings(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val caller = repository.callerScreen
    val dialer = repository.dialerRecents
    val context = LocalContext.current

    ScreenScaffold(
        title = "iOS Caller Screen",
        subtitle = "Full-screen dual horizontal sliders & continuous morph",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            caller.reloadFromDefaults()
            dialer.reloadFromDefaults()
        },
        restartAppTitle = "Restart InCallUI",
        restartAppSubtitle = "Restart com.android.incallui",
        onRestartApp = { RootUtils.restartApp(context, "com.android.incallui", "InCallUI") }
    ) {
        item {
            SectionHeader("MASTER SWITCH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable iOS Caller Screen",
                    subtitle = "Dual sliders for full-screen incoming calls",
                    colors = colors,
                    backdrop = backdrop,
                    checked = caller.enabled,
                    onCheckedChange = caller::setEnabled
                )
            }
        }

        item {
            SectionHeader("DIMENSIONS & POSITION", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Button & Track Size",
                    subtitle = "Scales attend, decline, and end-call buttons uniformly",
                    colors = colors,
                    backdrop = backdrop,
                    value = caller.buttonSize,
                    onValueChange = caller::setButtonSize,
                    valueRange = 64f..86f,
                    defaultValue = 76f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = caller.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Vertical Position Offset",
                    subtitle = "Upward height offset from the bottom of the screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = caller.bottomOffset,
                    onValueChange = caller::setBottomOffset,
                    valueRange = 30f..120f,
                    defaultValue = 68f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = caller.enabled
                )
            }
        }

        item {
            SectionHeader("STOCK DIALER RECENTS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Swipe Gestures in Dialer",
                    subtitle = "Swipe right to call, swipe left to delete, call history",
                    colors = colors,
                    backdrop = backdrop,
                    checked = dialer.enabled,
                    onCheckedChange = dialer::setEnabled
                )
            }
        }
    }
}
