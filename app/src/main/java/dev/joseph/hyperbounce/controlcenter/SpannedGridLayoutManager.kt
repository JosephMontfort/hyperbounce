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
        private const val MAX_ROWS = 50
    }

    private val mContext: Context
    private var mScrollY = 0
    private var mTotalContentHeight = 0
    private val mItemRects = arrayOfNulls<Rect>(200)
    private val mItemRowIndices = IntArray(200)
    private var mCalculatedItemCount = 0

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

    private fun initCustomSpanLookup() {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val spans = getItemSpans(position)
                return Math.max(1, Math.min(4, spans[0]))
            }

            override fun getSpanGroupIndex(adapterPosition: Int, spanCount: Int): Int {
                if (adapterPosition in 0 until mCalculatedItemCount) {
                    return Math.max(0, Math.min(9, mItemRowIndices[adapterPosition]))
                }
                return Math.max(0, Math.min(9, adapterPosition / 4))
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            mContext.resources.displayMetrics
        ).toInt()
    }

    /**
     * Returns [widthSpans, heightSpans] for item at adapter position.
     * widthSpans in [1..4], heightSpans in [1..2].
     */
    fun getItemSpans(position: Int): IntArray {
        var adapter: RecyclerView.Adapter<*>? = null
        try {
            val adapterField = RecyclerView.LayoutManager::class.java.getDeclaredField("mRecyclerView")
            adapterField.isAccessible = true
            val rv = adapterField.get(this) as? RecyclerView
            if (rv != null) {
                adapter = rv.adapter
            }
        } catch (t: Throwable) {}

        if (adapter != null && position in 0 until adapter.itemCount) {
            val viewType = adapter.getItemViewType(position)
            // 3484336: MediaPlayer -> 2x2 (or 4x1 landscape)
            if (viewType == 3484336) {
                val isLandscape = mContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                return if (isLandscape) intArrayOf(4, 1) else intArrayOf(2, 2)
            }
            // 3484338: Brightness Slider -> 1x2
            if (viewType == 3484338) {
                return intArrayOf(1, 2)
            }
            // 3484339: Volume Slider -> 1x2
            if (viewType == 3484339) {
                return intArrayOf(1, 2)
            }
            // 2274: Grouped 4-Tile Card Container -> 2x2
            if (viewType == 2274) {
                return intArrayOf(2, 2)
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
            // 338423: Mi Smart Hub -> 2x1
            if (viewType == 338423) {
                return intArrayOf(2, 1)
            }
            // 2668765: Google Home -> 2x1
            if (viewType == 2668765) {
                return intArrayOf(2, 1)
            }
            // 8398 or 3484337: Edit Mode Divider / Header -> 4x1
            if (viewType == 8398 || viewType == 3484337) {
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
        val tallHeight = unitHeight * 2 + gapY // ~162dp for 2-row modules

        val grid = Array(MAX_ROWS) { IntArray(SPAN_COUNT) { -1 } }

        mCalculatedItemCount = itemCount
        var maxBottom = padTop

        for (pos in 0 until Math.min(itemCount, mItemRects.size)) {
            val spans = getItemSpans(pos)
            val wSpans = Math.max(1, Math.min(4, spans[0]))
            val hSpans = Math.max(1, Math.min(2, spans[1]))

            // First-fit 2D search
            var assignedRow = -1
            var assignedCol = -1

            for (r in 0 until (MAX_ROWS - hSpans)) {
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
                assignedRow = 0
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

            val left = padLeft + assignedCol * (colWidth + gapX)
            val top = padTop + assignedRow * (unitHeight + gapY)
            val right = left + wSpans * colWidth + (wSpans - 1) * gapX
            val itemHeight = if (hSpans == 2) tallHeight else unitHeight
            val bottom = top + itemHeight

            mItemRects[pos] = Rect(left, top, right, bottom)
            mItemRowIndices[pos] = assignedRow

            if (bottom > maxBottom) {
                maxBottom = bottom
            }
        }

        mTotalContentHeight = maxBottom + padBottom
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            super.onLayoutChildren(recycler, state)
            return
        }

        detachAndScrapAttachedViews(recycler)

        val totalItemCount = itemCount
        val width = width
        val height = height

        calculate2DLayout(totalItemCount, width, height)

        val maxScroll = Math.max(0, mTotalContentHeight - height)
        mScrollY = Math.max(0, Math.min(mScrollY, maxScroll))

        for (i in 0 until Math.min(totalItemCount, mItemRects.size)) {
            val r = mItemRects[i] ?: continue

            val viewTop = r.top - mScrollY
            val viewBottom = r.bottom - mScrollY

            if (viewBottom >= -dpToPx(100f) && viewTop <= height + dpToPx(100f)) {
                val view = recycler.getViewForPosition(i)
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

        // Clean up remaining scrap views
        val scrapList = recycler.scrapList
        if (scrapList != null) {
            for (i in scrapList.size - 1 downTo 0) {
                val vh = scrapList[i]
                if (vh != null && vh.itemView.parent == null) {
                    recycler.recycleView(vh.itemView)
                }
            }
        }
    }

    override fun canScrollVertically(): Boolean = true

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (itemCount == 0 || dy == 0) return 0

        val height = height
        val maxScroll = Math.max(0, mTotalContentHeight - height)
        val oldScroll = mScrollY
        mScrollY = Math.max(0, Math.min(mScrollY + dy, maxScroll))
        val consumed = mScrollY - oldScroll

        if (consumed != 0) {
            offsetChildrenVertical(-consumed)
            onLayoutChildren(recycler, state)
        }

        return consumed
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = mScrollY
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = Math.max(mTotalContentHeight, height)
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = height
}
