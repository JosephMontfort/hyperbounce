package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun TuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    RootScaffold(
        title = "Tuning",
        subtitle = "What the hooks actually do",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = { onNavigate(Destination.About) }
    ) {
        SectionHeader("Features", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsNavRow(
                title = "Floating Navigation Bar",
                subtitle = "App-specific glass overrides",
                colors = colors,
                iconTint = colors.accentBlue,
                onClick = { onNavigate(Destination.AppsList) },
                icon = { dev.joseph.hyperbounce.ui.components.AppsIcon(color = Color.White, size = 16.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Toggles & Sliders",
                subtitle = engineSummary(repository),
                colors = colors,
                iconTint = colors.accentOrange,
                onClick = { onNavigate(Destination.Engine) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 16.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Lock Screen Sheet",
                subtitle = if (repository.lockScreenSheet.enabled) "4-Sided AGSL Refraction" else "Off",
                colors = colors,
                iconTint = colors.accentPink,
                onClick = { onNavigate(Destination.LockScreenSheet) },
                icon = { dev.joseph.hyperbounce.ui.components.LockIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Notification Center",
                subtitle = if (repository.notificationSheet.enabled) "iOS Glass Sheet & Static Flow" else "Off",
                colors = colors,
                iconTint = colors.accentPurple,
                onClick = { onNavigate(Destination.NotificationCenterSheet) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "PIN Pad Physics",
                subtitle = if (repository.lockScreenKeypad.enabled) "Fluid iOS Geometry & Jelly Squish" else "Off",
                colors = colors,
                iconTint = colors.accentBlue,
                onClick = { onNavigate(Destination.Keypad) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Screen Unlock",
                subtitle = if (repository.screenUnlock.enabled) "Velocity Kinetics & Dispersion" else "Off",
                colors = colors,
                iconTint = colors.accentPurple,
                onClick = { onNavigate(Destination.ScreenUnlock) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "iOS App Transitions",
                subtitle = if (repository.appTransition.enabled) "Weightless Physics & Row Flight Arcs" else "Off",
                colors = colors,
                iconTint = colors.accentGreen,
                onClick = { onNavigate(Destination.AppTransitions) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Control Center",
                subtitle = if (repository.controlCenter.modularEditEnabled || repository.controlCenter.animEnabled) "Modular Layout & Jelly Physics" else "Off",
                colors = colors,
                iconTint = colors.accentBlue,
                onClick = { onNavigate(Destination.ControlCenter) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "iOS Caller Screen",
                subtitle = if (repository.callerScreen.enabled) "Full-screen dual sliders & fluid morph" else "Off",
                colors = colors,
                iconTint = colors.accentGreen,
                onClick = { onNavigate(Destination.CallerScreen) },
                icon = { dev.joseph.hyperbounce.ui.components.PhoneIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Ringtones & Haptics",
                subtitle = "Custom synced ringtones, trimming & 5-pipeline synthesis",
                colors = colors,
                iconTint = colors.accentOrange,
                onClick = { onNavigate(Destination.HapticStudio) },
                icon = { dev.joseph.hyperbounce.ui.components.SlidersIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "Screen-Off Gestures",
                subtitle = if (repository.screenOffGestures.enabled) "O: Torch · C: Camera · 0% Battery Drain" else "Off",
                colors = colors,
                iconTint = colors.accentPink,
                onClick = { onNavigate(Destination.ScreenOffGestures) },
                icon = { dev.joseph.hyperbounce.ui.components.PowerIcon(color = Color.White, size = 15.dp) }
            )
        }
    }
}

private fun engineSummary(repository: SettingsRepository): String {
    val t = if (repository.toggleEngine == dev.joseph.hyperbounce.ui.state.RenderEngine.LiquidGlass) "Liquid Glass" else "Native Canvas"
    val sl = if (repository.sliderEngine == dev.joseph.hyperbounce.ui.state.RenderEngine.LiquidGlass) "Liquid Glass" else "Native Canvas"
    return "Toggles: $t · Sliders: $sl"
}
