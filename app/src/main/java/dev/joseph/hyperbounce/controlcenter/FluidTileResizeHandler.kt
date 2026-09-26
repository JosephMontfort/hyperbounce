package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        val isMediaSpec = "media".equals(spec, ignoreCase = true)

        if (ControlCenterConfigHolder.config.fluidResizeEnabled && !isMediaSpec) {
            handleView.setOnTouchListener(FluidTileResizeTouchListener(ctx, spec, tileView, density))
        } else {
            handleView.setOnTouchListener(TileResizeDragTouchListener(ctx, spec, tileView, density))
        }
    }

    /**
     * Custom morphing overlay drawable that renders the expanding or shrinking tile.
     * Preserves exact corner radius without stretching, keeps icons crisp and unstretched,
     * reveals full-scale stock text by clip mask as the tile expands, and draws the corner resize handle.
     */
    class FluidTileMorphOverlayDrawable(private val density: Float) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            color = Color.parseColor("#26FFFFFF")
        }
        private val sliderProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15.5f * density
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3FFFFFF")
            textSize = 13.5f * density
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val handleDrawable = CurvedResizeHandleDrawable(density)
        private val clipPath = Path()

        val currentRect = RectF()
        var cornerRadius = 20f * density
        var isSnapping = false
        var isSlider = false
        var layoutMode = LAYOUT_MODE_HORIZONTAL

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
            iconDrawable = icon
            titleText = title
            subtitleText = subtitle
            bgPaint.color = bgColor
            isSlider = isSliderItem
            customBgDrawable = customBg
            sliderProgressFraction = sliderProgress
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

            // If slider, draw background track, progress fill (clipped to corner radius), border, icon, and handle
            if (isSlider) {
                canvas.save()
                clipPath.reset()
                clipPath.addRoundRect(currentRect, cornerRadius, cornerRadius, Path.Direction.CW)
                canvas.clipPath(clipPath)

                // 1. Slider track background
                canvas.drawRect(currentRect, bgPaint)

                // 2. Slider progress fill (white pill clipped by the track's rounded boundary)
                if (sliderProgressFraction > 0.01f) {
                    val progressW = currentRect.width() * sliderProgressFraction
                    val progressRect = RectF(currentRect.left, currentRect.top, currentRect.left + progressW, currentRect.bottom)
                    canvas.drawRect(progressRect, sliderProgressPaint)
                }
                canvas.restore()

                // 3. Subtle border
                canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, borderPaint)

                // 4. Unstretched icon
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

                // 5. Curved resize handle at bottom-right corner
                val handleSize = 34f * density
                val hLeft = currentRect.right - handleSize
                val hTop = currentRect.bottom - handleSize
                handleDrawable.setBounds(hLeft.toInt(), hTop.toInt(), currentRect.right.toInt(), currentRect.bottom.toInt())
                handleDrawable.draw(canvas)
                return
            }

            // 1. Draw smooth background matching native cards/tiles with strictly preserved corner radius
            if (customBgDrawable != null && customBgDrawable is GradientDrawable) {
                val gd = customBgDrawable as GradientDrawable
                gd.cornerRadius = cornerRadius
                gd.setBounds(
                    currentRect.left.toInt(),
                    currentRect.top.toInt(),
                    currentRect.right.toInt(),
                    currentRect.bottom.toInt()
                )
                gd.draw(canvas)
            } else {
                canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, bgPaint)
            }

            // 2. Draw subtle border (Preserves clean stock look without blue frame)
            canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, borderPaint)

            // 3. Draw Unstretched Icon
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

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bgPaint.colorFilter = colorFilter
        }

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

        private var downRawX = 0f
        private var downRawY = 0f
        private var isDragging = false
        private var initialState = STATE_NORMAL
        private var initialCols = 2
        private var candidateTargetState = -1

        private var targetContainer: ViewGroup? = null
        private var morphDrawable: FluidTileMorphOverlayDrawable? = null

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
        private var gap = 0f

        private var fixedCornerRadius = 20f * density
        private var stockCardRadius = 20f * density
        private var sliderRadius = 16f * density

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
                        sliderRadius = 16f * density

                        if (isSliderSpec) {
                            val w = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                            initialCols = w.coerceIn(2, 4)
                            candidateTargetState = initialCols

                            originLeft = tileLeft
                            originTop = tileTop
                            originWidth = when (initialCols) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }
                            originHeight = cardH

                            fixedCornerRadius = sliderRadius

                            val rawIcon = findIcon(tileView)
                            val iconClone = rawIcon?.constantState?.newDrawable()?.mutate() ?: rawIcon

                            val seekBar = findSeekBar(tileView)
                            val sliderProgress = if (seekBar != null && seekBar.max > 0) {
                                seekBar.progress.toFloat() / seekBar.max.toFloat()
                            } else 0.5f

                            morphDrawable = FluidTileMorphOverlayDrawable(density).apply {
                                setup(
                                    icon = iconClone,
                                    title = null,
                                    subtitle = null,
                                    bgColor = stockDisabledColor,
                                    isSliderItem = true,
                                    customBg = null,
                                    sliderProgress = sliderProgress
                                )
                                cornerRadius = fixedCornerRadius
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

                            val sourceBg = if (initialState == STATE_NORMAL && iconFrame?.background != null) iconFrame.background else tileView.background
                            val currentBgColor = extractColorFromDrawable(sourceBg)
                            val resolvedBgColor = when {
                                currentBgColor != null && currentBgColor != 0 -> currentBgColor
                                isActive -> stockEnabledColor
                                isInsideContainer -> 0x66000000
                                else -> stockDisabledColor
                            }

                            val nativeBgDrawable = if (initialState != STATE_NORMAL && tileView.background != null) {
                                tileView.background?.constantState?.newDrawable()?.mutate()
                            } else null

                            morphDrawable = FluidTileMorphOverlayDrawable(density).apply {
                                setup(
                                    icon = iconClone,
                                    title = title,
                                    subtitle = subtitle,
                                    bgColor = resolvedBgColor,
                                    isSliderItem = false,
                                    customBg = if (nativeBgDrawable is GradientDrawable) nativeBgDrawable else null,
                                    sliderProgress = 0f
                                )
                                cornerRadius = fixedCornerRadius

                                // Exact stock typography preservation
                                if (titleTv != null && titleTv.textSize >= 14f * density) {
                                    titlePaint.textSize = titleTv.textSize
                                    titlePaint.typeface = titleTv.typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                    titlePaint.color = titleTv.currentTextColor
                                } else {
                                    titlePaint.textSize = 15.5f * density
                                    titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                    titlePaint.color = Color.WHITE
                                }
                                if (subTv != null && subTv.textSize >= 12.5f * density) {
                                    subtitlePaint.textSize = subTv.textSize
                                    subtitlePaint.typeface = subTv.typeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
                                    subtitlePaint.color = subTv.currentTextColor
                                } else {
                                    subtitlePaint.textSize = 13.5f * density
                                    subtitlePaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                    subtitlePaint.color = Color.parseColor("#B3FFFFFF")
                                }
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
                        morphDrawable?.let { md ->
                            targetContainer?.overlay?.add(md)
                        }
                        tileView.alpha = 0f
                    }

                    if (!isDragging) return true

                    val iconSize = 26f * density

                    var curW = originWidth
                    var curH = originHeight
                    // Corner radius is strictly preserved at fixedCornerRadius throughout the drag
                    val curRadius = fixedCornerRadius
                    var layoutMode = LAYOUT_MODE_HORIZONTAL
                    val iconBox = RectF()

                    if (isSliderSpec) {
                        // Slider resizing: Height strictly locked to 1 row, corner radius strictly preserved
                        curH = cardH
                        curW = (originWidth + dx).coerceIn(twoColW * 0.85f, fourColW * 1.05f)

                        val candidateCols = when {
                            curW > (threeColW + fourColW) / 2f -> 4
                            curW > (twoColW + threeColW) / 2f -> 3
                            else -> 2
                        }

                        val iconX = originLeft + 16f * density
                        val iconY = originTop + (cardH - iconSize) / 2f
                        iconBox.set(iconX, iconY, iconX + iconSize, iconY + iconSize)

                        val drawRect = RectF(originLeft, originTop, originLeft + curW, originTop + curH)
                        val isSnapping = (candidateCols != initialCols)
                        morphDrawable?.updateMorph(drawRect, curRadius, iconBox, isSnapping, LAYOUT_MODE_HORIZONTAL)
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
                    morphDrawable?.updateMorph(drawRect, curRadius, iconBox, isSnapping, layoutMode)
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
                        val md = morphDrawable
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

                            if (md != null && rv != null) {
                                val startRect = RectF(md.currentRect)
                                val targetRect = RectF(tileLeft, tileTop, tileLeft + finalW, tileTop + cardH)

                                if (finalCols != initialCols) {
                                    var animationEnded = false
                                    var layoutRefreshed = false

                                    fun tryFinish() {
                                        if (animationEnded && layoutRefreshed) {
                                            rv.overlay.clear()
                                            tileView.alpha = 1.0f
                                        }
                                    }

                                    ControlCenterConfigHolder.refreshRecyclerView(tileView) {
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
                                            md.updateMorph(r, sliderRadius, md.iconRect, false, LAYOUT_MODE_HORIZONTAL)
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
                                            md.updateMorph(r, sliderRadius, md.iconRect, false, LAYOUT_MODE_HORIZONTAL)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                rv.overlay.clear()
                                                tileView.alpha = 1.0f
                                            }
                                        })
                                    }
                                    anim.start()
                                }
                            } else {
                                rv?.overlay?.clear()
                                tileView.alpha = 1.0f
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

                            if (md != null && rv != null) {
                                val startRect = RectF(md.currentRect)
                                val targetRect = RectF(finalTargetLeft, finalTargetTop, finalTargetLeft + finalW, finalTargetTop + finalH)
                                val startRad = md.cornerRadius

                                if (finalState != initialState) {
                                    var animationEnded = false
                                    var layoutRefreshed = false

                                    fun tryFinish() {
                                        if (animationEnded && layoutRefreshed) {
                                            rv.overlay.clear()
                                            tileView.alpha = 1.0f
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
                                            md.updateMorph(r, rad, md.iconRect, false, md.layoutMode)
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
                                            md.updateMorph(r, rad, md.iconRect, false, md.layoutMode)
                                            rv.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                rv.overlay.clear()
                                                tileView.alpha = 1.0f
                                            }
                                        })
                                    }
                                    anim.start()
                                }
                            } else {
                                rv?.overlay?.clear()
                                tileView.alpha = 1.0f
                            }
                        }
                    } else {
                        // Regular tap to toggle/cycle
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isSliderSpec) {
                            ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightnessSpec, tileView)
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
                    }

                    isDragging = false
                    candidateTargetState = -1
                    return true
                }
            }
            return true
        }
    }
}
