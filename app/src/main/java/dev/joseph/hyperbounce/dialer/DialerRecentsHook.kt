package dev.joseph.hyperbounce.dialer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import androidx.recyclerview.widget.RecyclerView
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Xposed hook for com.android.contacts that introduces authentic iOS 26-styled
 * fluid swipe gestures to the Recents screen of Xiaomi's stock dialer:
 *  - Swipe Right: Reveals iOS Call Green action, launches stock call on trigger release.
 *  - Swipe Left: Reveals iOS Red Delete action, clicking it invokes Xiaomi stock confirmation
 *    dialog and stock call log deletion logic.
 */
object DialerRecentsHook {

    private const val TAG = "HyperBounce_Dialer"
    private const val TAG_ATTACHED_KEY = 0x7F0A0D1A

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

        try {
            val fragmentClass = Class.forName("com.android.contacts.list.TwelveKeyDialerFragment", false, classLoader)

            // Hook onViewCreated to attach our gesture and decoration layers
            for (m: Method in fragmentClass.declaredMethods) {
                if (m.name == "onViewCreated" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == View::class.java &&
                    m.parameterTypes[1] == Bundle::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val view = chain.args[0] as? View
                            val fragment = chain.thisObject
                            if (view != null && fragment != null) {
                                view.post {
                                    attachSwipeToRecyclerView(view, fragment, classLoader)
                                }
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

            // Also hook B5() (which initializes the recycler list) as secondary safety net
            for (m: Method in fragmentClass.declaredMethods) {
                if (m.name == "B5" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            if (fragment != null) {
                                val fragmentView = try {
                                    val getViewMethod = fragment.javaClass.getMethod("getView")
                                    getViewMethod.invoke(fragment) as? View
                                } catch (_: Throwable) { null }

                                if (fragmentView != null) {
                                    fragmentView.post {
                                        attachSwipeToRecyclerView(fragmentView, fragment, classLoader)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in TwelveKeyDialerFragment.B5 hook", t)
                        }
                        result
                    }
                    Log.d(TAG, "Hooked TwelveKeyDialerFragment.B5 successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook TwelveKeyDialerFragment", t)
        }
    }

    private fun attachSwipeToRecyclerView(rootView: View, fragment: Any, classLoader: ClassLoader) {
        val recyclerView = findRecyclerView(rootView, fragment) ?: return

        if (recyclerView.getTag(TAG_ATTACHED_KEY) == true) {
            return
        }
        recyclerView.setTag(TAG_ATTACHED_KEY, true)

        Log.i(TAG, "Attaching RecentsSwipeController to RecyclerView: $recyclerView")
        val controller = RecentsSwipeController(recyclerView, fragment, classLoader)
        recyclerView.addOnItemTouchListener(controller)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    controller.closeCurrentOpenRow()
                }
            }
        })
        recyclerView.addItemDecoration(controller)
    }

    private fun findRecyclerView(rootView: View, fragment: Any): RecyclerView? {
        // Try field M in TwelveKeyDialerFragment first
        try {
            val fieldM = fragment.javaClass.getDeclaredField("M")
            fieldM.isAccessible = true
            val rv = fieldM.get(fragment) as? RecyclerView
            if (rv != null) return rv
        } catch (_: Throwable) {}

        // Fallback: search view tree
        return findRecyclerViewRecursive(rootView)
    }

    private fun findRecyclerViewRecursive(v: View): RecyclerView? {
        if (v is RecyclerView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val child = v.getChildAt(i)
                val found = findRecyclerViewRecursive(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Complete controller managing fluid touch gestures, spring physics, stock call execution,
     * stock delete dialog invocation, and pixel-perfect iOS action drawing.
     */
    private class RecentsSwipeController(
        private val recyclerView: RecyclerView,
        private val fragment: Any,
        private val classLoader: ClassLoader
    ) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

        private val context: Context = recyclerView.context
        private val density: Float = context.resources.displayMetrics.density
        private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

        // Spring interpolator: iOS fluid response curve (slight overshoot settle)
        private val iosSpringInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

        // Drawing paints
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
            textSize = 13.5f * density
            textAlign = Paint.Align.CENTER
        }

        private val bgRect = RectF()
        private val phonePath = Path()
        private val trashPath = Path()

        // Touch tracking
        private var downX = 0f
        private var downY = 0f
        private var isSwiping = false
        private var activeChild: View? = null
        private var activeVH: RecyclerView.ViewHolder? = null

        // Open state tracking
        private var openChild: View? = null
        private var openVH: RecyclerView.ViewHolder? = null

        private var hasTriggeredCallHaptic = false
        private var hasTriggeredDeleteHaptic = false

        private val callThresholdPx: Float
            get() = DialerRecentsConfigHolder.config.callThresholdDp * density

        private val deleteWidthPx: Float
            get() = DialerRecentsConfigHolder.config.deleteWidthDp * density

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (!DialerRecentsConfigHolder.config.enabled) return false

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    isSwiping = false
                    hasTriggeredCallHaptic = false
                    hasTriggeredDeleteHaptic = false

                    // If a row is already swiped open to show Delete button
                    val currentOpen = openChild
                    if (currentOpen != null) {
                        val touchX = e.x
                        val touchY = e.y
                        val childTop = currentOpen.top.toFloat()
                        val childBottom = currentOpen.bottom.toFloat()
                        val deleteLeft = currentOpen.right - deleteWidthPx
                        val deleteRight = currentOpen.right.toFloat()

                        if (touchY in childTop..childBottom && touchX in deleteLeft..deleteRight) {
                            // User touched directly on the revealed Delete button!
                            // Intercept immediately to handle click on ACTION_UP.
                            activeChild = currentOpen
                            activeVH = openVH
                            return true
                        } else {
                            // Touched elsewhere -> smooth spring close
                            closeOpenRow(currentOpen)
                            activeChild = null
                            activeVH = null
                            return false
                        }
                    }

                    // Look for a call log item under touch
                    val child = rv.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val vh = rv.getChildViewHolder(child)
                        if (isCallLogViewHolder(vh) && !isAdapterInMultiSelectMode(rv)) {
                            activeChild = child
                            activeVH = vh
                        } else {
                            activeChild = null
                            activeVH = null
                        }
                    } else {
                        activeChild = null
                        activeVH = null
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val target = activeChild ?: return false
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

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwiping = false
                }
            }
            return isSwiping
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            val target = activeChild ?: return

            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - downX

                    if (openChild != null && openChild == target) {
                        // Dragging while already open at -deleteWidthPx
                        val baseTx = -deleteWidthPx
                        val newTx = baseTx + dx
                        val clampedTx = if (newTx < -deleteWidthPx) {
                            // Rubber-band resistance to the left
                            -deleteWidthPx - (abs(newTx) - deleteWidthPx) * 0.25f
                        } else {
                            newTx
                        }
                        target.translationX = clampedTx
                        rv.invalidate()
                        return
                    }

                    if (dx > 0f) {
                        // Swiping Right -> Call action
                        val tx = if (dx > callThresholdPx) {
                            // Rubber-band damping beyond call threshold
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
                        // Swiping Left -> Delete action
                        val absDx = abs(dx)
                        val tx = if (absDx > deleteWidthPx) {
                            // Rubber-band damping beyond delete button width
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
                }

                MotionEvent.ACTION_UP -> {
                    val currentTx = target.translationX

                    // 1. Check if user tapped Delete button on an already open row
                    if (openChild != null && openChild == target) {
                        val touchX = e.x
                        val touchY = e.y
                        val childTop = target.top.toFloat()
                        val childBottom = target.bottom.toFloat()
                        val deleteLeft = target.right - deleteWidthPx
                        val deleteRight = target.right.toFloat()

                        if (touchY in childTop..childBottom && touchX in deleteLeft..deleteRight && abs(e.x - downX) < touchSlop) {
                            // Tapped the Delete button!
                            performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                            val vh = activeVH
                            val itemVM = getDialerItemVM(vh, rv)
                            closeOpenRow(target)
                            if (itemVM != null) {
                                invokeStockDeleteDialogAndLogic(target.context, fragment, itemVM, classLoader)
                            }
                            resetState()
                            return
                        } else {
                            // Tapped or dragged elsewhere -> spring close
                            closeOpenRow(target)
                            resetState()
                            return
                        }
                    }

                    // 2. Handle swipe release for fresh drag
                    if (currentTx > 0f) {
                        // Right swipe (Call)
                        if (currentTx >= callThresholdPx) {
                            performHaptic(rv, HapticFeedbackConstants.CONFIRM)
                            val vh = activeVH
                            val itemVM = getDialerItemVM(vh, rv)
                            animateSpringBack(target, 0f, rv)
                            if (itemVM != null) {
                                invokeStockCall(target.context, itemVM, classLoader)
                            }
                        } else {
                            animateSpringBack(target, 0f, rv)
                        }
                    } else if (currentTx < 0f) {
                        // Left swipe (Delete)
                        if (abs(currentTx) >= deleteWidthPx * 0.45f) {
                            // Snap open to exactly reveal delete button
                            animateSpringTo(target, -deleteWidthPx, rv)
                            openChild = target
                            openVH = activeVH
                        } else {
                            animateSpringBack(target, 0f, rv)
                        }
                    } else {
                        animateSpringBack(target, 0f, rv)
                    }

                    isSwiping = false
                    activeChild = null
                    activeVH = null
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (openChild != target) {
                        animateSpringBack(target, 0f, rv)
                    }
                    resetState()
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

        fun closeCurrentOpenRow() {
            openChild?.let { closeOpenRow(it) }
        }

        private fun closeOpenRow(view: View) {
            animateSpringBack(view, 0f, recyclerView)
            if (openChild == view) {
                openChild = null
                openVH = null
            }
        }

        private fun resetState() {
            isSwiping = false
            activeChild = null
            activeVH = null
            hasTriggeredCallHaptic = false
            hasTriggeredDeleteHaptic = false
        }

        private fun animateSpringTo(view: View, targetTx: Float, rv: RecyclerView) {
            val startTx = view.translationX
            val animator = ValueAnimator.ofFloat(startTx, targetTx)
            animator.duration = 320L
            animator.interpolator = iosSpringInterpolator
            animator.addUpdateListener { va ->
                view.translationX = va.animatedValue as Float
                rv.invalidate()
            }
            animator.start()
        }

        private fun animateSpringBack(view: View, targetTx: Float, rv: RecyclerView) {
            val startTx = view.translationX
            if (startTx == targetTx) return
            val animator = ValueAnimator.ofFloat(startTx, targetTx)
            animator.duration = 280L
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

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val tx = child.translationX
                if (tx == 0f) continue

                val top = child.top.toFloat() + 2f * density
                val bottom = child.bottom.toFloat() - 2f * density
                val cornerRadius = 14f * density

                if (tx > 0f) {
                    // Right swipe -> Vibrant iOS Call Green (#34C759)
                    val left = child.left.toFloat()
                    val right = child.left + tx

                    c.save()
                    bgRect.set(left, top, right, bottom)
                    bgPaint.color = Color.parseColor("#34C759")
                    c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

                    // Draw Phone Icon centered in the revealed strip
                    val iconCenterX = left + min(tx / 2f, 44f * density)
                    val iconCenterY = (top + bottom) / 2f
                    val scaleProgress = min(1.2f, 0.85f + (tx / callThresholdPx) * 0.3f)
                    drawPhoneGlyph(c, iconCenterX, iconCenterY, scaleProgress)

                    c.restore()
                } else {
                    // Left swipe -> Vibrant iOS Delete Red (#FF3B30)
                    val left = child.right + tx
                    val right = child.right.toFloat()

                    c.save()
                    bgRect.set(left, top, right, bottom)
                    bgPaint.color = Color.parseColor("#FF3B30")
                    c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

                    // Draw Delete Icon and "Delete" Text
                    val revealWidth = abs(tx)
                    val actionCenterX = right - min(revealWidth / 2f, deleteWidthPx / 2f)
                    val actionCenterY = (top + bottom) / 2f

                    drawTrashGlyphAndText(c, actionCenterX, actionCenterY)

                    c.restore()
                }
            }
        }

        private fun drawPhoneGlyph(c: Canvas, cx: Float, cy: Float, scale: Float) {
            c.save()
            c.translate(cx, cy)
            c.scale(scale, scale)

            iconPaint.strokeWidth = 2.4f * density
            iconPaint.style = Paint.Style.STROKE

            phonePath.reset()
            // Sleek curved telephone receiver path
            val r = 10f * density
            phonePath.moveTo(-r * 0.6f, -r * 0.7f)
            phonePath.quadTo(-r * 0.1f, -r * 0.9f, r * 0.4f, -r * 0.4f)
            phonePath.lineTo(r * 0.15f, -r * 0.15f)
            phonePath.quadTo(-r * 0.1f, r * 0.1f, -r * 0.5f, -r * 0.3f)
            phonePath.lineTo(-r * 0.6f, -r * 0.7f)

            // Phone receiver curve
            phonePath.reset()
            phonePath.moveTo(-7f * density, -8f * density)
            phonePath.quadTo(-4f * density, -11f * density, 0f * density, -9f * density)
            phonePath.quadTo(3f * density, -7f * density, 6f * density, -4f * density)
            phonePath.quadTo(9f * density, 0f * density, 8f * density, 4f * density)
            phonePath.quadTo(5f * density, 9f * density, 0f * density, 8f * density)
            phonePath.quadTo(-6f * density, 6f * density, -8f * density, 0f * density)
            phonePath.close()

            iconPaint.style = Paint.Style.FILL_AND_STROKE
            c.drawPath(phonePath, iconPaint)

            c.restore()
        }

        private fun drawTrashGlyphAndText(c: Canvas, cx: Float, cy: Float) {
            c.save()
            c.translate(cx, cy)

            // 1. Trash Can glyph
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

            // Vertical rib lines
            trashPath.moveTo(-tw * 0.35f, 0f)
            trashPath.lineTo(-tw * 0.3f, th * 0.7f)
            trashPath.moveTo(tw * 0.35f, 0f)
            trashPath.lineTo(tw * 0.3f, th * 0.7f)

            c.drawPath(trashPath, iconPaint)

            // 2. "Delete" label beneath trash can
            c.drawText("Delete", 0f, th + 14f * density, textPaint)

            c.restore()
        }

        private fun isCallLogViewHolder(vh: RecyclerView.ViewHolder?): Boolean {
            if (vh == null) return false
            val className = vh.javaClass.name
            return className.endsWith("DialerCallVH") || vh.itemViewType == 0
        }

        private fun isAdapterInMultiSelectMode(rv: RecyclerView): Boolean {
            return try {
                val adapter = rv.adapter ?: return false
                val e0Method = adapter.javaClass.getMethod("e0")
                e0Method.invoke(adapter) as? Boolean ?: false
            } catch (_: Throwable) {
                false
            }
        }

        private fun getDialerItemVM(vh: RecyclerView.ViewHolder?, rv: RecyclerView): Any? {
            if (vh == null) return null
            // 1. Try field f6428c0 inside DialerCallVH
            try {
                for (field: Field in vh.javaClass.declaredFields) {
                    if (field.type.name.contains("DialerItemVM")) {
                        field.isAccessible = true
                        val item = field.get(vh)
                        if (item != null) return item
                    }
                }
            } catch (_: Throwable) {}

            // 2. Try adapter.K0(pos)
            try {
                val adapter = rv.adapter ?: return null
                val pos = vh.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val k0Method = adapter.javaClass.getMethod("K0", Int::class.javaPrimitiveType)
                    return k0Method.invoke(adapter, pos)
                }
            } catch (_: Throwable) {}

            return null
        }

        /**
         * Invokes Xiaomi's stock outgoing call engine via DialerVHUtil.b(context, itemVM, "")
         */
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

        /**
         * Invokes Xiaomi's EXACT stock dialog and stock deleting logic:
         * TwelveKeyDialerFragment.U4(strV, displayName) -> prompts stock Miuix AlertDialog
         * and deletes call logs using ContactsUtils.q(context, arrayOf(strV)).
         */
        private fun invokeStockDeleteDialogAndLogic(
            context: Context,
            fragment: Any,
            itemVM: Any,
            classLoader: ClassLoader
        ) {
            // Extract normalized number and display name
            val number = try {
                val wMethod = itemVM.javaClass.getMethod("w")
                wMethod.invoke(itemVM) as? String
            } catch (_: Throwable) { null }

            val normalizedNumber = try {
                val vMethod = itemVM.javaClass.getMethod("v")
                vMethod.invoke(itemVM) as? String
            } catch (_: Throwable) { null }

            val name = try {
                val tMethod = itemVM.javaClass.getMethod("t")
                tMethod.invoke(itemVM) as? String
            } catch (_: Throwable) { null }

            val targetNumber = normalizedNumber ?: number ?: return
            val displayName = if (!name.isNullOrEmpty()) name else targetNumber

            if (!DialerRecentsConfigHolder.config.confirmDelete) {
                // Delete directly if user disabled confirmation prompt
                executeStockDelete(context, targetNumber, classLoader)
                return
            }

            // Primary method: invoke TwelveKeyDialerFragment.U4(str, str2) directly via reflection
            try {
                val u4Method = fragment.javaClass.getDeclaredMethod("U4", String::class.java, String::class.java)
                u4Method.isAccessible = true
                u4Method.invoke(fragment, targetNumber, displayName)
                Log.i(TAG, "Successfully invoked TwelveKeyDialerFragment.U4 stock delete dialog")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "TwelveKeyDialerFragment.U4 reflection failed, falling back to stock builder", t)
            }

            // Fallback: build stock-matching dialog and execute ContactsUtils.q
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
                    if (formatArgs.isNotEmpty()) {
                        context.getString(id, *formatArgs)
                    } else {
                        context.getString(id)
                    }
                } else {
                    if (formatArgs.isNotEmpty()) String.format(fallback, *formatArgs) else fallback
                }
            } catch (_: Throwable) {
                if (formatArgs.isNotEmpty()) String.format(fallback, *formatArgs) else fallback
            }
        }
    }
}
