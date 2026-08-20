package dev.joseph.hyperbounce.ui.theme

/*
 * The glass effects in this app (blur/lens/vibrancy) read whatever's actually behind
 * them, so a flat single-color background makes every glass surface look flat too. This
 * paints a soft multi-tone gradient with a few large, low-opacity color blobs — cheap
 * (just radial gradients, no RenderEffect blur pass) and gives every glass panel in the
 * app something colorful to bend and blur, the same way the library's own catalog demo
 * sits its glass components over a photo rather than a solid fill.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun HyperBounceWallpaper(colors: HyperBounceColors, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base vertical gradient.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(colors.wallpaperTop, colors.wallpaperBottom)
            )
        )

        // Soft color blobs, additive-feeling via low alpha radial gradients.
        val blobs = listOf(
            Triple(Offset(w * 0.18f, h * 0.14f), w * 0.55f, colors.accentBlue),
            Triple(Offset(w * 0.85f, h * 0.28f), w * 0.5f, colors.accentOrange),
            Triple(Offset(w * 0.7f, h * 0.82f), w * 0.6f, colors.accentGreen),
            Triple(Offset(w * 0.1f, h * 0.9f), w * 0.45f, Color(0xFFAF52DE))
        )
        blobs.forEach { (center, radius, color) ->
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = if (colors.isDark) 0.32f else 0.28f), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                )
            )
        }

        // Gentle vignette so glass panels near the edges still read clearly.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.12f)),
                center = Offset(w / 2f, h / 2f),
                radius = w.coerceAtLeast(h) * 0.9f
            )
        )
    }
}
