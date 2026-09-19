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
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
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

    ScreenScaffold(
        title = "Sliders Appearance",
        subtitle = "Rendering engine & active track accent color",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            repository.setSliderEngine(RenderEngine.LiquidGlass)
            repository.setSliderAccent(AccentPreset.Green)
            repository.setSliderCustomColor(0xFF30D158L)
        },
        restartAppTitle = "Restart All Scoped Apps",
        restartAppSubtitle = "Restart all apps in active scope",
        onRestartApp = {
            val pkgs = dev.joseph.hyperbounce.ui.state.XposedServiceBridge.getScopedPackages(context)
            dev.joseph.hyperbounce.ui.utils.RootUtils.restartScopedApps(context, pkgs)
        }
    ) { contentBackdrop ->
        item {
            val combinedBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)
            SectionHeader("Rendering Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                EngineRow(
                    title = "Sliders Engine",
                    colors = colors,
                    backdrop = combinedBackdrop,
                    selected = repository.sliderEngine,
                    onSelect = repository::setSliderEngine
                )
            }
        }

        item {
            SectionHeader("Accent Color", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                    BasicText(
                        "Slider Track Accent",
                        style = HyperBounceType.body.copy(color = colors.label)
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
    }
}
