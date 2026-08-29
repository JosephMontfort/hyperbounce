package dev.joseph.hyperbounce.ui.components

/*
 * Every "choose one of several options" control in this app uses this dropdown style
 * instead of a segmented control. Renders via LocalDropdownOverlayHost (see
 * ui/glass/DropdownOverlay.kt) rather than inline: the inline part is only ever a small,
 * fixed-size label button, so it never pushes its parent Row/Card around, and the expanded
 * panel always draws above everything else on the screen since it's hosted at the
 * scaffold's own top level, not nested deep inside a Card.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .graphicsLayer {
                translationX = anchorPosition.x
                translationY = anchorPosition.y + anchorSize.height + 6f.dp.toPx()
                val p = progress.value
                scaleX = 0.85f + 0.15f * p
                scaleY = 0.85f + 0.15f * p
                alpha = p
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .widthIn(min = 160.dp, max = 240.dp)
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
                    CheckmarkIcon(color = Color.White, size = 13.dp, strokeWidth = 2.dp)
                } else {
                    Spacer(Modifier.width(13.dp))
                }
                BasicText(option, style = HyperBounceType.subhead.copy(color = Color.White))
            }
        }
    }
}
