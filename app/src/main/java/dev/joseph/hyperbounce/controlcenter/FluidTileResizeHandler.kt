package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView

/**
 * FluidTileResizeHandler provides semi-fluid, directional tile and slider expansion/shrinking
 * with strict corner radius preservation, unstretched 1:1 icon rendering, full-scale stock text reveal,
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
            strokeWidth = 2.0f * density
            color = Color.parseColor("#4DFFFFFF")
        }
        private val snapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.parseColor("#FF0088FF") // HyperOS blue snap highlight
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15.5f * density
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3FFFFFF")
            textSize = 12f * density
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val handleDrawable = CurvedResizeHandleDrawable(density)

        val currentRect = RectF()
        var cornerRadius = 26f * density
        var isSnapping = false
        var isSlider = false
        var layoutMode = LAYOUT_MODE_HORIZONTAL

        var iconDrawable: Drawable? = null
        var titleText: String? = null
        var subtitleText: String? = null
        val iconRect = RectF()

        fun setup(
            icon: Drawable?,
            title: String?,
            subtitle: String?,
            bgColor: Int,
            isSliderItem: Boolean
        ) {
            iconDrawable = icon
            titleText = title
            subtitleText = subtitle
            bgPaint.color = bgColor
            isSlider = isSliderItem
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

            // 1. Draw smooth rounded rectangle background (Corner radius is strictly preserved, never stretched!)
            canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Draw border
            if (isSnapping) {
                canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, snapBorderPaint)
            } else {
                canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, borderPaint)
            }

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

            // If slider, draw handle and exit
            if (isSlider) {
                val handleSize = 34f * density
                val hLeft = currentRect.right - handleSize
                val hTop = currentRect.bottom - handleSize
                handleDrawable.setBounds(hLeft.toInt(), hTop.toInt(), currentRect.right.toInt(), currentRect.bottom.toInt())
                handleDrawable.draw(canvas)
                return
            }

            // 4. Draw Unstretched Revealed Text (Reveals as tile expands without downscaling)
            if (!titleText.isNullOrEmpty()) {
                val fontMetrics = titlePaint.fontMetrics
                val titleH = fontMetrics.descent - fontMetrics.ascent
                val subH = if (!subtitleText.isNullOrEmpty()) {
                    val sm = subtitlePaint.fontMetrics
                    sm.descent - sm.ascent
                } else 0f
                val gapBetween = 2.5f * density
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

                        val clipBottom = currentRect.bottom - 12f * density
                        if (clipBottom > textStartY) {
                            canvas.save()
                            canvas.clipRect(currentRect.left + 8f * density, textStartY, currentRect.right - 8f * density, clipBottom)
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

                        val clipBottom = currentRect.bottom - 12f * density
                        if (clipBottom > textStartY) {
                            canvas.save()
                            canvas.clipRect(currentRect.left + 8f * density, textStartY, currentRect.right - 8f * density, clipBottom)
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

        /**
         * Real-time 2D Bin Packing displacement logic matching stock HyperOS rearrangement.
         */
        private fun updateNeighbourDisplacements(
            rv: ViewGroup,
            targetSpanW: Int,
            targetSpanH: Int
        ) {
            val SPAN_COUNT = 4
            val MAX_ROWS = 100
            val grid = Array(MAX_ROWS) { BooleanArray(SPAN_COUNT) { false } }

            fun canFit(r: Int, c: Int, w: Int, h: Int): Boolean {
                if (c + w > SPAN_COUNT) return false
                for (row in r until (r + h)) {
                    if (row >= MAX_ROWS) return false
                    for (col in c until (c + w)) {
                        if (grid[row][col]) return false
                    }
                }
                return true
            }

            fun markOccupied(r: Int, c: Int, w: Int, h: Int) {
                for (row in r until (r + h)) {
                    if (row < MAX_ROWS) {
                        for (col in c until (c + w)) {
                            grid[row][col] = true
                        }
                    }
                }
            }

            fun findSlot(w: Int, h: Int): Pair<Int, Int> {
                var r = 0
                while (r < MAX_ROWS) {
                    for (c in 0..(SPAN_COUNT - w)) {
                        if (canFit(r, c, w, h)) {
                            return Pair(r, c)
                        }
                    }
                    r++
                }
                return Pair(0, 0)
            }

            val items = mutableListOf<View>()
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i) ?: continue
                if (child.visibility != View.GONE) {
                    items.add(child)
                }
            }

            // Sort in visual grid order (top then left)
            items.sortWith(Comparator { a, b ->
                val rowA = ((a.top - rv.paddingTop + gap / 2f) / (cardH + gap)).toInt()
                val rowB = ((b.top - rv.paddingTop + gap / 2f) / (cardH + gap)).toInt()
                if (rowA != rowB) rowA.compareTo(rowB) else a.left.compareTo(b.left)
            })

            for (child in items) {
                val isTarget = (child === tileView)
                val w = if (isTarget) targetSpanW else ((child.width + gap / 2f) / (colW + gap)).toInt().coerceIn(1, SPAN_COUNT)
                val h = if (isTarget) targetSpanH else ((child.height + gap / 2f) / (cardH + gap)).toInt().coerceIn(1, 4)

                val (assignedRow, assignedCol) = findSlot(w, h)
                markOccupied(assignedRow, assignedCol, w, h)

                if (!isTarget) {
                    val targetLeft = rv.paddingLeft + assignedCol * (colW + gap)
                    val targetTop = rv.paddingTop + assignedRow * (cardH + gap)
                    val tx = targetLeft - child.left
                    val ty = targetTop - child.top

                    child.animate()
                        .translationX(tx)
                        .translationY(ty)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
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

                    if (rv != null) {
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

                        if (isSliderSpec) {
                            val w = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                            initialCols = w.coerceIn(2, 4)
                            candidateTargetState = initialCols

                            val locTile = IntArray(2)
                            val locRv = IntArray(2)
                            tileView.getLocationInWindow(locTile)
                            rv.getLocationInWindow(locRv)
                            originLeft = (locTile[0] - locRv[0]).toFloat()
                            originTop = (locTile[1] - locRv[1]).toFloat()
                            originWidth = when (initialCols) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }
                            originHeight = cardH

                            val rawIcon = findIcon(tileView)
                            val iconClone = rawIcon?.constantState?.newDrawable()?.mutate() ?: rawIcon

                            morphDrawable = FluidTileMorphOverlayDrawable(density).apply {
                                setup(iconClone, null, null, 0x33FFFFFF, true)
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

                            val visualView = if (initialState == STATE_NORMAL && iconFrame != null) iconFrame else tileView
                            val locTile = IntArray(2)
                            val locRv = IntArray(2)
                            visualView.getLocationInWindow(locTile)
                            rv.getLocationInWindow(locRv)
                            originLeft = (locTile[0] - locRv[0]).toFloat()
                            originTop = (locTile[1] - locRv[1]).toFloat()

                            if (initialState == STATE_NORMAL) {
                                originWidth = (iconFrame?.width?.toFloat() ?: (56f * density)).coerceAtLeast(56f * density)
                                originHeight = (iconFrame?.height?.toFloat() ?: (56f * density)).coerceAtLeast(56f * density)
                            } else {
                                originWidth = tileView.width.toFloat().coerceAtLeast(colW)
                                originHeight = tileView.height.toFloat().coerceAtLeast(cardH)
                            }

                            // Extract visual components for morph overlay
                            val rawIcon = findIcon(iconFrame) ?: findIcon(tileView)
                            val iconClone = rawIcon?.constantState?.newDrawable()?.mutate() ?: rawIcon

                            val textViews = mutableListOf<TextView>()
                            inspectTextViews(tileView, textViews)
                            val titleTv = textViews.getOrNull(0)
                            val subTv = textViews.getOrNull(1)

                            val title = titleTv?.text?.toString() ?: spec.replaceFirstChar { it.uppercase() }
                            val subtitle = subTv?.text?.toString() ?: "Off"

                            val isActive = tileView.isActivated || tileView.isSelected || (iconFrame?.isActivated == true) || (iconFrame?.isSelected == true)
                            val isInsideContainer = ControlCenterHook.isChildOfContainer(tileView)
                            val bgColor = when {
                                isActive -> 0xFF0088FF.toInt()
                                isInsideContainer -> 0x66000000
                                else -> 0x33FFFFFF
                            }

                            morphDrawable = FluidTileMorphOverlayDrawable(density).apply {
                                setup(iconClone, title, subtitle, bgColor, false)
                                if (titleTv != null) {
                                    titlePaint.textSize = titleTv.textSize.coerceAtLeast(15f * density)
                                    titlePaint.typeface = titleTv.typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                    titlePaint.color = titleTv.currentTextColor
                                }
                                if (subTv != null) {
                                    subtitlePaint.textSize = subTv.textSize.coerceAtLeast(12f * density)
                                    subtitlePaint.typeface = subTv.typeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
                                    subtitlePaint.color = subTv.currentTextColor
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
                        morphDrawable?.let { md ->
                            targetContainer?.overlay?.add(md)
                        }
                        tileView.alpha = 0f
                    }

                    if (!isDragging) return true

                    val cardRadius = 26f * density
                    val circRadius = originWidth.coerceAtMost(colW) / 2f
                    val iconSize = 26f * density

                    var curW = originWidth
                    var curH = originHeight
                    var curRadius = cardRadius
                    var layoutMode = LAYOUT_MODE_HORIZONTAL
                    val iconBox = RectF()

                    if (isSliderSpec) {
                        // Slider resizing: Horizontal strictly locked in height!
                        curH = cardH
                        curW = (originWidth + dx).coerceIn(twoColW * 0.85f, fourColW * 1.05f)
                        curRadius = cardRadius

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
                    val deltaW = (twoColW - originWidth).coerceAtLeast(30f * density)
                    val deltaH = (tallH - originHeight).coerceAtLeast(30f * density)
                    val snapDistX = deltaW * 0.28f
                    val snapDistY = deltaH * 0.28f

                    var targetState = initialState

                    when (initialState) {
                        STATE_NORMAL -> {
                            val isDiagonal = dx > snapDistX * 0.5f && dy > snapDistY * 0.5f
                            val isPureHorizontal = dx > 0 && dy < dx * 0.45f
                            val isPureVertical = dy > 0 && dx < dy * 0.45f

                            when {
                                isDiagonal -> {
                                    targetState = if (dx >= snapDistX && dy >= snapDistY) STATE_LARGE else STATE_NORMAL
                                    val progX = (dx / deltaW).coerceIn(0f, 1f)
                                    val progY = (dy / deltaH).coerceIn(0f, 1f)
                                    curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                    curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                    curRadius = cardRadius
                                    layoutMode = LAYOUT_MODE_LARGE

                                    // In 2x2: Icon glides towards upper-center
                                    val targetIconX = originLeft + (curW - iconSize) / 2f
                                    val targetIconY = originTop + 24f * density
                                    val ix = lerp((originLeft + (originWidth - iconSize) / 2f), targetIconX, Math.max(progX, progY))
                                    val iy = lerp((originTop + (originHeight - iconSize) / 2f), targetIconY, Math.max(progX, progY))
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                isPureHorizontal -> {
                                    // Height is LOCKED! Only width stretches!
                                    targetState = if (dx >= snapDistX) STATE_EXPANDED else STATE_NORMAL
                                    val progress = (dx / deltaW).coerceIn(0f, 1f)
                                    curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                    curH = cardH
                                    curRadius = lerp(circRadius, cardRadius, progress)
                                    layoutMode = LAYOUT_MODE_HORIZONTAL

                                    val ix = originLeft + lerp((originWidth - iconSize) / 2f, 16f * density, progress)
                                    val iy = originTop + (curH - iconSize) / 2f
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                isPureVertical -> {
                                    // Width is LOCKED! Only height stretches!
                                    targetState = if (dy >= snapDistY) STATE_VERTICAL else STATE_NORMAL
                                    val progress = (dy / deltaH).coerceIn(0f, 1f)
                                    curW = colW
                                    curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                    curRadius = lerp(circRadius, cardRadius, progress)
                                    layoutMode = LAYOUT_MODE_VERTICAL

                                    val ix = originLeft + (curW - iconSize) / 2f
                                    val iy = originTop + lerp((originHeight - iconSize) / 2f, 16f * density, progress)
                                    iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                }
                                else -> {
                                    // Fallback directional preference
                                    if (dx >= dy) {
                                        targetState = if (dx >= snapDistX) STATE_EXPANDED else STATE_NORMAL
                                        curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                        curH = cardH
                                        curRadius = cardRadius
                                        layoutMode = LAYOUT_MODE_HORIZONTAL
                                        val ix = originLeft + 16f * density
                                        val iy = originTop + (curH - iconSize) / 2f
                                        iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                    } else {
                                        targetState = if (dy >= snapDistY) STATE_VERTICAL else STATE_NORMAL
                                        curW = colW
                                        curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                        curRadius = cardRadius
                                        layoutMode = LAYOUT_MODE_VERTICAL
                                        val ix = originLeft + (curW - iconSize) / 2f
                                        val iy = originTop + 16f * density
                                        iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                                    }
                                }
                            }
                        }

                        STATE_EXPANDED -> {
                            val shrinkDeltaW = (originWidth - colW).coerceAtLeast(30f * density)
                            val snapShrinkX = shrinkDeltaW * 0.28f

                            if (dx < 0 && dy < -dx * 0.45f) {
                                // Pure horizontal shrinking to circular!
                                targetState = if (-dx >= snapShrinkX) STATE_NORMAL else STATE_EXPANDED
                                val shrinkProgress = (-dx / shrinkDeltaW).coerceIn(0f, 1f)
                                curW = (originWidth + dx).coerceIn(colW * 0.85f, originWidth)
                                curH = cardH
                                curRadius = lerp(cardRadius, circRadius, shrinkProgress)
                                layoutMode = LAYOUT_MODE_HORIZONTAL

                                val ix = originLeft + lerp(16f * density, (curW - iconSize) / 2f, shrinkProgress)
                                val iy = originTop + (curH - iconSize) / 2f
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else if (dy > 0) {
                                // Expanding vertically towards 2x2!
                                targetState = if (dy >= snapDistY) STATE_LARGE else STATE_EXPANDED
                                curW = originWidth
                                curH = (originHeight + dy).coerceIn(originHeight, tallH * 1.05f)
                                curRadius = cardRadius
                                layoutMode = LAYOUT_MODE_LARGE

                                val ix = originLeft + (curW - iconSize) / 2f
                                val iy = originTop + 24f * density
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else {
                                curW = originWidth
                                curH = originHeight
                                curRadius = cardRadius
                                layoutMode = LAYOUT_MODE_HORIZONTAL
                                val ix = originLeft + 16f * density
                                val iy = originTop + (curH - iconSize) / 2f
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            }
                        }

                        STATE_VERTICAL -> {
                            val shrinkDeltaH = (originHeight - cardH).coerceAtLeast(30f * density)
                            val snapShrinkY = shrinkDeltaH * 0.28f

                            if (dy < 0 && dx < -dy * 0.45f) {
                                // Pure vertical shrinking to circular!
                                targetState = if (-dy >= snapShrinkY) STATE_NORMAL else STATE_VERTICAL
                                val shrinkProgress = (-dy / shrinkDeltaH).coerceIn(0f, 1f)
                                curW = colW
                                curH = (originHeight + dy).coerceIn(colW * 0.85f, originHeight)
                                curRadius = lerp(cardRadius, circRadius, shrinkProgress)
                                layoutMode = LAYOUT_MODE_VERTICAL

                                val ix = originLeft + (curW - iconSize) / 2f
                                val iy = originTop + lerp(16f * density, (curH - iconSize) / 2f, shrinkProgress)
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else if (dx > 0) {
                                // Expanding horizontally towards 2x2!
                                targetState = if (dx >= snapDistX) STATE_LARGE else STATE_VERTICAL
                                curW = (originWidth + dx).coerceIn(originWidth, twoColW * 1.05f)
                                curH = originHeight
                                curRadius = cardRadius
                                layoutMode = LAYOUT_MODE_LARGE

                                val ix = originLeft + (curW - iconSize) / 2f
                                val iy = originTop + 24f * density
                                iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                            } else {
                                curW = originWidth
                                curH = originHeight
                                curRadius = cardRadius
                                layoutMode = LAYOUT_MODE_VERTICAL
                                val ix = originLeft + (curW - iconSize) / 2f
                                val iy = originTop + 16f * density
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
                            curRadius = cardRadius
                            layoutMode = if (targetState == STATE_VERTICAL) LAYOUT_MODE_VERTICAL else if (targetState == STATE_EXPANDED) LAYOUT_MODE_HORIZONTAL else LAYOUT_MODE_LARGE
                            val ix = originLeft + (curW - iconSize) / 2f
                            val iy = originTop + 24f * density
                            iconBox.set(ix, iy, ix + iconSize, iy + iconSize)
                        }
                    }

                    // Update morph overlay
                    val drawRect = RectF(originLeft, originTop, originLeft + curW, originTop + curH)
                    val isSnapping = (targetState != initialState && targetState != -1)
                    morphDrawable?.updateMorph(drawRect, curRadius, iconBox, isSnapping, layoutMode)
                    targetContainer?.invalidate()

                    // Live neighbour displacement using exact 2D bin packing
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

                            val finalW = when (finalCols) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }

                            if (md != null && rv != null) {
                                val startRect = RectF(md.currentRect)
                                val targetRect = RectF(originLeft, originTop, originLeft + finalW, originTop + cardH)
                                val cardRadius = 26f * density

                                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 240
                                    interpolator = OvershootInterpolator(1.1f)
                                    addUpdateListener { va ->
                                        val f = va.animatedFraction
                                        val r = RectF(
                                            lerp(startRect.left, targetRect.left, f),
                                            lerp(startRect.top, targetRect.top, f),
                                            lerp(startRect.right, targetRect.right, f),
                                            lerp(startRect.bottom, targetRect.bottom, f)
                                        )
                                        md.updateMorph(r, cardRadius, md.iconRect, false, LAYOUT_MODE_HORIZONTAL)
                                        rv.invalidate()
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            for (i in 0 until rv.childCount) {
                                                val child = rv.getChildAt(i) ?: continue
                                                child.translationX = 0f
                                                child.translationY = 0f
                                            }
                                            rv.overlay.remove(md)
                                            tileView.alpha = 1.0f

                                            if (isBrightnessSpec) {
                                                ControlCenterConfigHolder.sBrightnessSliderWidth = finalCols
                                            } else {
                                                ControlCenterConfigHolder.sVolumeSliderWidth = finalCols
                                            }
                                            ControlCenterConfigHolder.savePriorities(ctx)
                                            ControlCenterConfigHolder.refreshRecyclerView(tileView)
                                        }
                                    })
                                }
                                anim.start()
                            } else {
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
                                else -> if (initialState == STATE_NORMAL) originHeight else colW
                            }
                            val finalRadius = if (finalState == STATE_NORMAL) finalW / 2f else (26f * density)

                            if (md != null && rv != null) {
                                val startRect = RectF(md.currentRect)
                                val targetRect = RectF(originLeft, originTop, originLeft + finalW, originTop + finalH)
                                val startRad = md.cornerRadius

                                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 240
                                    interpolator = OvershootInterpolator(1.1f)
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
                                            for (i in 0 until rv.childCount) {
                                                val child = rv.getChildAt(i) ?: continue
                                                child.translationX = 0f
                                                child.translationY = 0f
                                            }
                                            rv.overlay.remove(md)
                                            tileView.alpha = 1.0f

                                            if (finalState != initialState) {
                                                when (finalState) {
                                                    STATE_EXPANDED -> ControlCenterConfigHolder.setTileToExpanded(ctx, spec, tileView)
                                                    STATE_VERTICAL -> ControlCenterConfigHolder.setTileToVertical(ctx, spec, tileView)
                                                    STATE_LARGE -> ControlCenterConfigHolder.setTileToLarge(ctx, spec, tileView)
                                                    STATE_NORMAL -> ControlCenterConfigHolder.setTileToNormal(ctx, spec, tileView)
                                                }
                                            } else {
                                                ControlCenterConfigHolder.refreshRecyclerView(tileView)
                                            }
                                        }
                                    })
                                }
                                anim.start()
                            } else {
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
                        morphDrawable?.let { md ->
                            targetContainer?.overlay?.remove(md)
                        }
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
