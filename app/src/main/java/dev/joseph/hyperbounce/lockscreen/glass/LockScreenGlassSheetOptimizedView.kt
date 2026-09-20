package dev.joseph.hyperbounce.lockscreen.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder
import kotlin.math.max

/**
 * Optimized 120Hz Liquid Glass Sheet View for Lock Screen.
 * Eliminates recursive tree-walking getLocationOnScreen() from onDraw() while keeping
 * AGSL Liquid Glass 2.0 optics, refraction, and shaders 100% bit-for-bit identical.
 */
class LockScreenGlassSheetOptimizedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val renderer: GlassLensRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlassLensRenderer()
        } else null

    @Volatile
    var swipeProgress: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            if (field != clamped) {
                field = clamped
                postInvalidateOnAnimation()
            }
        }

    private val fallbackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        style = Paint.Style.STROKE
    }
    private val boundsRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parent = parent as? View
        val w = if (parent != null && parent.width > 0) parent.width else resources.displayMetrics.widthPixels
        val h = if (parent != null && parent.height > 0) parent.height else resources.displayMetrics.heightPixels
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return
        // When stationary (resting position), zero glass effects and zero overdraw
        if (swipeProgress <= 0.001f) return

        val parent = parent as? View
        val w = if (width > 0) width else (parent?.width ?: resources.displayMetrics.widthPixels)
        val h = if (height > 0) height else (parent?.height ?: resources.displayMetrics.heightPixels)
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cfg = LockScreenConfigHolder.sheet

        // Smooth cubic Hermite curve for gradual, non-sudden incoming glass effects exactly like iOS
        val t = (swipeProgress / 0.18f).coerceIn(0.0f, 1.0f)
        val smoothIncoming = t * t * (3.0f - 2.0f * t)

        val leftBevel = cfg.leftBevelWidth * density * smoothIncoming
        val rightBevel = cfg.rightBevelWidth * density * smoothIncoming
        val bottomBevel = cfg.bottomBevelWidth * density * smoothIncoming
        val maxBevel = max(max(leftBevel, rightBevel), bottomBevel)
        val leftRefract = cfg.sideRefraction * density * smoothIncoming
        val rightRefract = cfg.sideRefraction * density * smoothIncoming
        val bottomRefract = cfg.bottomRefraction * density * smoothIncoming
        val maxRefract = max(max(leftRefract, rightRefract), bottomRefract)
        val cornerRadius = cfg.cornerRadius * density * smoothIncoming
        val edgeStretch = (cfg.edgeStretch * density) * smoothIncoming
        val margin = ((maxBevel + maxRefract * 0.4f) + 16f * density).toInt().coerceAtLeast(32)

        val wp = LockScreenWallpaperHelper.getLiveLockWallpaper(context)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        // Zero-overhead coordinate resolution: avoid recursive getLocationOnScreen() view-tree walks in onDraw()
        val viewX = 0
        val viewY = (parent?.translationY ?: translationY).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
            val params = GlassLensRenderer.LensParams(
                cornerRadius = cornerRadius,
                leftBevel = leftBevel,
                rightBevel = rightBevel,
                bottomBevel = bottomBevel,
                refract = maxRefract,
                leftRefract = leftRefract,
                rightRefract = rightRefract,
                bottomRefract = bottomRefract,
                edgeStretch = edgeStretch,
                dispersion = cfg.dispersionStrength,
                lightX = 0f,
                lightY = 1f,
                spec = cfg.specularStrength * smoothIncoming,
                hairline = cfg.hairlineStrength * smoothIncoming,
                innerGlow = cfg.innerGlowStrength * smoothIncoming,
                innerShadow = cfg.innerShadow * smoothIncoming,
                saturation = cfg.saturationFactor,
                rimSoftness = cfg.rimSoftness,
                blurRadius = cfg.blurRadius * density
            )

            val success = renderer.draw(canvas, w, h, params, margin) { recordingCanvas ->
                recordingCanvas.translate(margin.toFloat() - viewX.toFloat(), margin.toFloat() - viewY.toFloat())
                if (wp != null) {
                    wp.setBounds(0, 0, screenW, screenH)
                    wp.draw(recordingCanvas)
                } else {
                    val p = Paint().apply {
                        shader = LinearGradient(
                            0f, 0f, 0f, screenH.toFloat(),
                            0xFF1C1C1E.toInt(), 0xFF0A0A0C.toInt(),
                            Shader.TileMode.CLAMP
                        )
                    }
                    recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), p)
                }
            }
            if (success) return
        }

        // Fallback for non-hardware or API < 33
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val radii = floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        val path = Path().apply {
            addRoundRect(boundsRect, radii, Path.Direction.CW)
        }
        fallbackBorderPaint.strokeWidth = 2.5f * density
        canvas.drawPath(path, fallbackBorderPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LockScreenWallpaperHelper.registerIfNeeded(context)
        LockScreenWallpaperHelper.triggerAsyncWallpaperLoad(context)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
    }
}
