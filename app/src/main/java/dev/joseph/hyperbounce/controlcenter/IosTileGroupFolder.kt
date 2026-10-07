package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor

/**
 * iOS-style 7-Tile Cluster Container and Expanded Overlay for HyperOS Control Center.
 *
 * Collapsed State (2x2 Card in Grid):
 *   - Slot (0,0): Tile 1 (Normal 1x1 circular)
 *   - Slot (1,0): Tile 2 (Normal 1x1 circular)
 *   - Slot (0,1): Tile 3 (Normal 1x1 circular)
 *   - Slot (1,1): Shrunken 4-Tile Sub-Cluster (2x2 sub-grid)
 *     * The 4 shrunken tiles do not trigger tile clicks directly.
 *     * No separate background layer underneath the 4 mini tiles.
 *     * Tapping anywhere on quadrant (1,1) triggers opening of the Expanded Overlay.
 *
 * Expanded State (Full-Screen Overlay):
 *   - Native Xiaomi blur (MiBlurCompat) and dark scrim backdrop.
 *   - Underlying Control Center panel fades out smoothly during expansion.
 *   - Symmetrical 2x2 Macro Layout mirroring collapsed state:
 *     * Quadrant (0,0): Tile 1 expanded to true 2x2 vertical card (with proper On/Off status)
 *     * Quadrant (1,0): Tile 2 expanded to true 2x2 vertical card (with proper On/Off status)
 *     * Quadrant (0,1): Tile 3 expanded to true 2x2 vertical card (with proper On/Off status)
 *     * Quadrant (1,1): 4 circular tiles in 2x2 sub-grid at standard 1x1 size
 *       - Label visibility strictly respects wordless_mode ("Show icon labels in control center").
 *       - When labels enabled, no text overlap with tile icon.
 *   - All tiles fully receive touches and update their live state/colors.
 *   - Tapping empty space or swiping up dismisses the overlay with authentic Folme spring physics.
 *   - If Control Center is dismissed by swipe-up gesture, the overlay closes immediately with it.
 */
object IosTileGroupFolder {
    private const val TAG = "HyperBounce_IosFolder"

    // Folme authentic spring / morph interpolator
    val FOLME_SPRING_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.20f, 1.0f)

    private var activeOverlayRef: WeakReference<IosFolderExpandedOverlay>? = null

    fun isExpandedOverlayActive(): Boolean {
        val overlay = activeOverlayRef?.get()
        return overlay != null && overlay.parent != null
    }

    fun getActiveOverlay(): IosFolderExpandedOverlay? {
        return activeOverlayRef?.get()
    }

    fun dismissActiveOverlay(rootView: View? = null, animated: Boolean = false) {
        val overlay = activeOverlayRef?.get()
        activeOverlayRef = null
        if (overlay != null) {
            if (animated) {
                overlay.dismissOverlay()
            } else {
                overlay.dismissImmediately()
            }
        }
        if (rootView != null) {
            try {
                val root = rootView.rootView as? ViewGroup
                val orphan = root?.findViewById<View>(0x7f09999b)
                if (orphan is IosFolderExpandedOverlay) {
                    orphan.dismissImmediately()
                } else if (orphan != null) {
                    root.removeView(orphan)
                }
            } catch (t: Throwable) {}
        }
    }

    fun invokeRecordAction(actionObj: Any?, arg: Any?): Boolean {
        if (actionObj == null) return false
        try {
            for (m in actionObj.javaClass.methods) {
                if (m.name == "invoke") {
                    m.isAccessible = true
                    if (m.parameterTypes.size == 1) {
                        val res = m.invoke(actionObj, arg)
                        return (res as? Boolean) ?: true
                    } else if (m.parameterTypes.isEmpty()) {
                        val res = m.invoke(actionObj)
                        return (res as? Boolean) ?: true
                    }
                }
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * Collapsed 2x2 Container for the 7-Tile Cluster.
     */
    class IosClusterContainer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : ViewGroup(context, attrs, defStyleAttr) {

        var group: TileGroup? = null
        var classLoader: ClassLoader? = null
        var qsListCtrl: Any? = null

        // Slot (1,1) sub-quadrant container
        val miniClusterView: MiniClusterView = MiniClusterView(context).apply {
            id = 0x7f09999a
        }

        override fun onViewAdded(child: View) {
            super.onViewAdded(child)
            child.setTag(0x7f09999c, true)
            if (child !== miniClusterView) {
                child.background = null
                child.clipToOutline = false
                child.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.translationX != 0f) v.translationX = 0f
                    if (v.translationY != 0f) v.translationY = 0f
                    if (v.scaleX != 1.0f) v.scaleX = 1.0f
                    if (v.scaleY != 1.0f) v.scaleY = 1.0f
                }
            }
        }

        override fun dispatchDraw(canvas: android.graphics.Canvas) {
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child === miniClusterView) continue
                if (child.translationX != 0f) child.translationX = 0f
                if (child.translationY != 0f) child.translationY = 0f
                if (child.scaleX != 1.0f) child.scaleX = 1.0f
                if (child.scaleY != 1.0f) child.scaleY = 1.0f
            }
            super.dispatchDraw(canvas)
        }

        override fun generateDefaultLayoutParams(): LayoutParams {
            return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)

            val density = resources.displayMetrics.density
            val pad = (6 * density).toInt()
            val gap = (6 * density).toInt()

            val availW = Math.max(0, w - 2 * pad - gap)
            val colW = Math.max(0, availW / 2)

            val availH = Math.max(0, h - 2 * pad - gap)
            val rowH = Math.max(0, availH / 2)

            val cellWSpec = MeasureSpec.makeMeasureSpec(colW, MeasureSpec.EXACTLY)
            val cellHSpec = MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY)

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                child.measure(cellWSpec, cellHSpec)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val density = resources.displayMetrics.density
            val pad = (6 * density).toInt()
            val gap = (6 * density).toInt()

            val availW = Math.max(0, (r - l) - 2 * pad - gap)
            val colW = Math.max(0, availW / 2)

            val availH = Math.max(0, (b - t) - 2 * pad - gap)
            val rowH = Math.max(0, availH / 2)

            var normalIndex = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child === miniClusterView) {
                    val miniLeft = pad + 1 * (colW + gap)
                    val miniTop = pad + 1 * (rowH + gap)
                    child.layout(miniLeft, miniTop, miniLeft + colW, miniTop + rowH)
                    child.visibility = View.VISIBLE
                    child.alpha = 1.0f
                    continue
                }

                val (col, row) = when (normalIndex) {
                    0 -> Pair(0, 0)
                    1 -> Pair(1, 0)
                    2 -> Pair(0, 1)
                    else -> Pair(0, 0)
                }
                normalIndex++

                val childLeft = pad + col * (colW + gap)
                val childTop = pad + row * (rowH + gap)
                child.layout(childLeft, childTop, childLeft + colW, childTop + rowH)

                child.visibility = View.VISIBLE
                if (!ControlCenterConfigHolder.sIsEditMode) child.alpha = 1.0f
                child.scaleX = 1.0f
                child.scaleY = 1.0f
                child.translationX = 0f
                child.translationY = 0f

                styleCollapsedNormalTile(child)
            }
        }

        private fun styleCollapsedNormalTile(child: View) {
            try {
                child.visibility = View.VISIBLE
                if (!ControlCenterConfigHolder.sIsEditMode) child.alpha = 1.0f
                child.scaleX = 1.0f
                child.scaleY = 1.0f
                child.translationX = 0f
                child.translationY = 0f
                child.background = null
                child.clipToOutline = false

                val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                if (tileLabelId != 0) {
                    val tileLabel = child.findViewById<View>(tileLabelId)
                    tileLabel?.visibility = View.GONE
                    tileLabel?.alpha = 0f
                }

                val markId = context.resources.getIdentifier("mark", "id", context.packageName)
                if (markId != 0) {
                    val mark = child.findViewById<View>(markId)
                    mark?.visibility = View.GONE
                }

                val iconFrameId = context.resources.getIdentifier("icon_frame", "id", context.packageName)
                val iconFrame = child.findViewById<View>(iconFrameId)
                if (iconFrame != null) {
                    val iflp = iconFrame.layoutParams
                    if (iflp is FrameLayout.LayoutParams) {
                        iflp.gravity = Gravity.CENTER
                        iflp.topMargin = 0
                        iflp.bottomMargin = 0
                        iflp.leftMargin = 0
                        iflp.rightMargin = 0
                        iconFrame.layoutParams = iflp
                    }
                    iconFrame.background = null
                }
            } catch (t: Throwable) {}
        }
    }

    /**
     * Sub-container housing the 4 shrunken mini circular tiles in 2 rows x 2 columns.
     * No separate background layer underneath.
     */
    class MiniClusterView(context: Context) : ViewGroup(context) {

        private var downX = 0f
        private var downY = 0f

        init {
            isClickable = true
            isFocusable = true
            clipToOutline = false
            clipChildren = false
            background = null

            setOnClickListener {
                val parentCluster = parent as? IosClusterContainer ?: return@setOnClickListener
                openExpandedFolder(parentCluster)
            }
        }

        private var cachedScale = 0.42f
        private var cachedHalfBase = 0f

        override fun onViewAdded(child: View) {
            super.onViewAdded(child)
            child.setTag(0x7f09999c, true)
            child.background = null
            child.clipToOutline = false
            child.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val halfBase = cachedHalfBase
                if (halfBase > 0f) {
                    v.pivotX = halfBase
                    v.pivotY = halfBase
                }
                v.scaleX = cachedScale
                v.scaleY = cachedScale
                if (v.translationX != 0f) v.translationX = 0f
                if (v.translationY != 0f) v.translationY = 0f
            }
        }

        override fun dispatchDraw(canvas: android.graphics.Canvas) {
            val expectedScale = cachedScale
            val halfBase = cachedHalfBase

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child.translationX != 0f) child.translationX = 0f
                if (child.translationY != 0f) child.translationY = 0f
                if (halfBase > 0f) {
                    if (child.pivotX != halfBase) child.pivotX = halfBase
                    if (child.pivotY != halfBase) child.pivotY = halfBase
                }
                if (child.scaleX != expectedScale) child.scaleX = expectedScale
                if (child.scaleY != expectedScale) child.scaleY = expectedScale
            }
            super.dispatchDraw(canvas)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.x - downX)
                    val dy = Math.abs(event.y - downY)
                    val slop = 20 * resources.displayMetrics.density
                    if (dx < slop && dy < slop) {
                        val parentCluster = parent as? IosClusterContainer
                        if (parentCluster != null) {
                            openExpandedFolder(parentCluster)
                            return true
                        }
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        private fun getBaseTileSize(): Int {
            val iconSizeId = context.resources.getIdentifier("qs_tile_item_icon_size", "dimen", context.packageName)
            return if (iconSizeId != 0) {
                context.resources.getDimensionPixelSize(iconSizeId)
            } else {
                (68 * resources.displayMetrics.density).toInt()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)

            val baseSize = getBaseTileSize()
            val childSpec = MeasureSpec.makeMeasureSpec(baseSize, MeasureSpec.EXACTLY)

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                try {
                    val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                    if (tileLabelId != 0) {
                        child.findViewById<View>(tileLabelId)?.visibility = View.GONE
                    }
                    val markId = context.resources.getIdentifier("mark", "id", context.packageName)
                    if (markId != 0) {
                        child.findViewById<View>(markId)?.visibility = View.GONE
                    }
                    val iconFrameId = context.resources.getIdentifier("icon_frame", "id", context.packageName)
                    val iconFrame = child.findViewById<View>(iconFrameId)
                    if (iconFrame != null) {
                        val lp = iconFrame.layoutParams
                        if (lp is FrameLayout.LayoutParams) {
                            lp.gravity = Gravity.CENTER
                            lp.topMargin = 0
                            lp.bottomMargin = 0
                            lp.leftMargin = 0
                            lp.rightMargin = 0
                            iconFrame.layoutParams = lp
                        }
                        iconFrame.background = null
                    }
                } catch (t: Throwable) {}

                child.measure(childSpec, childSpec)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val density = resources.displayMetrics.density
            val pad = (4 * density).toInt()
            val gap = (4 * density).toInt()

            val availW = Math.max(0, (r - l) - 2 * pad - gap)
            val subW = availW / 2f
            val availH = Math.max(0, (b - t) - 2 * pad - gap)
            val subH = availH / 2f

            val baseSize = getBaseTileSize()
            val targetSize = Math.min(subW, subH)
            val scale = if (baseSize > 0 && targetSize > 10) (targetSize / baseSize.toFloat()).coerceIn(0.2f, 0.6f) else 0.42f
            val halfBase = baseSize / 2f
            cachedScale = scale
            cachedHalfBase = halfBase

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                val col = i % 2
                val row = i / 2

                val centerX = pad + col * (subW + gap) + subW / 2f
                val centerY = pad + row * (subH + gap) + subH / 2f

                val cl = (centerX - halfBase).toInt()
                val ct = (centerY - halfBase).toInt()
                child.layout(cl, ct, cl + baseSize, ct + baseSize)

                child.pivotX = halfBase
                child.pivotY = halfBase
                child.scaleX = scale
                child.scaleY = scale
                child.translationX = 0f
                child.translationY = 0f
                child.background = null
                child.clipToOutline = false
                child.visibility = View.VISIBLE
                child.alpha = 1.0f

                child.isClickable = false
                child.isLongClickable = false
                child.isFocusable = false

                try {
                    val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                    if (tileLabelId != 0) {
                        child.findViewById<View>(tileLabelId)?.visibility = View.GONE
                    }
                    val markId = context.resources.getIdentifier("mark", "id", context.packageName)
                    if (markId != 0) {
                        child.findViewById<View>(markId)?.visibility = View.GONE
                    }
                    val iconFrameId = context.resources.getIdentifier("icon_frame", "id", context.packageName)
                    val iconFrame = child.findViewById<View>(iconFrameId)
                    if (iconFrame != null) {
                        val lp = iconFrame.layoutParams
                        if (lp is FrameLayout.LayoutParams) {
                            lp.gravity = Gravity.CENTER
                            lp.topMargin = 0
                            lp.bottomMargin = 0
                            lp.leftMargin = 0
                            lp.rightMargin = 0
                            iconFrame.layoutParams = lp
                        }
                        iconFrame.background = null
                    }
                } catch (t: Throwable) {}
            }
        }
    }

    /**
     * Full-screen Expanded Overlay for 7-Tile Cluster.
     */
    class IosFolderExpandedOverlay(
        context: Context,
        private val sourceContainer: IosClusterContainer
    ) : FrameLayout(context) {

        private val scrimView = View(context)
        private val cardContainer = FrameLayout(context)
        private var cardCurrentRadius = 0f
        private var isClosing = false
        private var isDismissed = false
        private val originalHolders = HashMap<Any, Any?>()

        init {
            id = 0x7f09999b
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            // 1. Full-screen Clean Scrim Backdrop over existing CC window blur
            scrimView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scrimView.setBackgroundColor(Color.parseColor("#4D000000"))
            addView(scrimView)

            // Tapping or swiping outside the card closes the overlay
            scrimView.setOnClickListener {
                dismissOverlay()
            }
            var scrimDownY = 0f
            scrimView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        scrimDownY = event.y
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = scrimDownY - event.y
                        if (Math.abs(dy) > 40 * resources.displayMetrics.density) {
                            dismissOverlay()
                            true
                        } else {
                            v.performClick()
                            true
                        }
                    }
                    else -> false
                }
            }

            // 2. Centered Card Container styled identically to the collapsed group container
            val density = resources.displayMetrics.density
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val screenWidth = resources.displayMetrics.widthPixels
            val cardPadding = (if (isLandscape) 10 * density else 14 * density).toInt()

            val radiusResId = context.resources.getIdentifier("control_center_universal_corner_radius", "dimen", context.packageName)
            cardCurrentRadius = if (radiusResId != 0) context.resources.getDimension(radiusResId) else 20f * density

            val cardLp = if (isLandscape) {
                val cardW = Math.min((460 * density).toInt(), (screenWidth * 0.62f).toInt())
                LayoutParams(cardW, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            } else {
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    marginStart = (14 * density).toInt()
                    marginEnd = (14 * density).toInt()
                }
            }
            cardContainer.layoutParams = cardLp
            cardContainer.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)

            val classLoader = sourceContainer.classLoader ?: context.classLoader
            ControlCenterGrouping.applyGroupContainerStyle(cardContainer, classLoader, sourceContainer.group)

            cardContainer.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, Math.max(0f, cardCurrentRadius))
                }
            }
            cardContainer.clipToOutline = true
            cardContainer.clipChildren = true

            buildExpandedCardContent()
            addView(cardContainer)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val screenHeight = resources.displayMetrics.heightPixels
            val navBarSlop = 48 * resources.displayMetrics.density
            if (ev.rawY < screenHeight - navBarSlop) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.dispatchTouchEvent(ev)
        }

        private fun createExpanded2x2CardView(classLoader: ClassLoader): ViewGroup {
            val density = resources.displayMetrics.density
            val qsListCtrl = sourceContainer.qsListCtrl
            val holder = ControlCenterGrouping.createLargeCardViewHolder(cardContainer, qsListCtrl, classLoader)
            val cardView = ControlCenterGrouping.getViewHolderItemView(holder) as? ViewGroup
            if (cardView != null) return cardView

            val fallback = ControlCenterGrouping.inflateTileForType("E", cardContainer, qsListCtrl, classLoader) as ViewGroup
            ControlCenterGrouping.adjustCardToVertical(fallback, density, context)
            return fallback
        }

        private fun buildExpandedCardContent() {
            val group = sourceContainer.group ?: return
            val classLoader = sourceContainer.classLoader ?: context.classLoader
            val qsListCtrl = sourceContainer.qsListCtrl
            val density = resources.displayMetrics.density

            val mainPanelController = ControlCenterGrouping.getMainPanelController(qsListCtrl)
                ?: ControlCenterGrouping.getMainPanelController(ControlCenterConfigHolder.sQSListController)

            val isWordless = try {
                Settings.Secure.getInt(context.contentResolver, "wordless_mode", 0) != 0
            } catch (t: Throwable) { false }
            val showLabels = !isWordless

            val macroGrid = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            val macroRow1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val macroRow2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            }

            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val res2RowsId = context.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", context.packageName)
            val quadHeight = if (isLandscape) {
                (118 * density).toInt()
            } else {
                if (res2RowsId != 0) context.resources.getDimensionPixelSize(res2RowsId) else (158 * density).toInt()
            }

            val quadWeightLp = {
                LinearLayout.LayoutParams(0, quadHeight, 1f).apply {
                    marginStart = (5 * density).toInt()
                    marginEnd = (5 * density).toInt()
                }
            }

            // Quadrant (0,0): Tile 1
            if (group.specs.isNotEmpty()) {
                val tile1 = createExpanded2x2CardView(classLoader)
                macroRow1.addView(tile1, quadWeightLp())
                bindTileDirectly(tile1, group.specs[0], mainPanelController, classLoader)
            }

            // Quadrant (1,0): Tile 2
            if (group.specs.size > 1) {
                val tile2 = createExpanded2x2CardView(classLoader)
                macroRow1.addView(tile2, quadWeightLp())
                bindTileDirectly(tile2, group.specs[1], mainPanelController, classLoader)
            }

            // Quadrant (0,1): Tile 3
            if (group.specs.size > 2) {
                val tile3 = createExpanded2x2CardView(classLoader)
                macroRow2.addView(tile3, quadWeightLp())
                bindTileDirectly(tile3, group.specs[2], mainPanelController, classLoader)
            }

            // Quadrant (1,1): 2x2 Sub-grid of remaining 4 circular tiles
            val miniSubGrid = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = quadWeightLp()
                gravity = Gravity.CENTER
            }

            val subRow1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            val subRow2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    topMargin = (6 * density).toInt()
                }
            }

            val circLp = {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = (3 * density).toInt()
                    marginEnd = (3 * density).toInt()
                }
            }

            for (i in 3 until Math.min(7, group.specs.size)) {
                val spec = group.specs[i]
                val targetSubRow = if (i < 5) subRow1 else subRow2
                val circView = ControlCenterGrouping.inflateTileForType("C", targetSubRow, qsListCtrl, classLoader)

                val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                val tileLabel = if (tileLabelId != 0) circView.findViewById<TextView>(tileLabelId) else null
                val iconFrameId = context.resources.getIdentifier("icon_frame", "id", context.packageName)
                val iconFrame = if (iconFrameId != 0) circView.findViewById<View>(iconFrameId) else null

                circView.scaleX = 1.0f
                circView.scaleY = 1.0f

                if (showLabels) {
                    tileLabel?.visibility = View.VISIBLE
                    tileLabel?.alpha = 1.0f
                    tileLabel?.maxLines = 1
                    tileLabel?.textSize = 10f
                    val tlp = tileLabel?.layoutParams as? FrameLayout.LayoutParams
                    if (tlp != null) {
                        tlp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        tlp.bottomMargin = (2 * density).toInt()
                        tlp.topMargin = 0
                        tlp.height = (15 * density).toInt()
                        tileLabel.layoutParams = tlp
                    }
                    val iflp = iconFrame?.layoutParams as? FrameLayout.LayoutParams
                    if (iflp != null) {
                        iflp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        iflp.topMargin = (2 * density).toInt()
                        iflp.bottomMargin = 0
                        iconFrame.layoutParams = iflp
                    }
                } else {
                    tileLabel?.visibility = View.GONE
                    tileLabel?.alpha = 0.0f
                    val iflp = iconFrame?.layoutParams as? FrameLayout.LayoutParams
                    if (iflp != null) {
                        iflp.gravity = Gravity.CENTER
                        iflp.topMargin = 0
                        iflp.bottomMargin = 0
                        iconFrame.layoutParams = iflp
                    }
                }
                iconFrame?.background = null

                targetSubRow.addView(circView, circLp())
                bindTileDirectly(circView, spec, mainPanelController, classLoader)
            }

            miniSubGrid.addView(subRow1)
            miniSubGrid.addView(subRow2)
            macroRow2.addView(miniSubGrid)

            macroGrid.addView(macroRow1)
            macroGrid.addView(macroRow2)
            cardContainer.addView(macroGrid)
        }

        private fun bindTileDirectly(
            view: View,
            spec: String,
            mainPanelController: Any?,
            classLoader: ClassLoader
        ) {
            try {
                val recordMap = HashMap<String, Any>()
                val activeCtrl = sourceContainer.qsListCtrl ?: ControlCenterConfigHolder.sQSListController
                if (activeCtrl != null) {
                    val listFieldNames = arrayOf("addedTiles", "copiedTiles", "systemTiles", "packageTiles", "records")
                    for (fn in listFieldNames) {
                        try {
                            val f = activeCtrl.javaClass.getDeclaredField(fn)
                            f.isAccessible = true
                            val list = f.get(activeCtrl) as? List<*>
                            if (list != null) {
                                for (r in list) {
                                    val s = ControlCenterConfigHolder.getRecordSpec(r)
                                    if (s != null && !recordMap.containsKey(s) && r != null) recordMap[s] = r
                                }
                            }
                        } catch (t: Throwable) {}
                    }
                }
                for (r in ControlCenterConfigHolder.sAllQSRecords) {
                    val s = ControlCenterConfigHolder.getRecordSpec(r)
                    if (s != null && !recordMap.containsKey(s) && r != null) recordMap[s] = r
                }

                val childRecord = recordMap[spec] ?: return
                try {
                    val origH = childRecord.javaClass.getMethod("getHolder").invoke(childRecord)
                    if (origH != null && !originalHolders.containsKey(childRecord)) {
                        originalHolders[childRecord] = origH
                    }
                } catch (t: Throwable) {}

                val subHolder = ControlCenterGrouping.createSubHolderForView(view, mainPanelController, classLoader) ?: return
                ControlCenterGrouping.attachSubHolder(subHolder)

                for (shM in subHolder.javaClass.methods) {
                    if (shM.name.startsWith("setItem")) {
                        shM.invoke(subHolder, childRecord)
                        break
                    }
                }
                val setHolderMethod = childRecord.javaClass.getMethod(
                    "setHolder",
                    Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader)
                )
                setHolderMethod.invoke(childRecord, subHolder)

                val onBindMethod = childRecord.javaClass.getMethod("onBindViewHolder")
                onBindMethod.invoke(childRecord)

                val modeClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController\$Mode", false, classLoader)
                val currentMode = modeClass.getMethod("valueOf", String::class.java).invoke(null, "NORMAL")
                val updateModeMethod = childRecord.javaClass.getMethod("updateMode", modeClass, Boolean::class.javaPrimitiveType)
                updateModeMethod.invoke(childRecord, currentMode, false)
                try {
                    val onModeChangedM = view.javaClass.getMethod("onModeChanged", modeClass, Boolean::class.javaPrimitiveType)
                    onModeChangedM.invoke(view, currentMode, false)
                } catch (t: Throwable) {}

                val isConnected = (spec == "wifi" || spec == "cell")

                val getTileM = childRecord.javaClass.getMethod("getTile")
                val tileObj = getTileM.invoke(childRecord)
                if (tileObj != null) {
                    val setLisM = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                    setLisM.invoke(tileObj, childRecord, true)
                    val refM = tileObj.javaClass.getMethod("refreshState")
                    refM.invoke(tileObj)

                    val getStateM = tileObj.javaClass.getMethod("getState")
                    val curState = getStateM.invoke(tileObj)
                    if (curState != null) {
                        for (vm in view.javaClass.methods) {
                            if (vm.name == "updateState" && vm.parameterTypes.size == 3) {
                                vm.isAccessible = true
                                vm.invoke(view, curState, isConnected, false)
                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(view, curState, classLoader)
                                break
                            }
                        }
                    }
                }

                view.isClickable = true
                view.isLongClickable = true
                view.isFocusable = true
                view.setOnLongClickListener { clickedView ->
                    var handled = false
                    try {
                        val f = childRecord.javaClass.getDeclaredField("longClickAction")
                        f.isAccessible = true
                        val action = f.get(childRecord)
                        if (action != null) {
                            handled = invokeRecordAction(action, clickedView)
                        }
                    } catch (t: Throwable) {}

                    if (!handled) {
                        try {
                            val tObj = childRecord.javaClass.getMethod("getTile").invoke(childRecord)
                            if (tObj != null) {
                                val stateObj = try { tObj.javaClass.getMethod("getState").invoke(tObj) } catch (t: Throwable) { null }
                                val isDualTarget = try {
                                    val f = stateObj?.javaClass?.getField("dualTarget")
                                    f?.getBoolean(stateObj) ?: false
                                } catch (t: Throwable) { false }

                                if (isDualTarget) {
                                    try {
                                        tObj.javaClass.getMethod("secondaryClick").invoke(tObj)
                                        handled = true
                                    } catch (t: Throwable) {
                                        try {
                                            tObj.javaClass.getMethod("longClick").invoke(tObj)
                                            handled = true
                                        } catch (t2: Throwable) {}
                                    }
                                } else {
                                    try {
                                        tObj.javaClass.getMethod("longClick").invoke(tObj)
                                        handled = true
                                    } catch (t: Throwable) {
                                        try {
                                            tObj.javaClass.getMethod("secondaryClick").invoke(tObj)
                                            handled = true
                                        } catch (t2: Throwable) {}
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Tile onLongClick error: $spec", t)
                        }
                    }
                    if (handled) {
                        dismissOverlay()
                    }
                    handled
                }
            } catch (t: Throwable) {
                Log.e(TAG, "bindTileDirectly error: $spec", t)
            }
        }

        fun playOpenAnimation() {
            val sourceLoc = IntArray(2)
            sourceContainer.getLocationOnScreen(sourceLoc)
            val sourceX = sourceLoc[0].toFloat()
            val sourceY = sourceLoc[1].toFloat()
            val sourceW = sourceContainer.width.toFloat()
            val sourceH = sourceContainer.height.toFloat()

            scrimView.alpha = 0f
            cardContainer.alpha = 0f

            post {
                val cardLoc = IntArray(2)
                cardContainer.getLocationOnScreen(cardLoc)
                val cardX = cardLoc[0].toFloat()
                val cardY = cardLoc[1].toFloat()
                val cardW = cardContainer.width.toFloat()
                val cardH = cardContainer.height.toFloat()

                val deltaX = (sourceX + sourceW / 2f) - (cardX + cardW / 2f)
                val deltaY = (sourceY + sourceH / 2f) - (cardY + cardH / 2f)
                val initialScaleX = if (cardW > 0) sourceW / cardW else 0.5f
                val initialScaleY = if (cardH > 0) sourceH / cardH else 0.5f

                val radiusResId = context.resources.getIdentifier("control_center_universal_corner_radius", "dimen", context.packageName)
                val targetRadius = if (radiusResId != 0) context.resources.getDimension(radiusResId) else 20f * resources.displayMetrics.density
                val avgInitialScale = Math.max(0.01f, Math.sqrt((initialScaleX * initialScaleY).toDouble()).toFloat())
                val startLocalRadius = targetRadius / avgInitialScale

                cardContainer.pivotX = cardW / 2f
                cardContainer.pivotY = cardH / 2f
                cardContainer.translationX = deltaX
                cardContainer.translationY = deltaY
                cardContainer.scaleX = initialScaleX
                cardContainer.scaleY = initialScaleY
                cardCurrentRadius = startLocalRadius
                cardContainer.invalidateOutline()

                scrimView.animate()
                    .alpha(1f)
                    .setDuration(240)
                    .start()

                val mainPanel = findMainPanel()
                mainPanel?.animate()
                    ?.alpha(0f)
                    ?.setDuration(280)
                    ?.setInterpolator(FOLME_SPRING_INTERPOLATOR)
                    ?.start()

                val openAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 280
                    interpolator = FOLME_SPRING_INTERPOLATOR
                    addUpdateListener { va ->
                        val t = va.animatedValue as Float
                        cardContainer.translationX = deltaX * (1f - t)
                        cardContainer.translationY = deltaY * (1f - t)
                        cardContainer.scaleX = initialScaleX + (1f - initialScaleX) * t
                        cardContainer.scaleY = initialScaleY + (1f - initialScaleY) * t
                        cardCurrentRadius = startLocalRadius + (targetRadius - startLocalRadius) * t
                        cardContainer.invalidateOutline()
                        cardContainer.alpha = Math.min(1f, 0.35f + 0.65f * t)
                    }
                }
                openAnim.start()
            }
        }

        private fun restoreOriginalHolders() {
            try {
                val classLoader = sourceContainer.classLoader ?: context.classLoader
                val holderClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader)
                for ((record, origHolder) in originalHolders) {
                    if (origHolder != null) {
                        try {
                            val setHolderMethod = record.javaClass.getMethod("setHolder", holderClass)
                            setHolderMethod.invoke(record, origHolder)
                            val onBindMethod = record.javaClass.getMethod("onBindViewHolder")
                            onBindMethod.invoke(record)
                        } catch (t: Throwable) {}
                    }
                }
                originalHolders.clear()
                ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
            } catch (t: Throwable) {
                Log.e(TAG, "restoreOriginalHolders error", t)
            }
        }

        fun dismissOverlay() {
            if (isClosing || isDismissed) return
            isClosing = true
            activeOverlayRef = null

            val sourceLoc = IntArray(2)
            sourceContainer.getLocationOnScreen(sourceLoc)
            val sourceX = sourceLoc[0].toFloat()
            val sourceY = sourceLoc[1].toFloat()
            val sourceW = sourceContainer.width.toFloat()
            val sourceH = sourceContainer.height.toFloat()

            val cardLoc = IntArray(2)
            cardContainer.getLocationOnScreen(cardLoc)
            val cardX = cardLoc[0].toFloat()
            val cardY = cardLoc[1].toFloat()
            val cardW = cardContainer.width.toFloat()
            val cardH = cardContainer.height.toFloat()

            val targetDeltaX = (sourceX + sourceW / 2f) - (cardX + cardW / 2f)
            val targetDeltaY = (sourceY + sourceH / 2f) - (cardY + cardH / 2f)
            val targetScaleX = if (cardW > 0) sourceW / cardW else 0.5f
            val targetScaleY = if (cardH > 0) sourceH / cardH else 0.5f

            val radiusResId = context.resources.getIdentifier("control_center_universal_corner_radius", "dimen", context.packageName)
            val targetRadius = if (radiusResId != 0) context.resources.getDimension(radiusResId) else 20f * resources.displayMetrics.density
            val avgScale = Math.max(0.01f, Math.sqrt((targetScaleX * targetScaleY).toDouble()).toFloat())
            val endLocalRadius = targetRadius / avgScale

            cardContainer.pivotX = cardW / 2f
            cardContainer.pivotY = cardH / 2f

            val mainPanel = findMainPanel()
            mainPanel?.animate()
                ?.alpha(1f)
                ?.setDuration(240)
                ?.setInterpolator(FOLME_SPRING_INTERPOLATOR)
                ?.start()

            scrimView.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            val closeAnim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 240
                interpolator = FOLME_SPRING_INTERPOLATOR
                addUpdateListener { va ->
                    val t = va.animatedValue as Float
                    cardContainer.translationX = targetDeltaX * (1f - t)
                    cardContainer.translationY = targetDeltaY * (1f - t)
                    cardContainer.scaleX = targetScaleX + (1f - targetScaleX) * t
                    cardContainer.scaleY = targetScaleY + (1f - targetScaleY) * t
                    cardCurrentRadius = endLocalRadius + (targetRadius - endLocalRadius) * t
                    cardContainer.invalidateOutline()
                    cardContainer.alpha = Math.max(0f, t)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        dismissImmediately()
                    }
                })
            }
            closeAnim.start()
        }

        fun dismissImmediately() {
            if (isDismissed) return
            isDismissed = true
            isClosing = true
            activeOverlayRef = null
            val mainPanel = findMainPanel()
            mainPanel?.animate()?.cancel()
            mainPanel?.alpha = 1f
            restoreOriginalHolders()
            val p = parent as? ViewGroup
            p?.removeView(this)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            activeOverlayRef = null
            val mainPanel = findMainPanel()
            mainPanel?.animate()?.cancel()
            mainPanel?.alpha = 1f
        }

        private fun findMainPanel(): View? {
            var curr: View? = sourceContainer
            while (curr != null) {
                val idName = try { curr.resources.getResourceEntryName(curr.id) } catch (t: Throwable) { "" }
                if (idName.contains("main_panel") || curr.javaClass.name.contains("MainPanel")) {
                    return curr
                }
                curr = curr.parent as? View
            }
            val root = rootView ?: return null
            val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "control_center_main_panel", "main_panel_container")
            for (idStr in panelIds) {
                val resId = context.resources.getIdentifier(idStr, "id", context.packageName)
                if (resId != 0) {
                    val p = root.findViewById<View>(resId)
                    if (p != null) return p
                }
            }
            return null
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissOverlay()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /**
     * Traverses up to find the root window view (ControlCenterWindowViewImpl or DecorView).
     */
    fun getTargetRootView(view: View): ViewGroup? {
        var curr: View? = view
        var bestTarget: ViewGroup? = null
        while (curr != null) {
            if (curr is ViewGroup) {
                val cName = curr.javaClass.name
                if (cName.contains("ControlCenterWindowViewImpl") || cName.contains("NotificationShadeWindowView")) {
                    return curr
                }
                bestTarget = curr
            }
            val p = curr.parent
            curr = if (p is View) p else null
        }
        return bestTarget ?: (view.rootView as? ViewGroup)
    }

    /**
     * Opens the expanded folder overlay on top of the Control Center root window.
     */
    fun openExpandedFolder(clusterContainer: IosClusterContainer) {
        try {
            val rootWindow = getTargetRootView(clusterContainer) ?: return
            val existing = rootWindow.findViewById<View>(0x7f09999b)
            if (existing != null) {
                if (existing is IosFolderExpandedOverlay) {
                    existing.dismissImmediately()
                } else {
                    (existing.parent as? ViewGroup)?.removeView(existing)
                }
            }

            val overlay = IosFolderExpandedOverlay(clusterContainer.context, clusterContainer)
            activeOverlayRef = WeakReference(overlay)
            rootWindow.addView(overlay)
            overlay.playOpenAnimation()
            overlay.requestFocus()
        } catch (t: Throwable) {
            Log.e(TAG, "openExpandedFolder error", t)
        }
    }
}
