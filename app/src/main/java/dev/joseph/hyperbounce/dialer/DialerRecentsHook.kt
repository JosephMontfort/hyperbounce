package dev.joseph.hyperbounce.dialer

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.graphics.PathParser
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure framework-based Xposed hook for com.android.contacts that brings authentic iOS 26-styled
 * fluid swipe gestures to the stock dialer Recents list:
 *  - Swipe Right: Reveals iOS Call Green action, launches stock call on trigger release.
 *  - Swipe Left: Reveals iOS Red Delete action, clicking it invokes Xiaomi stock confirmation
 *    dialog and stock call log deletion logic.
 *
 * Coordinates seamlessly with DirectionViewPager and SpringBackLayout so gestures work both
 * on the home Recents landing page and in search results without screen stretching.
 */
object DialerRecentsHook {

    private const val TAG = "HyperBounce_Dialer"

    // Weak reference to active TwelveKeyDialerFragment instance for invoking stock U4 dialog
    private var sActiveFragment: WeakReference<Any>? = null

    // Touch & Swipe state
    private var openChild: View? = null
    private var openTargetNumber: String? = null
    private var openDisplayName: String? = null

    private var swipingChild: View? = null
    private var swipingItemVM: Any? = null
    private var swipingTargetNumber: String? = null
    private var swipingDisplayName: String? = null

    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false
    private var isDeleteButtonPressed = false
    private var hasTriggeredCallHaptic = false
    private var hasTriggeredDeleteHaptic = false

    // Recursion guard for dispatchTouchEvent
    private var isDispatching = false

    // iOS fluid spring interpolator
    private val iosSpringInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

    // Drawing resources
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Cached drawables / paths
    private var cachedCallDrawable: Drawable? = null
    private var cachedDeleteDrawable: Drawable? = null
    private var fallbackCallPath: Path? = null
    private var fallbackDeletePath: Path? = null

    fun init(module: XposedModule, classLoader: ClassLoader) {
        val prefs = try {
            HyperBouncePrefs(module)
        } catch (_: Throwable) {
            null
        }

        if (prefs != null && (!prefs.moduleEnabled || !prefs.dialerRecentsSwipeEnabled)) {
            Log.d(TAG, "Dialer Recents Swipe is disabled in preferences.")
            return
        }

        Log.i(TAG, "Initializing Dialer Recents Swipe Hook for com.android.contacts...")

        // 1. Hook TwelveKeyDialerFragment lifecycle methods to capture active fragment instance
        try {
            val fragmentClass = Class.forName("com.android.contacts.list.TwelveKeyDialerFragment", false, classLoader)
            for (m: Method in fragmentClass.declaredMethods) {
                if ((m.name == "Y0" || m.name == "onViewCreated") &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == View::class.java &&
                    m.parameterTypes[1] == Bundle::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            if (fragment != null) {
                                sActiveFragment = WeakReference(fragment)
                                Log.i(TAG, "Captured active TwelveKeyDialerFragment instance via ${m.name}")
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in TwelveKeyDialerFragment.${m.name} hook", t)
                        }
                        result
                    }
                    Log.i(TAG, "Hooked TwelveKeyDialerFragment.${m.name} successfully.")
                }

                if (m.name == "onResume" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            if (fragment != null) {
                                sActiveFragment = WeakReference(fragment)
                                Log.d(TAG, "Captured active TwelveKeyDialerFragment instance via onResume")
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.i(TAG, "Hooked TwelveKeyDialerFragment.onResume successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook TwelveKeyDialerFragment", t)
        }

        // 2. Hook DirectionViewPager.onInterceptTouchEvent to block tab-switch hijacking when touching/swiping a call row
        try {
            val dvpClass = Class.forName("com.android.contacts.widget.DirectionViewPager", false, classLoader)
            for (m: Method in dvpClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        if (isSwiping || isDeleteButtonPressed || swipingChild != null) {
                            return@intercept false
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked DirectionViewPager.onInterceptTouchEvent successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DirectionViewPager", t)
        }

        // 3. Hook SpringBackLayout.onInterceptTouchEvent and requestDisallowInterceptTouchEvent
        try {
            val sblClass = Class.forName("miuix.springback.view.SpringBackLayout", false, classLoader)
            for (m: Method in sblClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        if (isSwiping || isDeleteButtonPressed || swipingChild != null) {
                            return@intercept false
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked SpringBackLayout.onInterceptTouchEvent successfully.")
                }

                if (m.name == "requestDisallowInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == java.lang.Boolean.TYPE
                ) {
                    module.hook(m).intercept { chain ->
                        val sbl = chain.thisObject as? ViewGroup
                        val disallow = chain.args[0] as? Boolean ?: false
                        // Force propagate up past SpringBackLayout so DirectionViewPager receives disallow
                        sbl?.parent?.requestDisallowInterceptTouchEvent(disallow)
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked SpringBackLayout.requestDisallowInterceptTouchEvent successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook SpringBackLayout", t)
        }

        // 4. Hook RecyclerView.dispatchTouchEvent across all potential hierarchy classes
        val rvClassNames = listOf(
            "miuix.recyclerview.widget.RecyclerView",
            "androidx.recyclerview.widget.SpringRecyclerView",
            "androidx.recyclerview.widget.RemixRecyclerView",
            "androidx.recyclerview.widget.RecyclerView"
        )

        var dispatchHooked = false
        for (className in rvClassNames) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "dispatchTouchEvent" &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == MotionEvent::class.java
                    ) {
                        module.hook(m).intercept { chain ->
                            val rv = chain.thisObject as? ViewGroup
                            val ev = chain.args[0] as? MotionEvent

                            if (rv != null && ev != null && DialerRecentsConfigHolder.config.enabled) {
                                val handled = handleDispatchTouchEvent(rv, ev, classLoader)
                                if (handled == true) {
                                    return@intercept true
                                }
                            }
                            chain.proceed()
                        }
                        dispatchHooked = true
                        Log.i(TAG, "Hooked $className.dispatchTouchEvent successfully.")
                        break
                    }
                }
                if (dispatchHooked) {
                    break
                }
            } catch (_: Throwable) {}
        }

        // 5. Hook drawChild on base RecyclerView to render iOS Call and Delete action backgrounds
        try {
            val baseRvClass = Class.forName("androidx.recyclerview.widget.RecyclerView", false, classLoader)
            for (m: Method in baseRvClass.declaredMethods) {
                if (m.name == "drawChild" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == Canvas::class.java &&
                    m.parameterTypes[1] == View::class.java &&
                    m.parameterTypes[2] == java.lang.Long.TYPE
                ) {
                    module.hook(m).intercept { chain ->
                        val canvas = chain.args[0] as? Canvas
                        val child = chain.args[1] as? View
                        if (canvas != null && child != null && child.translationX != 0f) {
                            val rv = chain.thisObject as? ViewGroup
                            if (rv != null) {
                                drawSwipeBackground(canvas, child, rv.context)
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked RecyclerView.drawChild successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook RecyclerView.drawChild", t)
        }
    }

    private fun handleDispatchTouchEvent(rv: ViewGroup, ev: MotionEvent, classLoader: ClassLoader): Boolean? {
        if (isDispatching) return null
        isDispatching = true
        try {
            return processDispatchTouchEvent(rv, ev, classLoader)
        } finally {
            isDispatching = false
        }
    }

    private fun processDispatchTouchEvent(rv: ViewGroup, e: MotionEvent, classLoader: ClassLoader): Boolean? {
        val density = rv.resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop
        val callThresholdPx = DialerRecentsConfigHolder.config.callThresholdDp * density
        val deleteWidthPx = DialerRecentsConfigHolder.config.deleteWidthDp * density

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                isSwiping = false
                isDeleteButtonPressed = false
                hasTriggeredCallHaptic = false
                hasTriggeredDeleteHaptic = false

                // 1. Check if user tapped on the revealed Delete button of an open row
                val currentOpen = openChild
                if (currentOpen != null) {
                    val childTop = currentOpen.top.toFloat()
                    val childBottom = currentOpen.bottom.toFloat()
                    val deleteLeft = currentOpen.right - deleteWidthPx
                    val deleteRight = currentOpen.right.toFloat()

                    if (e.y in childTop..childBottom && e.x in deleteLeft..deleteRight) {
                        isDeleteButtonPressed = true
                        swipingChild = currentOpen
                        disallowAllParents(rv, true)
                        Log.d(TAG, "ACTION_DOWN on Delete button")
                        return true
                    } else {
                        // Tapped elsewhere -> smoothly close open row
                        Log.d(TAG, "ACTION_DOWN outside Delete button, closing open row")
                        closeOpenRow(currentOpen, rv)
                        swipingChild = null
                        return null
                    }
                }

                // 2. Check if touch is on a call log child
                val child = findChildUnder(rv, e.x, e.y)
                if (child != null && isCallLogChild(child, rv) && !isAdapterInMultiSelectMode(rv)) {
                    val info = extractCallInfo(child, rv)
                    if (info != null) {
                        swipingChild = child
                        swipingItemVM = info.itemVM
                        swipingTargetNumber = info.targetNumber
                        swipingDisplayName = info.displayName
                        // Proactively disallow ALL ancestor ViewGroups from intercepting
                        disallowAllParents(rv, true)
                        Log.d(TAG, "ACTION_DOWN on call row: ${info.displayName} (${info.targetNumber})")
                    } else {
                        swipingChild = null
                    }
                } else {
                    swipingChild = null
                }
                return null
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDeleteButtonPressed) {
                    disallowAllParents(rv, true)
                    return true
                }

                val child = swipingChild
                if (child != null) {
                    val dx = e.x - downX
                    val dy = e.y - downY

                    if (!isSwiping) {
                        if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                            // User is scrolling vertically -> restore normal scrolling to parents & RecyclerView
                            disallowAllParents(rv, false)
                            swipingChild = null
                            return null
                        }

                        if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.1f) {
                            // User is swiping horizontally -> engage iOS row swipe!
                            isSwiping = true
                            disallowAllParents(rv, true)

                            // Cancel touch on child itemView so click / longClick won't trigger
                            val cancel = MotionEvent.obtain(e)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            child.dispatchTouchEvent(cancel)
                            cancel.recycle()

                            applyDragTranslation(child, dx, callThresholdPx, deleteWidthPx, rv)
                            Log.d(TAG, "Engaged row swipe: dx=$dx")
                            return true
                        }
                    } else {
                        disallowAllParents(rv, true)
                        applyDragTranslation(child, dx, callThresholdPx, deleteWidthPx, rv)
                        return true
                    }
                }
                return null
            }

            MotionEvent.ACTION_UP -> {
                if (isDeleteButtonPressed) {
                    isDeleteButtonPressed = false
                    val target = swipingChild ?: openChild
                    val targetNumber = openTargetNumber ?: swipingTargetNumber
                    val displayName = openDisplayName ?: swipingDisplayName

                    if (target != null) {
                        performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                        closeOpenRow(target, rv)
                        if (targetNumber != null) {
                            Log.i(TAG, "Delete clicked for $displayName ($targetNumber)")
                            val fragment = sActiveFragment?.get()
                            invokeStockDeleteDialogAndLogic(rv.context, fragment, targetNumber, displayName ?: targetNumber, classLoader)
                        }
                    }
                    resetSwipingState()
                    return true
                }

                val target = swipingChild
                if (isSwiping && target != null) {
                    val currentTx = target.translationX
                    Log.d(TAG, "ACTION_UP during swipe: currentTx=$currentTx")

                    if (currentTx > 0f) {
                        // Swiped right -> Call action
                        if (currentTx >= callThresholdPx) {
                            performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                            animateSpring(target, 0f, rv)
                            val itemVM = swipingItemVM
                            val number = swipingTargetNumber
                            if (number != null) {
                                invokeStockCall(target.context, itemVM, number, classLoader)
                            }
                        } else {
                            animateSpring(target, 0f, rv)
                        }
                    } else if (currentTx < 0f) {
                        // Swiped left -> Reveal Delete action
                        if (abs(currentTx) >= deleteWidthPx * 0.40f) {
                            animateSpring(target, -deleteWidthPx, rv)
                            openChild = target
                            openTargetNumber = swipingTargetNumber
                            openDisplayName = swipingDisplayName
                        } else {
                            animateSpring(target, 0f, rv)
                        }
                    } else {
                        animateSpring(target, 0f, rv)
                    }

                    resetSwipingState()
                    return true
                }

                resetSwipingState()
                return null
            }

            MotionEvent.ACTION_CANCEL -> {
                val target = swipingChild
                if (isSwiping && target != null && target != openChild) {
                    animateSpring(target, 0f, rv)
                }
                resetSwipingState()
                return null
            }
        }
        return null
    }

    private fun disallowAllParents(view: View, disallow: Boolean) {
        var p = view.parent
        while (p != null) {
            try {
                p.requestDisallowInterceptTouchEvent(disallow)
            } catch (_: Throwable) {}
            p = p.parent
        }
    }

    private fun applyDragTranslation(
        target: View,
        dx: Float,
        callThresholdPx: Float,
        deleteWidthPx: Float,
        rv: ViewGroup
    ) {
        if (openChild != null && openChild == target) {
            val baseTx = -deleteWidthPx
            val newTx = baseTx + dx
            val clampedTx = if (newTx < -deleteWidthPx) {
                -deleteWidthPx - (abs(newTx) - deleteWidthPx) * 0.22f
            } else {
                newTx
            }
            target.translationX = min(0f, clampedTx)
            rv.invalidate()
            return
        }

        if (dx > 0f) {
            // Swiping right (Call)
            val tx = if (dx > callThresholdPx) {
                callThresholdPx + (dx - callThresholdPx) * 0.40f
            } else {
                dx
            }
            target.translationX = tx

            if (tx >= callThresholdPx && !hasTriggeredCallHaptic) {
                performHaptic(rv, HapticFeedbackConstants.CLOCK_TICK)
                hasTriggeredCallHaptic = true
            } else if (tx < callThresholdPx) {
                hasTriggeredCallHaptic = false
            }
        } else {
            // Swiping left (Delete)
            val absDx = abs(dx)
            val tx = if (absDx > deleteWidthPx) {
                -deleteWidthPx - (absDx - deleteWidthPx) * 0.25f
            } else {
                dx
            }
            target.translationX = tx

            if (abs(tx) >= deleteWidthPx && !hasTriggeredDeleteHaptic) {
                performHaptic(rv, HapticFeedbackConstants.CLOCK_TICK)
                hasTriggeredDeleteHaptic = true
            } else if (abs(tx) < deleteWidthPx) {
                hasTriggeredDeleteHaptic = false
            }
        }
        rv.invalidate()
    }

    private fun resetSwipingState() {
        isSwiping = false
        isDeleteButtonPressed = false
        swipingChild = null
        swipingItemVM = null
        swipingTargetNumber = null
        swipingDisplayName = null
        hasTriggeredCallHaptic = false
        hasTriggeredDeleteHaptic = false
    }

    private fun closeOpenRow(view: View, rv: ViewGroup) {
        animateSpring(view, 0f, rv)
        if (openChild == view) {
            openChild = null
            openTargetNumber = null
            openDisplayName = null
        }
    }

    private fun animateSpring(view: View, targetTx: Float, rv: ViewGroup) {
        val startTx = view.translationX
        if (startTx == targetTx) return
        val animator = ValueAnimator.ofFloat(startTx, targetTx)
        animator.duration = 290L
        animator.interpolator = iosSpringInterpolator
        animator.addUpdateListener { va ->
            view.translationX = va.animatedValue as Float
            rv.invalidate()
        }
        animator.start()
    }

    private fun performHaptic(view: View, feedbackConstant: Int) {
        if (!DialerRecentsConfigHolder.config.hapticsEnabled) return
        try {
            view.performHapticFeedback(feedbackConstant)
        } catch (_: Throwable) {}
    }

    private fun findChildUnder(parent: ViewGroup, x: Float, y: Float): View? {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val tx = child.translationX
                val ty = child.translationY
                if (x >= child.left + tx && x <= child.right + tx &&
                    y >= child.top + ty && y <= child.bottom + ty
                ) {
                    return child
                }
            }
        }
        return null
    }

    private fun isCallLogChild(child: View, rv: ViewGroup): Boolean {
        // Method 1: Check child view content IDs
        val nameId = rv.resources.getIdentifier("name", "id", rv.context.packageName)
        if (nameId != 0 && child.findViewById<View>(nameId) != null) return true

        val contentId = rv.resources.getIdentifier("content_layout", "id", rv.context.packageName)
        if (contentId != 0 && child.findViewById<View>(contentId) != null) return true

        val secActionId = rv.resources.getIdentifier("secondary_action_view", "id", rv.context.packageName)
        if (secActionId != 0 && child.findViewById<View>(secActionId) != null) return true

        // Method 2: Check ViewHolder class name
        try {
            val getChildVHMethod = rv.javaClass.getMethod("getChildViewHolder", View::class.java)
            val vh = getChildVHMethod.invoke(rv, child)
            if (vh != null && vh.javaClass.name.contains("DialerCallVH")) return true
        } catch (_: Throwable) {}

        return false
    }

    private fun isAdapterInMultiSelectMode(rv: ViewGroup): Boolean {
        return try {
            val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
            val adapter = getAdapterMethod.invoke(rv) ?: return false
            val e0Method = adapter.javaClass.getMethod("e0")
            e0Method.invoke(adapter) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private data class CallInfo(val itemVM: Any?, val targetNumber: String, val displayName: String)

    private fun extractCallInfo(child: View, rv: ViewGroup): CallInfo? {
        var itemVM: Any? = null
        var targetNumber: String? = null
        var displayName: String? = null

        // 1. Try to get ViewHolder and DialerItemVM
        try {
            val getChildVHMethod = rv.javaClass.getMethod("getChildViewHolder", View::class.java)
            val vh = getChildVHMethod.invoke(rv, child)
            if (vh != null) {
                var currClass: Class<*>? = vh.javaClass
                while (currClass != null && currClass != Any::class.java) {
                    for (f: Field in currClass.declaredFields) {
                        if (f.type.name.contains("DialerItemVM")) {
                            f.isAccessible = true
                            itemVM = f.get(vh)
                            if (itemVM != null) break
                        }
                    }
                    if (itemVM != null) break
                    currClass = currClass.superclass
                }

                // If not in field, try adapter.K0(position)
                if (itemVM == null) {
                    val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                    val adapter = getAdapterMethod.invoke(rv)
                    val getPosMethod = vh.javaClass.getMethod("getBindingAdapterPosition")
                    val pos = getPosMethod.invoke(vh) as? Int ?: -1
                    if (adapter != null && pos >= 0) {
                        for (m in adapter.javaClass.methods) {
                            if (m.name == "K0" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                                itemVM = m.invoke(adapter, pos)
                                break
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Extract numbers and names from itemVM if available
        if (itemVM != null) {
            for (methodName in listOf("v", "w", "x")) {
                try {
                    val res = itemVM.javaClass.getMethod(methodName).invoke(itemVM) as? String
                    if (!res.isNullOrEmpty()) {
                        targetNumber = res
                        break
                    }
                } catch (_: Throwable) {}
            }
            try {
                displayName = itemVM.javaClass.getMethod("t").invoke(itemVM) as? String
            } catch (_: Throwable) {}
        }

        // 2. Fallback to child TextViews
        if (targetNumber.isNullOrEmpty()) {
            val numId = rv.resources.getIdentifier("dialer_number", "id", rv.context.packageName)
            if (numId != 0) {
                val tv = child.findViewById<TextView>(numId)
                if (tv != null && tv.text.isNotEmpty()) {
                    targetNumber = tv.text.toString()
                }
            }
        }
        if (displayName.isNullOrEmpty()) {
            val nameId = rv.resources.getIdentifier("name", "id", rv.context.packageName)
            if (nameId != 0) {
                val tv = child.findViewById<TextView>(nameId)
                if (tv != null && tv.text.isNotEmpty()) {
                    displayName = tv.text.toString()
                }
            }
        }

        if (targetNumber.isNullOrEmpty() && displayName.isNullOrEmpty()) {
            return null
        }

        val finalNumber = targetNumber ?: displayName!!
        val finalName = if (!displayName.isNullOrEmpty()) displayName else finalNumber
        return CallInfo(itemVM, finalNumber, finalName)
    }

    private fun getCallDrawable(context: Context): Drawable? {
        if (cachedCallDrawable != null) return cachedCallDrawable
        val resId = context.resources.getIdentifier("dialer_btn_call_normal", "drawable", context.packageName)
        if (resId != 0) {
            try {
                cachedCallDrawable = context.getDrawable(resId)?.mutate()?.apply {
                    setTint(Color.WHITE)
                }
            } catch (_: Throwable) {}
        }
        return cachedCallDrawable
    }

    private fun getDeleteDrawable(context: Context): Drawable? {
        if (cachedDeleteDrawable != null) return cachedDeleteDrawable
        val candidates = listOf(
            "action_button_delete_normal_light_v2",
            "ic_history_delete",
            "miuix_action_icon_delete_light"
        )
        for (name in candidates) {
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId != 0) {
                try {
                    val d = context.getDrawable(resId)?.mutate()
                    if (d != null) {
                        d.setTint(Color.WHITE)
                        cachedDeleteDrawable = d
                        break
                    }
                } catch (_: Throwable) {}
            }
        }
        return cachedDeleteDrawable
    }

    private fun drawSwipeBackground(c: Canvas, child: View, context: Context) {
        val density = context.resources.displayMetrics.density
        val tx = child.translationX
        if (tx == 0f) return

        val top = child.top.toFloat() + 2f * density
        val bottom = child.bottom.toFloat() - 2f * density
        val cornerRadius = 14f * density
        val callThresholdPx = DialerRecentsConfigHolder.config.callThresholdDp * density
        val deleteWidthPx = DialerRecentsConfigHolder.config.deleteWidthDp * density

        if (tx > 0f) {
            // Right swipe -> Vibrant iOS Call Green (#34C759)
            val left = child.left.toFloat()
            val right = child.left + tx

            c.save()
            bgRect.set(left, top, right, bottom)
            bgPaint.color = Color.parseColor("#34C759")
            c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Centered official Phone icon
            val iconCenterX = left + min(tx / 2f, callThresholdPx / 2f)
            val iconCenterY = (top + bottom) / 2f
            val scaleProgress = min(1.15f, 0.88f + (tx / callThresholdPx) * 0.25f)
            drawPhoneIcon(c, iconCenterX, iconCenterY, scaleProgress, density, context)

            c.restore()
        } else {
            // Left swipe -> Vibrant iOS Delete Red (#FF3B30)
            val left = child.right + tx
            val right = child.right.toFloat()

            c.save()
            bgRect.set(left, top, right, bottom)
            bgPaint.color = Color.parseColor("#FF3B30")
            c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Properly centered Trash icon without text label
            val revealWidth = abs(tx)
            val actionCenterX = right - min(revealWidth / 2f, deleteWidthPx / 2f)
            val actionCenterY = (top + bottom) / 2f
            drawTrashIcon(c, actionCenterX, actionCenterY, density, context)

            c.restore()
        }
    }

    private fun drawPhoneIcon(
        c: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        density: Float,
        context: Context
    ) {
        val drawable = getCallDrawable(context)
        val iconSize = (28f * density * scale).toInt()
        val half = iconSize / 2

        if (drawable != null) {
            drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            drawable.draw(c)
        } else {
            // Crisp fallback phone path from stock vector
            if (fallbackCallPath == null) {
                try {
                    fallbackCallPath = PathParser.createPathFromPathData(
                        "M10.643,4.653C10.148,3.796 9.101,3.378 8.156,3.853L8.148,3.857C7.88,3.992 7.432,4.219 6.261,5.012C4.335,6.317 4.628,8.468 5.213,10.212C6.551,14.211 9.13,18.011 12.005,20.885C14.879,23.76 18.679,26.339 22.678,27.678C24.435,28.266 26.533,28.52 27.87,26.64C28.621,25.583 28.841,25.138 29.005,24.8L29.039,24.73C29.467,23.858 29.152,22.782 28.239,22.249L23.688,19.613C23.049,19.243 22.308,19.363 21.809,19.628L20.128,20.532L20.127,20.532C19.311,20.966 18.326,20.933 17.54,20.447C16.562,19.842 15.46,19.021 14.647,18.243C13.869,17.431 13.048,16.327 12.443,15.35C11.957,14.564 11.924,13.579 12.358,12.763L12.358,12.762L13.261,11.083L13.262,11.081C13.529,10.578 13.643,9.849 13.28,9.208L10.643,4.653Z"
                    )
                } catch (_: Throwable) {}
            }
            val path = fallbackCallPath
            if (path != null) {
                c.save()
                val factor = (iconSize / 34f)
                c.translate(cx - 17f * factor, cy - 17f * factor)
                c.scale(factor, factor)
                c.drawPath(path, iconPaint)
                c.restore()
            }
        }
    }

    private fun drawTrashIcon(
        c: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        context: Context
    ) {
        val drawable = getDeleteDrawable(context)
        val iconSize = (26f * density).toInt()
        val half = iconSize / 2

        if (drawable != null) {
            drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            drawable.draw(c)
        } else {
            // Crisp fallback trash path from stock vector
            if (fallbackDeletePath == null) {
                try {
                    fallbackDeletePath = PathParser.createPathFromPathData(
                        "M30.5,11C27.7386,11 25.5,13.2386 25.5,16V20H21.5H16.5002H13C11.6193,20 10.5,21.1193 10.5,22.5C10.5,23.8807 11.6193,25 13,25H16.5V48.2C16.5,52.6804 16.5,54.9206 17.3719,56.6319C18.1389,58.1372 19.3628,59.3611 20.8681,60.1281C22.5794,61 24.8196,61 29.3,61H45.7C50.1804,61 52.4206,61 54.1319,60.1281C55.6372,59.3611 56.8611,58.1372 57.6281,56.6319C58.5,54.9206 58.5,52.6804 58.5,48.2V25H62C63.3807,25 64.5,23.8807 64.5,22.5C64.5,21.1193 63.3807,20 62,20H58.4998H53.5H49.5V16C49.5,13.2386 47.2614,11 44.5,11H30.5ZM44.5,20V18C44.5,16.8954 43.6046,16 42.5,16H32.5C31.3954,16 30.5,16.8954 30.5,18V20H44.5ZM21.5,49.6V25H53.5V49.6C53.5,51.8402 53.5,52.9603 53.064,53.816C52.6805,54.5686 52.0686,55.1805 51.316,55.564C50.4603,56 49.3402,56 47.1,56H27.9C25.6598,56 24.5397,56 23.684,55.564C22.9314,55.1805 22.3195,54.5686 21.936,53.816C21.5,52.9603 21.5,51.8402 21.5,49.6Z"
                    )
                } catch (_: Throwable) {}
            }
            val path = fallbackDeletePath
            if (path != null) {
                c.save()
                val factor = (iconSize / 74f)
                c.translate(cx - 37f * factor, cy - 37f * factor)
                c.scale(factor, factor)
                c.drawPath(path, iconPaint)
                c.restore()
            }
        }
    }

    private fun invokeStockCall(context: Context, itemVM: Any?, targetNumber: String, classLoader: ClassLoader) {
        if (itemVM != null) {
            try {
                val vhUtilClass = Class.forName("com.android.contacts.dialer.list.DialerVHUtil", false, classLoader)
                val callMethod = vhUtilClass.getMethod("b", Context::class.java, itemVM.javaClass, String::class.java)
                callMethod.invoke(null, context, itemVM, "")
                Log.i(TAG, "Successfully invoked stock dialer call via DialerVHUtil.b")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Failed DialerVHUtil.b call, falling back to Intent", t)
            }
        }

        try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Log.i(TAG, "Successfully launched ACTION_CALL Intent for $targetNumber")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch ACTION_CALL Intent", t)
        }
    }

    private fun invokeStockDeleteDialogAndLogic(
        context: Context,
        fragment: Any?,
        targetNumber: String,
        displayName: String,
        classLoader: ClassLoader
    ) {
        if (!DialerRecentsConfigHolder.config.confirmDelete) {
            executeStockDelete(context, targetNumber, classLoader)
            return
        }

        // 1. Primary method: TwelveKeyDialerFragment.U4(str, str2) invokes Xiaomi's exact stock dialog
        if (fragment != null) {
            try {
                val u4Method = fragment.javaClass.getDeclaredMethod("U4", String::class.java, String::class.java)
                u4Method.isAccessible = true
                u4Method.invoke(fragment, targetNumber, displayName)
                Log.i(TAG, "Successfully invoked TwelveKeyDialerFragment.U4 stock delete dialog")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "TwelveKeyDialerFragment.U4 reflection failed, falling back to stock builder", t)
            }
        }

        // 2. Fallback: native dialog matching stock strings and calling ContactsUtils.q
        try {
            val activity = (context as? Activity)
                ?: (fragment?.javaClass?.getMethod("getActivity")?.invoke(fragment) as? Activity)
            if (activity != null) {
                AlertDialog.Builder(activity)
                    .setTitle(resolveStockString(context, "delete_call_log_title", "Delete call log"))
                    .setMessage(resolveStockString(context, "confirm_delete_contact_all_call_logs", "Delete all call history for %s?", displayName))
                    .setNegativeButton(resolveStockString(context, "cancel", "Cancel"), null)
                    .setPositiveButton(resolveStockString(context, "option_delete_contact", "Delete")) { _, _ ->
                        executeStockDelete(context, targetNumber, classLoader)
                    }
                    .show()
                Log.i(TAG, "Shown fallback delete dialog successfully")
            } else {
                executeStockDelete(context, targetNumber, classLoader)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show fallback delete dialog, executing delete directly", t)
            executeStockDelete(context, targetNumber, classLoader)
        }
    }

    private fun executeStockDelete(context: Context, targetNumber: String, classLoader: ClassLoader) {
        try {
            val contactsUtilsClass = Class.forName("com.android.contacts.ContactsUtils", false, classLoader)
            val qMethod = contactsUtilsClass.getMethod("q", Context::class.java, Array<String>::class.java)
            qMethod.invoke(null, context.applicationContext, arrayOf(targetNumber))
            Log.i(TAG, "Successfully executed stock ContactsUtils.q for number: $targetNumber")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to execute ContactsUtils.q", t)
        }
    }

    private fun resolveStockString(context: Context, resName: String, fallback: String, vararg formatArgs: Any): String {
        return try {
            val id = context.resources.getIdentifier(resName, "string", context.packageName)
            if (id != 0) {
                if (formatArgs.isNotEmpty()) context.getString(id, *formatArgs) else context.getString(id)
            } else {
                if (formatArgs.isNotEmpty()) String.format(fallback, *formatArgs) else fallback
            }
        } catch (_: Throwable) {
            if (formatArgs.isNotEmpty()) String.format(fallback, *formatArgs) else fallback
        }
    }
}
