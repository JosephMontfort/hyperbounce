package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
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
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { cardBackdrop ->
            EngineRow(
                title = "Toggle Switches",
                colors = colors,
                backdrop = cardBackdrop,
                selected = repository.toggleEngine,
                onSelect = repository::setToggleEngine
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            EngineRow(
                title = "Sliders",
                colors = colors,
                backdrop = cardBackdrop,
                selected = repository.sliderEngine,
                onSelect = repository::setSliderEngine
            )
        }
        SectionFooter(
            "Native Canvas is the original AGSL shader renderer. Liquid Glass renders " +
                "through this library's Compose backdrop pipeline — same one used everywhere " +
                "else in this app.",
            colors
        )

        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) { cardBackdrop ->
            SettingsToggleRow(
                title = "Liquid Glass Keypad",
                subtitle = "Dialer & lock screen keypad glass effect",
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                backdrop = cardBackdrop,
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

@Composable
private fun EngineRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    selected: RenderEngine,
    onSelect: (RenderEngine) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(title, style = HyperBounceType.body.copy(color = colors.label))
        DropdownPicker(
            options = RenderEngine.entries.map { it.label },
            selectedIndex = RenderEngine.entries.indexOf(selected),
            onSelect = { onSelect(RenderEngine.entries[it]) },
            backdrop = backdrop
        )
    }
}
