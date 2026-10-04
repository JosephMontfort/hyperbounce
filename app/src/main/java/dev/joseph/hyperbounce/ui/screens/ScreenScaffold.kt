package dev.joseph.hyperbounce.ui.screens

/*
 * Every subpage in this app (AppDetail x3, Engine, About) shares this scaffold: a
 * floating circular back button (jelly touch-and-drag) pinned to the top-start corner, a
 * large iOS-style title that collapses into the top bar as the user scrolls, a
 * progressive blur (ui/glass/ProgressiveBlurHeader.kt) behind that top bar so scrolling
 * content dissolves into the blur rather than being hard-clipped by it, and an optional
 * top-right expandable glass menu matching the main screen's exact liquid glass appearance.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.ui.components.CircularGlassBackButton
import dev.joseph.hyperbounce.ui.components.MenuRow
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.DropdownOverlayHost
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.glass.ProgressiveBlurHeader
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.joseph.hyperbounce.ui.state.ComponentStyle
import dev.joseph.hyperbounce.ui.state.LocalComponentStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import dev.joseph.hyperbounce.ui.glass.deadEndHaptic
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch

object SubpageScrollRegistry {
    private val scrollPositions = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    fun getSavedPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: Pair(0, 0)
    }

    fun savePosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun clear() {
        scrollPositions.clear()
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    subtitle: String? = null,
    onResetPage: (() -> Unit)? = null,
    restartAppTitle: String? = null,
    restartAppSubtitle: String? = null,
    onRestartApp: (() -> Unit)? = null,
    content: LazyListScope.(contentBackdrop: Backdrop) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollKey = remember(title, subtitle) { "subpage_${title}_${subtitle ?: ""}" }
    val savedPos = remember(scrollKey) { SubpageScrollRegistry.getSavedPosition(scrollKey) }
    val lazyListState = rememberSaveable(scrollKey, saver = LazyListState.Saver) {
        LazyListState(savedPos.first, savedPos.second)
    }

    androidx.compose.runtime.LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        SubpageScrollRegistry.savePosition(
            scrollKey,
            lazyListState.firstVisibleItemIndex,
            lazyListState.firstVisibleItemScrollOffset
        )
    }

    androidx.compose.runtime.DisposableEffect(scrollKey, lazyListState) {
        onDispose {
            SubpageScrollRegistry.savePosition(
                scrollKey,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        }
    }

    val targetIndex = dev.joseph.hyperbounce.ui.search.SearchHighlightState.targetScrollIndex
    androidx.compose.runtime.LaunchedEffect(targetIndex) {
        if (targetIndex != null && targetIndex >= 0) {
            kotlinx.coroutines.delay(220)
            try {
                lazyListState.animateScrollToItem(targetIndex)
            } catch (_: Exception) {}
        }
    }

    val scope = rememberCoroutineScope()
    val contentBackdrop = rememberLayerBackdrop()
    val glass = LocalGlassSettings.current

    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        val scrollBehavior = MiuixScrollBehavior()
        val isDark = dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme.current
        val dialogHost = dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost.current
        var menuExpanded by remember { mutableStateOf(false) }

        DropdownOverlayHost {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color.Black else MiuixTheme.colorScheme.background),
                topBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ProgressiveBlurHeader(
                            backdrop = contentBackdrop,
                            tintColor = if (isDark) Color.Black else MiuixTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp)
                        )
                        TopAppBar(
                            title = title,
                            largeTitle = title,
                            subtitle = subtitle ?: "",
                            color = Color.Transparent,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Image(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = "Back",
                                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                                    )
                                }
                            },
                            actions = {
                                if (onResetPage != null || onRestartApp != null) {
                                    Box {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Image(
                                                imageVector = MiuixIcons.More,
                                                contentDescription = "More",
                                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                                            )
                                        }
                                        val menuItems = remember(onResetPage, onRestartApp, restartAppTitle) {
                                            val items = mutableListOf<DropdownItem>()
                                            if (onResetPage != null) {
                                                items.add(
                                                    DropdownItem(
                                                        text = "Reset All",
                                                        onClick = {
                                                            menuExpanded = false
                                                            dialogHost.showConfirmation(
                                                                title = "Reset All Settings?",
                                                                message = "This will restore all switches, sliders, and animation curves on this screen back to their default values.",
                                                                confirmText = "Reset",
                                                                confirmColor = Color(0xFFFF453A),
                                                                onConfirm = {
                                                                    SubpageScrollRegistry.savePosition(scrollKey, 0, 0)
                                                                    scope.launch { lazyListState.scrollToItem(0) }
                                                                    onResetPage()
                                                                    android.widget.Toast.makeText(context, "Settings restored to defaults", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        }
                                                    )
                                                )
                                            }
                                            if (onRestartApp != null && restartAppTitle != null) {
                                                items.add(
                                                    DropdownItem(
                                                        text = restartAppTitle,
                                                        onClick = {
                                                            menuExpanded = false
                                                            dialogHost.showConfirmation(
                                                                title = "${restartAppTitle}?",
                                                                message = "This will force terminate and restart ${restartAppSubtitle ?: "the target process"}.",
                                                                confirmText = "Restart",
                                                                confirmColor = colors.accentBlue,
                                                                onConfirm = {
                                                                    onRestartApp()
                                                                }
                                                            )
                                                        }
                                                    )
                                                )
                                            }
                                            DropdownEntry(items = items)
                                        }
                                        WindowDropdownPopup(
                                            entry = menuItems,
                                            show = menuExpanded,
                                            onDismiss = { menuExpanded = false },
                                            onDismissFinished = {},
                                            maxHeight = 240.dp,
                                            dropdownColors = DropdownDefaults.dropdownColors()
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentBackdrop)
                        .background(if (isDark) Color.Black else MiuixTheme.colorScheme.background)
                        .deadEndHaptic(glass.hapticsEnabled)
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .safeDrawingPadding(),
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 64.dp
                    )
                ) {
                    content(contentBackdrop)
                }
            }
        }
        return
    }
    val collapseProgress by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) 1f
            else (lazyListState.firstVisibleItemScrollOffset / 220f).fastCoerceIn(0f, 1f)
        }
    }
    // A separate, local backdrop tagging just this screen's scrolling content — kept apart
    // from the app-wide `backdrop` (which the toggles/sliders/menus sample) so the
    // progressive blur below reflects the actual list content passing underneath it, not
    // the plain wallpaper.

    val menuAnimationScope = rememberCoroutineScope()
    val menuProgress = remember { Animatable(0f) }
    val menuExpanded = menuProgress.value > 0.05f || menuProgress.targetValue > 0.5f
    var dialogProgress by remember { androidx.compose.runtime.mutableStateOf(0f) }

    fun closeMenu() {
        menuAnimationScope.launch {
            menuProgress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        DropdownOverlayHost {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                    .deadEndHaptic(glass.hapticsEnabled)
                    .safeDrawingPadding(),
                state = lazyListState
            ) {
                item {
                    Spacer(Modifier.height(64.dp))
                    BasicText(
                        title,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .graphicsLayer {
                                alpha = (1f - (collapseProgress * 2.2f)).coerceIn(0f, 1f)
                                translationY = -collapseProgress * 8.dp.toPx()
                            },
                        style = HyperBounceType.largeTitle.copy(color = colors.label)
                    )
                    Spacer(Modifier.height(16.dp))
                }
                content(contentBackdrop)
                item {
                    Spacer(Modifier.height(56.dp))
                }
            }

            ProgressiveBlurHeader(
                backdrop = contentBackdrop,
                tintColor = colors.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .graphicsLayer { alpha = collapseProgress }
            )

            // Dedicated top-left pinned back button
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 6.dp)
            ) {
                CircularGlassBackButton(onClick = onBack, backdrop = backdrop)
            }

            // Centered collapsed title, fades in at center top as the large title scrolls away
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 72.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    title,
                    modifier = Modifier.graphicsLayer {
                        alpha = ((collapseProgress - 0.55f) * 2.2f).coerceIn(0f, 1f)
                    },
                    style = HyperBounceType.headline.copy(color = colors.label),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Full-screen scrim to dismiss the menu when tapping outside it
            if (menuExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { closeMenu() }
                        }
                )
            }

            // Dedicated top-right pinned expandable glass menu overlay (full-screen overlay with edge-to-edge dimming)
            if (onResetPage != null || onRestartApp != null) {
                val glass = LocalGlassSettings.current
                val isDark = dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme.current
                val menuContentColor = if (isDark) Color.White else Color.Black
                val menuSecondaryColor = menuContentColor.copy(alpha = 0.7f)

                ExpandableGlassMenu(
                    animatableProgress = menuProgress,
                    animationPreset = MenuAnimationPreset.Bouncy,
                    alignment = MenuAlignment.TopTrailing,
                    backdrop = rememberCombinedBackdrop(backdrop, contentBackdrop),
                    isGlassEnabled = true,
                    isHapticsEnabled = glass.hapticsEnabled,
                    cornerRadius = 22.dp,
                    blurRadius = glass.blurDp,
                    refractionHeight = glass.refractionHeightDp,
                    refractionAmount = glass.refractionAmountDp,
                    chromaticAberration = glass.chromaticAberration,
                    horizontalOffset = 0f,
                    verticalOffset = 10f,
                    modifier = Modifier.fillMaxSize().zIndex(10f),
                    contentPadding = PaddingValues(top = 8.dp, end = 16.dp),
                    labelSize = Size(44f, 44f),
                    onDialogProgressChange = { dialogProgress = it },
                        onRowSelected = { index, showDialog ->
                            if (index == 0 && onResetPage != null) {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Reset All Settings?",
                                        message = "This will restore all switches, sliders, and animation curves on this screen back to their default values.",
                                        confirmText = "Reset",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = {
                                            onResetPage()
                                            android.widget.Toast.makeText(context, "Settings restored to defaults", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                )
                                false // Morph into confirmation dialog
                            } else if ((index == 1 || (index == 0 && onResetPage == null)) && onRestartApp != null) {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "${restartAppTitle ?: "Restart App"}?",
                                        message = "This will force terminate and restart ${restartAppSubtitle ?: "the target process"}.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentBlue,
                                        onConfirm = {
                                            onRestartApp()
                                        }
                                    )
                                )
                                false // Morph into confirmation dialog
                            } else {
                                true
                            }
                        },
                        label = {
                            Canvas(Modifier.size(20.dp)) {
                                val x = size.width / 2f
                                val r = size.minDimension * 0.075f
                                val y1 = size.height * 0.25f
                                val y2 = size.height * 0.50f
                                val y3 = size.height * 0.75f
                                val dotColor = menuContentColor
                                drawCircle(dotColor, r, Offset(x, y1))
                                drawCircle(dotColor, r, Offset(x, y2))
                                drawCircle(dotColor, r, Offset(x, y3))
                            }
                        }
                    ) { globalTouch, closeMenuFromRow, hoveredIndex, setHovered, showDialog ->
                        Column(
                            modifier = Modifier.width(IntrinsicSize.Max),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var rowIndex = 0
                            if (onResetPage != null) {
                                val currentIdx = rowIndex++
                                MenuRow(
                                    title = "Reset All",
                                    contentColor = menuContentColor,
                                    secondaryColor = menuSecondaryColor,
                                    isHovered = hoveredIndex == currentIdx,
                                    onHoverChange = { hovering -> if (hovering) setHovered(currentIdx) else if (hoveredIndex == currentIdx) setHovered(null) },
                                    globalTouchPosition = globalTouch,
                                    onClick = {
                                        setHovered(null)
                                        showDialog(
                                            dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                                title = "Reset All Settings?",
                                                message = "This will restore all switches, sliders, and animation curves on this screen back to their default values.",
                                                confirmText = "Reset",
                                                confirmColor = Color(0xFFFF453A),
                                                onConfirm = {
                                                    SubpageScrollRegistry.savePosition(scrollKey, 0, 0)
                                                    scope.launch { lazyListState.scrollToItem(0) }
                                                    onResetPage()
                                                    android.widget.Toast.makeText(context, "Settings restored to defaults", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        )
                                    },
                                    icon = { ResetArrowIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) }
                                )
                            }
                            if (onRestartApp != null && restartAppTitle != null) {
                                val currentIdx = rowIndex++
                                MenuRow(
                                    title = restartAppTitle,
                                    contentColor = menuContentColor,
                                    secondaryColor = menuSecondaryColor,
                                    isHovered = hoveredIndex == currentIdx,
                                    onHoverChange = { hovering -> if (hovering) setHovered(currentIdx) else if (hoveredIndex == currentIdx) setHovered(null) },
                                    globalTouchPosition = globalTouch,
                                    onClick = {
                                        setHovered(null)
                                        showDialog(
                                            dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                                title = "${restartAppTitle}?",
                                                message = "This will force terminate and restart ${restartAppSubtitle ?: "the target process"}.",
                                                confirmText = "Restart",
                                                confirmColor = colors.accentBlue,
                                                onConfirm = {
                                                    onRestartApp()
                                                }
                                            )
                                        )
                                    },
                                    icon = { SlidersIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) }
                                )
                            }
                        }
                    }
                }
        }
    }
}
