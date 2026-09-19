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
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.AppsIcon
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.ColorPickerDialog
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.RenderEngine
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun EngineScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dialogHost = LocalGlassDialogHost.current
    var showMiuixScopeDialog by remember { mutableStateOf(false) }
    var showToggleColorPicker by remember { mutableStateOf(false) }
    var showSliderColorPicker by remember { mutableStateOf(false) }

    if (showMiuixScopeDialog) {
        dialogHost.showConfirmation(
            title = "LSPosed Scope Required",
            message = "To hook third-party MIUIX apps (such as Hypercieler or Hyperlight), make sure they are checked in HyperBounce's module scope in LSPosed Manager and restart the target apps.",
            confirmLabel = "Got It",
            onConfirm = { showMiuixScopeDialog = false },
            onDismiss = { showMiuixScopeDialog = false }
        )
    }

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

    ScreenScaffold(
        title = "Toggles & Sliders",
        subtitle = "HyperOS system toggles, sliders, and keypad",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            repository.toggleGlass.reloadFromDefaults()
            repository.sliderGlass.reloadFromDefaults()
            repository.setToggleEngine(RenderEngine.LiquidGlass)
            repository.setSliderEngine(RenderEngine.LiquidGlass)
            repository.setToggleAccent(AccentPreset.Green)
            repository.setToggleCustomColor(0xFF30D158L)
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
                    title = "Toggle Switches",
                    colors = colors,
                    backdrop = combinedBackdrop,
                    selected = repository.toggleEngine,
                    onSelect = repository::setToggleEngine
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                EngineRow(
                    title = "Sliders",
                    colors = colors,
                    backdrop = combinedBackdrop,
                    selected = repository.sliderEngine,
                    onSelect = repository::setSliderEngine
                )
            }
        }

        item {
            SectionHeader("Third-Party Apps", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsToggleRow(
                    title = "MIUIX App Hooking",
                    subtitle = "Auto-detect and replace toggles/sliders in third-party apps (e.g. Hypercieler, Hyperlight)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = repository.miuixAppsHookingEnabled,
                    onCheckedChange = { enabled ->
                        repository.setMiuixAppsHookingEnabled(enabled)
                        if (enabled) {
                            showMiuixScopeDialog = true
                            android.widget.Toast.makeText(
                                context,
                                "Make sure target apps are enabled in HyperBounce's LSPosed scope!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    AppsIcon(color = Color.White, size = 18.dp)
                }
            }
        }

        item {
            SectionHeader("Active Colors", colors)
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
                        AccentPreset.entries.forEach { preset ->
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
                RowDivider(colors.separator, insetStart = 16.dp)
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
                        AccentPreset.entries.forEach { preset ->
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
            GlassFeelSection(
                sectionTitle = "Toggle Glass",
                holder = repository.toggleGlass,
                colors = colors,
                backdrop = backdrop,
                enabled = repository.toggleEngine == RenderEngine.LiquidGlass
            )
        }

        item {
            GlassFeelSection(
                sectionTitle = "Slider Glass",
                holder = repository.sliderGlass,
                colors = colors,
                backdrop = backdrop,
                enabled = repository.sliderEngine == RenderEngine.LiquidGlass
            )
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

@Composable
private fun EngineRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    selected: RenderEngine,
    onSelect: (RenderEngine) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(title, style = HyperBounceType.body.copy(color = colors.label))
        DropdownPicker(
            options = RenderEngine.entries.map { it.label },
            selectedIndex = RenderEngine.entries.indexOf(selected),
            onSelect = { onSelect(RenderEngine.entries[it]) },
            backdrop = backdrop
        )
    }
}
