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
    private val customWpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val customWpDstRect = RectF()

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

        val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0 &&
                !dev.joseph.hyperbounce.notification.NotificationSheetHook.isKeyguard(context)
        if (!isUnlocked) {
            visibility = GONE
            return
        }

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
        val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached
        val isDepthActive = cfg.isDepthActive && !isLandscape && isUnlocked && isHomescreenAllowed
        val subjectBmp = if (isDepthActive) DepthWallpaperHelper.getSubjectBitmap(context) else null

        val isClosing = isClosingMode || isClosingFromFullOpen

        val fadeAlpha = if (isDepthActive) {
            if (isClosing || expansionFraction >= 0.96f) {
                1f
            } else if (expansionFraction >= 0.88f) {
                ((expansionFraction - 0.88f) / 0.08f).coerceIn(0f, 1f)
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

        val shouldDrawWallpaper = !isDepthActive && cfg.isWallpaperActive && !isLandscape && isHomescreenAllowed
        val shouldBlurBackdrop = !isDepthActive && !shouldDrawWallpaper

        getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        val shouldRenderGlass = !isLandscape && cfg.isGlassEffectsActive && (!cfg.glassOnlyWithoutWallpaper || (!shouldDrawWallpaper && !isDepthActive))

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

            val shadeBgBmp = (shadeBackgroundView?.background as? android.graphics.drawable.BitmapDrawable)?.bitmap
            val backdropBmp = if (shadeBgBmp != null && !shadeBgBmp.isRecycled) {
                shadeBgBmp
            } else {
                RealtimeBackdropHelper.getLatestBackdropBitmap(shouldBlurBackdrop)
            }
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
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || w > h
        val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0 &&
                !dev.joseph.hyperbounce.notification.NotificationSheetHook.isKeyguard(context)
        if (isLandscape || !isUnlocked) {
            // Strict landscape/lockscreen mode: hyperbounce never renders wallpaper or sheet content
            return
        }

        val density = resources.displayMetrics.density
        val cfg = NotificationSheetConfigHolder.sheet
        val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached
        val isLockScreenActive = cfg.isLockScreenActive && !isLandscape && isUnlocked && isHomescreenAllowed

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
                var drawn = false
                if (cfg.isCustomWallpaperActive) {
                    val customBmp = CustomWallpaperHelper.getCustomWallpaperBitmap(context)
                    if (customBmp != null && !customBmp.isRecycled) {
                        val wpY = if (isClosing) 0 else (-translationY).toInt()
                        drawCenterCropBitmap(targetCanvas, customBmp, 0, wpY, w, h, (drawWpAlpha * 255).toInt())
                        drawn = true
                    }
                }
                if (!drawn) {
                    val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                    if (unblurredWp != null) {
                        val wpY = if (isClosing) 0 else (-translationY).toInt()
                        unblurredWp.setBounds(0, wpY, w, wpY + h)
                        unblurredWp.alpha = (drawWpAlpha * 255).toInt()
                        unblurredWp.draw(targetCanvas)
                    }
                }
            }

            val clockOverSubject = isLockScreenActive && (expansionFraction >= 0.95f || dev.joseph.hyperbounce.notification.NotificationSheetHook.hasFullyOpened)

            // [Layer 1] Replicated Lock Screen Clock (BEHIND subject cutout during pulldown)
            if (isLockScreenActive && !clockOverSubject) {
                val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.05f) / 0.55f).coerceIn(0f, 1f)
                val clockY = 0f
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

            // [Layer 3] Foreground Clock Layer (and background clock when fully open so subject doesn't occlude)
            if (isLockScreenActive) {
                val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.05f) / 0.55f).coerceIn(0f, 1f)
                val clockY = 0f
                if (clockOverSubject) {
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

            var wallpaperDrawn = false
            if (cfg.isCustomWallpaperActive) {
                val customBmp = CustomWallpaperHelper.getCustomWallpaperBitmap(context)
                if (customBmp != null && !customBmp.isRecycled) {
                    drawCenterCropBitmap(targetCanvas, customBmp, 0, 0, w, h, 255)
                    wallpaperDrawn = true
                }
            }
            if (!wallpaperDrawn) {
                val unblurredWp = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
                if (unblurredWp != null) {
                    unblurredWp.setBounds(0, 0, w, h)
                    unblurredWp.alpha = 255
                    unblurredWp.draw(targetCanvas)
                    wallpaperDrawn = true
                }
            }

            // Replicated Lock Screen Clock
            if (isLockScreenActive) {
                val clockAlpha = if (isClosing) 1f else ((expansionFraction - 0.05f) / 0.55f).coerceIn(0f, 1f)
                val clockY = 0f
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

    private fun drawCenterCropBitmap(canvas: Canvas, bmp: Bitmap, x: Int, y: Int, dstW: Int, dstH: Int, alpha: Int) {
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        if (bmpW <= 0 || bmpH <= 0) return
        val scale = kotlin.math.max(dstW.toFloat() / bmpW, dstH.toFloat() / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val left = x + (dstW - scaledW) / 2f
        val top = y + (dstH - scaledH) / 2f
        customWpPaint.alpha = alpha.coerceIn(0, 255)
        customWpDstRect.set(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(bmp, null, customWpDstRect, customWpPaint)
    }

    private fun drawFallbackBorder(canvas: Canvas, w: Float, h: Float, radius: Float) {
        val density = resources.displayMetrics.density
        val strokeW = 1.5f * density
        fallbackBorderPaint.strokeWidth = strokeW
        val half = strokeW / 2f
        val r = radius.coerceAtLeast(0f)

        sheetClipPath.reset()
        sheetClipPath.moveTo(half, 0f)
        sheetClipPath.lineTo(half, h - r)
        sheetClipPath.arcTo(half, h - 2 * r, half + 2 * r, h, 180f, -90f, false)
        sheetClipPath.lineTo(w - half - r, h)
        sheetClipPath.arcTo(w - half - 2 * r, h - 2 * r, w - half, h, 90f, -90f, false)
        sheetClipPath.lineTo(w - half, 0f)
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
        if (cfg.isDepthActive) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
        RealtimeBackdropHelper.stopLiveRefresh()
    }
}
