package dev.joseph.hyperbounce.lockscreen.glass

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder
import kotlin.math.max
import kotlin.math.min

/**
 * Optimized Liquid Glass 2.0 Circular Lens for Lock Screen PIN Pad Keys.
 * 100% Real-Time Backdrop Refraction of Pure Unblurred Wallpaper at 60/120 FPS:
 * sdCircle SDF -> Numerical Normal Field -> Real-time Inward Refraction
 * -> Physical Chromatic Dispersion -> Apple-grade Adaptive Luminous Tint -> Vibrancy Curve -> Specular Reflection.
 *
 * Features hardware-synchronized Choreographer VSYNC live tracking so buttons continuously refract
 * the exact underlying wallpaper pixels during swipe gestures, cascade entrance, and button squishes.
 */
class LiquidGlassKeyOptimizedDrawable(
    private val view: View,
    private val density: Float
) : Drawable() {

    companion object {
        private const val TAG = "HyperBounce_KeyLensOpt"

        @Volatile
        var isCapturingBackdrop: Boolean = false

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

                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                // Expanded sampling area to permit genuine refraction of surrounding content within margin
                float2 lo = float2(1.0, 1.0);
                float2 hi = float2(viewSize.x + 2.0 * margin - 2.0, viewSize.y + 2.0 * margin - 2.0);
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);

                half4 sR = content.eval(cR);
                half4 sG = content.eval(cG);
                half4 sB = content.eval(cB);

                float3 col = float3(sR.r, sG.g, sB.b);

                // Apple-grade Adaptive Luminous Glass Tint
                float lumT = dot(col, float3(0.2126, 0.7152, 0.0722));
                float e = smoothstep(0.35, 0.75, lumT);
                col = mix(col, float3(1.0 - e), 0.12 + 0.06 * e);

                // Liquid Glass 2.0 Vibrancy Saturation Curve (Applied on top of tint so saturation remains rich and vibrant even when shrinking on press)
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                if (satFactor <= 1.0) {
                    col = mix(float3(lum), col, satFactor);
                } else {
                    float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                    float room = 1.0 - smoothstep(0.20, 0.85, satNow);
                    float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                    float amount = 1.0 + (satFactor - 1.0) * mix(0.30, 1.0, room * hl);
                    col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));
                }

                // Directional Specular Reflection
                float facing = dot(n, -lightDir);
                float facingPos = max(facing, 0.0);
                float facingNeg = max(-facing, 0.0);
                float bandW = clamp(bevel * 0.30, 2.0, 9.0);
                float rim = clamp(1.0 - (dist - 0.5) / bandW, 0.0, 1.0) * cov;
                float lobe = 0.55 * pow(facingPos, 5.0) + 0.18 * pow(facingNeg, 5.0) + 0.05;
                float hair = clamp(1.0 - abs(d + 1.0) / 1.5, 0.0, 1.0);
                float spec = (rim * lobe + hair * (0.22 + 0.35 * facingPos)) * specStrength * (1.0 - 0.35 * press);
                col += float3(spec);

                // Backside Inner Shadow for physical lens depth
                float shadowW = clamp(bevel, 4.0, 28.0);
                float shadowBand = pow(clamp(1.0 + d / shadowW, 0.0, 1.0), 1.5);
                float ish = shadowBand * facingNeg * innerShadow;
                col = col * (1.0 - 0.45 * ish);

                col = clamp(col, float3(0.0), float3(1.0));

                return half4(half3(col * cov), half(cov));
            }
        """
    }

    private val renderNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RenderNode("LiquidGlassKeyLensOpt")
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
    private val fallbackWallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var pressProgress: Float = 0f
        private set
    var touchLocalX: Float = 0f
        private set
    var touchLocalY: Float = 0f
        private set

    private var pressAnimator: ValueAnimator? = null

    @Volatile
    private var isLiveTracking = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isLiveTracking) return
            if (view.isAttachedToWindow && view.visibility == View.VISIBLE && view.windowVisibility == View.VISIBLE && view.alpha > 0.005f) {
                invalidateSelf()
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isLiveTracking = false
            }
        }
    }

    private val unblurredListener: () -> Unit = {
        view.post { invalidateSelf() }
    }

    init {
        LockScreenWallpaperHelper.addUnblurredWallpaperListener(unblurredListener)
    }

    fun startLiveTracking() {
        if (isLiveTracking) return
        isLiveTracking = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopLiveTracking() {
        if (!isLiveTracking) return
        isLiveTracking = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun onTouchDown(x: Float, y: Float) {
        touchLocalX = x
        touchLocalY = y
        startLiveTracking()
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, 1.0f).apply {
            duration = 60L
            interpolator = DecelerateInterpolator(2.2f)
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
        startLiveTracking()
        invalidateSelf()
    }

    fun onTouchUp() {
        startLiveTracking()
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, 0.0f).apply {
            duration = 240L
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    /**
     * Absolute protection against HWUI RenderThread cyclic display-list aborts:
     * Never draw a view if it is an ancestor of the target button or the target button itself.
     */
    private fun isAncestorOrSelf(candidate: View?, target: View): Boolean {
        if (candidate == null) return false
        if (candidate === target) return true
        var p = target.parent
        while (p != null) {
            if (p === candidate) return true
            p = p.parent
        }
        return false
    }

    /**
     * Obtains the pure unblurred lockscreen wallpaper directly.
     * Guaranteed 0ms overhead on UI thread, samples sharp original pixels.
     */
    private fun getPureWallpaperBackdrop(context: Context): Drawable? {
        return LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
    }

    /**
     * Safely draws a sibling view into the recording canvas only if it is NOT an ancestor,
     * is currently visible, and intersects the button's capture bounding box.
     */
    private fun drawSiblingLayerSafe(
        recordingCanvas: Canvas,
        sibling: View?,
        targetKeyView: View,
        viewX: Int,
        viewY: Int,
        margin: Int,
        keyW: Float,
        keyH: Float
    ) {
        if (sibling == null) return
        if (sibling.visibility != View.VISIBLE) return
        if (sibling.width <= 0 || sibling.height <= 0) return
        if (isAncestorOrSelf(sibling, targetKeyView)) return

        val alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            max(sibling.alpha, sibling.transitionAlpha)
        } else {
            sibling.alpha
        }
        if (alpha <= 0.01f) return

        try {
            sibling.getLocationOnScreen(parentLocation)
            val sibX = parentLocation[0]
            val sibY = parentLocation[1]

            val captureL = viewX - margin
            val captureT = viewY - margin
            val captureR = viewX + keyW + margin
            val captureB = viewY + keyH + margin

            val sibL = sibX.toFloat()
            val sibT = sibY.toFloat()
            val sibR = sibX + sibling.width.toFloat()
            val sibB = sibY + sibling.height.toFloat()

            // Strict intersection test before executing any drawing
            if (sibR <= captureL || sibL >= captureR || sibB <= captureT || sibT >= captureB) {
                return
            }

            recordingCanvas.save()
            recordingCanvas.translate(
                margin.toFloat() + (sibX - viewX).toFloat(),
                margin.toFloat() + (sibY - viewY).toFloat()
            )
            recordingCanvas.clipRect(0, 0, sibling.width, sibling.height)
            sibling.draw(recordingCanvas)
            recordingCanvas.restore()
        } catch (t: Throwable) {
            Log.d(TAG, "Suppressed sibling layer recursion", t)
        }
    }

    override fun draw(canvas: Canvas) {
        // Suppress drawing completely if inside backdrop recording to avoid infinite recursion
        if (isCapturingBackdrop) return

        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        // Auto-engage live tracking loop while attached, visible, and window is active
        if (!isLiveTracking && view.isAttachedToWindow && view.visibility == View.VISIBLE && view.windowVisibility == View.VISIBLE && view.alpha > 0.005f) {
            startLiveTracking()
        }

        val cfg = LockScreenConfigHolder.keypad
        val customDiameterPx = cfg.circleDiameter * density
        val radius = (customDiameterPx / 2f).coerceAtMost(min(w, h) / 2f).coerceAtLeast(16f)
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
                sh.setFloatUniform("bevel", max(cfg.buttonBevel * density, 1f))
                sh.setFloatUniform("refractPx", cfg.buttonRefraction * density)
                sh.setFloatUniform("dispersion", cfg.buttonDispersion)
                sh.setFloatUniform("lightDir", 0f, 1f)
                sh.setFloatUniform("specStrength", cfg.buttonSpecular)
                sh.setFloatUniform("innerShadow", cfg.buttonInnerShadow)
                sh.setFloatUniform("satFactor", cfg.buttonSaturation / 100f)
                sh.setFloatUniform("press", pressProgress)

                val lensEffect = RenderEffect.createRuntimeShaderEffect(sh, "content")
                // Only chain a blur if the user explicitly requested buttonBlur > 0; otherwise keep crystal-clear!
                val effect = if (cfg.buttonBlur > 0.01f) {
                    val blurPx = cfg.buttonBlur * density
                    RenderEffect.createChainEffect(
                        lensEffect,
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    )
                } else {
                    lensEffect
                }

                renderNode.setRenderEffect(effect)
                renderNode.setPosition(-margin, -margin, (w + margin).toInt(), (h + margin).toInt())

                val recordingCanvas = renderNode.beginRecording((w + 2 * margin).toInt(), (h + 2 * margin).toInt())
                isCapturingBackdrop = true

                try {
                    val screenW = view.resources.displayMetrics.widthPixels
                    val screenH = view.resources.displayMetrics.heightPixels

                    // 1. Base Layer: Pure Unblurred Lockscreen Wallpaper
                    val bgDrawable = getPureWallpaperBackdrop(view.context)
                    recordingCanvas.save()
                    recordingCanvas.translate(margin.toFloat() - viewX.toFloat(), margin.toFloat() - viewY.toFloat())
                    if (bgDrawable != null) {
                        bgDrawable.setBounds(0, 0, screenW, screenH)
                        bgDrawable.draw(recordingCanvas)
                    } else {
                        fallbackWallpaperPaint.shader = LinearGradient(
                            0f, 0f, 0f, screenH.toFloat(),
                            0xFF2C2C2E.toInt(), 0xFF121214.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), fallbackWallpaperPaint)
                    }
                    recordingCanvas.restore()

                    // 2. Underlying Sibling Layer: Injected LockScreen Liquid Glass Sheet (Feature 1)
                    val root = view.rootView
                    if (root != null) {
                        val sheetView = root.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                        drawSiblingLayerSafe(recordingCanvas, sheetView, view, viewX, viewY, margin, w, h)
                    }
                } finally {
                    isCapturingBackdrop = false
                    renderNode.endRecording()
                }

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

    fun release() {
        stopLiveTracking()
        pressAnimator?.cancel()
        LockScreenWallpaperHelper.removeUnblurredWallpaperListener(unblurredListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNode?.discardDisplayList()
        }
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
