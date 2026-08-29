package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        SectionHeader("Apps", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            NavApp.entries.forEachIndexed { index, app ->
                SettingsNavRow(
                    title = app.displayName,
                    subtitle = navSummary(repository, app),
                    colors = colors,
                    iconTint = appTint(app),
                    onClick = { onNavigate(Destination.AppDetail(app)) },
                    icon = { AppMonogram(app) }
                )
                if (index != NavApp.entries.lastIndex) {
                    RowDivider(colors.separator)
                }
            }
        }
        SectionFooter(
            "Floating nav-bar glass for Instagram, WhatsApp, and YouTube — each app's " +
                "settings are stored and applied independently.",
            colors
        )

        SectionHeader("Engine", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsNavRow(
                title = "Toggles & Sliders",
                subtitle = engineSummary(repository),
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                onClick = { onNavigate(Destination.Engine) },
                icon = { SlidersIcon(color = Color.White, size = 15.dp) }
            )
        }
        SectionFooter(
            "HyperOS system toggles, sliders, and keypad — pick the rendering engine and " +
                "tune the glass feel for each.",
            colors
        )
    }
}

@Composable
private fun AppMonogram(app: NavApp) {
    Box(
        Modifier
            .size(29.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.001f)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            app.displayName.take(1),
            style = HyperBounceType.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold)
        )
    }
}

private fun appTint(app: NavApp): Color = when (app) {
    NavApp.Instagram -> Color(0xFFE1306C).copy(alpha = 0.85f)
    NavApp.WhatsApp -> Color(0xFF25D366).copy(alpha = 0.85f)
    NavApp.YouTube -> Color(0xFFFF0000).copy(alpha = 0.85f)
}

private fun navSummary(repository: SettingsRepository, app: NavApp): String {
    val s = repository.appSettings(app)
    return if (!s.enabled) "Off" else "Glass ${(s.glassIntensity * 100).toInt()}% · ${s.cornerRadius.toInt()}dp corners"
}

private fun engineSummary(repository: SettingsRepository): String {
    val toggle = repository.toggleEngine.label
    val slider = repository.sliderEngine.label
    return if (toggle == slider) "Both using $toggle" else "Toggle: $toggle · Slider: $slider"
}
