package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pointerinput.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch

private class DropdownPopupPositionProvider(
    private val anchor: Rect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // `anchor` is already in window coordinates, so keep the popup visually anchored
        // to the picker while allowing the expanded glass surface to escape the scroll/card
        // hierarchy entirely.
        val x = anchor.left.toInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchor.bottom + 8f).toInt().coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

@Composable
fun DropdownPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    alignment: MenuAlignment = MenuAlignment.TopTrailing
) {
    val glass = LocalGlassSettings.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val expanded = progress.value > 0.05f || progress.targetValue > 0.5f
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    fun close() {
        scope.launch { progress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true)) }
    }

    Box(
        modifier
            .width(120.dp)
            .onGloballyPositioned { anchorBounds = it.boundsInWindow() }
    ) {
        Row(
            Modifier
                .width(120.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .then(
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            val target = if (expanded) 0f else 1f
                            scope.launch {
                                progress.animateTo(target, MenuAnimationPreset.Bouncy.getSpec(isClosing = target == 0f))
                            }
                        }
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                options.getOrElse(selectedIndex) { "" },
                style = HyperBounceType.subhead.copy(color = Color.White)
            )
            ChevronIcon(
                color = Color.White.copy(alpha = 0.7f),
                size = 9.dp,
                strokeWidth = 1.6.dp,
                pointingLeft = false
            )
        }
    }

    if (expanded && anchorBounds != null) {
        Popup(
            popupPositionProvider = DropdownPopupPositionProvider(anchorBounds!!),
            properties = PopupProperties(
                focusable = true,
                clippingEnabled = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            onDismissRequest = { close() }
        ) {
            Box(
                Modifier
                    .width(220.dp)
                    .height((options.size * 52 + 48).dp)
            ) {
                // This is intentionally rendered as a separate popup layer: it must never
                // participate in the clipping/layout of the master option container.
                ExpandableGlassMenu(
                    animatableProgress = progress,
                    animationPreset = MenuAnimationPreset.Bouncy,
                    alignment = MenuAlignment.TopLeading,
                    backdrop = backdrop,
                    isGlassEnabled = glass.enabled,
                    isHapticsEnabled = glass.hapticsEnabled,
                    cornerRadius = 16.dp,
                    blurRadius = glass.blurDp,
                    refractionHeight = glass.refractionHeightDp,
                    refractionAmount = glass.refractionAmountDp,
                    chromaticAberration = glass.chromaticAberration,
                    horizontalOffset = 0f,
                    verticalOffset = 0f,
                    labelSize = Size(120f, 36f),
                    surfaceColor = Color(0xFF1D1E21),
                    onRowSelected = { index ->
                        onSelect(index)
                        close()
                    },
                    label = {}
                ) { globalTouch, closeMenu, hoveredIndex, setHovered ->
                    Column {
                        options.forEachIndexed { index, option ->
                            MenuRow(
                                title = option,
                                description = "",
                                contentColor = Color.White,
                                secondaryColor = Color.White.copy(alpha = 0.7f),
                                isHovered = hoveredIndex == index,
                                onHoverChange = { hovering ->
                                    if (hovering) setHovered(index)
                                    else if (hoveredIndex == index) setHovered(null)
                                },
                                globalTouchPosition = globalTouch,
                                onClick = {
                                    closeMenu()
                                    onSelect(index)
                                },
                                icon = {
                                    if (index == selectedIndex) {
                                        CheckmarkIcon(color = Color.White, size = 13.dp, strokeWidth = 2.dp)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
