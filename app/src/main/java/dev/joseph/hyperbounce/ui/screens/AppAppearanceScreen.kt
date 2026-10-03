package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.ColorPickerDialog
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
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
fun AppAppearanceScreen(
    app: NavApp,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val holder = repository.appSettings(app)
    val s = holder.snapshot()
    val accentColor = accentColorFor(s.accent, s.customAccentColor, colors)
    var showColorPicker by remember { mutableStateOf(false) }

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
        title = "Appearance",
        subtitle = "${app.displayName} visual styling",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            SectionHeader("Liquid Glass Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass Engine",
                    subtitle = if (s.liquidGlass) "Dynamic liquid refraction, lens and blur" else "Translucent iOS-style bar (zero backdrop lag)",
                    colors = colors,
                    backdrop = backdrop,
                    checked = s.liquidGlass,
                    onCheckedChange = holder::setLiquidGlass,
                    enabled = s.enabled
                )
                if (s.liquidGlass) {
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
                        backdrop = backdrop,
                        checked = s.pillChromaticAberration,
                        onCheckedChange = holder::setPillChromaticAberration,
                        enabled = s.enabled && s.liquidGlass
                    )
                    RowDivider(colors.separator, insetStart = 16.dp)
                    SettingsToggleRow(
                        title = "Pill Shadows & Depth",
                        subtitle = "Drop shadow and inner depth shadow on the indicator pill",
                        colors = colors,
                        backdrop = backdrop,
                        checked = s.pillShadow,
                        onCheckedChange = holder::setPillShadow,
                        enabled = s.enabled && s.liquidGlass
                    )
                }
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
            SectionHeader("Theme & Accent", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText("Theme", style = HyperBounceType.body.copy(color = colors.label))
                    DropdownPicker(
                        options = listOf("Default", "Light", "Dark"),
                        selectedIndex = ThemeMode.entries.indexOf(s.themeMode),
                        onSelect = { holder.setThemeMode(ThemeMode.entries[it]) },
                        backdrop = backdrop
                    )
                }
                RowDivider(colors.separator, insetStart = 16.dp)

                BasicText(
                    "Accent Color",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
