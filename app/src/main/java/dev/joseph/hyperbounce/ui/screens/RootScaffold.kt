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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
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
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import dev.joseph.hyperbounce.ui.glass.deadEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MenuPage {
    Main,
    RestartScopedApps
}

@Composable
fun RootScaffold(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    repository: SettingsRepository,
    subtitle: String = "",
    showTopAction: Boolean = true,
    onAbout: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val glass = LocalGlassSettings.current
    val clipboard = LocalClipboardManager.current
    val componentStyle = dev.joseph.hyperbounce.ui.state.LocalComponentStyle.current
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
            appendLine("HyperiOS 1.0.0")
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
                .deadEndHaptic(glass.hapticsEnabled)
                .safeDrawingPadding()
                .then(
                    if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                        Modifier.overScrollVertical()
                    } else Modifier
                )
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
                        alpha = (1f - (collapseProgress * 2.2f)).coerceIn(0f, 1f)
                        translationY = -collapseProgress * 8.dp.toPx()
                    }
                    val titleColor = if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        colors.label
                    }
                    BasicText(title, style = HyperBounceType.largeTitle.copy(color = titleColor))
                }
                Spacer(Modifier.size(44.dp)) // reserved footprint; the real menu is drawn as an overlay below
            }

            Spacer(Modifier.height(20.dp))

            content()

            // Clearance so the last card isn't hidden behind the floating bottom tab bar,
            // and pages with fewer items (like Settings) are always scrollable so the title can collapse.
            val bottomClearance = if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) 340.dp else 260.dp
            Spacer(Modifier.height(bottomClearance))
        }

        val headerTint = if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
            MiuixTheme.colorScheme.background
        } else {
            colors.background
        }
        ProgressiveBlurHeader(
            backdrop = contentBackdrop,
            tintColor = headerTint,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .graphicsLayer { alpha = collapseProgress }
        )

        // Centered collapsed title, fades in at center top as the large title scrolls away.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            val titleColor = if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                MiuixTheme.colorScheme.onSurface
            } else {
                colors.label
            }
            BasicText(
                title,
                modifier = Modifier.graphicsLayer {
                    alpha = ((collapseProgress - 0.45f) * 2.2f).coerceIn(0f, 1f)
                },
                style = HyperBounceType.headline.copy(color = titleColor)
            )
        }

        // Full-screen scrim to dismiss the menu when tapping or swiping outside it
        if (menuExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { closeMenu() }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { closeMenu() },
                            onDrag = { _, _ -> }
                        )
                    }
            )
        }

        // Top-right expandable glass menu overlay (full-screen overlay with edge-to-edge dimming)
        val menuContentColor = if (dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme.current) Color.White else Color.Black
        val menuSecondaryColor = menuContentColor.copy(alpha = 0.7f)
        
        if (showTopAction) {
            if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                var miuixMenuExpanded by remember { mutableStateOf(false) }
                val dialogHost = dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 20.dp)
                        .zIndex(10f),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(onClick = { miuixMenuExpanded = true }) {
                        Image(
                            imageVector = MiuixIcons.More,
                            contentDescription = "More",
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                    val menuItems = remember {
                        listOf(
                            DropdownItem(
                                text = "Restart System UI",
                                onClick = {
                                    miuixMenuExpanded = false
                                    dialogHost.showConfirmation(
                                        title = "Restart System UI?",
                                        message = "This will terminate and restart com.android.systemui.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentBlue,
                                        onConfirm = { RootUtils.restartSystemUI(context) }
                                    )
                                }
                            ),
                            DropdownItem(
                                text = "Restart System Launcher",
                                onClick = {
                                    miuixMenuExpanded = false
                                    dialogHost.showConfirmation(
                                        title = "Restart System Launcher?",
                                        message = "This will terminate and restart com.miui.home.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentPurple,
                                        onConfirm = { RootUtils.restartLauncher(context) }
                                    )
                                }
                            ),
                            DropdownItem(
                                text = "System Reboot",
                                onClick = {
                                    miuixMenuExpanded = false
                                    dialogHost.showConfirmation(
                                        title = "Reboot Device?",
                                        message = "This will perform a reboot of your device using root permissions. Any unsaved progress in active apps will be lost.",
                                        confirmText = "Reboot",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = { RootUtils.rebootDevice(context) }
                                    )
                                }
                            )
                        )
                    }
                    WindowDropdownPopup(
                        entry = DropdownEntry(items = menuItems),
                        show = miuixMenuExpanded,
                        onDismiss = { miuixMenuExpanded = false },
                        onDismissFinished = {},
                        maxHeight = 240.dp,
                        dropdownColors = DropdownDefaults.dropdownColors()
                    )
                }
            } else {
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
                    contentPadding = PaddingValues(top = 8.dp, end = 20.dp),
                    labelSize = Size(44f, 44f),
                    onDialogProgressChange = { dialogProgress = it },
                    onRowSelected = { index, showDialog ->
                        when (index) {
                            0 -> {
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
                                false
                            }
                            1 -> {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart System Launcher?",
                                        message = "This will terminate and restart com.miui.home.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentPurple,
                                        onConfirm = {
                                            RootUtils.restartLauncher(context)
                                        }
                                    )
                                )
                                false
                            }
                            2 -> {
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Reboot Device?",
                                        message = "This will perform a reboot of your device using root permissions. Any unsaved progress in active apps will be lost.",
                                        confirmText = "Reboot",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = {
                                            RootUtils.rebootDevice(context)
                                        }
                                    )
                                )
                                false
                            }
                            else -> true
                        }
                    },
                    label = {
                        ResetArrowIcon(color = menuContentColor, size = 18.dp, strokeWidth = 2.dp)
                    }
                ) { globalTouch, closeMenuFromRow, hoveredIndex, setHovered, showDialog ->
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuRow(
                            title = "Restart System UI",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 0,
                            onHoverChange = { hovering -> if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) },
                            globalTouchPosition = globalTouch,
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
                            enabled = true
                        )
                        MenuRow(
                            title = "Restart System Launcher",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 1,
                            onHoverChange = { hovering -> if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) },
                            globalTouchPosition = globalTouch,
                            onClick = {
                                setHovered(null)
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Restart System Launcher?",
                                        message = "This will terminate and restart com.miui.home.",
                                        confirmText = "Restart",
                                        confirmColor = colors.accentPurple,
                                        onConfirm = {
                                            RootUtils.restartLauncher(context)
                                        }
                                    )
                                )
                            },
                            enabled = true
                        )
                        MenuRow(
                            title = "System Reboot",
                            contentColor = menuContentColor,
                            secondaryColor = menuSecondaryColor,
                            isHovered = hoveredIndex == 2,
                            onHoverChange = { hovering -> if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) },
                            globalTouchPosition = globalTouch,
                            onClick = {
                                setHovered(null)
                                showDialog(
                                    dev.joseph.hyperbounce.ui.glass.MenuDialogData(
                                        title = "Reboot Device?",
                                        message = "This will perform a reboot of your device using root permissions. Any unsaved progress in active apps will be lost.",
                                        confirmText = "Reboot",
                                        confirmColor = Color(0xFFFF453A),
                                        onConfirm = {
                                            RootUtils.rebootDevice(context)
                                        }
                                    )
                                )
                            },
                            enabled = true
                        )
                    }
                }
            }
        }

                /*
                 * PRESERVED BACKEND CODE: Multi-page submenu transition & morphing system
                 * Originally used when the top action opened a main menu with subpage navigation
                 * ("Restart Scoped Apps" -> Submenu with Back, System UI, System Launcher, System Reboot).
                 * Kept fully intact for future expandable hierarchical menus.
                 *
                 * Code pattern:
                 * Box(
                 *     modifier = Modifier.layout { measurable, constraints ->
                 *         val placeable = measurable.measure(constraints)
                 *         val targetW = if (isSubmenu) subPageWidth else mainPageWidth
                 *         val targetH = if (isSubmenu) subPageHeight else mainPageHeight
                 *         layout(targetW.toInt(), targetH.toInt()) { placeable.placeRelative(0, 0) }
                 *     }
                 * ) {
                 *     Column(Modifier.graphicsLayer { translationX = -pageTransition * 32.dp.toPx() }) { ... }
                 *     Column(Modifier.graphicsLayer { translationX = (1f - pageTransition) * 32.dp.toPx() }) { ... }
                 * }
                 */
        }
    }
}

@Composable
private fun StockMonochromeAppIcon(
    packageName: String,
    tint: Color,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName, context) {
        try {
            val pm = context.packageManager
            val appIcon = pm.getApplicationIcon(packageName)
            val drawable = if (appIcon is android.graphics.drawable.AdaptiveIconDrawable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && appIcon.monochrome != null) {
                    appIcon.monochrome
                } else {
                    appIcon.foreground
                }
            } else {
                appIcon
            } ?: appIcon

            val bmp = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            bmp.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier,
            colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
        )
    } else {
        fallback()
    }
}
