package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView

/**
 * FluidTileResizeHandler provides semi-fluid, directional tile and slider expansion/shrinking
 * with strict corner radius preservation, unstretched 1:1 icon rendering, full-scale stock text reveal,
 * exact stock theme color sampling, zero-flicker release-to-resize transition,
 * and real-time 2D bin-packing neighbour displacement matching HyperOS stock layout.
 */
object FluidTileResizeHandler {

    private const val TAG = "FluidTileResizeHandler"

    const val STATE_NORMAL = 0
    const val STATE_EXPANDED = 1
    const val STATE_VERTICAL = 2
    const val STATE_LARGE = 3

    const val MEDIA_1X1 = 9
    const val MEDIA_2X1 = 10
    const val MEDIA_1X2 = 11
    const val MEDIA_2X2 = 12
    const val MEDIA_4X1 = 13
    const val MEDIA_4X2 = 14
    const val MEDIA_2X4 = 15
    const val MEDIA_1X3 = 16
    const val MEDIA_1X4 = 17
    const val MEDIA_1X5 = 18
    const val MEDIA_2X3 = 19
    const val MEDIA_2X5 = 20
    const val MEDIA_3X1 = 21
    const val MEDIA_3X2 = 22
    const val MEDIA_3X3 = 23
    const val MEDIA_4X3 = 24

    fun mediaSizeStringToState(size: String): Int = when (size) {
        "1x1" -> MEDIA_1X1
        "1x2" -> MEDIA_1X2
        "1x3" -> MEDIA_1X3
        "1x4" -> MEDIA_1X4
        "1x5" -> MEDIA_1X5
        "2x1" -> MEDIA_2X1
        "2x2" -> MEDIA_2X2
        "2x3" -> MEDIA_2X3
        "2x4" -> MEDIA_2X4
        "2x5" -> MEDIA_2X5
        "3x1" -> MEDIA_3X1
        "3x2" -> MEDIA_3X2
        "3x3" -> MEDIA_3X3
        "4x1" -> MEDIA_4X1
        "4x2" -> MEDIA_4X2
        "4x3" -> MEDIA_4X3
        else -> MEDIA_2X2
    }

    fun mediaStateToSizeString(state: Int): String = when (state) {
        MEDIA_1X1 -> "1x1"
        MEDIA_1X2 -> "1x2"
        MEDIA_1X3 -> "1x3"
        MEDIA_1X4 -> "1x4"
        MEDIA_1X5 -> "1x5"
        MEDIA_2X1 -> "2x1"
        MEDIA_2X2 -> "2x2"
        MEDIA_2X3 -> "2x3"
        MEDIA_2X4 -> "2x4"
        MEDIA_2X5 -> "2x5"
        MEDIA_3X1 -> "3x1"
        MEDIA_3X2 -> "3x2"
        MEDIA_3X3 -> "3x3"
        MEDIA_4X1 -> "4x1"
        MEDIA_4X2 -> "4x2"
        MEDIA_4X3 -> "4x3"
        else -> "2x2"
    }

    fun mediaStateToSpans(state: Int): Pair<Int, Int> = when (state) {
        MEDIA_1X1 -> Pair(1, 1)
        MEDIA_1X2 -> Pair(1, 2)
        MEDIA_1X3 -> Pair(1, 3)
        MEDIA_1X4 -> Pair(1, 4)
        MEDIA_1X5 -> Pair(1, 5)
        MEDIA_2X1 -> Pair(2, 1)
        MEDIA_2X2 -> Pair(2, 2)
        MEDIA_2X3 -> Pair(2, 3)
        MEDIA_2X4 -> Pair(2, 4)
        MEDIA_2X5 -> Pair(2, 5)
        MEDIA_3X1 -> Pair(3, 1)
        MEDIA_3X2 -> Pair(3, 2)
        MEDIA_3X3 -> Pair(3, 3)
        MEDIA_4X1 -> Pair(4, 1)
        MEDIA_4X2 -> Pair(4, 2)
        MEDIA_4X3 -> Pair(4, 3)
        else -> Pair(2, 2)
    }

    const val LAYOUT_MODE_HORIZONTAL = 0
    const val LAYOUT_MODE_VERTICAL = 1
    const val LAYOUT_MODE_LARGE = 2

    /**
     * Attaches the appropriate resize listener to the given handle view based on
     * the companion app configuration switch.
     */
    fun attach(handleView: View, ctx: Context, spec: String, tileView: View, density: Float) {
        if (ControlCenterConfigHolder.config.fluidResizeEnabled) {
            handleView.setOnTouchListener(FluidTileResizeTouchListener(ctx, spec, tileView, density))
        } else {
            handleView.setOnTouchListener(TileResizeDragTouchListener(ctx, spec, tileView, density))
        }
    }

    fun resolveViewBackground(v: View?): Drawable? {
        if (v == null) return null
        if (v.background != null) return v.background
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val child = v.getChildAt(i)
                if (child is ImageView || child is TextView) continue
                val bg = resolveViewBackground(child)
                if (bg != null) return bg
            }
        }
        return null
    }

    /**
     * Native View-based resize overlay added to targetContainer.overlay via ViewGroupOverlay.add(View).
     * Connects directly to HyperOS HWUI native RenderNode blur shaders (MiBlurCompat) and Advanced Textures
     * (MiuiColorBlendToken), strictly clipped by ViewOutlineProvider to cornerRadius.
     * When blur is disabled, sets stock drawables without artificial acrylic or border painting.
     */
    class FluidResizeOverlayView(context: Context, val density: Float) : View(context) {
        val morphDrawable = FluidTileMorphOverlayDrawable(density)
        var currentCornerRadius: Float = 20f * density
            set(value) {
                field = value
                morphDrawable.cornerRadius = value
                invalidateOutline()
            }
        val currentRect = RectF()
        var layoutMode: Int = LAYOUT_MODE_HORIZONTAL
        var isNight: Boolean = true
            set(value) {
                field = value
                morphDrawable.isNight = value
            }

        init {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
                }
            }
            clipToOutline = true
        }

        fun applyStockBackgroundOrBlur(
            tileView: View,
            classLoader: ClassLoader,
            isActiveTile: Boolean,
            isSlider: Boolean = false,
            isMedia: Boolean = false
        ) {
            var blurApplied = false
            try {
                var blurEnabled = false
                try {
                    val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                    val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                    blurEnabled = getBlurM.invoke(null, context) as Boolean
                } catch (t: Throwable) {}

                if (blurEnabled) {
                    val miBlurClass = findMiBlurClass(classLoader)
                    val setModeMethod = miBlurClass?.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod?.invoke(null, this, 1)

                    val tokenClass = findMiuiColorBlendTokenClass(classLoader)
                    val tokenInst = tokenClass?.getDeclaredField("INSTANCE")?.get(null)
                    val tokenMethodName = if (isActiveTile && !isSlider && !isMedia) {
                        "getCC_TILE_ON_BLEND_COLORS"
                    } else {
                        "getCC_TILE_DEFAULT_BLEND_COLORS"
                    }
                    val blendColors = tokenClass?.getMethod(tokenMethodName)?.invoke(tokenInst)
                    if (blendColors != null && miBlurClass != null) {
                        val setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View::class.java, blendColors.javaClass)
                        setBlendMethod.invoke(null, this, blendColors)
                        blurApplied = true
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "applyStockBackgroundOrBlur failed", t)
            }

            if (blurApplied) {
                background = null
            } else {
                try {
                    val miBlurClass = findMiBlurClass(classLoader)
                    miBlurClass?.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)?.invoke(null, this, 0)
                    miBlurClass?.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)?.invoke(null, this)
                } catch (t: Throwable) {}
                try {
                    val commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader)
                    val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                    commonUtilsClass.getMethod("clearMiBlurBlendEffect", View::class.java).invoke(commonUtils, this)
                } catch (t: Throwable) {}

                val stockBg = (tileView.background ?: resolveViewBackground(tileView))?.constantState?.newDrawable()?.mutate()
                if (stockBg != null) {
                    background = stockBg
                } else {
                    val colorResId = if (isActiveTile) {
                        context.resources.getIdentifier("qs_card_enabled_color", "color", context.packageName)
                    } else {
                        context.resources.getIdentifier("qs_card_disabled_color", "color", context.packageName)
                    }
                    val fallbackColor = if (colorResId != 0) {
                        try { context.resources.getColor(colorResId, null) } catch (t: Throwable) { if (isActiveTile) 0xFF0088FF.toInt() else Color.parseColor("#4D000000") }
                    } else {
                        if (isActiveTile) 0xFF0088FF.toInt() else Color.parseColor("#4D000000")
                    }
                    background = GradientDrawable().apply {
                        this.cornerRadius = currentCornerRadius
                        setColor(fallbackColor)
                    }
                }
            }
            invalidateOutline()
        }

        fun updateMorph(
            rect: RectF,
            localRect: RectF,
            radius: Float,
            localIconBox: RectF,
            snapping: Boolean,
            mode: Int
        ) {
            currentRect.set(rect)
            layoutMode = mode
            currentCornerRadius = radius
            morphDrawable.updateMorph(localRect, radius, localIconBox, snapping, mode)
            val w = Math.max(1, (rect.right - rect.left).toInt())
            val h = Math.max(1, (rect.bottom - rect.top).toInt())
            measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
            layout(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            invalidateOutline()
            invalidate()
        }

        fun updateMediaMorph(
            rect: RectF,
            localRect: RectF,
            radius: Float,
            startState: Int,
            targetState: Int,
            progress: Float,
            hasTrack: Boolean
        ) {
            currentRect.set(rect)
            currentCornerRadius = radius
            morphDrawable.updateMediaMorph(localRect, radius, startState, targetState, progress, hasTrack)
            val w = Math.max(1, (rect.right - rect.left).toInt())
            val h = Math.max(1, (rect.bottom - rect.top).toInt())
            measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
            layout(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            invalidateOutline()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            morphDrawable.draw(canvas)
        }

        companion object {
            fun findMiBlurClass(classLoader: ClassLoader): Class<*>? {
                return try {
                    Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                } catch (e: Throwable) {
                    try {
                        Class.forName("com.miui.systemui.util.MiBlurCompat", false, classLoader)
                    } catch (e2: Throwable) { null }
                }
            }

            fun findMiuiColorBlendTokenClass(classLoader: ClassLoader): Class<*>? {
                return try {
                    Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader)
                } catch (e: Throwable) {
                    try {
                        Class.forName("com.miui.systemui.util.MiuiColorBlendToken", false, classLoader)
                    } catch (e2: Throwable) { null }
                }
            }
        }
    }

    /**
     * Custom morphing overlay drawable that renders foreground elements of the expanding or shrinking tile.
     * Preserves exact corner radius without stretching, keeps icons crisp and unstretched,
     * reveals full-scale stock text by clip mask as the tile expands, and draws the corner resize handle.
     * Background blur and texture are handled natively by FluidResizeOverlayView.
     */
    class FluidTileMorphOverlayDrawable(private val density: Float) : Drawable() {
        var isNight: Boolean = true
        private val sliderProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19.5f * density
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3FFFFFF")
            textSize = 15f * density
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val handleDrawable = CurvedResizeHandleDrawable(density)
        private val clipPath = Path()

        private fun lerp(start: Float, end: Float, fraction: Float): Float {
            return start + (end - start) * fraction.coerceIn(0f, 1f)
        }

        val currentRect = RectF()
        var cornerRadius = 20f * density
        var isSnapping = false
        var isSlider = false
        var layoutMode = LAYOUT_MODE_HORIZONTAL

        var isMedia = false
        var coverDrawable: Drawable? = null
        var playDrawable: Drawable? = null
        var prevDrawable: Drawable? = null
        var nextDrawable: Drawable? = null
        var deviceIconDrawable: Drawable? = null
        var mediaProgressFraction: Float = 0f

        var startMediaState: Int = MEDIA_2X2
        var targetMediaState: Int = MEDIA_2X2
        var mediaMorphProgress: Float = 0f
        var mediaHasTrack: Boolean = false

        data class MediaStateLayout(
            val coverRect: RectF,
            val coverRadius: Float,
            val prevRect: RectF,
            val playRect: RectF,
            val nextRect: RectF,
            val deviceIconRect: RectF,
            val deviceIconAlpha: Float,
            val textStartX: Float,
            val textTopY: Float,
            val textClipRight: Float,
            val titleAlpha: Float,
            val artistAlpha: Float,
            val isCenterAligned: Boolean,
            val controlsAlpha: Float = 1f,
            val progressBarRect: RectF = RectF(),
            val progressBarAlpha: Float = 0f
        )

        fun computeMediaLayout(state: Int, rect: RectF, hasTrack: Boolean): MediaStateLayout {
            return when (state) {
                MEDIA_1X1 -> {
                    val cSize = (Math.min(rect.width(), rect.height()) - 22f * density).coerceAtLeast(36f * density)
                    val coverRect = RectF(rect.centerX() - cSize / 2f, rect.centerY() - cSize / 2f, rect.centerX() + cSize / 2f, rect.centerY() + cSize / 2f)
                    val coverRadius = cSize / 2f

                    val diRect = RectF(rect.centerX(), rect.centerY(), rect.centerX(), rect.centerY())
                    val dummyBtn = RectF(rect.centerX(), rect.centerY(), rect.centerX(), rect.centerY())
                    MediaStateLayout(coverRect, coverRadius, dummyBtn, dummyBtn, dummyBtn, diRect, 0f, rect.centerX(), rect.centerY(), rect.centerX(), 0f, 0f, false, controlsAlpha = 0f, progressBarRect = RectF(), progressBarAlpha = 0f)
                }

                MEDIA_1X2 -> {
                    val cSize = 44f * density
                    val cLeft = rect.centerX() - cSize / 2f
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = cSize / 2f

                    val diSize = 20f * density
                    val deviceIconRect = RectF(rect.centerX() - diSize / 2f, rect.top + 14f * density, rect.centerX() + diSize / 2f, rect.top + 14f * density + diSize)

                    val playSize = 28f * density
                    val btnSize = 27f * density
                    val spacing = 6f * density
                    val ctrlCenterX = rect.centerX()
                    val bottomAvailableCenter = (coverRect.bottom + 8f * density + rect.bottom - 12f * density) / 2f
                    val playCenterY = bottomAvailableCenter
                    val prevCenterY = playCenterY - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterY = playCenterY + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(ctrlCenterX - btnSize / 2f, prevCenterY - btnSize / 2f, ctrlCenterX + btnSize / 2f, prevCenterY + btnSize / 2f)
                    val playRect = RectF(ctrlCenterX - playSize / 2f, playCenterY - playSize / 2f, ctrlCenterX + playSize / 2f, playCenterY + playSize / 2f)
                    val nextRect = RectF(ctrlCenterX - btnSize / 2f, nextCenterY - btnSize / 2f, ctrlCenterX + btnSize / 2f, nextCenterY + btnSize / 2f)

                    val textStartX = rect.left + 8f * density
                    val textTopY = coverRect.bottom + 8f * density
                    val textClipRight = rect.right - 8f * density

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 0f, textStartX, textTopY, textClipRight, 0f, 0f, false, controlsAlpha = 1f)
                }

                MEDIA_1X3, MEDIA_1X4, MEDIA_1X5 -> {
                    val cSize = if (state == MEDIA_1X3) 48f * density else 56f * density
                    val cLeft = rect.centerX() - cSize / 2f
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = cSize / 2f

                    val diSize = 20f * density
                    val deviceIconRect = RectF(rect.centerX() - diSize / 2f, rect.top + 14f * density, rect.centerX() + diSize / 2f, rect.top + 14f * density + diSize)

                    val playSize = 28f * density
                    val btnSize = 27f * density
                    val spacing = 6f * density
                    val ctrlCenterX = rect.centerX()
                    val ctrlCenterY = rect.bottom - 56f * density
                    val playCenterY = ctrlCenterY
                    val prevCenterY = playCenterY - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterY = playCenterY + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(ctrlCenterX - btnSize / 2f, prevCenterY - btnSize / 2f, ctrlCenterX + btnSize / 2f, prevCenterY + btnSize / 2f)
                    val playRect = RectF(ctrlCenterX - playSize / 2f, playCenterY - playSize / 2f, ctrlCenterX + playSize / 2f, playCenterY + playSize / 2f)
                    val nextRect = RectF(ctrlCenterX - btnSize / 2f, nextCenterY - btnSize / 2f, ctrlCenterX + btnSize / 2f, nextCenterY + btnSize / 2f)

                    val textStartX = rect.left + 6f * density
                    val textTopY = coverRect.bottom + 12f * density
                    val textClipRight = rect.right - 6f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 0f, textStartX, textTopY, textClipRight, 1f, artistAlpha, true, controlsAlpha = 1f)
                }

                MEDIA_2X1 -> {
                    val cSize = 44f * density
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.centerY() - cSize / 2f
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = cSize / 2f

                    val diSize = 20f * density
                    val deviceIconRect = RectF(rect.right - 14f * density - diSize, rect.centerY() - diSize / 2f, rect.right - 14f * density, rect.centerY() + diSize / 2f)

                    val playSize = 28f * density
                    val btnSize = 27f * density
                    val spacing = 5f * density
                    val ctrlCenterY = rect.centerY()
                    val rightAvailableCenter = (coverRect.right + 8f * density + rect.right - 10f * density) / 2f
                    val playCenterX = rightAvailableCenter
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterX = playCenterX + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    val textStartX = coverRect.right + 10f * density
                    val textTopY = rect.centerY() - 10f * density
                    val textClipRight = prevRect.left - 4f * density

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 0f, textStartX, textTopY, textClipRight, 0f, 0f, false, controlsAlpha = 1f)
                }

                MEDIA_3X1 -> {
                    val cSize = 46f * density
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.centerY() - cSize / 2f
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = cSize / 2f

                    val diSize = 20f * density
                    val deviceIconRect = RectF(rect.right - 14f * density - diSize, rect.centerY() - diSize / 2f, rect.right - 14f * density, rect.centerY() + diSize / 2f)

                    val playSize = 28f * density
                    val btnSize = 26f * density
                    val spacing = 5f * density
                    val ctrlCenterY = if (hasTrack) rect.centerY() - 6f * density else rect.centerY()
                    val nextRight = rect.right - 12f * density
                    val nextCenterX = nextRight - btnSize / 2f
                    val playCenterX = nextCenterX - btnSize / 2f - spacing - playSize / 2f
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    val pbRect = RectF(prevCenterX - btnSize / 2f, rect.bottom - 12f * density, nextRight, rect.bottom - 6f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val textStartX = coverRect.right + 10f * density
                    val textTopY = rect.centerY() - 14f * density
                    val textClipRight = prevRect.left - 6f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 0f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                MEDIA_4X1 -> {
                    val cSize = 48f * density
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.centerY() - cSize / 2f
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = cSize / 2f

                    val diSize = 20f * density
                    val deviceIconRect = RectF(rect.right - 14f * density - diSize, rect.centerY() - diSize / 2f, rect.right - 14f * density, rect.centerY() + diSize / 2f)

                    val playSize = 30f * density
                    val btnSize = 26f * density
                    val spacing = 6f * density
                    val ctrlCenterY = if (hasTrack) rect.centerY() - 8f * density else rect.centerY()
                    val nextRight = rect.right - 14f * density
                    val nextCenterX = nextRight - btnSize / 2f
                    val playCenterX = nextCenterX - btnSize / 2f - spacing - playSize / 2f
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    val pbRect = RectF(prevCenterX - btnSize / 2f, rect.bottom - 12f * density, nextRight, rect.bottom - 6f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val textStartX = coverRect.right + 12f * density
                    val textTopY = if (hasTrack) rect.centerY() - 18f * density else rect.centerY() - 10f * density
                    val textClipRight = prevRect.left - 8f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 0f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                MEDIA_2X3 -> {
                    val cSize = 64f * density
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = 14f * density

                    val diSize = 24f * density
                    val deviceIconRect = RectF(rect.right - 38f * density, rect.top + 14f * density, rect.right - 14f * density, rect.top + 38f * density)

                    val textStartX = rect.left + 14f * density
                    val textTopY = coverRect.bottom + 12f * density
                    val textClipRight = rect.right - 14f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    val pbRect = RectF(rect.left + 16f * density, rect.bottom - 56f * density, rect.right - 16f * density, rect.bottom - 46f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val playSize = 34f * density
                    val btnSize = 28f * density
                    val spacing = 14f * density
                    val ctrlCenterY = rect.bottom - 22f * density
                    val playCenterX = rect.centerX()
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterX = playCenterX + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 1f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                MEDIA_2X4, MEDIA_2X5 -> {
                    val cSize = (rect.width() - 28f * density).coerceAtLeast(48f * density)
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = 18f * density

                    val diSize = 24f * density
                    val diRight = rect.right - 22f * density
                    val diTop = rect.top + 22f * density
                    val deviceIconRect = RectF(diRight - diSize, diTop, diRight, diTop + diSize)

                    val textStartX = rect.left + 14f * density
                    val textTopY = coverRect.bottom + 16f * density
                    val textClipRight = rect.right - 14f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    val pbRect = RectF(rect.left + 18f * density, rect.bottom - 74f * density, rect.right - 18f * density, rect.bottom - 62f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val playSize = 38f * density
                    val btnSize = 32f * density
                    val spacing = 16f * density
                    val ctrlCenterY = rect.bottom - 36f * density
                    val playCenterX = rect.centerX()
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterX = playCenterX + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 1f, textStartX, textTopY, textClipRight, 1f, artistAlpha, true, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                MEDIA_3X2, MEDIA_3X3 -> {
                    val cSize = (rect.height() - 28f * density).coerceIn(48f * density, 130f * density)
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = 16f * density

                    val diSize = 24f * density
                    val diRight = rect.right - 16f * density
                    val diTop = rect.top + 16f * density
                    val deviceIconRect = RectF(diRight - diSize, diTop, diRight, diTop + diSize)

                    val textStartX = coverRect.right + 14f * density
                    val textTopY = rect.top + 22f * density
                    val textClipRight = deviceIconRect.left - 8f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    val pbRect = RectF(coverRect.right + 14f * density, rect.bottom - 54f * density, rect.right - 14f * density, rect.bottom - 44f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val playSize = 34f * density
                    val btnSize = 28f * density
                    val spacing = 12f * density
                    val ctrlCenterY = rect.bottom - 22f * density
                    val rightSectionCenter = (coverRect.right + rect.right) / 2f
                    val playCenterX = rightSectionCenter
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterX = playCenterX + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 1f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                MEDIA_4X2, MEDIA_4X3 -> {
                    val cSize = (rect.height() - 28f * density).coerceIn(48f * density, 160f * density)
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = 18f * density

                    val diSize = 24f * density
                    val diRight = rect.right - 16f * density
                    val diTop = rect.top + 16f * density
                    val deviceIconRect = RectF(diRight - diSize, diTop, diRight, diTop + diSize)

                    val textStartX = coverRect.right + 16f * density
                    val textTopY = rect.top + 26f * density
                    val textClipRight = deviceIconRect.left - 8f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    val pbRect = RectF(coverRect.right + 16f * density, rect.bottom - 58f * density, rect.right - 16f * density, rect.bottom - 46f * density)
                    val pbAlpha = if (hasTrack) 1f else 0f

                    val playSize = 36f * density
                    val btnSize = 30f * density
                    val spacing = 16f * density
                    val ctrlCenterY = rect.bottom - 26f * density
                    val rightSectionCenter = (coverRect.right + rect.right) / 2f
                    val playCenterX = rightSectionCenter
                    val prevCenterX = playCenterX - playSize / 2f - spacing - btnSize / 2f
                    val nextCenterX = playCenterX + playSize / 2f + spacing + btnSize / 2f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 1f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f, progressBarRect = pbRect, progressBarAlpha = pbAlpha)
                }

                else -> { // MEDIA_2X2 Stock
                    val cSize = 48f * density
                    val cLeft = rect.left + 14f * density
                    val cTop = rect.top + 14f * density
                    val coverRect = RectF(cLeft, cTop, cLeft + cSize, cTop + cSize)
                    val coverRadius = 12f * density

                    val diSize = 24f * density
                    val diRight = rect.right - 14f * density
                    val diTop = rect.top + 14f * density
                    val deviceIconRect = RectF(diRight - diSize, diTop, diRight, diTop + diSize)

                    val textStartX = rect.left + 14f * density
                    val textTopY = coverRect.bottom + 12f * density
                    val textClipRight = rect.right - 14f * density
                    val artistAlpha = if (hasTrack) 1f else 0f

                    val playSize = 34f * density
                    val btnSize = 26f * density
                    val ctrlCenterY = rect.bottom - 24f * density
                    val playCenterX = rect.centerX()
                    val prevCenterX = rect.left + rect.width() * 0.22f
                    val nextCenterX = rect.right - rect.width() * 0.22f

                    val prevRect = RectF(prevCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, prevCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)
                    val playRect = RectF(playCenterX - playSize / 2f, ctrlCenterY - playSize / 2f, playCenterX + playSize / 2f, ctrlCenterY + playSize / 2f)
                    val nextRect = RectF(nextCenterX - btnSize / 2f, ctrlCenterY - btnSize / 2f, nextCenterX + btnSize / 2f, ctrlCenterY + btnSize / 2f)

                    MediaStateLayout(coverRect, coverRadius, prevRect, playRect, nextRect, deviceIconRect, 1f, textStartX, textTopY, textClipRight, 1f, artistAlpha, false, controlsAlpha = 1f)
                }
            }
        }

        var iconDrawable: Drawable? = null
        var customBgDrawable: Drawable? = null
        var sliderProgressFraction: Float = 0.5f
        var titleText: String? = null
        var subtitleText: String? = null
        val iconRect = RectF()

        fun setup(
            icon: Drawable?,
            title: String?,
            subtitle: String?,
            bgColor: Int,
            isSliderItem: Boolean,
            customBg: Drawable? = null,
            sliderProgress: Float = 0.5f
        ) {
            isMedia = false
            iconDrawable = icon
            titleText = title
            subtitleText = subtitle
            isSlider = isSliderItem
            customBgDrawable = customBg
            sliderProgressFraction = sliderProgress
            titlePaint.textSize = 15.5f * density
            subtitlePaint.textSize = 13.5f * density
        }

        fun setupMedia(
            cover: Drawable?,
            play: Drawable?,
            prev: Drawable?,
            next: Drawable?,
            deviceIcon: Drawable?,
            title: String?,
            artist: String?,
            bgColor: Int,
            customBg: Drawable? = null,
            progress: Float = 0f,
            hasTrack: Boolean = false,
            titleSize: Float? = null,
            titleTypeface: Typeface? = null,
            titleColor: Int? = null,
            artistSize: Float? = null,
            artistTypeface: Typeface? = null,
            artistColor: Int? = null
        ) {
            isMedia = true
            isSlider = false
            coverDrawable = cover
            playDrawable = play
            prevDrawable = prev
            nextDrawable = next
            deviceIconDrawable = deviceIcon
            titleText = title
            subtitleText = if (hasTrack) artist else null
            mediaHasTrack = hasTrack
            customBgDrawable = customBg
            mediaProgressFraction = progress

            if (titleSize != null && titleSize > 0f) {
                titlePaint.textSize = titleSize
            } else {
                titlePaint.textSize = 19.5f * density
            }
            if (titleTypeface != null) {
                titlePaint.typeface = titleTypeface
            } else {
                titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            if (titleColor != null && titleColor != 0) {
                titlePaint.color = titleColor
            } else {
                titlePaint.color = Color.WHITE
            }

            if (artistSize != null && artistSize > 0f) {
                subtitlePaint.textSize = artistSize
            } else {
                subtitlePaint.textSize = 15f * density
            }
            if (artistTypeface != null) {
                subtitlePaint.typeface = artistTypeface
            } else {
                subtitlePaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            if (artistColor != null && artistColor != 0) {
                subtitlePaint.color = artistColor
            } else {
                subtitlePaint.color = Color.parseColor("#B3FFFFFF")
            }
        }

        fun updateMediaMorph(
            rect: RectF,
            radius: Float,
            startState: Int,
            targetState: Int,
            progress: Float,
            hasTrack: Boolean
        ) {
            currentRect.set(rect)
            cornerRadius = radius
            startMediaState = startState
            targetMediaState = targetState
            mediaMorphProgress = progress.coerceIn(0f, 1f)
            mediaHasTrack = hasTrack
            setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            invalidateSelf()
        }

        fun updateMorph(
            rect: RectF,
            radius: Float,
            iconBox: RectF,
            snapping: Boolean,
            mode: Int
        ) {
            currentRect.set(rect)
            cornerRadius = radius
            iconRect.set(iconBox)
            isSnapping = snapping
            layoutMode = mode
            setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            if (currentRect.isEmpty) return

            // If slider, draw progress fill (clipped to corner radius), icon, and handle
            if (isSlider) {
                // 1. Slider progress fill (white pill clipped by the track's rounded boundary)
                if (sliderProgressFraction > 0.01f) {
                    canvas.save()
                    clipPath.reset()
                    clipPath.addRoundRect(currentRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    canvas.clipPath(clipPath)
                    val progressW = currentRect.width() * sliderProgressFraction
                    val progressRect = RectF(currentRect.left, currentRect.top, currentRect.left + progressW, currentRect.bottom)
                    canvas.drawRect(progressRect, sliderProgressPaint)
                    canvas.restore()
                }

                // 2. Unstretched icon
                iconDrawable?.let { ic ->
                    ic.setBounds(
                        iconRect.left.toInt(),
                        iconRect.top.toInt(),
                        iconRect.right.toInt(),
                        iconRect.bottom.toInt()
                    )
                    ic.alpha = 255
                    ic.draw(canvas)
                }

                // 3. Curved resize handle at bottom-right corner
                val handleSize = 34f * density
                val hLeft = currentRect.right - handleSize
                val hTop = currentRect.bottom - handleSize
                handleDrawable.setBounds(hLeft.toInt(), hTop.toInt(), currentRect.right.toInt(), currentRect.bottom.toInt())
                handleDrawable.draw(canvas)
                return
            }

            // If media player, render album art, controls, device icon, and title/artist with ultra-precise continuous 2D interpolation
            if (isMedia) {
                val startLayout = computeMediaLayout(startMediaState, currentRect, mediaHasTrack)
                val targetLayout = computeMediaLayout(targetMediaState, currentRect, mediaHasTrack)
                val p = mediaMorphProgress

                val coverRect = RectF(
                    lerp(startLayout.coverRect.left, targetLayout.coverRect.left, p),
                    lerp(startLayout.coverRect.top, targetLayout.coverRect.top, p),
                    lerp(startLayout.coverRect.right, targetLayout.coverRect.right, p),
                    lerp(startLayout.coverRect.bottom, targetLayout.coverRect.bottom, p)
                )
                val coverRad = lerp(startLayout.coverRadius, targetLayout.coverRadius, p)

                val prevRect = RectF(
                    lerp(startLayout.prevRect.left, targetLayout.prevRect.left, p),
                    lerp(startLayout.prevRect.top, targetLayout.prevRect.top, p),
                    lerp(startLayout.prevRect.right, targetLayout.prevRect.right, p),
                    lerp(startLayout.prevRect.bottom, targetLayout.prevRect.bottom, p)
                )
                val playRect = RectF(
                    lerp(startLayout.playRect.left, targetLayout.playRect.left, p),
                    lerp(startLayout.playRect.top, targetLayout.playRect.top, p),
                    lerp(startLayout.playRect.right, targetLayout.playRect.right, p),
                    lerp(startLayout.playRect.bottom, targetLayout.playRect.bottom, p)
                )
                val nextRect = RectF(
                    lerp(startLayout.nextRect.left, targetLayout.nextRect.left, p),
                    lerp(startLayout.nextRect.top, targetLayout.nextRect.top, p),
                    lerp(startLayout.nextRect.right, targetLayout.nextRect.right, p),
                    lerp(startLayout.nextRect.bottom, targetLayout.nextRect.bottom, p)
                )

                val deviceIconRect = RectF(
                    lerp(startLayout.deviceIconRect.left, targetLayout.deviceIconRect.left, p),
                    lerp(startLayout.deviceIconRect.top, targetLayout.deviceIconRect.top, p),
                    lerp(startLayout.deviceIconRect.right, targetLayout.deviceIconRect.right, p),
                    lerp(startLayout.deviceIconRect.bottom, targetLayout.deviceIconRect.bottom, p)
                )
                val deviceIconAlpha = lerp(startLayout.deviceIconAlpha, targetLayout.deviceIconAlpha, p)

                val textStartX = lerp(startLayout.textStartX, targetLayout.textStartX, p)
                val textTopY = lerp(startLayout.textTopY, targetLayout.textTopY, p)
                val textClipRight = lerp(startLayout.textClipRight, targetLayout.textClipRight, p)
                val titleAlpha = lerp(startLayout.titleAlpha, targetLayout.titleAlpha, p)
                val artistAlpha = if (!mediaHasTrack) 0f else lerp(startLayout.artistAlpha, targetLayout.artistAlpha, p)
                val isCenterAligned = if (p >= 0.5f) targetLayout.isCenterAligned else startLayout.isCenterAligned

                // 1. Draw Album Art
                coverDrawable?.let { cov ->
                    canvas.save()
                    clipPath.reset()
                    clipPath.addRoundRect(coverRect, coverRad, coverRad, Path.Direction.CW)
                    canvas.clipPath(clipPath)
                    cov.setBounds(coverRect.left.toInt(), coverRect.top.toInt(), coverRect.right.toInt(), coverRect.bottom.toInt())
                    cov.draw(canvas)
                    canvas.restore()
                }

                // 2. Draw Device Icon
                if (deviceIconAlpha > 0.01f && deviceIconDrawable != null) {
                    val origDiAlpha = deviceIconDrawable!!.alpha
                    deviceIconDrawable!!.alpha = (255 * deviceIconAlpha).toInt().coerceIn(0, 255)
                    deviceIconDrawable!!.setBounds(
                        deviceIconRect.left.toInt(),
                        deviceIconRect.top.toInt(),
                        deviceIconRect.right.toInt(),
                        deviceIconRect.bottom.toInt()
                    )
                    deviceIconDrawable!!.draw(canvas)
                    deviceIconDrawable!!.alpha = origDiAlpha
                }

                // 3. Draw Controls
                val controlsAlpha = lerp(startLayout.controlsAlpha, targetLayout.controlsAlpha, p)
                if (controlsAlpha > 0.01f) {
                    prevDrawable?.let {
                        val origAlpha = it.alpha
                        it.alpha = (255 * controlsAlpha).toInt().coerceIn(0, 255)
                        it.setBounds(prevRect.left.toInt(), prevRect.top.toInt(), prevRect.right.toInt(), prevRect.bottom.toInt())
                        it.draw(canvas)
                        it.alpha = origAlpha
                    }
                    playDrawable?.let {
                        val origAlpha = it.alpha
                        it.alpha = (255 * controlsAlpha).toInt().coerceIn(0, 255)
                        it.setBounds(playRect.left.toInt(), playRect.top.toInt(), playRect.right.toInt(), playRect.bottom.toInt())
                        it.draw(canvas)
                        it.alpha = origAlpha
                    }
                    nextDrawable?.let {
                        val origAlpha = it.alpha
                        it.alpha = (255 * controlsAlpha).toInt().coerceIn(0, 255)
                        it.setBounds(nextRect.left.toInt(), nextRect.top.toInt(), nextRect.right.toInt(), nextRect.bottom.toInt())
                        it.draw(canvas)
                        it.alpha = origAlpha
                    }
                }

                // 3.5. Draw Collapsed Progress Bar
                val pbRect = RectF(
                    lerp(startLayout.progressBarRect.left, targetLayout.progressBarRect.left, p),
                    lerp(startLayout.progressBarRect.top, targetLayout.progressBarRect.top, p),
                    lerp(startLayout.progressBarRect.right, targetLayout.progressBarRect.right, p),
                    lerp(startLayout.progressBarRect.bottom, targetLayout.progressBarRect.bottom, p)
                )
                val pbAlpha = lerp(startLayout.progressBarAlpha, targetLayout.progressBarAlpha, p)
                if (pbAlpha > 0.01f && pbRect.width() > 10f) {
                    val pbTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#4DFFFFFF")
                        alpha = (255 * 0.35f * pbAlpha).toInt()
                    }
                    val pbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        alpha = (255 * 0.95f * pbAlpha).toInt()
                    }
                    val pbRadius = pbRect.height() / 2f
                    canvas.drawRoundRect(pbRect, pbRadius, pbRadius, pbTrackPaint)
                    val progFrac = if (mediaProgressFraction > 0.01f) mediaProgressFraction.coerceIn(0f, 1f) else 0.35f
                    val fillWidth = pbRect.width() * progFrac
                    val fillRect = RectF(pbRect.left, pbRect.top, pbRect.left + fillWidth, pbRect.bottom)
                    canvas.drawRoundRect(fillRect, pbRadius, pbRadius, pbFillPaint)
                }

                // 4. Draw Song Title & Artist
                if (!titleText.isNullOrEmpty() && titleAlpha > 0.01f) {
                    val origTitleAlpha = titlePaint.alpha
                    val origSubAlpha = subtitlePaint.alpha
                    titlePaint.alpha = (255 * titleAlpha).toInt().coerceIn(0, 255)
                    subtitlePaint.alpha = (255 * artistAlpha).toInt().coerceIn(0, 255)

                    val fontMetrics = titlePaint.fontMetrics
                    val gapBetween = 4f * density

                    if (textClipRight > textStartX) {
                        canvas.save()
                        canvas.clipRect(textStartX, currentRect.top, textClipRight, currentRect.bottom)
                        val availW = textClipRight - textStartX
                        val displayTitle = if (availW > 10f) {
                            TextUtils.ellipsize(titleText, titlePaint, availW, TextUtils.TruncateAt.END).toString()
                        } else titleText!!

                        val actualStartX = if (isCenterAligned) {
                            val measuredW = titlePaint.measureText(displayTitle)
                            (textStartX + (availW - measuredW) / 2f).coerceAtLeast(textStartX)
                        } else textStartX

                        val titleBaseline = textTopY - fontMetrics.ascent
                        canvas.drawText(displayTitle, actualStartX, titleBaseline, titlePaint)

                        if (mediaHasTrack && !subtitleText.isNullOrEmpty() && artistAlpha > 0.01f) {
                            val displayArtist = if (availW > 10f) {
                                TextUtils.ellipsize(subtitleText, subtitlePaint, availW, TextUtils.TruncateAt.END).toString()
                            } else subtitleText!!

                            val actualSubX = if (isCenterAligned) {
                                val measuredSubW = subtitlePaint.measureText(displayArtist)
                                (textStartX + (availW - measuredSubW) / 2f).coerceAtLeast(textStartX)
                            } else textStartX

                            val subBaseline = titleBaseline + fontMetrics.descent + gapBetween - subtitlePaint.fontMetrics.ascent
                            canvas.drawText(displayArtist, actualSubX, subBaseline, subtitlePaint)
                        }
                        canvas.restore()
                    }

                    titlePaint.alpha = origTitleAlpha
                    subtitlePaint.alpha = origSubAlpha
                }

                // 5. Curved resize handle at bottom-right corner
                val handleSize = 34f * density
                val hLeft = currentRect.right - handleSize
                val hTop = currentRect.bottom - handleSize
                handleDrawable.setBounds(hLeft.toInt(), hTop.toInt(), currentRect.right.toInt(), currentRect.bottom.toInt())
                handleDrawable.draw(canvas)
                return
            }

            // 1. Draw Unstretched Icon
            iconDrawable?.let { ic ->
                ic.setBounds(
                    iconRect.left.toInt(),
                    iconRect.top.toInt(),
                    iconRect.right.toInt(),
                    iconRect.bottom.toInt()
                )
                ic.alpha = 255
                ic.draw(canvas)
            }

            // 4. Draw Unstretched Revealed Text (Reveals as tile expands without downscaling)
            if (!titleText.isNullOrEmpty()) {
                val fontMetrics = titlePaint.fontMetrics
                val titleH = fontMetrics.descent - fontMetrics.ascent
                val subH = if (!subtitleText.isNullOrEmpty()) {
                    val sm = subtitlePaint.fontMetrics
                    sm.descent - sm.ascent
                } else 0f
                val gapBetween = 3f * density
                val totalTextH = if (subH > 0) titleH + subH + gapBetween else titleH

                when (layoutMode) {
                    LAYOUT_MODE_HORIZONTAL -> {
                        // Icon on left, text on right, vertically centered
                        val textStartX = iconRect.right + 12f * density
                        val textCenterY = currentRect.centerY()
                        val titleBaseline = textCenterY - totalTextH / 2f - fontMetrics.ascent
                        val subBaseline = titleBaseline + fontMetrics.descent + gapBetween - subtitlePaint.fontMetrics.ascent

                        val clipRight = currentRect.right - 14f * density
                        if (clipRight > textStartX) {
                            canvas.save()
                            canvas.clipRect(textStartX, currentRect.top, clipRight, currentRect.bottom)
                            canvas.drawText(titleText!!, textStartX, titleBaseline, titlePaint)
                            if (!subtitleText.isNullOrEmpty()) {
                                canvas.drawText(subtitleText!!, textStartX, subBaseline, subtitlePaint)
                            }
                            canvas.restore()
                        }
                    }
                    LAYOUT_MODE_VERTICAL -> {
                        // Icon at top, text below icon
                        val textStartX = currentRect.left + 14f * density
                        val textStartY = iconRect.bottom + 10f * density
                        val titleBaseline = textStartY - fontMetrics.ascent
                        val subBaseline = titleBaseline + fontMetrics.descent + gapBetween - subtitlePaint.fontMetrics.ascent

                        val clipBottom = currentRect.bottom - 10f * density
                        if (clipBottom > textStartY) {
                            canvas.save()
                            canvas.clipRect(currentRect.left + 6f * density, textStartY, currentRect.right - 6f * density, clipBottom)
                            canvas.drawText(titleText!!, textStartX, titleBaseline, titlePaint)
                            if (!subtitleText.isNullOrEmpty()) {
                                canvas.drawText(subtitleText!!, textStartX, subBaseline, subtitlePaint)
                            }
                            canvas.restore()
                        }
                    }
                    LAYOUT_MODE_LARGE -> {
                        // 2x2: Icon at upper-center, text below icon centered
                        val textStartY = iconRect.bottom + 12f * density
                        val titleBaseline = textStartY - fontMetrics.ascent
                        val subBaseline = titleBaseline + fontMetrics.descent + gapBetween - subtitlePaint.fontMetrics.ascent
                        val centerX = currentRect.centerX()

                        titlePaint.textAlign = Paint.Align.CENTER
                        subtitlePaint.textAlign = Paint.Align.CENTER

                        val clipBottom = currentRect.bottom - 10f * density
                        if (clipBottom > textStartY) {
                            canvas.save()
                            canvas.clipRect(currentRect.left + 6f * density, textStartY, currentRect.right - 6f * density, clipBottom)
                            canvas.drawText(titleText!!, centerX, titleBaseline, titlePaint)
                            if (!subtitleText.isNullOrEmpty()) {
                                canvas.drawText(subtitleText!!, centerX, subBaseline, subtitlePaint)
                            }
                            canvas.restore()
                        }
                        titlePaint.textAlign = Paint.Align.LEFT
                        subtitlePaint.textAlign = Paint.Align.LEFT
                    }
                }
            }

            // 5. Draw Curved Resize Handle at bottom-right corner
            val handleSize = 34f * density
            val hLeft = currentRect.right - handleSize
            val hTop = currentRect.bottom - handleSize
            handleDrawable.setBounds(hLeft.toInt(), hTop.toInt(), currentRect.right.toInt(), currentRect.bottom.toInt())
            handleDrawable.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class FluidTileResizeTouchListener(
        private val ctx: Context,
        private val spec: String,
        private val tileView: View,
        private val density: Float
    ) : View.OnTouchListener {

        private val isBrightnessSpec = "brightness".equals(spec, ignoreCase = true)
        private val isVolumeSpec = "volume".equals(spec, ignoreCase = true)
        private val isSliderSpec = isBrightnessSpec || isVolumeSpec
        private val isMediaSpec = "media".equals(spec, ignoreCase = true)

        companion object {
            const val STATE_NORMAL = 0
            const val STATE_EXPANDED = 1
            const val STATE_VERTICAL = 2
            const val STATE_LARGE = 3

            const val MEDIA_2X1 = 10
            const val MEDIA_1X2 = 11
            const val MEDIA_2X2 = 12
            const val MEDIA_4X1 = 13
            const val MEDIA_4X2 = 14
            const val MEDIA_2X4 = 15
        }

        private var downRawX = 0f
        private var downRawY = 0f
        private var isDragging = false
        private var initialState = STATE_NORMAL
        private var initialCols = 2
        private var candidateTargetState = -1

        private var targetContainer: ViewGroup? = null
        private var overlayView: FluidResizeOverlayView? = null
        private val morphDrawable: FluidTileMorphOverlayDrawable?
            get() = overlayView?.morphDrawable
        private val iconSize = 26f * density

        private var initialGridData: True2DGridEngine.AdapterGridData? = null
        private val childPosMap = mutableMapOf<View, Int>()

        private var tileLeft = 0f
        private var tileTop = 0f
        private var iconFrameLeft = 0f
        private var iconFrameTop = 0f
        private var iconFrameW = 0f
        private var iconFrameH = 0f

        private var originLeft = 0f
        private var originTop = 0f
        private var originWidth = 0f
        private var originHeight = 0f

        private var colW = 0f
        private var twoColW = 0f
        private var threeColW = 0f
        private var fourColW = 0f
        private var cardH = 0f
        private var tallH = 0f
        private var threeRowsH = 0f
        private var fourRowsH = 0f
        private var fiveRowsH = 0f
        private var gap = 0f

        private fun getTargetDims(st: Int): Pair<Float, Float> {
            val (cols, rows) = mediaStateToSpans(st)
            val w = when (cols) {
                1 -> colW
                2 -> twoColW
                3 -> threeColW
                4 -> fourColW
                else -> twoColW
            }
            val h = when (rows) {
                1 -> cardH
                2 -> tallH
                3 -> threeRowsH
                4 -> fourRowsH
                5 -> fiveRowsH
                else -> tallH
            }
            return Pair(w, h)
        }

        private var fixedCornerRadius = 20f * density
        private var stockCardRadius = 20f * density
        private var isMediaPlayingWithTrack = false

        private fun lerp(start: Float, end: Float, fraction: Float): Float {
            return start + (end - start) * fraction.coerceIn(0f, 1f)
        }

        private fun requestDisallowAll(view: View?, disallow: Boolean) {
            var p = view?.parent
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow)
                p = p.parent
            }
        }

        private fun findRecyclerView(view: View?): ViewGroup? {
            var cur = view?.parent
            while (cur != null) {
                if (cur is ViewGroup && cur.javaClass.name.contains("RecyclerView")) {
                    return cur
                }
                cur = cur.parent
            }
            return null
        }

        private fun findIcon(v: View?): Drawable? {
            if (v == null) return null
            if (v is ImageView && v.id != 0x7f099999 && v.id != 0x7f099998 && v.id != 0x7f099992) {
                if (v.drawable != null) return v.drawable
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val d = findIcon(v.getChildAt(i))
                    if (d != null) return d
                }
            }
            return null
        }

        private fun findSeekBar(v: View?): SeekBar? {
            if (v == null) return null
            if (v is SeekBar) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val s = findSeekBar(v.getChildAt(i))
                    if (s != null) return s
                }
            }
            return null
        }

        private fun resolveViewBackground(v: View?): Drawable? {
            if (v == null) return null
            if (v.background != null) return v.background
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i)
                    if (child is ImageView || child is TextView) continue
                    val bg = resolveViewBackground(child)
                    if (bg != null) return bg
                }
            }
            return null
        }

        private fun extractColorFromDrawable(d: Drawable?): Int? {
            if (d == null) return null
            if (d is ColorDrawable) {
                return d.color
            }
            if (d is GradientDrawable) {
                try {
                    val colorStateList = d.color
                    if (colorStateList != null) {
                        return colorStateList.defaultColor
                    }
                } catch (t: Throwable) {}
            }
            if (d is LayerDrawable) {
                for (i in 0 until d.numberOfLayers) {
                    val layer = d.getDrawable(i)
                    val c = extractColorFromDrawable(layer)
                    if (c != null && c != 0) return c
                }
            }
            if (d is InsetDrawable) {
                return extractColorFromDrawable(d.drawable)
            }
            try {
                val bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                val c = Canvas(bm)
                d.setBounds(0, 0, 1, 1)
                d.draw(c)
                val pixel = bm.getPixel(0, 0)
                bm.recycle()
                if (pixel != 0) return pixel
            } catch (t: Throwable) {}
            return null
        }

        private fun inspectTextViews(v: View?, list: MutableList<TextView>) {
            if (v == null) return
            if (v is TextView && v.id != 0x7f099999) {
                val t = v.text?.toString()?.trim()
                if (!t.isNullOrEmpty()) list.add(v)
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    inspectTextViews(v.getChildAt(i), list)
                }
            }
        }

        private fun getChildPosition(rv: ViewGroup, child: View): Int {
            try {
                val m = rv.javaClass.getMethod("getChildAdapterPosition", View::class.java)
                val pos = m.invoke(rv, child) as? Int
                if (pos != null && pos >= 0) return pos
            } catch (t: Throwable) {}
            try {
                val lp = child.layoutParams
                val m = lp.javaClass.getMethod("getViewAdapterPosition")
                val pos = m.invoke(lp) as? Int
                if (pos != null && pos >= 0) return pos
            } catch (t: Throwable) {}
            try {
                val lp = child.layoutParams
                val m = lp.javaClass.getMethod("getViewLayoutPosition")
                val pos = m.invoke(lp) as? Int
                if (pos != null && pos >= 0) return pos
            } catch (t: Throwable) {}
            return -1
        }

        /**
         * Real-time displacement logic matching stock HyperOS rearrangement using True2DGridEngine.
         *
         * WHY THIS PREVENTS SCRAMBLING & MATCHES STOCK HYPEROS EXACTLY:
         * 1. Stock HyperOS uses a 4-column 2D bin-packing algorithm to arrange QS tiles.
         * 2. Naive or dynamic offset chaining causes neighbour scrambling because moving one tile
         *    alters the physical layout boundaries of adjacent views unpredictably.
         * 3. True2DGridEngine computes a deterministic, pure virtual simulation of the entire grid
         *    with the requested tile's candidate span override (e.g. 1x1 -> 2x1, 1x2, or 2x2).
         * 4. For each neighbour view in the RecyclerView, its target translation is calculated
         *    strictly relative to its un-translated baseline position:
         *        deltaCol = candItem.col - initItem.col
         *        deltaRow = candItem.row - initItem.row
         *        targetTx = deltaCol * (colW + gap)
         *        targetTy = deltaRow * unitRowHeight
         * 5. Because delta offsets are derived purely from initial state to target state,
         *    tiles animate along direct, non-conflicting trajectories with DecelerateInterpolator,
         *    eliminating all scrambling and perfectly mirroring the stock HyperOS rearrangement.
         */
        private fun updateNeighbourDisplacements(
            rv: ViewGroup,
            targetSpanW: Int,
            targetSpanH: Int
        ) {
            val initialGrid = initialGridData ?: return
            val adapter = try {
                rv.javaClass.getMethod("getAdapter").invoke(rv)
            } catch (t: Throwable) { null } ?: True2DGridEngine.getActiveAdapter()

            val candidateGrid = True2DGridEngine.calculateLayout(
                adapter,
                ctx,
                overrideSpec = spec,
                overrideSpanW = targetSpanW,
                overrideSpanH = targetSpanH
            )

            val unitRowHeight = True2DGridEngine.getUnitRowHeight(ctx).toFloat()
            val colStep = colW + gap

            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i) ?: continue
                if (child === tileView || child.visibility == View.GONE) continue

                val pos = childPosMap[child] ?: continue
                if (pos < 0) continue

                val initItem = initialGrid.items.getOrNull(pos) ?: continue
                val candItem = candidateGrid.items.getOrNull(pos) ?: continue

                val deltaCol = candItem.col - initItem.col
                val deltaRow = candItem.row - initItem.row

                val targetTx = deltaCol * colStep
                val targetTy = deltaRow * unitRowHeight

                child.animate()
                    .translationX(targetTx)
                    .translationY(targetTy)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!ControlCenterConfigHolder.sIsEditMode) {
                v.visibility = View.GONE
                v.setOnTouchListener(null)
                v.setOnClickListener(null)
                v.isClickable = false
                return false
            }

            if (isSliderSpec) {
                val isHoriz = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderHorizontal else ControlCenterConfigHolder.sVolumeSliderHorizontal
                if (!isHoriz) {
                    v.visibility = View.GONE
                    v.setOnTouchListener(null)
                    v.setOnClickListener(null)
                    v.isClickable = false
                    return false
                }
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = true
                    requestDisallowAll(v, true)

                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    candidateTargetState = -1

                    val rv = findRecyclerView(tileView) ?: (tileView.rootView as? ViewGroup)
                    targetContainer = rv
                    targetContainer?.overlay?.clear()

                    if (rv != null) {
                        for (i in 0 until rv.childCount) {
                            val c = rv.getChildAt(i) ?: continue
                            c.animate().cancel()
                            c.translationX = 0f
                            c.translationY = 0f
                        }

                        val adapter = try {
                            rv.javaClass.getMethod("getAdapter").invoke(rv)
                        } catch (t: Throwable) { null } ?: True2DGridEngine.getActiveAdapter()

                        initialGridData = True2DGridEngine.calculateLayout(adapter, ctx)
                        childPosMap.clear()
                        for (i in 0 until rv.childCount) {
                            val c = rv.getChildAt(i) ?: continue
                            val pos = getChildPosition(rv, c)
                            if (pos >= 0) {
                                childPosMap[c] = pos
                            }
                        }

                        val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                        val iconFrame = if (iconFrameId != 0) tileView.findViewById<View>(iconFrameId) else null

                        val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
                        cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId).toFloat() else (71.5f * density)
                        val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
                        tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId).toFloat() else (157f * density)
                        val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
                        gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId).toFloat() else (10f * density)
                        threeRowsH = 3f * cardH + 2f * gap
                        fourRowsH = 4f * cardH + 3f * gap
                        fiveRowsH = 5f * cardH + 4f * gap

                        val rvContentW = (rv.width - rv.paddingLeft - rv.paddingRight).toFloat()
                        colW = if (rvContentW > 100f * density) {
                            (rvContentW - 3f * gap) / 4f
                        } else {
                            56f * density
                        }
                        twoColW = colW * 2f + gap
                        threeColW = colW * 3f + 2f * gap
                        fourColW = if (rvContentW > 100f * density) rvContentW else (colW * 4f + 3f * gap)

                        val locTile = IntArray(2)
                        val locRv = IntArray(2)
                        tileView.getLocationInWindow(locTile)
                        rv.getLocationInWindow(locRv)
                        tileLeft = (locTile[0] - locRv[0]).toFloat()
                        tileTop = (locTile[1] - locRv[1]).toFloat()

                        // Query stock card colors
                        val enabledColorResId = ctx.resources.getIdentifier("qs_card_enabled_color", "color", ctx.packageName)
                        val disabledColorResId = ctx.resources.getIdentifier("qs_card_disabled_color", "color", ctx.packageName)
                        val stockEnabledColor = if (enabledColorResId != 0) {
                            try { ctx.resources.getColor(enabledColorResId, null) } catch (t: Throwable) { 0xFF0088FF.toInt() }
                        } else 0xFF0088FF.toInt()
                        val stockDisabledColor = if (disabledColorResId != 0) {
                            try { ctx.resources.getColor(disabledColorResId, null) } catch (t: Throwable) { Color.parseColor("#4D000000") }
                        } else Color.parseColor("#4D000000")

                        // Query stock corner radii
                        val cardRadiusResId = ctx.resources.getIdentifier("control_center_universal_corner_radius", "dimen", ctx.packageName)
                        stockCardRadius = if (cardRadiusResId != 0) ctx.resources.getDimension(cardRadiusResId) else (20f * density)

                        val isNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                        if (isSliderSpec) {
                            val w = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                            initialCols = w.coerceIn(2, 4)
                            candidateTargetState = initialCols

                            originLeft = tileLeft
                            originTop = tileTop
                            originWidth = if (tileView.width > 0) tileView.width.toFloat() else when (initialCols) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }
                            originHeight = if (tileView.height > 0) tileView.height.toFloat() else cardH

                            // Slider corner radius strictly equals universal stock card radius
                            fixedCornerRadius = stockCardRadius

                            val rawIcon = findIcon(tileView)
                            val iconClone = rawIcon?.constantState?.newDrawable()?.mutate() ?: rawIcon

                            val seekBar = findSeekBar(tileView)
                            val sliderProgress = if (seekBar != null && seekBar.max > 0) {
                                seekBar.progress.toFloat() / seekBar.max.toFloat()
                            } else 0.5f

                            val nativeBgDrawable = resolveViewBackground(tileView)?.constantState?.newDrawable()?.mutate()
                            val currentBgColor = extractColorFromDrawable(nativeBgDrawable ?: tileView.background)
                            val resolvedBgColor = when {
                                currentBgColor != null && currentBgColor != 0 -> currentBgColor
                                else -> stockDisabledColor
                            }

                            val cl = tileView.javaClass.classLoader ?: ctx.classLoader
                            overlayView = FluidResizeOverlayView(ctx, density).apply {
                                this.isNight = isNight
                                morphDrawable.setup(
                                    icon = iconClone,
                                    title = null,
                                    subtitle = null,
                                    bgColor = resolvedBgColor,
                                    isSliderItem = true,
                                    customBg = nativeBgDrawable,
                                    sliderProgress = sliderProgress
                                )
                                currentCornerRadius = fixedCornerRadius
                                applyStockBackgroundOrBlur(
                                    tileView = tileView,
                                    classLoader = cl,
                                    isActiveTile = false,
                                    isSlider = true,
                                    isMedia = false
                                )
                                val initRect = RectF(originLeft, originTop, originLeft + originWidth, originTop + originHeight)
                                val initLocalRect = RectF(0f, 0f, originWidth, originHeight)
                                val initLocalIconBox = RectF(16f * density, (originHeight - iconSize) / 2f, 16f * density + iconSize, (originHeight + iconSize) / 2f)
                                updateMorph(initRect, initLocalRect, fixedCornerRadius, initLocalIconBox, false, LAYOUT_MODE_HORIZONTAL)
                            }
                        } else if (isMediaSpec) {
                            initialState = mediaSizeStringToState(ControlCenterConfigHolder.sMediaPlayerSize)
                            candidateTargetState = initialState

                            originLeft = tileLeft
                            originTop = tileTop
                            val (initTargetW, initTargetH) = getTargetDims(initialState)
                            originWidth = if (tileView.width > 0) tileView.width.toFloat() else initTargetW
                            originHeight = if (tileView.height > 0) tileView.height.toFloat() else initTargetH

                            fixedCornerRadius = stockCardRadius

                            // Sample media components: album art, title, artist, controls
                            var mediaCover: Drawable? = null
                            var mediaPlay: Drawable? = null
                            var mediaPrev: Drawable? = null
                            var mediaNext: Drawable? = null
                            var mediaDeviceIcon: Drawable? = null
                            var mediaTitle: String? = null
                            var mediaArtist: String? = null
                            var mediaTitleSize: Float = 0f
                            var mediaTitleTypeface: Typeface? = null
                            var mediaTitleColor: Int = 0
                            var mediaArtistSize: Float = 0f
                            var mediaArtistTypeface: Typeface? = null
                            var mediaArtistColor: Int = 0

                            val holder = ControlCenterHook.sMediaPlayerViewHolder?.get()
                            if (holder != null) {
                                try {
                                    val bField = holder.javaClass.getDeclaredField("binding").apply { isAccessible = true }
                                    val b = bField.get(holder)
                                    if (b != null) {
                                        fun getV(name: String): View? = try {
                                            b.javaClass.getField(name).get(b) as? View
                                        } catch (t: Throwable) { null }

                                        val covView = getV("cover") as? ImageView
                                        mediaCover = covView?.drawable?.constantState?.newDrawable()?.mutate() ?: covView?.drawable

                                        val plView = getV("play") as? ImageView
                                        mediaPlay = plView?.drawable?.constantState?.newDrawable()?.mutate() ?: plView?.drawable

                                        val prView = getV("prev") as? ImageView
                                        mediaPrev = prView?.drawable?.constantState?.newDrawable()?.mutate() ?: prView?.drawable

                                        val nxView = getV("next") as? ImageView
                                        mediaNext = nxView?.drawable?.constantState?.newDrawable()?.mutate() ?: nxView?.drawable

                                        val diView = getV("deviceIcon") as? ImageView
                                        mediaDeviceIcon = diView?.drawable?.constantState?.newDrawable()?.mutate() ?: diView?.drawable

                                        val tView = getV("title") as? TextView
                                        mediaTitle = tView?.text?.toString()?.takeIf { it.isNotBlank() }
                                        if (tView != null) {
                                            mediaTitleSize = tView.textSize
                                            mediaTitleTypeface = tView.typeface
                                            mediaTitleColor = tView.currentTextColor
                                        }

                                        val aView = getV("artist") as? TextView
                                        mediaArtist = aView?.text?.toString()?.takeIf { it.isNotBlank() }
                                        if (aView != null) {
                                            mediaArtistSize = aView.textSize
                                            mediaArtistTypeface = aView.typeface
                                            mediaArtistColor = aView.currentTextColor
                                        }

                                        if (mediaTitle == null) {
                                            val emptyView = getV("emptyState") as? TextView
                                            if (emptyView != null && emptyView.visibility == View.VISIBLE) {
                                                mediaTitle = emptyView.text?.toString()?.takeIf { it.isNotBlank() }
                                                if (emptyView.textSize > 0f) {
                                                    mediaTitleSize = emptyView.textSize
                                                    mediaTitleTypeface = emptyView.typeface
                                                    mediaTitleColor = emptyView.currentTextColor
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {}
                            }

                            if (mediaCover == null || mediaPlay == null) {
                                val imageViews = mutableListOf<ImageView>()
                                fun findImages(v: View?) {
                                    if (v == null) return
                                    if (v is ImageView && v.id != 0x7f099999 && v.id != 0x7f099998 && v.id != 0x7f099992) {
                                        if (v.drawable != null) imageViews.add(v)
                                    } else if (v is ViewGroup) {
                                        for (i in 0 until v.childCount) findImages(v.getChildAt(i))
                                    }
                                }
                                findImages(tileView)

                                if (mediaCover == null && imageViews.isNotEmpty()) {
                                    val largest = imageViews.maxByOrNull { it.width * it.height }
                                    mediaCover = largest?.drawable?.constantState?.newDrawable()?.mutate() ?: largest?.drawable
                                }
                                if (mediaPlay == null) {
                                    val buttons = imageViews.filter { it !== imageViews.maxByOrNull { iv -> iv.width * iv.height } }
                                    if (buttons.isNotEmpty()) {
                                        mediaPrev = buttons.getOrNull(0)?.drawable?.constantState?.newDrawable()?.mutate() ?: buttons.getOrNull(0)?.drawable
                                        mediaPlay = buttons.getOrNull(1)?.drawable?.constantState?.newDrawable()?.mutate() ?: buttons.getOrNull(1)?.drawable
                                        mediaNext = buttons.getOrNull(2)?.drawable?.constantState?.newDrawable()?.mutate() ?: buttons.getOrNull(2)?.drawable
                                    }
                                }
                            }

                            if (mediaCover == null) {
                                val defCoverId = ctx.resources.getIdentifier("miplay_cover_default", "drawable", ctx.packageName)
                                if (defCoverId != 0) {
                                    try { mediaCover = ctx.resources.getDrawable(defCoverId, null)?.constantState?.newDrawable()?.mutate() } catch (t: Throwable) {}
                                }
                                if (mediaCover == null) {
                                    mediaCover = DefaultCoverDrawable(density)
                                }
                            }

                            if (mediaPlay == null) mediaPlay = MediaVectorButtonDrawable("play", density)
                            if (mediaPrev == null) mediaPrev = MediaVectorButtonDrawable("prev", density)
                            if (mediaNext == null) mediaNext = MediaVectorButtonDrawable("next", density)
                            if (mediaDeviceIcon == null) {
                                val defCastId = ctx.resources.getIdentifier("ic_media_route", "drawable", ctx.packageName)
                                if (defCastId != 0) {
                                    try { mediaDeviceIcon = ctx.resources.getDrawable(defCastId, null)?.constantState?.newDrawable()?.mutate() } catch (t: Throwable) {}
                                }
                                if (mediaDeviceIcon == null) {
                                    mediaDeviceIcon = MediaVectorButtonDrawable("cast", density)
                                }
                            }

                            if (mediaTitle == null || mediaArtist == null) {
                                val textViews = mutableListOf<TextView>()
                                inspectTextViews(tileView, textViews)
                                if (mediaTitle == null) {
                                    val tv0 = textViews.getOrNull(0)
                                    mediaTitle = tv0?.text?.toString()?.takeIf { it.isNotBlank() } ?: "Music"
                                    if (tv0 != null && mediaTitleSize <= 0f) {
                                        mediaTitleSize = tv0.textSize
                                        mediaTitleTypeface = tv0.typeface
                                        mediaTitleColor = tv0.currentTextColor
                                    }
                                }
                                if (mediaArtist == null) {
                                    val tv1 = textViews.getOrNull(1)
                                    mediaArtist = tv1?.text?.toString()?.takeIf { it.isNotBlank() }
                                    if (tv1 != null && mediaArtistSize <= 0f) {
                                        mediaArtistSize = tv1.textSize
                                        mediaArtistTypeface = tv1.typeface
                                        mediaArtistColor = tv1.currentTextColor
                                    }
                                }
                            }

                            val meta = ControlCenterHook.sLastMediaPlayerMetaData
                            var hasTrack = false
                            if (meta != null) {
                                try {
                                    val titleProp = meta.javaClass.getMethod("getTitle").invoke(meta) as? CharSequence
                                    if (!titleProp.isNullOrBlank()) {
                                        hasTrack = true
                                    }
                                } catch (t: Throwable) {
                                    hasTrack = true
                                }
                            }
                            if (!hasTrack && mediaTitle != null && !mediaTitle.equals("Not playing", ignoreCase = true) && !mediaTitle.equals("Music", ignoreCase = true)) {
                                hasTrack = true
                            }
                            var emptyStateVisible = false
                            if (holder != null) {
                                try {
                                    val bField = holder.javaClass.getDeclaredField("binding").apply { isAccessible = true }
                                    val b = bField.get(holder)
                                    if (b != null) {
                                        val emptyView = b.javaClass.getField("emptyState").get(b) as? View
                                        if (emptyView != null && emptyView.visibility == View.VISIBLE) {
                                            emptyStateVisible = true
                                        }
                                    }
                                } catch (t: Throwable) {}
                            }
                            if (emptyStateVisible) {
                                hasTrack = false
                            }
                            if (!hasTrack) {
                                mediaArtist = null
                            }
                            isMediaPlayingWithTrack = hasTrack

                            val nativeBgDrawable = resolveViewBackground(tileView)?.constantState?.newDrawable()?.mutate()
                            val currentBgColor = extractColorFromDrawable(nativeBgDrawable ?: tileView.background)
                            val resolvedBgColor = when {
                                currentBgColor != null && currentBgColor != 0 -> currentBgColor
                                else -> stockDisabledColor
                            }

                            val cl = tileView.javaClass.classLoader ?: ctx.classLoader
                            overlayView = FluidResizeOverlayView(ctx, density).apply {
                                this.isNight = isNight
                                morphDrawable.setupMedia(
                                    cover = mediaCover,
                                    play = mediaPlay,
                                    prev = mediaPrev,
                                    next = mediaNext,
                                    deviceIcon = mediaDeviceIcon,
                                    title = mediaTitle,
                                    artist = mediaArtist,
                                    bgColor = resolvedBgColor,
                                    customBg = nativeBgDrawable,
                                    progress = 0.35f,
                                    hasTrack = isMediaPlayingWithTrack,
                                    titleSize = mediaTitleSize,
                                    titleTypeface = mediaTitleTypeface,
                                    titleColor = mediaTitleColor,
                                    artistSize = mediaArtistSize,
                                    artistTypeface = mediaArtistTypeface,
                                    artistColor = mediaArtistColor
                                )
                                currentCornerRadius = fixedCornerRadius
                                applyStockBackgroundOrBlur(
                                    tileView = tileView,
                                    classLoader = cl,
                                    isActiveTile = false,
                                    isSlider = false,
                                    isMedia = true
                                )
                                val initRect = RectF(originLeft, originTop, originLeft + originWidth, originTop + originHeight)
                                val initLocalRect = RectF(0f, 0f, originWidth, originHeight)
                                updateMediaMorph(initRect, initLocalRect, fixedCornerRadius, initialState, initialState, 0f, isMediaPlayingWithTrack)
                            }
                        } else {
                            initialState = when {
                                ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_LARGE
                                ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_EXPANDED
                                ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_VERTICAL
                                ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) && !ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) -> STATE_EXPANDED
                                else -> STATE_NORMAL
                            }
                            candidateTargetState = initialState

                            if (initialState == STATE_NORMAL && iconFrame != null) {
                                val locIcon = IntArray(2)
                                iconFrame.getLocationInWindow(locIcon)
                                iconFrameLeft = (locIcon[0] - locRv[0]).toFloat()
                                iconFrameTop = (locIcon[1] - locRv[1]).toFloat()
                                iconFrameW = (iconFrame.width.toFloat()).coerceAtLeast(56f * density)
                                iconFrameH = (iconFrame.height.toFloat()).coerceAtLeast(56f * density)
                            } else {
                                iconFrameLeft = tileLeft
                                iconFrameTop = tileTop
                                iconFrameW = tileView.width.toFloat().coerceAtLeast(colW)
                                iconFrameH = tileView.height.toFloat().coerceAtLeast(cardH)
                            }
                            originLeft = iconFrameLeft
                            originTop = iconFrameTop
                            originWidth = iconFrameW
                            originHeight = iconFrameH

                            fixedCornerRadius = if (initialState == STATE_NORMAL) {
                                originWidth.coerceAtMost(colW) / 2f
                            } else {
                                stockCardRadius
                            }

                            // Extract visual components for morph overlay
                            val rawIcon = findIcon(iconFrame) ?: findIcon(tileView)
                            val iconClone = rawIcon?.constantState?.newDrawable()?.mutate() ?: rawIcon

                            val textViews = mutableListOf<TextView>()
                            inspectTextViews(tileView, textViews)
                            val titleTv = textViews.getOrNull(0)
                            val subTv = textViews.getOrNull(1)

                            val title = titleTv?.text?.toString()?.takeIf { it.isNotBlank() } ?: spec.replaceFirstChar { it.uppercase() }
                            val subtitle = if (subTv != null && !subTv.text.isNullOrBlank() && subTv.text.length <= 20) {
                                subTv.text.toString()
                            } else {
                                val isActive = tileView.isActivated || tileView.isSelected || (iconFrame?.isActivated == true) || (iconFrame?.isSelected == true)
                                if (isActive) "On" else "Off"
                            }

                            val isActive = tileView.isActivated || tileView.isSelected || (iconFrame?.isActivated == true) || (iconFrame?.isSelected == true)
                            val isInsideContainer = ControlCenterHook.isChildOfContainer(tileView)

                            val sourceBg = if (initialState == STATE_NORMAL && iconFrame?.background != null) iconFrame.background else (resolveViewBackground(tileView) ?: tileView.background)
                            val currentBgColor = extractColorFromDrawable(sourceBg)
                            val resolvedBgColor = when {
                                currentBgColor != null && currentBgColor != 0 -> currentBgColor
                                isActive -> stockEnabledColor
                                isInsideContainer -> 0x66000000
                                else -> stockDisabledColor
                            }

                            val nativeBgDrawable = if (initialState != STATE_NORMAL) {
                                (resolveViewBackground(tileView) ?: tileView.background)?.constantState?.newDrawable()?.mutate()
                            } else null

                            val cl = tileView.javaClass.classLoader ?: ctx.classLoader
                            overlayView = FluidResizeOverlayView(ctx, density).apply {
                                this.isNight = isNight
                                morphDrawable.setup(
                                    icon = iconClone,
                                    title = title,
                                    subtitle = subtitle,
                                    bgColor = resolvedBgColor,
                                    isSliderItem = false,
                                    customBg = nativeBgDrawable,
                                    sliderProgress = 0f
                                )
                                currentCornerRadius = fixedCornerRadius

                                // Exact stock typography preservation
                                if (titleTv != null && titleTv.textSize >= 14f * density) {
                                    morphDrawable.titlePaint.textSize = titleTv.textSize
                                    morphDrawable.titlePaint.typeface = titleTv.typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                    morphDrawable.titlePaint.color = titleTv.currentTextColor
                                } else {
                                    morphDrawable.titlePaint.textSize = 15.5f * density
                                    morphDrawable.titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                    morphDrawable.titlePaint.color = Color.WHITE
                                }
                                if (subTv != null && subTv.textSize >= 12.5f * density) {
                                    morphDrawable.subtitlePaint.textSize = subTv.textSize
                                    morphDrawable.subtitlePaint.typeface = subTv.typeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
                                    morphDrawable.subtitlePaint.color = subTv.currentTextColor
                                } else {
                                    morphDrawable.subtitlePaint.textSize = 13.5f * density
                                    morphDrawable.subtitlePaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                    morphDrawable.subtitlePaint.color = Color.parseColor("#B3FFFFFF")
                                }

                                applyStockBackgroundOrBlur(
                                    tileView = tileView,
                                    classLoader = cl,
                                    isActiveTile = isActive,
                                    isSlider = false,
                                    isMedia = false
                                )
                                val initRect = RectF(originLeft, originTop, originLeft + originWidth, originTop + originHeight)
                                val initLocalRect = RectF(0f, 0f, originWidth, originHeight)
                                val initLocalIconBox = RectF(
                                    iconFrameLeft - originLeft,
                                    iconFrameTop - originTop,
                                    iconFrameLeft - originLeft + iconFrameW,
                                    iconFrameTop - originTop + iconFrameH
                                )
                                val initMode = when (initialState) {
                                    STATE_LARGE -> LAYOUT_MODE_LARGE
                                    STATE_VERTICAL -> LAYOUT_MODE_VERTICAL
                                    else -> LAYOUT_MODE_HORIZONTAL
                                }
                                updateMorph(initRect, initLocalRect, fixedCornerRadius, initLocalIconBox, false, initMode)
                            }
                        }
                    }

                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = true
                    requestDisallowAll(v, true)

                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val slop = 5f * density

                    if (dist > slop && !isDragging) {
                        isDragging = true
                        targetContainer?.overlay?.clear()
                        overlayView?.let { ov ->
                            targetContainer?.overlay?.add(ov)
                        }
                        tileView.alpha = 0f
                    }

                    if (!isDragging) return true

                    var curW = originWidth
                    var curH = originHeight
                    // Corner radius is strictly preserved at fixedCornerRadius throughout the drag
                    val curRadius = fixedCornerRadius
                    var layoutMode = LAYOUT_MODE_HORIZONTAL
                    val iconBox = RectF()

                    if (isSliderSpec) {
                        // Slider resizing: Height strictly locked to live tileView height, corner radius strictly preserved
                        curH = originHeight
                        curW = (originWidth + dx).coerceIn(twoColW * 0.85f, fourColW * 1.05f)

                        val candidateCols = when {
                            curW > (threeColW + fourColW) / 2f -> 4
                            curW > (twoColW + threeColW) / 2f -> 3
                            else -> 2
                        }

                        val iconX = originLeft + 16f * density
                        val iconY = originTop + (curH - iconSize) / 2f
                        iconBox.set(iconX, iconY, iconX + iconSize, iconY + iconSize)

                        val drawRect = RectF(originLeft, originTop, originLeft + curW, originTop + curH)
                        val isSnapping = (candidateCols != initialCols)
                        val localRect = RectF(0f, 0f, curW, curH)
                        val localIconBox = RectF(16f * density, (curH - iconSize) / 2f, 16f * density + iconSize, (curH + iconSize) / 2f)
                        overlayView?.updateMorph(drawRect, localRect, fixedCornerRadius, localIconBox, isSnapping, LAYOUT_MODE_HORIZONTAL)
                        targetContainer?.invalidate()

                        if (candidateCols != candidateTargetState) {
                            candidateTargetState = candidateCols
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            targetContainer?.let { rv ->
                                updateNeighbourDisplacements(rv, candidateCols, 1)
                            }
                        }
                        return true
                    }

                    if (isMediaSpec) {
                        val rawW = (originWidth + dx).coerceIn(colW * 0.75f, fourColW * 1.05f)
                        val rawH = (originHeight + dy).coerceIn(cardH * 0.75f, fiveRowsH * 1.05f)

                        val (initCols, initRows) = mediaStateToSpans(initialState)

                        // Directional intent analysis
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)
                        val minMovement = 14f * density

                        var targetState = candidateTargetState.takeIf { it != -1 } ?: initialState

                        if (absDx > minMovement || absDy > minMovement) {
                            val desiredCols = when {
                                rawW < (colW + twoColW) / 2f -> 1
                                rawW < (twoColW + threeColW) / 2f -> 2
                                rawW < (threeColW + fourColW) / 2f -> 3
                                else -> 4
                            }
                            val desiredRows = when {
                                rawH < (cardH + tallH) / 2f -> 1
                                rawH < (tallH + threeRowsH) / 2f -> 2
                                rawH < (threeRowsH + fourRowsH) / 2f -> 3
                                rawH < (fourRowsH + fiveRowsH) / 2f -> 4
                                else -> 5
                            }

                            // If pulling predominantly horizontally from a tall component (e.g. 2x4 -> 4x2)
                            val isPredominantlyHorizontal = absDx > absDy * 1.25f
                            // If pulling predominantly vertically from a wide component (e.g. 4x2 -> 2x4)
                            val isPredominantlyVertical = absDy > absDx * 1.25f

                            targetState = when {
                                isPredominantlyHorizontal && initRows >= 4 && dx > 0 -> {
                                    // 2x4 (or 1x4/2x5) pulled right -> switch directly to opposite 4x2 (or 3x2)
                                    when (desiredCols) {
                                        4 -> MEDIA_4X2
                                        3 -> MEDIA_3X2
                                        else -> MEDIA_2X4
                                    }
                                }
                                isPredominantlyVertical && initCols >= 4 && dy > 0 -> {
                                    // 4x2 (or 4x1) pulled down -> switch directly to opposite 2x4 (or 2x3/2x5)
                                    when (desiredRows) {
                                        5 -> MEDIA_2X5
                                        4 -> MEDIA_2X4
                                        3 -> if (desiredCols >= 3) MEDIA_4X3 else MEDIA_2X3
                                        else -> MEDIA_4X2
                                    }
                                }
                                isPredominantlyHorizontal && initRows == 2 && dx < 0 && desiredCols == 1 -> {
                                    MEDIA_1X2
                                }
                                isPredominantlyVertical && initCols == 2 && dy < 0 && desiredRows == 1 -> {
                                    MEDIA_2X1
                                }
                                else -> {
                                    // General mapping for (desiredCols, desiredRows)
                                    when (desiredCols) {
                                        1 -> when (desiredRows) {
                                            1 -> MEDIA_1X1
                                            2 -> MEDIA_1X2
                                            3 -> MEDIA_1X3
                                            4 -> MEDIA_1X4
                                            else -> MEDIA_1X5
                                        }
                                        2 -> when (desiredRows) {
                                            1 -> MEDIA_2X1
                                            2 -> MEDIA_2X2
                                            3 -> MEDIA_2X3
                                            4 -> MEDIA_2X4
                                            else -> MEDIA_2X5
                                        }
                                        3 -> when (desiredRows) {
                                            1 -> MEDIA_3X1
                                            2 -> MEDIA_3X2
                                            else -> MEDIA_3X3 // 3 columns max 3 rows
                                        }
                                        else -> when (desiredRows) {
                                            1 -> MEDIA_4X1
                                            2 -> MEDIA_4X2
                                            else -> MEDIA_4X3 // 4 columns max 3 rows, no 4x4!
                                        }
                                    }
                                }
                            }
                        }

                        val (targetW, targetH) = getTargetDims(targetState)
                        val dW = targetW - originWidth
                        val dH = targetH - originHeight
                        val distSq = dW * dW + dH * dH

                        val progress = if (targetState != initialState && distSq > 1f) {
                            val proj = (dx * dW + dy * dH) / distSq
                            proj.coerceIn(0f, 1f)
                        } else 0f

                        if (targetState != initialState) {
                            curW = originWidth + progress * dW
                            curH = originHeight + progress * dH
                        } else {
                            curW = (originWidth + dx * 0.25f).coerceIn(originWidth - 12f * density, originWidth + 12f * density)
                            curH = (originHeight + dy * 0.25f).coerceIn(originHeight - 12f * density, originHeight + 12f * density)
                        }

                        val drawRect = RectF(originLeft, originTop, originLeft + curW, originTop + curH)
                        val localRect = RectF(0f, 0f, curW, curH)
                        overlayView?.updateMediaMorph(
                            rect = drawRect,
                            localRect = localRect,
                            radius = fixedCornerRadius,
                            startState = initialState,
                            targetState = targetState,
                            progress = progress,
                            hasTrack = isMediaPlayingWithTrack
                        )
                        targetContainer?.invalidate()

                        if (targetState != candidateTargetState) {
                            candidateTargetState = targetState
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val (spanW, spanH) = mediaStateToSpans(targetState)
                            targetContainer?.let { rv ->
                                updateNeighbourDisplacements(rv, spanW, spanH)
                            }
                        }
                        return true
                    }

                    // Regular Tile Resizing: Semi-fluid directional expansion and shrinking
                    // Top-left anchor is rigidly fixed at originLeft/originTop to eliminate reverse resistance
                    val deltaW = (twoColW - originWidth).coerceAtLeast(30f * density)
                    val deltaH = (tallH - originHeight).coerceAtLeast(30f * density)
                    val snapDistX = deltaW * 0.28f
                    val snapDistY = deltaH * 0.28f

                    var targetState = initialState
                    val curLeft = originLeft
                    val curTop = originTop

                    when (initialState) {
                        STATE_NORMAL -> {
                            val isDiagonal = dx >= snapDistX * 0.75f && dy >= snapDistY * 0.75f
                            val isPureHorizontal = dx > 0 && (dy < 18f * density || dx >= dy * 1.5f)
                            val isPureVertical = dy > 0 && (dx < 18f * density || dy >= dx * 1.5f)

                            when {
                                isDiagonal -> {
                                    targetState = if (dx >= snapDistX && dy >= snapDistY) STATE_LARGE else STATE_NORMAL
                                    curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                    curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                    layoutMode = LAYOUT_MODE_LARGE

                                    val targetIconX = curLeft + (curW - iconSize) / 2f
                                    val targetIconY = curTop + 24f * density
                                    val progress = Math.max((dx / deltaW).coerceIn(0f, 1f), (dy / deltaH).coerceIn(0f, 1f))
                                    val ix = lerp((originWidth - iconSize) / 2f + curLeft, targetIconX, progress)
                                    val iy = lerp((originHeight - iconSize) / 2f + curTop, targetIconY, progress)
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                isPureHorizontal -> {
                                    targetState = if (dx >= snapDistX) STATE_EXPANDED else STATE_NORMAL
                                    val progress = (dx / deltaW).coerceIn(0f, 1f)
                                    curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                    curH = cardH // Strictly locked to 1 row! No vertical growth
                                    layoutMode = LAYOUT_MODE_HORIZONTAL

                                    val ix = curLeft + lerp((originWidth - iconSize) / 2f, 16f * density, progress)
                                    val iy = curTop + (curH - iconSize) / 2f
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                isPureVertical -> {
                                    targetState = if (dy >= snapDistY) STATE_VERTICAL else STATE_NORMAL
                                    val progress = (dy / deltaH).coerceIn(0f, 1f)
                                    curW = colW // Strictly locked to 1 col! No horizontal growth
                                    curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                    layoutMode = LAYOUT_MODE_VERTICAL

                                    val ix = curLeft + (curW - iconSize) / 2f
                                    val iy = curTop + lerp((originHeight - iconSize) / 2f, 16f * density, progress)
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                else -> {
                                    if (dx >= dy) {
                                        targetState = if (dx >= snapDistX) STATE_EXPANDED else STATE_NORMAL
                                        val progress = (dx / deltaW).coerceIn(0f, 1f)
                                        curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                        curH = cardH // Strictly locked to 1 row!
                                        layoutMode = LAYOUT_MODE_HORIZONTAL
                                        val ix = curLeft + lerp((originWidth - iconSize) / 2f, 16f * density, progress)
                                        val iy = curTop + (curH - iconSize) / 2f
                                        iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                    } else {
                                        targetState = if (dy >= snapDistY) STATE_VERTICAL else STATE_NORMAL
                                        val progress = (dy / deltaH).coerceIn(0f, 1f)
                                        curW = colW // Strictly locked to 1 col!
                                        curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                        layoutMode = LAYOUT_MODE_VERTICAL
                                        val ix = curLeft + (curW - iconSize) / 2f
                                        val iy = curTop + lerp((originHeight - iconSize) / 2f, 16f * density, progress)
                                        iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                    }
                                }
                            }
                        }

                        STATE_EXPANDED -> {
                            val shrinkDeltaW = (originWidth - colW).coerceAtLeast(30f * density)
                            val snapShrinkX = shrinkDeltaW * 0.28f

                            if (dx < 0 && (dy <= 0 || -dx >= dy * 1.5f)) {
                                targetState = if (-dx >= snapShrinkX) STATE_NORMAL else STATE_EXPANDED
                                val shrinkProgress = (-dx / shrinkDeltaW).coerceIn(0f, 1f)
                                curW = (originWidth + dx).coerceIn(colW * 0.85f, originWidth)
                                curH = cardH // Locked to 1 row
                                layoutMode = LAYOUT_MODE_HORIZONTAL

                                val ix = curLeft + lerp(16f * density, (curW - iconSize) / 2f, shrinkProgress)
                                val iy = curTop + (curH - iconSize) / 2f
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else if (dy > 0) {
                                targetState = if (dy >= snapDistY) STATE_LARGE else STATE_EXPANDED
                                curW = originWidth // Locked to 2 cols
                                curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                layoutMode = LAYOUT_MODE_LARGE

                                val ix = curLeft + (curW - iconSize) / 2f
                                val iy = curTop + 24f * density
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else {
                                curW = originWidth
                                curH = originHeight
                                layoutMode = LAYOUT_MODE_HORIZONTAL
                                val ix = curLeft + 16f * density
                                val iy = curTop + (curH - iconSize) / 2f
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            }
                        }

                        STATE_VERTICAL -> {
                            val shrinkDeltaH = (originHeight - cardH).coerceAtLeast(30f * density)
                            val snapShrinkY = shrinkDeltaH * 0.28f

                            if (dy < 0 && (dx <= 0 || -dy >= dx * 1.5f)) {
                                targetState = if (-dy >= snapShrinkY) STATE_NORMAL else STATE_VERTICAL
                                val shrinkProgress = (-dy / shrinkDeltaH).coerceIn(0f, 1f)
                                curW = colW // Locked to 1 col
                                curH = (originHeight + dy).coerceIn(cardH * 0.85f, originHeight)
                                layoutMode = LAYOUT_MODE_VERTICAL

                                val ix = curLeft + (curW - iconSize) / 2f
                                val iy = curTop + lerp(16f * density, (curH - iconSize) / 2f, shrinkProgress)
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else if (dx > 0) {
                                targetState = if (dx >= snapDistX) STATE_LARGE else STATE_VERTICAL
                                curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                curH = originHeight // Locked to 2 rows
                                layoutMode = LAYOUT_MODE_LARGE

                                val ix = curLeft + (curW - iconSize) / 2f
                                val iy = curTop + 24f * density
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else {
                                curW = originWidth
                                curH = originHeight
                                layoutMode = LAYOUT_MODE_VERTICAL
                                val ix = curLeft + (curW - iconSize) / 2f
                                val iy = curTop + 16f * density
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            }
                        }

                        STATE_LARGE -> {
                            val shrinkDeltaW = (originWidth - colW).coerceAtLeast(30f * density)
                            val shrinkDeltaH = (originHeight - cardH).coerceAtLeast(30f * density)
                            val snapShrinkX = shrinkDeltaW * 0.28f
                            val snapShrinkY = shrinkDeltaH * 0.28f

                            targetState = when {
                                -dx >= snapShrinkX && -dy >= snapShrinkY -> STATE_NORMAL
                                -dx >= snapShrinkX -> STATE_VERTICAL
                                -dy >= snapShrinkY -> STATE_EXPANDED
                                else -> STATE_LARGE
                            }

                            curW = (originWidth + dx).coerceIn(colW * 0.85f, originWidth)
                            curH = (originHeight + dy).coerceIn(cardH * 0.85f, originHeight)
                            layoutMode = if (targetState == STATE_VERTICAL) LAYOUT_MODE_VERTICAL else if (targetState == STATE_EXPANDED) LAYOUT_MODE_HORIZONTAL else LAYOUT_MODE_LARGE
                            val ix = curLeft + (curW - iconSize) / 2f
                            val iy = curTop + 24f * density
                            iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                        }
                    }

                    // Update morph overlay with strictly preserved corner radius
                    val drawRect = RectF(curLeft, curTop, curLeft + curW, curTop + curH)
                    val isSnapping = (targetState != initialState && targetState != -1)
                    val localRect = RectF(0f, 0f, curW, curH)
                    val localIconBox = RectF(iconBox.left - curLeft, iconBox.top - curTop, iconBox.right - curLeft, iconBox.bottom - curTop)
                    overlayView?.updateMorph(drawRect, localRect, curRadius, localIconBox, isSnapping, layoutMode)
                    targetContainer?.invalidate()

                    // Live neighbour displacement using True2DGridEngine
                    if (targetState != candidateTargetState) {
                        candidateTargetState = targetState
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val targetSpans = when (targetState) {
                            STATE_EXPANDED -> Pair(2, 1)
                            STATE_VERTICAL -> Pair(1, 2)
                            STATE_LARGE -> Pair(2, 2)
                            else -> Pair(1, 1)
                        }
                        targetContainer?.let { rv ->
                            updateNeighbourDisplacements(rv, targetSpans.first, targetSpans.second)
                        }
                    }

                    return true
                }

                MotionEvent.ACTION_UP -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                    requestDisallowAll(v, false)

                    if (isDragging) {
                        val rv = targetContainer

                        if (isSliderSpec) {
                            val finalCols = if (candidateTargetState != -1) candidateTargetState.coerceIn(2, 4) else initialCols
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                            if (finalCols != initialCols) {
                                if (isBrightnessSpec) {
                                    ControlCenterConfigHolder.sBrightnessSliderWidth = finalCols
                                } else {
                                    ControlCenterConfigHolder.sVolumeSliderWidth = finalCols
                                }
                                ControlCenterConfigHolder.savePriorities(ctx)
                            }

                            val finalW = when (finalCols) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }

                            val ov = overlayView
                            if (ov != null && rv != null) {
                                val startRect = RectF(ov.currentRect)
                                val targetRect = RectF(originLeft, originTop, originLeft + finalW, originTop + originHeight)

                                if (finalCols != initialCols) {
                                    var animationEnded = false
                                    var layoutRefreshed = false

                                    fun tryFinish() {
                                        if (animationEnded && layoutRefreshed) {
                                            rv.overlay.clear()
                                            tileView.alpha = 1.0f
                                            overlayView = null
                                        }
                                    }

                                    ControlCenterConfigHolder.setSliderWidth(ctx, isBrightnessSpec, finalCols, tileView) {
                                        layoutRefreshed = true
                                        // Layout with new spans is complete: reset translations with zero visual jump!
                                        for (i in 0 until rv.childCount) {
                                            val child = rv.getChildAt(i) ?: continue
                                            child.translationX = 0f
                                            child.translationY = 0f
                                        }
                                        tryFinish()
                                    }

                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val lib = RectF(16f * density, (r.height() - iconSize) / 2f, 16f * density + iconSize, (r.height() + iconSize) / 2f)
                                            ov.updateMorph(r, lr, fixedCornerRadius, lib, false, LAYOUT_MODE_HORIZONTAL)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                animationEnded = true
                                                tryFinish()
                                            }
                                        })
                                    }
                                    anim.start()
                                } else {
                                    for (i in 0 until rv.childCount) {
                                        val child = rv.getChildAt(i) ?: continue
                                        child.animate().translationX(0f).translationY(0f).setDuration(200).start()
                                    }

                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val lib = RectF(16f * density, (r.height() - iconSize) / 2f, 16f * density + iconSize, (r.height() + iconSize) / 2f)
                                            ov.updateMorph(r, lr, fixedCornerRadius, lib, false, LAYOUT_MODE_HORIZONTAL)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                rv.overlay.clear()
                                                tileView.alpha = 1.0f
                                                overlayView = null
                                            }
                                        })
                                    }
                                    anim.start()
                                }
                            } else {
                                rv?.overlay?.clear()
                                tileView.alpha = 1.0f
                                overlayView = null
                            }
                        } else if (isMediaSpec) {
                            val finalState = if (candidateTargetState != -1) candidateTargetState else initialState
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                            val targetSize = mediaStateToSizeString(finalState)
                            val (targetW, targetH) = getTargetDims(finalState)

                            if (finalState != initialState) {
                                ControlCenterConfigHolder.sMediaPlayerSize = targetSize
                                ControlCenterConfigHolder.savePriorities(ctx)
                            }

                            val ov = overlayView
                            if (ov != null && rv != null) {
                                val startRect = RectF(ov.currentRect)
                                val targetRect = RectF(originLeft, originTop, originLeft + targetW, originTop + targetH)

                                if (finalState != initialState) {
                                    var animationEnded = false
                                    var layoutRefreshed = false

                                    fun tryFinish() {
                                        if (animationEnded && layoutRefreshed) {
                                            rv.overlay.clear()
                                            tileView.alpha = 1.0f
                                            overlayView = null
                                        }
                                    }

                                    ControlCenterConfigHolder.setMediaSize(ctx, targetSize, tileView) {
                                        layoutRefreshed = true
                                        // Layout pass with new media size complete: reset translations
                                        for (i in 0 until rv.childCount) {
                                            val child = rv.getChildAt(i) ?: continue
                                            child.translationX = 0f
                                            child.translationY = 0f
                                        }
                                        tryFinish()
                                    }

                                    val startProgress = ov.morphDrawable.mediaMorphProgress
                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val animProgress = lerp(startProgress, 1f, f)
                                            ov.updateMediaMorph(r, lr, fixedCornerRadius, initialState, finalState, animProgress, isMediaPlayingWithTrack)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                animationEnded = true
                                                tryFinish()
                                            }
                                        })
                                    }
                                    anim.start()
                                } else {
                                    for (i in 0 until rv.childCount) {
                                        val child = rv.getChildAt(i) ?: continue
                                        child.animate().translationX(0f).translationY(0f).setDuration(200).start()
                                    }

                                    val startProgress = ov.morphDrawable.mediaMorphProgress
                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val animProgress = lerp(startProgress, 0f, f)
                                            ov.updateMediaMorph(r, lr, fixedCornerRadius, initialState, candidateTargetState, animProgress, isMediaPlayingWithTrack)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                rv.overlay.clear()
                                                tileView.alpha = 1.0f
                                                overlayView = null
                                            }
                                        })
                                    }
                                    anim.start()
                                }
                            } else {
                                rv?.overlay?.clear()
                                tileView.alpha = 1.0f
                                overlayView = null
                            }
                        } else {
                            val finalState = if (candidateTargetState != -1) candidateTargetState else initialState
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                            val finalW = when (finalState) {
                                STATE_EXPANDED, STATE_LARGE -> twoColW
                                else -> colW
                            }
                            val finalH = when (finalState) {
                                STATE_VERTICAL, STATE_LARGE -> tallH
                                STATE_EXPANDED -> cardH
                                else -> if (initialState == STATE_NORMAL) iconFrameH else colW
                            }
                            val finalTargetLeft = if (finalState == STATE_NORMAL) iconFrameLeft else tileLeft
                            val finalTargetTop = if (finalState == STATE_NORMAL) iconFrameTop else tileTop
                            val finalRadius = if (finalState == STATE_NORMAL) (finalW / 2f) else stockCardRadius

                            if (finalState != initialState) {
                                // Update layout configuration immediately upon handle release!
                                when (finalState) {
                                    STATE_EXPANDED -> {
                                        ControlCenterConfigHolder.sExpandedTileSpecs.add(spec)
                                        ControlCenterConfigHolder.sLargeTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sCircularCardSpecs.remove(spec)
                                    }
                                    STATE_VERTICAL -> {
                                        ControlCenterConfigHolder.sVerticalTileSpecs.add(spec)
                                        ControlCenterConfigHolder.sExpandedTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sLargeTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sCircularCardSpecs.remove(spec)
                                    }
                                    STATE_LARGE -> {
                                        ControlCenterConfigHolder.sLargeTileSpecs.add(spec)
                                        ControlCenterConfigHolder.sExpandedTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sCircularCardSpecs.remove(spec)
                                    }
                                    STATE_NORMAL -> {
                                        ControlCenterConfigHolder.sExpandedTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                                        ControlCenterConfigHolder.sLargeTileSpecs.remove(spec)
                                        if ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) {
                                            ControlCenterConfigHolder.sCircularCardSpecs.add(spec)
                                        } else {
                                            ControlCenterConfigHolder.sCircularCardSpecs.remove(spec)
                                        }
                                    }
                                }
                                ControlCenterConfigHolder.savePriorities(ctx)
                                ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
                            }

                            val ov = overlayView
                            if (ov != null && rv != null) {
                                val startRect = RectF(ov.currentRect)
                                val targetRect = RectF(finalTargetLeft, finalTargetTop, finalTargetLeft + finalW, finalTargetTop + finalH)
                                val startRad = ov.currentCornerRadius
                                val startIconBox = RectF(ov.morphDrawable.iconRect)
                                val targetIconBox = when (finalState) {
                                    STATE_EXPANDED -> RectF(16f * density, (finalH - iconSize) / 2f, 16f * density + iconSize, (finalH + iconSize) / 2f)
                                    STATE_VERTICAL -> RectF((finalW - iconSize) / 2f, 16f * density, (finalW + iconSize) / 2f, 16f * density + iconSize)
                                    STATE_LARGE -> RectF((finalW - iconSize) / 2f, 24f * density, (finalW + iconSize) / 2f, 24f * density + iconSize)
                                    else -> RectF((finalW - iconSize) / 2f, (finalH - iconSize) / 2f, (finalW + iconSize) / 2f, (finalH + iconSize) / 2f)
                                }
                                val finalLayoutMode = when (finalState) {
                                    STATE_EXPANDED -> LAYOUT_MODE_HORIZONTAL
                                    STATE_VERTICAL -> LAYOUT_MODE_VERTICAL
                                    STATE_LARGE -> LAYOUT_MODE_LARGE
                                    else -> ov.layoutMode
                                }

                                if (finalState != initialState) {
                                    var animationEnded = false
                                    var layoutRefreshed = false

                                    fun tryFinish() {
                                        if (animationEnded && layoutRefreshed) {
                                            rv.overlay.clear()
                                            tileView.alpha = 1.0f
                                            overlayView = null
                                        }
                                    }

                                    ControlCenterConfigHolder.refreshRecyclerView(tileView) {
                                        layoutRefreshed = true
                                        // The layout pass with the new spans has completed!
                                        // Reset translations so views sit naturally at their new physical positions with ZERO visual movement!
                                        for (i in 0 until rv.childCount) {
                                            val child = rv.getChildAt(i) ?: continue
                                            child.translationX = 0f
                                            child.translationY = 0f
                                        }
                                        tryFinish()
                                    }

                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val rad = lerp(startRad, finalRadius, f)
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val lib = RectF(
                                                lerp(startIconBox.left, targetIconBox.left, f),
                                                lerp(startIconBox.top, targetIconBox.top, f),
                                                lerp(startIconBox.right, targetIconBox.right, f),
                                                lerp(startIconBox.bottom, targetIconBox.bottom, f)
                                            )
                                            ov.updateMorph(r, lr, rad, lib, false, finalLayoutMode)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                animationEnded = true
                                                tryFinish()
                                            }
                                        })
                                    }
                                    anim.start()
                                } else {
                                    // Returned to initial state: smoothly animate neighbours back to 0
                                    for (i in 0 until rv.childCount) {
                                        val child = rv.getChildAt(i) ?: continue
                                        child.animate().translationX(0f).translationY(0f).setDuration(200).start()
                                    }

                                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 200
                                        interpolator = OvershootInterpolator(1.05f)
                                        addUpdateListener { va ->
                                            val f = va.animatedFraction
                                            val r = RectF(
                                                lerp(startRect.left, targetRect.left, f),
                                                lerp(startRect.top, targetRect.top, f),
                                                lerp(startRect.right, targetRect.right, f),
                                                lerp(startRect.bottom, targetRect.bottom, f)
                                            )
                                            val rad = lerp(startRad, finalRadius, f)
                                            val lr = RectF(0f, 0f, r.width(), r.height())
                                            val lib = RectF(
                                                lerp(startIconBox.left, targetIconBox.left, f),
                                                lerp(startIconBox.top, targetIconBox.top, f),
                                                lerp(startIconBox.right, targetIconBox.right, f),
                                                lerp(startIconBox.bottom, targetIconBox.bottom, f)
                                            )
                                            ov.updateMorph(r, lr, rad, lib, false, finalLayoutMode)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                rv.overlay.clear()
                                                tileView.alpha = 1.0f
                                                overlayView = null
                                            }
                                        })
                                    }
                                    anim.start()
                                }
                            } else {
                                rv?.overlay?.clear()
                                tileView.alpha = 1.0f
                                overlayView = null
                            }
                        }
                    } else {
                        // Regular tap to toggle/cycle
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isSliderSpec) {
                            ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightnessSpec, tileView)
                        } else if (isMediaSpec) {
                            ControlCenterConfigHolder.cycleMediaPlayerSize(ctx, tileView)
                        } else {
                            ControlCenterConfigHolder.toggleTileSize(ctx, spec, tileView)
                        }
                    }

                    isDragging = false
                    candidateTargetState = -1
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                    requestDisallowAll(v, false)

                    if (isDragging) {
                        targetContainer?.overlay?.clear()
                        targetContainer?.let { rv ->
                            for (i in 0 until rv.childCount) {
                                val child = rv.getChildAt(i) ?: continue
                                child.animate().translationX(0f).translationY(0f).setDuration(180).start()
                            }
                        }
                        tileView.alpha = 1.0f
                        overlayView = null
                    }

                    isDragging = false
                    candidateTargetState = -1
                    return true
                }
            }
            return true
        }
    }

    class MediaVectorButtonDrawable(private val type: String, private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.left + w / 2f
            val cy = b.top + h / 2f
            val s = Math.min(w, h) * 0.40f

            path.reset()
            when (type) {
                "play" -> {
                    path.moveTo(cx - s * 0.5f, cy - s * 0.7f)
                    path.lineTo(cx + s * 0.7f, cy)
                    path.lineTo(cx - s * 0.5f, cy + s * 0.7f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                "prev" -> {
                    val offset = s * 0.45f
                    path.moveTo(cx - offset + s * 0.45f, cy - s * 0.6f)
                    path.lineTo(cx - offset - s * 0.45f, cy)
                    path.lineTo(cx - offset + s * 0.45f, cy + s * 0.6f)
                    path.close()
                    path.moveTo(cx + offset + s * 0.45f, cy - s * 0.6f)
                    path.lineTo(cx + offset - s * 0.45f, cy)
                    path.lineTo(cx + offset + s * 0.45f, cy + s * 0.6f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                "next" -> {
                    val offset = s * 0.45f
                    path.moveTo(cx - offset - s * 0.45f, cy - s * 0.6f)
                    path.lineTo(cx - offset + s * 0.45f, cy)
                    path.lineTo(cx - offset - s * 0.45f, cy + s * 0.6f)
                    path.close()
                    path.moveTo(cx + offset - s * 0.45f, cy - s * 0.6f)
                    path.lineTo(cx + offset + s * 0.45f, cy)
                    path.lineTo(cx + offset - s * 0.45f, cy + s * 0.6f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                "cast", "device" -> {
                    val strokePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f * density
                    }
                    val rect = RectF(cx - s * 0.7f, cy - s * 0.5f, cx + s * 0.7f, cy + s * 0.5f)
                    canvas.drawRoundRect(rect, 2f * density, 2f * density, strokePaint)
                    canvas.drawCircle(cx, cy, s * 0.2f, paint)
                }
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class DefaultCoverDrawable(private val density: Float) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.FILL
        }
        private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3FFFFFF")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            canvas.drawRect(b, bgPaint)
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = 4f * density
            canvas.drawCircle(cx - 5f * density, cy + 3f * density, r, notePaint)
            canvas.drawCircle(cx + 5f * density, cy, r, notePaint)
            canvas.drawRect(cx - 2f * density, cy - 8f * density, cx, cy + 3f * density, notePaint)
            canvas.drawRect(cx + 8f * density, cy - 11f * density, cx + 10f * density, cy, notePaint)
            canvas.drawRect(cx - 2f * density, cy - 11f * density, cx + 10f * density, cy - 7f * density, notePaint)
        }

        override fun setAlpha(alpha: Int) { bgPaint.alpha = alpha; notePaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { notePaint.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
