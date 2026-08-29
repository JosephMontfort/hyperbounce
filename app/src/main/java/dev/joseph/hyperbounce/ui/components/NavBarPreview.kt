package dev.joseph.hyperbounce.ui.components

/*
 * A live-updating miniature of the floating nav bar, styled from the same
 * containerOpacity/cornerRadius/barHeight/tabWidth/glassIntensity values the sliders on
 * AppDetailScreen edit. Also reads LocalGlassSettings (the Settings screen's master
 * switch) — real subtle backdrop glass when on, a flat tinted fill when off.
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
                                blur((glass.blurDp / 2f).dp.toPx() * glassIntensity)
                                lens(
                                    (glass.refractionHeightDp / 1.7f).dp.toPx() * glassIntensity,
                                    (glass.refractionAmountDp / 1.7f).dp.toPx() * glassIntensity,
                                    chromaticAberration = glass.chromaticAberration
                                )
                            },
                            highlight = { Highlight.Default },
                            shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.14f)) },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = containerOpacity * 0.35f))
                            }
                        )
                    } else {
                        Modifier.background(Color(0xFF3A3A3C).copy(alpha = (0.35f + containerOpacity * 0.5f).coerceIn(0f, 1f)))
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
