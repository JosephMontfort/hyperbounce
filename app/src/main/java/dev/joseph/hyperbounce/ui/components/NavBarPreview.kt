package dev.joseph.hyperbounce.ui.components

/*
 * A live-updating miniature of the floating nav bar, styled from the same
 * containerOpacity/cornerRadius/barHeight/tabWidth/glassIntensity values the sliders on
 * AppDetailScreen edit — so dragging a slider visibly changes this preview in real time,
 * the same way iOS's Settings > Display & Brightness > Text Size screen previews live.
 * Built from this app's own backdrop (the wallpaper), not the real per-app hook engine
 * (nav/glass/LiquidGlassViewBackdrop needs a live target-app View to capture, which
 * doesn't exist in this settings UI).
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings

@Composable
fun NavBarPreview(
    backdrop: Backdrop,
    accentColor: Color,
    glassIntensity: Float,
    containerOpacity: Float,
    cornerRadiusDp: Float,
    barHeightDp: Float,
    tabWidthDp: Float,
    tabCount: Int = 4,
    selectedIndex: Int = 1,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassSettings.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(cornerRadiusDp.dp)
        Row(
            Modifier
                .height(barHeightDp.dp)
                .then(
                    if (glass.enabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(8f.dp.toPx() * glassIntensity)
                                lens(18f.dp.toPx() * glassIntensity, 18f.dp.toPx() * glassIntensity)
                            },
                            highlight = { Highlight.Default },
                            shadow = { Shadow(radius = 10f.dp, color = Color.Black.copy(alpha = 0.18f)) },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = containerOpacity * 0.5f))
                            }
                        )
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.12f + containerOpacity * 0.2f))
                    }
                )
                .clip(shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(tabCount) { index ->
                Box(
                    Modifier
                        .size(width = tabWidthDp.dp, height = (barHeightDp - 16f).dp.coerceAtLeast(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == selectedIndex) accentColor else Color.White.copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }
    }
}
