package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.glass.DropdownOverlayRequest
import dev.joseph.hyperbounce.ui.glass.LocalDropdownOverlayHost
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
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
    val glass = LocalGlassSettings.current
    val isDark = isSystemInDarkTheme()
    val overlayHost = LocalDropdownOverlayHost.current
    val ownerId = remember { Any() }
    val scope = rememberCoroutineScope()

    var anchorPosition by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val isOpen = overlayHost.request?.ownerId == ownerId

    fun open() {
        val progress = Animatable(0f)
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
                        backdrop = backdrop,
                        glassEnabled = glass.enabled,
                        blurDp = glass.blurDp,
                        refractionHeightDp = glass.refractionHeightDp,
                        refractionAmountDp = glass.refractionAmountDp,
                        chromaticAberration = glass.chromaticAberration,
                        progress = progress,
                        onSelect = { index ->
                            onSelect(index)
                            overlayHost.dismiss(ownerId)
                        }
                    )
                }
            )
        )
        scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
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
                onClick = { if (isOpen) overlayHost.dismiss(ownerId) else open() }
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
    backdrop: Backdrop,
    glassEnabled: Boolean,
    blurDp: Float,
    refractionHeightDp: Float,
    refractionAmountDp: Float,
    chromaticAberration: Boolean,
    progress: Animatable<Float, *>,
    onSelect: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(14.dp)
    
    var contentWidth by remember { mutableStateOf(0f) }
    var contentHeight by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .graphicsLayer {
                val p = progress.value
                val w = if (contentWidth > 0f) contentWidth else anchorSize.width.toFloat()
                val h = if (contentHeight > 0f) contentHeight else anchorSize.height.toFloat()
                
                val currentW = lerp(anchorSize.width.toFloat(), w, p)
                val currentH = lerp(anchorSize.height.toFloat(), h, p)
                
                translationX = anchorPosition.x + anchorSize.width / 2f - currentW / 2f
                translationY = anchorPosition.y + anchorSize.height - currentH
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                contentWidth = placeable.width.toFloat()
                contentHeight = placeable.height.toFloat()
                
                val p = progress.value
                val currentW = lerp(anchorSize.width.toFloat(), contentWidth, p).roundToInt()
                val currentH = lerp(anchorSize.height.toFloat(), contentHeight, p).roundToInt()
                
                // Fixed layout size prevents parent (ScreenScaffold) from recomposing/relayouting
                layout(0, 0) {
                    val x = (currentW - placeable.width) / 2
                    val y = currentH - placeable.height
                    placeable.placeWithLayer(x, y)
                }
            }
            .then(
                if (glassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur((blurDp / 2.5f).dp.toPx())
                            lens(
                                (refractionHeightDp / 1.5f).dp.toPx(),
                                (refractionAmountDp / 1.5f).dp.toPx(),
                                chromaticAberration = chromaticAberration
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow(radius = 14f.dp, color = Color.Black.copy(alpha = 0.22f)) }
                    )
                } else {
                    Modifier.background(Color(0xFF1C1C1E).copy(alpha = 0.96f))
                }
            )
            .clip(shape)
    ) {
        // Old content that fades out
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        // Position it so it exactly matches the anchor position visually when scale is applied
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
    }
}
