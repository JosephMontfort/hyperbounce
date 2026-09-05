package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object ControlCenterGrouping {
    private const val TAG = "HyperBounce_CC_Group"

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

    fun getChildTileAt(container: ViewGroup?, index: Int): View? {
        if (container == null) return null
        if (container.childCount > 0 && container.getChildAt(0) is LinearLayout && (container.getChildAt(0) as LinearLayout).childCount > 0 && !container.getChildAt(0).javaClass.name.contains("QSTileItemView")) {
            val rowIndex = index / 2
            val colIndex = index % 2
            if (rowIndex < container.childCount) {
                val row = container.getChildAt(rowIndex)
                if (row is ViewGroup && colIndex < row.childCount) {
                    return row.getChildAt(colIndex)
                }
            }
            return null
        } else {
            if (index < container.childCount) {
                return container.getChildAt(index)
            }
            return null
        }
    }

    fun applyGroupContainerStyle(view: View?, classLoader: ClassLoader) {
        if (view == null) return
        try {
            val ctx = view.context
            val radiusResId = ctx.resources.getIdentifier("control_center_universal_corner_radius", "dimen", ctx.packageName)
            val cornerRadius = if (radiusResId != 0) ctx.resources.getDimension(radiusResId) else 20f * ctx.resources.displayMetrics.density

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

            if (blurEnabled) {
                view.background = null
                try {
                    val miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
                    try {
                        val setPassWindowBlur = miBlurClass.getMethod("setPassWindowBlurEnabledCompat", View::class.java, Boolean::class.javaPrimitiveType)
                        setPassWindowBlur.invoke(null, view, true)
                    } catch (t: Throwable) {}

                    val setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType)
                    setModeMethod.invoke(null, view, 1)

                    val tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader)
                    val tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null)
                    val getBlendMethod = tokenClass.getMethod("getCC_TILE_DEFAULT_BLEND_COLORS")
                    val blendColors = getBlendMethod.invoke(tokenInst)

                    val setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View::class.java, blendColors.javaClass)
                    setBlendMethod.invoke(null, view, blendColors)
                } catch (t: Throwable) {
                    Log.e(TAG, "applyGroupContainerStyle MiBlur error", t)
                }
            } else {
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

                var cardBg: Drawable? = null
                var bgResId = ctx.resources.getIdentifier("qs_card_background_disabled", "drawable", ctx.packageName)
                if (bgResId == 0) {
                    bgResId = ctx.resources.getIdentifier("qs_background_disabled", "drawable", ctx.packageName)
                }
                if (bgResId != 0) {
                    try {
                        cardBg = ctx.theme.getDrawable(bgResId).mutate()
                    } catch (t: Throwable) {}
                }
                if (cardBg != null) {
                    view.background = cardBg
                } else {
                    val fallbackBg = GradientDrawable().apply {
                        this.cornerRadius = cornerRadius
                        setColor(Color.parseColor("#26808080"))
                    }
                    view.background = fallbackBg
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyGroupContainerStyle error", t)
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

            val resHeightId = ctx.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", ctx.packageName)
            val unitH = if (resHeightId != 0) ctx.resources.getDimensionPixelSize(resHeightId) else (76 * density).toInt()
            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
            val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (10 * density).toInt()
            val tallH = unitH * 2 + gap

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

            val resHeightId = ctx.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", ctx.packageName)
            val unitH = if (resHeightId != 0) ctx.resources.getDimensionPixelSize(resHeightId) else (76 * density).toInt()
            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
            val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (10 * density).toInt()
            val tallH = unitH * 2 + gap

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

            val cardH = (162 * density).toInt()
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
                        if (ControlCenterConfigHolder.sSelectedTileSpecs.size == 4 || ControlCenterConfigHolder.sSelectedTileSpecs.size == 2) {
                            val groupId = "group_" + System.currentTimeMillis()
                            for (spec in ControlCenterConfigHolder.sSelectedTileSpecs) {
                                ControlCenterConfigHolder.sExpandedTileSpecs.remove(spec)
                                ControlCenterConfigHolder.sVerticalTileSpecs.remove(spec)
                            }
                            ControlCenterConfigHolder.sTileGroups.add(TileGroup(groupId, ArrayList(ControlCenterConfigHolder.sSelectedTileSpecs), false))
                            ControlCenterConfigHolder.sIsGroupSelectMode = false
                            ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                            finalGroupBtn.setImageDrawable(GroupGridDrawable(false))
                            ControlCenterConfigHolder.savePriorities(ctx)
                            structural = true
                        } else {
                            ControlCenterConfigHolder.sIsGroupSelectMode = false
                            ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                            finalGroupBtn.setImageDrawable(GroupGridDrawable(false))
                        }
                    }
                    ControlCenterConfigHolder.updateAllVisibleTileBadges()
                    ControlCenterConfigHolder.refreshAdapter(root, ctx, structural)
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
                        if (ControlCenterConfigHolder.sSelectedDisassembleGroups.isNotEmpty()) {
                            val it = ControlCenterConfigHolder.sTileGroups.iterator()
                            while (it.hasNext()) {
                                val g = it.next()
                                if (ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(g.id)) {
                                    ControlCenterConfigHolder.sTileGroups.remove(g)
                                }
                            }
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
                    ControlCenterConfigHolder.updateAllVisibleTileBadges()
                    ControlCenterConfigHolder.refreshAdapter(root, ctx, structural)
                } catch (t: Throwable) {
                    Log.e(TAG, "disassembleButton error", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ensureGroupButtonInjected error", t)
        }
    }
}
