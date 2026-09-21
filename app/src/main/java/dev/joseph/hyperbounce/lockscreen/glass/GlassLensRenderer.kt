package dev.joseph.hyperbounce.lockscreen.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
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
 * - 100% Crystal-Clear interior transparency: flat glass centers pass through native underlying windows.
 * - Dual-shader architecture: foreground and backdrop sampled with independent subpixel alignment.
 * - Side specular catch-light: Left and Right glass rims gleam with authentic Apple iOS highlights.
 * - Normal-directed 3-tap sample smoothing (rimSoft): eliminates spiky caustic distortion and noise.
 * - Subpixel iOS twin-lobe specular highlight + edge hairline + soft inner glow.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassLensRenderer {

    companion object {
        private const val TAG = "HyperBounce_GlassLens"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        private const val LENS_AGSL = """
            uniform shader content;
            uniform shader backdrop;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float  cornerRadius;
            uniform float3 edgeBevels;     // x = left, y = right, z = bottom
            uniform float3 edgeRefracts;   // x = left, y = right, z = bottom
            uniform float  edgeStretch;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  hairlineStrength;
            uniform float  innerGlowStrength;
            uniform float  innerShadow;
            uniform float  satFactor;
            uniform float  rimSoftness;
            uniform float  blurRadius;
            uniform float  hasBackdrop;

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
                float refr = 0.0;
                float hStretch = 0.0;

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
                    refr = mix(edgeRefracts.z, edgeRefracts.x, clamp(u / (u + v + 0.0001), 0.0, 1.0));
                    hStretch = slope * abs(n.x);
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
                    refr = mix(edgeRefracts.z, edgeRefracts.y, clamp(u / (u + v + 0.0001), 0.0, 1.0));
                    hStretch = slope * abs(n.x);
                } else if (inLeft) {
                    // Straight Left Edge
                    float u = 1.0 - p.x / wL;
                    float edgeDist = clamp(u, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(-1.0, 0.0);
                    refr = edgeRefracts.x;
                    hStretch = slope;
                } else if (inRight) {
                    // Straight Right Edge
                    float u = 1.0 - (viewSize.x - p.x) / wR;
                    float edgeDist = clamp(u, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(1.0, 0.0);
                    refr = edgeRefracts.y;
                    hStretch = slope;
                } else if (inBottom) {
                    // Straight Bottom Edge
                    float v = 1.0 - (viewSize.y - p.y) / wB;
                    float edgeDist = clamp(v, 0.0, 1.0);
                    slope = edgeDist * edgeDist * (3.0 - 2.0 * edgeDist);
                    n = float2(0.0, 1.0);
                    refr = edgeRefracts.z;
                    hStretch = 0.0;
                }

                // === CASE 1: FLAT INTERIOR OF THE GLASS SHEET (ZERO REFRACTION) ===
                if (slope <= 0.005) {
                    half4 fg = content.eval(coord);
                    // 100% Crystal-Clear Transparency:
                    // If foreground is transparent (depth mode background or transparent glass sheet),
                    // return half4(0.0) so native underlying Android window shows through crystal clear!
                    if (fg.a <= 0.004) {
                        return half4(0.0);
                    }
                    return half4(fg.rgb * cov, fg.a * cov);
                }

                // === CASE 2: CURVED LIQUID GLASS BEVEL EDGES (OPTICAL REFRACTION) ===
                // Inward optical refraction along the edges (offset points inward: -n)
                float2 offset = -n * (slope * refr);
                // Dynamic squeeze & unsqueeze on side edges alone that modulates with vertical translation
                float worldY = p.y - viewPos.y;
                float squeezeWave = 0.28 * sin(worldY * 0.012);
                float dynamicStretch = edgeStretch * (1.0 + squeezeWave);
                offset.x += (-n.x) * (hStretch * dynamicStretch);

                // Physical chromatic dispersion
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + viewSize - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                // Normal-directed smoothing (rimSoft) and 2D frosted blur on bevel edges only
                float3 col;
                float fgAlpha = 0.0;
                float2 smN = n * (rimSoftness * slope + blurRadius * 0.5 * slope);
                float2 smT = float2(-n.y, n.x) * (blurRadius * 0.5 * slope);

                half4 fg;
                half4 bg = half4(0.0);

                if (blurRadius > 0.5) {
                    // 9-tap 2D frosted blur kernel along normal & tangent
                    float2 c0 = cG;
                    float2 cN1 = clamp(cG + smN * 0.5, lo, hi);
                    float2 cN2 = clamp(cG - smN * 0.5, lo, hi);
                    float2 cN3 = clamp(cG + smN, lo, hi);
                    float2 cN4 = clamp(cG - smN, lo, hi);
                    float2 cT1 = clamp(cG + smT * 0.5, lo, hi);
                    float2 cT2 = clamp(cG - smT * 0.5, lo, hi);
                    float2 cT3 = clamp(cG + smT, lo, hi);
                    float2 cT4 = clamp(cG - smT, lo, hi);

                    half4 fg0 = content.eval(c0);
                    half4 fgN1 = content.eval(cN1);
                    half4 fgN2 = content.eval(cN2);
                    half4 fgN3 = content.eval(cN3);
                    half4 fgN4 = content.eval(cN4);
                    half4 fgT1 = content.eval(cT1);
                    half4 fgT2 = content.eval(cT2);
                    half4 fgT3 = content.eval(cT3);
                    half4 fgT4 = content.eval(cT4);

                    fg = fg0 * 0.20 + (fgN1 + fgN2 + fgT1 + fgT2) * 0.12 + (fgN3 + fgN4 + fgT3 + fgT4) * 0.08;
                    fgAlpha = fg.a;

                    if (hasBackdrop > 0.5) {
                        half4 bg0 = backdrop.eval(c0);
                        half4 bgN1 = backdrop.eval(cN1);
                        half4 bgN2 = backdrop.eval(cN2);
                        half4 bgN3 = backdrop.eval(cN3);
                        half4 bgN4 = backdrop.eval(cN4);
                        half4 bgT1 = backdrop.eval(cT1);
                        half4 bgT2 = backdrop.eval(cT2);
                        half4 bgT3 = backdrop.eval(cT3);
                        half4 bgT4 = backdrop.eval(cT4);

                        bg = bg0 * 0.20 + (bgN1 + bgN2 + bgT1 + bgT2) * 0.12 + (bgN3 + bgN4 + bgT3 + bgT4) * 0.08;
                    }
                } else if (rimSoftness > 0.001) {
                    float2 c0 = cG;
                    float2 c1 = clamp(cG + smN, lo, hi);
                    float2 c2 = clamp(cG - smN, lo, hi);

                    half4 fg0 = content.eval(c0);
                    half4 fg1 = content.eval(c1);
                    half4 fg2 = content.eval(c2);
                    fg = fg0 * 0.34 + (fg1 + fg2) * 0.33;
                    fgAlpha = fg.a;

                    if (hasBackdrop > 0.5) {
                        half4 bg0 = backdrop.eval(c0);
                        half4 bg1 = backdrop.eval(c1);
                        half4 bg2 = backdrop.eval(c2);
                        bg = bg0 * 0.34 + (bg1 + bg2) * 0.33;
                    }
                } else {
                    fg = content.eval(cG);
                    fgAlpha = fg.a;
                    if (hasBackdrop > 0.5) {
                        bg = backdrop.eval(cG);
                    }
                }

                col = float3(
                    mix(bg.r, fg.r, fg.a),
                    mix(bg.g, fg.g, fg.a),
                    mix(bg.b, fg.b, fg.a)
                );

                if (dispersion > 0.005) {
                    half4 fgR = content.eval(cR);
                    half4 fgB = content.eval(cB);
                    half4 bgR = (hasBackdrop > 0.5) ? backdrop.eval(cR) : half4(0.0);
                    half4 bgB = (hasBackdrop > 0.5) ? backdrop.eval(cB) : half4(0.0);
                    float r = mix(bgR.r, fgR.r, fgR.a);
                    float b = mix(bgB.b, fgB.b, fgB.a);
                    col.r = mix(col.r, r, 0.6);
                    col.b = mix(col.b, b, 0.6);
                }

                // Color Vibrancy Curve with smooth inner fade
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float satFade = (satFactor - 1.0) * smoothstep(0.005, 0.40, slope);
                float amount = 1.0 + satFade * mix(0.3, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Backside inner shadow
                float2 lightDirNorm = length(lightDir) > 0.001 ? normalize(lightDir) : float2(0.0, 1.0);
                float facing = dot(n, -lightDirNorm);
                if (innerShadow > 0.001) {
                    float shadowW = clamp(maxBevel, 4.0, 28.0);
                    float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                    float ish = shadowBand * max(facing, 0.0) * innerShadow;
                    col = col * (1.0 - 0.45 * ish);
                }

                // iOS Optical Specular Model (All Edges):
                // 1. Crisp perimeter hairline reflection
                float hair = clamp(1.0 - abs(d + 1.0) / 1.8, 0.0, 1.0) * cov;

                // 2. Dual-direction specular lobe with omnidirectional ambient wrap so ALL edges catch light
                float wrapFacing = facing * 0.5 + 0.5;
                float lobeF = pow(clamp(wrapFacing, 0.0, 1.0), 3.0);
                float lobeB = pow(clamp(1.0 - wrapFacing, 0.0, 1.0), 3.0);
                float lobeSide = pow(abs(n.x), 2.0) * 0.45; // Side edge catch-light so left & right always gleam!

                // 3. Soft refractive inner glow band
                float bandW = clamp(maxBevel * 0.32, 2.0, 10.0);
                float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
                float glow = glowIn * pow(clamp(1.0 - (-d - 2.5) / bandW, 0.0, 1.0), 1.5) * cov;

                float spec = (hair * hairlineStrength * (lobeF * 0.55 + lobeB * 0.35 + lobeSide) + glow * innerGlowStrength * (lobeF * 0.70 + lobeSide * 0.50)) * specStrength;
                col += float3(spec);
                col = clamp(col, float3(0.0), float3(1.0));

                // Bevel alpha: smooth transition into the transparent center
                float bevelAlpha;
                if (fgAlpha > 0.004) {
                    bevelAlpha = cov;
                } else if (hasBackdrop > 0.5) {
                    float interiorBlend = smoothstep(0.005, 0.050, slope);
                    bevelAlpha = clamp(mix(spec, 1.0, interiorBlend) * cov, 0.0, 1.0);
                } else {
                    bevelAlpha = clamp(spec * cov, 0.0, 1.0);
                }

                return half4(col * bevelAlpha, bevelAlpha);
            }
        """
    }

    data class LensParams(
        val cornerRadius: Float = 36f,
        val leftBevel: Float = 32f,
        val rightBevel: Float = 32f,
        val bottomBevel: Float = 28f,
        val refract: Float = 24f,
        val leftRefract: Float = refract,
        val rightRefract: Float = refract,
        val bottomRefract: Float = refract,
        val edgeStretch: Float = 0f,
        val dispersion: Float = 0.030f,
        val lightX: Float = 0f,
        val lightY: Float = 1f,
        val spec: Float = 0.90f,
        val hairline: Float = 0.75f,
        val innerGlow: Float = 0.20f,
        val innerShadow: Float = 0.40f,
        val saturation: Float = 120f,
        val rimSoftness: Float = 2.0f,
        val blurRadius: Float = 0f
    )

    private val renderNode = RenderNode("LiquidGlass20Lens")
    private var shader: RuntimeShader? = null
    private var shaderBroken = false
    private var lastParams: LensParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastMargin = -1
    private var lastBackdropBmp: Bitmap? = null
    private var lastViewX = -1
    private var lastViewY = -1
    private var lastScreenW = 0
    private var lastScreenH = 0

    private val fallbackEmptyBitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    private val fallbackEmptyShader by lazy {
        BitmapShader(fallbackEmptyBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val backdropMatrix = Matrix()

    val isAvailable: Boolean get() = !shaderBroken

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        params: LensParams,
        margin: Int,
        backdropBmp: Bitmap? = null,
        viewX: Int = 0,
        viewY: Int = 0,
        screenW: Int = 0,
        screenH: Int = 0,
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

        val needsRebuild = params != lastParams || width != lastWidth || height != lastHeight ||
                margin != lastMargin || backdropBmp != lastBackdropBmp ||
                viewX != lastViewX || viewY != lastViewY ||
                screenW != lastScreenW || screenH != lastScreenH

        if (needsRebuild) {
            try {
                renderNode.setRenderEffect(buildEffect(sh, params, margin, width, height, backdropBmp, viewX, viewY, screenW, screenH))
            } catch (e: Exception) {
                Log.e(TAG, "Lens 2.0 RenderEffect build failed", e)
                shaderBroken = true
                return false
            }
            lastParams = params
            lastWidth = width
            lastHeight = height
            lastMargin = margin
            lastBackdropBmp = backdropBmp
            lastViewX = viewX
            lastViewY = viewY
            lastScreenW = screenW
            lastScreenH = screenH
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
        height: Int,
        backdropBmp: Bitmap?,
        viewX: Int,
        viewY: Int,
        screenW: Int,
        screenH: Int
    ): RenderEffect {
        sh.setFloatUniform("margin", margin.toFloat())
        sh.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
        sh.setFloatUniform("viewPos", viewX.toFloat(), viewY.toFloat())
        sh.setFloatUniform("screenSize", screenW.toFloat(), screenH.toFloat())
        sh.setFloatUniform("cornerRadius", p.cornerRadius)
        sh.setFloatUniform("edgeBevels", p.leftBevel, p.rightBevel, p.bottomBevel)

        val safeLeftRefr = p.leftRefract.coerceIn(0f, p.leftBevel.coerceAtLeast(1f) * 0.45f)
        val safeRightRefr = p.rightRefract.coerceIn(0f, p.rightBevel.coerceAtLeast(1f) * 0.45f)
        val safeBottomRefr = p.bottomRefract.coerceIn(0f, p.bottomBevel.coerceAtLeast(1f) * 0.35f)
        sh.setFloatUniform("edgeRefracts", safeLeftRefr, safeRightRefr, safeBottomRefr)
        sh.setFloatUniform("edgeStretch", p.edgeStretch)

        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("hairlineStrength", p.hairline)
        sh.setFloatUniform("innerGlowStrength", p.innerGlow)
        sh.setFloatUniform("innerShadow", p.innerShadow)
        sh.setFloatUniform("satFactor", p.saturation / 100f)
        sh.setFloatUniform("rimSoftness", p.rimSoftness)
        sh.setFloatUniform("blurRadius", p.blurRadius)

        val hasBackdrop = backdropBmp != null && !backdropBmp.isRecycled
        if (hasBackdrop) {
            val bmp = backdropBmp!!
            val backdropShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            backdropMatrix.reset()
            if (screenW > 0 && screenH > 0 && bmp.width > 0 && bmp.height > 0) {
                backdropMatrix.postScale(screenW.toFloat() / bmp.width.toFloat(), screenH.toFloat() / bmp.height.toFloat())
            }
            backdropMatrix.postTranslate((margin - viewX).toFloat(), (margin - viewY).toFloat())
            backdropShader.setLocalMatrix(backdropMatrix)
            sh.setInputShader("backdrop", backdropShader)
            sh.setFloatUniform("hasBackdrop", 1.0f)
        } else {
            sh.setInputShader("backdrop", fallbackEmptyShader)
            sh.setFloatUniform("hasBackdrop", 0.0f)
        }

        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }

    fun release() {
        renderNode.discardDisplayList()
        shader = null
        lastParams = null
        lastBackdropBmp = null
    }
}

