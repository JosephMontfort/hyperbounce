package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun AppDetailScreen(
    app: NavApp,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val holder = repository.appSettings(app)
    val s = holder.snapshot()
    val accentColor = accentColorFor(s.accent, s.customAccentColor, colors)
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = app.displayName,
        subtitle = app.packageName,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = holder::reloadFromDefaults,
        restartAppTitle = "Restart ${app.displayName}",
        restartAppSubtitle = "Restart ${app.packageName}",
        onRestartApp = {
            dev.joseph.hyperbounce.ui.utils.RootUtils.restartApp(context, app.packageName, app.displayName)
        }
    ) { _ ->
        // Master Toggle
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsToggleRow(
                    title = "Floating Nav Bar",
                    subtitle = if (s.enabled) "Liquid glass replaces the stock bar" else "Stock nav bar restored",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.enabled,
                    onCheckedChange = holder::setEnabled,
                    icon = { PowerIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
            }
        }

        // Subpage Categories
        item {
            SectionHeader("Configuration", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "Appearance",
                    subtitle = "Glass engine, opacity, theme & accent",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    onClick = { onNavigate(Destination.AppAppearance(app)) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Layout",
                    subtitle = "Corner radius, bar height, tab width & margin",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    onClick = { onNavigate(Destination.AppLayout(app)) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
            }
        }

        // App-specific feed & behavior settings
        when (app) {
            NavApp.Instagram -> {
                item {
                    SectionHeader("Feed Behavior", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        SettingsToggleRow(
                            title = "Auto-Shrink on Scroll",
                            subtitle = "Shrinks and docks down when scrolling feeds",
                            colors = colors,
                            iconTint = accentColor.copy(alpha = 0.85f),
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
                            icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) },
                            enabled = s.enabled
                        )
                        if (s.autoShrink) {
                            RowDivider(colors.separator, insetStart = 16.dp)
                            SettingsSliderRow(
                                title = "Shrunk Scale",
                                subtitle = "Percentage size when auto-shrunken on scroll",
                                colors = colors,
                                backdrop = backdrop,
                                value = s.autoShrinkScale,
                                onValueChange = holder::setAutoShrinkScale,
                                valueRange = 0.50f..0.90f,
                                defaultValue = 0.70f,
                                valueLabel = { "${(it * 100).roundToInt()}%" },
                                enabled = s.enabled && s.autoShrink
                            )
                        }
                    }
                }

                item {
                    SectionHeader("Instagram Optimizations", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SettingsToggleRow(
                            title = "Reels Fullscreen Zoom (Not Recommended)",
                            subtitle = "Expands Reels container to match parent. May crop 16:9 videos.",
                            colors = colors,
                            iconTint = Color(0xFFFF9500),
                            backdrop = backdrop,
                            checked = s.reelsFullscreenZoom,
                            onCheckedChange = holder::setReelsFullscreenZoom,
                            enabled = s.enabled,
                            icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                        )
                    }
                }
            }

            NavApp.YouTube -> {
                item {
                    SectionHeader("Feed Behavior", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        SettingsToggleRow(
                            title = "Auto-Shrink on Scroll",
                            subtitle = "Shrinks and docks down when scrolling feeds",
                            colors = colors,
                            iconTint = accentColor.copy(alpha = 0.85f),
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
                            icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) },
                            enabled = s.enabled
                        )
                        if (s.autoShrink) {
                            RowDivider(colors.separator, insetStart = 16.dp)
                            SettingsSliderRow(
                                title = "Shrunk Scale",
                                subtitle = "Percentage size when auto-shrunken on scroll",
                                colors = colors,
                                backdrop = backdrop,
                                value = s.autoShrinkScale,
                                onValueChange = holder::setAutoShrinkScale,
                                valueRange = 0.50f..0.90f,
                                defaultValue = 0.70f,
                                valueLabel = { "${(it * 100).roundToInt()}%" },
                                enabled = s.enabled && s.autoShrink
                            )
                        }
                    }
                }
            }

            NavApp.WhatsApp -> {
                // WhatsApp has no scroll shrink or extra features on main page
            }
        }
    }
}

@Composable
internal fun AccentSwatch(
    preset: AccentPreset,
    customColor: Long,
    colors: HyperBounceColors,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = accentColorFor(preset, customColor, colors)
    val isCustom = preset == AccentPreset.Custom
    val rainbowBrush = Brush.sweepGradient(
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    )

    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(
                if (isCustom) {
                    Modifier.background(rainbowBrush)
                } else {
                    Modifier.background(color)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isCustom) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(customColor))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    CheckmarkIcon(color = Color.White, size = 12.dp, strokeWidth = 2.dp)
                }
            }
        } else if (selected) {
            CheckmarkIcon(color = Color.White, size = 14.dp, strokeWidth = 2.dp)
        }
    }
}

internal fun accentColorFor(preset: AccentPreset, customColor: Long, colors: HyperBounceColors): Color =
    when (preset) {
        AccentPreset.Custom -> Color(customColor)
        AccentPreset.Auto -> colors.accentBlue
        else -> preset.argb?.let { Color(it) } ?: colors.accentBlue
    }
