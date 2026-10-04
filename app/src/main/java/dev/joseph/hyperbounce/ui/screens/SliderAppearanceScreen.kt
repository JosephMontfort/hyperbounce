package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.ColorPickerDialog
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.RenderEngine
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun SliderAppearanceScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSliderColorPicker by remember { mutableStateOf(false) }

    if (showSliderColorPicker) {
        ColorPickerDialog(
            initialColor = repository.sliderCustomColor,
            colors = colors,
            backdrop = backdrop,
            onDismiss = { showSliderColorPicker = false },
            onColorSelected = { newColor ->
                repository.setSliderCustomColor(newColor)
                repository.setSliderAccent(AccentPreset.Custom)
            }
        )
    }

    val presets = remember { AccentPreset.entries.filter { it != AccentPreset.Auto } }
    val isGlassEnabled = repository.sliderEngine == RenderEngine.LiquidGlass

    ScreenScaffold(
        title = "Sliders Appearance",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            repository.setSliderAccent(AccentPreset.Green)
            repository.setSliderCustomColor(0xFF30D158L)
            repository.sliderGlass.reloadFromDefaults()
        },
        restartAppTitle = "Restart All Scoped Apps",
        restartAppSubtitle = "Restart all apps in active scope",
        onRestartApp = {
            val pkgs = dev.joseph.hyperbounce.ui.state.XposedServiceBridge.getScopedPackages(context)
            dev.joseph.hyperbounce.ui.utils.RootUtils.restartScopedApps(context, pkgs)
        }
    ) { _ ->
        item {
            SectionHeader("Accent Color", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                    BasicText(
                        "Slider Track Accent",
                        style = HyperBounceType.headline.copy(color = colors.label, fontWeight = FontWeight.SemiBold)
                    )
                    BasicText(
                        "Active track fill color for sliders across the system",
                        style = HyperBounceType.footnote.copy(color = colors.secondaryLabel),
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presets.forEach { preset ->
                            AccentSwatch(
                                preset = preset,
                                customColor = repository.sliderCustomColor,
                                colors = colors,
                                selected = preset == repository.sliderAccent,
                                onClick = {
                                    repository.setSliderAccent(preset)
                                    if (preset == AccentPreset.Custom) {
                                        showSliderColorPicker = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassOpticsSection(
                sectionTitle = "Liquid Glass Optics",
                holder = repository.sliderGlass,
                colors = colors,
                backdrop = backdrop,
                enabled = isGlassEnabled
            )
        }
    }
}
