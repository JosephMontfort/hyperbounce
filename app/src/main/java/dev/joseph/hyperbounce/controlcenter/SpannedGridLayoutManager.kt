package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Arrays

/**
 * 2D Bin-Packing Layout Manager across 4 columns for mixed spans in HyperOS Control Center.
 */
class SpannedGridLayoutManager : GridLayoutManager {
    companion object {
        private const val TAG = "SpannedGridLayoutManager"
        private const val SPAN_COUNT = 4
        private const val MAX_ROWS = 100
    }

    private val mContext: Context
    private var mScrollY = 0
    private var mTotalContentHeight = 0
    private var mItemRects = arrayOfNulls<Rect>(256)
    private var mItemRowIndices = IntArray(256)
    private var mItemColIndices = IntArray(256)
    private var mCalculatedItemCount = 0
    private var mAttachedRv: RecyclerView? = null

    constructor(context: Context, spanCount: Int) : super(context, spanCount) {
        this.mContext = context
        initCustomSpanLookup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        this.mContext = context
        initCustomSpanLookup()
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mAttachedRv = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        mAttachedRv = null
    }

    private var mCustomLookupInitialized = false

    private fun initCustomSpanLookup() {
        super.setSpanSizeLookup(object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val spans = getItemSpans(position)
                return Math.max(1, Math.min(4, spans[0]))
            }

            override fun getSpanGroupIndex(adapterPosition: Int, spanCount: Int): Int {
                if (adapterPosition in 0 until mCalculatedItemCount) {
                    return Math.max(0, Math.min(MAX_ROWS - 1, mItemRowIndices[adapterPosition]))
                }
                return Math.max(0, Math.min(MAX_ROWS - 1, adapterPosition / 4))
            }

            override fun getSpanIndex(position: Int, spanCount: Int): Int {
                if (position in 0 until mCalculatedItemCount) {
                    return Math.max(0, Math.min(3, mItemColIndices[position]))
                }
                return Math.max(0, Math.min(3, position % 4))
            }
        })
        super.getSpanSizeLookup().isSpanIndexCacheEnabled = false
        super.getSpanSizeLookup().isSpanGroupIndexCacheEnabled = false
        mCustomLookupInitialized = true
    }

    override fun setSpanSizeLookup(spanSizeLookup: GridLayoutManager.SpanSizeLookup?) {
        if (!mCustomLookupInitialized) {
            super.setSpanSizeLookup(spanSizeLookup)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            mContext.resources.displayMetrics
        ).toInt()
    }

    private fun ensureCapacity(required: Int) {
        if (required > mItemRects.size) {
            val newSize = Math.max(required + 64, mItemRects.size * 2)
            mItemRects = mItemRects.copyOf(newSize)
            mItemRowIndices = mItemRowIndices.copyOf(newSize)
            mItemColIndices = mItemColIndices.copyOf(newSize)
        }
    }

    /**
     * Returns [widthSpans, heightSpans] for item at adapter position.
     * widthSpans in [1..4], heightSpans in [1..2].
     */
    fun getItemSpans(position: Int): IntArray {
        var adapter: RecyclerView.Adapter<*>? = mAttachedRv?.adapter
        if (adapter == null) {
            try {
                val adapterField = RecyclerView.LayoutManager::class.java.getDeclaredField("mRecyclerView")
                adapterField.isAccessible = true
                val rv = adapterField.get(this) as? RecyclerView
                if (rv != null) {
                    adapter = rv.adapter
                    mAttachedRv = rv
                }
            } catch (t: Throwable) {}
        }
        if (adapter == null) {
            val rv = ControlCenterConfigHolder.sRecyclerView?.get() as? RecyclerView
            if (rv != null) {
                adapter = rv.adapter
                mAttachedRv = rv
            }
        }
        if (adapter == null) {
            adapter = ControlCenterConfigHolder.sMainPanelAdapter as? RecyclerView.Adapter<*>
        }

        if (adapter != null && position in 0 until adapter.itemCount) {
            val viewType = try {
                adapter.getItemViewType(position)
            } catch (t: Throwable) {
                -1
            }

            // 3484336 / 63342: MediaPlayer -> dynamic size (or 4x1 landscape)
            if (viewType == 3484336 || viewType == 63342) {
                val isLandscape = mContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape) return intArrayOf(4, 1)
                return when (ControlCenterConfigHolder.sMediaPlayerSize) {
                    "2x1" -> intArrayOf(2, 1)
                    "1x2" -> intArrayOf(1, 2)
                    "1x1" -> intArrayOf(1, 1)
                    "4x1" -> intArrayOf(4, 1)
                    "4x2" -> intArrayOf(4, 2)
                    "2x4" -> intArrayOf(2, 4)
                    else -> intArrayOf(2, 2)
                }
            }
            // 3484338 / 274442: Brightness Slider -> 1x2 vertical or 2x1 horizontal
            if (viewType == 3484338 || viewType == 274442) {
                return if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) intArrayOf(2, 1) else intArrayOf(1, 2)
            }
            // 3484339 / 865269: Volume Slider -> 1x2 vertical or 2x1 horizontal
            if (viewType == 3484339 || viewType == 865269) {
                return if (ControlCenterConfigHolder.sVolumeSliderHorizontal) intArrayOf(2, 1) else intArrayOf(1, 2)
            }
            // 2274: Grouped 4-Tile Card Container -> 2x2
            // 2281..2284: Grouped Containers -> 2x2
            if (viewType == 2274 || viewType in 2281..2284) {
                return intArrayOf(2, 2)
            }
            // 2285..2290: Dynamic Group Containers -> 2 columns x H rows (1..6)
            if (viewType in 2285..2290) {
                val h = viewType - 2284
                return intArrayOf(2, h)
            }
            // 2275: Grouped 2-Tile Horizontal -> 2x1
            if (viewType == 2275) {
                return intArrayOf(2, 1)
            }
            // 2276: Grouped 2-Tile Vertical -> 1x2
            if (viewType == 2276) {
                return intArrayOf(1, 2)
            }
            // 2273: Wi-Fi / Cell / Expanded Card -> 2x1
            if (viewType == 2273) {
                return intArrayOf(2, 1)
            }
            // 2277: Single Tile Vertical Card -> 1x2
            if (viewType == 2277) {
                return intArrayOf(1, 2)
            }
            // 2278: Single Tile Large 2x2 Card -> 2x2
            if (viewType == 2278) {
                return intArrayOf(2, 2)
            }
            // 338423: Mi Smart Hub -> 2x1
            if (viewType == 338423) {
                return intArrayOf(2, 1)
            }
            // 2668765: Google Home -> 2x1
            if (viewType == 2668765) {
                return intArrayOf(2, 1)
            }
            // 432337: Header Space -> 4x1
            if (viewType == 432337) {
                return intArrayOf(4, 1)
            }
            // 366837: Footer Space -> 4x1
            if (viewType == 366837) {
                return intArrayOf(4, 1)
            }
            // 8398 or 3484337 or 8397: Edit Mode Divider / Header / Security Footer -> 4x1
            if (viewType == 8398 || viewType == 3484337 || viewType == 8397) {
                return intArrayOf(4, 1)
            }
        }
        // Default circular tile: 1x1
        return intArrayOf(1, 1)
    }

    /**
     * 2D Bin-Packing Layout Computation across 4 columns
     */
    private fun calculate2DLayout(itemCount: Int, parentWidth: Int, parentHeight: Int) {
        if (itemCount <= 0 || parentWidth <= 0) {
            mCalculatedItemCount = 0
            mTotalContentHeight = 0
            return
        }

        ensureCapacity(itemCount)
        mCalculatedItemCount = itemCount

        val padLeft = paddingLeft
        val padRight = paddingRight
        val padTop = paddingTop
        val padBottom = paddingBottom

        val availableWidth = Math.max(0, parentWidth - padLeft - padRight)
        val resMarginId = mContext.resources.getIdentifier("control_center_universal_margin", "dimen", mContext.packageName)
        val gapX = if (resMarginId != 0) mContext.resources.getDimensionPixelSize(resMarginId) else dpToPx(10f)
        val gapY = gapX

        val colWidth = Math.max(1, (availableWidth - (SPAN_COUNT - 1) * gapX) / SPAN_COUNT)
        val resHeightId = mContext.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", mContext.packageName)
        val unitHeight = if (resHeightId != 0) mContext.resources.getDimensionPixelSize(resHeightId) else dpToPx(76f)
        val resHeaderId = mContext.resources.getIdentifier("new_header_height", "dimen", mContext.packageName)
        val headerHeight = if (resHeaderId != 0) mContext.resources.getDimensionPixelSize(resHeaderId) else dpToPx(125f)
        val dividerHeight = dpToPx(48f)
        val footerHeight = dpToPx(36f)

        val grid = Array(MAX_ROWS) { IntArray(SPAN_COUNT) { -1 } }
        val rowHeights = IntArray(MAX_ROWS) { unitHeight }

        var minRowForSection = 0

        // First-fit 2D bin-packing
        for (pos in 0 until itemCount) {
            val spans = getItemSpans(pos)
            val wSpans = Math.max(1, Math.min(4, spans[0]))
            val hSpans = Math.max(1, Math.min(6, spans[1]))

            var adapter: RecyclerView.Adapter<*>? = mAttachedRv?.adapter ?: (ControlCenterConfigHolder.sMainPanelAdapter as? RecyclerView.Adapter<*>)
            val vt = if (adapter != null && pos in 0 until adapter.itemCount) {
                try { adapter.getItemViewType(pos) } catch (t: Throwable) { -1 }
            } else -1

            val isFullRowDivider = (wSpans == 4) && (vt == 8398 || vt == 3484337 || vt == 8397)

            val searchStartRow = if (isFullRowDivider) {
                var rCandidate = minRowForSection
                while (rCandidate < MAX_ROWS) {
                    var rowEmpty = true
                    for (c in 0 until SPAN_COUNT) {
                        if (grid[rCandidate][c] != -1) {
                            rowEmpty = false
                            break
                        }
                    }
                    if (rowEmpty) break
                    rCandidate++
                }
                rCandidate
            } else {
                minRowForSection
            }

            var assignedRow = -1
            var assignedCol = -1

            for (r in searchStartRow until (MAX_ROWS - hSpans)) {
                for (c in 0..(SPAN_COUNT - wSpans)) {
                    var fits = true
                    for (dr in 0 until hSpans) {
                        for (dc in 0 until wSpans) {
                            if (grid[r + dr][c + dc] != -1) {
                                fits = false
                                break
                            }
                        }
                        if (!fits) break
                    }
                    if (fits) {
                        assignedRow = r
                        assignedCol = c
                        break
                    }
                }
                if (assignedRow != -1) break
            }

            if (assignedRow == -1) {
                assignedRow = searchStartRow
                assignedCol = 0
            }

            // Mark grid occupied
            for (dr in 0 until hSpans) {
                if (assignedRow + dr < MAX_ROWS) {
                    for (dc in 0 until wSpans) {
                        if (assignedCol + dc < SPAN_COUNT) {
                            grid[assignedRow + dr][assignedCol + dc] = pos
                        }
                    }
                }
            }

            mItemRowIndices[pos] = assignedRow
            mItemColIndices[pos] = assignedCol

            if (isFullRowDivider) {
                minRowForSection = assignedRow + hSpans
            }

            // Determine custom row heights for header/divider/footer
            if (vt == 432337 && assignedRow < MAX_ROWS) {
                rowHeights[assignedRow] = headerHeight
            } else if ((vt == 8398 || vt == 3484337 || vt == 8397) && assignedRow < MAX_ROWS) {
                rowHeights[assignedRow] = dividerHeight
            } else if (vt == 366837 && assignedRow < MAX_ROWS) {
                rowHeights[assignedRow] = footerHeight
            }
        }

        // Calculate cumulative row top positions
        val rowTops = IntArray(MAX_ROWS)
        rowTops[0] = padTop
        for (r in 1 until MAX_ROWS) {
            rowTops[r] = rowTops[r - 1] + rowHeights[r - 1] + gapY
        }

        var maxBottom = padTop
        for (pos in 0 until itemCount) {
            val spans = getItemSpans(pos)
            val wSpans = Math.max(1, Math.min(4, spans[0]))
            val hSpans = Math.max(1, Math.min(6, spans[1]))
            val r = mItemRowIndices[pos]
            val c = mItemColIndices[pos]

            val left = padLeft + c * (colWidth + gapX)
            val top = rowTops[r]
            val right = left + wSpans * colWidth + (wSpans - 1) * gapX
            val itemHeight = if (hSpans > 1) {
                (0 until hSpans).sumOf { dr -> rowHeights[Math.min(MAX_ROWS - 1, r + dr)] } + (hSpans - 1) * gapY
            } else {
                rowHeights[r]
            }
            val bottom = top + itemHeight

            mItemRects[pos] = Rect(left, top, right, bottom)
            if (bottom > maxBottom) {
                maxBottom = bottom
            }
        }

        mTotalContentHeight = maxBottom + padBottom
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val totalItemCount = state.itemCount
        if (totalItemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            mTotalContentHeight = 0
            mCalculatedItemCount = 0
            return
        }

        detachAndScrapAttachedViews(recycler)

        val width = width
        val height = height
        if (width <= 0) return

        calculate2DLayout(totalItemCount, width, height)

        val maxScroll = Math.max(0, mTotalContentHeight - height)
        mScrollY = Math.max(0, Math.min(mScrollY, maxScroll))

        val buffer = dpToPx(120f)
        for (i in 0 until Math.min(totalItemCount, mItemRects.size)) {
            val r = mItemRects[i] ?: continue

            val viewTop = r.top - mScrollY
            val viewBottom = r.bottom - mScrollY

            if (viewBottom >= -buffer && viewTop <= height + buffer) {
                val view = try {
                    recycler.getViewForPosition(i)
                } catch (t: Throwable) {
                    null
                } ?: continue

                addView(view)

                val rlp = view.layoutParams as? RecyclerView.LayoutParams
                if (rlp != null) {
                    rlp.width = r.width()
                    rlp.height = r.height()
                }

                val widthSpec = View.MeasureSpec.makeMeasureSpec(r.width(), View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(r.height(), View.MeasureSpec.EXACTLY)
                view.measure(widthSpec, heightSpec)

                layoutDecorated(view, r.left, viewTop, r.right, viewBottom)
            }
        }

        // Clean up scrap views
        val scrapList = recycler.scrapList
        if (scrapList != null) {
            for (i in scrapList.size - 1 downTo 0) {
                val vh = scrapList[i]
                if (vh != null && vh.itemView.parent == null) {
                    try {
                        val scrapField = RecyclerView.ViewHolder::class.java.getDeclaredField("mScrapContainer")
                        scrapField.isAccessible = true
                        scrapField.set(vh, null)
                        recycler.recycleView(vh.itemView)
                    } catch (t: Throwable) {
                        try {
                            recycler.recycleView(vh.itemView)
                        } catch (t2: Throwable) {}
                    }
                }
            }
        }
    }

    override fun canScrollVertically(): Boolean = true

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (state.itemCount == 0 || dy == 0) return 0

        val height = height
        val maxScroll = Math.max(0, mTotalContentHeight - height)
        val oldScroll = mScrollY
        mScrollY = Math.max(0, Math.min(mScrollY + dy, maxScroll))
        val consumed = mScrollY - oldScroll

        if (consumed != 0) {
            onLayoutChildren(recycler, state)
        }

        return consumed
    }

    override fun scrollToPosition(position: Int) {
        if (position in 0 until mCalculatedItemCount) {
            val r = mItemRects[position]
            if (r != null) {
                mScrollY = Math.max(0, Math.min(r.top, mTotalContentHeight - height))
                requestLayout()
            }
        }
    }

    override fun findFirstVisibleItemPosition(): Int {
        val padT = paddingTop
        val padB = height - paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (getDecoratedBottom(child) > padT && getDecoratedTop(child) < padB) {
                return getPosition(child)
            }
        }
        return RecyclerView.NO_POSITION
    }

    override fun findFirstCompletelyVisibleItemPosition(): Int {
        val padT = paddingTop
        val padB = height - paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (getDecoratedTop(child) >= padT && getDecoratedBottom(child) <= padB) {
                return getPosition(child)
            }
        }
        return RecyclerView.NO_POSITION
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = mScrollY
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = Math.max(mTotalContentHeight, height)
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = height
}
