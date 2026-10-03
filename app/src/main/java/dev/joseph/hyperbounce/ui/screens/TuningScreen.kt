package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.FeatureCategoryRow
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.HyperLightSearchBar
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

private data class FeatureItem(
    val title: String,
    val description: String,
    val destination: Destination,
    val icon: @Composable () -> Unit
)

@Composable
fun TuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val features = remember {
        listOf(
            FeatureItem(
                title = "Floating Navigation Bar",
                description = "Liquid Glass Floating navigation bar in Instagram, WhatsApp and Youtube",
                destination = Destination.AppsList,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_navbar),
                        contentDescription = "Floating Navigation Bar",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "Toggles and Sliders",
                description = "iOS 26 style toggles and sliders in system apps",
                destination = Destination.Engine,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_toggles_sliders),
                        contentDescription = "Toggles and Sliders",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "LockScreen Stuffs",
                description = "iOS style liquid glass aesthetics on lockscreen and Pinpad",
                destination = Destination.LockscreenStuffs,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_lockscreen),
                        contentDescription = "LockScreen Stuffs",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "System Launcher Stuffs",
                description = "App transitions and Screen Unlock animation tuning",
                destination = Destination.SystemLauncherStuffs,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_launcher),
                        contentDescription = "System Launcher Stuffs",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "Notification Centre",
                description = "iOS 26 inspired aesthetics on Notification centre",
                destination = Destination.NotificationCenterSheet,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_notification),
                        contentDescription = "Notification Centre",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "Control Centre",
                description = "Enhanced Control centre customisation",
                destination = Destination.ControlCenter,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_control_center),
                        contentDescription = "Control Centre",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "Dialer and Caller Screen",
                description = "Swipe gestures on dialer and iOS style full screen incoming calls",
                destination = Destination.CallerScreen,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_dialer),
                        contentDescription = "Dialer and Caller Screen",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            ),
            FeatureItem(
                title = "Haptic Studio",
                description = "Compose synced haptics for music to be set as custom Ringtones",
                destination = Destination.HapticStudio,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_feature_haptic_studio),
                        contentDescription = "Haptic Studio",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        )
    }

    val filteredFeatures = remember(searchQuery, features) {
        if (searchQuery.isBlank()) {
            features
        } else {
            val q = searchQuery.trim().lowercase()
            features.filter {
                it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
            }
        }
    }

    RootScaffold(
        title = "HyperiOS",
        subtitle = "",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = { onNavigate(Destination.About) }
    ) {
        // HyperLight-style Search Bar with increased bounce
        HyperLightSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            colors = colors,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SectionHeader("Features", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            filteredFeatures.forEachIndexed { index, feature ->
                if (index > 0) {
                    RowDivider(colors.separator, insetStart = 72.dp)
                }
                FeatureCategoryRow(
                    title = feature.title,
                    description = feature.description,
                    colors = colors,
                    onClick = { onNavigate(feature.destination) },
                    icon = feature.icon
                )
            }
        }
    }
}
