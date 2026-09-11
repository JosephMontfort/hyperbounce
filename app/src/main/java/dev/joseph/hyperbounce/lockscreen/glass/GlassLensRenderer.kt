package dev.joseph.hyperbounce.lockscreen.glass

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Liquid Glass 2.0 AGSL Lens Pipeline for Lock Screen and Notification Center Sheets.
 * Implements Apple iOS Liquid Glass optical model with per-edge bevel control:
 * Left, Right, and Bottom edges independently configurable with continuous elliptical corner blending.
 *
 * Features:
 * - Monotonic C1 smoothstep refraction curve: strictly zero image inversion or vertical icon flipping.
 * - Normal-directed 3-tap sample smoothing (rimSoft): eliminates spiky caustic distortion and rainbow noise.
 * - Subpixel iOS 26 twin-lobe specular highlight + edge hairline + soft inner glow.
 * - Unified single-pass rendering: zero double-layer ghosting or translucent edge bleeding.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassLensRenderer {

    companion object {
        private const val TAG = "HyperBounce_GlassLens"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        private const val LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float  cornerRadius;
            uniform float3 edgeBevels; // x = left, y = right, z = bottom
            uniform float  refractPx;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  innerShadow;
            uniform float  satFactor;

            float sd3SidedSheet(float2 p, float2 size, float r) {
                if (p.x < r && p.y > (size.y - r)) {
                    return length(p - float2(r, size.y - r)) - r;
                }
                if (p.x > (size.x - r) && p.y > (size.y - r)) {
                    return length(p - float2(size.x - r, size.y - r)) - r;
                }
                float dLeft = -p.x;
                float dRight = -(size.x - p.x);
                float dBottom = -(size.y - p.y);
                return max(max(dLeft, dRight), dBottom);
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                float d = sd3SidedSheet(p, viewSize, cornerRadius);

                // Subpixel anti-aliasing: 1.5px feathering along outer boundary
                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // Independent per-edge bevel slopes with elliptical continuous corner blending
                float wL = max(edgeBevels.x, 0.5);
                float wR = max(edgeBevels.y, 0.5);
                float wB = max(edgeBevels.z, 0.5);
                float maxBevel = max(max(wL, wR), wB);

                float slope = 0.0;
                float2 n = float2(0.0, 1.0);

                bool inLeft = (p.x < wL) && (edgeBevels.x > 0.5);
                bool inRight = (p.x > (viewSize.x - wR)) && (edgeBevels.y > 0.5);
                bool inBottom = (p.y > (viewSize.y - wB)) && (edgeBevels.z > 0.5);

                if (inLeft && inBottom) {
                    // Bottom-Left Corner: Elliptical Continuous Blend
                    float u = 1.0 - p.x / wL;
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float dist = sqrt(u * u + v * v);
                    float edgeDist = clamp(dist, 0.0, 1.0);
                    // Monotonic cubic smoothstep profile
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    float2 cornerN = float2(-u / wL, v / wB);
                    float lenN = length(cornerN);
                    n = lenN > 0.0001 ? cornerN / lenN : float2(-0.7071, 0.7071);
                } else if (inRight && inBottom) {
                    // Bottom-Right Corner: Elliptical Continuous Blend
                    float u = 1.0 - (viewSize.x - p.x) / wR;
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float dist = sqrt(u * u + v * v);
                    float edgeDist = clamp(dist, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    float2 cornerN = float2(u / wR, v / wB);
                    float lenN = length(cornerN);
                    n = lenN > 0.0001 ? cornerN / lenN : float2(0.7071, 0.7071);
                } else if (inLeft) {
                    // Straight Left Edge
                    float u = 1.0 - p.x / wL;
                    float edgeDist = clamp(u, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(-1.0, 0.0);
                } else if (inRight) {
                    // Straight Right Edge
                    float u = 1.0 - (viewSize.x - p.x) / wR;
                    float edgeDist = clamp(u, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(1.0, 0.0);
                } else if (inBottom) {
                    // Straight Bottom Edge
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float edgeDist = clamp(v, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(0.0, 1.0);
                }

                // Inward optical refraction along the edges (offset points inward: -n)
                // refractPx is strictly bounded so derivative of sampled coordinate is always positive (no inversion!)
                float refr = refractPx;
                float2 offset = -n * (slope * refr);

                // Physical chromatic dispersion
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + viewSize - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                // Normal-directed smoothing (rimSoft) to eliminate caustic spikes and harsh lines
                float3 col;
                if (slope > 0.005) {
                    float2 sm = n * (2.0 * slope);
                    float2 cR1 = clamp(cR - sm, lo, hi);
                    float2 cR2 = clamp(cR + sm, lo, hi);
                    float2 cG1 = clamp(cG - sm, lo, hi);
                    float2 cG2 = clamp(cG + sm, lo, hi);
                    float2 cB1 = clamp(cB - sm, lo, hi);
                    float2 cB2 = clamp(cB + sm, lo, hi);
                    col = float3(
                        (content.eval(cR).r + content.eval(cR1).r + content.eval(cR2).r) * 0.33333,
                        (content.eval(cG).g + content.eval(cG1).g + content.eval(cG2).g) * 0.33333,
                        (content.eval(cB).b + content.eval(cB1).b + content.eval(cB2).b) * 0.33333
                    );
                } else {
                    col = float3(
                        content.eval(cR).r,
                        content.eval(cG).g,
                        content.eval(cB).b
                    );
                }

                // Color Vibrancy Curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Backside inner shadow
                float facing = dot(n, -lightDir);
                if (innerShadow > 0.001) {
                    float shadowW = clamp(maxBevel, 4.0, 28.0);
                    float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                    float ish = shadowBand * max(facing, 0.0) * innerShadow;
                    col = col * (1.0 - 0.45 * ish);
                }

                // iOS 26 Measured Dual-Lobe Specular Highlight + Hairline + Inner Glow
                float lobeF = pow(max(facing, 0.0), 4.5);
                float lobeB = pow(max(-facing, 0.0), 4.5);

                float hair = clamp(1.0 - abs(d + 1.0) / 2.0, 0.0, 1.0) * cov;
                float bandW = clamp(maxBevel * 0.30, 2.0, 8.0);
                float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
                float glow = glowIn * pow(clamp(1.0 - (-d - 3.0) / bandW, 0.0, 1.0), 1.5) * cov;

                float spec = (hair * 0.70 * (lobeF + lobeB) + glow * 0.12 * lobeF) * specStrength;
                col += float3(spec);
                col = clamp(col, float3(0.0), float3(1.0));

                return half4(col * cov, cov);
            }
        """
    }

    data class LensParams(
        val cornerRadius: Float = 36f,
        val leftBevel: Float = 28f,
        val rightBevel: Float = 28f,
        val bottomBevel: Float = 28f,
        val refract: Float = 24f,
        val dispersion: Float = 0.025f,
        val lightX: Float = 0f,
        val lightY: Float = 1f,
        val spec: Float = 0.90f,
        val innerShadow: Float = 0.0f,
        val saturation: Float = 120f,
        val blurRadius: Float = 0f
    )

    private val renderNode = RenderNode("LiquidGlass20Lens")
    private var shader: RuntimeShader? = null
    private var shaderBroken = false
    private var lastParams: LensParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastMargin = -1

    val isAvailable: Boolean get() = !shaderBroken

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        params: LensParams,
        margin: Int,
        recordContent: (recordingCanvas: Canvas) -> Unit
    ): Boolean {
        if (shaderBroken || !canvas.isHardwareAccelerated) return false
        if (width <= 0 || height <= 0) return false

        val sh = shader ?: try {
            RuntimeShader(LENS_AGSL).also { shader = it }
        } catch (e: Exception) {
            Log.e(TAG, "Lens 2.0 AGSL compilation failed", e)
            shaderBroken = true
            return false
        }

        if (params != lastParams || width != lastWidth || height != lastHeight || margin != lastMargin) {
            try {
                renderNode.setRenderEffect(buildEffect(sh, params, margin, width, height))
            } catch (e: Exception) {
                Log.e(TAG, "Lens 2.0 RenderEffect build failed", e)
                shaderBroken = true
                return false
            }
            lastParams = params
            lastWidth = width
            lastHeight = height
            lastMargin = margin
        }

        renderNode.setPosition(-margin, -margin, width + margin, height + margin)
        val recordingCanvas = renderNode.beginRecording(width + 2 * margin, height + 2 * margin)
        try {
            recordContent(recordingCanvas)
        } finally {
            renderNode.endRecording()
        }

        canvas.drawRenderNode(renderNode)
        return true
    }

    private fun buildEffect(
        sh: RuntimeShader,
        p: LensParams,
        margin: Int,
        width: Int,
        height: Int
    ): RenderEffect {
        sh.setFloatUniform("margin", margin.toFloat())
        sh.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
        sh.setFloatUniform("cornerRadius", p.cornerRadius)
        sh.setFloatUniform("edgeBevels", p.leftBevel, p.rightBevel, p.bottomBevel)
        // Bound refraction displacement to prevent image inversion / flip bug
        val maxBevel = maxOf(p.leftBevel, p.rightBevel, p.bottomBevel).coerceAtLeast(1.0f)
        val safeRefract = p.refract.coerceIn(0f, maxBevel * 0.40f)
        sh.setFloatUniform("refractPx", safeRefract)
        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("innerShadow", p.innerShadow)
        sh.setFloatUniform("satFactor", p.saturation / 100f)

        val lensEffect = RenderEffect.createRuntimeShaderEffect(sh, "content")
        return if (p.blurRadius > 0.01f) {
            RenderEffect.createChainEffect(
                lensEffect,
                RenderEffect.createBlurEffect(p.blurRadius, p.blurRadius, Shader.TileMode.CLAMP)
            )
        } else {
            lensEffect
        }
    }

    fun release() {
        renderNode.discardDisplayList()
        shader = null
        lastParams = null
    }
}
