package dev.joseph.hyperbounce.ui.glass

/*
 * Ported from com.kyant.backdrop.catalog.destinations.ProgressiveBlurContent (the
 * AndroidLiquidGlass 2.0.0 demo catalog) — a blur that's strong at the very top and
 * smoothly fades to nothing by the bottom of the header zone, via a custom AGSL shader
 * (blur() then a hand-written alpha-mask shader), not a hard-edged toolbar. Unmodified
 * effect logic — only the package and the placeholder demo content were changed; this
 * version renders as a standalone overlay (no text) sized/positioned by the caller, and
 * takes a tint color instead of hardcoding light/dark swap.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
@Composable
fun ProgressiveBlurHeader(
    backdrop: Backdrop,
    tintColor: Color,
    modifier: Modifier = Modifier,
    tintIntensity: Float = 0.8f
) {
    Box(modifier = modifier) {
        Box(
            Modifier.matchParentSize().drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(4f.dp.toPx())
                    runtimeShaderEffect(
                        "AlphaMask",
                        """
uniform shader content;

uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}""",
                        "content"
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tintColor)
                        setFloatUniform("tintIntensity", tintIntensity)
                    }
                }
            )
        )
        // Thin bottom separator
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.33.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
                .align(Alignment.BottomCenter)
        )
    }
}
