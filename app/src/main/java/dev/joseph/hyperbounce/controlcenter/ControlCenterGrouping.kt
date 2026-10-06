package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

data class PlacedTile(
    val spec: String,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val type: String
)

data class GroupLayoutResult(
    val rowCount: Int,
    val placedTiles: List<PlacedTile>,
    val layoutType: String
)

class DynamicGroupContainer(context: Context) : ViewGroup(context) {
    var rowCount: Int = 2
    var placedTiles: List<PlacedTile> = emptyList()

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        val density = resources.displayMetrics.density
        val pad = (6 * density).toInt()
        val availW = Math.max(0, w - 2 * pad)
        val gap = (6 * density).toInt()
        val colW = Math.max(0, (availW - gap) / 2)
        val totalGapsY = Math.max(0, rowCount - 1) * gap
        val availH = Math.max(0, h - 2 * pad - totalGapsY)
        val rowH = if (rowCount > 0) Math.max(0, availH / rowCount) else 0

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val pt = if (i < placedTiles.size) placedTiles[i] else null
            val colSpan = pt?.colSpan ?: 1
            val rowSpan = pt?.rowSpan ?: 1

            val childW = if (colSpan >= 2) availW else colW
            val childH = if (rowSpan > 1) (rowSpan * rowH + (rowSpan - 1) * gap) else rowH

            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val density = resources.displayMetrics.density
        val pad = (6 * density).toInt()
        val availW = Math.max(0, (r - l) - 2 * pad)
        val gap = (6 * density).toInt()
        val colW = Math.max(0, (availW - gap) / 2)
        val totalGapsY = Math.max(0, rowCount - 1) * gap
        val availH = Math.max(0, (b - t) - 2 * pad - totalGapsY)
        val rowH = if (rowCount > 0) Math.max(0, availH / rowCount) else 0

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val pt = if (i < placedTiles.size) placedTiles[i] else null
            val col = pt?.col ?: (i % 2)
            val row = pt?.row ?: (i / 2)
            val colSpan = pt?.colSpan ?: 1
            val rowSpan = pt?.rowSpan ?: 1

            val childLeft = pad + col * (colW + gap)
            val childTop = pad + row * (rowH + gap)
            val childW = if (colSpan >= 2) availW else colW
            val childH = if (rowSpan > 1) (rowSpan * rowH + (rowSpan - 1) * gap) else rowH

            child.layout(childLeft, childTop, childLeft + childW, childTop + childH)

            if (child.visibility != View.VISIBLE) {
                child.visibility = View.VISIBLE
            }
            if (child.alpha < 1.0f && !ControlCenterConfigHolder.sIsEditMode) {
                child.alpha = 1.0f
            }
        }
    }
}

object ControlCenterGrouping {
    private const val TAG = "HyperBounce_CC_Group"

    fun computeGroupLayout(specs: List<String>, tileSizesList: List<String>): GroupLayoutResult? {
        if (specs.size < 2 || specs.size != tileSizesList.size) return null

        // Specialized iOS 7-tile cluster (3 normal 1x1 + 4 shrunken mini tiles in slot (1,1))
        if (specs.size == 7 && tileSizesList.all { it == "C" }) {
            val placed = mutableListOf<PlacedTile>()
            placed.add(PlacedTile(specs[0], 0, 0, 1, 1, "C"))
            placed.add(PlacedTile(specs[1], 1, 0, 1, 1, "C"))
            placed.add(PlacedTile(specs[2], 0, 1, 1, 1, "C"))
            for (i in 3 until 7) {
                placed.add(PlacedTile(specs[i], 1, 1, 1, 1, "C"))
            }
            return GroupLayoutResult(2, placed, "7_IOS_CLUSTER")
        }

        val grid = Array(6) { arrayOfNulls<String>(2) }
        val placed = mutableListOf<PlacedTile>()

        for (i in specs.indices) {
            val spec = specs[i]
            val type = tileSizesList[i]
            val colSpan = if (type == "E") 2 else 1
            val rowSpan = if (type == "V") 2 else 1

            var startR = -1
            var startC = -1
            findLoop@ for (r in 0 until 6) {
                for (c in 0 until 2) {
                    if (grid[r][c] == null) {
                        startR = r
                        startC = c
                        break@findLoop
                    }
                }
            }
            if (startR == -1) return null

            if (startC + colSpan > 2) return null
            if (startR + rowSpan > 6) return null

            for (dr in 0 until rowSpan) {
                for (dc in 0 until colSpan) {
                    if (grid[startR + dr][startC + dc] != null) return null
                }
            }

            for (dr in 0 until rowSpan) {
                for (dc in 0 until colSpan) {
                    grid[startR + dr][startC + dc] = spec
                }
            }

            placed.add(PlacedTile(spec, startC, startR, colSpan, rowSpan, type))
        }

        val h = placed.maxOf { it.row + it.rowSpan }
        if (h !in 1..6) return null

        for (r in 0 until h) {
            for (c in 0 until 2) {
                if (grid[r][c] == null) return null
            }
        }

        val layoutType = when {
            h == 2 && specs.size == 4 && tileSizesList.all { it == "C" } -> "4_CIRCULAR"
            h == 1 && specs.size == 2 && tileSizesList.all { it == "C" } -> "2_CIRCULAR_HORIZ"
            h == 2 && specs.size == 2 && tileSizesList.all { it == "E" } -> "2_EXPANDED"
            h == 2 && specs.size == 2 && tileSizesList.all { it == "V" } -> "2_VERTICAL"
            h == 2 && specs.size == 3 && tileSizesList[0] == "E" && tileSizesList[1] == "C" && tileSizesList[2] == "C" -> "1_EXP_2_CIRC"
            h == 2 && specs.size == 3 && tileSizesList[0] == "V" && tileSizesList[1] == "C" && tileSizesList[2] == "C" -> "1_VERT_2_CIRC"
            else -> "DYNAMIC_${h}R"
        }

        return GroupLayoutResult(h, placed, layoutType)
    }

    fun computeGroupLayoutForGroup(group: TileGroup): GroupLayoutResult? {
        val sizes = if (group.tileSizes.isNotEmpty()) {
            group.tileSizes.split(",")
        } else {
            val inferred = mutableListOf<String>()
            val effectiveType = group.getEffectiveLayoutType()
            when (effectiveType) {
                "2_EXPANDED" -> for (s in group.specs) inferred.add("E")
                "2_VERTICAL" -> for (s in group.specs) inferred.add("V")
                "1_EXP_2_CIRC" -> {
                    inferred.add("E")
                    for (i in 1 until group.specs.size) inferred.add("C")
                }
                "1_VERT_2_CIRC" -> {
                    inferred.add("V")
                    for (i in 1 until group.specs.size) inferred.add("C")
                }
                else -> {
                    for (s in group.specs) {
                        val isCard = ("wifi".equals(s, ignoreCase = true) || "cell".equals(s, ignoreCase = true))
                        val isCircCard = isCard && ControlCenterConfigHolder.sCircularCardSpecs.contains(s)
                        val isExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(s) || (isCard && !isCircCard && !ControlCenterConfigHolder.sVerticalTileSpecs.contains(s) && !ControlCenterConfigHolder.sLargeTileSpecs.contains(s))
                        val isVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(s)
                        when {
                            isExp -> inferred.add("E")
                            isVert -> inferred.add("V")
                            else -> inferred.add("C")
                        }
                    }
                }
            }
            inferred
        }
        return computeGroupLayout(group.specs, sizes)
    }

    fun createDynamicGroupViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader, rowCount: Int): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density

            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            try {
                val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                var cardIconCtor: Constructor<*>? = null
                for (c in cardIconViewClass.declaredConstructors) {
                    if (c.parameterTypes.size == 5) {
                        cardIconCtor = c
                        break
                    }
                }
                if (cardIconCtor == null) {
                    cardIconCtor = cardIconViewClass.declaredConstructors[0]
                }
                cardIconCtor.isAccessible = true
                val cardIconView = if (cardIconCtor.parameterTypes.size == 5) {
                    cardIconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                } else {
                    cardIconCtor.newInstance(root.context, sysUIContext)
                }
                val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
                initCardMethod.invoke(root, cardIconView)
            } catch (t: Throwable) {
                Log.e(TAG, "QSCardItemView init error", t)
            }

            root.removeAllViews()

            val res1RowId = ctx.resources.getIdentifier("control_center_universal_1_row_size", "dimen", ctx.packageName)
            val unitHeight = if (res1RowId != 0) ctx.resources.getDimensionPixelSize(res1RowId) else (76 * density).toInt()
            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
            val gapY = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (10 * density).toInt()

            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val cardH = if (rowCount == 2 && res2RowsId != 0) {
                ctx.resources.getDimensionPixelSize(res2RowsId)
            } else {
                rowCount * unitHeight + (rowCount - 1) * gapY
            }

            val rlp = root.layoutParams
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                rlp.height = cardH
                root.layoutParams = rlp
            } else {
                root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)
            }

            val wrapper = FrameLayout(ctx).apply {
                id = 0x7f099996
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val container = DynamicGroupContainer(ctx).apply {
                id = 0x7f099995
                this.rowCount = rowCount
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            wrapper.addView(container)

            val disassembleIndicator = ImageView(ctx).apply {
                id = 0x7f099998
                val dSize = (26 * density).toInt()
                val dlp = FrameLayout.LayoutParams(dSize, dSize).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val dMargin = (6 * density).toInt()
                    setMargins(dMargin, dMargin, 0, 0)
                }
                layoutParams = dlp
                setImageDrawable(SelectCircleDrawable())
                visibility = View.GONE
            }
            wrapper.addView(disassembleIndicator)

            root.addView(wrapper)

            container.isClickable = false
            container.isFocusable = false
            root.isClickable = false
            root.isFocusable = false

            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createDynamicGroupViewHolder error", t)
        }
        return null
    }

    fun inflateTileForType(type: String, parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): View {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        val activeCtrl = qsListCtrl ?: ControlCenterConfigHolder.sQSListController
        val sysUIContextField = activeCtrl?.javaClass?.getDeclaredField("sysUIContext")
        sysUIContextField?.isAccessible = true
        val sysUIContext = (sysUIContextField?.get(activeCtrl) as? Context) ?: ctx

        return when (type) {
            "E" -> inflateChildCardView(ctx, sysUIContext, classLoader)
            "V" -> {
                val v = inflateChildCardView(ctx, sysUIContext, classLoader)
                adjustCardToVertical(v, density, ctx)
                v
            }
            else -> {
                var blurEnabled = false
                try {
                    val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                    val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                    blurEnabled = getBlurM.invoke(null, ctx) as Boolean
                } catch (t: Throwable) {}
                inflateChildTileView(ctx, sysUIContext, classLoader, blurEnabled, density, parent)
            }
        }
    }

    fun getViewHolderItemView(holder: Any?): View? {
        if (holder == null) return null
        var clazz: Class<*>? = holder.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField("itemView")
                f.isAccessible = true
                return f.get(holder) as? View
            } catch (t: Throwable) {}
            clazz = clazz.superclass
        }
        return null
    }

    fun getAllChildTiles(parent: ViewGroup): List<View> {
        val list = mutableListOf<View>()
        fun findTiles(vg: ViewGroup) {
            for (i in 0 until vg.childCount) {
                val c = vg.getChildAt(i) ?: continue
                val cName = c.javaClass.name
                if (cName.contains("QSTileItemView") || cName.contains("QSCardItemView")) {
                    list.add(c)
                } else if (c is ViewGroup && c.id != 0x7f099998 && c.id != 0x7f099992) {
                    findTiles(c)
                }
            }
        }
        findTiles(parent)
        return list
    }

    fun getChildTileAt(container: ViewGroup?, index: Int): View? {
        if (container == null) return null
        val tiles = getAllChildTiles(container)
        if (index in tiles.indices) return tiles[index]
        return null
    }

    fun getMainPanelController(qsListCtrl: Any?): Any? {
        if (qsListCtrl == null) return null
        return try {
            val mainPanelControllerField = qsListCtrl.javaClass.getDeclaredField("mainPanelController")
            mainPanelControllerField.isAccessible = true
            val mainPanelCtrlLazy = mainPanelControllerField.get(qsListCtrl)
            val getMethod = mainPanelCtrlLazy?.javaClass?.getMethod("get")
            getMethod?.invoke(mainPanelCtrlLazy)
        } catch (t: Throwable) { null }
    }

    fun createSubHolderForView(childView: View, mainPanelController: Any?, classLoader: ClassLoader): Any? {
        try {
            val mpc = mainPanelController ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)
            val isCard = childView.javaClass.name.contains("QSCardItemView")
            if (isCard) {
                if (mpc != null) {
                    try {
                        val setMainCtrlMethod = childView.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childView, mpc)
                    } catch (t: Throwable) {}
                }
                val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
                for (c in cardHolderClass.declaredConstructors) {
                    if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(childView.javaClass)) {
                        c.isAccessible = true
                        return c.newInstance(childView)
                    }
                }
                if (cardHolderClass.declaredConstructors.isNotEmpty()) {
                    val c = cardHolderClass.declaredConstructors[0]
                    c.isAccessible = true
                    return c.newInstance(childView)
                }
            } else {
                if (mpc != null) {
                    try {
                        val setMainCtrlMethod = childView.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childView, mpc)
                    } catch (t: Throwable) {}
                }
                val subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader)
                for (c in subHolderClass.declaredConstructors) {
                    if (c.parameterTypes.size == 2) {
                        c.isAccessible = true
                        return c.newInstance(childView, mpc)
                    }
                }
                if (subHolderClass.declaredConstructors.isNotEmpty()) {
                    val c = subHolderClass.declaredConstructors[0]
                    c.isAccessible = true
                    return c.newInstance(childView)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createSubHolderForView error", t)
        }
        return null
    }

    fun attachSubHolder(holder: Any?) {
        if (holder == null) return
        var sc: Class<*>? = holder.javaClass
        while (sc != null) {
            try {
                val af = sc.getDeclaredField("attached")
                af.isAccessible = true
                af.setBoolean(holder, true)
            } catch (t: Throwable) {}
            try {
                val mpaField = sc.getDeclaredField("mainPanelAdapter")
                mpaField.isAccessible = true
                if (ControlCenterConfigHolder.sMainPanelAdapter != null) {
                    mpaField.set(holder, ControlCenterConfigHolder.sMainPanelAdapter)
                }
            } catch (t: Throwable) {}
            try {
                val fcField = sc.getDeclaredField("frameCallback")
                fcField.isAccessible = true
                if (ControlCenterConfigHolder.sFrameCallback != null) {
                    fcField.set(holder, ControlCenterConfigHolder.sFrameCallback)
                }
            } catch (t: Throwable) {}
            sc = sc.superclass
        }
    }

    fun inflateChildCardView(ctx: Context, sysUIContext: Context, classLoader: ClassLoader): ViewGroup {
        val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
        val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
        val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), null, false)
        val getRootMethod = cardBindingClass.getMethod("getRoot")
        val root = getRootMethod.invoke(binding) as ViewGroup

        val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
        var cardIconCtor: Constructor<*>? = null
        for (c in cardIconViewClass.declaredConstructors) {
            if (c.parameterTypes.size == 5) {
                cardIconCtor = c
                break
            }
        }
        if (cardIconCtor == null) {
            cardIconCtor = cardIconViewClass.declaredConstructors[0]
        }
        cardIconCtor.isAccessible = true
        val cardIconView = if (cardIconCtor.parameterTypes.size == 5) {
            cardIconCtor.newInstance(root.context, sysUIContext, null, 4, null)
        } else {
            cardIconCtor.newInstance(root.context, sysUIContext)
        }
        val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
        initCardMethod.invoke(root, cardIconView)
        return root
    }

    fun inflateChildTileView(ctx: Context, sysUIContext: Context, classLoader: ClassLoader, blurEnabled: Boolean, density: Float, parent: ViewGroup? = null): View {
        val tileLayoutId = ctx.resources.getIdentifier("qs_tile_item_view", "layout", ctx.packageName)
        val childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, parent, false)
        val tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
        var iconCtor: Constructor<*>? = null
        for (c in tileIconViewClass.declaredConstructors) {
            if (c.parameterTypes.size == 5) {
                iconCtor = c
                break
            }
        }
        if (iconCtor == null) iconCtor = tileIconViewClass.declaredConstructors[0]
        iconCtor.isAccessible = true
        val iconView = if (iconCtor.parameterTypes.size == 5) {
            iconCtor.newInstance(ctx, sysUIContext, false, 4, null)
        } else {
            iconCtor.newInstance(ctx, sysUIContext, false)
        }
        val initMethod = childTile.javaClass.getMethod("init", tileIconViewClass)
        initMethod.invoke(childTile, iconView)

        val tileLabelId = ctx.resources.getIdentifier("tile_label", "id", ctx.packageName)
        if (tileLabelId != 0) {
            val tileLabel = childTile.findViewById<View>(tileLabelId)
            tileLabel?.visibility = View.GONE
            tileLabel?.alpha = 0.0f
        }

        val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
        val iconFrame = childTile.findViewById<View>(iconFrameId)
        if (iconFrame != null) {
            val iflp = iconFrame.layoutParams
            if (iflp is FrameLayout.LayoutParams) {
                iflp.gravity = Gravity.CENTER
                iflp.topMargin = 0
                iflp.bottomMargin = 0
                iconFrame.layoutParams = iflp
            }
            iconFrame.background = null
        }
        return childTile
    }

    fun adjustCardToVertical(root: View, density: Float, ctx: Context) {
        try {
            val scaleContentResId = ctx.resources.getIdentifier("scale_content", "id", ctx.packageName)
            val scaleContent = if (scaleContentResId != 0) root.findViewById<LinearLayout>(scaleContentResId) else null
            if (scaleContent != null) {
                scaleContent.orientation = LinearLayout.VERTICAL
                scaleContent.gravity = Gravity.CENTER_HORIZONTAL

                if (scaleContent.childCount > 0) {
                    val iconChild = scaleContent.getChildAt(0)
                    val iconLp = iconChild.layoutParams as? LinearLayout.LayoutParams
                    if (iconLp != null) {
                        iconLp.gravity = Gravity.CENTER_HORIZONTAL
                        iconLp.topMargin = (12 * density).toInt()
                        iconLp.bottomMargin = (4 * density).toInt()
                        iconChild.layoutParams = iconLp
                    }
                }

                val labelContResId = ctx.resources.getIdentifier("label_container", "id", ctx.packageName)
                val labelCont = if (labelContResId != 0) root.findViewById<LinearLayout>(labelContResId) else null
                if (labelCont != null) {
                    val lblLp = labelCont.layoutParams as? LinearLayout.LayoutParams
                    if (lblLp != null) {
                        lblLp.gravity = Gravity.CENTER_HORIZONTAL
                        lblLp.marginStart = (4 * density).toInt()
                        lblLp.marginEnd = (4 * density).toInt()
                        labelCont.layoutParams = lblLp
                    }

                    val titleResId = ctx.resources.getIdentifier("title", "id", ctx.packageName)
                    val titleView = if (titleResId != 0) labelCont.findViewById<TextView>(titleResId) else null
                    titleView?.gravity = Gravity.CENTER

                    val statusResId = ctx.resources.getIdentifier("status", "id", ctx.packageName)
                    val statusView = if (statusResId != 0) labelCont.findViewById<TextView>(statusResId) else null
                    statusView?.gravity = Gravity.CENTER
                }
            }
        } catch (t: Throwable) {}
    }

    fun createGroupRootContainer(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader, isVerticalLayout: Boolean = false): Pair<ViewGroup, ViewGroup> {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density

        val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
        sysUIContextField?.isAccessible = true
        val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

        val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
        val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
        val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

        val getRootMethod = cardBindingClass.getMethod("getRoot")
        val root = getRootMethod.invoke(binding) as ViewGroup

        try {
            val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
            var cardIconCtor: Constructor<*>? = null
            for (c in cardIconViewClass.declaredConstructors) {
                if (c.parameterTypes.size == 5) {
                    cardIconCtor = c
                    break
                }
            }
            if (cardIconCtor == null) {
                cardIconCtor = cardIconViewClass.declaredConstructors[0]
            }
            cardIconCtor.isAccessible = true
            val cardIconView = if (cardIconCtor.parameterTypes.size == 5) {
                cardIconCtor.newInstance(root.context, sysUIContext, null, 4, null)
            } else {
                cardIconCtor.newInstance(root.context, sysUIContext)
            }

            val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
            initCardMethod.invoke(root, cardIconView)
        } catch (t: Throwable) {
            Log.e(TAG, "QSCardItemView init error", t)
        }

        root.removeAllViews()

        val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
        val cardH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
        val rlp = root.layoutParams
        if (rlp != null) {
            rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
            rlp.height = cardH
            root.layoutParams = rlp
        } else {
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)
        }

        val wrapper = FrameLayout(ctx).apply {
            id = 0x7f099996
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val container = LinearLayout(ctx).apply {
            id = 0x7f099995
            orientation = if (isVerticalLayout) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        wrapper.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val disassembleIndicator = ImageView(ctx).apply {
            id = 0x7f099998
            val dSize = (26 * density).toInt()
            val dlp = FrameLayout.LayoutParams(dSize, dSize).apply {
                gravity = Gravity.TOP or Gravity.START
                val dMargin = (6 * density).toInt()
                setMargins(dMargin, dMargin, 0, 0)
            }
            layoutParams = dlp
            setImageDrawable(SelectCircleDrawable())
            visibility = View.GONE
        }
        wrapper.addView(disassembleIndicator)

        val rotateBtn = ImageView(ctx).apply {
            id = 0x7f099992
            val rSize = (32 * density).toInt()
            val rlp = FrameLayout.LayoutParams(rSize, rSize).apply {
                gravity = Gravity.TOP or Gravity.END
                val rMargin = (6 * density).toInt()
                setMargins(0, rMargin, rMargin, 0)
            }
            layoutParams = rlp
            setImageDrawable(RotateIconDrawable())
            visibility = View.GONE
        }
        wrapper.addView(rotateBtn)

        root.addView(wrapper)

        container.isClickable = false
        container.isFocusable = false
        root.isClickable = false
        root.isFocusable = false

        return Pair(root, container)
    }

    fun wrapInCardViewHolder(root: ViewGroup, classLoader: ClassLoader): Any? {
        applyGroupContainerStyle(root, classLoader)
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            applyGroupContainerStyle(v, classLoader)
        }
        val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
        val qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader)
        val holderCtor = cardHolderClass.getConstructor(qsItemViewClass)
        return holderCtor.newInstance(root)
    }

    fun createGroupedExpandedViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            val (root, container) = createGroupRootContainer(parent, qsListCtrl, classLoader, isVerticalLayout = false)
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(2)

            for (i in 0 until 2) {
                val childCard = inflateChildCardView(ctx, sysUIContext, classLoader)
                val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                    val m = (3 * density).toInt()
                    if (i == 0) setMargins(0, 0, 0, m) else setMargins(0, m, 0, 0)
                }
                container.addView(childCard, clp)
                val holder = createSubHolderForView(childCard, mainPanelController, classLoader)
                attachSubHolder(holder)
                subHolders[i] = holder
            }

            root.setTag(0x7f099993, subHolders)
            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedExpandedViewHolder error", t)
        }
        return null
    }

    fun createGroupedVerticalCardsViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            val (root, container) = createGroupRootContainer(parent, qsListCtrl, classLoader, isVerticalLayout = true)
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(2)

            for (i in 0 until 2) {
                val childCard = inflateChildCardView(ctx, sysUIContext, classLoader)
                adjustCardToVertical(childCard, density, ctx)
                val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    val m = (3 * density).toInt()
                    if (i == 0) setMargins(0, 0, m, 0) else setMargins(m, 0, 0, 0)
                }
                container.addView(childCard, clp)
                val holder = createSubHolderForView(childCard, mainPanelController, classLoader)
                attachSubHolder(holder)
                subHolders[i] = holder
            }

            root.setTag(0x7f099993, subHolders)
            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedVerticalCardsViewHolder error", t)
        }
        return null
    }

    fun createGroupedMixedExpCircViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            val (root, container) = createGroupRootContainer(parent, qsListCtrl, classLoader, isVerticalLayout = false)
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(3)

            val childCard = inflateChildCardView(ctx, sysUIContext, classLoader)
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                val m = (3 * density).toInt()
                setMargins(0, 0, 0, m)
            }
            container.addView(childCard, clp)
            val cardHolder = createSubHolderForView(childCard, mainPanelController, classLoader)
            attachSubHolder(cardHolder)
            subHolders[0] = cardHolder

            var blurEnabled = false
            try {
                val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                blurEnabled = getBlurM.invoke(null, ctx) as Boolean
            } catch (t: Throwable) {}

            val circRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                val m = (3 * density).toInt()
                setMargins(0, m, 0, 0)
            }
            container.addView(circRow, rlp)

            for (i in 0 until 2) {
                val childTile = inflateChildTileView(ctx, sysUIContext, classLoader, blurEnabled, density)
                val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    gravity = Gravity.CENTER
                }
                circRow.addView(childTile, tlp)
                val tileHolder = createSubHolderForView(childTile, mainPanelController, classLoader)
                attachSubHolder(tileHolder)
                subHolders[1 + i] = tileHolder
            }

            root.setTag(0x7f099993, subHolders)
            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedMixedExpCircViewHolder error", t)
        }
        return null
    }

    fun createGroupedMixedVertCircViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            val (root, container) = createGroupRootContainer(parent, qsListCtrl, classLoader, isVerticalLayout = true)
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(3)

            val childCard = inflateChildCardView(ctx, sysUIContext, classLoader)
            adjustCardToVertical(childCard, density, ctx)
            val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                val m = (3 * density).toInt()
                setMargins(0, 0, m, 0)
            }
            container.addView(childCard, clp)
            val cardHolder = createSubHolderForView(childCard, mainPanelController, classLoader)
            attachSubHolder(cardHolder)
            subHolders[0] = cardHolder

            var blurEnabled = false
            try {
                val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                blurEnabled = getBlurM.invoke(null, ctx) as Boolean
            } catch (t: Throwable) {}

            val circCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val rlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                val m = (3 * density).toInt()
                setMargins(m, 0, 0, 0)
            }
            container.addView(circCol, rlp)

            for (i in 0 until 2) {
                val childTile = inflateChildTileView(ctx, sysUIContext, classLoader, blurEnabled, density)
                val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                    gravity = Gravity.CENTER
                }
                circCol.addView(childTile, tlp)
                val tileHolder = createSubHolderForView(childTile, mainPanelController, classLoader)
                attachSubHolder(tileHolder)
                subHolders[1 + i] = tileHolder
            }

            root.setTag(0x7f099993, subHolders)
            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedMixedVertCircViewHolder error", t)
        }
        return null
    }

    fun applyGroupContainerStyle(view: View?, classLoader: ClassLoader, group: TileGroup? = null) {
        if (view == null) return
        try {
            val ctx = view.context
            val density = ctx.resources.displayMetrics.density
            val radiusResId = ctx.resources.getIdentifier("control_center_universal_corner_radius", "dimen", ctx.packageName)
            val cornerRadius = if (radiusResId != 0) ctx.resources.getDimension(radiusResId) else 20f * density

            val g = group ?: (view.getTag(0x7f099994) as? TileGroup)
            if (group != null) {
                view.setTag(0x7f099994, group)
            }

            val isTransparent = g?.isTransparent == true
            val isEdit = ControlCenterConfigHolder.sIsEditMode

            if (isTransparent) {
                try {
                    val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                    val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod.invoke(null, view, 0)
                    val clearBlendMethod = miBlurClass.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                    clearBlendMethod.invoke(null, view)
                } catch (t: Throwable) {}

                try {
                    val commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader)
                    val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                    val clearEffect = commonUtilsClass.getMethod("clearMiBlurBlendEffect", View::class.java)
                    clearEffect.invoke(commonUtils, view)
                } catch (t: Throwable) {}

                if (isEdit) {
                    val strokeW = Math.max(1, (1.5f * density).toInt())
                    val outlineDrawable = GradientDrawable().apply {
                        this.cornerRadius = cornerRadius
                        setColor(Color.TRANSPARENT)
                        setStroke(strokeW, Color.parseColor("#44FFFFFF"))
                    }
                    view.background = outlineDrawable
                    view.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, v.width, v.height, cornerRadius)
                        }
                    }
                    view.clipToOutline = true
                } else {
                    view.background = null
                    view.outlineProvider = null
                    view.clipToOutline = false
                }
                if (view is ViewGroup) {
                    view.clipChildren = true
                }
                view.invalidateOutline()
                return
            }

            // Check if Background Blur is enabled in system
            var blurEnabled = false
            try {
                val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                blurEnabled = getBlurM.invoke(null, ctx) as Boolean
            } catch (t: Throwable) {}

            try {
                val crField = view.javaClass.getDeclaredField("_cornerRadius")
                crField.isAccessible = true
                crField.setFloat(view, cornerRadius)
            } catch (t: Throwable) {}

            view.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, cornerRadius)
                }
            }
            view.clipToOutline = true
            if (view is ViewGroup) {
                view.clipChildren = true
            }
            view.invalidateOutline()

            // The container background should NEVER use the tile advanced texture blend color (CC_TILE_DEFAULT_BLEND_COLORS),
            // because otherwise both the container and inner child tiles have identical colors and blend into a single flat shape.
            // When advanced textures is ON, the container background alone is rendered with normal blur / non-advanced mode:
            // window blur pass-through enabled, but with view blur mode 0, cleared color blend effects, and the normal card background drawable.
            try {
                val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                try {
                    val setPassWindowBlur = miBlurClass.getMethod("setPassWindowBlurEnabledCompat", View::class.java, Boolean::class.javaPrimitiveType)
                    setPassWindowBlur.invoke(null, view, true)
                } catch (t: Throwable) {}

                val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                setModeMethod.invoke(null, view, 0)

                val clearBlendMethod = miBlurClass.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                clearBlendMethod.invoke(null, view)
            } catch (t: Throwable) {}

            try {
                val commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader)
                val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                val clearEffect = commonUtilsClass.getMethod("clearMiBlurBlendEffect", View::class.java)
                clearEffect.invoke(commonUtils, view)
            } catch (t: Throwable) {}

            val colorResId = ctx.resources.getIdentifier("qs_card_disabled_color", "color", ctx.packageName)
            val finalColor = if (colorResId != 0) {
                try {
                    ctx.resources.getColor(colorResId, null)
                } catch (t: Throwable) {
                    Color.parseColor("#66000000")
                }
            } else {
                Color.parseColor("#66000000")
            }

            val cardBg = GradientDrawable().apply {
                this.cornerRadius = cornerRadius
                setColor(finalColor)
            }
            view.background = cardBg
        } catch (t: Throwable) {
            Log.e(TAG, "applyGroupContainerStyle error", t)
        }
    }

    fun isAdvancedTexturesEnabled(context: Context, classLoader: ClassLoader? = null): Boolean {
        try {
            if (android.provider.Settings.Secure.getInt(context.contentResolver, "background_blur_enable", 0) == 1) {
                return true
            }
        } catch (t: Throwable) {}

        try {
            val cl = classLoader ?: context.classLoader
            val hmuClass = Class.forName("miuix.core.util.HyperMaterialUtils", false, cl)
            val isFeatureEnableM = hmuClass.getMethod("isFeatureEnable", Context::class.java)
            val res = isFeatureEnableM.invoke(null, context) as? Boolean
            if (res == true) return true
        } catch (t: Throwable) {}

        try {
            val cl = classLoader ?: context.classLoader
            val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, cl)
            val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
            val res = getBlurM.invoke(null, context) as? Boolean
            if (res == true) return true
        } catch (t: Throwable) {}

        return false
    }

    fun applyNonAdvancedStyleToChildTile(childView: View?, curState: Any?, classLoader: ClassLoader) {
        if (childView == null) return
        try {
            val ctx = childView.context
            val density = ctx.resources.displayMetrics.density
            val radiusResId = ctx.resources.getIdentifier("control_center_universal_corner_radius", "dimen", ctx.packageName)
            val cornerRadius = if (radiusResId != 0) ctx.resources.getDimension(radiusResId) else 20f * density

            try {
                val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                setModeMethod.invoke(null, childView, 0)

                val clearBlendMethod = miBlurClass.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                clearBlendMethod.invoke(null, childView)
            } catch (t: Throwable) {}

            try {
                val commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader)
                val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                val clearEffect = commonUtilsClass.getMethod("clearMiBlurBlendEffect", View::class.java)
                clearEffect.invoke(commonUtils, childView)
            } catch (t: Throwable) {}

            val resolvedState = curState ?: try {
                val f = childView.javaClass.getDeclaredField("state").apply { isAccessible = true }
                f.get(childView)
            } catch (t: Throwable) {
                try {
                    val f = childView.javaClass.getField("state")
                    f.get(childView)
                } catch (t2: Throwable) { null }
            }

            val stateInt = try {
                val f = resolvedState?.javaClass?.getField("state")
                f?.getInt(resolvedState) ?: 1
            } catch (t: Throwable) { 1 }

            val isCard = childView.javaClass.name.contains("QSCardItemView") || (try {
                val f = childView.javaClass.getDeclaredField("card").apply { isAccessible = true }
                f.getBoolean(childView)
            } catch (t: Throwable) { false })
            if (isCard) {
                val enabledColorResId = ctx.resources.getIdentifier("qs_card_enabled_color", "color", ctx.packageName)
                val disabledColorResId = ctx.resources.getIdentifier("qs_card_disabled_color", "color", ctx.packageName)
                val enabledColor = if (enabledColorResId != 0) {
                    try { ctx.resources.getColor(enabledColorResId, null) } catch (t: Throwable) { Color.parseColor("#ebffffff") }
                } else Color.parseColor("#ebffffff")
                val disabledColor = if (disabledColorResId != 0) {
                    try { ctx.resources.getColor(disabledColorResId, null) } catch (t: Throwable) { Color.parseColor("#66000000") }
                } else Color.parseColor("#66000000")

                val cardBg = GradientDrawable().apply {
                    this.cornerRadius = cornerRadius
                    setColor(if (stateInt == 2) enabledColor else disabledColor)
                }
                childView.background = cardBg
                childView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, cornerRadius)
                    }
                }
                childView.clipToOutline = true
            } else {
                val iconView = try {
                    val f = childView.javaClass.getDeclaredField("icon")
                    f.isAccessible = true
                    f.get(childView) as? View
                } catch (t: Throwable) { childView }

                if (iconView != null) {
                    try {
                        val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                        val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                        setModeMethod.invoke(null, iconView, 0)
                        val clearBlendMethod = miBlurClass.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                        clearBlendMethod.invoke(null, iconView)
                        val commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader)
                        val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                        val clearEffect = commonUtilsClass.getMethod("clearMiBlurBlendEffect", View::class.java)
                        clearEffect.invoke(commonUtils, iconView)
                    } catch (t: Throwable) {}

                    val enabledColorResId = ctx.resources.getIdentifier("qs_enabled_color", "color", ctx.packageName)
                    val disabledColorResId = ctx.resources.getIdentifier("qs_disabled_color", "color", ctx.packageName)
                    val enabledColor = if (enabledColorResId != 0) {
                        try { ctx.resources.getColor(enabledColorResId, null) } catch (t: Throwable) { Color.parseColor("#ebffffff") }
                    } else Color.parseColor("#ebffffff")
                    val disabledColor = if (disabledColorResId != 0) {
                        try { ctx.resources.getColor(disabledColorResId, null) } catch (t: Throwable) { Color.parseColor("#66000000") }
                    } else Color.parseColor("#66000000")

                    val tileBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (stateInt == 2) enabledColor else disabledColor)
                    }
                    val innerIcon = iconView.findViewById<ImageView>(ctx.resources.getIdentifier("icon", "id", ctx.packageName))
                    if (innerIcon != null) {
                        innerIcon.background = tileBg
                    } else {
                        iconView.background = tileBg
                    }
                }

                val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                if (iconFrameId != 0) {
                    val iconFrame = childView.findViewById<View>(iconFrameId)
                    iconFrame?.background = null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyNonAdvancedStyleToChildTile error", t)
        }
    }

    fun rebindGroupContainer(
        anchorView: View,
        group: TileGroup?,
        qsListCtrl: Any?,
        classLoader: ClassLoader,
        forceRebuild: Boolean = false
    ) {
        try {
            val container = anchorView.findViewById<ViewGroup>(0x7f099995) ?: return
            val resolvedGroup = group 
                ?: (anchorView.getTag(0x7f099994) as? TileGroup)
                ?: ControlCenterConfigHolder.isGroupAnchor(anchorView.getTag(0x7f099990) as? String)
                ?: return

            anchorView.setTag(0x7f099994, resolvedGroup)
            applyGroupContainerStyle(anchorView, classLoader, resolvedGroup)

            val ctx = anchorView.context
            val density = ctx.resources.displayMetrics.density
            val rowCount = resolvedGroup.getEffectiveRowCount()
            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin_y", "dimen", ctx.packageName)
            val resUnitId = ctx.resources.getIdentifier("control_center_universal_unit_height", "dimen", ctx.packageName)
            val unitHeight = if (resUnitId != 0) ctx.resources.getDimensionPixelSize(resUnitId) else (78 * density).toInt()
            val gapY = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (10 * density).toInt()
            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val cardH = if (rowCount == 2 && res2RowsId != 0) {
                ctx.resources.getDimensionPixelSize(res2RowsId)
            } else {
                rowCount * unitHeight + (rowCount - 1) * gapY
            }

            val alp = anchorView.layoutParams
            if (alp != null && alp.height != cardH) {
                alp.height = cardH
                anchorView.layoutParams = alp
            }

            val isEdit = ControlCenterConfigHolder.sIsEditMode
            if (!isEdit) {
                for (i in 0 until 4) {
                    val childTile = getChildTileAt(container, i)
                    if (childTile != null) {
                        ControlCenterHook.hideAllCustomBadges(childTile)
                        childTile.isClickable = true
                        childTile.isLongClickable = true
                        childTile.isFocusable = true
                    }
                }
            }

            val activeCtrl = qsListCtrl ?: ControlCenterConfigHolder.sQSListController
            val recordMap = HashMap<String, Any>()
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

            if (container is DynamicGroupContainer) {
                if (forceRebuild || container.childCount == 0 || container.getTag(0x7f099997) != resolvedGroup.id) {
                    container.removeAllViews()
                    container.setTag(0x7f099997, resolvedGroup.id)
                    container.rowCount = rowCount

                    val layoutRes = computeGroupLayoutForGroup(resolvedGroup)
                    if (layoutRes != null) {
                        val isIosCluster = layoutRes.layoutType == "7_IOS_CLUSTER" && resolvedGroup.specs.size == 7
                        if (isIosCluster) {
                            val iosCluster = IosTileGroupFolder.IosClusterContainer(ctx).apply {
                                this.group = resolvedGroup
                                this.classLoader = classLoader
                                this.qsListCtrl = activeCtrl
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            }
                            container.addView(iosCluster)

                            val dynamicSubHolders = arrayOfNulls<Any>(7)
                            val mainPanelController = getMainPanelController(activeCtrl)
                                ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)

                            // First 3 normal tiles
                            for (i in 0 until 3) {
                                val child = inflateTileForType("C", iosCluster, activeCtrl, classLoader)
                                iosCluster.addView(child)
                                val h = createSubHolderForView(child, mainPanelController, classLoader)
                                attachSubHolder(h)
                                dynamicSubHolders[i] = h
                            }

                            // 4 mini tiles inside miniClusterView
                            for (i in 3 until 7) {
                                val child = inflateTileForType("C", iosCluster.miniClusterView, activeCtrl, classLoader)
                                iosCluster.miniClusterView.addView(child)
                                val h = createSubHolderForView(child, mainPanelController, classLoader)
                                attachSubHolder(h)
                                dynamicSubHolders[i] = h
                            }
                            anchorView.setTag(0x7f099993, dynamicSubHolders)
                        } else {
                            container.placedTiles = layoutRes.placedTiles
                            val dynamicSubHolders = arrayOfNulls<Any>(resolvedGroup.specs.size)
                            val mainPanelController = getMainPanelController(activeCtrl)
                                ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)

                            for (i in resolvedGroup.specs.indices) {
                                val pt = layoutRes.placedTiles[i]
                                val child = inflateTileForType(pt.type, container, activeCtrl, classLoader)
                                container.addView(child)
                                val h = createSubHolderForView(child, mainPanelController, classLoader)
                                attachSubHolder(h)
                                dynamicSubHolders[i] = h
                            }
                            anchorView.setTag(0x7f099993, dynamicSubHolders)
                        }
                    }
                    container.requestLayout()
                }
            }

            var subHolders = anchorView.getTag(0x7f099993) as? Array<Any?>
            if (subHolders == null) {
                val count = resolvedGroup.specs.size
                subHolders = arrayOfNulls(count)
                val mainPanelController = getMainPanelController(activeCtrl)
                    ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)

                for (i in 0 until count) {
                    val childTile = getChildTileAt(container, i)
                    if (childTile != null) {
                        val h = createSubHolderForView(childTile, mainPanelController, classLoader)
                        attachSubHolder(h)
                        subHolders[i] = h
                    }
                }
                anchorView.setTag(0x7f099993, subHolders)
            }

            val bindCount = if (subHolders != null) Math.min(subHolders.size, resolvedGroup.specs.size) else resolvedGroup.specs.size
            for (i in 0 until bindCount) {
                val childSpec = resolvedGroup.specs[i]
                val childView = getChildTileAt(container, i)
                val childRecord = recordMap[childSpec]
                val subHolder = if (subHolders != null && i < subHolders.size) subHolders[i] else null

                if (childView != null) {
                    childView.visibility = View.VISIBLE
                    if (!isEdit) {
                        childView.alpha = 1.0f
                    }
                }

                if (childRecord != null && subHolder != null && childView != null) {
                    try {
                        for (shM in subHolder.javaClass.methods) {
                            if (shM.name.startsWith("setItem")) {
                                shM.invoke(subHolder, childRecord)
                                break
                            }
                        }
                        val setHolderMethod = childRecord.javaClass.getMethod("setHolder", Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader))
                        setHolderMethod.invoke(childRecord, subHolder)

                        val onBindMethod = childRecord.javaClass.getMethod("onBindViewHolder")
                        onBindMethod.invoke(childRecord)

                        var vhCls: Class<*>? = subHolder.javaClass
                        while (vhCls != null) {
                            try {
                                val af = vhCls.getDeclaredField("attached")
                                af.isAccessible = true
                                af.setBoolean(subHolder, true)
                                break
                            } catch (t: Throwable) { vhCls = vhCls.superclass }
                        }

                        try {
                            val modeClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController\$Mode", false, classLoader)
                            val currentMode = modeClass.getMethod("valueOf", String::class.java).invoke(null, if (isEdit) "EDIT" else "NORMAL")
                            val updateModeMethod = childRecord.javaClass.getMethod("updateMode", modeClass, Boolean::class.javaPrimitiveType)
                            updateModeMethod.invoke(childRecord, currentMode, false)
                        } catch (t: Throwable) {}

                        try {
                            val addCb = childRecord.javaClass.getMethod("addCallback")
                            addCb.invoke(childRecord)
                        } catch (t: Throwable) {}

                        try {
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
                                    for (vm in childView.javaClass.methods) {
                                        if (vm.name == "updateState" && vm.parameterTypes.size == 3) {
                                            vm.isAccessible = true
                                            vm.invoke(childView, curState, true, false)
                                            applyNonAdvancedStyleToChildTile(childView, curState, classLoader)
                                            break
                                        }
                                    }
                                }
                            }
                        } catch (t: Throwable) {}

                        if (isEdit) {
                            childView.isClickable = false
                            childView.isLongClickable = false
                            childView.isFocusable = false
                        } else {
                            val isIosMiniTile = resolvedGroup.getEffectiveLayoutType() == "7_IOS_CLUSTER" && i >= 3
                            if (isIosMiniTile) {
                                childView.isClickable = false
                                childView.isLongClickable = false
                                childView.isFocusable = false
                            } else {
                                childView.isClickable = true
                                childView.isLongClickable = true
                                childView.isFocusable = true
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "rebindGroupContainer subHolder error: $childSpec", t)
                    }
                }
            }
            container.requestLayout()
        } catch (t: Throwable) {
            Log.e(TAG, "rebindGroupContainer error", t)
        }
    }

    fun updateChildHolderFrame(parentHolder: Any?, childHolder: Any?) {
        if (parentHolder == null || childHolder == null) return
        try {
            val parentClass = parentHolder.javaClass
            val childClass = childHolder.javaClass

            try {
                val getScale = parentClass.getMethod("getHolderScale")
                val setScale = childClass.getMethod("setHolderScale", Float::class.javaPrimitiveType)
                setScale.invoke(childHolder, getScale.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getAlpha = parentClass.getMethod("getHolderAlpha")
                val setAlpha = childClass.getMethod("setHolderAlpha", Float::class.javaPrimitiveType)
                setAlpha.invoke(childHolder, getAlpha.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getTransX = parentClass.getMethod("getHolderTransX")
                val setTransX = childClass.getMethod("setHolderTransX", Float::class.javaPrimitiveType)
                setTransX.invoke(childHolder, getTransX.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getTransY = parentClass.getMethod("getHolderTransY")
                val setTransY = childClass.getMethod("setHolderTransY", Float::class.javaPrimitiveType)
                setTransY.invoke(childHolder, getTransY.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getExpandAlpha = parentClass.getMethod("getExpandAlpha")
                val setExpandAlpha = childClass.getMethod("setExpandAlpha", Float::class.javaPrimitiveType)
                setExpandAlpha.invoke(childHolder, getExpandAlpha.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getExpandScaleRatio = parentClass.getMethod("getExpandScaleRatio")
                val setExpandScaleRatio = childClass.getMethod("setExpandScaleRatio", Float::class.javaPrimitiveType)
                setExpandScaleRatio.invoke(childHolder, getExpandScaleRatio.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getSpreadScale = parentClass.getMethod("getSpreadScale")
                val setSpreadScale = childClass.getMethod("setSpreadScale", Float::class.javaPrimitiveType)
                setSpreadScale.invoke(childHolder, getSpreadScale.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getShrinkX = parentClass.getMethod("getShrinkX")
                val setShrinkX = childClass.getMethod("setShrinkX", Float::class.javaPrimitiveType)
                setShrinkX.invoke(childHolder, getShrinkX.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getShrinkY = parentClass.getMethod("getShrinkY")
                val setShrinkY = childClass.getMethod("setShrinkY", Float::class.javaPrimitiveType)
                setShrinkY.invoke(childHolder, getShrinkY.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getCenterX = parentClass.getMethod("getCenterX\$miui_controlcenter_release")
                val setCenterX = childClass.getMethod("setCenterX\$miui_controlcenter_release", Float::class.javaPrimitiveType)
                setCenterX.invoke(childHolder, getCenterX.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getCenterY = parentClass.getMethod("getCenterY\$miui_controlcenter_release")
                val setCenterY = childClass.getMethod("setCenterY\$miui_controlcenter_release", Float::class.javaPrimitiveType)
                setCenterY.invoke(childHolder, getCenterY.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getShrinkCenterY = parentClass.getMethod("getShrinkCenterY\$miui_controlcenter_release")
                val setShrinkCenterY = childClass.getMethod("setShrinkCenterY\$miui_controlcenter_release", Float::class.javaPrimitiveType)
                setShrinkCenterY.invoke(childHolder, getShrinkCenterY.invoke(parentHolder))
            } catch (t: Throwable) {}

            try {
                val getShrinkExpandTransY = parentClass.getMethod("getShrinkExpandTransY")
                val setShrinkExpandTransY = childClass.getMethod("setShrinkExpandTransY", Float::class.javaPrimitiveType)
                setShrinkExpandTransY.invoke(childHolder, getShrinkExpandTransY.invoke(parentHolder))
            } catch (t: Throwable) {}

            val onFrame = childClass.getMethod("onFrameCallback")
            onFrame.invoke(childHolder)
        } catch (t: Throwable) {}
    }

    fun createCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            var sysUIContext = ctx
            if (qsListCtrl != null) {
                try {
                    val sysUIContextField = qsListCtrl.javaClass.getDeclaredField("sysUIContext")
                    sysUIContextField.isAccessible = true
                    val sc = sysUIContextField.get(qsListCtrl) as? Context
                    if (sc != null) sysUIContext = sc
                } catch (t: Throwable) {}
            }

            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as View

            val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
            var iconCtor: Constructor<*>? = null
            for (c in cardIconViewClass.declaredConstructors) {
                if (c.parameterTypes.size == 5) {
                    iconCtor = c
                    break
                }
            }
            if (iconCtor == null && cardIconViewClass.declaredConstructors.isNotEmpty()) {
                iconCtor = cardIconViewClass.declaredConstructors[0]
            }
            if (iconCtor != null) {
                iconCtor.isAccessible = true
                val iconView = if (iconCtor.parameterTypes.size == 5) {
                    iconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                } else {
                    iconCtor.newInstance(root.context, sysUIContext)
                }

                val initMethod = root.javaClass.getMethod("init", cardIconViewClass)
                initMethod.invoke(root, iconView)
            }

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            for (c in cardHolderClass.declaredConstructors) {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(root.javaClass)) {
                    c.isAccessible = true
                    return c.newInstance(root)
                }
            }
            if (cardHolderClass.declaredConstructors.isNotEmpty()) {
                val c = cardHolderClass.declaredConstructors[0]
                c.isAccessible = true
                return c.newInstance(root)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createCardViewHolder error", t)
        }
        return null
    }

    fun createVerticalSingleCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            var sysUIContext = ctx
            if (qsListCtrl != null) {
                try {
                    val sysUIContextField = qsListCtrl.javaClass.getDeclaredField("sysUIContext")
                    sysUIContextField.isAccessible = true
                    val sc = sysUIContextField.get(qsListCtrl) as? Context
                    if (sc != null) sysUIContext = sc
                } catch (t: Throwable) {}
            }

            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            try {
                val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                var iconCtor: Constructor<*>? = null
                for (c in cardIconViewClass.declaredConstructors) {
                    if (c.parameterTypes.size == 5) {
                        iconCtor = c
                        break
                    }
                }
                if (iconCtor == null && cardIconViewClass.declaredConstructors.isNotEmpty()) {
                    iconCtor = cardIconViewClass.declaredConstructors[0]
                }
                if (iconCtor != null) {
                    iconCtor.isAccessible = true
                    val iconView = if (iconCtor.parameterTypes.size == 5) {
                        iconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                    } else {
                        iconCtor.newInstance(root.context, sysUIContext)
                    }

                    val initMethod = root.javaClass.getMethod("init", cardIconViewClass)
                    initMethod.invoke(root, iconView)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "createVerticalSingleCardViewHolder init error", t)
            }

            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()

            val rlp = root.layoutParams
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                rlp.height = tallH
                root.layoutParams = rlp
            } else {
                root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tallH)
            }

            try {
                val scaleContentResId = ctx.resources.getIdentifier("scale_content", "id", ctx.packageName)
                val scaleContent = if (scaleContentResId != 0) root.findViewById<LinearLayout>(scaleContentResId) else null
                if (scaleContent != null) {
                    scaleContent.orientation = LinearLayout.VERTICAL
                    scaleContent.gravity = Gravity.CENTER_HORIZONTAL

                    if (scaleContent.childCount > 0) {
                        val iconChild = scaleContent.getChildAt(0)
                        val iconLp = iconChild.layoutParams as? LinearLayout.LayoutParams
                        if (iconLp != null) {
                            iconLp.gravity = Gravity.CENTER_HORIZONTAL
                            iconLp.topMargin = (16 * density).toInt()
                            iconLp.bottomMargin = (4 * density).toInt()
                            iconChild.layoutParams = iconLp
                        }
                    }

                    val labelContResId = ctx.resources.getIdentifier("label_container", "id", ctx.packageName)
                    val labelCont = if (labelContResId != 0) root.findViewById<LinearLayout>(labelContResId) else null
                    if (labelCont != null) {
                        val lblLp = labelCont.layoutParams as? LinearLayout.LayoutParams
                        if (lblLp != null) {
                            lblLp.gravity = Gravity.CENTER_HORIZONTAL
                            lblLp.marginStart = (4 * density).toInt()
                            lblLp.marginEnd = (4 * density).toInt()
                            labelCont.layoutParams = lblLp
                        }

                        val titleResId = ctx.resources.getIdentifier("title", "id", ctx.packageName)
                        val titleView = if (titleResId != 0) labelCont.findViewById<TextView>(titleResId) else null
                        titleView?.gravity = Gravity.CENTER

                        val statusResId = ctx.resources.getIdentifier("status", "id", ctx.packageName)
                        val statusView = if (statusResId != 0) labelCont.findViewById<TextView>(statusResId) else null
                        statusView?.gravity = Gravity.CENTER
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "createVerticalSingleCardViewHolder layout adjustment error", t)
            }

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            for (c in cardHolderClass.declaredConstructors) {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(root.javaClass)) {
                    c.isAccessible = true
                    return c.newInstance(root)
                }
            }
            if (cardHolderClass.declaredConstructors.isNotEmpty()) {
                val c = cardHolderClass.declaredConstructors[0]
                c.isAccessible = true
                return c.newInstance(root)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createVerticalSingleCardViewHolder error", t)
        }
        return null
    }

    fun createLargeCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            var sysUIContext = ctx
            if (qsListCtrl != null) {
                try {
                    val sysUIContextField = qsListCtrl.javaClass.getDeclaredField("sysUIContext")
                    sysUIContextField.isAccessible = true
                    val sc = sysUIContextField.get(qsListCtrl) as? Context
                    if (sc != null) sysUIContext = sc
                } catch (t: Throwable) {}
            }

            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            try {
                val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                var iconCtor: Constructor<*>? = null
                for (c in cardIconViewClass.declaredConstructors) {
                    if (c.parameterTypes.size == 5) {
                        iconCtor = c
                        break
                    }
                }
                if (iconCtor == null && cardIconViewClass.declaredConstructors.isNotEmpty()) {
                    iconCtor = cardIconViewClass.declaredConstructors[0]
                }
                if (iconCtor != null) {
                    iconCtor.isAccessible = true
                    val iconView = if (iconCtor.parameterTypes.size == 5) {
                        iconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                    } else {
                        iconCtor.newInstance(root.context, sysUIContext)
                    }

                    val initMethod = root.javaClass.getMethod("init", cardIconViewClass)
                    initMethod.invoke(root, iconView)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "createLargeCardViewHolder init error", t)
            }

            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()

            val rlp = root.layoutParams
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                rlp.height = tallH
                root.layoutParams = rlp
            } else {
                root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tallH)
            }

            try {
                val scaleContentResId = ctx.resources.getIdentifier("scale_content", "id", ctx.packageName)
                val scaleContent = if (scaleContentResId != 0) root.findViewById<LinearLayout>(scaleContentResId) else null
                if (scaleContent != null) {
                    scaleContent.orientation = LinearLayout.VERTICAL
                    scaleContent.gravity = Gravity.CENTER_HORIZONTAL

                    if (scaleContent.childCount > 0) {
                        val iconChild = scaleContent.getChildAt(0)
                        val iconLp = iconChild.layoutParams as? LinearLayout.LayoutParams
                        if (iconLp != null) {
                            iconLp.gravity = Gravity.CENTER_HORIZONTAL
                            iconLp.topMargin = (22 * density).toInt()
                            iconLp.bottomMargin = (6 * density).toInt()
                            iconChild.layoutParams = iconLp
                        }
                    }

                    val labelContResId = ctx.resources.getIdentifier("label_container", "id", ctx.packageName)
                    val labelCont = if (labelContResId != 0) root.findViewById<LinearLayout>(labelContResId) else null
                    if (labelCont != null) {
                        val lblLp = labelCont.layoutParams as? LinearLayout.LayoutParams
                        if (lblLp != null) {
                            lblLp.gravity = Gravity.CENTER_HORIZONTAL
                            lblLp.marginStart = (8 * density).toInt()
                            lblLp.marginEnd = (8 * density).toInt()
                            labelCont.layoutParams = lblLp
                        }

                        val titleResId = ctx.resources.getIdentifier("title", "id", ctx.packageName)
                        val titleView = if (titleResId != 0) labelCont.findViewById<TextView>(titleResId) else null
                        titleView?.gravity = Gravity.CENTER

                        val statusResId = ctx.resources.getIdentifier("status", "id", ctx.packageName)
                        val statusView = if (statusResId != 0) labelCont.findViewById<TextView>(statusResId) else null
                        statusView?.gravity = Gravity.CENTER
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "createLargeCardViewHolder layout adjustment error", t)
            }

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            for (c in cardHolderClass.declaredConstructors) {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(root.javaClass)) {
                    c.isAccessible = true
                    return c.newInstance(root)
                }
            }
            if (cardHolderClass.declaredConstructors.isNotEmpty()) {
                val c = cardHolderClass.declaredConstructors[0]
                c.isAccessible = true
                return c.newInstance(root)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createLargeCardViewHolder error", t)
        }
        return null
    }

    fun createGrouped2TileViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader, isVertical: Boolean): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density

            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            // Inflate genuine QSCardItemView as root
            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            // Initialize QSCardItemView with QSCardItemIconView
            try {
                val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                var cardIconCtor: Constructor<*>? = null
                for (c in cardIconViewClass.declaredConstructors) {
                    if (c.parameterTypes.size == 5) {
                        cardIconCtor = c
                        break
                    }
                }
                if (cardIconCtor == null) {
                    cardIconCtor = cardIconViewClass.declaredConstructors[0]
                }
                cardIconCtor.isAccessible = true
                val cardIconView = if (cardIconCtor.parameterTypes.size == 5) {
                    cardIconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                } else {
                    cardIconCtor.newInstance(root.context, sysUIContext)
                }

                val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
                initCardMethod.invoke(root, cardIconView)
            } catch (t: Throwable) {
                Log.e(TAG, "QSCardItemView init error", t)
            }

            root.removeAllViews()

            val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
            val unitH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId) else (71.5f * density).toInt()
            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()

            val cardH = if (isVertical) tallH else unitH
            val rlp = root.layoutParams
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                rlp.height = cardH
                root.layoutParams = rlp
            } else {
                root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)
            }

            var mainPanelController: Any? = null
            if (qsListCtrl != null) {
                try {
                    val mainPanelControllerField = qsListCtrl.javaClass.getDeclaredField("mainPanelController")
                    mainPanelControllerField.isAccessible = true
                    val mainPanelCtrlLazy = mainPanelControllerField.get(qsListCtrl)
                    val getMethod = mainPanelCtrlLazy?.javaClass?.getMethod("get")
                    mainPanelController = getMethod?.invoke(mainPanelCtrlLazy)
                } catch (t: Throwable) {}
            }

            var blurEnabled = false
            try {
                val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                blurEnabled = getBlurM.invoke(null, ctx) as Boolean
            } catch (t: Throwable) {}

            val wrapper = FrameLayout(ctx).apply {
                id = 0x7f099996
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                clipChildren = false
                clipToPadding = false
            }

            val container = LinearLayout(ctx).apply {
                id = 0x7f099995
                orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                if (isVertical) {
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                } else {
                    val pad = (4 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
            }
            wrapper.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            // Rotate Button for 2-tile containers in Edit Mode
            val rotateBtn = ImageView(ctx).apply {
                id = 0x7f099992
                setImageDrawable(RotateIconDrawable())
                val rSize = (22 * density).toInt()
                val rlpBtn = FrameLayout.LayoutParams(rSize, rSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    val rMargin = (3 * density).toInt()
                    setMargins(0, 0, rMargin, rMargin)
                }
                layoutParams = rlpBtn
                visibility = View.GONE
            }
            wrapper.addView(rotateBtn)

            // Disassemble Selection Indicator
            val disassembleIndicator = ImageView(ctx).apply {
                id = 0x7f099998
                val dSize = (26 * density).toInt()
                val dlp = FrameLayout.LayoutParams(dSize, dSize).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val dMargin = (6 * density).toInt()
                    setMargins(dMargin, dMargin, 0, 0)
                }
                layoutParams = dlp
                setImageDrawable(SelectCircleDrawable())
                visibility = View.GONE
            }
            wrapper.addView(disassembleIndicator)

            root.addView(wrapper)

            // Inflate 2 circular child tiles
            val tileLayoutId = ctx.resources.getIdentifier("qs_tile_item_view", "layout", ctx.packageName)
            val tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
            var iconCtor: Constructor<*>? = null
            for (c in tileIconViewClass.declaredConstructors) {
                if (c.parameterTypes.size == 5) {
                    iconCtor = c
                    break
                }
            }
            if (iconCtor == null) iconCtor = tileIconViewClass.declaredConstructors[0]
            iconCtor.isAccessible = true

            container.isClickable = false
            container.isFocusable = false
            root.isClickable = false
            root.isFocusable = false

            val subHolders = arrayOfNulls<Any>(2)
            var subHolderClass: Class<*>? = null
            var subHolderCtor: Constructor<*>? = null
            try {
                subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader)
                for (c in subHolderClass.declaredConstructors) {
                    if (c.parameterTypes.size == 2) {
                        subHolderCtor = c
                        break
                    }
                }
                if (subHolderCtor == null && subHolderClass.declaredConstructors.isNotEmpty()) {
                    subHolderCtor = subHolderClass.declaredConstructors[0]
                }
                subHolderCtor?.isAccessible = true
            } catch (t: Throwable) {}

            for (i in 0 until 2) {
                val childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, container, false)
                val iconView = if (iconCtor.parameterTypes.size == 5) {
                    iconCtor.newInstance(ctx, sysUIContext, false, 4, null)
                } else {
                    iconCtor.newInstance(ctx, sysUIContext, false)
                }

                val initMethod = childTile.javaClass.getMethod("init", tileIconViewClass)
                initMethod.invoke(childTile, iconView)

                if (isVertical && iconView is View) {
                    iconView.scaleX = 0.92f
                    iconView.scaleY = 0.92f
                }

                val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                val iconFrame = childTile.findViewById<View>(iconFrameId)
                if (iconFrame != null) {
                    val iflp = iconFrame.layoutParams
                    if (iflp is FrameLayout.LayoutParams) {
                        iflp.gravity = Gravity.CENTER
                        iflp.topMargin = 0
                        iflp.bottomMargin = 0
                        iconFrame.layoutParams = iflp
                    }
                    if (blurEnabled) {
                        val discBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#38FFFFFF"))
                            setStroke(Math.max(1, (1.5f * density).toInt()), Color.parseColor("#80FFFFFF"))
                        }
                        iconFrame.background = discBg
                    } else {
                        iconFrame.background = null
                    }
                }

                if (mainPanelController != null) {
                    try {
                        val setMainCtrlMethod = childTile.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childTile, mainPanelController)
                    } catch (t: Throwable) {}
                }

                if (subHolderCtor != null) {
                    try {
                        subHolders[i] = if (subHolderCtor.parameterTypes.size == 2) {
                            subHolderCtor.newInstance(childTile, mainPanelController)
                        } else {
                            subHolderCtor.newInstance(childTile)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed creating QSItemViewHolder for subTile $i", t)
                    }
                }

                if (subHolders[i] != null) {
                    var sc: Class<*>? = subHolders[i]!!.javaClass
                    while (sc != null) {
                        try {
                            val af = sc.getDeclaredField("attached")
                            af.isAccessible = true
                            af.setBoolean(subHolders[i], true)
                        } catch (t: Throwable) {}
                        try {
                            val mpaField = sc.getDeclaredField("mainPanelAdapter")
                            mpaField.isAccessible = true
                            if (ControlCenterConfigHolder.sMainPanelAdapter != null) {
                                mpaField.set(subHolders[i], ControlCenterConfigHolder.sMainPanelAdapter)
                            }
                        } catch (t: Throwable) {}
                        try {
                            val fcField = sc.getDeclaredField("frameCallback")
                            fcField.isAccessible = true
                            if (ControlCenterConfigHolder.sFrameCallback != null) {
                                fcField.set(subHolders[i], ControlCenterConfigHolder.sFrameCallback)
                            }
                        } catch (t: Throwable) {}
                        sc = sc.superclass
                    }
                }

                val tlp = if (isVertical) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
                } else {
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                }
                tlp.gravity = Gravity.CENTER
                container.addView(childTile, tlp)
            }

            root.setTag(0x7f099993, subHolders)

            applyGroupContainerStyle(root, classLoader)
            root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyGroupContainerStyle(v, classLoader)
            }
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    try {
                        val holders = v.getTag(0x7f099993) as? Array<*>
                        if (holders != null) {
                            for (h in holders) {
                                if (h != null) {
                                    try {
                                        val getItemM = h.javaClass.getMethod("getItem")
                                        val rec = getItemM.invoke(h)
                                        if (rec != null) {
                                            val startM = rec.javaClass.getMethod("startListening")
                                            startM.invoke(rec)
                                            val getTileM = rec.javaClass.getMethod("getTile")
                                            val tile = getTileM.invoke(rec)
                                            if (tile != null) {
                                                val setLisM = tile.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                                setLisM.invoke(tile, rec, true)
                                                val refM = tile.javaClass.getMethod("refreshState")
                                                refM.invoke(tile)
                                            }
                                        }
                                    } catch (t: Throwable) {}
                                }
                            }
                        }
                    } catch (t: Throwable) {}
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            val qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader)
            val holderCtor = cardHolderClass.getConstructor(qsItemViewClass)
            return holderCtor.newInstance(root)
        } catch (t: Throwable) {
            Log.e(TAG, "createGrouped2TileViewHolder error", t)
            return null
        }
    }

    fun createGroupedCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density

            val sysUIContextField = qsListCtrl?.javaClass?.getDeclaredField("sysUIContext")
            sysUIContextField?.isAccessible = true
            val sysUIContext = (sysUIContextField?.get(qsListCtrl) as? Context) ?: ctx

            // Inflate genuine QSCardItemView as root
            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            // Initialize QSCardItemView with QSCardItemIconView
            try {
                val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                var cardIconCtor: Constructor<*>? = null
                for (c in cardIconViewClass.declaredConstructors) {
                    if (c.parameterTypes.size == 5) {
                        cardIconCtor = c
                        break
                    }
                }
                if (cardIconCtor == null) {
                    cardIconCtor = cardIconViewClass.declaredConstructors[0]
                }
                cardIconCtor.isAccessible = true
                val cardIconView = if (cardIconCtor.parameterTypes.size == 5) {
                    cardIconCtor.newInstance(root.context, sysUIContext, null, 4, null)
                } else {
                    cardIconCtor.newInstance(root.context, sysUIContext)
                }

                val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
                initCardMethod.invoke(root, cardIconView)
            } catch (t: Throwable) {
                Log.e(TAG, "QSCardItemView init error", t)
            }

            // Remove default card children
            root.removeAllViews()

            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val cardH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
            val rlp = root.layoutParams
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                rlp.height = cardH
                root.layoutParams = rlp
            } else {
                root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)
            }

            var mainPanelController: Any? = null
            if (qsListCtrl != null) {
                try {
                    val mainPanelControllerField = qsListCtrl.javaClass.getDeclaredField("mainPanelController")
                    mainPanelControllerField.isAccessible = true
                    val mainPanelCtrlLazy = mainPanelControllerField.get(qsListCtrl)
                    val getMethod = mainPanelCtrlLazy?.javaClass?.getMethod("get")
                    mainPanelController = getMethod?.invoke(mainPanelCtrlLazy)
                } catch (t: Throwable) {}
            }

            var blurEnabled = false
            try {
                val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                blurEnabled = getBlurM.invoke(null, ctx) as Boolean
            } catch (t: Throwable) {}

            val wrapper = FrameLayout(ctx).apply {
                id = 0x7f099996
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val container = LinearLayout(ctx).apply {
                id = 0x7f099995
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = (6 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            wrapper.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val disassembleIndicator = ImageView(ctx).apply {
                id = 0x7f099998
                val dSize = (26 * density).toInt()
                val dlp = FrameLayout.LayoutParams(dSize, dSize).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val dMargin = (6 * density).toInt()
                    setMargins(dMargin, dMargin, 0, 0)
                }
                layoutParams = dlp
                setImageDrawable(SelectCircleDrawable())
                visibility = View.GONE
            }
            wrapper.addView(disassembleIndicator)

            root.addView(wrapper)

            val rowTop = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            container.addView(rowTop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f))

            val rowBottom = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            container.addView(rowBottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f))

            val tileLayoutId = ctx.resources.getIdentifier("qs_tile_item_view", "layout", ctx.packageName)
            val tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
            var iconCtor: Constructor<*>? = null
            for (c in tileIconViewClass.declaredConstructors) {
                if (c.parameterTypes.size == 5) {
                    iconCtor = c
                    break
                }
            }
            if (iconCtor == null) iconCtor = tileIconViewClass.declaredConstructors[0]
            iconCtor.isAccessible = true

            container.isClickable = false
            container.isFocusable = false
            root.isClickable = false
            root.isFocusable = false

            val subHolders = arrayOfNulls<Any>(4)
            var subHolderClass: Class<*>? = null
            var subHolderCtor: Constructor<*>? = null
            try {
                subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader)
                for (c in subHolderClass.declaredConstructors) {
                    if (c.parameterTypes.size == 2) {
                        subHolderCtor = c
                        break
                    }
                }
                if (subHolderCtor == null && subHolderClass.declaredConstructors.isNotEmpty()) {
                    subHolderCtor = subHolderClass.declaredConstructors[0]
                }
                subHolderCtor?.isAccessible = true
            } catch (t: Throwable) {}

            for (i in 0 until 4) {
                val targetRow = if (i < 2) rowTop else rowBottom
                val childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, targetRow, false)
                val iconView = if (iconCtor.parameterTypes.size == 5) {
                    iconCtor.newInstance(ctx, sysUIContext, false, 4, null)
                } else {
                    iconCtor.newInstance(ctx, sysUIContext, false)
                }

                val initMethod = childTile.javaClass.getMethod("init", tileIconViewClass)
                initMethod.invoke(childTile, iconView)

                val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                val iconFrame = childTile.findViewById<View>(iconFrameId)
                if (iconFrame != null) {
                    val iflp = iconFrame.layoutParams
                    if (iflp is FrameLayout.LayoutParams) {
                        iflp.gravity = Gravity.CENTER
                        iflp.topMargin = 0
                        iflp.bottomMargin = 0
                        iconFrame.layoutParams = iflp
                    }
                    if (blurEnabled) {
                        val discBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#38FFFFFF"))
                            setStroke(Math.max(1, (1.5f * density).toInt()), Color.parseColor("#80FFFFFF"))
                        }
                        iconFrame.background = discBg
                    } else {
                        iconFrame.background = null
                    }
                }

                if (mainPanelController != null) {
                    try {
                        val setMainCtrlMethod = childTile.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childTile, mainPanelController)
                    } catch (t: Throwable) {}
                }

                if (subHolderCtor != null) {
                    try {
                        subHolders[i] = if (subHolderCtor.parameterTypes.size == 2) {
                            subHolderCtor.newInstance(childTile, mainPanelController)
                        } else {
                            subHolderCtor.newInstance(childTile)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed creating QSItemViewHolder for subTile $i", t)
                    }
                }

                if (subHolders[i] != null) {
                    var sc: Class<*>? = subHolders[i]!!.javaClass
                    while (sc != null) {
                        try {
                            val af = sc.getDeclaredField("attached")
                            af.isAccessible = true
                            af.setBoolean(subHolders[i], true)
                        } catch (t: Throwable) {}
                        try {
                            val mpaField = sc.getDeclaredField("mainPanelAdapter")
                            mpaField.isAccessible = true
                            if (ControlCenterConfigHolder.sMainPanelAdapter != null) {
                                mpaField.set(subHolders[i], ControlCenterConfigHolder.sMainPanelAdapter)
                            }
                        } catch (t: Throwable) {}
                        try {
                            val fcField = sc.getDeclaredField("frameCallback")
                            fcField.isAccessible = true
                            if (ControlCenterConfigHolder.sFrameCallback != null) {
                                fcField.set(subHolders[i], ControlCenterConfigHolder.sFrameCallback)
                            }
                        } catch (t: Throwable) {}
                        sc = sc.superclass
                    }
                }

                val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                tlp.gravity = Gravity.CENTER
                targetRow.addView(childTile, tlp)
            }

            root.setTag(0x7f099993, subHolders)

            applyGroupContainerStyle(root, classLoader)
            root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyGroupContainerStyle(v, classLoader)
            }

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            val qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader)
            val holderCtor = cardHolderClass.getConstructor(qsItemViewClass)
            return holderCtor.newInstance(root)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedCardViewHolder error", t)
        }
        return null
    }

    fun ensureGroupButtonInjected(controller: Any?, classLoader: ClassLoader) {
        if (controller == null) return
        try {
            val getViewMethod = controller.javaClass.getMethod("getView")
            val root = getViewMethod.invoke(controller) as? View ?: return

            val ctx = root.context
            val density = ctx.resources.displayMetrics.density

            val headerId = ctx.resources.getIdentifier("customize_header", "id", ctx.packageName)
            val completeBtnId = ctx.resources.getIdentifier("complete_button", "id", ctx.packageName)
            val titleId = ctx.resources.getIdentifier("customize_title", "id", ctx.packageName)

            val customizeHeader = root.findViewById<ViewGroup>(headerId) ?: return
            val completeButton = root.findViewById<View>(completeBtnId) ?: return
            val customizeTitle = root.findViewById<View>(titleId)

            if (completeButton.getTag(0x7f099996) == null) {
                completeButton.setTag(0x7f099996, true)
                completeButton.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.post {
                            ControlCenterConfigHolder.onExitEditMode(root, v.context)
                        }
                    }
                    false
                }
            }

            val groupTag = 0x7f099997
            val disassembleTag = 0x7f099994

            var groupButton: ImageView? = customizeHeader.findViewById(groupTag)
            var disassembleButton: ImageView? = customizeHeader.findViewById(disassembleTag)

            val btnSize = if (completeButton.layoutParams != null && completeButton.layoutParams.height > 0) {
                completeButton.layoutParams.height
            } else {
                (40 * density).toInt()
            }

            if (groupButton == null) {
                groupButton = ImageView(ctx).apply {
                    id = groupTag
                    setImageDrawable(GroupGridDrawable(ControlCenterConfigHolder.sIsGroupSelectMode))
                }

                var clp: ViewGroup.LayoutParams? = null
                try {
                    val genMethod = customizeHeader.javaClass.getMethod("generateDefaultLayoutParams")
                    genMethod.isAccessible = true
                    clp = genMethod.invoke(customizeHeader) as? ViewGroup.LayoutParams
                } catch (t: Throwable) {
                    try {
                        val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, classLoader)
                        clp = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(btnSize, btnSize) as ViewGroup.LayoutParams
                    } catch (t2: Throwable) {
                        clp = ViewGroup.MarginLayoutParams(btnSize, btnSize)
                    }
                }

                if (clp != null) {
                    clp.width = btnSize
                    clp.height = btnSize
                    try {
                        clp.javaClass.getField("endToStart").setInt(clp, completeButton.id)
                        clp.javaClass.getField("topToTop").setInt(clp, completeButton.id)
                        clp.javaClass.getField("bottomToBottom").setInt(clp, completeButton.id)
                        clp.javaClass.getField("rightMargin").setInt(clp, (8 * density).toInt())
                    } catch (t: Throwable) {}
                }

                try {
                    val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                    val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod.invoke(null, groupButton, 1)

                    val tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader)
                    val tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null)
                    val getBlendMethod = tokenClass.getMethod("getCC_EDIT_BUTTON_BLEND_COLORS")
                    val blendColors = getBlendMethod.invoke(tokenInst)
                    val setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View::class.java, blendColors.javaClass)
                    setBlendMethod.invoke(null, groupButton, blendColors)
                } catch (t: Throwable) {}

                val gRadius = btnSize / 2f
                groupButton.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, gRadius)
                    }
                }
                groupButton.clipToOutline = true

                try {
                    val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                    val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                    val bOpened = getBlurM.invoke(null, ctx) as Boolean
                    if (!bOpened) {
                        val gBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#33808080"))
                        }
                        groupButton.background = gBg
                    }
                } catch (t: Throwable) {}

                try {
                    val attachTouchMethod = controller.javaClass.getMethod("attachTouchTarget", View::class.java)
                    attachTouchMethod.invoke(controller, groupButton)
                } catch (t: Throwable) {}

                customizeHeader.addView(groupButton, clp)
            }

            if (disassembleButton == null) {
                disassembleButton = ImageView(ctx).apply {
                    id = disassembleTag
                    setImageDrawable(UngroupHeaderDrawable(ControlCenterConfigHolder.sIsDisassembleMode))
                }

                var dlp: ViewGroup.LayoutParams? = null
                try {
                    val genMethod = customizeHeader.javaClass.getMethod("generateDefaultLayoutParams")
                    genMethod.isAccessible = true
                    dlp = genMethod.invoke(customizeHeader) as? ViewGroup.LayoutParams
                } catch (t: Throwable) {
                    try {
                        val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, classLoader)
                        dlp = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(btnSize, btnSize) as ViewGroup.LayoutParams
                    } catch (t2: Throwable) {
                        dlp = ViewGroup.MarginLayoutParams(btnSize, btnSize)
                    }
                }

                if (dlp != null) {
                    dlp.width = btnSize
                    dlp.height = btnSize
                    try {
                        dlp.javaClass.getField("endToStart").setInt(dlp, groupButton.id)
                        dlp.javaClass.getField("topToTop").setInt(dlp, completeButton.id)
                        dlp.javaClass.getField("bottomToBottom").setInt(dlp, completeButton.id)
                        dlp.javaClass.getField("rightMargin").setInt(dlp, (8 * density).toInt())
                    } catch (t: Throwable) {}
                }

                if (customizeTitle?.layoutParams != null) {
                    try {
                        customizeTitle.layoutParams.javaClass.getField("endToStart").setInt(customizeTitle.layoutParams, disassembleButton.id)
                    } catch (t: Throwable) {}
                }

                try {
                    val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                    val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod.invoke(null, disassembleButton, 1)

                    val tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader)
                    val tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null)
                    val getBlendMethod = tokenClass.getMethod("getCC_EDIT_BUTTON_BLEND_COLORS")
                    val blendColors = getBlendMethod.invoke(tokenInst)
                    val setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View::class.java, blendColors.javaClass)
                    setBlendMethod.invoke(null, disassembleButton, blendColors)
                } catch (t: Throwable) {}

                val dRadius = btnSize / 2f
                disassembleButton.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, dRadius)
                    }
                }
                disassembleButton.clipToOutline = true

                try {
                    val ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
                    val getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context::class.java)
                    val bOpened = getBlurM.invoke(null, ctx) as Boolean
                    if (!bOpened) {
                        val dBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#33808080"))
                        }
                        disassembleButton.background = dBg
                    }
                } catch (t: Throwable) {}

                try {
                    val attachTouchMethod = controller.javaClass.getMethod("attachTouchTarget", View::class.java)
                    attachTouchMethod.invoke(controller, disassembleButton)
                } catch (t: Throwable) {}

                customizeHeader.addView(disassembleButton, dlp)
            }

            val finalGroupBtn = groupButton
            val finalDisassembleBtn = disassembleButton

            groupButton.setImageDrawable(GroupGridDrawable(ControlCenterConfigHolder.sIsGroupSelectMode))
            groupButton.visibility = View.VISIBLE
            groupButton.bringToFront()

            disassembleButton.setImageDrawable(UngroupHeaderDrawable(ControlCenterConfigHolder.sIsDisassembleMode))
            disassembleButton.visibility = View.VISIBLE
            disassembleButton.bringToFront()

            groupButton.setOnClickListener {
                try {
                    var structural = false
                    if (!ControlCenterConfigHolder.sIsGroupSelectMode) {
                        ControlCenterConfigHolder.sIsGroupSelectMode = true
                        ControlCenterConfigHolder.sIsDisassembleMode = false
                        ControlCenterConfigHolder.sSelectedDisassembleGroups.clear()
                        ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                        finalGroupBtn.setImageDrawable(GroupGridDrawable(true))
                        finalDisassembleBtn?.setImageDrawable(UngroupHeaderDrawable(false))
                    } else {
                        if (ControlCenterConfigHolder.sSelectedTileSpecs.isNotEmpty()) {
                            val selectedSpecs = ArrayList(ControlCenterConfigHolder.sSelectedTileSpecs)
                            val tileSizesList = mutableListOf<String>()
                            for (spec in selectedSpecs) {
                                val isCard = ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true))
                                val isCircCard = isCard && ControlCenterConfigHolder.sCircularCardSpecs.contains(spec)
                                val isExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) || (isCard && !isCircCard && !ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) && !ControlCenterConfigHolder.sLargeTileSpecs.contains(spec))
                                val isVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                when {
                                    isExp -> tileSizesList.add("E")
                                    isVert -> tileSizesList.add("V")
                                    else -> tileSizesList.add("C")
                                }
                            }

                            val layoutResult = computeGroupLayout(selectedSpecs, tileSizesList)
                            if (layoutResult != null) {
                                val groupId = "group_" + System.currentTimeMillis()
                                for (spec in selectedSpecs) {
                                    ControlCenterConfigHolder.sExpandedTileSpecs.remove(spec)
                                    ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                                    ControlCenterConfigHolder.sLargeTileSpecs.remove(spec)
                                }
                                val isVert = layoutResult.layoutType == "2_CIRCULAR_VERT" || layoutResult.layoutType == "2_VERTICAL" || layoutResult.layoutType == "1_VERT_2_CIRC"
                                val cardSpec = if (selectedSpecs.isNotEmpty() && tileSizesList[0] != "C") selectedSpecs[0] else ""
                                val tileSizesStr = tileSizesList.joinToString(",")
                                val newGroup = TileGroup(
                                    id = groupId,
                                    specs = selectedSpecs,
                                    isVertical = isVert,
                                    layoutType = layoutResult.layoutType,
                                    cardSpec = cardSpec,
                                    isTransparent = false,
                                    rowCount = layoutResult.rowCount,
                                    tileSizes = tileSizesStr
                                )
                                ControlCenterConfigHolder.sTileGroups.add(newGroup)
                                ControlCenterFullUnlock.onGroupCreated(groupId, selectedSpecs, ctx)
                                ControlCenterConfigHolder.sIsGroupSelectMode = false
                                ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                                finalGroupBtn.setImageDrawable(GroupGridDrawable(false))
                                ControlCenterConfigHolder.savePriorities(ctx)
                                structural = true
                            } else {
                                android.widget.Toast.makeText(ctx, "Unsupported group", android.widget.Toast.LENGTH_SHORT).show()
                                finalGroupBtn.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                            }
                        } else {
                            ControlCenterConfigHolder.sIsGroupSelectMode = false
                            ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                            finalGroupBtn.setImageDrawable(GroupGridDrawable(false))
                        }
                    }
                    if (ControlCenterConfigHolder.sRecyclerView?.get() == null) {
                        val decor = root.rootView
                        val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
                        for (pIdStr in panelIds) {
                            val rvId = ctx.resources.getIdentifier(pIdStr, "id", ctx.packageName)
                            if (rvId != 0) {
                                val foundRv = decor.findViewById<View>(rvId)
                                if (foundRv != null) {
                                    ControlCenterConfigHolder.sRecyclerView = java.lang.ref.WeakReference(foundRv)
                                    break
                                }
                            }
                        }
                    }
                    ControlCenterConfigHolder.updateAllVisibleTileBadges()
                    if (structural) {
                        ControlCenterConfigHolder.refreshAdapter(root, ctx, true)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "groupButton error", t)
                }
            }

            disassembleButton.setOnClickListener {
                try {
                    var structural = false
                    if (!ControlCenterConfigHolder.sIsDisassembleMode) {
                        ControlCenterConfigHolder.sIsDisassembleMode = true
                        ControlCenterConfigHolder.sIsGroupSelectMode = false
                        ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                        ControlCenterConfigHolder.sSelectedDisassembleGroups.clear()
                        finalDisassembleBtn.setImageDrawable(UngroupHeaderDrawable(true))
                        finalGroupBtn?.setImageDrawable(GroupGridDrawable(false))
                    } else {
                        val toRemove = if (ControlCenterConfigHolder.sSelectedDisassembleGroups.isNotEmpty()) {
                            HashSet(ControlCenterConfigHolder.sSelectedDisassembleGroups)
                        } else if (ControlCenterConfigHolder.sTileGroups.size == 1) {
                            hashSetOf(ControlCenterConfigHolder.sTileGroups[0].id)
                        } else {
                            hashSetOf<String>()
                        }

                        if (toRemove.isNotEmpty()) {
                            val removedGroups = ControlCenterConfigHolder.sTileGroups.filter { toRemove.contains(it.id) }
                            for (rg in removedGroups) {
                                val sizes = if (rg.tileSizes.isNotEmpty()) rg.tileSizes.split(",") else emptyList()
                                if (sizes.size == rg.specs.size) {
                                    for (i in rg.specs.indices) {
                                        val s = rg.specs[i]
                                        when (sizes[i]) {
                                            "E" -> ControlCenterConfigHolder.sExpandedTileSpecs.add(s)
                                            "V" -> ControlCenterConfigHolder.sVerticalTileSpecs.add(s)
                                        }
                                    }
                                } else {
                                    val effectiveType = rg.getEffectiveLayoutType()
                                    when (effectiveType) {
                                        "2_EXPANDED" -> {
                                            for (s in rg.specs) {
                                                ControlCenterConfigHolder.sExpandedTileSpecs.add(s)
                                            }
                                        }
                                        "2_VERTICAL" -> {
                                            for (s in rg.specs) {
                                                ControlCenterConfigHolder.sVerticalTileSpecs.add(s)
                                            }
                                        }
                                        "1_EXP_2_CIRC" -> {
                                            val card = if (rg.cardSpec.isNotEmpty()) rg.cardSpec else rg.specs.firstOrNull()
                                            if (card != null) {
                                                ControlCenterConfigHolder.sExpandedTileSpecs.add(card)
                                            }
                                        }
                                        "1_VERT_2_CIRC" -> {
                                            val card = if (rg.cardSpec.isNotEmpty()) rg.cardSpec else rg.specs.firstOrNull()
                                            if (card != null) {
                                                ControlCenterConfigHolder.sVerticalTileSpecs.add(card)
                                            }
                                        }
                                        else -> {
                                            // Circular tiles remain circular
                                        }
                                    }
                                }
                            }
                            ControlCenterConfigHolder.sTileGroups.removeAll { g -> toRemove.contains(g.id) }
                            ControlCenterFullUnlock.onGroupsDisassembled(removedGroups, ctx)
                            ControlCenterConfigHolder.sSelectedDisassembleGroups.clear()
                            ControlCenterConfigHolder.sIsDisassembleMode = false
                            finalDisassembleBtn.setImageDrawable(UngroupHeaderDrawable(false))
                            ControlCenterConfigHolder.savePriorities(ctx)
                            structural = true
                        } else {
                            ControlCenterConfigHolder.sIsDisassembleMode = false
                            finalDisassembleBtn.setImageDrawable(UngroupHeaderDrawable(false))
                        }
                    }
                    if (ControlCenterConfigHolder.sRecyclerView?.get() == null) {
                        val decor = root.rootView
                        val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
                        for (pIdStr in panelIds) {
                            val rvId = ctx.resources.getIdentifier(pIdStr, "id", ctx.packageName)
                            if (rvId != 0) {
                                val foundRv = decor.findViewById<View>(rvId)
                                if (foundRv != null) {
                                    ControlCenterConfigHolder.sRecyclerView = java.lang.ref.WeakReference(foundRv)
                                    break
                                }
                            }
                        }
                    }
                    ControlCenterConfigHolder.updateAllVisibleTileBadges()
                    if (structural) {
                        ControlCenterConfigHolder.refreshAdapter(root, ctx, true)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "disassembleButton error", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ensureGroupButtonInjected error", t)
        }
    }
}
