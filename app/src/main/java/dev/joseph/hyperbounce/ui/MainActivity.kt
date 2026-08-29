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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTab
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTabs
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.screens.AboutScreen
import dev.joseph.hyperbounce.ui.screens.AppDetailScreen
import dev.joseph.hyperbounce.ui.screens.EngineScreen
import dev.joseph.hyperbounce.ui.screens.KeypadScreen
import dev.joseph.hyperbounce.ui.screens.SettingsScreen
import dev.joseph.hyperbounce.ui.screens.TuningScreen
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceBackground
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import dev.joseph.hyperbounce.ui.theme.hyperBounceColors
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

    // Tuning (page 0) and Settings (page 1) are true swipeable pager pages, not pushed
    // stack destinations — swiping between them, and the floating tab bar below, are both
    // driven by this one pagerState so they can never fall out of sync.
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val pagerScope = rememberCoroutineScope()

    // AppDetail/Engine/About are genuine drill-down subpages, layered on top of the pager
    // via their own small back stack.
    val backStack = remember { mutableStateListOf<Destination>() }
    var isPush by remember { mutableStateOf(true) }
    val atRoot = backStack.isEmpty()

    val push: (Destination) -> Unit = { destination ->
        isPush = true
        backStack.add(destination)
    }
    val pop: () -> Unit = {
        if (backStack.isNotEmpty()) {
            isPush = false
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val selectTab: (Int) -> Unit = { index ->
        pagerScope.launch { pagerState.animateScrollToPage(index) }
    }

    BackHandler(enabled = backStack.isNotEmpty()) { pop() }

    CompositionLocalProvider(LocalGlassSettings provides repository.globalGlass.snapshot()) {
        Box(Modifier.fillMaxSize()) {
            // IMPORTANT: layerBackdrop tags ONLY this background's own render node as the
            // thing every drawBackdrop() consumer samples from. It must never wrap a Box
            // that also contains those same glass consumers as children — doing so puts
            // the consumers inside the very node being captured, so each captured frame
            // includes the previous frame's glass rendering, which includes the capture
            // again, and so on: an unbounded recursive RenderNode graph that crashes
            // RenderThread with a stack-overflow SIGSEGV. The background and the screen
            // content below must be siblings under a plain (non-backdrop) Box, exactly as
            // the library's own catalog HomeContent does it.
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                HyperBounceBackground(colors, Modifier.fillMaxSize())
            }

            if (atRoot) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> TuningScreen(
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onNavigate = push
                        )

                        else -> SettingsScreen(
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onNavigate = push
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = backStack.last(),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forwardSpring = spring<androidx.compose.ui.unit.IntOffset>(
                            dampingRatio = 0.88f,
                            stiffness = 340f
                        )
                        if (isPush) {
                            (slideInHorizontally(forwardSpring) { fullWidth -> fullWidth } + fadeIn())
                                .togetherWith(
                                    slideOutHorizontally(forwardSpring) { fullWidth -> -fullWidth / 5 } + fadeOut()
                                )
                        } else {
                            (slideInHorizontally(forwardSpring) { fullWidth -> -fullWidth / 5 } + fadeIn())
                                .togetherWith(
                                    slideOutHorizontally(forwardSpring) { fullWidth -> fullWidth } + fadeOut()
                                )
                        }
                    },
                    label = "screenTransition"
                ) { destination ->
                    when (destination) {
                        is Destination.AppDetail -> AppDetailScreen(
                            app = destination.app,
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onBack = pop
                        )

                        Destination.Engine -> EngineScreen(
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onBack = pop,
                            onNavigate = push
                        )

                        Destination.Keypad -> KeypadScreen(
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            onBack = pop
                        )

                        Destination.About -> AboutScreen(
                            colors = colors,
                            backdrop = backdrop,
                            onBack = pop
                        )
                    }
                }
            }

            // Floating bottom-center tab bar — hidden while drilled into a subpage,
            // matching iOS tab bars hiding on push and reappearing on pop back to a root
            // tab. selectedTabIndex reads pagerState.currentPage directly (a single source
            // of truth), so the pill can never drift out of sync with either a swipe or a
            // tap.
            AnimatedVisibility(
                visible = atRoot,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 16.dp),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                exit = slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeOut()
            ) {
                Box(Modifier.width(196.dp)) {
                    LiquidBottomTabs(
                        selectedTabIndex = { pagerState.currentPage },
                        onTabSelected = selectTab,
                        backdrop = backdrop,
                        tabsCount = 2
                    ) {
                        LiquidBottomTab(onClick = { selectTab(0) }) {
                            TabIcon(SlidersIcon)
                            BasicText("Tuning", style = HyperBounceType.caption.copy(color = Color.White))
                        }
                        LiquidBottomTab(onClick = { selectTab(1) }) {
                            TabIcon(Icons.Filled.Settings)
                            BasicText("Settings", style = HyperBounceType.caption.copy(color = Color.White))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(icon: ImageVector) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(Color.White),
        modifier = Modifier.size(20.dp)
    )
}
