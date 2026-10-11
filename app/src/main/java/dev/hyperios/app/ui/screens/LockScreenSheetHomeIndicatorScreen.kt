package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetHomeIndicatorScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Home Indicator Pill",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setPillEnabled(ModuleDefaults.LockScreenSheet.PILL_ENABLED)
            sheet.setPillWidth(ModuleDefaults.LockScreenSheet.PILL_WIDTH)
            sheet.setPillHeight(ModuleDefaults.LockScreenSheet.PILL_HEIGHT)
            sheet.setPillBottomMargin(ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        val pillActive = sheet.enabled && sheet.pillEnabled

        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Home Indicator Pill",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.pillEnabled,
                    onCheckedChange = sheet::setPillEnabled,
                    enabled = sheet.enabled
                )
            }
        }

        item {
            SectionHeader("Pill Dimensions & Position", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Pill Width",
                    subtitle = "Horizontal size of the bottom home indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillWidth,
                    onValueChange = sheet::setPillWidth,
                    valueRange = ModuleDefaults.LockScreenSheet.PILL_WIDTH_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.PILL_WIDTH,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = pillActive
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Pill Height",
                    subtitle = "Vertical thickness of the indicator bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillHeight,
                    onValueChange = sheet::setPillHeight,
                    valueRange = ModuleDefaults.LockScreenSheet.PILL_HEIGHT_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.PILL_HEIGHT,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = pillActive
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance from screen bottom to indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillBottomMargin,
                    onValueChange = sheet::setPillBottomMargin,
                    valueRange = ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = pillActive
                )
            }
        }
    }
}
