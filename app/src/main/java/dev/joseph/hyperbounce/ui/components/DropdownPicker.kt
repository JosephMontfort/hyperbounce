package dev.joseph.hyperbounce.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.DropdownOverlayRequest
import dev.joseph.hyperbounce.ui.glass.LocalDropdownOverlayHost
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun DropdownPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isDark = dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme.current
    val overlayHost = LocalDropdownOverlayHost.current
    val ownerId = remember { Any() }

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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isOpen) open() else overlayHost.dismiss(ownerId) }
            )
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
    onSelect: (Int) -> Unit
) {
    val isDark = dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme.current
    
    var contentWidth by remember { mutableStateOf(0f) }

    Box(
        Modifier
            // Move to the absolute (x,y)
            .graphicsLayer {
                val w = if (contentWidth > 0f) contentWidth else anchorSize.width.toFloat()
                
                // Align right edge exactly with the anchor's right edge
                val rightEdge = anchorPosition.x + anchorSize.width
                
                translationX = rightEdge - w
                // Align top exactly with the anchor's top, perfectly covering the resting state
                translationY = anchorPosition.y
                
                shadowElevation = 24f
                shape = RoundedCornerShape(14.dp)
                spotShadowColor = Color.Black.copy(alpha = 0.6f)
                ambientShadowColor = Color.Black.copy(alpha = 0.2f)
                clip = false
            }
            // Prevent parent relayout by reporting 0x0
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                contentWidth = placeable.width.toFloat()
                layout(0, 0) {
                    placeable.placeWithLayer(0, 0)
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
        Column(
            Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .padding(vertical = 6.dp)
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
    }
}
