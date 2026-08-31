package dev.joseph.hyperbounce.ui.screens

/*
 * Shared by both root tabs (Tuning, Settings): the collapsing large title, the progressive
 * blur behind the fixed top bar, and the top-right ExpandableGlassMenu (Reset to Defaults /
 * Copy Debug Info / About) — factored out so both tabs get identical behavior without
 * duplicating the Animatable/gesture-scrim/menu wiring.
 *
 * The menu supports both interaction styles at once: a plain tap on a row (MenuRow's own
 * .clickable), and iOS-context-menu-style long-press → drag across rows → release over one
 * to select it (ExpandableGlassMenu's onRowSelected, fired with the row index that was
 * hovered when the finger lifted).
 */

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.ChevronIcon
import dev.joseph.hyperbounce.ui.components.CopyIcon
import dev.joseph.hyperbounce.ui.components.InfoIcon
import dev.joseph.hyperbounce.ui.components.MenuRow
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.DropdownOverlayHost
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.glass.ProgressiveBlurHeader
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import dev.joseph.hyperbounce.ui.utils.RootUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MenuPage {
    Main,
    RestartScopedApps
}

@Composable
fun RootScaffold(
    title: String,
    subtitle: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    repository: SettingsRepository,
    onAbout: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val glass = LocalGlassSettings.current
    val clipboard = LocalClipboardManager.current
    val menuAnimationScope = rememberCoroutineScope()
    val menuProgress = remember { Animatable(0f) }
    val menuExpanded = menuProgress.value > 0.05f || menuProgress.targetValue > 0.5f
    var dialogProgress by remember { mutableStateOf(0f) }

    var menuPage by remember { mutableStateOf(MenuPage.Main) }
    val isSubmenu = menuPage == MenuPage.RestartScopedApps

    val pageTransition by animateFloatAsState(
        targetValue = if (isSubmenu) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "menuPageTransition"
    )

    // Silently reset submenu state back to root page after the menu finishes closing
    androidx.compose.runtime.LaunchedEffect(menuProgress.targetValue) {
        if (menuProgress.targetValue == 0f) {
            delay(350)
            menuPage = MenuPage.Main
        }
    }

    val scrollState = rememberScrollState()
    val collapseProgress = (scrollState.value / 160f).fastCoerceIn(0f, 1f)
    val contentBackdrop = rememberLayerBackdrop()

    fun closeMenu() {
        menuAnimationScope.launch {
            menuProgress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true))
        }
    }

    fun copyDebugInfo() {
        val text = buildString {
            appendLine("HyperBounce 1.0.0 (UI Phase 1)")
            appendLine("Module: ${if (repository.moduleEnabled) "On" else "Off"}")
            appendLine("Toggle engine: ${repository.toggleEngine.label}")
            appendLine("Slider engine: ${repository.sliderEngine.label}")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Debug info copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
        DropdownOverlayHost {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
                .safeDrawingPadding()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(56.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    Modifier.graphicsLayer {
                        alpha = 1f - collapseProgress * 0.85f
                        translationY = -collapseProgress * 6.dp.toPx()
                    }
                ) {
                    BasicText(title, style = HyperBounceType.largeTitle.copy(color = colors.label))
                    BasicText(subtitle, style = HyperBounceType.subhead.copy(color = colors.secondaryLabel))
                }
                Spacer(Modifier.size(44.dp)) // reserved footprint; the real menu is drawn as an overlay below
            }

            Spacer(Modifier.height(20.dp))

            content()

            // Clearance so the last card isn't hidden behind the floating bottom tab bar.
            Spacer(Modifier.height(110.dp))
        }

        ProgressiveBlurHeader(
            backdrop = contentBackdrop,
            tintColor = colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        // Small collapsed title, fades in behind the menu as the large title scrolls away.
        BasicText(
            title,
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = 20.dp, top = 14.dp)
                .graphicsLayer { alpha = collapseProgress },
            style = HyperBounceType.headline.copy(color = colors.label)
        )

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

        // GPU-efficient full-screen dialog dim layer (zero shader compilation overhead)
        if (dialogProgress > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (dialogProgress * 0.45f).coerceIn(0f, 0.45f)
                    }
                    .background(Color.Black)
                    .pointerInput(Unit) {}
                    .zIndex(1.5f)
            )
        }

        // Top-right expandable glass menu overlay with synchronized 2D sub-menu morphing support
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 8.dp, end = 20.dp)
        ) {
            val menuContentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
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
                labelSize = Size(44f, 44f),
                onDialogProgressChange = { dialogProgress = it },
                onRowSelected = { index, showDialog ->
                    if (menuPage == MenuPage.Main) {
                        when (index) {
                            0 -> {
                                menuPage = MenuPage.RestartScopedApps
                                false // Open submenu, keep menu open for fluid morph!
                            }
                            1 -> {
                                copyDebugInfo()
                                true // Close menu
                            }
                            2 -> {
                                onAbout()
                                true // Close menu
                            }
                            else -> true
                        }
                    } else {
                        when (index) {
                            0 -> {
                                menuPage = MenuPage.Main
                                false // Morph back to main menu, keep open!
                            }
                            1 -> {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart System UI?",
                                        message = "This will terminate and restart com.android.systemui.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentBlue,
                                        onConfirm = {
                                            RootUtils.restartSystemUI(context)
                                        }
                                    )
                                )
                                false // Morph into confirmation dialog
                            }
                            2 -> {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart Launcher?",
                                        message = "This will terminate and restart com.miui.home.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentPurple,
                                        onConfirm = {
                                            RootUtils.restartLauncher(context)
                                        }
                                    )
                                )
                                false // Morph into confirmation dialog
                            }
                            3 -> {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Reboot Device?",
                                        message = "This will perform a soft reboot of your device using root permissions. Any unsaved progress in active apps will be lost.",
                                        confirmText = "Reboot",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = {
                                            RootUtils.rebootDevice(context)
                                        }
                                    )
                                )
                                false // Morph into confirmation dialog
                            }
                            else -> true
                        }
                    }
                },
                label = {
                    Canvas(
                        modifier = Modifier.size(22.dp)
                    ) {
                        val y = size.height / 2f
                        val r = size.minDimension * 0.075f
                        val x1 = size.width * 0.25f
                        val x2 = size.width * 0.50f
                        val x3 = size.width * 0.75f
                        val dotColor = menuContentColor
                        drawCircle(dotColor, r, Offset(x1, y))
                        drawCircle(dotColor, r, Offset(x2, y))
                        drawCircle(dotColor, r, Offset(x3, y))
                    }
                }
            ) { globalTouch, closeMenuFromRow, hoveredIndex, setHovered, showDialog ->
                androidx.compose.runtime.LaunchedEffect(menuPage) {
                    setHovered(null)
                }

                var mainPageWidth by remember { mutableStateOf(0f) }
                var mainPageHeight by remember { mutableStateOf(0f) }
                var subPageWidth by remember { mutableStateOf(0f) }
                var subPageHeight by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val targetW = if (isSubmenu) subPageWidth else mainPageWidth
                            val targetH = if (isSubmenu) subPageHeight else mainPageHeight
                            val finalW = if (targetW > 0f) targetW.toInt() else placeable.width
                            val finalH = if (targetH > 0f) targetH.toInt() else placeable.height
                            layout(finalW, finalH) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) {
                    // Main Menu Page
                    Column(
                        modifier = Modifier
                            .zIndex(if (!isSubmenu) 2f else 0f)
                            .onSizeChanged { size ->
                                if (size.width > 0 && size.height > 0) {
                                    mainPageWidth = size.width.toFloat()
                                    mainPageHeight = size.height.toFloat()
                                }
                            }
                            .graphicsLayer {
                                val p = pageTransition
                                alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                                translationX = -p * 32.dp.toPx()
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuRow(
                            title = "Restart Scoped Apps",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 0 && !isSubmenu,
                            onHoverChange = { hovering -> if (!isSubmenu) { if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) } },
                            globalTouchPosition = if (!isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                setHovered(null)
                                menuPage = MenuPage.RestartScopedApps
                            },
                            icon = { ResetArrowIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) },
                            trailingIcon = { ChevronIcon(pointingLeft = false, size = 10.dp, strokeWidth = 2.dp, color = menuSecondaryColor) },
                            enabled = !isSubmenu
                        )
                        MenuRow(
                            title = "Copy Diagnostic Info",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 1 && !isSubmenu,
                            onHoverChange = { hovering -> if (!isSubmenu) { if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) } },
                            globalTouchPosition = if (!isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                closeMenuFromRow()
                                copyDebugInfo()
                            },
                            icon = { CopyIcon(color = menuContentColor, size = 14.dp, strokeWidth = 1.6.dp) },
                            enabled = !isSubmenu
                        )
                        MenuRow(
                            title = "About HyperBounce",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 2 && !isSubmenu,
                            onHoverChange = { hovering -> if (!isSubmenu) { if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) } },
                            globalTouchPosition = if (!isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                closeMenuFromRow()
                                onAbout()
                            },
                            icon = { InfoIcon(color = menuContentColor, size = 14.dp, strokeWidth = 1.6.dp) },
                            enabled = !isSubmenu
                        )
                    }

                    // Submenu Page ("Restart Scoped Apps")
                    Column(
                        modifier = Modifier
                            .zIndex(if (isSubmenu) 2f else 0f)
                            .onSizeChanged { size ->
                                if (size.width > 0 && size.height > 0) {
                                    subPageWidth = size.width.toFloat()
                                    subPageHeight = size.height.toFloat()
                                }
                            }
                            .graphicsLayer {
                                val p = pageTransition
                                alpha = ((p - 0.45f) * 2.2f).coerceIn(0f, 1f)
                                translationX = (1f - p) * 32.dp.toPx()
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuRow(
                            title = "Back",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 0 && isSubmenu,
                            onHoverChange = { hovering -> if (isSubmenu) { if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) } },
                            globalTouchPosition = if (isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                setHovered(null)
                                menuPage = MenuPage.Main
                            },
                            icon = { ChevronIcon(pointingLeft = true, size = 13.dp, strokeWidth = 2.2.dp, color = menuContentColor) },
                            enabled = isSubmenu
                        )
                        MenuRow(
                            title = "System UI",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 1 && isSubmenu,
                            onHoverChange = { hovering -> if (isSubmenu) { if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) } },
                            globalTouchPosition = if (isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                setHovered(null)
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart System UI?",
                                        message = "This will terminate and restart com.android.systemui.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentBlue,
                                        onConfirm = {
                                            RootUtils.restartSystemUI(context)
                                        }
                                    )
                                )
                            },
                            icon = { SlidersIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) },
                            enabled = isSubmenu
                        )
                        MenuRow(
                            title = "System Launcher",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 2 && isSubmenu,
                            onHoverChange = { hovering -> if (isSubmenu) { if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) } },
                            globalTouchPosition = if (isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                setHovered(null)
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart Launcher?",
                                        message = "This will terminate and restart com.miui.home.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentPurple,
                                        onConfirm = {
                                            RootUtils.restartLauncher(context)
                                        }
                                    )
                                )
                            },
                            icon = { AppGridIcon(color = menuContentColor, size = 15.dp) },
                            enabled = isSubmenu
                        )
                        MenuRow(
                            title = "System Reboot",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 3 && isSubmenu,
                            onHoverChange = { hovering -> if (isSubmenu) { if (hovering) setHovered(3) else if (hoveredIndex == 3) setHovered(null) } },
                            globalTouchPosition = if (isSubmenu) globalTouch else Offset.Unspecified,
                            onClick = {
                                setHovered(null)
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Reboot Device?",
                                        message = "This will perform a soft reboot of your device using root permissions. Any unsaved progress in active apps will be lost.",
                                        confirmText = "Reboot",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = {
                                            RootUtils.rebootDevice(context)
                                        }
                                    )
                                )
                            },
                            icon = { PowerIcon(color = menuContentColor, size = 14.dp, strokeWidth = 1.8.dp) },
                            enabled = isSubmenu
                        )
                    }
                }
            }
        }
        }
    }
}
