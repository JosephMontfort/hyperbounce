package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import dev.joseph.hyperbounce.lockscreen.glass.GlassLensRenderer
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import kotlin.math.max

/**
 * iOS-style Notification Center 3-Edge Liquid Glass Container Sheet View.
 * Renders the Apple iOS Liquid Glass 2.0 optical refraction model around
 * the notification container sheet without sampling wallpaper, refracting the
 * real-time blurred shade background layer (ShadeBackgroundView).
 */
class NotificationGlassSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val renderer: GlassLensRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlassLensRenderer()
        } else null

    private val fallbackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }
    private val boundsRect = RectF()
    private val location = IntArray(2)
    private val shadeBgLocation = IntArray(2)

    var shadeBackgroundView: View? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parent = parent as? View
        val w = if (parent != null && parent.width > 0) parent.width else resources.displayMetrics.widthPixels
        val h = if (parent != null && parent.height > 0) parent.height else resources.displayMetrics.heightPixels
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val parent = parent as? View
        val w = if (width > 0) width else (parent?.width ?: resources.displayMetrics.widthPixels)
        val h = if (height > 0) height else (parent?.height ?: resources.displayMetrics.heightPixels)
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled) return

        val leftBevel = cfg.leftBevelWidth * density
        val rightBevel = cfg.rightBevelWidth * density
        val bottomBevel = cfg.bottomBevelWidth * density
        val maxBevel = max(max(leftBevel, rightBevel), bottomBevel)
        val refractAmount = cfg.refractionHeight * density
        val cornerRadius = cfg.cornerRadius * density
        val margin = ((maxBevel + refractAmount * 0.4f) + 16f * density).toInt().coerceAtLeast(32)

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
            val params = GlassLensRenderer.LensParams(
                cornerRadius = cornerRadius,
                leftBevel = leftBevel,
                rightBevel = rightBevel,
                bottomBevel = bottomBevel,
                refract = refractAmount,
                dispersion = cfg.dispersionStrength,
                lightX = 0f,
                lightY = 1f,
                spec = cfg.specularStrength,
                innerShadow = cfg.innerShadow,
                saturation = cfg.saturationFactor,
                blurRadius = cfg.blurRadius * density
            )

            val success = renderer.draw(canvas, w, h, params, margin) { recordingCanvas ->
                // Base solid dark gradient so the glass tint has rich contrast and specular pop
                val basePaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, screenH.toFloat(),
                        0xFF18181C.toInt(), 0xFF0E0E12.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), basePaint)

                // Refract the live blurred shade background layer (ShadeBackgroundView)
                val shadeBg = shadeBackgroundView
                if (shadeBg != null && shadeBg.visibility == VISIBLE) {
                    shadeBg.getLocationOnScreen(shadeBgLocation)
                    val shadeX = shadeBgLocation[0]
                    val shadeY = shadeBgLocation[1]

                    recordingCanvas.save()
                    recordingCanvas.translate(
                        margin.toFloat() - viewX.toFloat() + shadeX.toFloat(),
                        margin.toFloat() - viewY.toFloat() + shadeY.toFloat()
                    )
                    shadeBg.draw(recordingCanvas)
                    recordingCanvas.restore()
                }
            }

            if (!success) {
                drawFallbackBorder(canvas, w.toFloat(), h.toFloat(), cornerRadius)
            }
        } else {
            drawFallbackBorder(canvas, w.toFloat(), h.toFloat(), cornerRadius)
        }
    }

    private fun drawFallbackBorder(canvas: Canvas, w: Float, h: Float, radius: Float) {
        val density = resources.displayMetrics.density
        fallbackBorderPaint.strokeWidth = 2.0f * density
        boundsRect.set(0f, 0f, w, h)
        val radii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        val path = android.graphics.Path().apply {
            addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(path, fallbackBorderPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
    }
}
