package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * iOS-style 7-Tile Cluster Container and Expanded Overlay for HyperOS Control Center.
 *
 * Collapsed State (2x2 Card in Grid):
 *   - Slot (0,0): Tile 1 (Normal 1x1 circular)
 *   - Slot (1,0): Tile 2 (Normal 1x1 circular)
 *   - Slot (0,1): Tile 3 (Normal 1x1 circular)
 *   - Slot (1,1): Shrunken 4-Tile Sub-Cluster (2x2 sub-grid)
 *     * The 4 shrunken tiles do not trigger tile clicks directly.
 *     * Tapping anywhere on the (1,1) quadrant triggers opening of the Expanded Overlay.
 *
 * Expanded State (Full-Screen Overlay):
 *   - High-fidelity Xiaomi native blur (MiBlurCompat) and dark scrim backdrop.
 *   - The underlying Control Center main panel fades out smoothly during expansion.
 *   - Symmetrical 2x2 Macro Layout mirroring collapsed state:
 *     * Macro Slot (0,0): Tile 1 expanded to large 2x2 card
 *     * Macro Slot (1,0): Tile 2 expanded to large 2x2 card
 *     * Macro Slot (0,1): Tile 3 expanded to large 2x2 card
 *     * Macro Slot (1,1): 4 circular tiles scaled to standard 1x1 circular tiles with full labels
 *   - Tapping anywhere on the empty blurred backdrop dismisses the folder back into the card.
 *   - Back key presses dismiss the folder.
 *   - Authentic Folme spring physics curve (PathInterpolator(0.33f, 0f, 0.2f, 1f)).
 */
object IosTileGroupFolder {
    private const val TAG = "HyperBounce_IosFolder"

    // Folme authentic spring / morph interpolator
    val FOLME_SPRING_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.20f, 1.0f)

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

            // Measure children
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

            // Layout slots:
            // Slot 0 (0,0): child 0
            // Slot 1 (1,0): child 1
            // Slot 2 (0,1): child 2
            // Slot 3 (1,1): miniClusterView
            var normalIndex = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child === miniClusterView) {
                    val miniLeft = pad + 1 * (colW + gap)
                    val miniTop = pad + 1 * (rowH + gap)
                    child.layout(miniLeft, miniTop, miniLeft + colW, miniTop + rowH)
                    if (child.visibility != View.VISIBLE) child.visibility = View.VISIBLE
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

                if (child.visibility != View.VISIBLE) child.visibility = View.VISIBLE
                if (child.alpha < 1.0f && !ControlCenterConfigHolder.sIsEditMode) child.alpha = 1.0f

                // Style circular tile inside collapsed slot
                styleCollapsedNormalTile(child, density)
            }
        }

        private fun styleCollapsedNormalTile(child: View, density: Float) {
            try {
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

                    if (iconFrame.background == null) {
                        val discBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#38FFFFFF"))
                            setStroke(Math.max(1, (1.5f * density).toInt()), Color.parseColor("#80FFFFFF"))
                        }
                        iconFrame.background = discBg
                    }
                }
            } catch (t: Throwable) {}
        }
    }

    /**
     * Sub-container housing the 4 shrunken mini circular tiles in 2 rows x 2 columns.
     */
    class MiniClusterView(context: Context) : ViewGroup(context) {

        private var downX = 0f
        private var downY = 0f

        init {
            isClickable = true
            isFocusable = true
            clipToOutline = true
            clipChildren = false
            val density = resources.displayMetrics.density
            val radius = 18 * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }

            // Subtle iOS-style translucent container background
            val miniBg = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.parseColor("#1FFFFFFF"))
            }
            background = miniBg

            // Tapping opens the expanded folder overlay
            setOnClickListener {
                val parentCluster = parent as? IosClusterContainer ?: return@setOnClickListener
                openExpandedFolder(parentCluster)
            }
        }

        // Intercept all touches so child tiles never receive direct clicks
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

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)

            val density = resources.displayMetrics.density
            val stdTilePx = (56 * density).toInt()
            val stdSpec = MeasureSpec.makeMeasureSpec(stdTilePx, MeasureSpec.EXACTLY)

            for (i in 0 until childCount) {
                getChildAt(i)?.measure(stdSpec, stdSpec)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val density = resources.displayMetrics.density
            val pad = (4 * density).toInt()
            val gap = (4 * density).toInt()

            val availW = Math.max(0, (r - l) - 2 * pad - gap)
            val subW = Math.max(0, availW / 2)
            val availH = Math.max(0, (b - t) - 2 * pad - gap)
            val subH = Math.max(0, availH / 2)

            val stdTilePx = (56 * density).toInt()
            val targetSize = Math.min(subW, subH)
            val scale = if (stdTilePx > 0) targetSize.toFloat() / stdTilePx.toFloat() else 0.5f

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                val col = i % 2
                val row = i / 2
                val cl = pad + col * (subW + gap)
                val ct = pad + row * (subH + gap)

                child.layout(cl, ct, cl + stdTilePx, ct + stdTilePx)
                child.pivotX = 0f
                child.pivotY = 0f
                child.scaleX = scale
                child.scaleY = scale

                child.isClickable = false
                child.isLongClickable = false
                child.isFocusable = false

                // Style mini tile
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
                    if (iconFrame != null && iconFrame.background == null) {
                        val discBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#38FFFFFF"))
                            setStroke(Math.max(1, (1.5f * density).toInt()), Color.parseColor("#80FFFFFF"))
                        }
                        iconFrame.background = discBg
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
        private var isClosing = false

        init {
            id = 0x7f09999b
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            // 1. Full-screen Scrim / Blur Backdrop
            scrimView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scrimView.setBackgroundColor(Color.parseColor("#80000000"))
            addView(scrimView)

            // Apply authentic Xiaomi MiBlurCompat
            val classLoader = sourceContainer.classLoader ?: context.classLoader
            try {
                val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                setModeMethod.invoke(null, scrimView, 2)
                val setPassWindowBlur = miBlurClass.getMethod("setPassWindowBlurEnabledCompat", View::class.java, Boolean::class.javaPrimitiveType)
                setPassWindowBlur.invoke(null, scrimView, true)
            } catch (t: Throwable) {
                try {
                    val miBlurClass = Class.forName("com.miui.systemui.util.MiBlurCompat", false, classLoader)
                    val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod.invoke(null, scrimView, 2)
                } catch (t2: Throwable) {}
            }

            // Tapping empty blurred space closes the overlay
            scrimView.setOnClickListener {
                dismissOverlay()
            }

            // 2. Centered Card Container (Mirroring 2x2 layout at 2x scale)
            val density = resources.displayMetrics.density
            val cardPadding = (16 * density).toInt()
            val cardCornerRadius = 32 * density

            val cardLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                marginStart = cardPadding
                marginEnd = cardPadding
            }
            cardContainer.layoutParams = cardLp
            cardContainer.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            cardContainer.clipToOutline = true
            cardContainer.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cardCornerRadius)
                }
            }

            // Card background styling
            val cardBg = GradientDrawable().apply {
                cornerRadius = cardCornerRadius
                setColor(Color.parseColor("#331A1A1A"))
                setStroke(Math.max(1, (1.5f * density).toInt()), Color.parseColor("#38FFFFFF"))
            }
            cardContainer.background = cardBg

            // Apply blur mode to card container
            try {
                val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                setModeMethod.invoke(null, cardContainer, 1)
            } catch (t: Throwable) {}

            // Populate expanded 2x2 macro grid
            buildExpandedCardContent()
            addView(cardContainer)
        }

        private fun buildExpandedCardContent() {
            val group = sourceContainer.group ?: return
            val classLoader = sourceContainer.classLoader ?: context.classLoader
            val qsListCtrl = sourceContainer.qsListCtrl
            val density = resources.displayMetrics.density

            val mainPanelController = ControlCenterGrouping.getMainPanelController(qsListCtrl)
                ?: ControlCenterGrouping.getMainPanelController(ControlCenterConfigHolder.sQSListController)

            // Symmetrical 2x2 Macro Layout:
            // Macro Row 1:
            //   - Quadrant (0,0): Tile 1 (Expanded into 2x2 vertical card)
            //   - Quadrant (1,0): Tile 2 (Expanded into 2x2 vertical card)
            // Macro Row 2:
            //   - Quadrant (0,1): Tile 3 (Expanded into 2x2 vertical card)
            //   - Quadrant (1,1): 2x2 sub-grid of Tiles 4, 5, 6, 7 (Normal 1x1 circular tiles with full labels)
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
                    topMargin = (12 * density).toInt()
                }
            }

            val quadHeight = (156 * density).toInt()
            val quadWeightLp = {
                LinearLayout.LayoutParams(0, quadHeight, 1f).apply {
                    marginStart = (6 * density).toInt()
                    marginEnd = (6 * density).toInt()
                }
            }

            // Quadrant (0,0): Tile 1
            if (group.specs.isNotEmpty()) {
                val tile1 = ControlCenterGrouping.inflateTileForType("E", cardContainer, qsListCtrl, classLoader)
                ControlCenterGrouping.adjustCardToVertical(tile1, density, context)
                macroRow1.addView(tile1, quadWeightLp())
                bindTileDirectly(tile1, group.specs[0], mainPanelController, classLoader)
            }

            // Quadrant (1,0): Tile 2
            if (group.specs.size > 1) {
                val tile2 = ControlCenterGrouping.inflateTileForType("E", cardContainer, qsListCtrl, classLoader)
                ControlCenterGrouping.adjustCardToVertical(tile2, density, context)
                macroRow1.addView(tile2, quadWeightLp())
                bindTileDirectly(tile2, group.specs[1], mainPanelController, classLoader)
            }

            // Quadrant (0,1): Tile 3
            if (group.specs.size > 2) {
                val tile3 = ControlCenterGrouping.inflateTileForType("E", cardContainer, qsListCtrl, classLoader)
                ControlCenterGrouping.adjustCardToVertical(tile3, density, context)
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
                    topMargin = (4 * density).toInt()
                }
            }

            val circLp = {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = (2 * density).toInt()
                    marginEnd = (2 * density).toInt()
                }
            }

            for (i in 3 until Math.min(7, group.specs.size)) {
                val spec = group.specs[i]
                val circView = ControlCenterGrouping.inflateTileForType("C", miniSubGrid, qsListCtrl, classLoader)

                // Ensure labels and icons are fully visible in expanded mode
                val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                if (tileLabelId != 0) {
                    val tileLabel = circView.findViewById<TextView>(tileLabelId)
                    tileLabel?.visibility = View.VISIBLE
                    tileLabel?.alpha = 1.0f
                }

                val targetSubRow = if (i < 5) subRow1 else subRow2
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
                                vm.invoke(view, curState, true, false)
                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(view, curState, classLoader)
                                break
                            }
                        }
                    }
                }

                // Explicit click handling to ensure toggles always work in expanded view
                view.isClickable = true
                view.isLongClickable = true
                view.isFocusable = true
                view.setOnClickListener {
                    try {
                        val clickM = childRecord.javaClass.getMethod("click", View::class.java)
                        clickM.invoke(childRecord, view)
                    } catch (t: Throwable) {
                        try {
                            val tObj = childRecord.javaClass.getMethod("getTile").invoke(childRecord)
                            tObj?.javaClass?.getMethod("click", View::class.java)?.invoke(tObj, view)
                        } catch (t2: Throwable) {}
                    }
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
                val initialScale = if (cardW > 0) Math.min(1f, sourceW / cardW) else 0.5f

                cardContainer.translationX = deltaX
                cardContainer.translationY = deltaY
                cardContainer.scaleX = initialScale
                cardContainer.scaleY = initialScale

                // Fade out underlying Control Center panel
                findMainPanel()?.animate()?.alpha(0f)?.setDuration(220)?.start()

                // Folme spring animation
                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 320
                    interpolator = FOLME_SPRING_INTERPOLATOR
                    addUpdateListener { va ->
                        val fraction = va.animatedValue as Float
                        scrimView.alpha = fraction
                        cardContainer.alpha = fraction
                        cardContainer.translationX = deltaX * (1f - fraction)
                        cardContainer.translationY = deltaY * (1f - fraction)
                        cardContainer.scaleX = initialScale + (1f - initialScale) * fraction
                        cardContainer.scaleY = initialScale + (1f - initialScale) * fraction
                    }
                }
                anim.start()
            }
        }

        fun dismissOverlay() {
            if (isClosing) return
            isClosing = true

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
            val targetScale = if (cardW > 0) Math.min(1f, sourceW / cardW) else 0.5f

            // Fade in underlying Control Center panel
            findMainPanel()?.animate()?.alpha(1f)?.setDuration(240)?.start()

            val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 260
                interpolator = FOLME_SPRING_INTERPOLATOR
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    scrimView.alpha = fraction
                    cardContainer.alpha = fraction
                    cardContainer.translationX = targetDeltaX * (1f - fraction)
                    cardContainer.translationY = targetDeltaY * (1f - fraction)
                    cardContainer.scaleX = targetScale + (1f - targetScale) * fraction
                    cardContainer.scaleY = targetScale + (1f - targetScale) * fraction
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (parent as? ViewGroup)?.removeView(this@IosFolderExpandedOverlay)
                        // Refresh collapsed group tiles state
                        sourceContainer.classLoader?.let { cl ->
                            ControlCenterGrouping.rebindGroupContainer(
                                sourceContainer.parent as? View ?: sourceContainer,
                                sourceContainer.group,
                                sourceContainer.qsListCtrl,
                                cl,
                                forceRebuild = false
                            )
                        }
                    }
                })
            }
            anim.start()
        }

        private fun findMainPanel(): View? {
            // First search parent hierarchy of sourceContainer
            var curr: View? = sourceContainer
            while (curr != null) {
                val idName = try { curr.resources.getResourceEntryName(curr.id) } catch (t: Throwable) { "" }
                if (idName.contains("main_panel") || curr.javaClass.name.contains("MainPanel")) {
                    return curr
                }
                curr = curr.parent as? View
            }
            // Fallback to searching from root
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
            if (existing != null) return

            val overlay = IosFolderExpandedOverlay(clusterContainer.context, clusterContainer)
            rootWindow.addView(overlay)
            overlay.playOpenAnimation()
            overlay.requestFocus()
        } catch (t: Throwable) {
            Log.e(TAG, "openExpandedFolder error", t)
        }
    }
}
