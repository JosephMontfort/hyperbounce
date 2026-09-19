package dev.joseph.hyperbounce.ui.screens

import androidx.compose.runtime.Composable
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.state.RenderEngine
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun SliderPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Sliders Drag Physics",
        subtitle = "Liquid Glass lens feel, optics & spring dynamics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
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
