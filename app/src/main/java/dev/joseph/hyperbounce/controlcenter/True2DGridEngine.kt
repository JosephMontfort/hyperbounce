package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * True 2D Grid Layout Engine for HyperOS Control Center.
 *
 * Eliminates tile vertical stretching next to 2x2 or 1x2 components.
 * Computes 2D bin-packing coordinates (col, row, spanW, spanH) for all active
 * items, measures each view to its own natural height (71.5dp for 1-row,
 * 157dp for 2-row), and coordinates layoutChunk row stepping without row skipping.
 */
object True2DGridEngine {
    private const val TAG = "HyperBounce_True2D"
    private const val SPAN_COUNT = 4
    private const val MAX_GRID_ROWS = 200

    data class ItemLayout(
        val position: Int,
        val col: Int,
        val row: Int,
        val spanW: Int,
        val spanH: Int,
        val isHeader: Boolean,
        val isSystemItem: Boolean = false
    )

    class AdapterGridData(
        val items: Array<ItemLayout?>,
        val maxRows: Int,
        val unitRowHeight: Int,
        val itemsCount: Int = 0
    ) {
        fun getItemRow(pos: Int, adapter: Any? = null): Int {
            if (pos in items.indices && items[pos] != null) {
                return items[pos]!!.row
            }
            return (pos / SPAN_COUNT).coerceIn(0, MAX_GRID_ROWS - 1)
        }

        fun getItemCol(pos: Int, adapter: Any? = null): Int {
            if (pos in items.indices && items[pos] != null) {
                return items[pos]!!.col
            }
            val targetAdapter = adapter ?: getActiveAdapter()
            if (targetAdapter != null) {
                try {
                    val getItemViewTypeM = targetAdapter.javaClass.getMethod("getItemViewType", Int::class.javaPrimitiveType)
                    val vt = getItemViewTypeM.invoke(targetAdapter, pos) as Int
                    if (isFullWidthSystemItem(null, vt)) return 0
                } catch (t: Throwable) {}
            }
            return (pos % SPAN_COUNT).coerceIn(0, SPAN_COUNT - 1)
        }

        fun getItemSpanW(pos: Int, adapter: Any? = null): Int {
            if (pos in items.indices && items[pos] != null) {
                return items[pos]!!.spanW
            }
            val targetAdapter = adapter ?: getActiveAdapter()
            if (targetAdapter != null) {
                try {
                    val getItemViewTypeM = targetAdapter.javaClass.getMethod("getItemViewType", Int::class.javaPrimitiveType)
                    val vt = getItemViewTypeM.invoke(targetAdapter, pos) as Int
                    if (isFullWidthSystemItem(null, vt)) return SPAN_COUNT
                } catch (t: Throwable) {}
            }
            return 1
        }

        fun getItemSpanH(pos: Int): Int {
            if (pos in items.indices && items[pos] != null) {
                return items[pos]!!.spanH
            }
            return 1
        }

        fun isSystemItem(pos: Int, adapter: Any? = null): Boolean {
            if (pos in items.indices && items[pos] != null) {
                return items[pos]!!.isSystemItem
            }
            val targetAdapter = adapter ?: getActiveAdapter()
            if (targetAdapter != null) {
                try {
                    val getItemViewTypeM = targetAdapter.javaClass.getMethod("getItemViewType", Int::class.javaPrimitiveType)
                    val vt = getItemViewTypeM.invoke(targetAdapter, pos) as Int
                    return isFullWidthSystemItem(null, vt)
                } catch (t: Throwable) {}
            }
            return false
        }
    }

    private val adapterGrids = ConcurrentHashMap<Any, AdapterGridData>()
    @Volatile private var sLastCalculatedGrid: AdapterGridData? = null

    fun isFullWidthSystemItem(item: Any?, vt: Int): Boolean {
        if (vt == 3348 || vt == 334833) {
            return true
        }
        if (item != null) {
            val className = item.javaClass.name
            if (className.contains("EditButton") ||
                className.contains("WordlessMode")
            ) {
                return true
            }
        }
        return false
    }

    fun invalidate(adapter: Any?) {
        if (adapter != null) {
            adapterGrids.remove(adapter)
        }
        sLastCalculatedGrid = null
    }

    fun invalidateAll() {
        adapterGrids.clear()
        sLastCalculatedGrid = null
    }

    fun getUnitRowHeight(context: Context?): Int {
        if (context != null) {
            try {
                val resId = context.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", context.packageName)
                if (resId != 0) {
                    val h = context.resources.getDimensionPixelSize(resId)
                    if (h > 0) return h
                }
                return (85.5f * context.resources.displayMetrics.density + 0.5f).toInt()
            } catch (t: Throwable) {}
        }
        return 214
    }

    fun getGridData(adapter: Any?, context: Context?): AdapterGridData {
        if (adapter == null) {
            return sLastCalculatedGrid ?: calculateLayout(null, context)
        }
        val currentItemCount = try {
            (adapter.javaClass.getMethod("getItemCount").invoke(adapter) as? Int) ?: -1
        } catch (t: Throwable) { -1 }

        val cached = adapterGrids[adapter]
        if (cached != null && (currentItemCount < 0 || currentItemCount == cached.itemsCount)) {
            return cached
        }
        val computed = calculateLayout(adapter, context)
        adapterGrids[adapter] = computed
        sLastCalculatedGrid = computed
        return computed
    }

    fun isSystemItemForActiveAdapter(pos: Int): Boolean {
        val adapter = getActiveAdapter()
        val data = getGridData(adapter, ControlCenterConfigHolder.sLastKnownContext)
        return data.isSystemItem(pos, adapter)
    }

    fun getItemRowForActiveAdapter(pos: Int): Int {
        val adapter = getActiveAdapter()
        val data = getGridData(adapter, ControlCenterConfigHolder.sLastKnownContext)
        return data.getItemRow(pos, adapter)
    }

    fun getItemColForActiveAdapter(pos: Int): Int {
        val adapter = getActiveAdapter()
        val data = getGridData(adapter, ControlCenterConfigHolder.sLastKnownContext)
        return data.getItemCol(pos, adapter)
    }

    fun getItemSpanWForActiveAdapter(pos: Int): Int {
        val adapter = getActiveAdapter()
        val data = getGridData(adapter, ControlCenterConfigHolder.sLastKnownContext)
        return data.getItemSpanW(pos, adapter)
    }

    fun getItemSpanHForActiveAdapter(pos: Int): Int {
        val adapter = getActiveAdapter()
        val data = getGridData(adapter, ControlCenterConfigHolder.sLastKnownContext)
        return data.getItemSpanH(pos)
    }

    private fun getActiveAdapter(): Any? {
        val rv = ControlCenterConfigHolder.sRecyclerView?.get()
        if (rv != null) {
            try {
                val getAdapterM = rv.javaClass.getMethod("getAdapter")
                val ad = getAdapterM.invoke(rv)
                if (ad != null) return ad
            } catch (t: Throwable) {}
        }
        return ControlCenterConfigHolder.sMainPanelAdapter
            ?: ControlCenterFullUnlock.sRightAdapter
            ?: ControlCenterFullUnlock.sLeftAdapter
    }

    /**
     * Computes the 2D bin-packing layout for all items currently in adapter.contentMap.
     */
    fun calculateLayout(adapter: Any?, context: Context?): AdapterGridData {
        val unitRowHeight = getUnitRowHeight(context)
        val flatList = ArrayList<Any>()

        if (adapter != null) {
            try {
                val contentMapField = adapter.javaClass.getDeclaredField("contentMap")
                contentMapField.isAccessible = true
                val map = contentMapField.get(adapter) as? Map<*, *>
                if (map != null) {
                    for (entry in map.values) {
                        if (entry is List<*>) {
                            for (item in entry) {
                                if (item != null) flatList.add(item)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read contentMap for 2D layout calculation", t)
            }
        }

        val itemCount = flatList.size
        if (itemCount == 0) {
            val emptyItems = arrayOfNulls<ItemLayout>(64)
            val fallback = AdapterGridData(emptyItems, 16, unitRowHeight, 0)
            sLastCalculatedGrid = fallback
            return fallback
        }

        val itemsArray = arrayOfNulls<ItemLayout>(itemCount + 32)
        val grid = Array(MAX_GRID_ROWS) { BooleanArray(SPAN_COUNT) { false } }

        fun canFit(r: Int, c: Int, w: Int, h: Int): Boolean {
            if (c + w > SPAN_COUNT) return false
            for (row in r until (r + h)) {
                if (row >= MAX_GRID_ROWS) return false
                for (col in c until (c + w)) {
                    if (grid[row][col]) return false
                }
            }
            return true
        }

        fun markOccupied(r: Int, c: Int, w: Int, h: Int) {
            for (row in r until (r + h)) {
                if (row < MAX_GRID_ROWS) {
                    for (col in c until (c + w)) {
                        grid[row][col] = true
                    }
                }
            }
        }

        fun findSlot(w: Int, h: Int, minRow: Int): Pair<Int, Int> {
            if (w >= SPAN_COUNT) {
                var r = minRow
                while (r < MAX_GRID_ROWS) {
                    var allClear = true
                    for (c in 0 until SPAN_COUNT) {
                        if (grid[r][c]) {
                            allClear = false
                            break
                        }
                    }
                    if (allClear) return Pair(r, 0)
                    r++
                }
                return Pair(minRow, 0)
            }

            var r = minRow
            while (r < MAX_GRID_ROWS) {
                for (c in 0..(SPAN_COUNT - w)) {
                    if (canFit(r, c, w, h)) {
                        return Pair(r, c)
                    }
                }
                r++
            }
            return Pair(minRow, 0)
        }

        var maxStartRow = 0
        var highestOccupiedRow = 0

        for (p in 0 until itemCount) {
            val item = flatList[p]
            val vt = try {
                val getTypeMethod = item.javaClass.getMethod("getType")
                getTypeMethod.invoke(item) as Int
            } catch (t: Throwable) { -1 }

            val isSystem = isFullWidthSystemItem(item, vt)
            val spec = if (!isSystem) ControlCenterConfigHolder.getRecordSpec(item) else null
            val spans = if (isSystem) {
                Pair(SPAN_COUNT, 1)
            } else {
                ControlCenterFullUnlock.getItemSpans(item, vt, spec)
            }

            val spanW = spans.first.coerceIn(1, SPAN_COUNT)
            val spanH = spans.second.coerceIn(1, 6)
            val isHeader = (vt == 432337 || (p == 0 && spanW == SPAN_COUNT) || isSystem)

            val (assignedRow, assignedCol) = findSlot(spanW, spanH, maxStartRow)
            markOccupied(assignedRow, assignedCol, spanW, spanH)
            if (isSystem) {
                maxStartRow = assignedRow + spanH
            } else {
                maxStartRow = assignedRow
            }

            val endRow = assignedRow + spanH
            if (endRow > highestOccupiedRow) {
                highestOccupiedRow = endRow
            }

            itemsArray[p] = ItemLayout(
                position = p,
                col = assignedCol,
                row = assignedRow,
                spanW = spanW,
                spanH = spanH,
                isHeader = isHeader,
                isSystemItem = isSystem
            )
        }

        val gridData = AdapterGridData(itemsArray, highestOccupiedRow, unitRowHeight, itemCount)
        sLastCalculatedGrid = gridData
        return gridData
    }

    // --- Reflection Cache for LayoutChunk Hook -------------------------------------------

    private object ReflectionCache {
        @Volatile var initialized = false

        var layoutStateCurrentPositionField: Field? = null
        var layoutStateOffsetField: Field? = null
        var layoutStateItemDirectionField: Field? = null
        var layoutStateLayoutDirectionField: Field? = null
        var layoutStateScrapListField: Field? = null
        var layoutStateNextMethod: Method? = null

        var layoutChunkResultConsumedField: Field? = null
        var layoutChunkResultFinishedField: Field? = null
        var layoutChunkResultIgnoreConsumedField: Field? = null
        var layoutChunkResultFocusableField: Field? = null

        var glmCachedBordersField: Field? = null
        var glmDecorInsetsField: Field? = null
        var glmOrientationField: Field? = null
        var lmRecyclerViewField: Field? = null

        var addViewMethod: Method? = null
        var addViewAtMethod: Method? = null
        var addDisappearingViewMethod: Method? = null
        var addDisappearingViewAtMethod: Method? = null
        var layoutDecoratedWithMarginsMethod: Method? = null
        var calculateItemDecorationsMethod: Method? = null
        var getPaddingLeftMethod: Method? = null
        var getPaddingTopMethod: Method? = null
        var getWidthMethod: Method? = null
        var getHeightMethod: Method? = null
        var stateItemCountMethod: Method? = null

        var calculateItemBordersMethod: Method? = null

        fun init(glmClass: Class<*>, classLoader: ClassLoader): Boolean {
            if (initialized) return true
            try {
                val lmClass = glmClass.superclass ?: return false
                val rvlClass = Class.forName("androidx.recyclerview.widget.RecyclerView\$LayoutManager", false, classLoader)
                val layoutStateClass = Class.forName("androidx.recyclerview.widget.LinearLayoutManager\$LayoutState", false, classLoader)
                val chunkResultClass = Class.forName("androidx.recyclerview.widget.LinearLayoutManager\$LayoutChunkResult", false, classLoader)
                val recyclerClass = Class.forName("androidx.recyclerview.widget.RecyclerView\$Recycler", false, classLoader)
                val stateClass = Class.forName("androidx.recyclerview.widget.RecyclerView\$State", false, classLoader)

                layoutStateCurrentPositionField = layoutStateClass.getDeclaredField("mCurrentPosition").apply { isAccessible = true }
                layoutStateOffsetField = layoutStateClass.getDeclaredField("mOffset").apply { isAccessible = true }
                layoutStateItemDirectionField = layoutStateClass.getDeclaredField("mItemDirection").apply { isAccessible = true }
                layoutStateLayoutDirectionField = layoutStateClass.getDeclaredField("mLayoutDirection").apply { isAccessible = true }
                layoutStateScrapListField = layoutStateClass.getDeclaredField("mScrapList").apply { isAccessible = true }
                layoutStateNextMethod = layoutStateClass.getDeclaredMethod("next", recyclerClass).apply { isAccessible = true }

                layoutChunkResultConsumedField = chunkResultClass.getDeclaredField("mConsumed").apply { isAccessible = true }
                layoutChunkResultFinishedField = chunkResultClass.getDeclaredField("mFinished").apply { isAccessible = true }
                try {
                    layoutChunkResultIgnoreConsumedField = chunkResultClass.getDeclaredField("mIgnoreConsumed").apply { isAccessible = true }
                } catch (t: Throwable) {}
                try {
                    layoutChunkResultFocusableField = chunkResultClass.getDeclaredField("mFocusable").apply { isAccessible = true }
                } catch (t: Throwable) {}

                glmCachedBordersField = glmClass.getDeclaredField("mCachedBorders").apply { isAccessible = true }
                try {
                    glmDecorInsetsField = glmClass.getDeclaredField("mDecorInsets").apply { isAccessible = true }
                } catch (t: Throwable) {}
                glmOrientationField = lmClass.getDeclaredField("mOrientation").apply { isAccessible = true }
                try {
                    lmRecyclerViewField = rvlClass.getDeclaredField("mRecyclerView").apply { isAccessible = true }
                } catch (t: Throwable) {}

                addViewMethod = rvlClass.getMethod("addView", View::class.java).apply { isAccessible = true }
                addViewAtMethod = rvlClass.getMethod("addView", View::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
                addDisappearingViewMethod = rvlClass.getMethod("addDisappearingView", View::class.java).apply { isAccessible = true }
                addDisappearingViewAtMethod = rvlClass.getMethod("addDisappearingView", View::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
                layoutDecoratedWithMarginsMethod = rvlClass.getMethod(
                    "layoutDecoratedWithMargins",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
                calculateItemDecorationsMethod = rvlClass.getMethod("calculateItemDecorationsForChild", View::class.java, Rect::class.java).apply { isAccessible = true }
                getPaddingLeftMethod = rvlClass.getMethod("getPaddingLeft").apply { isAccessible = true }
                getPaddingTopMethod = rvlClass.getMethod("getPaddingTop").apply { isAccessible = true }
                getWidthMethod = rvlClass.getMethod("getWidth").apply { isAccessible = true }
                getHeightMethod = rvlClass.getMethod("getHeight").apply { isAccessible = true }
                stateItemCountMethod = stateClass.getMethod("getItemCount").apply { isAccessible = true }

                for (m in glmClass.declaredMethods) {
                    if (m.name == "calculateItemBorders" && m.parameterTypes.size == 3) {
                        m.isAccessible = true
                        calculateItemBordersMethod = m
                        break
                    }
                }

                initialized = true
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize ReflectionCache for True2DGridEngine", t)
                return false
            }
        }
    }

    /**
     * Intercept implementation for GridLayoutManager.layoutChunk.
     * When True 2D Grid Layout is active, lays out views for targetRow without stretching.
     */
    fun handleLayoutChunk(
        glm: Any,
        recycler: Any,
        state: Any,
        layoutState: Any,
        layoutChunkResult: Any,
        classLoader: ClassLoader
    ): Boolean {
        if (!ReflectionCache.init(glm.javaClass, classLoader)) {
            return false
        }

        try {
            val orientation = ReflectionCache.glmOrientationField?.getInt(glm) ?: 1
            if (orientation != 1) {
                // Horizontal orientation not used by Control Center; let stock handle
                return false
            }

            val itemCount = ReflectionCache.stateItemCountMethod?.invoke(state) as? Int ?: 0
            val currentPos = ReflectionCache.layoutStateCurrentPositionField?.getInt(layoutState) ?: -1
            if (currentPos < 0 || currentPos >= itemCount) {
                ReflectionCache.layoutChunkResultFinishedField?.setBoolean(layoutChunkResult, true)
                return true
            }

            val itemDirection = ReflectionCache.layoutStateItemDirectionField?.getInt(layoutState) ?: 1
            val offset = ReflectionCache.layoutStateOffsetField?.getInt(layoutState) ?: 0
            val isForward = (itemDirection == 1)

            // Resolve adapter
            val rv = ReflectionCache.lmRecyclerViewField?.get(glm) as? View
            val adapter = if (rv != null) {
                try {
                    rv.javaClass.getMethod("getAdapter").invoke(rv)
                } catch (t: Throwable) { null }
            } else null
            val targetAdapter = adapter ?: getActiveAdapter()

            val context = rv?.context ?: ControlCenterConfigHolder.sLastKnownContext
            val gridData = getGridData(targetAdapter, context)
            val unitRowHeight = gridData.unitRowHeight
            val targetRow = gridData.getItemRow(currentPos, targetAdapter)

            // System full-width items (Edit Button, WordlessMode strip, Dividers, Spaces):
            // Let stock GridLayoutManager layoutChunk handle them natively with full 4-column width,
            // center gravity, and natural wrap_content height!
            if (gridData.isSystemItem(currentPos, targetAdapter)) {
                return false
            }

            // Collect views belonging to targetRow
            val viewsInChunk = ArrayList<View>()
            val positionsInChunk = ArrayList<Int>()

            var scanPos = currentPos
            while (scanPos in 0 until itemCount && gridData.getItemRow(scanPos, targetAdapter) == targetRow && !gridData.isSystemItem(scanPos, targetAdapter)) {
                val nextView = ReflectionCache.layoutStateNextMethod?.invoke(layoutState, recycler) as? View ?: break
                viewsInChunk.add(nextView)
                positionsInChunk.add(scanPos)
                scanPos = ReflectionCache.layoutStateCurrentPositionField?.getInt(layoutState) ?: -1
            }

            if (viewsInChunk.isEmpty()) {
                ReflectionCache.layoutChunkResultFinishedField?.setBoolean(layoutChunkResult, true)
                return true
            }

            val paddingLeft = ReflectionCache.getPaddingLeftMethod?.invoke(glm) as? Int ?: 0
            val width = ReflectionCache.getWidthMethod?.invoke(glm) as? Int ?: 0

            var cachedBorders = ReflectionCache.glmCachedBordersField?.get(glm) as? IntArray
            if (cachedBorders == null || cachedBorders.size <= SPAN_COUNT) {
                try {
                    val borders = ReflectionCache.calculateItemBordersMethod?.invoke(null, null, SPAN_COUNT, width - paddingLeft) as? IntArray
                    if (borders != null) {
                        cachedBorders = borders
                        ReflectionCache.glmCachedBordersField?.set(glm, borders)
                    }
                } catch (t: Throwable) {}
            }
            if (cachedBorders == null || cachedBorders.size <= SPAN_COUNT) {
                return false
            }

            val isScrap = (ReflectionCache.layoutStateScrapListField?.get(layoutState) != null)
            val decorInsets = Rect()
            var maxViewHeight = 0
            var isAnyHeader = false

            for (i in viewsInChunk.indices) {
                val view = viewsInChunk[i]
                val itemPos = try {
                    val lp = view.layoutParams
                    val getPosM = lp.javaClass.getMethod("getViewLayoutPosition")
                    (getPosM.invoke(lp) as? Int)?.takeIf { it in 0 until itemCount }
                } catch (t: Throwable) { null } ?: positionsInChunk.getOrNull(i) ?: currentPos

                // 1. Add view to RecyclerView
                if (!isScrap) {
                    if (isForward) {
                        ReflectionCache.addViewMethod?.invoke(glm, view)
                    } else {
                        ReflectionCache.addViewAtMethod?.invoke(glm, view, 0)
                    }
                } else {
                    if (isForward) {
                        ReflectionCache.addDisappearingViewMethod?.invoke(glm, view)
                    } else {
                        ReflectionCache.addDisappearingViewAtMethod?.invoke(glm, view, 0)
                    }
                }

                // 2. Compute decoration insets
                decorInsets.setEmpty()
                ReflectionCache.calculateItemDecorationsMethod?.invoke(glm, view, decorInsets)

                // 3. Grid positioning
                val col = gridData.getItemCol(itemPos, targetAdapter)
                val spanW = gridData.getItemSpanW(itemPos, targetAdapter).coerceIn(1, SPAN_COUNT)
                val spanH = gridData.getItemSpanH(itemPos).coerceIn(1, 6)
                val isHeader = (itemPos == 0 && spanW == SPAN_COUNT)
                if (isHeader) isAnyHeader = true

                val lp = view.layoutParams as ViewGroup.MarginLayoutParams
                try {
                    val spanIndexField = lp.javaClass.getField("mSpanIndex")
                    spanIndexField.setInt(lp, col)
                    val spanSizeField = lp.javaClass.getField("mSpanSize")
                    spanSizeField.setInt(lp, spanW)
                } catch (t: Throwable) {}

                val horizMargin = lp.leftMargin + lp.rightMargin + decorInsets.left + decorInsets.right
                val vertMargin = lp.topMargin + lp.bottomMargin + decorInsets.top + decorInsets.bottom

                val leftBorder = cachedBorders[col.coerceIn(0, cachedBorders.size - 1)]
                val rightCol = (col + spanW).coerceAtMost(cachedBorders.size - 1)
                val rightBorder = cachedBorders[rightCol]
                val targetDecoratedWidth = rightBorder - leftBorder
                val targetContentWidth = (targetDecoratedWidth - horizMargin).coerceAtLeast(0)

                val targetDecoratedHeight = if (isHeader) {
                    if (lp.height > 0) lp.height + vertMargin else unitRowHeight
                } else {
                    spanH * unitRowHeight
                }
                val targetContentHeight = (targetDecoratedHeight - vertMargin).coerceAtLeast(0)

                val widthSpec = View.MeasureSpec.makeMeasureSpec(targetContentWidth, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(targetContentHeight, View.MeasureSpec.EXACTLY)

                view.measure(widthSpec, heightSpec)

                val left = paddingLeft + leftBorder
                val right = paddingLeft + rightBorder
                val top: Int
                val bottom: Int
                if (isForward) {
                    top = offset
                    bottom = offset + targetDecoratedHeight
                } else {
                    bottom = offset
                    top = offset - targetDecoratedHeight
                }

                ReflectionCache.layoutDecoratedWithMarginsMethod?.invoke(glm, view, left, top, right, bottom)

                if (targetDecoratedHeight > maxViewHeight) {
                    maxViewHeight = targetDecoratedHeight
                }
            }

            // 4. Calculate consumed row height
            val consumed: Int
            if (isAnyHeader) {
                consumed = maxViewHeight
            } else {
                val nextPos = ReflectionCache.layoutStateCurrentPositionField?.getInt(layoutState) ?: -1
                if (isForward) {
                    val nextRow = if (nextPos in 0 until itemCount) {
                        gridData.getItemRow(nextPos, targetAdapter)
                    } else {
                        targetRow + (maxViewHeight / unitRowHeight).coerceAtLeast(1)
                    }
                    val deltaRows = (nextRow - targetRow).coerceAtLeast(1)
                    consumed = deltaRows * unitRowHeight
                } else {
                    val prevRow = if (nextPos in 0 until itemCount) {
                        gridData.getItemRow(nextPos, targetAdapter)
                    } else {
                        targetRow - 1
                    }
                    val deltaRows = (targetRow - prevRow).coerceAtLeast(1)
                    consumed = deltaRows * unitRowHeight
                }
            }

            ReflectionCache.layoutChunkResultConsumedField?.setInt(layoutChunkResult, consumed)

            for (v in viewsInChunk) {
                if (v.hasFocusable()) {
                    ReflectionCache.layoutChunkResultFocusableField?.setBoolean(layoutChunkResult, true)
                    break
                }
            }

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Error in True2DGridEngine.handleLayoutChunk", t)
            return false
        }
    }
}
