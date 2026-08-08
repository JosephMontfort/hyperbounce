package dev.joseph.hyperbounce

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
fun LiquidNavBarHost(stockBar: ViewGroup) {
    val backdrop = rememberLayerBackdrop()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 28.dp) // Hover above the void
                .width(280.dp)
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.15f)) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.5f) },
                    onDrawSurface = {
                        // The base tint of the liquid glass
                        drawRect(Color(0xFFFAFAFA).copy(alpha = 0.4f)) 
                    }
                )
        ) {
            // TODO: Inject extracted icons from stockBar here
        }
    }
}
