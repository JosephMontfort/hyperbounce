package dev.joseph.hyperbounce;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class SpannedGridLayoutManager extends GridLayoutManager {
    private static final String TAG = "SpannedGridLayoutManager";
    private static final int SPAN_COUNT = 4;
    private static final int MAX_ROWS = 50;

    private final Context mContext;
    private int mScrollY = 0;
    private int mTotalContentHeight = 0;
    private final Rect[] mItemRects = new Rect[200];
    private final int[] mItemRowIndices = new int[200];
    private int mCalculatedItemCount = 0;

    public SpannedGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
        this.mContext = context;
        initCustomSpanLookup();
    }

    public SpannedGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mContext = context;
        initCustomSpanLookup();
    }

    private void initCustomSpanLookup() {
        setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int[] spans = getItemSpans(position);
                return Math.max(1, Math.min(4, spans[0]));
            }

            @Override
            public int getSpanGroupIndex(int adapterPosition, int spanCount) {
                if (adapterPosition >= 0 && adapterPosition < mCalculatedItemCount) {
                    return Math.max(0, Math.min(9, mItemRowIndices[adapterPosition]));
                }
                return Math.max(0, Math.min(9, adapterPosition / 4));
            }
        });
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, mContext.getResources().getDisplayMetrics());
    }

    /**
     * Returns [widthSpans, heightSpans] for item at adapter position.
     * widthSpans in [1..4], heightSpans in [1..2].
     */
    public int[] getItemSpans(int position) {
        RecyclerView.Adapter adapter = null;
        try {
            Field adapterField = RecyclerView.LayoutManager.class.getDeclaredField("mRecyclerView");
            adapterField.setAccessible(true);
            RecyclerView rv = (RecyclerView) adapterField.get(this);
            if (rv != null) {
                adapter = rv.getAdapter();
            }
        } catch (Throwable ignore) {}

        if (adapter != null && position >= 0 && position < adapter.getItemCount()) {
            int viewType = adapter.getItemViewType(position);
            // 3484336: MediaPlayer -> 2x2
            if (viewType == 3484336) {
                boolean isLandscape = mContext.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                return isLandscape ? new int[]{4, 1} : new int[]{2, 2};
            }
            // 3484338: Brightness Slider -> 1x2 (or 2x1 if compact)
            if (viewType == 3484338) {
                return new int[]{1, 2};
            }
            // 3484339: Volume Slider -> 1x2 (or 2x1 if compact)
            if (viewType == 3484339) {
                return new int[]{1, 2};
            }
            // 2274: Grouped 4-Tile Card Container -> 2x2
            if (viewType == 2274) {
                return new int[]{2, 2};
            }
            // 2273: Wi-Fi / Cell / Expanded Card -> 2x1
            if (viewType == 2273) {
                return new int[]{2, 1};
            }
            // 338423: Mi Smart Hub -> 2x1
            if (viewType == 338423) {
                return new int[]{2, 1};
            }
            // 2668765: Google Home -> 2x1
            if (viewType == 2668765) {
                return new int[]{2, 1};
            }
            // 8398: Edit Mode Divider / Header -> 4x1
            if (viewType == 8398 || viewType == 3484337) {
                return new int[]{4, 1};
            }
        }
        // Default circular tile: 1x1
        return new int[]{1, 1};
    }

    /**
     * 2D Bin-Packing Layout Computation
     */
    private void calculate2DLayout(int itemCount, int parentWidth, int parentHeight) {
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        int availableWidth = Math.max(0, parentWidth - paddingLeft - paddingRight);
        int gapX = dpToPx(10);
        int gapY = dpToPx(10);

        int colWidth = Math.max(1, (availableWidth - (SPAN_COUNT - 1) * gapX) / SPAN_COUNT);
        int unitHeight = dpToPx(76);
        int tallHeight = unitHeight * 2 + gapY; // ~162dp for 2-row modules

        int[][] grid = new int[MAX_ROWS][SPAN_COUNT];
        for (int[] row : grid) {
            Arrays.fill(row, -1);
        }

        mCalculatedItemCount = itemCount;
        int maxBottom = paddingTop;

        for (int pos = 0; pos < itemCount && pos < mItemRects.length; pos++) {
            int[] spans = getItemSpans(pos);
            int wSpans = Math.max(1, Math.min(4, spans[0]));
            int hSpans = Math.max(1, Math.min(2, spans[1]));

            // First-fit 2D search
            int assignedRow = -1;
            int assignedCol = -1;

            for (int r = 0; r < MAX_ROWS - hSpans; r++) {
                for (int c = 0; c <= SPAN_COUNT - wSpans; c++) {
                    boolean fits = true;
                    for (int dr = 0; dr < hSpans; dr++) {
                        for (int dc = 0; dc < wSpans; dc++) {
                            if (grid[r + dr][c + dc] != -1) {
                                fits = false;
                                break;
                            }
                        }
                        if (!fits) break;
                    }
                    if (fits) {
                        assignedRow = r;
                        assignedCol = c;
                        break;
                    }
                }
                if (assignedRow != -1) break;
            }

            if (assignedRow == -1) {
                assignedRow = 0;
                assignedCol = 0;
            }

            // Mark grid occupied
            for (int dr = 0; dr < hSpans && (assignedRow + dr) < MAX_ROWS; dr++) {
                for (int dc = 0; dc < wSpans && (assignedCol + dc) < SPAN_COUNT; dc++) {
                    grid[assignedRow + dr][assignedCol + dc] = pos;
                }
            }

            int left = paddingLeft + assignedCol * (colWidth + gapX);
            int top = paddingTop + assignedRow * (unitHeight + gapY);
            int right = left + wSpans * colWidth + (wSpans - 1) * gapX;
            int itemHeight = (hSpans == 2) ? tallHeight : unitHeight;
            int bottom = top + itemHeight;

            mItemRects[pos] = new Rect(left, top, right, bottom);
            mItemRowIndices[pos] = assignedRow;

            if (bottom > maxBottom) {
                maxBottom = bottom;
            }
        }

        mTotalContentHeight = maxBottom + paddingBottom;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() == 0 || state.isPreLayout()) {
            super.onLayoutChildren(recycler, state);
            return;
        }

        detachAndScrapAttachedViews(recycler);

        int itemCount = getItemCount();
        int width = getWidth();
        int height = getHeight();

        calculate2DLayout(itemCount, width, height);

        // Clamp scrollY
        int maxScroll = Math.max(0, mTotalContentHeight - height);
        mScrollY = Math.max(0, Math.min(mScrollY, maxScroll));

        for (int i = 0; i < itemCount && i < mItemRects.length; i++) {
            Rect r = mItemRects[i];
            if (r == null) continue;

            // View culling: check if intersects visible window
            int viewTop = r.top - mScrollY;
            int viewBottom = r.bottom - mScrollY;

            if (viewBottom >= -dpToPx(100) && viewTop <= height + dpToPx(100)) {
                View view = recycler.getViewForPosition(i);
                addView(view);

                int widthSpec = View.MeasureSpec.makeMeasureSpec(r.width(), View.MeasureSpec.EXACTLY);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(r.height(), View.MeasureSpec.EXACTLY);
                view.measure(widthSpec, heightSpec);

                layoutDecoratedWithMargins(view, r.left, viewTop, r.right, viewBottom);
            }
        }

        // Clean up any remaining scrap views
        List<RecyclerView.ViewHolder> scrapList = recycler.getScrapList();
        if (scrapList != null) {
            for (int i = scrapList.size() - 1; i >= 0; i--) {
                RecyclerView.ViewHolder vh = scrapList.get(i);
                if (vh != null && vh.itemView != null && vh.itemView.getParent() == null) {
                    recycler.recycleView(vh.itemView);
                }
            }
        }
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() == 0 || dy == 0) return 0;

        int height = getHeight();
        int maxScroll = Math.max(0, mTotalContentHeight - height);
        int oldScroll = mScrollY;
        mScrollY = Math.max(0, Math.min(mScrollY + dy, maxScroll));
        int consumed = mScrollY - oldScroll;

        if (consumed != 0) {
            offsetChildrenVertical(-consumed);
            onLayoutChildren(recycler, state);
        }

        return consumed;
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return mScrollY;
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return Math.max(mTotalContentHeight, getHeight());
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return getHeight();
    }
}
