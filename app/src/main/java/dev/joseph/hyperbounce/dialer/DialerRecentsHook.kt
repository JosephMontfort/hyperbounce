package dev.joseph.hyperbounce.dialer

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
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
import android.view.ViewParent
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.graphics.PathParser
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
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
    private var swipingVH: WeakReference<Any>? = null
    private var swipingAdapter: WeakReference<Any>? = null

    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false
    private var isDeleteButtonPressed = false
    private var hasTriggeredCallHaptic = false
    private var hasTriggeredDeleteHaptic = false

    // Recursion guard for dispatchTouchEvent & item touch listener
    private var isDispatching = false
    private var lastEventTime = -1L
    private var lastEventAction = -1
    private var lastHandledResult: Boolean? = null

    // Track attached RecyclerViews to avoid duplicate listeners
    private val attachedRecyclerViews = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())

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

    private fun enforceFragmentState(fragment: Any, classLoader: ClassLoader) {
        try {
            val activity = fragment.javaClass.getMethod("getActivity").invoke(fragment) as? Activity
            if (activity != null && DialerRecentsConfigHolder.config.enabled) {
                try {
                    activity.javaClass.getMethod("s1", java.lang.Boolean.TYPE).invoke(activity, false)
                    Log.i(TAG, "Enforced s1(false) on TwelveKeyDialerFragment")
                } catch (_: Throwable) {}
            }
            val fragView = fragment.javaClass.getMethod("getView").invoke(fragment) as? View
            val rvId = fragView?.resources?.getIdentifier("recyclerlist", "id", fragView.context.packageName) ?: 0
            val rv = if (rvId != 0) fragView?.findViewById<ViewGroup>(rvId) else null
            if (rv != null) {
                attachSwipeListenerToRecyclerView(rv, classLoader)
            }
        } catch (_: Throwable) {}
    }

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

        // 1. Hook TwelveKeyDialerFragment lifecycle methods to capture active fragment instance, enforce s1(false), and attach to RecyclerView
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
                                enforceFragmentState(fragment, classLoader)
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
                                enforceFragmentState(fragment, classLoader)
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.i(TAG, "Hooked TwelveKeyDialerFragment.onResume successfully.")
                }

                if (m.name == "B5" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            if (fragment != null) {
                                enforceFragmentState(fragment, classLoader)
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.i(TAG, "Hooked TwelveKeyDialerFragment.B5 successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook TwelveKeyDialerFragment", t)
        }

        // 2. Hook PeopleActivity to coordinate ViewPager draggable and SpringBackLayout springBackEnable
        try {
            val peopleActivityClass = Class.forName("com.android.contacts.activities.PeopleActivity", false, classLoader)
            for (m: Method in peopleActivityClass.declaredMethods) {
                if (m.name == "s1" && m.parameterTypes.size == 1 && m.parameterTypes[0] == java.lang.Boolean.TYPE) {
                    module.hook(m).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        if (activity != null && DialerRecentsConfigHolder.config.enabled) {
                            val vpId = activity.resources.getIdentifier("view_pager", "id", activity.packageName)
                            val vp = if (vpId != 0) activity.findViewById<View>(vpId) else null
                            val currentItem = try {
                                vp?.javaClass?.getMethod("getCurrentItem")?.invoke(vp) as? Int ?: 0
                            } catch (_: Throwable) { 0 }
                            if (currentItem == 0 && (chain.args.getOrNull(0) as? Boolean) == true) {
                                // On Dialer/Recents tab (Tab 0), suppress s1(true) so SpringBackLayout and ViewPager never hijack horizontal row swipe!
                                return@intercept null
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked PeopleActivity.s1 successfully.")
                }

                if (m.name == "onResume" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val activity = chain.thisObject as? Activity
                            if (activity != null && DialerRecentsConfigHolder.config.enabled) {
                                val vpId = activity.resources.getIdentifier("view_pager", "id", activity.packageName)
                                val vp = if (vpId != 0) activity.findViewById<View>(vpId) else null
                                val currentItem = try {
                                    vp?.javaClass?.getMethod("getCurrentItem")?.invoke(vp) as? Int ?: 0
                                } catch (_: Throwable) { 0 }
                                if (currentItem == 0) {
                                    val s1Method = activity.javaClass.getMethod("s1", java.lang.Boolean.TYPE)
                                    s1Method.invoke(activity, false)
                                    Log.i(TAG, "Enforced s1(false) in PeopleActivity.onResume for Tab 0")
                                }
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.i(TAG, "Hooked PeopleActivity.onResume successfully.")
                }

                if (m.name == "onCreate" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Bundle::class.java) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val activity = chain.thisObject as? Activity
                            if (activity != null && DialerRecentsConfigHolder.config.enabled) {
                                activity.window?.decorView?.post {
                                    try {
                                        val vpId = activity.resources.getIdentifier("view_pager", "id", activity.packageName)
                                        val vp = if (vpId != 0) activity.findViewById<View>(vpId) else null
                                        val currentItem = try {
                                            vp?.javaClass?.getMethod("getCurrentItem")?.invoke(vp) as? Int ?: 0
                                        } catch (_: Throwable) { 0 }
                                        if (currentItem == 0) {
                                            val s1Method = activity.javaClass.getMethod("s1", java.lang.Boolean.TYPE)
                                            s1Method.invoke(activity, false)
                                            Log.i(TAG, "Enforced s1(false) in PeopleActivity post-create for Tab 0")
                                        }
                                    } catch (_: Throwable) {}
                                }
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.i(TAG, "Hooked PeopleActivity.onCreate successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook PeopleActivity", t)
        }

        // 3. Hook DirectionViewPager to block tab-switch hijacking when on Dialer tab or touching/swiping a call row
        try {
            val dvpClass = Class.forName("com.android.contacts.widget.DirectionViewPager", false, classLoader)
            for (m: Method in dvpClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val vp = chain.thisObject as? View
                        val currentItem = try {
                            vp?.javaClass?.getMethod("getCurrentItem")?.invoke(vp) as? Int ?: 0
                        } catch (_: Throwable) { 0 }
                        if (currentItem == 0 && DialerRecentsConfigHolder.config.enabled) {
                            return@intercept false
                        }
                        if (isSwiping || isDeleteButtonPressed || swipingChild != null) {
                            return@intercept false
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked DirectionViewPager.onInterceptTouchEvent successfully.")
                    break
                }
            }

            for (m: Method in dvpClass.methods) {
                if ((m.name == "setCurrentItem" || m.name == "setCurrentItemDirection") &&
                    m.parameterTypes.isNotEmpty() &&
                    m.parameterTypes[0] == java.lang.Integer.TYPE
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val vp = chain.thisObject as? View
                            val targetItem = chain.args[0] as? Int ?: 0
                            val activity = vp?.context as? Activity
                            if (activity != null && DialerRecentsConfigHolder.config.enabled) {
                                val s1Method = activity.javaClass.getMethod("s1", java.lang.Boolean.TYPE)
                                if (targetItem == 0) {
                                    s1Method.invoke(activity, false)
                                } else {
                                    s1Method.invoke(activity, true)
                                }
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DirectionViewPager", t)
        }

        // 4. Hook SpringBackLayout.onInterceptTouchEvent and requestDisallowInterceptTouchEvent
        try {
            val sblClass = Class.forName("miuix.springback.view.SpringBackLayout", false, classLoader)
            for (m: Method in sblClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val sbl = chain.thisObject as? View
                        if (sbl != null && DialerRecentsConfigHolder.config.enabled) {
                            val vppId = sbl.resources.getIdentifier("view_pager_parent", "id", sbl.context.packageName)
                            if (vppId != 0 && sbl.id == vppId) {
                                val vp = sbl.findViewById<View>(sbl.resources.getIdentifier("view_pager", "id", sbl.context.packageName))
                                val currentItem = try {
                                    vp?.javaClass?.getMethod("getCurrentItem")?.invoke(vp) as? Int ?: 0
                                } catch (_: Throwable) { 0 }
                                if (currentItem == 0) {
                                    return@intercept false
                                }
                            }
                        }
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
                        try {
                            val vgClass = ViewGroup::class.java
                            val flagsField = vgClass.getDeclaredField("mGroupFlags")
                            flagsField.isAccessible = true
                            var flags = flagsField.getInt(sbl)
                            flags = if (disallow) {
                                flags or 0x80000
                            } else {
                                flags and 0x80000.inv()
                            }
                            flagsField.setInt(sbl, flags)
                        } catch (_: Throwable) {}
                        sbl?.parent?.requestDisallowInterceptTouchEvent(disallow)
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked SpringBackLayout.requestDisallowInterceptTouchEvent successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook SpringBackLayout", t)
        }

        // 4. Hook RecyclerView touch interception & dispatch across all potential hierarchy classes
        try {
            val baseRvClass = Class.forName("androidx.recyclerview.widget.RecyclerView", false, classLoader)
            for (m: Method in baseRvClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
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
                    Log.i(TAG, "Hooked RecyclerView.onInterceptTouchEvent successfully.")
                }

                if (m.name == "onTouchEvent" &&
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
                    Log.i(TAG, "Hooked RecyclerView.onTouchEvent successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook base RecyclerView onInterceptTouchEvent/onTouchEvent", t)
        }

        // Also hook dispatchTouchEvent across all potential hierarchy classes without breaking early
        val rvClassNames = listOf(
            "miuix.recyclerview.widget.RecyclerView",
            "androidx.recyclerview.widget.SpringRecyclerView",
            "androidx.recyclerview.widget.RemixRecyclerView",
            "androidx.recyclerview.widget.RecyclerView"
        )
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
                        Log.i(TAG, "Hooked $className.dispatchTouchEvent successfully.")
                        break
                    }
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

    private fun attachSwipeListenerToRecyclerView(rv: ViewGroup, classLoader: ClassLoader) {
        if (!attachedRecyclerViews.add(rv)) return
        try {
            val listenerInterface = Class.forName("androidx.recyclerview.widget.RecyclerView\$OnItemTouchListener", false, classLoader)
            val proxy = Proxy.newProxyInstance(classLoader, arrayOf(listenerInterface)) { _, method, args ->
                when (method.name) {
                    "onInterceptTouchEvent" -> {
                        val motionEvent = args?.getOrNull(1) as? MotionEvent
                        if (motionEvent != null && DialerRecentsConfigHolder.config.enabled) {
                            val handled = handleDispatchTouchEvent(rv, motionEvent, classLoader)
                            handled == true
                        } else {
                            false
                        }
                    }
                    "onTouchEvent" -> {
                        val motionEvent = args?.getOrNull(1) as? MotionEvent
                        if (motionEvent != null && DialerRecentsConfigHolder.config.enabled) {
                            handleDispatchTouchEvent(rv, motionEvent, classLoader)
                        }
                        null
                    }
                    "onRequestDisallowInterceptTouchEvent" -> null
                    else -> null
                }
            }

            for (m in rv.javaClass.methods) {
                if ((m.name == "addOnItemTouchListener" || m.name == "j") &&
                    m.parameterTypes.size == 1 &&
                    listenerInterface.isAssignableFrom(m.parameterTypes[0])
                ) {
                    m.invoke(rv, proxy)
                    Log.i(TAG, "Successfully attached OnItemTouchListener to RecyclerView via ${m.name}")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to attach OnItemTouchListener to RecyclerView", t)
        }
    }

    private fun handleDispatchTouchEvent(rv: ViewGroup, ev: MotionEvent, classLoader: ClassLoader): Boolean? {
        if (isDispatching) return null
        // Event de-duplication guard
        if (ev.eventTime == lastEventTime && ev.actionMasked == lastEventAction) {
            return lastHandledResult
        }
        isDispatching = true
        try {
            val res = processDispatchTouchEvent(rv, ev, classLoader)
            lastEventTime = ev.eventTime
            lastEventAction = ev.actionMasked
            lastHandledResult = res
            return res
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
                if (child != null && !isAdapterInMultiSelectMode(rv) && isCallLogChild(child, rv)) {
                    swipingChild = child
                    val info = extractCallInfo(child, rv)
                    swipingItemVM = info?.itemVM
                    swipingTargetNumber = info?.targetNumber?.takeIf { it.isNotEmpty() }
                    swipingDisplayName = info?.displayName?.takeIf { it.isNotEmpty() }
                    swipingVH = if (info?.vh != null) WeakReference(info.vh) else null
                    swipingAdapter = if (info?.adapter != null) WeakReference(info.adapter) else null
                    // Proactively disallow ALL ancestor ViewGroups from intercepting
                    disallowAllParents(rv, true)
                    Log.d(TAG, "ACTION_DOWN on call row: ${swipingDisplayName ?: "Unknown"} (${swipingTargetNumber ?: "No-Num"})")
                } else {
                    swipingChild = null
                    swipingVH = null
                    swipingAdapter = null
                    swipingItemVM = null
                    swipingTargetNumber = null
                    swipingDisplayName = null
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
                        // User is scrolling vertically -> only cancel if vertical scroll dominates significantly
                        if (abs(dy) > touchSlop * 2f && abs(dy) > abs(dx) * 2f) {
                            disallowAllParents(rv, false)
                            swipingChild = null
                            swipingVH = null
                            swipingAdapter = null
                            return null
                        }

                        // User is swiping horizontally -> engage iOS row swipe!
                        if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
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
                    var targetNumber = openTargetNumber ?: swipingTargetNumber
                    var displayName = openDisplayName ?: swipingDisplayName

                    if (target != null) {
                        performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                        closeOpenRow(target, rv)
                        if (targetNumber.isNullOrEmpty()) {
                            val vh = getViewHolderForChild(target, rv)
                            val vm = if (vh != null) getItemVMFromVH(vh) else null
                            if (vm != null) {
                                targetNumber = getNumberFromItemVM(vm)
                                if (displayName.isNullOrEmpty()) {
                                    displayName = getNameFromItemVM(vm)
                                }
                            }
                        }
                        val finalNum = targetNumber ?: ""
                        val finalName = if (!displayName.isNullOrBlank()) displayName else finalNum
                        Log.i(TAG, "Delete clicked for $finalName ($finalNum)")
                        val fragment = sActiveFragment?.get()
                        invokeStockDeleteDialogAndLogic(rv.context, fragment, finalNum, finalName, classLoader)
                    }
                    resetSwipingState()
                    return true
                }

                val target = swipingChild
                if (isSwiping && target != null) {
                    val currentTx = target.translationX
                    Log.d(TAG, "ACTION_UP during swipe: currentTx=$currentTx")

                    if (swipingTargetNumber == null || swipingItemVM == null) {
                        val reInfo = extractCallInfo(target, rv)
                        if (swipingItemVM == null) swipingItemVM = reInfo?.itemVM
                        if (swipingTargetNumber == null) swipingTargetNumber = reInfo?.targetNumber?.takeIf { it.isNotEmpty() }
                        if (swipingDisplayName == null) swipingDisplayName = reInfo?.displayName?.takeIf { it.isNotEmpty() }
                        if (reInfo?.vh != null) swipingVH = WeakReference(reInfo.vh)
                        if (reInfo?.adapter != null) swipingAdapter = WeakReference(reInfo.adapter)
                    }

                    if (swipingTargetNumber == null && swipingItemVM != null) {
                        swipingTargetNumber = getNumberFromItemVM(swipingItemVM!!)
                    }
                    if (swipingDisplayName == null && swipingItemVM != null) {
                        swipingDisplayName = getNameFromItemVM(swipingItemVM!!)
                    }

                    if (currentTx > 0f) {
                        // Swiped right -> Call action
                        val triggerThreshold = callThresholdPx * 0.78f
                        if (currentTx >= triggerThreshold) {
                            performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                            val itemVM = swipingItemVM
                            val number = swipingTargetNumber ?: ""
                            val vh = swipingVH?.get() ?: getViewHolderForChild(target, rv)
                            val adapter = swipingAdapter?.get()
                            val displayName = swipingDisplayName ?: number

                            animateGreenCallMorph(target, displayName, number, rv) {
                                invokeStockCall(target.context, target, vh, adapter, itemVM, number, classLoader)
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

    private fun enforcePagerDisabled(view: View) {
        try {
            (view.context as? Activity)?.let { act ->
                try {
                    act.javaClass.getMethod("s1", java.lang.Boolean.TYPE).invoke(act, false)
                } catch (_: Throwable) {}
            }
            var p: ViewParent? = view.parent
            while (p != null) {
                if (p is ViewGroup) {
                    val className = p.javaClass.name
                    if (className.contains("SpringBackLayout")) {
                        try {
                            p.javaClass.getMethod("setSpringBackEnable", java.lang.Boolean.TYPE).invoke(p, false)
                        } catch (_: Throwable) {}
                    }
                    if (className.contains("ViewPager")) {
                        try {
                            p.javaClass.getMethod("setDraggable", java.lang.Boolean.TYPE).invoke(p, false)
                        } catch (_: Throwable) {}
                    }
                }
                p = p.parent
            }
        } catch (_: Throwable) {}
    }

    private fun disallowAllParents(view: View, disallow: Boolean) {
        if (disallow) {
            enforcePagerDisabled(view)
        }
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
            // Before threshold: slight resistance so swipe feels physical but smooth
            // Past threshold: resistance breaks, tick haptic triggers, entry moves out to full green
            val triggerThreshold = callThresholdPx * 0.78f
            val tx = if (dx >= callThresholdPx) {
                val excess = dx - callThresholdPx
                triggerThreshold + excess * 1.55f
            } else {
                dx * 0.78f
            }
            target.translationX = tx

            if (tx >= triggerThreshold && !hasTriggeredCallHaptic) {
                performHaptic(rv, HapticFeedbackConstants.CLOCK_TICK)
                hasTriggeredCallHaptic = true
            } else if (tx < triggerThreshold) {
                // User pushed slightly left to cancel -> reset haptic state
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

    private fun findActivity(context: Context?): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun animateGreenCallMorph(
        target: View,
        displayName: String,
        targetNumber: String,
        rv: ViewGroup,
        onCallLaunch: () -> Unit
    ) {
        val activity = findActivity(target.context) ?: findActivity(rv.context)
        val decorView = activity?.window?.decorView as? ViewGroup
        if (decorView == null) {
            animateSpring(target, 0f, rv)
            onCallLaunch()
            return
        }

        val loc = IntArray(2)
        target.getLocationInWindow(loc)
        val density = target.resources.displayMetrics.density
        val rowLeft = loc[0].toFloat() - target.translationX
        val rowTop = loc[1].toFloat()
        val rowRight = rowLeft + target.width.toFloat()
        val rowBottom = rowTop + target.height.toFloat()
        val startRect = RectF(rowLeft, rowTop, rowRight, rowBottom)
        val decorW = if (decorView.width > 0) decorView.width.toFloat() else target.resources.displayMetrics.widthPixels.toFloat()
        val decorH = if (decorView.height > 0) decorView.height.toFloat() else target.resources.displayMetrics.heightPixels.toFloat()
        val endRect = RectF(0f, 0f, decorW, decorH)

        val overlay = object : View(decorView.context) {
            var progress = 0f
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#34C759")
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 24f * density
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0FFFFFF")
                textSize = 16f * density
                textAlign = Paint.Align.CENTER
            }
            val currRect = RectF(startRect)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cornerRadius = (14f * density) * (1f - progress)
                currRect.set(
                    startRect.left + (endRect.left - startRect.left) * progress,
                    startRect.top + (endRect.top - startRect.top) * progress,
                    startRect.right + (endRect.right - startRect.right) * progress,
                    startRect.bottom + (endRect.bottom - startRect.bottom) * progress
                )
                canvas.drawRoundRect(currRect, cornerRadius, cornerRadius, bgPaint)

                if (progress > 0.20f) {
                    val alpha = ((progress - 0.20f) / 0.35f).coerceIn(0f, 1f)
                    textPaint.alpha = (alpha * 255).toInt()
                    subTextPaint.alpha = (alpha * 255).toInt()
                    val centerY = currRect.centerY()
                    val title = if (displayName.isNotBlank()) displayName else targetNumber
                    if (title.isNotBlank()) {
                        canvas.drawText(title, currRect.centerX(), centerY - 12f * density, textPaint)
                        if (targetNumber.isNotBlank() && targetNumber != title) {
                            canvas.drawText(targetNumber, currRect.centerX(), centerY + 18f * density, subTextPaint)
                        }
                    }
                }
            }
        }

        decorView.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        target.translationX = 0f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            interpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f) // Folme fluid curve
            addUpdateListener { va ->
                overlay.progress = va.animatedValue as Float
                overlay.invalidate()
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(160L)
                    .withEndAction {
                        try {
                            decorView.removeView(overlay)
                        } catch (_: Throwable) {}
                    }
                    .start()
            }
        })
        animator.start()
        onCallLaunch()
    }

    private fun resetSwipingState() {
        isSwiping = false
        isDeleteButtonPressed = false
        swipingChild = null
        swipingItemVM = null
        swipingTargetNumber = null
        swipingDisplayName = null
        swipingVH = null
        swipingAdapter = null
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
        try {
            val findMethod = parent.javaClass.getMethod("findChildViewUnder", java.lang.Float.TYPE, java.lang.Float.TYPE)
            val v = findMethod.invoke(parent, x, y) as? View
            if (v != null && v.visibility == View.VISIBLE) return v
        } catch (_: Throwable) {}

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

    private fun getViewHolderForChild(child: View, rv: ViewGroup): Any? {
        // 1. From LayoutParams (universal in AndroidX and Remix/Spring RecyclerView)
        val lp = child.layoutParams
        if (lp != null) {
            for (f in lp.javaClass.declaredFields) {
                try {
                    f.isAccessible = true
                    val obj = f.get(lp)
                    if (obj != null) {
                        for (vf in obj.javaClass.fields + obj.javaClass.declaredFields) {
                            if (View::class.java.isAssignableFrom(vf.type)) {
                                vf.isAccessible = true
                                if (vf.get(obj) === child) {
                                    return obj
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. From RecyclerView methods (getChildViewHolder, findContainingViewHolder, g0, T)
        for (methodName in listOf("getChildViewHolder", "findContainingViewHolder", "g0", "T")) {
            try {
                val m = rv.javaClass.getMethod(methodName, View::class.java)
                val res = m.invoke(rv, child)
                if (res != null) return res
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun isCallLogChild(child: View, rv: ViewGroup): Boolean {
        if (child.visibility != View.VISIBLE || child.height < 15) return false
        val vh = getViewHolderForChild(child, rv)
        if (vh != null) {
            val vhName = vh.javaClass.simpleName.lowercase()
            if (vhName.contains("header") || vhName.contains("divider") || vhName.contains("footer") || vhName.contains("stranger")) {
                return false
            }
            return true
        }
        // Fallback: accept if view has reasonable height for a list row (> 35dp)
        return child.height >= (35 * rv.resources.displayMetrics.density)
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

    private data class CallInfo(
        val vh: Any?,
        val adapter: Any?,
        val itemVM: Any?,
        val targetNumber: String,
        val displayName: String
    )

    private fun isValidPhoneNumber(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        val str = s.trim()
        // Reject timestamps, dates, and formatted time strings (e.g. "10:45", "12:30:15", "10:45 AM")
        if (str.contains(":") || str.contains("/") || str.contains("AM", ignoreCase = true) || str.contains("PM", ignoreCase = true)) {
            return false
        }
        // Reject strings that have alphabetic letters (e.g. "Mom", "Mobile", "Yesterday")
        val letters = str.filter { it.isLetter() }
        if (letters.isNotEmpty()) return false

        val digits = str.filter { it.isDigit() }
        // Genuine phone numbers (short codes, emergency 911/112/100, standard mobile) have >= 3 digits
        return digits.length >= 3
    }

    private fun getItemVMFromVH(vh: Any): Any? {
        var currClass: Class<*>? = vh.javaClass
        while (currClass != null && currClass != Any::class.java) {
            for (f: Field in currClass.declaredFields) {
                if (f.type.name.contains("DialerItemVM")) {
                    try {
                        f.isAccessible = true
                        val obj = f.get(vh)
                        if (obj != null) return obj
                    } catch (_: Throwable) {}
                }
            }
            currClass = currClass.superclass
        }
        return null
    }

    private fun getNumberFromItemVM(itemVM: Any): String? {
        // In DialerItemVM: w() is the raw phone number
        try {
            val num = itemVM.javaClass.getMethod("w").invoke(itemVM) as? String
            if (isValidPhoneNumber(num)) return num
        } catch (_: Throwable) {}

        // Fallback to m() (formatted matched) or v() (normalized)
        for (methodName in listOf("m", "v")) {
            try {
                val num = itemVM.javaClass.getMethod(methodName).invoke(itemVM) as? String
                if (isValidPhoneNumber(num)) return num
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun getNameFromItemVM(itemVM: Any): String? {
        try {
            val name = itemVM.javaClass.getMethod("t").invoke(itemVM) as? String
            if (!name.isNullOrBlank()) return name
        } catch (_: Throwable) {}
        return null
    }

    private fun extractCallInfo(child: View, rv: ViewGroup): CallInfo? {
        if (child.visibility != View.VISIBLE || child.height < 15) return null

        val vh = getViewHolderForChild(child, rv)
        if (vh != null) {
            val vhName = vh.javaClass.simpleName.lowercase()
            if (vhName.contains("header") || vhName.contains("divider") || vhName.contains("footer") || vhName.contains("stranger")) {
                return null
            }
        }

        var adapter: Any? = null
        try {
            val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
            adapter = getAdapterMethod.invoke(rv)
        } catch (_: Throwable) {}

        var itemVM: Any? = null
        if (vh != null) {
            itemVM = getItemVMFromVH(vh)
        }

        var targetNumber: String? = null
        var displayName: String? = null

        if (itemVM != null) {
            targetNumber = getNumberFromItemVM(itemVM)
            displayName = getNameFromItemVM(itemVM)
        }

        // TextView fallback if targetNumber or displayName still missing (strictly ignore relative_time)
        if (targetNumber.isNullOrEmpty() || displayName.isNullOrEmpty()) {
            val dialerNumId = rv.resources.getIdentifier("dialer_number", "id", rv.context.packageName)
            if (dialerNumId != 0) {
                val tv = child.findViewById<TextView>(dialerNumId)
                val txt = tv?.text?.toString()?.trim()
                if (isValidPhoneNumber(txt) && targetNumber.isNullOrEmpty()) {
                    targetNumber = txt
                }
            }

            val nameId = rv.resources.getIdentifier("name", "id", rv.context.packageName)
            if (nameId != 0) {
                val tv = child.findViewById<TextView>(nameId)
                val txt = tv?.text?.toString()?.trim()
                if (!txt.isNullOrEmpty()) {
                    if (isValidPhoneNumber(txt)) {
                        if (targetNumber.isNullOrEmpty()) targetNumber = txt
                    } else {
                        if (displayName.isNullOrEmpty()) displayName = txt
                    }
                }
            }
        }

        val finalNumber = targetNumber ?: ""
        val finalName = if (!displayName.isNullOrBlank()) displayName else finalNumber
        return CallInfo(vh, adapter, itemVM, finalNumber, finalName)
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
        if (abs(tx) < 1f) return

        val top = child.top.toFloat() + 2f * density
        val bottom = child.bottom.toFloat() - 2f * density
        val cornerRadius = 14f * density
        val callThresholdPx = DialerRecentsConfigHolder.config.callThresholdDp * density
        val deleteWidthPx = DialerRecentsConfigHolder.config.deleteWidthDp * density

        val revealWidth = abs(tx)
        // Fade icon out smoothly as swipe slides back to prevent it bleeding over row content
        // Icon is completely hidden when revealWidth is below 28dp
        val iconAlpha = ((revealWidth - 28f * density) / (24f * density)).coerceIn(0f, 1f)

        if (tx > 0f) {
            // Right swipe -> Vibrant iOS Call Green (#34C759)
            val left = child.left.toFloat()
            val right = child.left + tx
            if (right <= left) return

            c.save()
            bgRect.set(left, top, right, bottom)
            c.clipRect(bgRect) // STRICTLY CLIP to prevent icon leaking over person row

            bgPaint.color = Color.parseColor("#34C759")
            c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Centered official Phone icon
            if (iconAlpha > 0f) {
                val iconCenterX = left + min(tx / 2f, callThresholdPx / 2f)
                val iconCenterY = (top + bottom) / 2f
                val scaleProgress = min(1.15f, 0.70f + (tx / callThresholdPx) * 0.35f) * iconAlpha
                drawPhoneIcon(c, iconCenterX, iconCenterY, scaleProgress, iconAlpha, density, context)
            }

            c.restore()
        } else {
            // Left swipe -> Vibrant iOS Delete Red (#FF3B30)
            val left = child.right + tx
            val right = child.right.toFloat()
            if (right <= left) return

            c.save()
            bgRect.set(left, top, right, bottom)
            c.clipRect(bgRect) // STRICTLY CLIP to prevent icon leaking over person row

            bgPaint.color = Color.parseColor("#FF3B30")
            c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Properly centered Trash icon without text label
            if (iconAlpha > 0f) {
                val actionCenterX = right - min(revealWidth / 2f, deleteWidthPx / 2f)
                val actionCenterY = (top + bottom) / 2f
                val scaleProgress = min(1.0f, 0.70f + (revealWidth / deleteWidthPx) * 0.30f) * iconAlpha
                drawTrashIcon(c, actionCenterX, actionCenterY, scaleProgress, iconAlpha, density, context)
            }

            c.restore()
        }
    }

    private fun drawPhoneIcon(
        c: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        alphaProgress: Float,
        density: Float,
        context: Context
    ) {
        val drawable = getCallDrawable(context)
        val iconSize = (28f * density * scale).toInt()
        val half = iconSize / 2
        val alphaInt = (alphaProgress * 255).toInt().coerceIn(0, 255)

        if (drawable != null) {
            drawable.alpha = alphaInt
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
                iconPaint.alpha = alphaInt
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
        scale: Float,
        alphaProgress: Float,
        density: Float,
        context: Context
    ) {
        val drawable = getDeleteDrawable(context)
        val iconSize = (26f * density * scale).toInt()
        val half = iconSize / 2
        val alphaInt = (alphaProgress * 255).toInt().coerceIn(0, 255)

        if (drawable != null) {
            drawable.alpha = alphaInt
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
                iconPaint.alpha = alphaInt
                val factor = (iconSize / 74f)
                c.translate(cx - 37f * factor, cy - 37f * factor)
                c.scale(factor, factor)
                c.drawPath(path, iconPaint)
                c.restore()
            }
        }
    }

    private fun invokeStockCall(
        context: Context,
        child: View?,
        vh: Any?,
        adapter: Any?,
        itemVM: Any?,
        targetNumber: String,
        classLoader: ClassLoader
    ) {
        val resolvedVM = itemVM ?: (if (vh != null) getItemVMFromVH(vh) else null)
        val vmNumber = if (resolvedVM != null) getNumberFromItemVM(resolvedVM) else null
        val finalNumber = if (!targetNumber.isNullOrBlank()) targetNumber else (vmNumber ?: "")
        val cleanNumber = finalNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val activity = findActivity(context) ?: (context as? Activity)
        Log.i(TAG, "invokeStockCall targetNumber=$finalNumber, cleanNumber=$cleanNumber, vh=${vh?.javaClass?.simpleName}, hasVM=${resolvedVM != null}, hasActivity=${activity != null}")

        val isContactVH = vh?.javaClass?.name?.contains("Contact", ignoreCase = true) == true
        val act = activity ?: context
        var callInitiated = false

        if (isContactVH) {
            // 1. Try stock click: In DialerContactVH, clicking itemView triggers official stock dialing
            if (child != null) {
                try {
                    if (vh is View.OnClickListener) {
                        vh.onClick(child)
                        callInitiated = true
                        Log.i(TAG, "Successfully invoked vh.onClick(child) for search contact")
                    } else if (child.hasOnClickListeners()) {
                        child.performClick()
                        callInitiated = true
                        Log.i(TAG, "Successfully invoked child.performClick() for search contact")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed vh.onClick/child.performClick for contact", t)
                }
            }

            // 2. Try DialerVHUtil.d with the Activity context and itemVM
            if (!callInitiated && resolvedVM != null) {
                try {
                    val vhUtilClass = Class.forName("com.android.contacts.dialer.list.DialerVHUtil", false, classLoader)
                    val dMethod = vhUtilClass.methods.firstOrNull { it.name == "d" && it.parameterTypes.size == 3 }
                    if (dMethod != null) {
                        dMethod.invoke(null, act, resolvedVM, "")
                        callInitiated = true
                        Log.i(TAG, "Successfully called search contact via DialerVHUtil.d")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed DialerVHUtil.d for contact", t)
                }
            }
        } else {
            // For Recents call log items: use DialerVHUtil.b with the exact itemVM
            if (resolvedVM != null) {
                try {
                    val vhUtilClass = Class.forName("com.android.contacts.dialer.list.DialerVHUtil", false, classLoader)
                    val callMethod = vhUtilClass.methods.firstOrNull { it.name == "b" && it.parameterTypes.size == 3 }
                    if (callMethod != null) {
                        callMethod.invoke(null, act, resolvedVM, "")
                        callInitiated = true
                        Log.i(TAG, "Successfully invoked stock dialer call via DialerVHUtil.b")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed DialerVHUtil.b call", t)
                }
            }
        }

        // Direct ACTION_CALL_PRIVILEGED / ACTION_CALL fallback (guaranteed to dial if cleanNumber >= 3)
        if (!callInitiated && cleanNumber.length >= 3 && isValidPhoneNumber(cleanNumber)) {
            val launchedPrivileged = try {
                val privIntent = Intent("android.intent.action.CALL_PRIVILEGED", Uri.parse("tel:${Uri.encode(cleanNumber)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("com.android.phone.CALL_ORIGIN", "com.android.contacts")
                }
                act.startActivity(privIntent)
                Log.i(TAG, "Successfully launched CALL_PRIVILEGED Intent for $cleanNumber")
                true
            } catch (_: Throwable) {
                false
            }
            if (!launchedPrivileged) {
                try {
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(cleanNumber)}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("com.android.phone.CALL_ORIGIN", "com.android.contacts")
                    }
                    act.startActivity(callIntent)
                    Log.i(TAG, "Successfully launched ACTION_CALL Intent for $cleanNumber")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to launch ACTION_CALL Intent", t)
                }
            }
        } else if (!callInitiated) {
            Log.w(TAG, "invokeStockCall skipped: callInitiated=false and invalid cleanNumber='$cleanNumber'")
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
