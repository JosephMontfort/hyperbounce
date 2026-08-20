package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SegmentedGlassPicker
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.RenderEngine
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun EngineScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "Toggles & Sliders",
        subtitle = "HyperOS system toggles, sliders, and keypad",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        SectionHeader("Rendering Engine", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            BasicText(
                "Toggle Switches",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
            )
            SegmentedGlassPicker(
                options = RenderEngine.entries.map { it.label },
                selectedIndex = RenderEngine.entries.indexOf(repository.toggleEngine),
                onSelect = { repository.setToggleEngine(RenderEngine.entries[it]) },
                backdrop = backdrop,
                colors = colors
            )

            BasicText(
                "Sliders",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
                style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
            )
            SegmentedGlassPicker(
                options = RenderEngine.entries.map { it.label },
                selectedIndex = RenderEngine.entries.indexOf(repository.sliderEngine),
                onSelect = { repository.setSliderEngine(RenderEngine.entries[it]) },
                backdrop = backdrop,
                colors = colors
            )
        }
        SectionFooter(
            "Native Canvas is the original AGSL shader renderer. Liquid Glass renders " +
                "through this library's Compose backdrop pipeline — same one used everywhere " +
                "else in this app.",
            colors
        )

        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Liquid Glass Keypad",
                subtitle = "Dialer & lock screen keypad glass effect",
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = repository.keypadEnabled,
                onCheckedChange = repository::setKeypadEnabled,
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )
        }

        GlassFeelSection(
            sectionTitle = "Toggle Glass",
            footer = "Applies to every liquid glass toggle switch across HyperOS when the " +
                "Toggle Switches engine above is set to Liquid Glass.",
            holder = repository.toggleGlass,
            colors = colors,
            backdrop = backdrop,
            enabled = repository.toggleEngine == RenderEngine.LiquidGlass
        )

        GlassFeelSection(
            sectionTitle = "Slider Glass",
            footer = "Applies to every liquid glass slider (brightness, volume, etc.) when " +
                "the Sliders engine above is set to Liquid Glass.",
            holder = repository.sliderGlass,
            colors = colors,
            backdrop = backdrop,
            enabled = repository.sliderEngine == RenderEngine.LiquidGlass
        )
    }
}
