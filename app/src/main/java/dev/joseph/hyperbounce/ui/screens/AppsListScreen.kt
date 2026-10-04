package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.FeatureCategoryRow
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun AppsListScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    ScreenScaffold(
        title = "Floating Navigation Bar",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                FeatureCategoryRow(
                    title = "Instagram",
                    description = "Liquid Glass Floating Bottom Bar in Instagram",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.Instagram)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_instagram),
                            contentDescription = "Instagram",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "WhatsApp",
                    description = "Liquid Glass Floating Bottom Bar in WhatsApp",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.WhatsApp)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_whatsapp),
                            contentDescription = "WhatsApp",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "YouTube",
                    description = "Liquid Glass Floating Bottom Bar in YouTube",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.YouTube)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_youtube),
                            contentDescription = "YouTube",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}
