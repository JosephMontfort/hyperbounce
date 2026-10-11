package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.NavApp
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun AppLayoutScreen(
    app: NavApp,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val holder = repository.appSettings(app)
    val s = holder.snapshot()

    val defaultCornerRadius = when (app) {
        NavApp.WhatsApp -> ModuleDefaults.NavApps.WhatsApp.CORNER_RADIUS
        NavApp.YouTube -> ModuleDefaults.NavApps.YouTube.CORNER_RADIUS
        NavApp.Instagram -> ModuleDefaults.NavApps.Instagram.CORNER_RADIUS
    }
    val defaultBarHeight = when (app) {
        NavApp.WhatsApp -> ModuleDefaults.NavApps.WhatsApp.BAR_HEIGHT
        NavApp.YouTube -> ModuleDefaults.NavApps.YouTube.BAR_HEIGHT
        NavApp.Instagram -> ModuleDefaults.NavApps.Instagram.BAR_HEIGHT
    }
    val defaultTabWidth = when (app) {
        NavApp.WhatsApp -> ModuleDefaults.NavApps.WhatsApp.TAB_WIDTH
        NavApp.YouTube -> ModuleDefaults.NavApps.YouTube.TAB_WIDTH
        NavApp.Instagram -> ModuleDefaults.NavApps.Instagram.TAB_WIDTH
    }
    val defaultBottomMargin = when (app) {
        NavApp.WhatsApp -> ModuleDefaults.NavApps.WhatsApp.BOTTOM_MARGIN
        NavApp.YouTube -> ModuleDefaults.NavApps.YouTube.BOTTOM_MARGIN
        NavApp.Instagram -> ModuleDefaults.NavApps.Instagram.BOTTOM_MARGIN
    }

    ScreenScaffold(
        title = "Layout",
        subtitle = null,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = holder::reloadFromDefaults
    ) { _ ->
        item {
            SectionHeader("Shape & Size", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Corner Radius",
                    subtitle = "Corner rounding radius for floating bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.cornerRadius,
                    onValueChange = holder::setCornerRadius,
                    valueRange = ModuleDefaults.NavApps.CORNER_RADIUS_RANGE,
                    defaultValue = defaultCornerRadius,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bar Height",
                    subtitle = "Vertical height of floating pill container",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.barHeight,
                    onValueChange = holder::setBarHeight,
                    valueRange = ModuleDefaults.NavApps.BAR_HEIGHT_RANGE,
                    defaultValue = defaultBarHeight,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Tab Width",
                    subtitle = "Width allocated per tab icon",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.tabWidth,
                    onValueChange = holder::setTabWidth,
                    valueRange = ModuleDefaults.NavApps.TAB_WIDTH_RANGE,
                    defaultValue = defaultTabWidth,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance of floating bar above bottom of screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = s.bottomMargin,
                    onValueChange = holder::setBottomMargin,
                    valueRange = ModuleDefaults.NavApps.BOTTOM_MARGIN_RANGE,
                    defaultValue = defaultBottomMargin,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
            }
        }
    }
}
