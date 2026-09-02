package dev.joseph.hyperbounce.lockscreen.glass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassLensRenderer {

    companion object {
        private const val TAG = "HyperBounce_GlassLens"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        private const val LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float4 shape;
            uniform float4 cornerRadii;
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  innerShadow;
            uniform float4 tintColor;
            uniform float4 glassTint;
            uniform float  dimAmount;
            uniform float  satFactor;

            float radiusAt(float2 coord, float4 radii) {
                if (coord.x >= 0.0) {
                    if (coord.y <= 0.0) return radii.y;
                    else return radii.z;
                } else {
                    if (coord.y <= 0.0) return radii.x;
                    else return radii.w;
                }
            }

            float sdRoundedBox(float2 p, float2 b, float4 radii) {
                float r = radiusAt(p, radii);
                float2 q = abs(p) - b + float2(r);
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                float2 halfSize = shape.zw;
                float2 centered = p - shape.xy;
                float d = sdRoundedBox(centered, halfSize, cornerRadii);

                // Anti-aliased feathering: 1.5px
                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // SDF gradient for surface normal
                float2 n = float2(
                    sdRoundedBox(centered + float2(1.0, 0.0), halfSize, cornerRadii) - sdRoundedBox(centered - float2(1.0, 0.0), halfSize, cornerRadii),
                    sdRoundedBox(centered + float2(0.0, 1.0), halfSize, cornerRadii) - sdRoundedBox(centered - float2(0.0, 1.0), halfSize, cornerRadii)
                );
                float nLen = length(n);
                if (nLen > 0.0001) {
                    n = n / nLen;
                } else {
                    n = float2(0.0, -1.0);
                }

                // Thickness slope
                float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope = edge * edge;

                // Real-time inward refraction
                float refr = refractPx;
                float2 offset = -n * (slope * refr);

                // Chromatic dispersion (per-channel RGB refraction)
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                // Clamp to safe content bounds to prevent transparent black border
                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + viewSize - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                float3 col = float3(
                    content.eval(cR).r,
                    content.eval(cG).g,
                    content.eval(cB).b
                );

                // Vibrancy saturation curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                if (satFactor <= 1.0) {
                    col = mix(float3(lum), col, satFactor);
                } else {
                    float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                    float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                    float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                    float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                    col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));
                }

                // Tint & dimming
                col = mix(col, tintColor.rgb, tintColor.a);
                col = col * (1.0 - dimAmount);

                if (glassTint.a > 0.002) {
                    float lumTint = dot(col, float3(0.2126, 0.7152, 0.0722));
                    float3 absorbed = col * mix(float3(1.0), glassTint.rgb, 0.85);
                    float3 scattered = glassTint.rgb * (0.38 * (1.0 - lumTint));
                    col = mix(col, clamp(absorbed + scattered, float3(0.0), float3(1.0)), glassTint.a);
                }

                // Specular highlight
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);

                float bandW = clamp(bevel * 0.35, 2.0, 10.0);
                float rim = clamp(1.0 - (-d - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.55 * pow(facingPos, 5.0) + 0.18 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.25 + 0.38 * facingPos)) * specStrength;
                col += float3(spec);

                // Backside inner shadow
                float shadowW = clamp(bevel, 4.0, 32.0);
                float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                float ish = shadowBand * facingNeg * innerShadow;
                col = col * (1.0 - 0.45 * ish);

                col = clamp(col, float3(0.0), float3(1.0));
                return half4(half3(col * cov), half(cov));
            }
        """
    }

    data class LensParams(
        val blurRadius: Float = 24f,
        val shapeCX: Float,
        val shapeCY: Float,
        val shapeHW: Float,
        val shapeHH: Float,
        val cornerRadii: FloatArray, // (topLeft, topRight, bottomRight, bottomLeft)
        val bevel: Float = 36f,
        val refract: Float = 40f,
        val dispersion: Float = 0.20f,
        val lightX: Float = 0f,
        val lightY: Float = 1f,
        val spec: Float = 0.85f,
        val innerShadow: Float = 0.40f,
        val tint: Int = 0x1AFFFFFF,
        val glassTint: Int = 0x00000000,
        val dim: Float = 0.05f,
        val saturation: Float = 125f
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LensParams) return false
            return blurRadius == other.blurRadius &&
                    shapeCX == other.shapeCX && shapeCY == other.shapeCY &&
                    shapeHW == other.shapeHW && shapeHH == other.shapeHH &&
                    cornerRadii.contentEquals(other.cornerRadii) &&
                    bevel == other.bevel && refract == other.refract &&
                    dispersion == other.dispersion &&
                    lightX == other.lightX && lightY == other.lightY &&
                    spec == other.spec && innerShadow == other.innerShadow &&
                    tint == other.tint && glassTint == other.glassTint &&
                    dim == other.dim && saturation == other.saturation
        }

        override fun hashCode(): Int {
            var result = blurRadius.hashCode()
            result = 31 * result + shapeCX.hashCode()
            result = 31 * result + shapeCY.hashCode()
            result = 31 * result + shapeHW.hashCode()
            result = 31 * result + shapeHH.hashCode()
            result = 31 * result + cornerRadii.contentHashCode()
            return result
        }
    }

    private val renderNode = RenderNode("HyperBounceLockSheetLens")
    private var shader: RuntimeShader? = null
    private var shaderBroken = false
    private var lastParams: LensParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastMargin = -1

    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

    val isAvailable: Boolean get() = !shaderBroken

    fun draw(
        canvas: Canvas,
        glassView: View,
        backdropSource: View,
        params: LensParams,
        margin: Int
    ): Boolean {
        if (shaderBroken || !canvas.isHardwareAccelerated) return false
        val width = glassView.width
        val height = glassView.height
        if (width <= 0 || height <= 0) return false

        val sh = shader ?: try {
            RuntimeShader(LENS_AGSL).also { shader = it }
        } catch (e: Exception) {
            Log.e(TAG, "AGSL compilation failed", e)
            shaderBroken = true
            return false
        }

        if (params != lastParams || width != lastWidth || height != lastHeight || margin != lastMargin) {
            try {
                renderNode.setRenderEffect(buildEffect(sh, params, margin, width, height))
            } catch (e: Exception) {
                Log.e(TAG, "RenderEffect build failed", e)
                shaderBroken = true
                return false
            }
            lastParams = params
            lastWidth = width
            lastHeight = height
            lastMargin = margin
        }

        glassView.getLocationOnScreen(location)
        backdropSource.getLocationOnScreen(parentLocation)
        val offsetX = (location[0] - parentLocation[0]).toFloat()
        val offsetY = (location[1] - parentLocation[1]).toFloat()

        renderNode.setPosition(-margin, -margin, width + margin, height + margin)
        val recordingCanvas = renderNode.beginRecording(width + 2 * margin, height + 2 * margin)
        try {
            recordingCanvas.translate(margin - offsetX, margin - offsetY)
            glassView.visibility = View.INVISIBLE
            try {
                backdropSource.draw(recordingCanvas)
            } finally {
                glassView.visibility = View.VISIBLE
            }
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
        sh.setFloatUniform("shape", p.shapeCX, p.shapeCY, p.shapeHW, p.shapeHH)
        sh.setFloatUniform(
            "cornerRadii",
            p.cornerRadii[0],
            p.cornerRadii[1],
            p.cornerRadii[2],
            p.cornerRadii[3]
        )
        sh.setFloatUniform("bevel", p.bevel)
        sh.setFloatUniform("refractPx", p.refract)
        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("innerShadow", p.innerShadow)
        sh.setFloatUniform(
            "tintColor",
            Color.red(p.tint) / 255f,
            Color.green(p.tint) / 255f,
            Color.blue(p.tint) / 255f,
            Color.alpha(p.tint) / 255f
        )
        sh.setFloatUniform(
            "glassTint",
            Color.red(p.glassTint) / 255f,
            Color.green(p.glassTint) / 255f,
            Color.blue(p.glassTint) / 255f,
            Color.alpha(p.glassTint) / 255f
        )
        sh.setFloatUniform("dimAmount", p.dim)
        sh.setFloatUniform("satFactor", p.saturation / 100f)

        val lens = RenderEffect.createRuntimeShaderEffect(sh, "content")
        return if (p.blurRadius > 0.01f) {
            RenderEffect.createChainEffect(
                lens,
                RenderEffect.createBlurEffect(p.blurRadius, p.blurRadius, Shader.TileMode.CLAMP)
            )
        } else {
            lens
        }
    }

    fun release() {
        renderNode.discardDisplayList()
        shader = null
        lastParams = null
    }
}
