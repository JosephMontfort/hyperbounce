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
        onReset = repository::resetToDefaults,
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
                title = "Lock Screen",
                subtitle = if (repository.keypadEnabled) "Glass Pinpad" else "Off",
                colors = colors,
                iconTint = colors.accentBlue,
                onClick = { onNavigate(Destination.Keypad) },
                icon = { dev.joseph.hyperbounce.ui.components.LockIcon(color = Color.White, size = 15.dp) }
            )
            RowDivider(colors.separator)
            SettingsNavRow(
                title = "System Diagnostics",
                subtitle = "Control Center & Lockscreen overrides",
                colors = colors,
                iconTint = colors.accentGreen,
                onClick = { onNavigate(Destination.Diagnostics) },
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
            )
        }
        SectionFooter(
            "Tap any section to configure its specific rendering engine and physical feel.",
            colors
        )
    }
}

private fun engineSummary(repository: SettingsRepository): String {
    val t = if (repository.toggleEngine == dev.joseph.hyperbounce.ui.state.RenderEngine.LiquidGlass) "Liquid Glass" else "Native Canvas"
    val sl = if (repository.sliderEngine == dev.joseph.hyperbounce.ui.state.RenderEngine.LiquidGlass) "Liquid Glass" else "Native Canvas"
    return "Toggles: $t · Sliders: $sl"
}
