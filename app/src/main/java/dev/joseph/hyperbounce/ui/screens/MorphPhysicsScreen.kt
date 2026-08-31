package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
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
                    valueRange = 0.10f..2.00f,
                    defaultValue = 0.78f,
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
                    valueRange = 0.15f..0.60f,
                    defaultValue = 0.35f,
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
                    valueRange = 20f..600f,
                    defaultValue = 220f,
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
                    valueRange = 0.40f..1.00f,
                    defaultValue = 0.70f,
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
                    valueRange = 28f..64f,
                    defaultValue = 44f,
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
                    valueRange = 0.00f..0.50f,
                    defaultValue = 0.50f,
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
                    valueRange = 0f..60f,
                    defaultValue = 60f,
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
                    valueRange = 0f..40f,
                    defaultValue = 0f,
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
                    valueRange = 0.00f..0.15f,
                    defaultValue = 0.00f,
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
                    valueRange = 0.00f..0.15f,
                    defaultValue = 0.00f,
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
                    valueRange = 0f..20f,
                    defaultValue = 20f,
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
                    valueRange = 0f..30f,
                    defaultValue = 30f,
                    valueLabel = { "${it.roundToInt()}dp" }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
