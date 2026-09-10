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
import dev.joseph.hyperbounce.lockscreen.glass.LockScreenWallpaperHelper
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import kotlin.math.max

/**
 * iOS-style Notification Center 3-Edge Liquid Glass Container Sheet View.
 * Renders the Apple iOS Liquid Glass 2.0 optical refraction model around
 * the notification container sheet, refracting the backdrop wallpaper darkened
 * by the shade scrim layer.
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

    var shadeBackgroundView: View? = null

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parent = parent as? View
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            if (parent != null && parent.width > 0) parent.width else resources.displayMetrics.widthPixels
        }
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            if (parent != null && parent.height > 0) parent.height else resources.displayMetrics.heightPixels
        }
        setMeasuredDimension(w, h)
    }

    @Volatile
    var currentSheetTranslationY: Float = 0f

    fun updateSheetTranslationY(finalY: Float) {
        currentSheetTranslationY = finalY
        translationY = finalY
        invalidate()
    }

    @Volatile
    private var isContentDirty = true

    fun markDirty() {
        isContentDirty = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        markDirty()
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

        val needsRecord = isContentDirty
        isContentDirty = false

        val sOffsetX = -margin.toFloat()
        val sOffsetY = currentSheetTranslationY - margin.toFloat()

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

            val success = renderer.draw(
                canvas, w, h, params, margin,
                sheetOffsetX = sOffsetX,
                sheetOffsetY = sOffsetY,
                needsRecord = needsRecord
            ) { recordingCanvas ->
                val backdropBmp = RealtimeBackdropHelper.getLatestBackdropBitmap()
                if (backdropBmp != null && !backdropBmp.isRecycled) {
                    val destRect = android.graphics.Rect(0, 0, screenW, screenH)
                    recordingCanvas.drawBitmap(backdropBmp, null, destRect, null)
                    // Apply notification shade dark scrim
                    recordingCanvas.drawColor(0x80101014.toInt())
                } else {
                    val wp = LockScreenWallpaperHelper.getLiveLockWallpaper(context)
                    if (wp != null) {
                        wp.setBounds(0, 0, screenW, screenH)
                        wp.draw(recordingCanvas)
                        // Apply notification shade dark scrim
                        recordingCanvas.drawColor(0x80101014.toInt())
                    } else {
                        val p = Paint().apply {
                            shader = LinearGradient(
                                0f, 0f, 0f, screenH.toFloat(),
                                0xFF2A2A2E.toInt(), 0xFF101014.toInt(),
                                Shader.TileMode.CLAMP
                            )
                        }
                        recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), p)
                    }
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        markDirty()
        LockScreenWallpaperHelper.registerIfNeeded(context)
        LockScreenWallpaperHelper.triggerAsyncWallpaperLoad(context)
        RealtimeBackdropHelper.triggerCapture(context) {
            markDirty()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        RealtimeBackdropHelper.stopLiveRefresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
    }
}
