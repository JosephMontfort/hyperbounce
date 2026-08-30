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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch

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
    content: @Composable ColumnScope.(contentBackdrop: Backdrop) -> Unit
) {
    val scrollState = rememberScrollState()
    val collapseProgress = (scrollState.value / 220f).fastCoerceIn(0f, 1f)
    // A separate, local backdrop tagging just this screen's scrolling content — kept apart
    // from the app-wide `backdrop` (which the toggles/sliders/menus sample) so the
    // progressive blur below reflects the actual list content passing underneath it, not
    // the plain wallpaper.
    val contentBackdrop = rememberLayerBackdrop()

    val menuAnimationScope = rememberCoroutineScope()
    val menuProgress = remember { Animatable(0f) }
    val menuExpanded = menuProgress.value > 0.05f || menuProgress.targetValue > 0.5f

    fun closeMenu() {
        menuAnimationScope.launch {
            menuProgress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true))
        }
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
                Spacer(Modifier.height(64.dp))
                BasicText(
                    title,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .graphicsLayer {
                            alpha = 1f - collapseProgress * 0.85f
                            translationY = -collapseProgress * 6.dp.toPx()
                        },
                    style = HyperBounceType.largeTitle.copy(color = colors.label)
                )
                if (subtitle != null) {
                    BasicText(
                        subtitle,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .graphicsLayer { alpha = 1f - collapseProgress },
                        style = HyperBounceType.subhead.copy(color = colors.secondaryLabel)
                    )
                }
                Spacer(Modifier.height(16.dp))
                content(contentBackdrop)
                Spacer(Modifier.height(56.dp))
            }

            ProgressiveBlurHeader(
                backdrop = contentBackdrop,
                tintColor = colors.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            // Dedicated top-left pinned back button and collapsed title
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularGlassBackButton(onClick = onBack, backdrop = backdrop)
                Spacer(Modifier.width(12.dp))
                BasicText(
                    title,
                    modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                    style = HyperBounceType.headline.copy(color = colors.label)
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

            // Dedicated top-right pinned expandable glass menu overlay
            if (onResetPage != null || onRestartApp != null) {
                val glass = LocalGlassSettings.current
                val isDark = isSystemInDarkTheme()
                val menuContentColor = if (isDark) Color.White else Color.Black
                val menuSecondaryColor = menuContentColor.copy(alpha = 0.7f)

                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(top = 8.dp, end = 16.dp)
                ) {
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
                        onRowSelected = { index ->
                            if (index == 0 && onResetPage != null) {
                                onResetPage()
                                true
                            } else if ((index == 1 || (index == 0 && onResetPage == null)) && onRestartApp != null) {
                                onRestartApp()
                                true
                            } else {
                                true
                            }
                        },
                        label = {
                            Canvas(Modifier.size(20.dp)) {
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
                        Column(
                            modifier = Modifier.width(IntrinsicSize.Max),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var rowIndex = 0
                            if (onResetPage != null) {
                                val currentIdx = rowIndex++
                                MenuRow(
                                    title = "Reset All",
                                    description = "Restore this page to defaults",
                                    contentColor = menuContentColor,
                                    secondaryColor = menuSecondaryColor,
                                    isHovered = hoveredIndex == currentIdx,
                                    onHoverChange = { hovering -> if (hovering) setHovered(currentIdx) else if (hoveredIndex == currentIdx) setHovered(null) },
                                    globalTouchPosition = globalTouch,
                                    onClick = {
                                        closeMenuFromRow()
                                        onResetPage()
                                    },
                                    icon = { ResetArrowIcon(color = menuContentColor, size = 15.dp, strokeWidth = 1.8.dp) }
                                )
                            }
                            if (onRestartApp != null && restartAppTitle != null) {
                                val currentIdx = rowIndex++
                                MenuRow(
                                    title = restartAppTitle,
                                    description = restartAppSubtitle ?: "Restart scoped process",
                                    contentColor = menuContentColor,
                                    secondaryColor = menuSecondaryColor,
                                    isHovered = hoveredIndex == currentIdx,
                                    onHoverChange = { hovering -> if (hovering) setHovered(currentIdx) else if (hoveredIndex == currentIdx) setHovered(null) },
                                    globalTouchPosition = globalTouch,
                                    onClick = {
                                        closeMenuFromRow()
                                        onRestartApp()
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
}

