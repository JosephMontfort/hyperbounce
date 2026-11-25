package dev.hyperios.app.ui.screens

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
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.CheckmarkIcon
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.PowerIcon
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsNavRow
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.AccentPreset
import dev.hyperios.app.ui.state.NavApp
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
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
        subtitle = null,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = holder::reloadFromDefaults,
        restartAppTitle = "Restart ${app.displayName}",
        restartAppSubtitle = "Restart ${app.packageName}",
        onRestartApp = {
            dev.hyperios.app.ui.utils.RootUtils.restartApp(context, app.packageName, app.displayName)
        }
    ) { _ ->
        // Master Toggle
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsToggleRow(
                    title = "Floating Bottom Bar",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = s.enabled,
                    onCheckedChange = holder::setEnabled
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
                    enabled = s.enabled,
                    onClick = { onNavigate(Destination.AppAppearance(app)) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Layout",
                    subtitle = "Corner radius, bar height, tab width & margin",
                    colors = colors,
                    enabled = s.enabled,
                    onClick = { onNavigate(Destination.AppLayout(app)) }
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
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
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
                                valueRange = ModuleDefaults.NavApps.AUTO_SHRINK_SCALE_RANGE,
                                defaultValue = ModuleDefaults.NavApps.Instagram.AUTO_SHRINK_SCALE,
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
                            backdrop = backdrop,
                            checked = s.reelsFullscreenZoom,
                            onCheckedChange = holder::setReelsFullscreenZoom,
                            enabled = s.enabled
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
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
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
                                valueRange = ModuleDefaults.NavApps.AUTO_SHRINK_SCALE_RANGE,
                                defaultValue = ModuleDefaults.NavApps.YouTube.AUTO_SHRINK_SCALE,
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

            NavApp.Facebook -> {
                item {
                    SectionHeader("Feed Behavior", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        SettingsToggleRow(
                            title = "Auto-Shrink on Scroll",
                            subtitle = "Shrinks and docks down when scrolling feeds",
                            colors = colors,
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
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
                                valueRange = ModuleDefaults.NavApps.AUTO_SHRINK_SCALE_RANGE,
                                defaultValue = ModuleDefaults.NavApps.Facebook.AUTO_SHRINK_SCALE,
                                valueLabel = { "${(it * 100).roundToInt()}%" },
                                enabled = s.enabled && s.autoShrink
                            )
                        }
                    }
                }
            }

            NavApp.TikTok -> {
                item {
                    SectionHeader("Feed Behavior", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        SettingsToggleRow(
                            title = "Auto-Shrink on Scroll",
                            subtitle = "Shrinks and docks down when scrolling feeds",
                            colors = colors,
                            backdrop = backdrop,
                            checked = s.autoShrink,
                            onCheckedChange = holder::setAutoShrink,
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
                                valueRange = ModuleDefaults.NavApps.AUTO_SHRINK_SCALE_RANGE,
                                defaultValue = ModuleDefaults.NavApps.TikTok.AUTO_SHRINK_SCALE,
                                valueLabel = { "${(it * 100).roundToInt()}%" },
                                enabled = s.enabled && s.autoShrink
                            )
                        }
                    }
                }
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
    val isAutoOrWhite = preset == AccentPreset.Auto || color == Color.White
    val checkmarkColor = if (isAutoOrWhite) Color.Black else Color.White
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
            .then(
                if (isAutoOrWhite) {
                    Modifier.border(0.8.dp, colors.separator.copy(alpha = 0.45f), CircleShape)
                } else Modifier
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
            CheckmarkIcon(color = checkmarkColor, size = 14.dp, strokeWidth = 2.dp)
        }
    }
}

internal fun accentColorFor(preset: AccentPreset, customColor: Long, colors: HyperBounceColors): Color =
    when (preset) {
        AccentPreset.Custom -> Color(customColor)
        AccentPreset.Auto -> Color.White
        else -> preset.argb?.let { Color(it) } ?: Color.White
    }
