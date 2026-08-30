package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.InfoIcon
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    val glass = LocalGlassSettings.current

    RootScaffold(
        title = "Settings",
        subtitle = "HyperBounce itself",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = { onNavigate(Destination.About) }
    ) {
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "HyperBounce Enabled",
                subtitle = if (repository.moduleEnabled) "All liquid glass effects are active" else "Effects paused everywhere",
                colors = colors,
                iconTint = colors.accentGreen.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = repository.moduleEnabled,
                onCheckedChange = repository::setModuleEnabled,
                icon = { PowerIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
            )
        }

        SectionHeader("Appearance", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Liquid Glass Effects",
                subtitle = if (glass.enabled) "Real blur & refraction everywhere" else "Flat translucent surfaces",
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = glass.enabled,
                onCheckedChange = repository.globalGlass::setEnabled,
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Blur",
                colors = colors,
                backdrop = backdrop,
                value = glass.blurDp,
                onValueChange = repository.globalGlass::setBlurDp,
                valueRange = 0f..16f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = glass.enabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "Refraction Height",
                colors = colors,
                backdrop = backdrop,
                value = glass.refractionHeightDp,
                onValueChange = repository.globalGlass::setRefractionHeightDp,
                valueRange = 0f..24f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = glass.enabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "Refraction Amount",
                colors = colors,
                backdrop = backdrop,
                value = glass.refractionAmountDp,
                onValueChange = repository.globalGlass::setRefractionAmountDp,
                valueRange = 0f..30f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = glass.enabled
            )
            RowDivider(colors.separator)
            SettingsToggleRow(
                title = "Chromatic Aberration",
                colors = colors,
                iconTint = colors.accentOrange.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = glass.chromaticAberration,
                onCheckedChange = repository.globalGlass::setChromaticAberration,
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )
            RowDivider(colors.separator)
            SettingsToggleRow(
                title = "Haptic Feedback",
                subtitle = "Felt when dragging over the menu",
                colors = colors,
                iconTint = colors.accentGreen.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = glass.hapticsEnabled,
                onCheckedChange = repository.globalGlass::setHapticsEnabled,
                icon = { PowerIcon(color = Color.White, size = 14.dp) }
            )
        }
        SectionFooter(
            "Applies to cards, buttons, previews, and the dropdown menu throughout the app. Toggles and sliders always render with full glass regardless of this switch.",
            colors
        )

        SectionHeader("About", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsNavRow(
                title = "About HyperBounce",
                subtitle = "Version, credits",
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                onClick = { onNavigate(Destination.About) },
                icon = { InfoIcon(color = Color.White, size = 14.dp, strokeWidth = 1.6.dp) }
            )
        }
    }
}
