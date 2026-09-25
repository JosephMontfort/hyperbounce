package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator

/**
 * FluidTileResizeHandler provides real-time, ultra-fluid tile expansion and
 * immediate neighbour tile displacement following stock HyperOS rearrangement logic
 * as the user drags the resize handle.
 */
object FluidTileResizeHandler {

    private const val TAG = "FluidTileResizeHandler"

    const val STATE_NORMAL = 0
    const val STATE_EXPANDED = 1
    const val STATE_VERTICAL = 2
    const val STATE_LARGE = 3
    const val STATE_WIDE_1 = 4
    const val STATE_WIDE_2 = 5
    const val STATE_TALL_4 = 6

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

        private var downRawX = 0f
        private var downRawY = 0f
        private var isDragging = false
        private var initialState = STATE_NORMAL
        private var lastCandidateState = -1

        private var targetContainer: ViewGroup? = null
        private var activeViewHolder: Any? = null

        private var originWidth = 0f
        private var originHeight = 0f
        private var unitWidth1x1 = 0f
        private var unitHeight1x1 = 0f
        private var cardWidth2x1 = 0f
        private var cardHeight1x2 = 0f
        private var oneColW = 0f
        private var twoColW = 0f
        private var threeColW = 0f
        private var fourColW = 0f
        private var row1H = 0f
        private var row2H = 0f
        private var row4H = 0f

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

        private fun findViewHolder(view: View, rv: ViewGroup): Any? {
            try {
                val findMethod = rv.javaClass.getMethod("findContainingViewHolder", View::class.java)
                return findMethod.invoke(rv, view)
            } catch (t: Throwable) {}
            try {
                val getHolderMethod = rv.javaClass.getMethod("getChildViewHolder", View::class.java)
                return getHolderMethod.invoke(rv, view)
            } catch (t: Throwable) {}
            return null
        }

        private fun setIsRecyclable(viewHolder: Any?, recyclable: Boolean) {
            if (viewHolder == null) return
            try {
                val m = viewHolder.javaClass.getMethod("setIsRecyclable", Boolean::class.javaPrimitiveType)
                m.invoke(viewHolder, recyclable)
            } catch (t: Throwable) {}
        }

        private fun triggerStockDisplacement(rv: ViewGroup) {
            try {
                val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                val adp = getAdapterMethod.invoke(rv)
                if (adp != null) {
                    try {
                        val distMethod = adp.javaClass.getDeclaredMethod("distributeContent", Boolean::class.javaPrimitiveType)
                        distMethod.isAccessible = true
                        distMethod.invoke(adp, true)
                    } catch (t: Throwable) {}
                    val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                    notifyDataSetChangedMethod.invoke(adp)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "triggerStockDisplacement error", t)
            }
        }

        private fun applyInMemState(targetState: Int) {
            if (targetState == -1) return
            try {
                if (isSliderSpec) {
                    if (isBrightnessSpec) {
                        ControlCenterConfigHolder.sBrightnessSliderWidth = targetState.coerceIn(2, 4)
                    } else {
                        ControlCenterConfigHolder.sVolumeSliderWidth = targetState.coerceIn(2, 4)
                    }
                } else if (isMediaSpec) {
                    val targetMediaSize = when (targetState) {
                        STATE_EXPANDED -> "2x1"
                        STATE_VERTICAL -> "1x2"
                        STATE_LARGE -> "2x2"
                        STATE_WIDE_1 -> "4x1"
                        STATE_WIDE_2 -> "4x2"
                        STATE_TALL_4 -> "2x4"
                        else -> "2x2"
                    }
                    ControlCenterConfigHolder.sMediaPlayerSize = targetMediaSize
                } else {
                    when (targetState) {
                        STATE_EXPANDED -> {
                            ControlCenterConfigHolder.sExpandedTileSpecs.add(spec)
                            ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                            ControlCenterConfigHolder.sLargeTileSpecs.remove(spec)
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
                }
            } catch (t: Throwable) {
                Log.e(TAG, "applyInMemState error", t)
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
                    lastCandidateState = -1

                    initialState = when {
                        isSliderSpec -> {
                            val w = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                            w.coerceIn(2, 4)
                        }
                        isMediaSpec -> when (ControlCenterConfigHolder.sMediaPlayerSize) {
                            "2x1" -> STATE_EXPANDED
                            "1x2" -> STATE_VERTICAL
                            "2x2" -> STATE_LARGE
                            "4x1" -> STATE_WIDE_1
                            "4x2" -> STATE_WIDE_2
                            "2x4" -> STATE_TALL_4
                            else -> STATE_LARGE
                        }
                        ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_LARGE
                        ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_EXPANDED
                        ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_VERTICAL
                        ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) && !ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) -> STATE_EXPANDED
                        else -> STATE_NORMAL
                    }

                    lastCandidateState = initialState

                    val rv = findRecyclerView(tileView) ?: (tileView.rootView as? ViewGroup)
                    targetContainer = rv

                    if (rv != null) {
                        activeViewHolder = findViewHolder(tileView, rv)
                        setIsRecyclable(activeViewHolder, false)

                        val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                        val iconFrame = if (iconFrameId != 0) tileView.findViewById<View>(iconFrameId) else null
                        val visualView = if (initialState == STATE_NORMAL && iconFrame != null) iconFrame else tileView

                        originWidth = visualView.width.toFloat().coerceAtLeast(56f * density)
                        originHeight = visualView.height.toFloat().coerceAtLeast(56f * density)

                        val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
                        val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId).toFloat() else (71.5f * density)
                        val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
                        val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId).toFloat() else (157f * density)
                        val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
                        val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId).toFloat() else (10f * density)

                        val rvContentW = (rv.width - rv.paddingLeft - rv.paddingRight).toFloat()
                        val colW = if (rvContentW > 100f * density) {
                            (rvContentW - 3f * gap) / 4f
                        } else {
                            originWidth
                        }
                        val calcTwoColW = colW * 2f + gap

                        oneColW = colW
                        twoColW = calcTwoColW
                        threeColW = colW * 3f + 2f * gap
                        fourColW = if (rvContentW > 100f * density) rvContentW else (colW * 4f + 3f * gap)
                        row1H = cardH
                        row2H = tallH
                        row4H = 4f * cardH + 3f * gap

                        if (isSliderSpec) {
                            originWidth = when (initialState) {
                                3 -> threeColW
                                4 -> fourColW
                                else -> twoColW
                            }
                            originHeight = row1H
                        } else {
                            when (initialState) {
                                STATE_NORMAL -> {
                                    unitWidth1x1 = originWidth
                                    unitHeight1x1 = originHeight
                                    cardWidth2x1 = twoColW.coerceAtLeast(originWidth * 2f + gap)
                                    cardHeight1x2 = tallH
                                }
                                STATE_EXPANDED -> {
                                    cardWidth2x1 = originWidth
                                    unitHeight1x1 = originHeight
                                    unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                    cardHeight1x2 = tallH
                                }
                                STATE_VERTICAL -> {
                                    unitWidth1x1 = originWidth
                                    cardHeight1x2 = originHeight
                                    unitHeight1x1 = colW.coerceAtLeast(56f * density)
                                    cardWidth2x1 = twoColW.coerceAtLeast(originWidth * 2f + gap)
                                }
                                STATE_LARGE -> {
                                    cardWidth2x1 = originWidth
                                    cardHeight1x2 = originHeight
                                    unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                    unitHeight1x1 = colW.coerceAtLeast(56f * density)
                                }
                                STATE_WIDE_1, STATE_WIDE_2, STATE_TALL_4 -> {
                                    cardWidth2x1 = twoColW
                                    cardHeight1x2 = tallH
                                    unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                    unitHeight1x1 = colW.coerceAtLeast(56f * density)
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

                    if (dist > slop) {
                        if (!isDragging) {
                            isDragging = true
                            tileView.bringToFront()
                            tileView.elevation = 20f * density
                            tileView.translationZ = 10f * density
                        }
                    }

                    if (!isDragging) return true

                    var targetState = initialState

                    if (isSliderSpec) {
                        val rawW = (originWidth + dx).coerceIn(twoColW * 0.85f, fourColW * 1.05f)
                        targetState = when {
                            rawW > (threeColW + fourColW) / 2f -> 4
                            rawW > (twoColW + threeColW) / 2f -> 3
                            else -> 2
                        }
                        val curW = (originWidth + dx).coerceAtLeast(twoColW * 0.8f)
                        val sx = curW / originWidth
                        tileView.pivotX = 0f
                        tileView.pivotY = 0f
                        tileView.scaleX = sx
                        tileView.scaleY = 1f
                    } else if (isMediaSpec) {
                        val snapDistX = (oneColW + 10f * density) * 0.35f
                        val snapDistY = (row1H + 10f * density) * 0.35f

                        when (initialState) {
                            STATE_LARGE -> {
                                if (dx > snapDistX) {
                                    targetState = if (dy < -snapDistY * 0.6f) STATE_WIDE_1 else STATE_WIDE_2
                                } else if (dx < -snapDistX) {
                                    targetState = STATE_VERTICAL
                                } else if (dy > snapDistY) {
                                    targetState = STATE_TALL_4
                                } else if (dy < -snapDistY) {
                                    targetState = STATE_EXPANDED
                                }
                            }
                            STATE_EXPANDED -> {
                                if (dx > snapDistX) {
                                    targetState = if (dy > snapDistY * 0.6f) STATE_WIDE_2 else STATE_WIDE_1
                                } else if (dy > snapDistY) {
                                    targetState = if (dy > snapDistY * 2.0f) STATE_TALL_4 else STATE_LARGE
                                } else if (dx < -snapDistX && dy > snapDistY * 0.5f) {
                                    targetState = STATE_VERTICAL
                                }
                            }
                            STATE_VERTICAL -> {
                                if (dx > snapDistX) {
                                    if (dx > snapDistX * 2.0f) {
                                        targetState = if (dy < -snapDistY * 0.6f) STATE_WIDE_1 else STATE_WIDE_2
                                    } else {
                                        targetState = if (dy < -snapDistY * 0.6f) STATE_EXPANDED else STATE_LARGE
                                    }
                                } else if (dy > snapDistY) {
                                    targetState = STATE_TALL_4
                                } else if (dy < -snapDistY && dx > snapDistX * 0.5f) {
                                    targetState = STATE_EXPANDED
                                }
                            }
                            STATE_WIDE_1 -> {
                                if (dx < -snapDistX && dy > snapDistY * 1.2f) {
                                    targetState = STATE_TALL_4
                                } else if (dy > snapDistY) {
                                    targetState = if (dx < -snapDistX * 1.5f) STATE_LARGE else STATE_WIDE_2
                                } else if (dx < -snapDistX) {
                                    targetState = if (dy > snapDistY * 0.5f) STATE_LARGE else STATE_EXPANDED
                                }
                            }
                            STATE_WIDE_2 -> {
                                if (dy < -snapDistY) {
                                    targetState = if (dx < -snapDistX) STATE_EXPANDED else STATE_WIDE_1
                                } else if (dx < -snapDistX) {
                                    if (dy > snapDistY) {
                                        targetState = STATE_TALL_4
                                    } else if (dy < -snapDistY) {
                                        targetState = STATE_EXPANDED
                                    } else if (dx < -snapDistX * 2.2f) {
                                        targetState = STATE_VERTICAL
                                    } else {
                                        targetState = STATE_LARGE
                                    }
                                } else if (dy > snapDistY && dx < -snapDistX * 0.5f) {
                                    targetState = STATE_TALL_4
                                }
                            }
                            STATE_TALL_4 -> {
                                if (dx > snapDistX && dy < -snapDistY * 1.2f) {
                                    targetState = STATE_WIDE_1
                                } else if (dy < -snapDistY) {
                                    if (dy < -snapDistY * 2.0f) {
                                        targetState = if (dx > snapDistX) STATE_WIDE_1 else STATE_EXPANDED
                                    } else {
                                        if (dx > snapDistX) {
                                            targetState = STATE_WIDE_2
                                        } else if (dx < -snapDistX) {
                                            targetState = STATE_VERTICAL
                                        } else {
                                            targetState = STATE_LARGE
                                        }
                                    }
                                } else if (dx > snapDistX && dy < -snapDistY * 0.5f) {
                                    targetState = STATE_WIDE_2
                                } else if (dx < -snapDistX && dy < -snapDistY * 0.5f) {
                                    targetState = STATE_VERTICAL
                                } else if (dx > snapDistX) {
                                    targetState = STATE_WIDE_2
                                } else if (dx < -snapDistX) {
                                    targetState = STATE_VERTICAL
                                }
                            }
                            else -> {
                                targetState = STATE_LARGE
                            }
                        }

                        val curW = (originWidth + dx).coerceAtLeast(oneColW * 0.7f)
                        val curH = (originHeight + dy).coerceAtLeast(row1H * 0.7f)
                        tileView.pivotX = 0f
                        tileView.pivotY = 0f
                        tileView.scaleX = curW / originWidth
                        tileView.scaleY = curH / originHeight
                    } else {
                        val deltaW = (cardWidth2x1 - unitWidth1x1).coerceAtLeast(40f * density)
                        val deltaH = (cardHeight1x2 - unitHeight1x1).coerceAtLeast(40f * density)
                        val snapThreshold = 0.22f
                        val snapDistX = deltaW * snapThreshold
                        val snapDistY = deltaH * snapThreshold

                        when (initialState) {
                            STATE_NORMAL -> {
                                val isDiagonal = dx > snapDistX * 0.65f && dy > snapDistY * 0.65f
                                if (isDiagonal) {
                                    if (dx >= snapDistX && dy >= snapDistY) {
                                        targetState = STATE_LARGE
                                    }
                                } else if (dx >= dy) {
                                    if (dx >= snapDistX) {
                                        targetState = STATE_EXPANDED
                                    }
                                } else {
                                    if (dy >= snapDistY) {
                                        targetState = STATE_VERTICAL
                                    }
                                }
                            }
                            STATE_EXPANDED -> {
                                val isDownLeft = -dx > snapDistX * 0.55f && dy > snapDistY * 0.55f
                                if (isDownLeft) {
                                    if (-dx >= snapDistX && dy >= snapDistY) {
                                        targetState = STATE_VERTICAL
                                    }
                                } else if (dy > snapDistY * 0.5f && dy > -dx) {
                                    if (dy >= snapDistY) {
                                        targetState = STATE_LARGE
                                    }
                                } else if (dx < 0) {
                                    if (-dx >= snapDistX) {
                                        targetState = STATE_NORMAL
                                    }
                                }
                            }
                            STATE_VERTICAL -> {
                                val isUpRight = dx > snapDistX * 0.55f && -dy > snapDistY * 0.55f
                                if (isUpRight) {
                                    if (dx >= snapDistX && -dy >= snapDistY) {
                                        targetState = STATE_EXPANDED
                                    }
                                } else if (dx > snapDistX * 0.5f && dx > -dy) {
                                    if (dx >= snapDistX) {
                                        targetState = STATE_LARGE
                                    }
                                } else if (dy < 0) {
                                    if (-dy >= snapDistY) {
                                        targetState = STATE_NORMAL
                                    }
                                }
                            }
                            STATE_LARGE -> {
                                val isUpLeft = -dx > snapDistX * 0.55f && -dy > snapDistY * 0.55f
                                if (isUpLeft) {
                                    if (-dx >= snapDistX && -dy >= snapDistY) {
                                        targetState = STATE_NORMAL
                                    }
                                } else if (-dy >= -dx && -dy > snapDistY * 0.4f) {
                                    if (-dy >= snapDistY) {
                                        targetState = STATE_EXPANDED
                                    }
                                } else if (-dx > 0) {
                                    if (-dx >= snapDistX) {
                                        targetState = STATE_VERTICAL
                                    }
                                }
                            }
                        }

                        val curW = (originWidth + dx).coerceAtLeast(30f * density)
                        val curH = (originHeight + dy).coerceAtLeast(30f * density)
                        tileView.pivotX = 0f
                        tileView.pivotY = 0f
                        tileView.scaleX = curW / originWidth
                        tileView.scaleY = curH / originHeight
                    }

                    // Dynamic Neighbour Displacement:
                    // If candidate target state switched (crossing snap threshold), rearrange neighbours immediately
                    if (targetState != lastCandidateState) {
                        lastCandidateState = targetState
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        applyInMemState(targetState)
                        targetContainer?.let { rv ->
                            triggerStockDisplacement(rv)
                        }
                    }

                    return true
                }

                MotionEvent.ACTION_UP -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                    requestDisallowAll(v, false)

                    if (isDragging) {
                        if (lastCandidateState != -1 && lastCandidateState != initialState) {
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            applyInMemState(lastCandidateState)
                            ControlCenterConfigHolder.savePriorities(ctx)
                            targetContainer?.let { rv ->
                                triggerStockDisplacement(rv)
                            }
                        } else {
                            // Revert to initialState if dragged back
                            applyInMemState(initialState)
                            targetContainer?.let { rv ->
                                triggerStockDisplacement(rv)
                            }
                        }

                        // Settle spring animation for tileView
                        tileView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationZ(0f)
                            .setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.15f))
                            .withEndAction {
                                tileView.elevation = 0f
                                setIsRecyclable(activeViewHolder, true)
                                activeViewHolder = null
                            }
                            .start()
                    } else {
                        // Regular tap to toggle/cycle
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isSliderSpec) {
                            ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightnessSpec, tileView)
                        } else {
                            ControlCenterConfigHolder.toggleTileSize(ctx, spec, tileView)
                        }
                        setIsRecyclable(activeViewHolder, true)
                        activeViewHolder = null
                    }

                    isDragging = false
                    lastCandidateState = -1
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                    requestDisallowAll(v, false)

                    if (isDragging) {
                        applyInMemState(initialState)
                        targetContainer?.let { rv ->
                            triggerStockDisplacement(rv)
                        }

                        tileView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationZ(0f)
                            .setDuration(200)
                            .withEndAction {
                                tileView.elevation = 0f
                                setIsRecyclable(activeViewHolder, true)
                                activeViewHolder = null
                            }
                            .start()
                    } else {
                        setIsRecyclable(activeViewHolder, true)
                        activeViewHolder = null
                    }

                    isDragging = false
                    lastCandidateState = -1
                    return true
                }
            }
            return true
        }
    }
}
