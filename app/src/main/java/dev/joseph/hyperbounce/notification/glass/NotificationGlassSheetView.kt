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
    private val sheetClipPath = android.graphics.Path()
    private val backdropDestRect = android.graphics.Rect()
    private val wallpaperLoadListener = {
        postInvalidateOnAnimation()
    }
    private val depthSubjectListener = {
        postInvalidateOnAnimation()
    }
    private val location = IntArray(2)

    var shadeBackgroundView: View? = null

    var expansionFraction: Float = 0f
        set(value) {
            if (kotlin.math.abs(field - value) > 0.005f) {
                field = value
                postInvalidateOnAnimation()
            }
        }

    private val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val subjectDstRect = android.graphics.Rect()

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

        // Draw attached unblurred lock screen wallpaper background if enabled
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || w > h
        val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
        val isDepthActive = cfg.depthWallpaperEnabled && !isLandscape && isUnlocked

        if (isDepthActive) {
            val subjectBmp = DepthWallpaperHelper.getSubjectBitmap(context)
            val count = canvas.save()
            sheetClipPath.reset()
            val radii = floatArrayOf(
                0f, 0f,
                0f, 0f,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius
            )
            boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
            sheetClipPath.addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
            canvas.clipPath(sheetClipPath)

            // When shade is pulling down (fraction < 0.90f), background is 100% transparent.
            // As shade reaches 90%-100% full open, cross-fade in the unblurred lock screen wallpaper:
            val fadeAlpha = if (expansionFraction >= 0.90f) {
                ((expansionFraction - 0.90f) / 0.10f).coerceIn(0f, 1f)
            } else {
                0f
            }

            if (fadeAlpha > 0f) {
                val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                if (unblurredWp != null) {
                    unblurredWp.setBounds(0, 0, w, h)
                    unblurredWp.alpha = (fadeAlpha * 255).toInt()
                    unblurredWp.draw(canvas)
                }
            }

            // Draw the stationary subject pinned to physical screen coordinates (0, 0, screenW, screenH)
            // curSheet has translationY, so in canvas coordinates screen Y=0 is at -translationY.
            // Canvas clipping to boundsRect (0, 0, w, h) automatically clips the subject at the descending sheet bottom!
            if (subjectBmp != null && !subjectBmp.isRecycled) {
                val canvasY = -translationY
                subjectDstRect.set(0, canvasY.toInt(), w, (canvasY + h).toInt())
                canvas.drawBitmap(subjectBmp, null, subjectDstRect, subjectPaint)
            }

            if (fadeAlpha > 0f) {
                canvas.drawColor(((0x33 * fadeAlpha).toInt() shl 24))
            }

            canvas.restoreToCount(count)
        } else {
            val shouldDrawWallpaper = cfg.lockWallpaperEnabled && !isLandscape &&
                    (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)

            if (shouldDrawWallpaper) {
                val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                if (unblurredWp != null) {
                    val count = canvas.save()
                    sheetClipPath.reset()
                    val radii = floatArrayOf(
                        0f, 0f,
                        0f, 0f,
                        cornerRadius, cornerRadius,
                        cornerRadius, cornerRadius
                    )
                    boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
                    sheetClipPath.addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
                    canvas.clipPath(sheetClipPath)

                    unblurredWp.setBounds(0, 0, w, h)
                    unblurredWp.alpha = 255
                    unblurredWp.draw(canvas)

                    // Subtle dark scrim (20% black) so notification text & icons remain crystal clear against bright wallpapers
                    canvas.drawColor(0x33000000.toInt())
                    canvas.restoreToCount(count)
                }
            }
        }

        getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        val shouldDrawWallpaperLegacy = cfg.lockWallpaperEnabled && !isLandscape &&
                (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)
        val shouldRenderGlass = cfg.glassEffectsEnabled && (!cfg.glassOnlyWithoutWallpaper || (!shouldDrawWallpaperLegacy && !isDepthActive))

        if (shouldRenderGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
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
                recordingCanvas.translate(margin.toFloat() - viewX.toFloat(), margin.toFloat() - viewY.toFloat())
                val backdropBmp = RealtimeBackdropHelper.getLatestBackdropBitmap()
                if (backdropBmp != null && !backdropBmp.isRecycled) {
                    backdropDestRect.set(0, 0, screenW, screenH)
                    recordingCanvas.drawBitmap(backdropBmp, null, backdropDestRect, null)
                } else {
                    val wp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                        ?: LockScreenWallpaperHelper.getLiveLockWallpaper(context)
                        ?: try {
                            val wm = android.app.WallpaperManager.getInstance(context)
                            wm.peekDrawable() ?: wm.drawable
                        } catch (_: Throwable) { null }
                    if (wp != null) {
                        wp.setBounds(0, 0, screenW, screenH)
                        wp.draw(recordingCanvas)
                    } else {
                        val p = Paint().apply {
                            shader = LinearGradient(
                                0f, 0f, screenW.toFloat(), screenH.toFloat(),
                                intArrayOf(0xFF3B3B48.toInt(), 0xFF22222A.toInt(), 0xFF121216.toInt()),
                                null,
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
        sheetClipPath.reset()
        val radii = floatArrayOf(
            0f, 0f,
            0f, 0f,
            radius, radius,
            radius, radius
        )
        sheetClipPath.addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
        canvas.drawPath(sheetClipPath, fallbackBorderPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dev.joseph.hyperbounce.notification.NotificationSheetHook.updateHomeScreenCache(context)
        val cfg = NotificationSheetConfigHolder.sheet
        LockScreenWallpaperHelper.registerIfNeeded(context)
        LockScreenWallpaperHelper.triggerAsyncWallpaperLoad(context)
        LockScreenWallpaperHelper.addUnblurredWallpaperListener(wallpaperLoadListener)
        LockScreenWallpaperHelper.triggerAsyncUnblurredWallpaperLoad(context)
        DepthWallpaperHelper.addSubjectListener(depthSubjectListener)
        if (cfg.depthWallpaperEnabled) {
            DepthWallpaperHelper.triggerAsyncSubjectGeneration(context)
        }
        if (cfg.glassEffectsEnabled) {
            RealtimeBackdropHelper.triggerCapture(context) { postInvalidateOnAnimation() }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        LockScreenWallpaperHelper.removeUnblurredWallpaperListener(wallpaperLoadListener)
        DepthWallpaperHelper.removeSubjectListener(depthSubjectListener)
        RealtimeBackdropHelper.stopLiveRefresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
    }
}
