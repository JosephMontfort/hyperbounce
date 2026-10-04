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
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsDropdownRow
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
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

    if (showMiuixScopeDialog) {
        dialogHost.showConfirmation(
            title = "LSPosed Scope Required",
            message = "To hook third-party MIUIX apps, make sure they are checked in HyperBounce's module scope in LSPosed Manager and restart the target apps.",
            confirmText = "Got It",
            cancelText = "Dismiss",
            onConfirm = { showMiuixScopeDialog = false },
            onCancel = { showMiuixScopeDialog = false }
        )
    }

    ScreenScaffold(
        title = "Toggles & Sliders",
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
            SectionHeader("Toggle Switches", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsNavRow(
                    title = "Toggles Appearance",
                    subtitle = "Accent color & Liquid Glass optics",
                    colors = colors,
                    onClick = { onNavigate(Destination.ToggleAppearance) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsNavRow(
                    title = "Toggles Drag Physics",
                    subtitle = "Touch tracking & spring bounce dynamics",
                    colors = colors,
                    onClick = { onNavigate(Destination.TogglePhysics) }
                )
            }
        }

        item {
            SectionHeader("Sliders", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsNavRow(
                    title = "Sliders Appearance",
                    subtitle = "Track accent & Liquid Glass optics",
                    colors = colors,
                    onClick = { onNavigate(Destination.SliderAppearance) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsNavRow(
                    title = "Sliders Drag Physics",
                    subtitle = "Touch tracking & spring bounce dynamics",
                    colors = colors,
                    onClick = { onNavigate(Destination.SliderPhysics) }
                )
            }
        }

        item {
            SectionHeader("Third-Party Apps", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsToggleRow(
                    title = "MIUIX App Hooking",
                    subtitle = "Auto-detect and replace toggles and sliders in third-party apps using miuix-kmp library",
                    colors = colors,
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
                )
            }
        }
    }
}

@Composable
internal fun EngineRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    selected: RenderEngine,
    onSelect: (RenderEngine) -> Unit
) {
    SettingsDropdownRow(
        title = title,
        options = RenderEngine.entries.map { it.label },
        selectedIndex = RenderEngine.entries.indexOf(selected),
        onSelect = { onSelect(RenderEngine.entries[it]) },
        colors = colors,
        backdrop = backdrop
    )
}
