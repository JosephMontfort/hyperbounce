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
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun NotificationCenterSheetHomeIndicatorScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.notificationSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Home Indicator Pill",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setPillWidth(ModuleDefaults.NotificationSheet.PILL_WIDTH)
            sheet.setPillHeight(ModuleDefaults.NotificationSheet.PILL_HEIGHT)
            sheet.setPillBottomMargin(ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
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
                    valueRange = ModuleDefaults.NotificationSheet.PILL_WIDTH_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.PILL_WIDTH,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Pill Height",
                    subtitle = "Vertical thickness of the indicator bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillHeight,
                    onValueChange = sheet::setPillHeight,
                    valueRange = ModuleDefaults.NotificationSheet.PILL_HEIGHT_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.PILL_HEIGHT,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance from screen bottom to indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillBottomMargin,
                    onValueChange = sheet::setPillBottomMargin,
                    valueRange = ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
        }
    }
}
