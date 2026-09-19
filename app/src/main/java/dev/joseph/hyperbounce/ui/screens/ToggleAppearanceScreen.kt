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
import dev.joseph.hyperbounce.ui.components.ColorPickerDialog
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.RenderEngine
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun ToggleAppearanceScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showToggleColorPicker by remember { mutableStateOf(false) }

    if (showToggleColorPicker) {
        ColorPickerDialog(
            initialColor = repository.toggleCustomColor,
            colors = colors,
            backdrop = backdrop,
            onDismiss = { showToggleColorPicker = false },
            onColorSelected = { newColor ->
                repository.setToggleCustomColor(newColor)
                repository.setToggleAccent(AccentPreset.Custom)
            }
        )
    }

    val presets = remember { AccentPreset.entries.filter { it != AccentPreset.Auto } }
    val isGlassEnabled = repository.toggleEngine == RenderEngine.LiquidGlass

    ScreenScaffold(
        title = "Toggles Appearance",
        subtitle = "Active switch accent color & Liquid Glass optics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            repository.setToggleAccent(AccentPreset.Green)
            repository.setToggleCustomColor(0xFF30D158L)
            repository.toggleGlass.reloadFromDefaults()
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
                        "Toggle Switch Accent",
                        style = HyperBounceType.body.copy(color = colors.label)
                    )
                    BasicText(
                        "Active state color for toggle switches across the system",
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
                                customColor = repository.toggleCustomColor,
                                colors = colors,
                                selected = preset == repository.toggleAccent,
                                onClick = {
                                    repository.setToggleAccent(preset)
                                    if (preset == AccentPreset.Custom) {
                                        showToggleColorPicker = true
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
                holder = repository.toggleGlass,
                colors = colors,
                backdrop = backdrop,
                enabled = isGlassEnabled
            )
        }
    }
}
