package dev.joseph.hyperbounce.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.ui.components.GearIcon
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTab
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTabs
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.screens.AboutScreen
import dev.joseph.hyperbounce.ui.screens.AppDetailScreen
import dev.joseph.hyperbounce.ui.screens.EngineScreen
import dev.joseph.hyperbounce.ui.screens.SettingsScreen
import dev.joseph.hyperbounce.ui.screens.TuningScreen
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import dev.joseph.hyperbounce.ui.theme.hyperBounceColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsRepository(this) }
            HyperBounceApp(repository)
        }
    }
}

@Composable
private fun HyperBounceApp(repository: SettingsRepository) {
    val colors = hyperBounceColors()
    val backdrop = rememberLayerBackdrop()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var rootTabIndex by remember { mutableStateOf(0) }
    var subDestination by remember { mutableStateOf<Destination?>(null) }
    val navigationScope = rememberCoroutineScope()

    // Tuning and Settings are the actual root pages. They live together in one horizontal
    // pager, so a swipe changes the page rather than pushing Settings onto Tuning's stack.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> rootTabIndex = page.coerceIn(0, 1) }
    }

    fun selectRootTab(index: Int) {
        if (subDestination != null) subDestination = null
        if (pagerState.currentPage != index) {
            // Keep the bar, pager and swipe position as one state machine.
            navigationScope.launch {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    BackHandler(enabled = subDestination != null) {
        subDestination = null
    }

    CompositionLocalProvider(LocalGlassSettings provides repository.globalGlass.snapshot()) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            // Only this plain background render node becomes the source backdrop. None of the
            // glass consumers are descendants of this layer, preventing recursive self-capture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.background,
                                colors.wallpaperTop.copy(alpha = if (colors.isDark) 0.28f else 0.16f),
                                colors.background,
                                colors.wallpaperBottom.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
                            )
                        )
                    )
                    .layerBackdrop(backdrop)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = subDestination == null,
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> TuningScreen(
                        repository = repository,
                        colors = colors,
                        backdrop = backdrop,
                        onNavigate = { subDestination = it }
                    )
                    else -> SettingsScreen(
                        repository = repository,
                        colors = colors,
                        backdrop = backdrop,
                        onNavigate = { subDestination = it }
                    )
                }
            }

            // Pushed detail screens are deliberately separate from the two root pages.
            if (subDestination != null) {
                AnimatedContent(
                    targetState = subDestination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = 340f)) { it } + fadeIn())
                            .togetherWith(androidx.compose.animation.slideOutHorizontally(spring(dampingRatio = 0.88f, stiffness = 340f)) { -it / 5 } + fadeOut())
                    },
                    label = "subscreenTransition"
                ) { destination ->
                    when (destination) {
                        is Destination.AppDetail -> AppDetailScreen(
                            app = destination.app,
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onBack = { subDestination = null }
                        )
                        Destination.Engine -> EngineScreen(
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onBack = { subDestination = null }
                        )
                        Destination.About -> AboutScreen(
                            colors = colors,
                            backdrop = backdrop,
                            onBack = { subDestination = null }
                        )
                        Destination.Tuning, Destination.Settings, null -> Unit
                    }
                }
            }

            AnimatedVisibility(
                visible = subDestination == null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 16.dp),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                exit = slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeOut()
            ) {
                Box(Modifier.width(200.dp)) {
                    LiquidBottomTabs(
                        selectedTabIndex = rootTabIndex,
                        onTabSelected = ::selectRootTab,
                        backdrop = backdrop,
                        tabsCount = 2
                    ) {
                        LiquidBottomTab(onClick = { selectRootTab(0) }) {
                            SlidersIcon(color = Color.White, size = 22.dp)
                            BasicText("Tuning", style = HyperBounceType.caption.copy(color = Color.White))
                        }
                        LiquidBottomTab(onClick = { selectRootTab(1) }) {
                            GearIcon(color = Color.White, size = 22.dp)
                            BasicText("Settings", style = HyperBounceType.caption.copy(color = Color.White))
                        }
                    }
                }
            }
        }
    }
}
