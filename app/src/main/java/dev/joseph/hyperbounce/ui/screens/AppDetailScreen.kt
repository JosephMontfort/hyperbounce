package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.NavBarPreview
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.state.ThemeMode
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.roundToInt

@Composable
fun AppDetailScreen(
    app: NavApp,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val holder = repository.appSettings(app)
    val s = holder.snapshot()
    val accentColor = accentColorFor(s.accent, colors)
    val context = androidx.compose.ui.platform.LocalContext.current

    val defaultCornerRadius = if (app == NavApp.WhatsApp) 36f else 32f
    val defaultBarHeight = if (app == NavApp.WhatsApp) 70f else 64f
    val defaultTabWidth = if (app == NavApp.WhatsApp) 92f else 64f

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
        item {
            NavBarPreview(
                backdrop = backdrop,
                accentColor = accentColor,
                glassIntensity = s.glassIntensity,
                barGlassEnabled = s.barGlassEnabled,
                pillGlassEnabled = s.pillGlassEnabled,
                containerOpacity = s.containerOpacity,
                pillOpacity = s.pillOpacity,
                cornerRadiusDp = s.cornerRadius,
                barHeightDp = s.barHeight,
                tabWidthDp = s.tabWidth,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

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

        item {
            SectionHeader("Glass & Backdrop Effects", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Bar Glass Effect",
                    subtitle = if (s.barGlassEnabled) "Liquid glass & live backdrop blur enabled" else "Flat translucent background (Zero GPU overhead)",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.barGlassEnabled,
                    onCheckedChange = holder::setBarGlassEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Tab Pill Glass Effect",
                    subtitle = if (s.pillGlassEnabled) "Refraction & lens shader on active tab indicator" else "Clean native translucent pill indicator",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.pillGlassEnabled,
                    onCheckedChange = holder::setPillGlassEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Icon Reflection Layer",
                    subtitle = if (s.reflectionLayerEnabled) "Refracts tab icons through liquid pill glass" else "Skips extra icon reflection render pass",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.reflectionLayerEnabled,
                    onCheckedChange = holder::setReflectionLayerEnabled,
                    icon = { AppGridIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Glass Intensity",
                    subtitle = "Overall refractive clarity and blur multiplier",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.glassIntensity,
                    onValueChange = holder::setGlassIntensity,
                    valueRange = 0.0f..2.0f,
                    defaultValue = 1.0f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled && (s.barGlassEnabled || s.pillGlassEnabled)
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bar Blur Radius",
                    subtitle = "Background blur radius for floating bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.barBlurDp,
                    onValueChange = holder::setBarBlurDp,
                    valueRange = 0f..24f,
                    defaultValue = 8f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled && s.barGlassEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bar Lens Refraction",
                    subtitle = "AGSL edge refraction height for bar container",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.barLensAmount,
                    onValueChange = holder::setBarLensAmount,
                    valueRange = 0f..32f,
                    defaultValue = 18f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled && s.barGlassEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Pill Lens Refraction",
                    subtitle = "AGSL edge refraction height on active tab pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillLensAmount,
                    onValueChange = holder::setPillLensAmount,
                    valueRange = 0f..24f,
                    defaultValue = 11f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled && s.pillGlassEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Pill Highlight",
                    subtitle = "Specular highlight strength on active tab pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillHighlight,
                    onValueChange = holder::setPillHighlight,
                    valueRange = 0f..1f,
                    defaultValue = 0.8f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled && s.pillGlassEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Pill Shadow Depth",
                    subtitle = "Drop and inner shadow depth on active tab pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillShadow,
                    onValueChange = holder::setPillShadow,
                    valueRange = 0f..1f,
                    defaultValue = 0.6f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled && s.pillGlassEnabled
                )
            }
        }

        item {
            SectionHeader("Opacity & Tint", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Container Opacity",
                    subtitle = "Translucent background tint opacity for bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.containerOpacity,
                    onValueChange = holder::setContainerOpacity,
                    valueRange = 0f..1f,
                    defaultValue = 0.4f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Tab Pill Opacity",
                    subtitle = "Resting background tint opacity for active tab pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillOpacity,
                    onValueChange = holder::setPillOpacity,
                    valueRange = 0.02f..0.50f,
                    defaultValue = 0.10f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled
                )
            }
        }

        item {
            SectionHeader("Shape & Size", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Corner Radius",
                    subtitle = "Corner rounding radius for floating bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.cornerRadius,
                    onValueChange = holder::setCornerRadius,
                    valueRange = 12f..40f,
                    defaultValue = defaultCornerRadius,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bar Height",
                    subtitle = "Vertical height of floating pill container",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.barHeight,
                    onValueChange = holder::setBarHeight,
                    valueRange = 48f..88f,
                    defaultValue = defaultBarHeight,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Tab Width",
                    subtitle = "Width allocated per tab icon",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.tabWidth,
                    onValueChange = holder::setTabWidth,
                    valueRange = 48f..88f,
                    defaultValue = defaultTabWidth,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
            }
        }

        item {
            SectionHeader("Appearance", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText("Theme", style = HyperBounceType.body.copy(color = colors.label))
                    DropdownPicker(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = ThemeMode.entries.indexOf(s.themeMode),
                        onSelect = { holder.setThemeMode(ThemeMode.entries[it]) },
                        backdrop = backdrop
                    )
                }
                RowDivider(colors.separator, insetStart = 16.dp)

                BasicText(
                    "Accent Color",
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
                    style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentPreset.entries.forEach { preset ->
                        AccentSwatch(
                            preset = preset,
                            colors = colors,
                            selected = preset == s.accent,
                            onClick = { holder.setAccent(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    colors: HyperBounceColors,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = accentColorFor(preset, colors)
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            CheckmarkIcon(color = Color.White, size = 14.dp, strokeWidth = 2.dp)
        }
    }
}

private fun accentColorFor(preset: AccentPreset, colors: HyperBounceColors): Color =
    preset.argb?.let { Color(it) } ?: colors.accentBlue
