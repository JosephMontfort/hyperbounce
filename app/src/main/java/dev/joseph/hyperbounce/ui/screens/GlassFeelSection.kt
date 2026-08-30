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
    holder: SettingsRepository.GlassFeelHolder,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    enabled: Boolean
) {
    val feel = holder.snapshot()

    SectionHeader(sectionTitle, colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        SettingsSliderRow(
            title = "Blur",
            subtitle = "Backdrop gaussian blur radius",
            colors = colors,
            backdrop = backdrop,
            value = feel.blurDp,
            onValueChange = holder::setBlurDp,
            valueRange = 0f..16f,
            defaultValue = holder.defaults.blurDp,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Lens Size",
            subtitle = "Curvature radius of refraction lens",
            colors = colors,
            backdrop = backdrop,
            value = feel.lensSizeDp,
            onValueChange = holder::setLensSizeDp,
            valueRange = 0f..18f,
            defaultValue = holder.defaults.lensSizeDp,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Lens Depth",
            subtitle = "Optical refraction extrusion depth",
            colors = colors,
            backdrop = backdrop,
            value = feel.lensDepthDp,
            onValueChange = holder::setLensDepthDp,
            valueRange = 0f..26f,
            defaultValue = holder.defaults.lensDepthDp,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Highlight Strength",
            subtitle = "Specular Fresnel rim highlight intensity",
            colors = colors,
            backdrop = backdrop,
            value = feel.highlightStrength,
            onValueChange = holder::setHighlightStrength,
            valueRange = 0f..1f,
            defaultValue = holder.defaults.highlightStrength,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Shadow Radius",
            subtitle = "Diffusion radius of glass drop shadow",
            colors = colors,
            backdrop = backdrop,
            value = feel.shadowRadiusDp,
            onValueChange = holder::setShadowRadiusDp,
            valueRange = 0f..10f,
            defaultValue = holder.defaults.shadowRadiusDp,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
    }

    SectionHeader("$sectionTitle · Drag Physics", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        SettingsSliderRow(
            title = "Drag Sensitivity",
            subtitle = "Touch tracking movement ratio",
            colors = colors,
            backdrop = backdrop,
            value = feel.dragSensitivity,
            onValueChange = holder::setDragSensitivity,
            valueRange = 0.2f..1.5f,
            defaultValue = holder.defaults.dragSensitivity,
            valueLabel = { String.format("%.2f", it) },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Spring Bounciness",
            subtitle = "Lower = more overshoot bounce, Higher = stiffer vibration damping",
            colors = colors,
            backdrop = backdrop,
            value = feel.springDamping,
            onValueChange = holder::setSpringDamping,
            valueRange = 0.3f..1.0f,
            defaultValue = holder.defaults.springDamping,
            valueLabel = { String.format("%.2f", it) },
            enabled = enabled
        )
        RowDivider(colors.separator, insetStart = 16.dp)
        SettingsSliderRow(
            title = "Spring Stiffness",
            subtitle = "Lower = slower return oscillation, Higher = rapid snap settling",
            colors = colors,
            backdrop = backdrop,
            value = feel.springStiffness,
            onValueChange = holder::setSpringStiffness,
            valueRange = 100f..1000f,
            defaultValue = holder.defaults.springStiffness,
            valueLabel = { "${it.roundToInt()}" },
            enabled = enabled
        )
    }
}
