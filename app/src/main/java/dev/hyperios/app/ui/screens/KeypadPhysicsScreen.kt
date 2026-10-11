package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsNavRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors

/**
 * KeypadPhysicsScreen: Legacy PIN Pad Liquid Dynamics subpage.
 *
 * NOTE FOR FUTURE AGENTS / DEVELOPERS:
 * Per user request, the iOS 26 Radial Bloom Physics navigation row was moved directly to the
 * main KeypadScreen ("PIN Pad Physics") page, and Quick Dynamics Tuning as well as Pull & Stretch
 * and Release Snapback subpages were removed from the UI. Their underlying controls and calibrated
 * defaults remain fully active in the backend hook.
 */
@Composable
fun KeypadPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad Liquid Dynamics",
        subtitle = "Inertial mass transfer & radial bloom kinetics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setIosBloomDuration(ModuleDefaults.LockScreenKeypad.BLOOM_DURATION)
            keypad.setIosBloomClusterFactor(ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR)
            keypad.setIosBloomOvershoot(ModuleDefaults.LockScreenKeypad.BLOOM_OVERSHOOT)
            keypad.setIosBloomDamping(ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING)
            keypad.setIosBloomStiffness(ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS)
            keypad.setIosBloomRadialSquish(ModuleDefaults.LockScreenKeypad.BLOOM_RADIAL_SQUISH)
            keypad.setIosBloomVelocityStretch(ModuleDefaults.LockScreenKeypad.BLOOM_VELOCITY_STRETCH)
            keypad.setIosBloomWaveStagger(ModuleDefaults.LockScreenKeypad.BLOOM_WAVE_STAGGER)
            keypad.setIosBloomDecoupleText(ModuleDefaults.LockScreenKeypad.BLOOM_DECOUPLE_TEXT)
            keypad.setDragMaxThreshold(ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD)
            keypad.setDragInitialStiffness(ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS)
            keypad.setDragResistancePower(ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER)
            keypad.setDragVolumeStretch(ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH)
            keypad.setReleaseDuration(ModuleDefaults.LockScreenKeypad.RELEASE_DURATION)
            keypad.setReleaseSquishAmp(ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP)
            keypad.setReleaseOvershoot(ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Physics Subpages", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "iOS 26 Radial Bloom Physics",
                    subtitle = "Cluster origin, radial spring damping & bloom expansion",
                    colors = colors,
                    onClick = { onNavigate(Destination.KeypadIosBloomPhysics) }
                )
            }
            SectionFooter(
                "Tune the iOS 26 radial bloom entrance kinetics for PIN pad keys.",
                colors
            )
        }
    }
}
