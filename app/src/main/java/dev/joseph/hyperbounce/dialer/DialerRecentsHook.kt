package dev.joseph.hyperbounce.dialer

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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
 * NOTE: This hook intentionally avoids any direct compile-time or runtime references to
 * `androidx.recyclerview.widget.*` to eliminate NoClassDefFoundError across different ClassLoaders.
 * All RecyclerView interactions hook standard ViewGroup-level methods (onInterceptTouchEvent,
 * onTouchEvent, drawChild) which are never obfuscated.
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

    // iOS fluid spring interpolator
    private val iosSpringInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

    // Drawing resources
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bgRect = RectF()
    private val phonePath = Path()
    private val trashPath = Path()

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

        // 1. Hook TwelveKeyDialerFragment.onViewCreated to capture active fragment instance
        try {
            val fragmentClass = Class.forName("com.android.contacts.list.TwelveKeyDialerFragment", false, classLoader)
            for (m: Method in fragmentClass.declaredMethods) {
                if (m.name == "onViewCreated" &&
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
                                Log.d(TAG, "Captured active TwelveKeyDialerFragment instance")
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in TwelveKeyDialerFragment.onViewCreated hook", t)
                        }
                        result
                    }
                    Log.d(TAG, "Hooked TwelveKeyDialerFragment.onViewCreated successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook TwelveKeyDialerFragment", t)
        }

        // 2. Hook RecyclerView methods via host app classloader
        try {
            val rvClass = Class.forName("androidx.recyclerview.widget.RecyclerView", false, classLoader)

            // A. onInterceptTouchEvent
            for (m: Method in rvClass.declaredMethods) {
                if (m.name == "onInterceptTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val rv = chain.thisObject as? ViewGroup
                        val ev = chain.args[0] as? MotionEvent

                        if (rv != null && ev != null && DialerRecentsConfigHolder.config.enabled) {
                            val intercepted = handleInterceptTouchEvent(rv, ev, classLoader)
                            if (intercepted) {
                                return@intercept true
                            }
                        }
                        chain.proceed()
                    }
                    Log.d(TAG, "Hooked RecyclerView.onInterceptTouchEvent successfully.")
                    break
                }
            }

            // B. onTouchEvent
            for (m: Method in rvClass.declaredMethods) {
                if (m.name == "onTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val rv = chain.thisObject as? ViewGroup
                        val ev = chain.args[0] as? MotionEvent

                        if (rv != null && ev != null && DialerRecentsConfigHolder.config.enabled) {
                            val handled = handleTouchEvent(rv, ev, classLoader)
                            if (handled) {
                                return@intercept true
                            }
                        }
                        chain.proceed()
                    }
                    Log.d(TAG, "Hooked RecyclerView.onTouchEvent successfully.")
                    break
                }
            }

            // C. drawChild
            for (m: Method in rvClass.declaredMethods) {
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
                    Log.d(TAG, "Hooked RecyclerView.drawChild successfully.")
                    break
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook RecyclerView methods", t)
        }
    }

    private fun handleInterceptTouchEvent(rv: ViewGroup, e: MotionEvent, classLoader: ClassLoader): Boolean {
        val density = rv.resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop
        val deleteWidthPx = DialerRecentsConfigHolder.config.deleteWidthDp * density

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                isSwiping = false
                isDeleteButtonPressed = false
                hasTriggeredCallHaptic = false
                hasTriggeredDeleteHaptic = false

                val currentOpen = openChild
                if (currentOpen != null) {
                    val touchX = e.x
                    val touchY = e.y
                    val childTop = currentOpen.top.toFloat()
                    val childBottom = currentOpen.bottom.toFloat()
                    val deleteLeft = currentOpen.right - deleteWidthPx
                    val deleteRight = currentOpen.right.toFloat()

                    if (touchY in childTop..childBottom && touchX in deleteLeft..deleteRight) {
                        // Touched Delete button! Intercept to handle on ACTION_UP.
                        isDeleteButtonPressed = true
                        swipingChild = currentOpen
                        return true
                    } else {
                        // Touched elsewhere -> smooth spring close
                        closeOpenRow(currentOpen, rv)
                        swipingChild = null
                        return false
                    }
                }

                // Check if touch is on a call log child
                val child = findChildUnder(rv, e.x, e.y)
                if (child != null && isCallLogChild(child, rv, classLoader) && !isAdapterInMultiSelectMode(rv)) {
                    val info = extractCallInfo(child, rv)
                    if (info != null) {
                        swipingChild = child
                        swipingItemVM = info.itemVM
                        swipingTargetNumber = info.targetNumber
                        swipingDisplayName = info.displayName
                    } else {
                        swipingChild = null
                    }
                } else {
                    swipingChild = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (swipingChild != null && !isDeleteButtonPressed) {
                    val dx = e.x - downX
                    val dy = e.y - downY

                    if (!isSwiping) {
                        if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.3f) {
                            isSwiping = true
                            rv.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    } else {
                        return true
                    }
                }

                // If a row is open and user moves vertically, close it
                if (openChild != null && abs(e.y - downY) > touchSlop) {
                    closeOpenRow(openChild!!, rv)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
            }
        }
        return isSwiping || isDeleteButtonPressed
    }

    private fun handleTouchEvent(rv: ViewGroup, e: MotionEvent, classLoader: ClassLoader): Boolean {
        val density = rv.resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop
        val callThresholdPx = DialerRecentsConfigHolder.config.callThresholdDp * density
        val deleteWidthPx = DialerRecentsConfigHolder.config.deleteWidthDp * density

        if (isDeleteButtonPressed) {
            when (e.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    isDeleteButtonPressed = false
                    val target = swipingChild ?: openChild
                    val targetNumber = openTargetNumber ?: swipingTargetNumber
                    val displayName = openDisplayName ?: swipingDisplayName

                    if (target != null) {
                        performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                        closeOpenRow(target, rv)
                        if (targetNumber != null) {
                            val fragment = sActiveFragment?.get()
                            invokeStockDeleteDialogAndLogic(rv.context, fragment, targetNumber, displayName ?: targetNumber, classLoader)
                        }
                    }
                    resetSwipingState()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDeleteButtonPressed = false
                    resetSwipingState()
                    return true
                }
            }
            return true
        }

        val target = swipingChild ?: return false

        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX

                if (openChild != null && openChild == target) {
                    // Dragging already open row
                    val baseTx = -deleteWidthPx
                    val newTx = baseTx + dx
                    val clampedTx = if (newTx < -deleteWidthPx) {
                        -deleteWidthPx - (abs(newTx) - deleteWidthPx) * 0.25f
                    } else {
                        newTx
                    }
                    target.translationX = clampedTx
                    rv.invalidate()
                    return true
                }

                if (dx > 0f) {
                    // Right swipe -> Call action
                    val tx = if (dx > callThresholdPx) {
                        callThresholdPx + (dx - callThresholdPx) * 0.45f
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
                    // Left swipe -> Delete action
                    val absDx = abs(dx)
                    val tx = if (absDx > deleteWidthPx) {
                        -deleteWidthPx - (absDx - deleteWidthPx) * 0.3f
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
                return true
            }

            MotionEvent.ACTION_UP -> {
                val currentTx = target.translationX

                if (currentTx > 0f) {
                    // Right swipe release (Call)
                    if (currentTx >= callThresholdPx) {
                        performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                        val itemVM = swipingItemVM
                        animateSpring(target, 0f, rv)
                        if (itemVM != null) {
                            invokeStockCall(target.context, itemVM, classLoader)
                        }
                    } else {
                        animateSpring(target, 0f, rv)
                    }
                } else if (currentTx < 0f) {
                    // Left swipe release (Delete)
                    if (abs(currentTx) >= deleteWidthPx * 0.45f) {
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

            MotionEvent.ACTION_CANCEL -> {
                if (openChild != target) {
                    animateSpring(target, 0f, rv)
                }
                resetSwipingState()
                return true
            }
        }
        return false
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

    private fun isCallLogChild(child: View, rv: ViewGroup, classLoader: ClassLoader): Boolean {
        // Method 1: Check child view contents (id name or text_content)
        val nameView = child.findViewById<View>(android.R.id.text1)
            ?: child.findViewById<View>(rv.resources.getIdentifier("name", "id", rv.context.packageName))
        if (nameView != null) return true

        // Method 2: Check ViewHolder class name via reflection
        try {
            val getChildVHMethod = rv.javaClass.getMethod("getChildViewHolder", View::class.java)
            val vh = getChildVHMethod.invoke(rv, child)
            if (vh != null) {
                val className = vh.javaClass.name
                if (className.contains("DialerCallVH")) return true
            }
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

    private data class CallInfo(val itemVM: Any, val targetNumber: String, val displayName: String)

    private fun extractCallInfo(child: View, rv: ViewGroup): CallInfo? {
        try {
            val getChildVHMethod = rv.javaClass.getMethod("getChildViewHolder", View::class.java)
            val vh = getChildVHMethod.invoke(rv, child) ?: return null

            // Look up DialerItemVM in field or from adapter.K0(pos)
            var itemVM: Any? = null
            for (f: Field in vh.javaClass.declaredFields) {
                if (f.type.name.contains("DialerItemVM")) {
                    f.isAccessible = true
                    itemVM = f.get(vh)
                    if (itemVM != null) break
                }
            }

            if (itemVM == null) {
                val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                val adapter = getAdapterMethod.invoke(rv)
                val getPosMethod = vh.javaClass.getMethod("getBindingAdapterPosition")
                val pos = getPosMethod.invoke(vh) as? Int ?: -1
                if (adapter != null && pos >= 0) {
                    val k0Method = adapter.javaClass.getMethod("K0", Int::class.javaPrimitiveType)
                    itemVM = k0Method.invoke(adapter, pos)
                }
            }

            if (itemVM != null) {
                val number = try {
                    itemVM.javaClass.getMethod("w").invoke(itemVM) as? String
                } catch (_: Throwable) { null }

                val normalizedNumber = try {
                    itemVM.javaClass.getMethod("v").invoke(itemVM) as? String
                } catch (_: Throwable) { null }

                val name = try {
                    itemVM.javaClass.getMethod("t").invoke(itemVM) as? String
                } catch (_: Throwable) { null }

                val targetNumber = normalizedNumber ?: number ?: return null
                val displayName = if (!name.isNullOrEmpty()) name else targetNumber
                return CallInfo(itemVM, targetNumber, displayName)
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun drawSwipeBackground(c: Canvas, child: View, context: Context) {
        val density = context.resources.displayMetrics.density
        val tx = child.translationX
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

            // Phone receiver glyph
            val iconCenterX = left + min(tx / 2f, 44f * density)
            val iconCenterY = (top + bottom) / 2f
            val scaleProgress = min(1.2f, 0.85f + (tx / callThresholdPx) * 0.3f)
            drawPhoneGlyph(c, iconCenterX, iconCenterY, scaleProgress, density)

            c.restore()
        } else {
            // Left swipe -> Vibrant iOS Delete Red (#FF3B30)
            val left = child.right + tx
            val right = child.right.toFloat()

            c.save()
            bgRect.set(left, top, right, bottom)
            bgPaint.color = Color.parseColor("#FF3B30")
            c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Trash can glyph and "Delete" label
            val revealWidth = abs(tx)
            val actionCenterX = right - min(revealWidth / 2f, deleteWidthPx / 2f)
            val actionCenterY = (top + bottom) / 2f
            drawTrashGlyphAndText(c, actionCenterX, actionCenterY, density)

            c.restore()
        }
    }

    private fun drawPhoneGlyph(c: Canvas, cx: Float, cy: Float, scale: Float, density: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(scale, scale)

        iconPaint.strokeWidth = 2.4f * density
        iconPaint.style = Paint.Style.FILL_AND_STROKE

        phonePath.reset()
        phonePath.moveTo(-7f * density, -8f * density)
        phonePath.quadTo(-4f * density, -11f * density, 0f * density, -9f * density)
        phonePath.quadTo(3f * density, -7f * density, 6f * density, -4f * density)
        phonePath.quadTo(9f * density, 0f * density, 8f * density, 4f * density)
        phonePath.quadTo(5f * density, 9f * density, 0f * density, 8f * density)
        phonePath.quadTo(-6f * density, 6f * density, -8f * density, 0f * density)
        phonePath.close()

        c.drawPath(phonePath, iconPaint)
        c.restore()
    }

    private fun drawTrashGlyphAndText(c: Canvas, cx: Float, cy: Float, density: Float) {
        c.save()
        c.translate(cx, cy)

        iconPaint.strokeWidth = 2.0f * density
        iconPaint.style = Paint.Style.STROKE

        trashPath.reset()
        val tw = 7f * density
        val th = 8f * density

        // Can body
        trashPath.moveTo(-tw, -th * 0.3f)
        trashPath.lineTo(-tw * 0.8f, th)
        trashPath.quadTo(-tw * 0.8f, th + 2f * density, -tw * 0.5f, th + 2f * density)
        trashPath.lineTo(tw * 0.5f, th + 2f * density)
        trashPath.quadTo(tw * 0.8f, th + 2f * density, tw * 0.8f, th)
        trashPath.lineTo(tw, -th * 0.3f)

        // Lid line
        trashPath.moveTo(-tw * 1.25f, -th * 0.3f)
        trashPath.lineTo(tw * 1.25f, -th * 0.3f)

        // Lid handle
        trashPath.moveTo(-tw * 0.45f, -th * 0.3f)
        trashPath.lineTo(-tw * 0.45f, -th * 0.75f)
        trashPath.lineTo(tw * 0.45f, -th * 0.75f)
        trashPath.lineTo(tw * 0.45f, -th * 0.3f)

        // Rib lines
        trashPath.moveTo(-tw * 0.35f, 0f)
        trashPath.lineTo(-tw * 0.3f, th * 0.7f)
        trashPath.moveTo(tw * 0.35f, 0f)
        trashPath.lineTo(tw * 0.3f, th * 0.7f)

        c.drawPath(trashPath, iconPaint)

        // "Delete" text
        textPaint.textSize = 13f * density
        c.drawText("Delete", 0f, th + 13f * density, textPaint)

        c.restore()
    }

    private fun invokeStockCall(context: Context, itemVM: Any, classLoader: ClassLoader) {
        try {
            val vhUtilClass = Class.forName("com.android.contacts.dialer.list.DialerVHUtil", false, classLoader)
            val callMethod = vhUtilClass.getMethod("b", Context::class.java, itemVM.javaClass, String::class.java)
            callMethod.invoke(null, context, itemVM, "")
            Log.i(TAG, "Successfully invoked stock dialer call via DialerVHUtil.b")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to invoke stock call via DialerVHUtil.b", t)
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

        // Primary method: TwelveKeyDialerFragment.U4(str, str2)
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

        // Fallback: build stock dialog matching strings and execute ContactsUtils.q
        try {
            val activity = context as? Activity ?: return
            AlertDialog.Builder(activity)
                .setTitle(resolveStockString(context, "delete_call_log_title", "Delete call log"))
                .setMessage(resolveStockString(context, "confirm_delete_contact_all_call_logs", "Delete all call history for %s?", displayName))
                .setNegativeButton(resolveStockString(context, "cancel", "Cancel"), null)
                .setPositiveButton(resolveStockString(context, "option_delete_contact", "Delete")) { _, _ ->
                    executeStockDelete(context, targetNumber, classLoader)
                }
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show fallback delete dialog", t)
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
