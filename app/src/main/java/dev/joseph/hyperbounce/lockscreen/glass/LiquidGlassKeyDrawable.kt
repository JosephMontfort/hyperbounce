package dev.joseph.hyperbounce.lockscreen.glass

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder
import kotlin.math.min

/**
 * Circular Liquid Glass 2.0 Button Lens Drawable for Lockscreen PIN Pad Keys.
 * Ported from Liquid-Glass-Android LiquidGlassButton / GlassLensRenderer.
 */
class LiquidGlassKeyDrawable(private val density: Float) : Drawable() {

    companion object {
        private const val KEY_LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 size;
            uniform float  radius;
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  innerShadow;
            uniform float  satFactor;
            uniform float  press;

            float sdCircle(float2 p, float r) {
                return length(p) - r;
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin) - size * 0.5;
                float d = sdCircle(p, radius);

                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // Surface normal
                float2 n = length(p) > 0.001 ? normalize(p) : float2(0.0, 1.0);

                // Bevel slope
                float dist = -d;
                float t = clamp(dist / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope = edge * edge;

                // Press response
                float refr = refractPx * (1.0 + 0.60 * press);
                float2 offset = -n * (slope * refr);

                // Chromatic dispersion
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                float2 lo = float2(margin + 1.0, margin + 1.0);
                float2 hi = lo + size - float2(2.0, 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                half4 sR = content.eval(cR);
                half4 sG = content.eval(cG);
                half4 sB = content.eval(cB);

                float3 col = float3(sR.r, sG.g, sB.b);

                // Vibrancy curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Specular highlight
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);
                float bandW = clamp(bevel * 0.35, 2.0, 10.0);
                float rim = clamp(1.0 - (dist - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.60 * pow(facingPos, 5.0) + 0.20 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.25 + 0.40 * facingPos)) * specStrength * (1.0 - 0.35 * press);
                col += float3(spec);

                // Inner shadow
                float shadowW = clamp(bevel, 4.0, 24.0);
                float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                float ish = shadowBand * facingNeg * innerShadow;
                col = col * (1.0 - 0.45 * ish);

                // Press glow & tint
                float fillAlpha = mix(0.22, 0.45, press);
                float edgeAlpha = mix(fillAlpha, 0.90, slope);
                float alpha = edgeAlpha * cov;

                return half4(col * alpha, alpha);
            }
        """
    }

    private val renderNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RenderNode("LiquidGlassKeyLens")
    } else null

    private var shader: RuntimeShader? = null
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FFFFFF
    }
    private val fallbackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
    }

    var pressProgress: Float = 0f
        private set

    private var pressAnimator: ValueAnimator? = null

    fun setPressedState(pressed: Boolean) {
        pressAnimator?.cancel()
        val target = if (pressed) 1.0f else 0.0f
        val duration = if (pressed) 140L else 280L
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val cfg = LockScreenConfigHolder.keypad
        val diameter = min(w, h)
        val radius = diameter / 2f
        val margin = (24f * density).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderNode != null && canvas.isHardwareAccelerated) {
            val sh = shader ?: try {
                RuntimeShader(KEY_LENS_AGSL).also { shader = it }
            } catch (_: Throwable) { null }

            if (sh != null) {
                sh.setFloatUniform("margin", margin.toFloat())
                sh.setFloatUniform("size", w, h)
                sh.setFloatUniform("radius", radius)
                sh.setFloatUniform("bevel", cfg.buttonBevel * density)
                sh.setFloatUniform("refractPx", cfg.buttonRefraction * density)
                sh.setFloatUniform("dispersion", cfg.buttonDispersion)
                sh.setFloatUniform("lightDir", 0f, 1f)
                sh.setFloatUniform("specStrength", cfg.buttonSpecular)
                sh.setFloatUniform("innerShadow", cfg.buttonInnerShadow)
                sh.setFloatUniform("satFactor", cfg.buttonSaturation / 100f)
                sh.setFloatUniform("press", pressProgress)

                val lensEffect = RenderEffect.createRuntimeShaderEffect(sh, "content")
                val effect = if (cfg.buttonBlur > 0.01f) {
                    RenderEffect.createChainEffect(
                        lensEffect,
                        RenderEffect.createBlurEffect(cfg.buttonBlur * density, cfg.buttonBlur * density, Shader.TileMode.CLAMP)
                    )
                } else lensEffect

                renderNode.setRenderEffect(effect)
                renderNode.setPosition(-margin, -margin, (w + margin).toInt(), (h + margin).toInt())

                val recordingCanvas = renderNode.beginRecording((w + 2 * margin).toInt(), (h + 2 * margin).toInt())
                try {
                    recordingCanvas.drawColor(0x28FFFFFF)
                } finally {
                    renderNode.endRecording()
                }

                canvas.save()
                canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
                canvas.drawRenderNode(renderNode)
                canvas.restore()
                return
            }
        }

        // Fallback for API < 33
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val alpha = (0x33 + (pressProgress * 0x33)).toInt().coerceIn(0, 255)
        fallbackPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, fallbackPaint)
        fallbackStrokePaint.strokeWidth = 1.5f * density
        canvas.drawCircle(cx, cy, radius, fallbackStrokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fallbackPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fallbackPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
