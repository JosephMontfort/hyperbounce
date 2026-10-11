package dev.hyperios.app.ui.screens

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.AppsIcon
import dev.hyperios.app.ui.components.CheckmarkIcon
import dev.hyperios.app.ui.components.DropdownPicker
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsDropdownRow
import dev.hyperios.app.ui.components.SettingsNavRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.glass.LocalGlassDialogHost
import dev.hyperios.app.ui.state.AccentPreset
import dev.hyperios.app.ui.state.RenderEngine
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType

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
            repository.setTogglesSlidersHookEnabled(ModuleDefaults.MasterToggles.MASTER_HOOKS_ENABLED)
            repository.toggleGlass.reloadFromDefaults()
            repository.sliderGlass.reloadFromDefaults()
            repository.setToggleEngine(RenderEngine.valueOf(ModuleDefaults.Engine.TOGGLE_ENGINE))
            repository.setSliderEngine(RenderEngine.valueOf(ModuleDefaults.Engine.SLIDER_ENGINE))
            repository.setToggleAccent(AccentPreset.valueOf(ModuleDefaults.Engine.TOGGLE_ACCENT))
            repository.setToggleCustomColor(ModuleDefaults.Engine.TOGGLE_CUSTOM_COLOR)
            repository.setSliderAccent(AccentPreset.valueOf(ModuleDefaults.Engine.SLIDER_ACCENT))
            repository.setSliderCustomColor(ModuleDefaults.Engine.SLIDER_CUSTOM_COLOR)
            repository.setMiuixAppsHookingEnabled(ModuleDefaults.MasterToggles.MIUIX_APPS_HOOKING_ENABLED)
        },
        restartAppTitle = "Restart All Scoped Apps",
        restartAppSubtitle = "Restart all apps in active scope",
        onRestartApp = {
            val pkgs = dev.hyperios.app.ui.state.XposedServiceBridge.getScopedPackages(context)
            dev.hyperios.app.ui.utils.RootUtils.restartScopedApps(context, pkgs)
        }
    ) { contentBackdrop ->
        val masterEnabled = repository.togglesSlidersHookEnabled
        val togglesEnabled = masterEnabled && repository.toggleEngine != RenderEngine.Disabled
        val slidersEnabled = masterEnabled && repository.sliderEngine != RenderEngine.Disabled

        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsToggleRow(
                    title = "Enable Hook",
                    colors = colors,
                    backdrop = backdrop,
                    checked = repository.togglesSlidersHookEnabled,
                    onCheckedChange = repository::setTogglesSlidersHookEnabled
                )
            }
        }

        item {
            val combinedBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)
            SectionHeader("Rendering Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                EngineRow(
                    title = "Toggles",
                    colors = colors,
                    backdrop = combinedBackdrop,
                    selected = repository.toggleEngine,
                    onSelect = repository::setToggleEngine,
                    enabled = masterEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                EngineRow(
                    title = "Sliders",
                    colors = colors,
                    backdrop = combinedBackdrop,
                    selected = repository.sliderEngine,
                    onSelect = repository::setSliderEngine,
                    enabled = masterEnabled
                )
            }
        }

        item {
            SectionHeader("Toggles", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsNavRow(
                    title = "Toggles Appearance",
                    subtitle = "Accent color & Liquid Glass optics",
                    colors = colors,
                    onClick = { onNavigate(Destination.ToggleAppearance) },
                    enabled = togglesEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsNavRow(
                    title = "Toggles Drag Physics",
                    subtitle = "Touch tracking & spring bounce dynamics",
                    colors = colors,
                    onClick = { onNavigate(Destination.TogglePhysics) },
                    enabled = togglesEnabled
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
                    onClick = { onNavigate(Destination.SliderAppearance) },
                    enabled = slidersEnabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsNavRow(
                    title = "Sliders Drag Physics",
                    subtitle = "Touch tracking & spring bounce dynamics",
                    colors = colors,
                    onClick = { onNavigate(Destination.SliderPhysics) },
                    enabled = slidersEnabled
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
                    enabled = masterEnabled,
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
    onSelect: (RenderEngine) -> Unit,
    enabled: Boolean = true
) {
    SettingsDropdownRow(
        title = title,
        options = RenderEngine.entries.map { it.label },
        selectedIndex = RenderEngine.entries.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(RenderEngine.entries[it]) },
        colors = colors,
        backdrop = backdrop,
        enabled = enabled
    )
}
