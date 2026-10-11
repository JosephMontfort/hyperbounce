package dev.hyperios.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.ColorPickerDialog
import dev.hyperios.app.ui.components.DropdownPicker
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsDropdownRow
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.AccentPreset
import dev.hyperios.app.ui.state.NavApp
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.state.ThemeMode
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
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
        subtitle = null,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = holder::reloadFromDefaults
    ) { _ ->
        item {
            SectionHeader("Liquid Glass Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass Engine",
                    subtitle = null,
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
                        valueRange = ModuleDefaults.NavApps.GLASS_INTENSITY_RANGE,
                        defaultValue = ModuleDefaults.NavApps.GLASS_INTENSITY,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        enabled = s.enabled && s.liquidGlass
                    )
                    RowDivider(colors.separator, insetStart = 16.dp)
                    SettingsSliderRow(
                        title = "Bar Blur Intensity",
                        subtitle = "Backdrop blur intensity for the nav bar container (0% = pure refraction)",
                        colors = colors,
                        backdrop = backdrop,
                        value = s.barBlurIntensity,
                        onValueChange = holder::setBarBlurIntensity,
                        valueRange = ModuleDefaults.NavApps.BAR_BLUR_INTENSITY_RANGE,
                        defaultValue = ModuleDefaults.NavApps.BAR_BLUR_INTENSITY,
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
                        valueRange = ModuleDefaults.NavApps.PILL_GLASS_INTENSITY_RANGE,
                        defaultValue = ModuleDefaults.NavApps.PILL_GLASS_INTENSITY,
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
                    valueRange = ModuleDefaults.NavApps.CONTAINER_OPACITY_RANGE,
                    defaultValue = ModuleDefaults.NavApps.CONTAINER_OPACITY,
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
                    valueRange = ModuleDefaults.NavApps.PILL_CONTAINER_OPACITY_RANGE,
                    defaultValue = ModuleDefaults.NavApps.PILL_CONTAINER_OPACITY,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = s.enabled
                )
            }
        }

        item {
            SectionHeader("Theme & Accent", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsDropdownRow(
                    title = "Theme",
                    options = listOf("Default", "Light", "Dark"),
                    selectedIndex = ThemeMode.entries.indexOf(s.themeMode),
                    onSelect = { holder.setThemeMode(ThemeMode.entries[it]) },
                    colors = colors,
                    backdrop = backdrop
                )
                RowDivider(colors.separator, insetStart = 16.dp)

                BasicText(
                    "Accent Color",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    style = HyperBounceType.headline.copy(color = colors.label, fontWeight = FontWeight.SemiBold)
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
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
