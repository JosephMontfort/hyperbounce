package dev.joseph.hyperbounce.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTab
import dev.joseph.hyperbounce.ui.glass.LiquidBottomTabs
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.screens.AboutScreen
import dev.joseph.hyperbounce.ui.screens.AppAppearanceScreen
import dev.joseph.hyperbounce.ui.screens.AppDetailScreen
import dev.joseph.hyperbounce.ui.screens.AppLayoutScreen
import dev.joseph.hyperbounce.ui.screens.AppTransitionsScreen
import dev.joseph.hyperbounce.ui.screens.ControlCenterScreen
import dev.joseph.hyperbounce.ui.screens.EngineScreen
import dev.joseph.hyperbounce.ui.screens.KeypadButtonsLayoutScreen
import dev.joseph.hyperbounce.ui.screens.KeypadDelaysScreen
import dev.joseph.hyperbounce.ui.screens.KeypadGlassTuningScreen
import dev.joseph.hyperbounce.ui.screens.KeypadIosBloomPhysicsScreen
import dev.joseph.hyperbounce.ui.screens.KeypadPadLayoutScreen
import dev.joseph.hyperbounce.ui.screens.KeypadPhysicsScreen
import dev.joseph.hyperbounce.ui.screens.KeypadReleasePhysicsScreen
import dev.joseph.hyperbounce.ui.screens.KeypadScreen
import dev.joseph.hyperbounce.ui.screens.KeypadStretchPhysicsScreen
import dev.joseph.hyperbounce.ui.screens.LockScreenSheetScreen
import dev.joseph.hyperbounce.ui.screens.LockscreenStuffsScreen
import dev.joseph.hyperbounce.ui.screens.NotificationCenterSheetScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockCascadeBounceScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockCascadeDockScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockCascadeGlobalsScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockCascadeSettleScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockCascadeZDepthScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockRadialDockScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockRadialKineticsScreen
import dev.joseph.hyperbounce.ui.screens.ScreenUnlockScreen
import dev.joseph.hyperbounce.ui.screens.SettingsScreen
import dev.joseph.hyperbounce.ui.screens.SliderAppearanceScreen
import dev.joseph.hyperbounce.ui.screens.SliderPhysicsScreen
import dev.joseph.hyperbounce.ui.screens.SystemLauncherStuffsScreen
import dev.joseph.hyperbounce.ui.screens.ToggleAppearanceScreen
import dev.joseph.hyperbounce.ui.screens.TogglePhysicsScreen
import dev.joseph.hyperbounce.ui.screens.TuningScreen
import dev.joseph.hyperbounce.ui.state.AppTheme
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceBackground
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme
import dev.joseph.hyperbounce.ui.theme.hyperBounceColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val repository = remember { SettingsRepository(this) }
            HyperiOSApp(repository)
        }
    }
}

@Composable
private fun HyperiOSApp(repository: SettingsRepository) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (repository.appTheme) {
        AppTheme.FollowSystem -> isSystemDark
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    // Update status bar icons: dark icons in light mode so they are never blinded
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    val colors = hyperBounceColors(repository.appTheme)
    val backdrop = rememberLayerBackdrop()
    val navBackdrop = rememberLayerBackdrop()

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val pagerScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    val backStack = remember { mutableStateListOf<Destination>() }
    val atRoot = backStack.isEmpty()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val selectTab: (Int) -> Unit = { index ->
        focusManager.clearFocus()
        keyboardController?.hide()
        pagerScope.launch { pagerState.animateScrollToPage(index) }
    }

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        LocalGlassSettings provides repository.globalGlass.snapshot(),
        dev.joseph.hyperbounce.ui.glass.LocalMorphPhysics provides repository.morphPhysics.snapshot()
    ) {
        dev.joseph.hyperbounce.ui.glass.CustomValueDialogHost(colors) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val screenWidth = constraints.maxWidth.toFloat()
                val dragOffset = remember { Animatable(0f) }
                var isPopping by remember { mutableStateOf(false) }

                val push: (Destination) -> Unit = { destination ->
                    coroutineScope.launch {
                        dragOffset.snapTo(screenWidth)
                        backStack.add(destination)
                        dragOffset.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 380f))
                    }
                }

                val pop: () -> Unit = {
                    if (backStack.isNotEmpty() && !isPopping) {
                        isPopping = true
                        coroutineScope.launch {
                            dragOffset.animateTo(screenWidth, spring(dampingRatio = 0.88f, stiffness = 420f))
                            if (backStack.isNotEmpty()) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                            dragOffset.snapTo(0f)
                            isPopping = false
                        }
                    }
                }

                BackHandler(enabled = backStack.isNotEmpty()) { pop() }

                Box(Modifier.fillMaxSize().layerBackdrop(navBackdrop)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        HyperBounceBackground(colors, Modifier.fillMaxSize())
                    }

                    if (backStack.isEmpty()) {
                        // Root Pager
                        ScreenLayer(
                            destination = null,
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            pagerState = pagerState,
                            onBack = pop,
                            onNavigate = push
                        )
                    } else {
                        // Interactive swipe-back container (Keimi architecture)
                        val parentDest = if (backStack.size > 1) backStack[backStack.size - 2] else null
                        val currentDest = backStack.last()

                        Box(Modifier.fillMaxSize()) {
                            // 1. Parent layer underneath with parallax
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val progress = (dragOffset.value / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        translationX = -screenWidth * 0.22f * (1f - progress)
                                    }
                            ) {
                                ScreenLayer(
                                    destination = parentDest,
                                    repository = repository,
                                    colors = colors,
                                    backdrop = backdrop,
                                    pagerState = pagerState,
                                    onBack = pop,
                                    onNavigate = push
                                )
                            }

                            // 2. Dimming scrim over parent
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val progress = (dragOffset.value / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        alpha = (1f - progress) * 0.22f
                                    }
                                    .background(Color.Black)
                            )

                            // 3. Top subpage following finger drag
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = dragOffset.value
                                        shadowElevation = if (dragOffset.value > 0f) 16.dp.toPx() else 0f
                                    }
                                    .background(colors.background)
                                    .pointerInput(currentDest) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            var totalDx = 0f
                                            var totalDy = 0f
                                            var localDragging = false
                                            val pointerId = down.id

                                            while (true) {
                                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                                // If child (LiquidSlider / LiquidToggle thumb) consumed the pointer,
                                                // do NOT intercept or trigger screen swipe!
                                                if (change.isConsumed && !localDragging) {
                                                    break
                                                }

                                                if (!change.pressed) {
                                                    break
                                                }

                                                val dx = change.position.x - change.previousPosition.x
                                                val dy = change.position.y - change.previousPosition.y
                                                totalDx += dx
                                                totalDy += dy

                                                if (!localDragging) {
                                                    // Highly sensitive screen-wide swipe right: ~10px threshold
                                                    if (totalDx > 10f && totalDx > kotlin.math.abs(totalDy) * 1.15f) {
                                                        localDragging = true
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            dragOffset.snapTo(totalDx)
                                                        }
                                                    } else if (kotlin.math.abs(totalDy) > 14f || totalDx < -10f) {
                                                        break
                                                    }
                                                } else {
                                                    change.consume()
                                                    val nextX = (dragOffset.value + dx).coerceAtLeast(0f)
                                                    coroutineScope.launch {
                                                        dragOffset.snapTo(nextX)
                                                    }
                                                }
                                            }

                                            if (localDragging) {
                                                val threshold = screenWidth * 0.32f
                                                coroutineScope.launch {
                                                    if (dragOffset.value > threshold) {
                                                        dragOffset.animateTo(
                                                            screenWidth,
                                                            spring(dampingRatio = 0.88f, stiffness = 420f)
                                                        )
                                                        if (backStack.isNotEmpty()) {
                                                            backStack.removeAt(backStack.lastIndex)
                                                        }
                                                        dragOffset.snapTo(0f)
                                                    } else {
                                                        dragOffset.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.78f, stiffness = 440f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                DestinationContent(
                                    destination = currentDest,
                                    repository = repository,
                                    colors = colors,
                                    backdrop = backdrop,
                                    onBack = pop,
                                    onNavigate = push
                                )
                            }
                        }
                    }
                }

                // Floating bottom-center tab bar
                AnimatedVisibility(
                    visible = atRoot,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                    exit = slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeOut()
                ) {
                    Box(Modifier.width(264.dp)) {
                        val tabItemColor = if (isDark) Color.White else Color.Black
                        LiquidBottomTabs(
                            selectedTabIndex = { pagerState.currentPage },
                            onTabSelected = selectTab,
                            backdrop = navBackdrop,
                            tabsCount = 3,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            LiquidBottomTab(onClick = { selectTab(0) }) {
                                Image(
                                    painter = painterResource(R.drawable.ic_nav_home),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(tabItemColor)
                                )
                                BasicText("Home", style = HyperBounceType.caption.copy(color = tabItemColor))
                            }
                            LiquidBottomTab(onClick = { selectTab(1) }) {
                                Image(
                                    painter = painterResource(R.drawable.ic_nav_settings),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(tabItemColor)
                                )
                                BasicText("Settings", style = HyperBounceType.caption.copy(color = tabItemColor))
                            }
                            LiquidBottomTab(onClick = { selectTab(2) }) {
                                Image(
                                    painter = painterResource(R.drawable.ic_nav_about),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(tabItemColor)
                                )
                                BasicText("About", style = HyperBounceType.caption.copy(color = tabItemColor))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenLayer(
    destination: Destination?,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    if (destination == null) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TuningScreen(
                    repository = repository,
                    colors = colors,
                    backdrop = backdrop,
                    onNavigate = onNavigate
                )

                1 -> SettingsScreen(
                    repository = repository,
                    colors = colors,
                    backdrop = backdrop,
                    onNavigate = onNavigate
                )

                else -> AboutScreen(
                    colors = colors,
                    backdrop = backdrop,
                    onBack = null
                )
            }
        }
    } else {
        DestinationContent(
            destination = destination,
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun DestinationContent(
    destination: Destination,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    when (destination) {
        Destination.AppsList -> dev.joseph.hyperbounce.ui.screens.AppsListScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        is Destination.AppDetail -> AppDetailScreen(
            app = destination.app,
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        is Destination.AppAppearance -> AppAppearanceScreen(
            app = destination.app,
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        is Destination.AppLayout -> AppLayoutScreen(
            app = destination.app,
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.Engine -> EngineScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.ToggleAppearance -> ToggleAppearanceScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.TogglePhysics -> TogglePhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.SliderAppearance -> SliderAppearanceScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.SliderPhysics -> SliderPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.LockscreenStuffs -> LockscreenStuffsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.LockScreenSheet -> LockScreenSheetScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.NotificationCenterSheet -> NotificationCenterSheetScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.Keypad -> KeypadScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.KeypadDelays -> KeypadDelaysScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadPhysics -> KeypadPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.KeypadStretchPhysics -> KeypadStretchPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadReleasePhysics -> KeypadReleasePhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadIosBloomPhysics -> KeypadIosBloomPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadGlassTuning -> KeypadGlassTuningScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadButtonsLayout -> KeypadButtonsLayoutScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.KeypadPadLayout -> KeypadPadLayoutScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.SystemLauncherStuffs -> SystemLauncherStuffsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.ScreenUnlock -> ScreenUnlockScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.ScreenUnlockRadialKinetics -> ScreenUnlockRadialKineticsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockRadialDock -> ScreenUnlockRadialDockScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockCascadeGlobals -> ScreenUnlockCascadeGlobalsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockCascadeDock -> ScreenUnlockCascadeDockScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockCascadeZDepth -> ScreenUnlockCascadeZDepthScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockCascadeBounce -> ScreenUnlockCascadeBounceScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ScreenUnlockCascadeSettle -> ScreenUnlockCascadeSettleScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.AppTransitions -> AppTransitionsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.ControlCenter -> ControlCenterScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.MorphPhysics -> dev.joseph.hyperbounce.ui.screens.MorphPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.CallerScreen -> dev.joseph.hyperbounce.ui.screens.CallerScreenSettings(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.SafeMode -> dev.joseph.hyperbounce.ui.screens.SafeModeScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.HapticStudio -> dev.joseph.hyperbounce.ui.screens.HapticStudioScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.About -> AboutScreen(
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )
    }
}
