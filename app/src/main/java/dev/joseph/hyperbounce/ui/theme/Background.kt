package dev.joseph.hyperbounce.ui.theme

/*
 * Blurring a perfectly flat, single-color background is visually indistinguishable from
 * not blurring it at all — there's nothing for the blur to soften, so every glass surface
 * in the app (cards, the back button, the tab bar, the menu) reads as flat even though the
 * backdrop code is doing real work. This adds just enough subtle, strictly monochromatic
 * tonal variation — a soft gradient plus two very low-contrast highlight patches — for
 * blur and vibrancy to have visible texture to react to, while staying "plain," not the
 * colorful gradient wallpaper from earlier iterations. No warm/cool color tinting: every
 * tone here is a shade of the theme's own background/label colors.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun HyperBounceBackground(colors: HyperBounceColors, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        if (colors.isDark) {
            drawRect(color = Color(0xFF000000))
        } else {
            val top = Color(0xFFF9F9FB)
            val bottom = Color(0xFFE9E9ED)
            val glow = Color.White

            drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0f)),
                    center = Offset(size.width * 0.22f, size.height * 0.06f),
                    radius = size.width * 0.9f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.3f), glow.copy(alpha = 0f)),
                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.width * 0.8f
                )
            )
        }
    }
}
