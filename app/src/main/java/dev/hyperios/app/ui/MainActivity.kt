package dev.hyperios.app.ui

import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import dev.hyperios.app.R
import dev.hyperios.app.ui.components.SliderThumbTracker
import dev.hyperios.app.ui.glass.LiquidBottomTab
import dev.hyperios.app.ui.glass.LiquidBottomTabs
import dev.hyperios.app.ui.glass.LocalGlassSettings
import dev.hyperios.app.ui.screens.AboutScreen
import dev.hyperios.app.ui.screens.AppAppearanceScreen
import dev.hyperios.app.ui.screens.AppDetailScreen
import dev.hyperios.app.ui.screens.AppLayoutScreen
import dev.hyperios.app.ui.screens.AppTransitionsScreen
import dev.hyperios.app.ui.screens.ControlCenterScreen
import dev.hyperios.app.ui.screens.EngineScreen
import dev.hyperios.app.ui.screens.KeypadButtonsLayoutScreen
import dev.hyperios.app.ui.screens.KeypadDelaysScreen
import dev.hyperios.app.ui.screens.KeypadGlassTuningScreen
import dev.hyperios.app.ui.screens.KeypadIosBloomPhysicsScreen
import dev.hyperios.app.ui.screens.KeypadPadLayoutScreen
import dev.hyperios.app.ui.screens.KeypadPhysicsScreen
import dev.hyperios.app.ui.screens.KeypadReleasePhysicsScreen
import dev.hyperios.app.ui.screens.KeypadScreen
import dev.hyperios.app.ui.screens.KeypadStretchPhysicsScreen
import dev.hyperios.app.ui.screens.LockScreenSheetScreen
import dev.hyperios.app.ui.screens.LockScreenSheetGlassTuningScreen
import dev.hyperios.app.ui.screens.LockScreenSheetLightingScreen
import dev.hyperios.app.ui.screens.LockScreenSheetHomeIndicatorScreen
import dev.hyperios.app.ui.screens.LockscreenStuffsScreen
import dev.hyperios.app.ui.screens.NotificationCenterSheetScreen
import dev.hyperios.app.ui.screens.NotificationCenterSheetGlassTuningScreen
import dev.hyperios.app.ui.screens.NotificationCenterSheetLightingScreen
import dev.hyperios.app.ui.screens.NotificationCenterSheetHomeIndicatorScreen
import dev.hyperios.app.ui.screens.ScreenUnlockCascadeBounceScreen
import dev.hyperios.app.ui.screens.ScreenUnlockCascadeDockScreen
import dev.hyperios.app.ui.screens.ScreenUnlockCascadeGlobalsScreen
import dev.hyperios.app.ui.screens.ScreenUnlockCascadeSettleScreen
import dev.hyperios.app.ui.screens.ScreenUnlockCascadeZDepthScreen
import dev.hyperios.app.ui.screens.ScreenUnlockRadialDockScreen
import dev.hyperios.app.ui.screens.ScreenUnlockRadialKineticsScreen
import dev.hyperios.app.ui.screens.ScreenUnlockScreen
import dev.hyperios.app.ui.screens.SettingsScreen
import dev.hyperios.app.ui.screens.SliderAppearanceScreen
import dev.hyperios.app.ui.screens.SliderPhysicsScreen
import dev.hyperios.app.ui.screens.SystemLauncherStuffsScreen
import dev.hyperios.app.ui.screens.ToggleAppearanceScreen
import dev.hyperios.app.ui.screens.TogglePhysicsScreen
import dev.hyperios.app.ui.screens.TuningScreen
import dev.hyperios.app.ui.state.AppTheme
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceBackground
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
import dev.hyperios.app.ui.theme.LocalIsDarkTheme
import dev.hyperios.app.ui.theme.hyperBounceColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.hyperios.app.ui.glass.DropdownOverlayHost
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        setContent {
            val repository = remember { SettingsRepository(this) }
            HyperiOSApp(repository)
        }
    }
}

@Composable
private fun rememberNavSystemCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val insets = view.rootWindowInsets
    val radiusPx = remember(context, view, insets) {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
        } else {
            null
        }
        val fromRes = if (fromInsets == null) {
            val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
            if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        } else null
        fromInsets ?: fromRes ?: 0
    }
    val dp = (radiusPx / density).dp
    return if (dp > 0.dp) dp else 28.dp
}

@Composable
private fun HyperiOSApp(repository: SettingsRepository) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (repository.appTheme) {
        AppTheme.FollowSystem -> isSystemDark
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    // Update status bar & system navigation bar appearance: transparent bar with matching icon contrast
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
    }

    val colors = hyperBounceColors(repository.appTheme)
    val backdrop = rememberLayerBackdrop()
    val navBackdrop = rememberLayerBackdrop()
    val miuixNavBackdrop = top.yukonga.miuix.kmp.blur.rememberLayerBackdrop()

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val pagerScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    val backStack = remember { mutableStateListOf<Destination>() }
    val scrollStateHolder = remember { dev.hyperios.app.ui.state.SubpageScrollStateHolder() }
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

    val miuixColors = if (isDark) {
        darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF242424),
            surfaceContainerHigh = Color(0xFF2E2E2E),
            surfaceVariant = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF7F7F7),
            surface = Color(0xFFF7F7F7),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE8E8E8),
            surfaceVariant = Color.White
        )
    }
    MiuixTheme(colors = miuixColors) {
        CompositionLocalProvider(
            LocalComponentStyle provides repository.componentStyle,
            LocalIsDarkTheme provides isDark,
            LocalGlassSettings provides repository.globalGlass.snapshot(),
            dev.hyperios.app.ui.glass.LocalMorphPhysics provides repository.morphPhysics.snapshot(),
            dev.hyperios.app.ui.state.LocalSubpageScrollState provides scrollStateHolder
        ) {
            DropdownOverlayHost {
                dev.hyperios.app.ui.glass.CustomValueDialogHost(colors) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                val screenWidth = constraints.maxWidth.toFloat()
                val dragOffset = remember { Animatable(0f) }
                var isPopping by remember { mutableStateOf(false) }
                var activeAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                val popDestination: () -> Unit = {
                    if (backStack.isNotEmpty()) {
                        val removed = backStack.removeAt(backStack.lastIndex)
                        scrollStateHolder.clear(removed.toString())
                    }
                }

                val push: (Destination) -> Unit = { destination ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    activeAnimationJob?.cancel()
                    if (isPopping || dragOffset.value > 0f) {
                        isPopping = false
                        popDestination()
                    }
                    activeAnimationJob = coroutineScope.launch {
                        dragOffset.snapTo(screenWidth)
                        backStack.add(destination)
                        dragOffset.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 380f))
                    }
                }

                val pop: () -> Unit = {
                    if (backStack.isNotEmpty() && !isPopping) {
                        isPopping = true
                        activeAnimationJob = coroutineScope.launch {
                            try {
                                dragOffset.animateTo(screenWidth, spring(dampingRatio = 0.88f, stiffness = 420f))
                                popDestination()
                                dragOffset.snapTo(0f)
                            } finally {
                                isPopping = false
                            }
                        }
                    }
                }

                BackHandler(enabled = backStack.isNotEmpty()) { pop() }

                val useMiuixBottomBar = repository.componentStyle == ComponentStyle.MIUIX && !repository.miuixLiquidGlassBottomBar
                val rootBoxModifier = if (useMiuixBottomBar) {
                    Modifier.fillMaxSize().miuixLayerBackdrop(miuixNavBackdrop)
                } else {
                    Modifier.fillMaxSize().layerBackdrop(navBackdrop)
                }
                Box(rootBoxModifier) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        HyperBounceBackground(colors, Modifier.fillMaxSize())
                    }

                    // 0. The Root Pager - ALWAYS MOUNTED at this stable composition slot!
                    // By remaining mounted permanently, its scroll state, pager state, and layout
                    // positions are 100% preserved during navigation and interactive back gestures.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (backStack.isEmpty()) {
                                    translationX = 0f
                                } else if (backStack.size == 1) {
                                    val progress = (dragOffset.value / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    translationX = -screenWidth * 0.22f * (1f - progress)
                                } else {
                                    translationX = -screenWidth * 0.22f
                                }
                            }
                    ) {
                        ScreenLayer(
                            destination = null,
                            repository = repository,
                            colors = colors,
                            backdrop = backdrop,
                            pagerState = pagerState,
                            onBack = pop,
                            onNavigate = push
                        )
                    }

                    // Interactive subpage layer container (when drilled into 1 or more subpages)
                    if (backStack.isNotEmpty()) {
                        val parentDest = if (backStack.size > 1) backStack[backStack.size - 2] else null
                        val currentDest = backStack.last()
                        val systemCornerRadius = rememberNavSystemCornerRadius()

                        Box(Modifier.fillMaxSize()) {
                            // 1. Parent subpage underneath with parallax (when backStack.size >= 2)
                            if (parentDest != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val progress = (dragOffset.value / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            translationX = -screenWidth * 0.22f * (1f - progress)
                                        }
                                        .background(colors.background)
                                ) {
                                    androidx.compose.runtime.key(parentDest) {
                                        DestinationContent(
                                            destination = parentDest,
                                            repository = repository,
                                            colors = colors,
                                            backdrop = backdrop,
                                            onBack = pop,
                                            onNavigate = push
                                        )
                                    }
                                }
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

                            // 3. Top active subpage following finger drag
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = dragOffset.value
                                        val isSwiping = dragOffset.value > 0f
                                        shadowElevation = if (isSwiping) 20.dp.toPx() else 0f
                                        clip = isSwiping
                                        shape = if (isSwiping) {
                                            AbsoluteRoundedCornerShape(
                                                topLeft = systemCornerRadius,
                                                bottomLeft = systemCornerRadius,
                                                topRight = 0.dp,
                                                bottomRight = 0.dp
                                            )
                                        } else {
                                            RectangleShape
                                        }
                                    }
                                    .background(colors.background)
                                    .pointerInput(currentDest) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                                            // Only exempt the slider's thumb from swipe-to-back gesture.
                                            // Everything else on screen (tuning cards, headers, background) responds to swipe-to-back!
                                            if (SliderThumbTracker.isThumbTouched) {
                                                return@awaitEachGesture
                                            }

                                            // Interruptible: if a previous pop animation is running, cancel and finalize immediately
                                            // so there is zero cooldown between swipe gestures
                                            if (activeAnimationJob?.isActive == true || isPopping) {
                                                activeAnimationJob?.cancel()
                                                isPopping = false
                                                if (dragOffset.value > screenWidth * 0.25f && backStack.isNotEmpty()) {
                                                    popDestination()
                                                }
                                                coroutineScope.launch {
                                                    dragOffset.snapTo(0f)
                                                }
                                            }

                                            val velocityTracker = VelocityTracker()
                                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                                            var totalDx = 0f
                                            var totalDy = 0f
                                            var localDragging = false
                                            val pointerId = down.id

                                            while (true) {
                                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                                if (!change.pressed) {
                                                    break
                                                }

                                                if (SliderThumbTracker.isThumbTouched) {
                                                    break
                                                }

                                                val dx = change.position.x - change.previousPosition.x
                                                val dy = change.position.y - change.previousPosition.y
                                                if (dx == 0f && dy == 0f) continue

                                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                                if (!localDragging) {
                                                    totalDx += dx
                                                    totalDy += dy

                                                    // Effortless horizontal activation on cards, lists, and background
                                                    if (totalDx > 12f && totalDx > kotlin.math.abs(totalDy) * 0.85f) {
                                                        localDragging = true
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            dragOffset.snapTo(totalDx)
                                                        }
                                                    } else if (kotlin.math.abs(totalDy) > 16f || totalDx < -10f) {
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
                                                val velocityX = velocityTracker.calculateVelocity().x
                                                val threshold = screenWidth * 0.22f
                                                val shouldDismiss = dragOffset.value > threshold || velocityX >= 750f

                                                if (shouldDismiss) {
                                                    // Directly execute pop animation without cooldown blocking
                                                    activeAnimationJob = coroutineScope.launch {
                                                        isPopping = true
                                                        try {
                                                            dragOffset.animateTo(
                                                                screenWidth,
                                                                spring(dampingRatio = 0.90f, stiffness = 380f)
                                                            )
                                                            popDestination()
                                                            dragOffset.snapTo(0f)
                                                        } finally {
                                                            isPopping = false
                                                        }
                                                    }
                                                } else {
                                                    activeAnimationJob = coroutineScope.launch {
                                                        dragOffset.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.82f, stiffness = 420f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                androidx.compose.runtime.key(currentDest) {
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
                }

                // Floating bottom-center tab bar (fluidly synchronized with screen transitions)
                if (backStack.size <= 1) {
                    val barProgress = if (backStack.isEmpty()) 1f else (dragOffset.value / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    if (barProgress > 0.001f) {
                        if (useMiuixBottomBar) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .graphicsLayer {
                                        alpha = barProgress
                                        translationY = (1f - barProgress) * 72.dp.toPx()
                                    }
                            ) {
                                FloatingNavigationBar(
                                    color = Color.Transparent,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.textureBlur(
                                        backdrop = miuixNavBackdrop,
                                        shape = RoundedCornerShape(26.dp),
                                        blurRadius = 25f,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = listOf(
                                                BlendColorEntry(
                                                    color = (if (isDark) Color(0xFF242424) else Color(0xFFF6F6F6)).copy(alpha = 0.7f)
                                                )
                                            )
                                        ),
                                        highlight = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
                                    )
                                ) {
                                    FloatingNavigationBarItem(
                                        selected = pagerState.targetPage == 0,
                                        onClick = { selectTab(0) },
                                        icon = MiuixIcons.Home,
                                        label = "Home"
                                    )
                                    FloatingNavigationBarItem(
                                        selected = pagerState.targetPage == 1,
                                        onClick = { selectTab(1) },
                                        icon = MiuixIcons.Settings,
                                        label = "Settings"
                                    )
                                    FloatingNavigationBarItem(
                                        selected = pagerState.targetPage == 2,
                                        onClick = { selectTab(2) },
                                        icon = MiuixIcons.Info,
                                        label = "About"
                                    )
                                }
                            }
                        } else {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp)
                                    .graphicsLayer {
                                        alpha = barProgress
                                        translationY = (1f - barProgress) * 72.dp.toPx()
                                    }
                            ) {
                                Box(Modifier.width(264.dp)) {
                                    val tabItemColor = if (isDark) Color.White else Color.Black
                                    LiquidBottomTabs(
                                        selectedTabIndex = { pagerState.targetPage },
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
                    repository = repository,
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
    CompositionLocalProvider(dev.hyperios.app.ui.state.LocalDestinationKey provides destination.toString()) {
        when (destination) {
        Destination.AppsList -> dev.hyperios.app.ui.screens.AppsListScreen(
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
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.LockScreenSheetGlassTuning -> LockScreenSheetGlassTuningScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.LockScreenSheetLighting -> LockScreenSheetLightingScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.LockScreenSheetHomeIndicator -> LockScreenSheetHomeIndicatorScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.NotificationCenterSheet -> NotificationCenterSheetScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack,
            onNavigate = onNavigate
        )

        Destination.NotificationCenterSheetGlassTuning -> NotificationCenterSheetGlassTuningScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.NotificationCenterSheetLighting -> NotificationCenterSheetLightingScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.NotificationCenterSheetHomeIndicator -> NotificationCenterSheetHomeIndicatorScreen(
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

        Destination.MorphPhysics -> dev.hyperios.app.ui.screens.MorphPhysicsScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.CallerScreen -> dev.hyperios.app.ui.screens.CallerScreenSettings(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.SafeMode -> dev.hyperios.app.ui.screens.SafeModeScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.HapticStudio -> dev.hyperios.app.ui.screens.HapticStudioScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )

        Destination.About -> AboutScreen(
            repository = repository,
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        )
        }
    }
}
