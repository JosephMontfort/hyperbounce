package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.DropdownOverlayRequest
import dev.joseph.hyperbounce.ui.glass.LocalDropdownOverlayHost
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DropdownPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val overlayHost = LocalDropdownOverlayHost.current
    val ownerId = remember { Any() }
    val scope = rememberCoroutineScope()

    var anchorPosition by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val isOpen = overlayHost.request?.ownerId == ownerId

    fun open() {
        overlayHost.show(
            DropdownOverlayRequest(
                ownerId = ownerId,
                anchorPosition = anchorPosition,
                anchorSize = anchorSize,
                content = {
                    DropdownPanel(
                        options = options,
                        selectedIndex = selectedIndex,
                        anchorPosition = anchorPosition,
                        anchorSize = anchorSize,
                        isDismissing = overlayHost.isDismissing,
                        onDismissed = { overlayHost.clear() },
                        onSelect = { index ->
                            onSelect(index)
                            overlayHost.dismiss(ownerId)
                        }
                    )
                }
            )
        )
    }

    Row(
        modifier
            .onGloballyPositioned { coords ->
                anchorPosition = coords.positionInRoot()
                anchorSize = coords.size
            }
            .clip(RoundedCornerShape(9.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isOpen) open() else overlayHost.dismiss(ownerId) }
            )
            .alpha(if (isOpen) 0f else 1f)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            options.getOrElse(selectedIndex) { "" },
            style = HyperBounceType.subhead.copy(color = if (isDark) Color.White else Color.Black.copy(alpha = 0.82f))
        )
        ChevronIcon(
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
            size = 9.dp,
            strokeWidth = 1.6.dp,
            pointingLeft = false
        )
    }
}

@Composable
private fun DropdownPanel(
    options: List<String>,
    selectedIndex: Int,
    anchorPosition: Offset,
    anchorSize: IntSize,
    isDismissing: Boolean,
    onDismissed: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val inwardOffsetPx = with(density) { 16.dp.toPx() }
    
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            progress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
            onDismissed()
        } else {
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    
    var contentWidth by remember { mutableStateOf(0f) }
    var contentHeight by remember { mutableStateOf(0f) }

    Box(
        Modifier
            // 1. Move to the absolute (x,y)
            .graphicsLayer {
                val p = progress.value
                val w = if (contentWidth > 0f) contentWidth else anchorSize.width.toFloat()
                val h = if (contentHeight > 0f) contentHeight else anchorSize.height.toFloat()
                
                val currentW = lerp(anchorSize.width.toFloat(), w, p)
                val currentH = lerp(anchorSize.height.toFloat(), h, p)
                
                // Shift inward slightly as it expands
                val rightEdgeStart = anchorPosition.x + anchorSize.width
                val rightEdgeEnd = rightEdgeStart - inwardOffsetPx
                val currentRightEdge = lerp(rightEdgeStart, rightEdgeEnd, p)
                
                translationX = currentRightEdge - currentW
                translationY = anchorPosition.y + anchorSize.height - currentH
                
                // Add a drop shadow manually to the graphics layer so it follows the morphing shape
                shadowElevation = lerp(0f, 24f, p)
                shape = RoundedCornerShape(14.dp)
                spotShadowColor = Color.Black.copy(alpha = 0.6f)
                ambientShadowColor = Color.Black.copy(alpha = 0.2f)
                clip = false
            }
            // 2. Prevent parent relayout by reporting 0x0
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) {
                    placeable.placeWithLayer(0, 0)
                }
            }
            // 3. Size the container physically to currentW x currentH so background/clip morphs
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints) // measures inner unbounded box
                contentWidth = placeable.width.toFloat()
                contentHeight = placeable.height.toFloat()
                
                val p = progress.value
                val currentW = lerp(anchorSize.width.toFloat(), contentWidth, p).roundToInt()
                val currentH = lerp(anchorSize.height.toFloat(), contentHeight, p).roundToInt()
                
                layout(currentW, currentH) {
                    // Place inner content anchored to the bottom right
                    val x = currentW - placeable.width
                    val y = currentH - placeable.height
                    placeable.place(x, y)
                }
            }
            // Fully opaque background using the deep black / underlying layer color
            .background(
                color = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
    ) {
        // Inner Box that is measured at FULL size (W x H)
        Box {
            // New content that fades in
            Column(
                Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .padding(vertical = 6.dp)
                    .graphicsLayer {
                        alpha = progress.value
                    }
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index == selectedIndex) {
                            CheckmarkIcon(color = if (isDark) Color.White else Color.Black, size = 13.dp, strokeWidth = 2.dp)
                        } else {
                            Spacer(Modifier.width(13.dp))
                        }
                        BasicText(option, style = HyperBounceType.subhead.copy(color = if (isDark) Color.White else Color.Black))
                    }
                }
            }

            // Old content that fades out, positioned exactly where the anchor would be in the full size box
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    // Force the old content to strictly match the anchor size
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = anchorSize.width, maxWidth = anchorSize.width,
                                minHeight = anchorSize.height, maxHeight = anchorSize.height
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        alpha = 1f - progress.value
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        options.getOrElse(selectedIndex) { "" },
                        style = HyperBounceType.subhead.copy(color = if (isDark) Color.White else Color.Black.copy(alpha = 0.82f))
                    )
                    ChevronIcon(
                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                        size = 9.dp,
                        strokeWidth = 1.6.dp,
                        pointingLeft = false
                    )
                }
            }
        }
    }
}
