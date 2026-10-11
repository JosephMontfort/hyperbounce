package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun MorphPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val morph = repository.morphPhysics

    ScreenScaffold(
        title = "Morph Physics",
        subtitle = "Dialog Morph Curve & Mass Engine",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = { morph.reloadFromDefaults() },
        onRestartApp = {
            android.os.Process.killProcess(android.os.Process.myPid())
        },
        restartAppTitle = "Restart Companion App",
        restartAppSubtitle = "HyperBounce Settings"
    ) {
        item {
            SectionHeader("Timing & Speed Multipliers", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Morph Speed Multiplier",
                    subtitle = "Lower = slow-motion curve inspection, Higher = instantaneous snap-flip",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.morphSpeedMultiplier,
                    onValueChange = morph::setMorphSpeedMultiplier,
                    valueRange = ModuleDefaults.MorphPhysics.MORPH_SPEED_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.MORPH_SPEED_MULTIPLIER,
                    valueLabel = { String.format("%.2fx", it) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Circle Collapse Ratio",
                    subtitle = "Lower = quick snap to circle, Higher = prolonged mass condensation",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.circleCollapseRatio,
                    onValueChange = morph::setCircleCollapseRatio,
                    valueRange = ModuleDefaults.MorphPhysics.CIRCLE_COLLAPSE_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.CIRCLE_COLLAPSE_RATIO,
                    valueLabel = { String.format("%.0f%%", it * 100f) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Spring Stiffness",
                    subtitle = "Lower = relaxed harmonic swing, Higher = high-tension snappy stroke",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.springStiffness,
                    onValueChange = morph::setSpringStiffness,
                    valueRange = ModuleDefaults.MorphPhysics.SPRING_STIFFNESS_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.SPRING_STIFFNESS,
                    valueLabel = { "${it.roundToInt()}f" }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Spring Damping Ratio",
                    subtitle = "Lower = elastic oscillation bounces, Higher = critical no-bounce damping",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.springDamping,
                    onValueChange = morph::setSpringDamping,
                    valueRange = ModuleDefaults.MorphPhysics.SPRING_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.SPRING_DAMPING,
                    valueLabel = { String.format("%.2f", it) }
                )
            }
        }

        item {
            SectionHeader("3D Geometry & Trajectory", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Intermediate Circle Diameter",
                    subtitle = "Lower = compact core pill, Higher = larger intermediate droplet",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.circleDiameterDp,
                    onValueChange = morph::setCircleDiameterDp,
                    valueRange = ModuleDefaults.MorphPhysics.CIRCLE_DIAMETER_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.CIRCLE_DIAMETER_DP,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Z-Axis Plunge Depth",
                    subtitle = "Lower = flat 2D travel, Higher = deeper 3D depth into screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.zPlungeDepth,
                    onValueChange = morph::setZPlungeDepth,
                    valueRange = ModuleDefaults.MorphPhysics.Z_PLUNGE_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.Z_PLUNGE_DEPTH,
                    valueLabel = { String.format("%.0f%%", it * 100f) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Gravity Swoop Y",
                    subtitle = "Lower = straight diagonal flight, Higher = pronounced parabolic arc",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.gravitySwoopDp,
                    onValueChange = morph::setGravitySwoopDp,
                    valueRange = ModuleDefaults.MorphPhysics.GRAVITY_SWOOP_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.GRAVITY_SWOOP_DP,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Landing Y-Bounce",
                    subtitle = "Lower = soft landing, Higher = energetic vertical harmonic rebound",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.yBounceAmountDp,
                    onValueChange = morph::setYBounceAmountDp,
                    valueRange = ModuleDefaults.MorphPhysics.Y_BOUNCE_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.Y_BOUNCE_AMOUNT_DP,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
            }
        }

        item {
            SectionHeader("Material Elasticity & Content Bloom", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "4-Edge Isotropic Jelly Recoil",
                    subtitle = "Lower = rigid container edges, Higher = rubbery multi-edge recoil overshoot",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.jellyOvershoot,
                    onValueChange = morph::setJellyOvershoot,
                    valueRange = ModuleDefaults.MorphPhysics.JELLY_OVERSHOOT_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.JELLY_OVERSHOOT,
                    valueLabel = { String.format("%.1f%%", it * 100f) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Content Outward Stretch",
                    subtitle = "Lower = static content appearance, Higher = dynamic center-bloom stretch",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.contentStretch,
                    onValueChange = morph::setContentStretch,
                    valueRange = ModuleDefaults.MorphPhysics.CONTENT_STRETCH_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.CONTENT_STRETCH,
                    valueLabel = { String.format("%.1f%%", it * 100f) }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Lens Refraction Surge",
                    subtitle = "Lower = uniform optics, Higher = intense magnification lens surge in-flight",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.lensSurgeDp,
                    onValueChange = morph::setLensSurgeDp,
                    valueRange = ModuleDefaults.MorphPhysics.LENS_SURGE_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.LENS_SURGE_DP,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Motion Blur Surge",
                    subtitle = "Lower = sharp backdrop, Higher = dynamic speed-blur distortion surge",
                    colors = colors,
                    backdrop = backdrop,
                    value = morph.motionBlurSurgeDp,
                    onValueChange = morph::setMotionBlurSurgeDp,
                    valueRange = ModuleDefaults.MorphPhysics.MOTION_BLUR_RANGE,
                    defaultValue = ModuleDefaults.MorphPhysics.MOTION_BLUR_SURGE_DP,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
