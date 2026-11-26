package dev.hyperios.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.R
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.FeatureCategoryRow
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.state.NavApp
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors

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

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "Facebook",
                    description = "Liquid Glass Floating Bottom Bar in Facebook",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.Facebook)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_facebook),
                            contentDescription = "Facebook",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "TikTok",
                    description = "Liquid Glass Floating Bottom Bar in TikTok",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.TikTok)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_tiktok),
                            contentDescription = "TikTok",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                RowDivider(colors.separator, insetStart = 72.dp)

                FeatureCategoryRow(
                    title = "X (Twitter)",
                    description = "Liquid Glass Floating Bottom Bar in X (Twitter)",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppDetail(NavApp.Twitter)) },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_sub_twitter),
                            contentDescription = "X (Twitter)",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}
