package dev.joseph.hyperbounce.ui.screens

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
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.FeatureCategoryRow
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

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
        subtitle = "iOS style liquid glass aesthetics on lockscreen and Pinpad",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            SectionHeader("Features", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                FeatureCategoryRow(
                    title = "Lock Screen Sheet",
                    description = "Curvature refraction, bevel dispersion & bottom edge lift",
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
                    title = "PIN Pad Physics",
                    description = "Radial bloom kinetics, lens keycaps & custom grid lift",
                    colors = colors,
                    onClick = { onNavigate(Destination.Keypad) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_pinpad),
                            contentDescription = "PIN Pad Physics",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}
