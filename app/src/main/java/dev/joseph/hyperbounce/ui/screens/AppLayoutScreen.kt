package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
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

    val defaultCornerRadius = if (app == NavApp.WhatsApp) 36f else 32f
    val defaultBarHeight = if (app == NavApp.WhatsApp) 70f else 64f
    val defaultTabWidth = if (app == NavApp.WhatsApp) 92f else 64f
    val defaultBottomMargin = if (app == NavApp.YouTube) 28f else 40f

    ScreenScaffold(
        title = "Layout",
        subtitle = null,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
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
                    valueRange = 12f..40f,
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
                    valueRange = 48f..88f,
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
                    valueRange = 48f..88f,
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
                    valueRange = 0f..80f,
                    defaultValue = defaultBottomMargin,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = s.enabled
                )
            }
        }
    }
}
