package dev.hyperios.app.ui.screens

import androidx.compose.runtime.Composable
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.ui.state.RenderEngine
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors

@Composable
fun TogglePhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Toggles Drag Physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            repository.toggleGlass.reloadFromDefaults()
        },
        restartAppTitle = "Restart All Scoped Apps",
        restartAppSubtitle = "Restart all apps in active scope",
        onRestartApp = {
            val pkgs = dev.hyperios.app.ui.state.XposedServiceBridge.getScopedPackages(context)
            dev.hyperios.app.ui.utils.RootUtils.restartScopedApps(context, pkgs)
        }
    ) { _ ->
        item {
            DragPhysicsSection(
                sectionTitle = null,
                holder = repository.toggleGlass,
                colors = colors,
                backdrop = backdrop,
                enabled = repository.toggleEngine == RenderEngine.LiquidGlass
            )
        }
    }
}
