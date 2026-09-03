package dev.joseph.hyperbounce.lockscreen.glass

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder
import kotlin.math.min

/**
 * Authentic Liquid Glass 2.0 Circular Lens for Lock Screen PIN Pad Keys.
 * Captures 100% real-time backdrop directly beneath each button (live lockscreen blur, animations, and wallpaper):
 * sdCircle SDF -> Numerical Normal Field -> Real-time Inward Refraction
 * -> Physical Chromatic Dispersion -> Adaptive Luminous Tint -> Vibrancy Curve -> Specular Reflection.
 */
class LiquidGlassKeyDrawable(
    private val view: View,
    private val density: Float
) : Drawable() {

    companion object {
        private const val TAG = "HyperBounce_KeyLens"

        @Volatile
        var isCapturingBackdrop: Boolean = false

        private val setTransitionVisibilityMethod by lazy {
            try {
                val method = View::class.java.getDeclaredMethod("setTransitionVisibility", Int::class.javaPrimitiveType)
                method.isAccessible = true
                method
            } catch (_: Throwable) {
                null
            }
        }

        fun setTransitionVisibilitySafe(v: View?, visibility: Int) {
            if (v == null) return
            try {
                setTransitionVisibilityMethod?.invoke(v, visibility)
            } catch (_: Throwable) {
                try {
                    v.visibility = visibility
                } catch (_: Throwable) {}
            }
        }

        private var cachedWallpaper: Drawable? = null
        private var lastWallpaperFetchTime = 0L

        private fun getSharedWallpaper(context: Context): Drawable? {
            val now = SystemClock.uptimeMillis()
            if (cachedWallpaper == null || now - lastWallpaperFetchTime > 4000L) {
                lastWallpaperFetchTime = now
                try {
                    val wm = WallpaperManager.getInstance(context)
                    cachedWallpaper = wm.drawable ?: wm.peekDrawable()
                } catch (t: Throwable) {
                    Log.w(TAG, "Wallpaper fetch fallback error", t)
                }
            }
            return cachedWallpaper
        }

        private const val KEY_LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float2 circleCenter;
            uniform float  radius;
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  innerShadow;
            uniform float  satFactor;
            uniform float  press;
            uniform float2 touchPos;
            uniform float  touchAmp;

            float sdCircle(float2 p, float r) {
                return length(p) - r;
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                float2 relP = p - circleCenter;
                float d = sdCircle(relP, radius);

                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                float relDist = length(relP);
                float2 n = relDist > 0.001 ? (relP / relDist) : float2(0.0, -1.0);

                float dist = -d;
                float t = clamp(dist / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope = edge * edge;

                float refr = refractPx * (1.0 + 0.60 * press);
                float2 offset = -n * (slope * refr);

                if (touchAmp > 0.001) {
                    float2 tp = relP - touchPos;
                    float tr = length(tp);
                    float sigma = max(radius * 0.75, 1.0);
                    float bump = touchAmp * exp(-(tr * tr) / (sigma * sigma * 0.35));
                    if (tr > 1.0) {
                        offset -= (tp / tr) * (bump * refr * 0.50);
                    }
                }

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

                float3 col = float3(sR.r, sG.g, sB.b);

                // Liquid Glass 2.0 Vibrancy Saturation Curve
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                float room = 1.0 - smoothstep(0.20, 0.85, satNow);
                float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                float amount = 1.0 + (satFactor - 1.0) * mix(0.30, 1.0, room * hl);
                col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

                // Adaptive Luminous Glass Tint (Radiant crystal glow on dark backgrounds)
                float lumT = dot(col, float3(0.2126, 0.7152, 0.0722));
                float e = smoothstep(0.30, 0.75, lumT);
                float tintAlpha = mix(0.18, 0.38, press);
                col = mix(col, float3(1.0 - e), tintAlpha * (0.60 + 0.40 * slope));

                // Directional Specular Reflection
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);
                float bandW = clamp(bevel * 0.35, 2.0, 10.0);
                float rim = clamp(1.0 - (dist - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.60 * pow(facingPos, 5.0) + 0.20 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.28 + 0.45 * facingPos)) * specStrength * (1.0 - 0.30 * press);
                col += float3(spec);

                // Backside Inner Shadow for physical lens depth
                float shadowW = clamp(bevel, 4.0, 24.0);
                float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                float ish = shadowBand * facingNeg * innerShadow;
                col = col * (1.0 - 0.45 * ish);

                col = clamp(col, float3(0.0), float3(1.0));

                return half4(col * cov, cov);
            }
        """
    }

    private val renderNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RenderNode("LiquidGlassKeyLens")
    } else null

    private var shader: RuntimeShader? = null
    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

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
    var touchLocalX: Float = 0f
        private set
    var touchLocalY: Float = 0f
        private set

    private var pressAnimator: ValueAnimator? = null

    fun onTouchDown(x: Float, y: Float) {
        touchLocalX = x
        touchLocalY = y
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, 1.0f).apply {
            duration = 140L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    fun onTouchMove(x: Float, y: Float) {
        touchLocalX = x
        touchLocalY = y
        invalidateSelf()
    }

    fun onTouchUp() {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, 0.0f).apply {
            duration = 280L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (isCapturingBackdrop) return

        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val cfg = LockScreenConfigHolder.keypad
        val customDiameterPx = cfg.circleDiameter * density
        val radius = (customDiameterPx / 2f).coerceAtMost(min(w, h) / 2f)
        val cx = w / 2f
        val cy = h / 2f

        val margin = ((cfg.buttonBevel + cfg.buttonRefraction * 0.5f) * density + 16f * density).toInt().coerceAtLeast(32)

        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderNode != null && canvas.isHardwareAccelerated) {
            val sh = shader ?: try {
                RuntimeShader(KEY_LENS_AGSL).also { shader = it }
            } catch (t: Throwable) {
                Log.e(TAG, "Shader compilation error", t)
                null
            }

            if (sh != null) {
                sh.setFloatUniform("margin", margin.toFloat())
                sh.setFloatUniform("viewSize", w, h)
                sh.setFloatUniform("circleCenter", cx, cy)
                sh.setFloatUniform("radius", radius)
                sh.setFloatUniform("bevel", cfg.buttonBevel * density)
                sh.setFloatUniform("refractPx", cfg.buttonRefraction * density)
                sh.setFloatUniform("dispersion", cfg.buttonDispersion)
                sh.setFloatUniform("lightDir", 0f, 1f)
                sh.setFloatUniform("specStrength", cfg.buttonSpecular)
                sh.setFloatUniform("innerShadow", cfg.buttonInnerShadow)
                sh.setFloatUniform("satFactor", cfg.buttonSaturation / 100f)
                sh.setFloatUniform("press", pressProgress)
                sh.setFloatUniform("touchPos", touchLocalX - cx, touchLocalY - cy)
                sh.setFloatUniform("touchAmp", if (pressProgress > 0.01f) pressProgress else 0f)

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

                var capturedLive = false
                val pinContainer = (view.parent as? ViewGroup)
                val backdropSource = (pinContainer?.parent as? View) ?: pinContainer?.rootView

                if (backdropSource != null && backdropSource.width > 0 && backdropSource.height > 0) {
                    backdropSource.getLocationOnScreen(parentLocation)
                    val offsetX = (viewX - parentLocation[0]).toFloat()
                    val offsetY = (viewY - parentLocation[1]).toFloat()

                    recordingCanvas.save()
                    recordingCanvas.translate(margin.toFloat() - offsetX, margin.toFloat() - offsetY)

                    isCapturingBackdrop = true
                    // Temporarily hide the PIN container layout so no PIN buttons are drawn into the lens backdrop
                    setTransitionVisibilitySafe(pinContainer, View.INVISIBLE)
                    try {
                        backdropSource.draw(recordingCanvas)
                        capturedLive = true
                    } catch (t: Throwable) {
                        Log.w(TAG, "Live backdrop draw failed, falling back to wallpaper", t)
                    } finally {
                        setTransitionVisibilitySafe(pinContainer, View.VISIBLE)
                        isCapturingBackdrop = false
                        recordingCanvas.restore()
                    }
                }

                if (!capturedLive) {
                    val wp = getSharedWallpaper(view.context)
                    val screenW = view.resources.displayMetrics.widthPixels
                    val screenH = view.resources.displayMetrics.heightPixels

                    recordingCanvas.save()
                    recordingCanvas.translate(margin.toFloat() - viewX.toFloat(), margin.toFloat() - viewY.toFloat())
                    if (wp != null) {
                        wp.setBounds(0, 0, screenW, screenH)
                        wp.draw(recordingCanvas)
                    } else {
                        val p = Paint().apply {
                            shader = LinearGradient(
                                0f, 0f, 0f, screenH.toFloat(),
                                0xFF2C2C2E.toInt(), 0xFF121214.toInt(),
                                Shader.TileMode.CLAMP
                            )
                        }
                        recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), p)
                    }
                    recordingCanvas.restore()
                }

                renderNode.endRecording()

                canvas.save()
                canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
                canvas.drawRenderNode(renderNode)
                canvas.restore()
                return
            }
        }

        // Fallback for non-hardware or API < 33
        val alpha = (0x33 + (pressProgress * 0x44)).toInt().coerceIn(0, 255)
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
