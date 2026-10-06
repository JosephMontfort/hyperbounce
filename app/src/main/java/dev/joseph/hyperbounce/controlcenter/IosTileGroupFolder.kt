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
import java.lang.ref.WeakReference

/**
 * iOS-style 7-Tile Cluster Container and Expanded Overlay for HyperOS Control Center.
 *
 * Collapsed State (2x2 Card in Grid):
 *   - Slot (0,0): Tile 1 (Normal 1x1 circular)
 *   - Slot (0,1): Tile 2 (Normal 1x1 circular)
 *   - Slot (1,0): Tile 3 (Normal 1x1 circular)
 *   - Slot (1,1): Shrunken 4-Tile Sub-Cluster (2x2 sub-grid)
 *     * The 4 shrunken tiles do not trigger tile clicks directly.
 *     * Tapping anywhere on the (1,1) quadrant triggers opening of the Expanded Overlay.
 *
 * Expanded State (Full-Screen Overlay):
 *   - High-fidelity Xiaomi native blur (MiBlurCompat) and dark scrim backdrop.
 *   - The underlying Control Center main panel fades out smoothly during expansion.
 *   - The 3 normal-size tiles expand to larger 2x2 cards.
 *   - The 4 shrunken mini tiles scale up to normal 1x1 circular tiles with labels.
 *   - Tapping anywhere on the empty blurred backdrop dismisses the folder back into the card.
 *   - Back key presses dismiss the folder.
 *   - HyperOS / Folme authentic spring physics curve (zeta=0.85, PathInterpolator(0.33f, 0f, 0.2f, 1f)).
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
        val miniClusterView: MiniClusterView = MiniClusterView(context)

        init {
            // Add miniClusterView as our 4th virtual child
            miniClusterView.id = 0x7f09999a
            addView(miniClusterView)
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

            val availW = Math.max(0, w - 2 * pad)
            val colW = Math.max(0, (availW - gap) / 2)

            val availH = Math.max(0, h - 2 * pad - gap)
            val rowH = Math.max(0, availH / 2)

            val cellWSpec = MeasureSpec.makeMeasureSpec(colW, MeasureSpec.EXACTLY)
            val cellHSpec = MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY)

            // Measure first 3 normal tile children
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child === miniClusterView) continue
                child.measure(cellWSpec, cellHSpec)
            }

            // Measure miniClusterView in slot (1,1)
            miniClusterView.measure(cellWSpec, cellHSpec)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val density = resources.displayMetrics.density
            val pad = (6 * density).toInt()
            val gap = (6 * density).toInt()

            val availW = Math.max(0, (r - l) - 2 * pad)
            val colW = Math.max(0, (availW - gap) / 2)

            val availH = Math.max(0, (b - t) - 2 * pad - gap)
            val rowH = Math.max(0, availH / 2)

            // Slot coordinates:
            // 0: (col 0, row 0)
            // 1: (col 1, row 0)
            // 2: (col 0, row 1)
            // 3: (col 1, row 1) -> miniClusterView
            var normalChildIndex = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child === miniClusterView) continue

                val (col, row) = when (normalChildIndex) {
                    0 -> Pair(0, 0)
                    1 -> Pair(1, 0)
                    2 -> Pair(0, 1)
                    else -> Pair(0, 0)
                }
                normalChildIndex++

                val childLeft = pad + col * (colW + gap)
                val childTop = pad + row * (rowH + gap)
                child.layout(childLeft, childTop, childLeft + colW, childTop + rowH)

                if (child.visibility != View.VISIBLE) child.visibility = View.VISIBLE
                if (child.alpha < 1.0f && !ControlCenterConfigHolder.sIsEditMode) child.alpha = 1.0f
            }

            // Layout miniClusterView at (col 1, row 1)
            val miniLeft = pad + 1 * (colW + gap)
            val miniTop = pad + 1 * (rowH + gap)
            miniClusterView.layout(miniLeft, miniTop, miniLeft + colW, miniTop + rowH)
            if (miniClusterView.visibility != View.VISIBLE) miniClusterView.visibility = View.VISIBLE
        }
    }

    /**
     * Sub-container housing the 4 shrunken mini circular tiles in 2 rows x 2 columns.
     */
    class MiniClusterView(context: Context) : ViewGroup(context) {

        init {
            isClickable = true
            isFocusable = true
            clipToOutline = true
            val density = resources.displayMetrics.density
            val radius = 16 * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }

            // Tapping this mini quadrant opens the expanded folder overlay
            setOnClickListener {
                if (ControlCenterConfigHolder.sIsEditMode) return@setOnClickListener
                val parentCluster = parent as? IosClusterContainer ?: return@setOnClickListener
                openExpandedFolder(parentCluster)
            }
        }

        // Intercept all touch events to guarantee inner mini tiles do not toggle
        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
            return !ControlCenterConfigHolder.sIsEditMode
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)

            val density = resources.displayMetrics.density
            val pad = (4 * density).toInt()
            val gap = (4 * density).toInt()

            val availW = Math.max(0, w - 2 * pad - gap)
            val subW = Math.max(0, availW / 2)
            val availH = Math.max(0, h - 2 * pad - gap)
            val subH = Math.max(0, availH / 2)

            val subWSpec = MeasureSpec.makeMeasureSpec(subW, MeasureSpec.EXACTLY)
            val subHSpec = MeasureSpec.makeMeasureSpec(subH, MeasureSpec.EXACTLY)

            for (i in 0 until childCount) {
                getChildAt(i)?.measure(subWSpec, subHSpec)
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

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                val col = i % 2
                val row = i / 2
                val cl = pad + col * (subW + gap)
                val ct = pad + row * (subH + gap)
                child.layout(cl, ct, cl + subW, ct + subH)
                child.isClickable = false
                child.isLongClickable = false
                child.isFocusable = false
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

            // 1. Scrim / Blur Backdrop
            scrimView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scrimView.setBackgroundColor(Color.parseColor("#77000000"))
            addView(scrimView)

            // Apply Xiaomi MiBlurCompat to the scrim if available
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

            // Tapping empty space outside card closes the overlay
            scrimView.setOnClickListener {
                dismissOverlay()
            }

            // 2. Centered Card Container
            val density = resources.displayMetrics.density
            val cardPadding = (16 * density).toInt()
            val cardCornerRadius = 28 * density

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
                setColor(Color.parseColor("#33FFFFFF"))
            }
            cardContainer.background = cardBg

            // Apply view blur to card container as well
            try {
                val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                setModeMethod.invoke(null, cardContainer, 1)
            } catch (t: Throwable) {}

            // Populate expanded layout inside cardContainer
            buildExpandedCardContent()
            addView(cardContainer)
        }

        private fun buildExpandedCardContent() {
            val group = sourceContainer.group ?: return
            val classLoader = sourceContainer.classLoader ?: context.classLoader
            val qsListCtrl = sourceContainer.qsListCtrl
            val density = resources.displayMetrics.density

            // Expanded Grid:
            // - Top section: 3 normal tiles expanded into 2x2 cards (or standard large cards)
            // - Bottom section: 4 shrunken tiles scaled up to standard 1x1 circular tiles with labels
            val expandedGrid = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            // Row 1: First 2 large cards
            val row1 = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Row 2: 3rd large card + divider or spacer
            val row2 = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            }

            // Row 3: 4 circular tiles in a horizontal row with full labels
            val row3 = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (14 * density).toInt()
                }
            }

            val cardWeightLp = {
                android.widget.LinearLayout.LayoutParams(0, (76 * density).toInt(), 1f).apply {
                    marginEnd = (5 * density).toInt()
                    marginStart = (5 * density).toInt()
                }
            }

            val circWeightLp = {
                android.widget.LinearLayout.LayoutParams(0, (86 * density).toInt(), 1f).apply {
                    marginEnd = (3 * density).toInt()
                    marginStart = (3 * density).toInt()
                }
            }

            val mainPanelController = ControlCenterGrouping.getMainPanelController(qsListCtrl)
                ?: ControlCenterGrouping.getMainPanelController(ControlCenterConfigHolder.sQSListController)

            // Inflate & bind first 3 large cards
            for (i in 0 until Math.min(3, group.specs.size)) {
                val spec = group.specs[i]
                val cardView = ControlCenterGrouping.inflateTileForType("E", cardContainer, qsListCtrl, classLoader)
                val targetRow = if (i < 2) row1 else row2
                targetRow.addView(cardView, cardWeightLp())

                bindTileDirectly(cardView, spec, mainPanelController, classLoader)
            }

            // Inflate & bind remaining 4 circular tiles with full labels visible
            for (i in 3 until Math.min(7, group.specs.size)) {
                val spec = group.specs[i]
                val circView = ControlCenterGrouping.inflateTileForType("C", cardContainer, qsListCtrl, classLoader)

                // Ensure labels are visible in expanded mode
                val tileLabelId = context.resources.getIdentifier("tile_label", "id", context.packageName)
                if (tileLabelId != 0) {
                    val tileLabel = circView.findViewById<View>(tileLabelId)
                    tileLabel?.visibility = View.VISIBLE
                    tileLabel?.alpha = 1.0f
                }

                row3.addView(circView, circWeightLp())
                bindTileDirectly(circView, spec, mainPanelController, classLoader)
            }

            expandedGrid.addView(row1)
            expandedGrid.addView(row2)
            expandedGrid.addView(row3)
            cardContainer.addView(expandedGrid)
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

                view.isClickable = true
                view.isLongClickable = true
                view.isFocusable = true
            } catch (t: Throwable) {
                Log.e(TAG, "bindTileDirectly error: $spec", t)
            }
        }

        fun playOpenAnimation() {
            // Source container position
            val sourceLoc = IntArray(2)
            sourceContainer.getLocationOnScreen(sourceLoc)
            val sourceX = sourceLoc[0].toFloat()
            val sourceY = sourceLoc[1].toFloat()
            val sourceW = sourceContainer.width.toFloat()
            val sourceH = sourceContainer.height.toFloat()

            // Prepare initial state
            scrimView.alpha = 0f
            cardContainer.alpha = 0f

            // Animate after layout pass
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
            val root = rootView ?: return null
            val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "control_center_main_panel")
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
     * Opens the expanded folder overlay on top of the Control Center root window.
     */
    fun openExpandedFolder(clusterContainer: IosClusterContainer) {
        try {
            val rootWindow = clusterContainer.rootView as? ViewGroup ?: return
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
