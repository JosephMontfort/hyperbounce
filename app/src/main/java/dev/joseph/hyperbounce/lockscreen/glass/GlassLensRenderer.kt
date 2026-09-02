package dev.joseph.hyperbounce.lockscreen.glass

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Liquid Glass 2.0 AGSL Lens Pipeline for Lock Screen Sheet.
 * Implements Apple iOS Liquid Glass 2.0 optical model:
 * 3-sided SDF -> Numerical Normal Field -> Real-time Inward Refraction -> Chromatic Dispersion
 * -> Directional Specular Highlight -> Inner Shadow -> Vibrancy Saturation.
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
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  innerShadow;
            uniform float  satFactor;

            // Liquid Glass 2.0 Exact 3-sided SDF (Left, Right, Bottom with rounded bottom corners, open top)
            float sd3SidedSheet(float2 p, float2 size, float r) {
                // Bottom-left rounded corner
                if (p.x < r && p.y > (size.y - r)) {
                    return length(p - float2(r, size.y - r)) - r;
                }
                // Bottom-right rounded corner
                if (p.x > (size.x - r) && p.y > (size.y - r)) {
                    return length(p - float2(size.x - r, size.y - r)) - r;
                }
                // Straight 3-sided borders
                float dLeft = -p.x;
                float dRight = -(size.x - p.x);
                float dBottom = -(size.y - p.y);
                return max(max(dLeft, dRight), dBottom);
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                float d = sd3SidedSheet(p, viewSize, cornerRadius);

                // Anti-aliased feathering: 1.5px along outer boundary
                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // SDF gradient for outward surface normal (N)
                float2 n = float2(
                    sd3SidedSheet(p + float2(1.0, 0.0), viewSize, cornerRadius) - sd3SidedSheet(p - float2(1.0, 0.0), viewSize, cornerRadius),
                    sd3SidedSheet(p + float2(0.0, 1.0), viewSize, cornerRadius) - sd3SidedSheet(p - float2(0.0, 1.0), viewSize, cornerRadius)
                );
                float nLen = length(n);
                if (nLen > 0.0001) {
                    n = n / nLen;
                } else {
                    n = float2(0.0, 1.0);
                }

                // Thickness slope: 1.0 at outer edge, 0.0 inside the flat sheet
                float dist = -d;
                float t = clamp(dist / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope = edge * edge;

                // Specular highlight: dot(N, -L) main lobe + opposite side bounce + hair edge
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);

                float bandW = clamp(bevel * 0.30, 2.0, 9.0);
                float rim = clamp(1.0 - (dist - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.55 * pow(facingPos, 5.0) + 0.18 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.22 + 0.35 * facingPos)) * specStrength;

                // Center of screen is completely untouched (100% crystal-clear wallpaper transparency)
                if (slope <= 0.001 && spec <= 0.005) {
                    return half4(0.0);
                }

                // Liquid Glass 2.0 Inward Refraction (intensified lens magnification along the 3 edges)
                float refr = refractPx;
                float2 offset = -n * (slope * refr);

                // Liquid Glass 2.0 Physical Chromatic Dispersion (calibrated R/G/B normal separation)
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                // Safe clamping to avoid transparent black borders
                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + viewSize - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                half4 sR = content.eval(cR);
                half4 sG = content.eval(cG);
                half4 sB = content.eval(cB);

                float3 col = float3(sR.r, sG.g, sB.b);

                // Liquid Glass 2.0 Vibrancy Saturation Curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Backside Inner Shadow on bevel transition
                float shadowW = clamp(bevel, 4.0, 28.0);
                float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                float ish = shadowBand * facingNeg * innerShadow;
                col = col * (1.0 - 0.45 * ish);

                // Add specular reflection
                col += float3(spec);
                col = clamp(col, float3(0.0), float3(1.0));

                // Alpha of the glass edge: 1.0 on bevel rim, smoothly dropping to 0.0 inside
                float edgeAlpha = clamp(slope + spec * 0.85, 0.0, 1.0) * cov;

                return half4(col * edgeAlpha, edgeAlpha);
            }
        """
    }

    data class LensParams(
        val cornerRadius: Float = 38f,
        val bevel: Float = 38f,
        val refract: Float = 68f,
        val dispersion: Float = 0.09f,
        val lightX: Float = 0f,
        val lightY: Float = 1f,
        val spec: Float = 0.95f,
        val innerShadow: Float = 0.45f,
        val saturation: Float = 125f
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
        sh.setFloatUniform("bevel", p.bevel)
        sh.setFloatUniform("refractPx", p.refract)
        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("innerShadow", p.innerShadow)
        sh.setFloatUniform("satFactor", p.saturation / 100f)

        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }

    fun release() {
        renderNode.discardDisplayList()
        shader = null
        lastParams = null
    }
}
