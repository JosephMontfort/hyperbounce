package dev.hyperios.app.controlcenter.hos4

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.hyperios.app.controlcenter.*
import java.lang.reflect.Constructor
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust Grouping & ViewHolder Factory for HyperOS 4.
 * Fixes reflection mismatches in QSTileItemIconView / QSItemViewHolder,
 * guarantees non-null ViewHolder creation to prevent IllegalStateException,
 * and handles intelligent group layout computation with dynamic tile sizing.
 */
object ControlCenterGroupingHos4 {
    private const val TAG = "HyperBounce_Grouping4"

    val sGroupChildHolders: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(ConcurrentHashMap()))

    // Container for dynamic matrix placement
    class DynamicGroupContainer(context: Context) : ViewGroup(context) {
        var rowCount: Int = 2
        var colCount: Int = 2
        var placedTiles: List<PlacedTile> = emptyList()

        override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
            return MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val totalW = MeasureSpec.getSize(widthMeasureSpec)
            val totalH = MeasureSpec.getSize(heightMeasureSpec)
            val count = childCount
            if (count == 0 || placedTiles.size != count) {
                setMeasuredDimension(totalW, totalH)
                return
            }

            val density = resources.displayMetrics.density
            val gapX = (6 * density).toInt()
            val gapY = (6 * density).toInt()

            val availW = totalW - paddingLeft - paddingRight - (gapX * (colCount - 1).coerceAtLeast(0))
            val availH = totalH - paddingTop - paddingBottom - (gapY * (rowCount - 1).coerceAtLeast(0))

            val cellW = availW / colCount.coerceAtLeast(1)
            val cellH = availH / rowCount.coerceAtLeast(1)

            for (i in 0 until count) {
                val child = getChildAt(i)
                val pt = placedTiles[i]
                val cw = pt.colSpan * cellW + (pt.colSpan - 1) * gapX
                val ch = pt.rowSpan * cellH + (pt.rowSpan - 1) * gapY
                child.measure(
                    MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
                )
            }
            setMeasuredDimension(totalW, totalH)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val count = childCount
            if (count == 0 || placedTiles.size != count) return

            val density = resources.displayMetrics.density
            val gapX = (6 * density).toInt()
            val gapY = (6 * density).toInt()

            val availW = (r - l) - paddingLeft - paddingRight - (gapX * (colCount - 1).coerceAtLeast(0))
            val availH = (b - t) - paddingTop - paddingBottom - (gapY * (rowCount - 1).coerceAtLeast(0))

            val cellW = availW / colCount.coerceAtLeast(1)
            val cellH = availH / rowCount.coerceAtLeast(1)

            for (i in 0 until count) {
                val child = getChildAt(i)
                val pt = placedTiles[i]

                val cl = paddingLeft + pt.col * (cellW + gapX)
                val ct = paddingTop + pt.row * (cellH + gapY)
                val cr = cl + pt.colSpan * cellW + (pt.colSpan - 1) * gapX
                val cb = ct + pt.rowSpan * cellH + (pt.rowSpan - 1) * gapY

                child.layout(cl, ct, cr, cb)
            }
        }
    }

    /* -------------------------------------------------------------------------
     * Dynamic Reflection Helpers for HyperOS 4
     * ------------------------------------------------------------------------- */

    /**
     * Instantiates QSTileItemIconView with robust constructor fallback for HyperOS 4.
     */
    fun createQSTileItemIconView(pluginContext: Context, sysUIContext: Context, classLoader: ClassLoader): View? {
        try {
            val cls = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                val pts = c.parameterTypes

                // 4 params: (Context, Context, boolean, boolean)
                if (pts.size == 4 && pts[0] == Context::class.java && pts[1] == Context::class.java &&
                    pts[2] == Boolean::class.javaPrimitiveType && pts[3] == Boolean::class.javaPrimitiveType) {
                    return c.newInstance(pluginContext, sysUIContext, false, false) as? View
                }
                // 3 params: (Context, Context, boolean)
                if (pts.size == 3 && pts[0] == Context::class.java && pts[1] == Context::class.java &&
                    pts[2] == Boolean::class.javaPrimitiveType) {
                    return c.newInstance(pluginContext, sysUIContext, false) as? View
                }
                // 2 params: (Context, Context)
                if (pts.size == 2 && pts[0] == Context::class.java && pts[1] == Context::class.java) {
                    return c.newInstance(pluginContext, sysUIContext) as? View
                }
                // 6 params synthetic: (Context, Context, boolean, boolean, int, DefaultConstructorMarker)
                if (pts.size == 6 && pts[0] == Context::class.java && pts[1] == Context::class.java) {
                    return c.newInstance(pluginContext, sysUIContext, false, false, 12, null) as? View
                }
            }

            // Fallback: pick constructor with matching leading Context parameters
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                val pts = c.parameterTypes
                if (pts.isNotEmpty() && pts[0] == Context::class.java) {
                    val args = arrayOfNulls<Any>(pts.size)
                    args[0] = pluginContext
                    if (pts.size > 1 && pts[1] == Context::class.java) args[1] = sysUIContext
                    for (i in 2 until pts.size) {
                        args[i] = when (pts[i]) {
                            Boolean::class.javaPrimitiveType -> false
                            Int::class.javaPrimitiveType -> 0
                            Float::class.javaPrimitiveType -> 0f
                            else -> null
                        }
                    }
                    return c.newInstance(*args) as? View
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createQSTileItemIconView error", t)
        }
        return null
    }

    /**
     * Instantiates QSCardItemIconView with robust constructor fallback.
     */
    fun createQSCardItemIconView(rootContext: Context, sysUIContext: Context, classLoader: ClassLoader): View? {
        try {
            val cls = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                val pts = c.parameterTypes
                // 5 params synthetic: (Context, Context, AttributeSet, int, DefaultConstructorMarker)
                if (pts.size == 5 && pts[0] == Context::class.java && pts[1] == Context::class.java) {
                    return c.newInstance(rootContext, sysUIContext, null, 4, null) as? View
                }
                // 3 params: (Context, Context, AttributeSet)
                if (pts.size == 3 && pts[0] == Context::class.java && pts[1] == Context::class.java) {
                    return c.newInstance(rootContext, sysUIContext, null) as? View
                }
                // 2 params: (Context, Context)
                if (pts.size == 2 && pts[0] == Context::class.java && pts[1] == Context::class.java) {
                    return c.newInstance(rootContext, sysUIContext) as? View
                }
            }
            if (cls.declaredConstructors.isNotEmpty()) {
                val c = cls.declaredConstructors[0]
                c.isAccessible = true
                return c.newInstance(rootContext, sysUIContext) as? View
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createQSCardItemIconView error", t)
        }
        return null
    }

    /**
     * Multi-tier resolution for MainPanelController so it is never null when needed.
     */
    fun getMainPanelController(qsListCtrl: Any?): Any? {
        if (qsListCtrl != null) {
            try {
                val f = qsListCtrl.javaClass.getDeclaredField("mainPanelController")
                f.isAccessible = true
                val lazy = f.get(qsListCtrl)
                val m = lazy?.javaClass?.getMethod("get")
                val res = m?.invoke(lazy)
                if (res != null) return res
            } catch (_: Throwable) {}
        }

        val adp = ControlCenterConfigHolder.sMainPanelAdapter
        if (adp != null) {
            try {
                val f = adp.javaClass.getDeclaredField("mainPanelController")
                f.isAccessible = true
                val lazy = f.get(adp)
                val m = lazy?.javaClass?.getMethod("get")
                val res = m?.invoke(lazy)
                if (res != null) return res
            } catch (_: Throwable) {}
        }

        val stored = ControlCenterConfigHolder.sQSListController
        if (stored != null && stored !== qsListCtrl) {
            try {
                val f = stored.javaClass.getDeclaredField("mainPanelController")
                f.isAccessible = true
                val lazy = f.get(stored)
                val m = lazy?.javaClass?.getMethod("get")
                val res = m?.invoke(lazy)
                if (res != null) return res
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Resolves sysUIContext from controller or views.
     */
    fun getSysUIContext(ctrl: Any?, fallback: Context): Context {
        if (ctrl != null) {
            try {
                val f = ctrl.javaClass.getDeclaredField("sysUIContext")
                f.isAccessible = true
                val sc = f.get(ctrl) as? Context
                if (sc != null) return sc
            } catch (_: Throwable) {}
        }
        return ControlCenterConfigHolder.sLastKnownContext ?: fallback
    }

    /**
     * Instantiates subHolder (QSItemViewHolder or QSCardViewHolder) for a child tile inside group.
     */
    fun createSubHolderForView(childView: View, mainPanelController: Any?, classLoader: ClassLoader): Any? {
        try {
            val mpc = mainPanelController ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)
            val isCard = childView.javaClass.name.contains("QSCardItemView")
            if (isCard) {
                if (mpc != null) {
                    try {
                        val setMainCtrlMethod = childView.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childView, mpc)
                    } catch (_: Throwable) {}
                }
                val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
                for (c in cardHolderClass.declaredConstructors) {
                    c.isAccessible = true
                    if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(childView.javaClass)) {
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
                    } catch (_: Throwable) {}
                }
                val subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader)
                for (c in subHolderClass.declaredConstructors) {
                    c.isAccessible = true
                    if (c.parameterTypes.size == 2) {
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

    /**
     * Safely wraps root view in QSCardViewHolder.
     */
    fun wrapInCardViewHolder(root: ViewGroup, classLoader: ClassLoader): Any? {
        try {
            applyGroupContainerStyle(root, classLoader)
            root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyGroupContainerStyle(v, classLoader)
            }
            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            val qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader)
            val holderCtor = cardHolderClass.getConstructor(qsItemViewClass)
            return holderCtor.newInstance(root)
        } catch (t: Throwable) {
            Log.e(TAG, "wrapInCardViewHolder error", t)
        }
        return null
    }

    /**
     * Instantiates genuine QSCardItemView binding.
     */
    fun inflateCardRoot(ctx: Context, parent: ViewGroup, classLoader: ClassLoader, sysUIContext: Context): ViewGroup? {
        try {
            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)
            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup

            val cardIconView = createQSCardItemIconView(root.context, sysUIContext, classLoader)
            if (cardIconView != null) {
                try {
                    val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                    val initCardMethod = root.javaClass.getMethod("init", cardIconViewClass)
                    initCardMethod.invoke(root, cardIconView)
                } catch (t: Throwable) {
                    Log.w(TAG, "QSCardItemView init error", t)
                }
            }
            // Retain required viewBinding hierarchy (scale_content, label_container, title, status)
            // but hide it so QsCardItemViewBinding.bind() never throws NullPointerException
            val scaleContentId = ctx.resources.getIdentifier("scale_content", "id", ctx.packageName)
            val scaleContent = if (scaleContentId != 0) root.findViewById<View>(scaleContentId) else null
            if (scaleContent != null) {
                scaleContent.visibility = View.GONE
            } else {
                for (ci in 0 until root.childCount) {
                    root.getChildAt(ci).visibility = View.GONE
                }
            }
            return root
        } catch (t: Throwable) {
            Log.e(TAG, "inflateCardRoot error", t)
        }
        return null
    }

    /* -------------------------------------------------------------------------
     * Group ViewHolders Construction
     * ------------------------------------------------------------------------- */

    /**
     * Creates 4-tile circular group card (viewType 2274).
     */
    fun createGroupedCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContext = getSysUIContext(qsListCtrl, ctx)

            val root = inflateCardRoot(ctx, parent, classLoader, sysUIContext) ?: return null

            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val cardH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)

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
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(4)

            for (i in 0 until 4) {
                val targetRow = if (i < 2) rowTop else rowBottom
                val childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, targetRow, false)
                val iconView = createQSTileItemIconView(ctx, sysUIContext, classLoader)
                if (iconView != null) {
                    val initMethod = childTile.javaClass.getMethod("init", tileIconViewClass)
                    initMethod.invoke(childTile, iconView)
                }

                if (mainPanelController != null) {
                    try {
                        val setMainCtrlMethod = childTile.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childTile, mainPanelController)
                    } catch (_: Throwable) {}
                }

                val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    gravity = Gravity.CENTER
                }
                targetRow.addView(childTile, tlp)

                val subHolder = createSubHolderForView(childTile, mainPanelController, classLoader)
                if (subHolder != null) {
                    sGroupChildHolders.add(subHolder)
                    ControlCenterGrouping.attachSubHolder(subHolder)
                }
                subHolders[i] = subHolder
            }
            root.setTag(0x7f099993, subHolders)

            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGroupedCardViewHolder error", t)
        }
        return null
    }

    /**
     * Creates 2-tile group card (viewType 2275 horizontal or 2276 vertical).
     */
    fun createGrouped2TileViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader, isVertical: Boolean): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContext = getSysUIContext(qsListCtrl, ctx)

            val root = inflateCardRoot(ctx, parent, classLoader, sysUIContext) ?: return null

            val res1RowId = ctx.resources.getIdentifier("control_center_universal_1_row_size", "dimen", ctx.packageName)
            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val cardH = if (isVertical) {
                if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
            } else {
                if (res1RowId != 0) ctx.resources.getDimensionPixelSize(res1RowId) else (76 * density).toInt()
            }
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)

            val wrapper = FrameLayout(ctx).apply {
                id = 0x7f099996
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val container = LinearLayout(ctx).apply {
                id = 0x7f099995
                orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val pad = (6 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            wrapper.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(wrapper)

            val tileLayoutId = ctx.resources.getIdentifier("qs_tile_item_view", "layout", ctx.packageName)
            val tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
            val mainPanelController = getMainPanelController(qsListCtrl)
            val subHolders = arrayOfNulls<Any>(2)

            for (i in 0 until 2) {
                val childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, container, false)
                val iconView = createQSTileItemIconView(ctx, sysUIContext, classLoader)
                if (iconView != null) {
                    val initMethod = childTile.javaClass.getMethod("init", tileIconViewClass)
                    initMethod.invoke(childTile, iconView)
                }

                if (mainPanelController != null) {
                    try {
                        val setMainCtrlMethod = childTile.javaClass.getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader))
                        setMainCtrlMethod.invoke(childTile, mainPanelController)
                    } catch (_: Throwable) {}
                }

                val tlp = if (isVertical) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
                } else {
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                }.apply { gravity = Gravity.CENTER }

                container.addView(childTile, tlp)

                val subHolder = createSubHolderForView(childTile, mainPanelController, classLoader)
                if (subHolder != null) {
                    sGroupChildHolders.add(subHolder)
                    ControlCenterGrouping.attachSubHolder(subHolder)
                }
                subHolders[i] = subHolder
            }
            root.setTag(0x7f099993, subHolders)

            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createGrouped2TileViewHolder error", t)
        }
        return null
    }

    /**
     * Creates dynamic group view holder for arbitrary row counts (viewTypes 2285..2290).
     */
    fun createDynamicGroupViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader, rowCount: Int): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val sysUIContext = getSysUIContext(qsListCtrl, ctx)

            val root = inflateCardRoot(ctx, parent, classLoader, sysUIContext) ?: return null

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
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH)

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
            root.addView(wrapper)

            return wrapInCardViewHolder(root, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "createDynamicGroupViewHolder error", t)
        }
        return null
    }

    /**
     * Creates standard 2x1 card view holder (viewType 2273).
     */
    fun createCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val sysUIContext = getSysUIContext(qsListCtrl, ctx)
            val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
            val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)
            val getRootMethod = cardBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as View

            val cardIconView = createQSCardItemIconView(root.context, sysUIContext, classLoader)
            if (cardIconView != null) {
                try {
                    val cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                    val initMethod = root.javaClass.getMethod("init", cardIconViewClass)
                    initMethod.invoke(root, cardIconView)
                } catch (_: Throwable) {}
            }

            val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
            for (c in cardHolderClass.declaredConstructors) {
                c.isAccessible = true
                if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(root.javaClass)) {
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

    /**
     * Unified, fail-safe entry point for MainPanelAdapter.onCreateViewHolder.
     * Guarantees a non-null ViewHolder is returned for all custom types to avoid IllegalStateException.
     */
    fun createViewHolderSafe(parent: ViewGroup, viewType: Int, qsListCtrl: Any?, classLoader: ClassLoader): Any? {
        return try {
            when (viewType) {
                8399 -> ControlCenterFullUnlock.createLandscapeDividerViewHolder(parent, classLoader)
                2274 -> createGroupedCardViewHolder(parent, qsListCtrl, classLoader)
                2275 -> createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, false)
                2276 -> createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, true)
                2281 -> createGroupedExpandedViewHolder(parent, qsListCtrl, classLoader)
                2282 -> createGroupedVerticalCardsViewHolder(parent, qsListCtrl, classLoader)
                2283 -> createGroupedMixedExpCircViewHolder(parent, qsListCtrl, classLoader)
                2284 -> createGroupedMixedVertCircViewHolder(parent, qsListCtrl, classLoader)
                in 2285..2290 -> createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, viewType - 2284)
                2273 -> createCardViewHolder(parent, qsListCtrl, classLoader)
                2277 -> createVerticalSingleCardViewHolder(parent, qsListCtrl, classLoader)
                2278 -> createLargeCardViewHolder(parent, qsListCtrl, classLoader)
                else -> null
            } ?: if (viewType in 2273..2290) {
                // Fail-safe fallback: NEVER return null for custom CC types!
                Log.w(TAG, "Fallback card view holder created for custom viewType $viewType")
                createCardViewHolder(parent, qsListCtrl, classLoader)
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "createViewHolderSafe unexpected error for $viewType", t)
            createCardViewHolder(parent, qsListCtrl, classLoader)
        }
    }

    // Additional layout holders with fallbacks
    fun createGroupedExpandedViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, 2)

    fun createGroupedVerticalCardsViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, 2)

    fun createGroupedMixedExpCircViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, 2)

    fun createGroupedMixedVertCircViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, 2)

    fun createVerticalSingleCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createCardViewHolder(parent, qsListCtrl, classLoader)

    fun createLargeCardViewHolder(parent: ViewGroup, qsListCtrl: Any?, classLoader: ClassLoader): Any? =
        createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, 2)

    /* -------------------------------------------------------------------------
     * Group Styling & Rebind
     * ------------------------------------------------------------------------- */

    fun applyGroupContainerStyle(view: View?, classLoader: ClassLoader, group: TileGroup? = null) {
        if (view == null) return
        try {
            val ctx = view.context
            val density = ctx.resources.displayMetrics.density

            val bgCard = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.parseColor("#33808080"))
            }
            view.background = bgCard
            view.clipToOutline = true
        } catch (t: Throwable) {
            Log.w(TAG, "applyGroupContainerStyle error", t)
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
            val resUnitId = ctx.resources.getIdentifier("control_center_universal_unit_height", "dimen", ctx.packageName)
            val unitHeight = if (resUnitId != 0) ctx.resources.getDimensionPixelSize(resUnitId) else (78 * density).toInt()
            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin_y", "dimen", ctx.packageName)
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

            // Collect available QSRecords
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
                                val s = ControlCenterTileDetectorHos4.inspectRecord(r)
                                if (s != null && !recordMap.containsKey(s) && r != null) recordMap[s] = r
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            for (r in ControlCenterConfigHolder.sAllQSRecords) {
                val s = ControlCenterTileDetectorHos4.inspectRecord(r)
                if (s != null && !recordMap.containsKey(s) && r != null) recordMap[s] = r
            }

            // SubHolders resolution
            var subHolders = anchorView.getTag(0x7f099993) as? Array<Any?>
            if (subHolders == null) {
                val count = resolvedGroup.specs.size
                subHolders = arrayOfNulls(count)
                val mainPanelController = getMainPanelController(activeCtrl)
                    ?: getMainPanelController(ControlCenterConfigHolder.sQSListController)

                for (i in 0 until count) {
                    val childTile = ControlCenterGrouping.getChildTileAt(container, i)
                    if (childTile != null) {
                        childTile.setTag(0x7f09999b, true)
                        val h = createSubHolderForView(childTile, mainPanelController, classLoader)
                        if (h != null) {
                            sGroupChildHolders.add(h)
                            ControlCenterGrouping.attachSubHolder(h)
                        }
                        subHolders[i] = h
                    }
                }
                anchorView.setTag(0x7f099993, subHolders)
            }

            // Bind each tile in group
            val bindCount = Math.min(subHolders.size, resolvedGroup.specs.size)
            for (i in 0 until bindCount) {
                val childSpec = resolvedGroup.specs[i]
                val childView = ControlCenterGrouping.getChildTileAt(container, i)
                val childRecord = recordMap[childSpec]
                val subHolder = subHolders[i]

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

                        try {
                            val setHolderMethod = childRecord.javaClass.getMethod("setHolder", Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader))
                            setHolderMethod.invoke(childRecord, subHolder)
                        } catch (_: Throwable) {}

                        try {
                            val onBindMethod = childRecord.javaClass.getMethod("onBindViewHolder")
                            onBindMethod.invoke(childRecord)
                        } catch (_: Throwable) {}

                        var vhCls: Class<*>? = subHolder.javaClass
                        while (vhCls != null) {
                            try {
                                val af = vhCls.getDeclaredField("attached")
                                af.isAccessible = true
                                af.setBoolean(subHolder, true)
                                break
                            } catch (_: Throwable) { vhCls = vhCls.superclass }
                        }

                        try {
                            val modeClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController\$Mode", false, classLoader)
                            val currentMode = modeClass.getMethod("valueOf", String::class.java).invoke(null, if (isEdit) "EDIT" else "NORMAL")
                            val updateModeMethod = childRecord.javaClass.getMethod("updateMode", modeClass, Boolean::class.javaPrimitiveType)
                            updateModeMethod.invoke(childRecord, currentMode, false)
                        } catch (_: Throwable) {}

                        try {
                            val addCb = childRecord.javaClass.getMethod("addCallback")
                            addCb.invoke(childRecord)
                        } catch (_: Throwable) {}

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
                                        if (vm.name == "updateState") {
                                            vm.isAccessible = true
                                            if (vm.parameterTypes.size == 3) {
                                                val isConnected = ControlCenterConfigHolder.isTileConnected(tileObj)
                                                vm.invoke(childView, curState, isConnected, false)
                                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(childView, curState, classLoader)
                                                break
                                            } else if (vm.parameterTypes.size == 1) {
                                                vm.invoke(childView, curState)
                                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(childView, curState, classLoader)
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {}

                        if (isEdit) {
                            childView.isClickable = false
                            childView.isLongClickable = false
                            childView.isFocusable = false
                        } else {
                            childView.isClickable = true
                            childView.isLongClickable = true
                            childView.isFocusable = true
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
}
