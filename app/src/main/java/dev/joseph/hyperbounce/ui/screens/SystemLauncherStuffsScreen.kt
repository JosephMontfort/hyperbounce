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
fun SystemLauncherStuffsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    ScreenScaffold(
        title = "System Launcher Stuffs",
        subtitle = "App transitions and Screen Unlock animation tuning",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            SectionHeader("Features", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                FeatureCategoryRow(
                    title = "App Transitions",
                    description = "Activity launch scale, swipe dismiss & stretch physics",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppTransitions) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_feature_launcher),
                            contentDescription = "App Transitions",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "Screen Unlock",
                    description = "Unlock launch velocity, radial settle & Z-depth bounce",
                    colors = colors,
                    onClick = { onNavigate(Destination.ScreenUnlock) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF5856D6)),
                            contentAlignment = Alignment.Center
                        ) {
                            SlidersIcon(color = Color.White, size = 20.dp)
                        }
                    }
                )
            }
        }
    }
}
