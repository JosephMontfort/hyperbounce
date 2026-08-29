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
        val w = size.width
        val h = size.height

        val top: Color
        val bottom: Color
        val glow: Color
        if (colors.isDark) {
            top = Color(0xFF0B0B0D)
            bottom = Color(0xFF1C1C1F)
            glow = Color(0xFF3A3A3F)
        } else {
            top = Color(0xFFF9F9FB)
            bottom = Color(0xFFE9E9ED)
            glow = Color.White
        }

        drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = if (colors.isDark) 0.22f else 0.55f), glow.copy(alpha = 0f)),
                center = Offset(w * 0.22f, h * 0.06f),
                radius = w * 0.9f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = if (colors.isDark) 0.12f else 0.3f), glow.copy(alpha = 0f)),
                center = Offset(w * 0.85f, h * 0.75f),
                radius = w * 0.8f
            )
        )
    }
}
