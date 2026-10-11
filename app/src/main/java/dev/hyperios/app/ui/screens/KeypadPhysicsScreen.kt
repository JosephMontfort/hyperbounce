package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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
            keypad.setIosBloomDuration(900.0f)
            keypad.setIosBloomClusterFactor(0.60f)
            keypad.setIosBloomOvershoot(0.16f)
            keypad.setIosBloomDamping(0.40f)
            keypad.setIosBloomStiffness(18.0f)
            keypad.setIosBloomRadialSquish(0.28f)
            keypad.setIosBloomVelocityStretch(0.16f)
            keypad.setIosBloomWaveStagger(22.0f)
            keypad.setIosBloomDecoupleText(false)
            keypad.setDragMaxThreshold(0.16f)
            keypad.setDragInitialStiffness(0.22f)
            keypad.setDragResistancePower(1.8f)
            keypad.setDragVolumeStretch(0.11f)
            keypad.setReleaseDuration(600.0f)
            keypad.setReleaseSquishAmp(0.35f)
            keypad.setReleaseOvershoot(2.0f)
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
