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
        var mediaProgressFraction: Float = 0f

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
            title: String?,
            artist: String?,
            bgColor: Int,
            customBg: Drawable? = null,
            progress: Float = 0f,
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
            titleText = title
            subtitleText = artist
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

            // If media player, render album art, controls, and title/artist with strict corner radius and unstretched elements
            if (isMedia) {
                val w = currentRect.width()
                val h = currentRect.height()

                val isTall4 = h > 220f * density
                val isWide2 = w > 230f * density && h > 105f * density
                val isWide1 = w > 230f * density && !isWide2
                val isVert = w < 95f * density
                val isExpanded2x1 = h < 95f * density && !isWide1

                // 1. Draw Unstretched Album Art
                val coverRect = RectF()
                var coverRad = 12f * density

                if (isWide2) {
                    val coverSize = (h - 28f * density).coerceAtLeast(48f * density)
                    coverRad = 18f * density
                    coverRect.set(
                        currentRect.left + 14f * density,
                        currentRect.top + 14f * density,
                        currentRect.left + 14f * density + coverSize,
                        currentRect.top + 14f * density + coverSize
                    )
                } else if (isTall4) {
                    val coverSize = (w - 28f * density).coerceAtLeast(48f * density)
                    coverRad = 18f * density
                    coverRect.set(
                        currentRect.left + 14f * density,
                        currentRect.top + 14f * density,
                        currentRect.left + 14f * density + coverSize,
                        currentRect.top + 14f * density + coverSize
                    )
                } else if (isVert) {
                    val coverSize = (w - 24f * density).coerceIn(40f * density, 52f * density)
                    coverRect.set(
                        currentRect.centerX() - coverSize / 2f,
                        currentRect.top + 14f * density,
                        currentRect.centerX() + coverSize / 2f,
                        currentRect.top + 14f * density + coverSize
                    )
                } else {
                    val coverSize = 48f * density
                    val coverTop = if (isExpanded2x1 || isWide1) {
                        currentRect.top + (h - coverSize) / 2f
                    } else {
                        currentRect.top + 14f * density
                    }
                    coverRect.set(
                        currentRect.left + 14f * density,
                        coverTop,
                        currentRect.left + 14f * density + coverSize,
                        coverTop + coverSize
                    )
                }

                coverDrawable?.let { cov ->
                    canvas.save()
                    clipPath.reset()
                    clipPath.addRoundRect(coverRect, coverRad, coverRad, Path.Direction.CW)
                    canvas.clipPath(clipPath)
                    cov.setBounds(
                        coverRect.left.toInt(),
                        coverRect.top.toInt(),
                        coverRect.right.toInt(),
                        coverRect.bottom.toInt()
                    )
                    cov.draw(canvas)
                    canvas.restore()
                }

                // 2. Draw Playback Controls
                val btnSize = 30f * density
                val playSize = 36f * density
                val spacing = 8f * density

                if (isVert) {
                    val remHeight = currentRect.bottom - coverRect.bottom
                    val centerY = coverRect.bottom + remHeight / 2f
                    val playTop = centerY - playSize / 2f
                    val prevTop = playTop - spacing - btnSize
                    val nextTop = playTop + playSize + spacing

                    val prevL = currentRect.centerX() - btnSize / 2f
                    val playL = currentRect.centerX() - playSize / 2f
                    val nextL = currentRect.centerX() - btnSize / 2f

                    prevDrawable?.setBounds(prevL.toInt(), prevTop.toInt(), (prevL + btnSize).toInt(), (prevTop + btnSize).toInt())
                    prevDrawable?.draw(canvas)

                    playDrawable?.setBounds(playL.toInt(), playTop.toInt(), (playL + playSize).toInt(), (playTop + playSize).toInt())
                    playDrawable?.draw(canvas)

                    nextDrawable?.setBounds(nextL.toInt(), nextTop.toInt(), (nextL + btnSize).toInt(), (nextTop + btnSize).toInt())
                    nextDrawable?.draw(canvas)
                } else {
                    val ctrlCenterY = when {
                        isExpanded2x1 || isWide1 -> currentRect.centerY()
                        isWide2 -> currentRect.bottom - 28f * density
                        isTall4 -> currentRect.bottom - 36f * density
                        else -> currentRect.bottom - 28f * density
                    }

                    val ctrlRight = currentRect.right - 14f * density
                    val nextR = ctrlRight
                    val nextL = nextR - btnSize
                    val playR = nextL - spacing
                    val playL = playR - playSize
                    val prevR = playL - spacing
                    val prevL = prevR - btnSize

                    val btnTop = ctrlCenterY - btnSize / 2f
                    val playTop = ctrlCenterY - playSize / 2f

                    prevDrawable?.setBounds(prevL.toInt(), btnTop.toInt(), prevR.toInt(), (btnTop + btnSize).toInt())
                    prevDrawable?.draw(canvas)

                    playDrawable?.setBounds(playL.toInt(), playTop.toInt(), playR.toInt(), (playTop + playSize).toInt())
                    playDrawable?.draw(canvas)

                    nextDrawable?.setBounds(nextL.toInt(), btnTop.toInt(), nextR.toInt(), (btnTop + btnSize).toInt())
                    nextDrawable?.draw(canvas)
                }

                // 3. Draw Song Title & Artist (revealed via clipping and ellipsizing)
                if (!titleText.isNullOrEmpty() && !isExpanded2x1 && !isVert) {
                    val fontMetrics = titlePaint.fontMetrics
                    val titleH = fontMetrics.descent - fontMetrics.ascent
                    val gapBetween = 4f * density

                    val textStartX: Float
                    val textTopY: Float
                    val clipRight: Float

                    when {
                        isWide2 -> {
                            textStartX = coverRect.right + 16f * density
                            textTopY = currentRect.top + 24f * density
                            clipRight = currentRect.right - 14f * density
                        }
                        isTall4 -> {
                            textStartX = currentRect.left + 16f * density
                            textTopY = coverRect.bottom + 16f * density
                            clipRight = currentRect.right - 14f * density
                        }
                        isWide1 -> {
                            textStartX = coverRect.right + 14f * density
                            val totalTextH = titleH + (if (!subtitleText.isNullOrEmpty()) (gapBetween + subtitlePaint.fontMetrics.descent - subtitlePaint.fontMetrics.ascent) else 0f)
                            textTopY = currentRect.centerY() - totalTextH / 2f
                            clipRight = currentRect.right - (btnSize * 2f + playSize + spacing * 2f + 28f * density)
                        }
                        else -> { // 2x2
                            textStartX = coverRect.right + 14f * density
                            textTopY = coverRect.top + 4f * density
                            clipRight = currentRect.right - 14f * density
                        }
                    }

                    if (clipRight > textStartX) {
                        canvas.save()
                        canvas.clipRect(textStartX, currentRect.top, clipRight, currentRect.bottom)
                        val availW = clipRight - textStartX
                        val displayTitle = if (availW > 10f) {
                            TextUtils.ellipsize(titleText, titlePaint, availW, TextUtils.TruncateAt.END).toString()
                        } else titleText!!

                        val titleBaseline = textTopY - fontMetrics.ascent
                        canvas.drawText(displayTitle, textStartX, titleBaseline, titlePaint)

                        if (!subtitleText.isNullOrEmpty()) {
                            val displayArtist = if (availW > 10f) {
                                TextUtils.ellipsize(subtitleText, subtitlePaint, availW, TextUtils.TruncateAt.END).toString()
                            } else subtitleText!!

                            val subBaseline = titleBaseline + fontMetrics.descent + gapBetween - subtitlePaint.fontMetrics.ascent
                            canvas.drawText(displayArtist, textStartX, subBaseline, subtitlePaint)
                        }
                        canvas.restore()
                    }
                }

                // 4. Curved resize handle at bottom-right corner
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
        private var fourRowsH = 0f
        private var gap = 0f

        private var fixedCornerRadius = 20f * density
        private var stockCardRadius = 20f * density

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
                        fourRowsH = 4f * cardH + 3f * gap

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
                            initialState = when (ControlCenterConfigHolder.sMediaPlayerSize) {
                                "2x1" -> MEDIA_2X1
                                "1x2" -> MEDIA_1X2
                                "4x1" -> MEDIA_4X1
                                "4x2" -> MEDIA_4X2
                                "2x4" -> MEDIA_2X4
                                else -> MEDIA_2X2
                            }
                            candidateTargetState = initialState

                            originLeft = tileLeft
                            originTop = tileTop
                            originWidth = if (tileView.width > 0) tileView.width.toFloat() else when (initialState) {
                                MEDIA_1X2 -> colW
                                MEDIA_4X1, MEDIA_4X2 -> fourColW
                                else -> twoColW
                            }
                            originHeight = if (tileView.height > 0) tileView.height.toFloat() else when (initialState) {
                                MEDIA_2X1, MEDIA_4X1 -> cardH
                                MEDIA_2X4 -> fourRowsH
                                else -> tallH
                            }

                            fixedCornerRadius = stockCardRadius

                            // Sample media components: album art, title, artist, controls
                            var mediaCover: Drawable? = null
                            var mediaPlay: Drawable? = null
                            var mediaPrev: Drawable? = null
                            var mediaNext: Drawable? = null
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
                                    title = mediaTitle,
                                    artist = mediaArtist,
                                    bgColor = resolvedBgColor,
                                    customBg = nativeBgDrawable,
                                    progress = 0.35f,
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
                                val initLocalIconBox = RectF(0f, 0f, 0f, 0f)
                                updateMorph(initRect, initLocalRect, fixedCornerRadius, initLocalIconBox, false, LAYOUT_MODE_HORIZONTAL)
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
                        val snapDistX = (colW + gap) * 0.30f
                        val snapDistY = cardH * 0.30f

                        var targetState = initialState
                        when (initialState) {
                            MEDIA_2X2 -> {
                                when {
                                    dx > snapDistX -> targetState = if (dy < -snapDistY * 0.6f) MEDIA_4X1 else MEDIA_4X2
                                    dx < -snapDistX -> targetState = MEDIA_1X2
                                    dy > snapDistY -> targetState = MEDIA_2X4
                                    dy < -snapDistY -> targetState = MEDIA_2X1
                                }
                            }
                            MEDIA_2X1 -> {
                                when {
                                    dx > snapDistX -> targetState = if (dy > snapDistY * 0.6f) MEDIA_4X2 else MEDIA_4X1
                                    dy > snapDistY -> targetState = if (dy > snapDistY * 2.0f) MEDIA_2X4 else MEDIA_2X2
                                    dx < -snapDistX && dy > snapDistY * 0.5f -> targetState = MEDIA_1X2
                                }
                            }
                            MEDIA_1X2 -> {
                                when {
                                    dx > snapDistX -> {
                                        targetState = if (dx > snapDistX * 2.0f) {
                                            if (dy < -snapDistY * 0.6f) MEDIA_4X1 else MEDIA_4X2
                                        } else {
                                            if (dy < -snapDistY * 0.6f) MEDIA_2X1 else MEDIA_2X2
                                        }
                                    }
                                    dy > snapDistY -> targetState = MEDIA_2X4
                                    dy < -snapDistY && dx > snapDistX * 0.5f -> targetState = MEDIA_2X1
                                }
                            }
                            MEDIA_4X1 -> {
                                when {
                                    dx < -snapDistX && dy > snapDistY * 1.2f -> targetState = MEDIA_2X4
                                    dy > snapDistY -> targetState = if (dx < -snapDistX * 1.5f) MEDIA_2X2 else MEDIA_4X2
                                    dx < -snapDistX -> targetState = if (dy > snapDistY * 0.5f) MEDIA_2X2 else MEDIA_2X1
                                }
                            }
                            MEDIA_4X2 -> {
                                when {
                                    dy < -snapDistY -> targetState = if (dx < -snapDistX) MEDIA_2X1 else MEDIA_4X1
                                    dx < -snapDistX -> {
                                        targetState = if (dy > snapDistY) {
                                            MEDIA_2X4
                                        } else if (dy < -snapDistY) {
                                            MEDIA_2X1
                                        } else if (dx < -snapDistX * 2.0f) {
                                            MEDIA_1X2
                                        } else {
                                            MEDIA_2X2
                                        }
                                    }
                                    dy > snapDistY && dx < -snapDistX * 0.5f -> targetState = MEDIA_2X4
                                }
                            }
                            MEDIA_2X4 -> {
                                when {
                                    dx > snapDistX && dy < -snapDistY * 1.2f -> targetState = MEDIA_4X1
                                    dy < -snapDistY -> {
                                        if (dy < -snapDistY * 2.0f) {
                                            targetState = if (dx > snapDistX) MEDIA_4X1 else MEDIA_2X1
                                        } else {
                                            targetState = if (dx > snapDistX) MEDIA_4X2 else if (dx < -snapDistX) MEDIA_1X2 else MEDIA_2X2
                                        }
                                    }
                                    dx > snapDistX -> targetState = MEDIA_4X2
                                    dx < -snapDistX -> targetState = MEDIA_1X2
                                }
                            }
                        }

                        val (targetW, targetH) = when (targetState) {
                            MEDIA_2X1 -> Pair(twoColW, cardH)
                            MEDIA_1X2 -> Pair(colW, tallH)
                            MEDIA_4X1 -> Pair(fourColW, cardH)
                            MEDIA_4X2 -> Pair(fourColW, tallH)
                            MEDIA_2X4 -> Pair(twoColW, fourRowsH)
                            else -> Pair(twoColW, tallH)
                        }

                        curW = (originWidth + dx).coerceIn(colW * 0.85f, fourColW * 1.05f)
                        curH = (originHeight + dy).coerceIn(cardH * 0.85f, fourRowsH * 1.05f)
                        if (targetState != initialState) {
                            curW = lerp(curW, targetW, 0.45f)
                            curH = lerp(curH, targetH, 0.45f)
                        }

                        val drawRect = RectF(originLeft, originTop, originLeft + curW, originTop + curH)
                        val isSnapping = (targetState != initialState)
                        val localRect = RectF(0f, 0f, curW, curH)
                        val localIconBox = RectF(0f, 0f, 0f, 0f)
                        overlayView?.updateMorph(drawRect, localRect, fixedCornerRadius, localIconBox, isSnapping, LAYOUT_MODE_HORIZONTAL)
                        targetContainer?.invalidate()

                        if (targetState != candidateTargetState) {
                            candidateTargetState = targetState
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val targetSpans = when (targetState) {
                                MEDIA_2X1 -> Pair(2, 1)
                                MEDIA_1X2 -> Pair(1, 2)
                                MEDIA_4X1 -> Pair(4, 1)
                                MEDIA_4X2 -> Pair(4, 2)
                                MEDIA_2X4 -> Pair(2, 4)
                                else -> Pair(2, 2)
                            }
                            targetContainer?.let { rv ->
                                updateNeighbourDisplacements(rv, targetSpans.first, targetSpans.second)
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

                            val targetSize = when (finalState) {
                                MEDIA_2X1 -> "2x1"
                                MEDIA_1X2 -> "1x2"
                                MEDIA_4X1 -> "4x1"
                                MEDIA_4X2 -> "4x2"
                                MEDIA_2X4 -> "2x4"
                                else -> "2x2"
                            }
                            val (targetW, targetH) = when (finalState) {
                                MEDIA_2X1 -> Pair(twoColW, cardH)
                                MEDIA_1X2 -> Pair(colW, tallH)
                                MEDIA_4X1 -> Pair(fourColW, cardH)
                                MEDIA_4X2 -> Pair(fourColW, tallH)
                                MEDIA_2X4 -> Pair(twoColW, fourRowsH)
                                else -> Pair(twoColW, tallH)
                            }

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
                                            val lib = RectF(0f, 0f, 0f, 0f)
                                            ov.updateMorph(r, lr, fixedCornerRadius, lib, false, ov.layoutMode)
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
                                            val lib = RectF(0f, 0f, 0f, 0f)
                                            ov.updateMorph(r, lr, fixedCornerRadius, lib, false, ov.layoutMode)
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
