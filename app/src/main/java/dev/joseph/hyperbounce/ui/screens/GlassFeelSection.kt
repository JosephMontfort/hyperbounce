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
import kotlin.math.roundToInt

/**
 * The 8-slider "feel" block shared by the toggle engine and the slider engine — same
 * parameter set (blur, lens size/depth, highlight, shadow, drag sensitivity, spring
 * damping/stiffness), independent stored values per engine (see
 * SettingsRepository.toggleGlass / .sliderGlass).
 */
@Composable
fun GlassFeelSection(
    sectionTitle: String,
    footer: String,
    holder: SettingsRepository.GlassFeelHolder,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    enabled: Boolean
) {
    val feel = holder.snapshot()

    SectionHeader(sectionTitle, colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) { cardBackdrop ->
        SettingsSliderRow(
            title = "Blur",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.blurDp,
            onValueChange = holder::setBlurDp,
            valueRange = 0f..16f,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Lens Size",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.lensSizeDp,
            onValueChange = holder::setLensSizeDp,
            valueRange = 0f..18f,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Lens Depth",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.lensDepthDp,
            onValueChange = holder::setLensDepthDp,
            valueRange = 0f..26f,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Highlight Strength",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.highlightStrength,
            onValueChange = holder::setHighlightStrength,
            valueRange = 0f..1f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Shadow Radius",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.shadowRadiusDp,
            onValueChange = holder::setShadowRadiusDp,
            valueRange = 0f..10f,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
    }

    SectionHeader("$sectionTitle · Drag Physics", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) { cardBackdrop ->
        SettingsSliderRow(
            title = "Drag Sensitivity",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.dragSensitivity,
            onValueChange = holder::setDragSensitivity,
            valueRange = 0.2f..1.5f,
            valueLabel = { String.format("%.2f", it) },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Spring Bounciness",
            subtitle = "Lower damping = more bounce",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.springDamping,
            onValueChange = holder::setSpringDamping,
            valueRange = 0.3f..1.0f,
            valueLabel = { String.format("%.2f", it) },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Spring Stiffness",
            subtitle = "How quickly it settles",
            colors = colors,
            backdrop = cardBackdrop,
            value = feel.springStiffness,
            onValueChange = holder::setSpringStiffness,
            valueRange = 100f..1000f,
            valueLabel = { "${it.roundToInt()}" },
            enabled = enabled
        )
    }
    SectionFooter(footer, colors)
}
