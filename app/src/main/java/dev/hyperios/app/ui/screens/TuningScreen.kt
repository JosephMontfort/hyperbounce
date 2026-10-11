package dev.hyperios.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.R
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.ChevronIcon
import dev.hyperios.app.ui.components.FeatureCategoryRow
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.HyperLightSearchBar
import dev.hyperios.app.ui.components.HyperLightMiuixSearchBar
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsClickableRow
import dev.hyperios.app.ui.search.SearchHighlightState
import dev.hyperios.app.ui.search.SearchIndex
import dev.hyperios.app.ui.state.SettingsRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import dev.hyperios.app.ui.state.XposedServiceBridge
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType

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

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isSearching = searchQuery.isNotBlank()
    val searchResults = remember(searchQuery) {
        if (isSearching) SearchIndex.search(searchQuery) else emptyList()
    }

    RootScaffold(
        title = "HyperiOS",
        subtitle = "",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onNavigate(Destination.About)
        }
    ) {
        val componentStyle = LocalComponentStyle.current
        if (componentStyle == ComponentStyle.MIUIX) {
            HyperLightMiuixSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(bottom = 12.dp),
                placeholder = "Search"
            )
        } else {
            // HyperLight-style Search Bar with increased bounce
            HyperLightSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                colors = colors,
                modifier = Modifier.padding(bottom = 12.dp),
                placeholder = "Search"
            )
        }

        val isModuleActivated = XposedServiceBridge.isBoundState.value

        if (!isModuleActivated) {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicText(
                        text = "This module is not activated",
                        style = HyperBounceType.headline.copy(color = colors.accent)
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        text = "Please enable this module in LSPosed and restart this app.",
                        style = HyperBounceType.subheadline.copy(color = colors.secondaryLabel)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isSearching) {
            SectionHeader("SEARCH RESULTS (${searchResults.size})", colors)
            if (searchResults.isNotEmpty()) {
                GlassCard(
                    backdrop,
                    Modifier
                        .padding(horizontal = 16.dp)
                        .alpha(if (isModuleActivated) 1f else 0.45f)
                ) {
                    searchResults.forEachIndexed { index, result ->
                        if (index > 0) {
                            RowDivider(colors.separator, insetStart = 16.dp)
                        }
                        SettingsClickableRow(
                            title = result.title,
                            subtitle = result.category,
                            colors = colors,
                            trailing = {
                                ChevronIcon(
                                    color = colors.tertiaryLabel,
                                    size = 11.dp,
                                    strokeWidth = 1.8.dp,
                                    pointingLeft = false
                                )
                            },
                            onClick = {
                                if (isModuleActivated) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    searchQuery = ""
                                    SearchHighlightState.highlight(result.highlightKey, result.scrollIndex)
                                    onNavigate(result.destination)
                                }
                            }
                        )
                    }
                }
            } else {
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "No tuning options found",
                            style = HyperBounceType.body.copy(color = colors.secondaryLabel)
                        )
                    }
                }
            }
        } else {
            GlassCard(
                backdrop,
                Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(if (isModuleActivated) 1f else 0.45f)
            ) {
                features.forEachIndexed { index, feature ->
                    if (index > 0) {
                        RowDivider(colors.separator, insetStart = 72.dp)
                    }
                    FeatureCategoryRow(
                        title = feature.title,
                        description = feature.description,
                        colors = colors,
                        onClick = {
                            if (isModuleActivated) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                searchQuery = ""
                                onNavigate(feature.destination)
                            }
                        },
                        icon = feature.icon
                    )
                }
            }
        }
    }
}
