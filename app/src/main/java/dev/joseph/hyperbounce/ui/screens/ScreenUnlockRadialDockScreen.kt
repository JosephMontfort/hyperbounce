package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun ScreenUnlockRadialDockScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Radial Dock Settling",
        subtitle = "Dock icons bounce & arrival settling",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            unlock.setDockDamping(0.70f)
            unlock.setDockResponse(0.40f)
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("RADIAL DOCK SETTLING", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Lower = bouncier dock icons, Higher = stiffer dock settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.dockDamping,
                    onValueChange = unlock::setDockDamping,
                    valueRange = 0.1f..1.0f,
                    defaultValue = 0.70f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Lower = snaps dock faster, Higher = slower dock arrival",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.dockResponse,
                    onValueChange = unlock::setDockResponse,
                    valueRange = 0.1f..1.5f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Independent harmonic damping and arrival timing for dock icons in Mode 0 radial unlock.",
                colors
            )
        }
    }
}
