package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.ColorPickerDialog
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
    val accentColor = accentColorFor(s.accent, s.customAccentColor, colors)
    val context = androidx.compose.ui.platform.LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    val defaultCornerRadius = if (app == NavApp.WhatsApp) 36f else 32f
    val defaultBarHeight = if (app == NavApp.WhatsApp) 70f else 64f
    val defaultTabWidth = if (app == NavApp.WhatsApp) 92f else 64f
    val defaultBottomMargin = if (app == NavApp.YouTube) 28f else 40f

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = s.customAccentColor,
            colors = colors,
            backdrop = backdrop,
            onDismiss = { showColorPicker = false },
            onColorSelected = { newColor ->
                holder.setCustomAccentColor(newColor)
                holder.setAccent(AccentPreset.Custom)
            }
        )
    }

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
                containerOpacity = s.containerOpacity,
                cornerRadiusDp = s.cornerRadius,
                barHeightDp = s.barHeight,
                tabWidthDp = s.tabWidth,
                pillGlassIntensity = s.pillGlassIntensity,
                pillContainerOpacity = s.pillContainerOpacity,
                pillChromaticAberration = s.pillChromaticAberration,
                pillShadow = s.pillShadow,
                liquidGlass = s.liquidGlass,
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
            SectionHeader("Liquid Glass Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass Engine",
                    subtitle = if (s.liquidGlass) "Dynamic liquid refraction, lens and blur" else "Translucent iOS-style bar (zero backdrop lag)",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.liquidGlass,
                    onCheckedChange = holder::setLiquidGlass,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bar Glass Intensity",
                    subtitle = "Refractive clarity and blur multiplier for the nav bar container",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.glassIntensity,
                    onValueChange = holder::setGlassIntensity,
                    valueRange = 0f..2.0f,
                    defaultValue = 1.0f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled && s.liquidGlass
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Pill Glass Intensity",
                    subtitle = "Refraction lens strength for the moving tab indicator pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillGlassIntensity,
                    onValueChange = holder::setPillGlassIntensity,
                    valueRange = 0f..2.0f,
                    defaultValue = 1.0f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled && s.liquidGlass
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Pill Chromatic Aberration",
                    subtitle = "Color dispersion fringe around pill refraction edges",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.pillChromaticAberration,
                    onCheckedChange = holder::setPillChromaticAberration,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) },
                    enabled = s.enabled && s.liquidGlass
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsToggleRow(
                    title = "Pill Shadows & Depth",
                    subtitle = "Drop shadow and inner depth shadow on the indicator pill",
                    colors = colors,
                    iconTint = accentColor.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = s.pillShadow,
                    onCheckedChange = holder::setPillShadow,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) },
                    enabled = s.enabled && s.liquidGlass
                )
            }
        }

        item {
            SectionHeader("Surface Opacity", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Bar Container Opacity",
                    subtitle = "Translucent background tint opacity for the floating bar",
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
                    title = "Pill Tint Opacity",
                    subtitle = "Surface tint opacity for the indicator pill",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.pillContainerOpacity,
                    onValueChange = holder::setPillContainerOpacity,
                    valueRange = 0f..1f,
                    defaultValue = 1.0f,
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
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance of floating bar above bottom of screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.bottomMargin,
                    onValueChange = holder::setBottomMargin,
                    valueRange = 0f..80f,
                    defaultValue = defaultBottomMargin,
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
                            customColor = s.customAccentColor,
                            colors = colors,
                            selected = preset == s.accent,
                            onClick = {
                                holder.setAccent(preset)
                                if (preset == AccentPreset.Custom) {
                                    showColorPicker = true
                                }
                            }
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

private fun accentColorFor(preset: AccentPreset, customColor: Long, colors: HyperBounceColors): Color =
    when (preset) {
        AccentPreset.Custom -> Color(customColor)
        AccentPreset.Auto -> colors.accentBlue
        else -> preset.argb?.let { Color(it) } ?: colors.accentBlue
    }
