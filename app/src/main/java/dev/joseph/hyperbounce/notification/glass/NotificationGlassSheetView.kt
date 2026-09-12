package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Bitmap
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
import dev.joseph.hyperbounce.notification.lockscreen.LockScreenCoverSheetController
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

    var isClosingMode: Boolean = false
    var isClosingFromFullOpen: Boolean = false

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
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        setMeasuredDimension(screenW, screenH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled) return

        val leftBevel = cfg.leftBevelWidth * density
        val rightBevel = cfg.rightBevelWidth * density
        val bottomBevel = cfg.bottomBevelWidth * density
        val maxBevel = max(max(leftBevel, rightBevel), bottomBevel)

        val leftRefract = cfg.sideRefraction * density
        val rightRefract = cfg.sideRefraction * density
        val bottomRefract = cfg.bottomRefraction * density
        val maxRefract = max(max(leftRefract, rightRefract), bottomRefract)

        val cornerRadius = cfg.cornerRadius * density
        val margin = ((maxBevel + maxRefract * 0.45f) + 16f * density).toInt().coerceAtLeast(32)

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        // Draw attached unblurred lock screen wallpaper background if enabled
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || w > h
        val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
        val isDepthActive = cfg.depthWallpaperEnabled && !isLandscape && isUnlocked
        val subjectBmp = if (isDepthActive) DepthWallpaperHelper.getSubjectBitmap(context) else null

        val isClosing = isClosingMode || isClosingFromFullOpen

        val fadeAlpha = if (isDepthActive) {
            if (isClosing || expansionFraction >= 0.92f) {
                1f
            } else if (expansionFraction >= 0.65f) {
                ((expansionFraction - 0.65f) / 0.27f).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            0f
        }

        if (isDepthActive && subjectBmp != null && !subjectBmp.isRecycled) {
            val canvasY = if (isClosing) 0f else -translationY
            val bmpW = subjectBmp.width.toFloat()
            val bmpH = subjectBmp.height.toFloat()
            val screenAspect = w.toFloat() / h.toFloat()
            val bmpAspect = bmpW / bmpH

            val isFullScreenCutout = kotlin.math.abs(bmpAspect - screenAspect) / screenAspect < 0.08f

            if (isFullScreenCutout) {
                val clampedY = if (isClosing) 0f else kotlin.math.max(0f, canvasY)
                subjectDstRect.set(0, clampedY.toInt(), w, (clampedY + h).toInt())
            } else {
                val scale = kotlin.math.min(w.toFloat() / bmpW, h.toFloat() / bmpH)
                val scaledW = bmpW * scale
                val scaledH = bmpH * scale
                val left = (w - scaledW) / 2f
                val top = (if (isClosing) 0f else canvasY) + (h - scaledH) / 2f
                subjectDstRect.set(left.toInt(), top.toInt(), (left + scaledW).toInt(), (top + scaledH).toInt())
            }
        }

        val shouldDrawWallpaper = !isDepthActive && cfg.lockWallpaperEnabled && !isLandscape &&
                (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)

        getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        val shouldRenderGlass = cfg.glassEffectsEnabled && (!cfg.glassOnlyWithoutWallpaper || (!shouldDrawWallpaper && !isDepthActive))

        var glassRendered = false
        if (shouldRenderGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
            val params = GlassLensRenderer.LensParams(
                cornerRadius = cornerRadius,
                leftBevel = leftBevel,
                rightBevel = rightBevel,
                bottomBevel = bottomBevel,
                refract = maxRefract,
                leftRefract = leftRefract,
                rightRefract = rightRefract,
                bottomRefract = bottomRefract,
                dispersion = cfg.dispersionStrength,
                lightX = 0f,
                lightY = 1f,
                spec = cfg.specularStrength,
                hairline = cfg.hairlineStrength,
                innerGlow = cfg.innerGlowStrength,
                innerShadow = cfg.innerShadow,
                saturation = cfg.saturationFactor,
                rimSoftness = cfg.rimSoftness,
                blurRadius = cfg.blurRadius * density
            )

            val backdropBmp = RealtimeBackdropHelper.getLatestBackdropBitmap()
            glassRendered = renderer.draw(
                canvas = canvas,
                width = w,
                height = h,
                params = params,
                margin = margin,
                backdropBmp = backdropBmp,
                viewX = viewX,
                viewY = viewY,
                screenW = screenW,
                screenH = screenH
            ) { recordingCanvas ->
                recordingCanvas.save()
                recordingCanvas.translate(margin.toFloat(), margin.toFloat())
                drawSheetForeground(recordingCanvas, w, h, cornerRadius, isDepthActive, fadeAlpha, subjectBmp, shouldDrawWallpaper)
                recordingCanvas.restore()
            }
        }

        if (!glassRendered) {
            drawSheetForeground(canvas, w, h, cornerRadius, isDepthActive, fadeAlpha, subjectBmp, shouldDrawWallpaper)
            drawFallbackBorder(canvas, w.toFloat(), h.toFloat(), cornerRadius)
        }
    }

    private fun drawSheetForeground(
        targetCanvas: Canvas,
        w: Int,
        h: Int,
        cornerRadius: Float,
        isDepthActive: Boolean,
        fadeAlpha: Float,
        subjectBmp: Bitmap?,
        shouldDrawWallpaper: Boolean
    ) {
        val density = resources.displayMetrics.density
        val cfg = NotificationSheetConfigHolder.sheet
        val isLockScreenActive = cfg.enabled && cfg.lockScreenAsNotifCenterEnabled

        val isClosing = isClosingMode || isClosingFromFullOpen

        if (isDepthActive) {
            val count = targetCanvas.save()
            sheetClipPath.reset()
            val radii = floatArrayOf(
                0f, 0f,
                0f, 0f,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius
            )
            boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
            sheetClipPath.addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
            targetCanvas.clipPath(sheetClipPath)

            val drawWpAlpha = if (isClosing) 1f else fadeAlpha
            if (drawWpAlpha > 0f) {
                val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                if (unblurredWp != null) {
                    unblurredWp.setBounds(0, 0, w, h)
                    unblurredWp.alpha = (drawWpAlpha * 255).toInt()
                    unblurredWp.draw(targetCanvas)
                }
            }

            // [Layer 1] Replicated Lock Screen Clock (BEHIND subject cutout)
            if (isLockScreenActive) {
                val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.15f) / 0.70f).coerceIn(0f, 1f)
                val clockY = if (isClosing) 0f else (expansionFraction - 1.0f) * 40f * density
                LockScreenCoverSheetController.drawClock(
                    canvas = targetCanvas,
                    rootView = this.rootView,
                    context = context,
                    w = w,
                    h = h,
                    density = density,
                    layer = LockScreenCoverSheetController.ClockLayer.BACKGROUND,
                    alpha = clockAlpha,
                    translationY = clockY
                )
            }

            // [Layer 2] iOS 27 Depth Subject Cutout (occludes clock numbers!)
            if (subjectBmp != null && !subjectBmp.isRecycled) {
                targetCanvas.drawBitmap(subjectBmp, null, subjectDstRect, subjectPaint)
            }

            // [Layer 3] Foreground Clock Layer (for styles like oversize_b)
            if (isLockScreenActive) {
                val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.15f) / 0.70f).coerceIn(0f, 1f)
                val clockY = if (isClosing) 0f else (expansionFraction - 1.0f) * 40f * density
                LockScreenCoverSheetController.drawClock(
                    canvas = targetCanvas,
                    rootView = this.rootView,
                    context = context,
                    w = w,
                    h = h,
                    density = density,
                    layer = LockScreenCoverSheetController.ClockLayer.FOREGROUND,
                    alpha = clockAlpha,
                    translationY = clockY
                )
            }

            if (fadeAlpha > 0f) {
                targetCanvas.drawColor(((0x33 * fadeAlpha).toInt() shl 24))
            }

            targetCanvas.restoreToCount(count)
        } else if (shouldDrawWallpaper || isLockScreenActive) {
            val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
            if (unblurredWp != null) {
                val count = targetCanvas.save()
                sheetClipPath.reset()
                val radii = floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius
                )
                boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
                sheetClipPath.addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
                targetCanvas.clipPath(sheetClipPath)

                unblurredWp.setBounds(0, 0, w, h)
                unblurredWp.alpha = 255
                unblurredWp.draw(targetCanvas)

                // Replicated Lock Screen Clock
                if (isLockScreenActive) {
                    val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.10f) / 0.70f).coerceIn(0f, 1f)
                    val clockY = if (isClosing) 0f else (expansionFraction - 1.0f) * 40f * density
                    LockScreenCoverSheetController.drawClock(
                        canvas = targetCanvas,
                        rootView = this.rootView,
                        context = context,
                        w = w,
                        h = h,
                        density = density,
                        layer = LockScreenCoverSheetController.ClockLayer.BACKGROUND,
                        alpha = clockAlpha,
                        translationY = clockY
                    )
                    LockScreenCoverSheetController.drawClock(
                        canvas = targetCanvas,
                        rootView = this.rootView,
                        context = context,
                        w = w,
                        h = h,
                        density = density,
                        layer = LockScreenCoverSheetController.ClockLayer.FOREGROUND,
                        alpha = clockAlpha,
                        translationY = clockY
                    )
                }

                // Subtle dark scrim (20% black) so notification text & icons remain crystal clear against bright wallpapers
                targetCanvas.drawColor(0x33000000.toInt())
                targetCanvas.restoreToCount(count)
            }
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
