package dev.joseph.hyperbounce.nav.glass

import androidx.annotation.FloatRange
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape
import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

@Language("AGSL")
private const val CustomLensWithDispersionShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$RoundedRectSDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    
    if (chromaticAberration <= 0.0) {
        return content.eval(refractedCoord);
    }
    
    // High-fidelity chromatic dispersion across the bevel
    float dispersionIntensity = chromaticAberration * 0.45;
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}
"""

fun BackdropEffectScope.liquidLens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val cornerRadii = getCornerRadii(this) ?: floatArrayOf(0f, 0f, 0f, 0f)
    val shader = obtainRuntimeShader(
        "LiquidLensWithDispersion",
        CustomLensWithDispersionShader
    )
    shader.apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadii", cornerRadii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", if (chromaticAberration) 1f else 0f)
    }
    val effect = RuntimeShaderEffect(shader, "content")
    effect(effect)
}

private fun getCornerRadii(scope: BackdropEffectScope): FloatArray? {
    return when (val shape = scope.shape) {
        is RoundedRectangularShape -> {
            val corners = shape.corners(scope.size, scope.layoutDirection, scope)
            floatArrayOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft
            )
        }

        is AbsoluteRoundedCornerShape -> {
            val size = scope.size
            val maxRadius = size.minDimension / 2f
            val topLeft = shape.topStart.toPx(size, scope)
            val topRight = shape.topEnd.toPx(size, scope)
            val bottomRight = shape.bottomEnd.toPx(size, scope)
            val bottomLeft = shape.bottomStart.toPx(size, scope)
            floatArrayOf(
                topLeft.fastCoerceAtMost(maxRadius),
                topRight.fastCoerceAtMost(maxRadius),
                bottomRight.fastCoerceAtMost(maxRadius),
                bottomLeft.fastCoerceAtMost(maxRadius)
            )
        }

        is CornerBasedShape -> {
            val size = scope.size
            val maxRadius = size.minDimension / 2f
            val isLtr = scope.layoutDirection == LayoutDirection.Ltr
            val topLeft =
                if (isLtr) shape.topStart.toPx(size, scope)
                else shape.topEnd.toPx(size, scope)
            val topRight =
                if (isLtr) shape.topEnd.toPx(size, scope)
                else shape.topStart.toPx(size, scope)
            val bottomRight =
                if (isLtr) shape.bottomEnd.toPx(size, scope)
                else shape.bottomStart.toPx(size, scope)
            val bottomLeft =
                if (isLtr) shape.bottomStart.toPx(size, scope)
                else shape.bottomEnd.toPx(size, scope)
            floatArrayOf(
                topLeft.fastCoerceAtMost(maxRadius),
                topRight.fastCoerceAtMost(maxRadius),
                bottomRight.fastCoerceAtMost(maxRadius),
                bottomLeft.fastCoerceAtMost(maxRadius)
            )
        }

        else -> null
    }
}
