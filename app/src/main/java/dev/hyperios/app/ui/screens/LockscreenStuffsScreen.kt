package dev.hyperios.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.R
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.FeatureCategoryRow
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors

@Composable
fun LockscreenStuffsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    ScreenScaffold(
        title = "LockScreen Stuffs",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                FeatureCategoryRow(
                    title = "Lock Screen Sheet",
                    description = "Curvature refraction, bevel dispersion & home bar pill",
                    colors = colors,
                    onClick = { onNavigate(Destination.LockScreenSheet) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_lockscreen_sheet),
                            contentDescription = "Lock Screen Sheet",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "PIN Pad",
                    description = "Radial bloom kinetics, lens keycaps & custom grid lift",
                    colors = colors,
                    onClick = { onNavigate(Destination.Keypad) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_pinpad),
                            contentDescription = "PIN Pad",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}
