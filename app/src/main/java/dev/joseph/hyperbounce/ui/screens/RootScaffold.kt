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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

private enum class MenuPage {
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

    var menuPage by remember { mutableStateOf(MenuPage.Main) }

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

        // Top-right expandable glass menu overlay with sub-menu morphing support
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
                horizontalOffset = -16f,
                verticalOffset = 30f,
                labelSize = Size(44f, 44f),
                onRowSelected = { index ->
                    if (menuPage == MenuPage.Main) {
                        when (index) {
                            0 -> menuPage = MenuPage.RestartScopedApps
                            1 -> {
                                copyDebugInfo()
                                closeMenu()
                            }
                            2 -> {
                                onAbout()
                                closeMenu()
                            }
                        }
                    } else {
                        when (index) {
                            0 -> menuPage = MenuPage.Main
                            1 -> {
                                RootUtils.restartSystemUI(context)
                                closeMenu()
                            }
                            2 -> {
                                RootUtils.restartLauncher(context)
                                closeMenu()
                            }
                            3 -> {
                                RootUtils.rebootDevice(context)
                                closeMenu()
                            }
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
            ) { globalTouch, closeMenuFromRow, hoveredIndex, setHovered ->
                AnimatedContent(
                    targetState = menuPage,
                    transitionSpec = {
                        val forward = targetState == MenuPage.RestartScopedApps
                        val slideOffset: (Int) -> Int = { width -> if (forward) width / 4 else -width / 4 }
                        (slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.76f, stiffness = 420f),
                            initialOffsetX = slideOffset
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.76f, stiffness = 420f),
                                targetOffsetX = { width -> -slideOffset(width) }
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 140, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                            )
                        ).using(
                            // Disable internal staggered size animation so the outer GlassEffectContainer
                            // morphs both width and height simultaneously in all directions on the exact same frame!
                            sizeTransform = null
                        )
                    },
                    label = "menuPageMorph"
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (page == MenuPage.Main) {
                            MenuRow(
                                title = "Restart Scoped Apps",
                                description = "System UI, Launcher & Reboot",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 0,
                                onHoverChange = { hovering -> if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    menuPage = MenuPage.RestartScopedApps
                                },
                                icon = { ResetArrowIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) },
                                trailingIcon = { ChevronIcon(pointingLeft = false, size = 10.dp, strokeWidth = 2.dp, color = menuSecondaryColor) }
                            )
                            MenuRow(
                                title = "Copy Debug Info",
                                description = "Version & current engine state",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 1,
                                onHoverChange = { hovering -> if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenuFromRow()
                                    copyDebugInfo()
                                },
                                icon = { CopyIcon(color = menuContentColor, size = 14.dp, strokeWidth = 1.6.dp) }
                            )
                            MenuRow(
                                title = "About HyperBounce",
                                description = "Credits and library versions",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 2,
                                onHoverChange = { hovering -> if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenuFromRow()
                                    onAbout()
                                },
                                icon = { InfoIcon(color = menuContentColor, size = 14.dp, strokeWidth = 1.6.dp) }
                            )
                        } else {
                            MenuRow(
                                title = "Back",
                                description = "Return to main menu",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 0,
                                onHoverChange = { hovering -> if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    menuPage = MenuPage.Main
                                },
                                icon = { ChevronIcon(pointingLeft = true, size = 13.dp, strokeWidth = 2.2.dp, color = menuContentColor) }
                            )
                            MenuRow(
                                title = "System UI",
                                description = "Restart com.android.systemui",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 1,
                                onHoverChange = { hovering -> if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenuFromRow()
                                    RootUtils.restartSystemUI(context)
                                },
                                icon = { SlidersIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) }
                            )
                            MenuRow(
                                title = "System Launcher",
                                description = "Restart com.miui.home",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 2,
                                onHoverChange = { hovering -> if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenuFromRow()
                                    RootUtils.restartLauncher(context)
                                },
                                icon = { AppGridIcon(color = menuContentColor, size = 15.dp) }
                            )
                            MenuRow(
                                title = "System Reboot",
                                description = "Full device system reboot",
                                contentColor = menuContentColor,
                                secondaryColor = menuSecondaryColor,
                                isHovered = hoveredIndex == 3,
                                onHoverChange = { hovering -> if (hovering) setHovered(3) else if (hoveredIndex == 3) setHovered(null) },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenuFromRow()
                                    RootUtils.rebootDevice(context)
                                },
                                icon = { PowerIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
