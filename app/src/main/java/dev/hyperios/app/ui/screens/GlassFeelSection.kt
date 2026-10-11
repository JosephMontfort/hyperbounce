package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

/**
 * The 8-slider "feel" block shared by the toggle engine and the slider engine — same
 * parameter set (blur, lens size/depth, highlight, shadow, drag sensitivity, spring
 * damping/stiffness), independent stored values per engine (see
 * SettingsRepository.toggleGlass / .sliderGlass).
 */
@Composable
fun GlassOpticsSection(
    sectionTitle: String = "Liquid Glass Optics",
    holder: SettingsRepository.GlassFeelHolder,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    enabled: Boolean
) {
    val feel = holder.snapshot()
    val isSlider = holder.prefix == "slider_glass"

    SectionHeader(sectionTitle, colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        SettingsSliderRow(
            title = "Blur",
            subtitle = "Backdrop gaussian blur radius",
            colors = colors,
            backdrop = backdrop,
            value = feel.blurDp,
            onValueChange = holder::setBlurDp,
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_BLUR_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_BLUR_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_LENS_SIZE_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_LENS_SIZE_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_LENS_DEPTH_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_LENS_DEPTH_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_HIGHLIGHT_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_HIGHLIGHT_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_SHADOW_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_SHADOW_RANGE,
            defaultValue = holder.defaults.shadowRadiusDp,
            valueLabel = { "${it.roundToInt()}dp" },
            enabled = enabled
        )
    }
}

@Composable
fun DragPhysicsSection(
    sectionTitle: String? = "Drag Physics",
    holder: SettingsRepository.GlassFeelHolder,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    enabled: Boolean
) {
    val feel = holder.snapshot()
    val isSlider = holder.prefix == "slider_glass"

    if (!sectionTitle.isNullOrEmpty()) {
        SectionHeader(sectionTitle, colors)
    }
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        SettingsSliderRow(
            title = "Drag Sensitivity",
            subtitle = "Touch tracking movement ratio",
            colors = colors,
            backdrop = backdrop,
            value = feel.dragSensitivity,
            onValueChange = holder::setDragSensitivity,
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_DRAG_SENSITIVITY_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_DRAG_SENSITIVITY_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_SPRING_DAMPING_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_DAMPING_RANGE,
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
            valueRange = if (isSlider) ModuleDefaults.Engine.SLIDER_GLASS_SPRING_STIFFNESS_RANGE else ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_STIFFNESS_RANGE,
            defaultValue = holder.defaults.springStiffness,
            valueLabel = { "${it.roundToInt()}" },
            enabled = enabled
        )
    }
}

@Composable
fun GlassFeelSection(
    sectionTitle: String,
    holder: SettingsRepository.GlassFeelHolder,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    enabled: Boolean
) {
    GlassOpticsSection(
        sectionTitle = sectionTitle,
        holder = holder,
        colors = colors,
        backdrop = backdrop,
        enabled = enabled
    )
    DragPhysicsSection(
        sectionTitle = "$sectionTitle · Drag Physics",
        holder = holder,
        colors = colors,
        backdrop = backdrop,
        enabled = enabled
    )
}
