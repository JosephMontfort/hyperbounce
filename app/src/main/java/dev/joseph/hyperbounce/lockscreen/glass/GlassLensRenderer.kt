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
 * Liquid Glass 2.0 AGSL Lens Pipeline for Lock Screen Sheet.
 * Implements Apple iOS Liquid Glass 2.0 optical model with per-edge bevel control:
 * Left, Right, and Bottom edges independently configurable.
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

                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // Independent per-edge bevel slopes with elliptical continuous corner blending (zero crease, zero overlap)
                float wL = max(edgeBevels.x, 0.5);
                float wR = max(edgeBevels.y, 0.5);
                float wB = max(edgeBevels.z, 0.5);

                float slope = 0.0;
                float2 n = float2(0.0, 1.0);

                bool inLeft = (p.x < wL) && (edgeBevels.x > 0.5);
                bool inRight = (p.x > (viewSize.x - wR)) && (edgeBevels.y > 0.5);
                bool inBottom = (p.y > (viewSize.y - wB)) && (edgeBevels.z > 0.5);

                if (inLeft && inBottom) {
                    // Bottom-Left Corner: Elliptical Continuous Blend (zero crease, zero overlap)
                    float u = 1.0 - p.x / wL;
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float dist = clamp(sqrt(u * u + v * v), 0.0, 1.0);
                    slope = dist * dist * (3.0 - 2.0 * dist);
                    float2 cornerN = float2(-u / wL, v / wB);
                    float lenN = length(cornerN);
                    n = lenN > 0.0001 ? cornerN / lenN : float2(-0.7071, 0.7071);
                } else if (inRight && inBottom) {
                    // Bottom-Right Corner: Elliptical Continuous Blend (zero crease, zero overlap)
                    float u = 1.0 - (viewSize.x - p.x) / wR;
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float dist = clamp(sqrt(u * u + v * v), 0.0, 1.0);
                    slope = dist * dist * (3.0 - 2.0 * dist);
                    float2 cornerN = float2(u / wR, v / wB);
                    float lenN = length(cornerN);
                    n = lenN > 0.0001 ? cornerN / lenN : float2(0.7071, 0.7071);
                } else if (inLeft) {
                    // Straight Left Edge
                    float u = clamp(1.0 - p.x / wL, 0.0, 1.0);
                    slope = u * u * (3.0 - 2.0 * u);
                    n = float2(-1.0, 0.0);
                } else if (inRight) {
                    // Straight Right Edge
                    float u = clamp(1.0 - (viewSize.x - p.x) / wR, 0.0, 1.0);
                    slope = u * u * (3.0 - 2.0 * u);
                    n = float2(1.0, 0.0);
                } else if (inBottom) {
                    // Straight Bottom Edge
                    float v = clamp(1.0 - (viewSize.y - p.y) / wB, 0.0, 1.0);
                    slope = v * v * (3.0 - 2.0 * v);
                    n = float2(0.0, 1.0);
                }

                // Specular highlight
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);

                float maxBevel = max(max(edgeBevels.x, edgeBevels.y), edgeBevels.z);
                float dist = -d;
                float bandW = clamp(maxBevel * 0.30, 2.0, 9.0);
                float rim = clamp(1.0 - (dist - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.55 * pow(facingPos, 5.0) + 0.18 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.22 + 0.35 * facingPos)) * specStrength * (slope > 0.01 ? 1.0 : 0.0);

                // Center is completely transparent
                if (slope <= 0.001 && spec <= 0.005) {
                    return half4(0.0);
                }

                // Inward monotonic refraction along the 3 edges
                // Bounded displacement guarantees strictly positive derivative: d(sample_coord)/d(coord) > 0.
                // Eliminates vertical and horizontal caustic inversion so underlying icons/text remain strictly upright!
                float curBevel = inBottom ? wB : (inLeft ? wL : wR);
                float effRefr = min(refractPx, curBevel * 0.45);
                float2 offset = -n * (slope * effRefr);

                // Physical chromatic dispersion
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + viewSize - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                half4 sR = content.eval(cR);
                half4 sG = content.eval(cG);
                half4 sB = content.eval(cB);

                float contentAlpha = clamp((sR.a + sG.a + sB.a) * 0.33333, 0.0, 1.0);
                float3 col = float3(sR.r, sG.g, sB.b);

                // Vibrancy curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Backside inner shadow
                if (innerShadow > 0.001) {
                    float shadowW = clamp(maxBevel, 4.0, 28.0);
                    float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                    float ish = shadowBand * facingNeg * innerShadow;
                    col = col * (1.0 - 0.45 * ish);
                }

                col += float3(spec);
                col = clamp(col, float3(0.0), float3(1.0));

                float edgeAlpha = clamp(slope * contentAlpha + spec * 0.85, 0.0, 1.0) * cov;

                return half4(col * edgeAlpha, edgeAlpha);
            }
        """
    }

    data class LensParams(
        val cornerRadius: Float = 38f,
        val leftBevel: Float = 38f,
        val rightBevel: Float = 38f,
        val bottomBevel: Float = 38f,
        val refract: Float = 68f,
        val dispersion: Float = 0.09f,
        val lightX: Float = 0f,
        val lightY: Float = 1f,
        val spec: Float = 0.95f,
        val innerShadow: Float = 0.45f,
        val saturation: Float = 125f,
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
        sh.setFloatUniform("refractPx", p.refract)
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
