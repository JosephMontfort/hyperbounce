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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val backStack = remember { mutableStateListOf<Destination>(Destination.Tuning) }
    var isPush by remember { mutableStateOf(true) }
    val current = backStack.last()
    val atRoot = backStack.size == 1
    val rootTabIndex = if (backStack.first() == Destination.Settings) 1 else 0

    val push: (Destination) -> Unit = { destination ->
        isPush = true
        backStack.add(destination)
    }
    val pop: () -> Unit = {
        if (backStack.size > 1) {
            isPush = false
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val selectRootTab: (Int) -> Unit = { index ->
        val target = if (index == 1) Destination.Settings else Destination.Tuning
        if (backStack.first() != target || backStack.size > 1) {
            isPush = index > rootTabIndex
            backStack.clear()
            backStack.add(target)
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    CompositionLocalProvider(LocalGlassSettings provides repository.globalGlass.snapshot()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // IMPORTANT: layerBackdrop tags ONLY this plain-fill Box's own render node as
            // the thing every drawBackdrop() consumer samples from. It must never wrap a
            // Box that also contains those same glass consumers as children — doing so
            // puts the consumers inside the very node being captured, so each captured
            // frame includes the previous frame's glass rendering, which includes the
            // capture again, and so on: an unbounded recursive RenderNode graph that
            // crashes RenderThread with a stack-overflow SIGSEGV. The background and the
            // screen content below must be siblings under a plain (non-backdrop) Box,
            // exactly as the library's own catalog HomeContent does it.
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

            AnimatedContent(
                targetState = current,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(rootTabIndex, atRoot) {
                        var totalDx = 0f
                        var totalDy = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDx = 0f; totalDy = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDx += dragAmount
                                totalDy += kotlin.math.abs(change.position.y - change.previousPosition.y)
                            },
                            onDragEnd = {
                                if (atRoot && kotlin.math.abs(totalDx) > 80f && kotlin.math.abs(totalDx) > totalDy * 1.25f) {
                                    when {
                                        totalDx < 0f && rootTabIndex == 0 -> selectRootTab(1)
                                        totalDx > 0f && rootTabIndex == 1 -> selectRootTab(0)
                                    }
                                }
                            }
                        )
                    },
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
                    Destination.Tuning -> TuningScreen(
                        repository = repository,
                        colors = colors,
                        backdrop = backdrop,
                        onNavigate = push
                    )

                    Destination.Settings -> SettingsScreen(
                        repository = repository,
                        colors = colors,
                        backdrop = backdrop,
                        onNavigate = push
                    )

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
                        onBack = pop
                    )

                    Destination.About -> AboutScreen(
                        colors = colors,
                        backdrop = backdrop,
                        onBack = pop
                    )
                }
            }

            // Floating bottom-left tab bar — hidden while drilled into a subpage, matching
            // iOS tab bars hiding on push and reappearing on pop back to a root tab.
            AnimatedVisibility(
                visible = atRoot,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 16.dp),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                exit = slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeOut()
            ) {
                Box(Modifier.width(184.dp)) {
                    LiquidBottomTabs(
                        selectedTabIndex = rootTabIndex,
                        onTabSelected = selectRootTab,
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
