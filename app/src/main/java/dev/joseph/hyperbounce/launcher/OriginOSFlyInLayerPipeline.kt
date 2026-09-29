package dev.joseph.hyperbounce.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * 1:1 Precision Implementation of OriginOS 7 "Fly in by layer" (逐层飞入) Screen Unlock Animation.
 * Extracted and ported directly from OriginOS 7 Launcher (il.f, il.c, il.g, jj.a).
 *
 * Architecture (Flagship Profile):
 * 1. 6-Ring Concentric Shell Partitioning radiating outward from the focal center.
 * 2. Radial Camera Zoom Illusion via dynamic pivot shifting:
 *    pivotX = screenCenter.x - view.x
 *    pivotY = screenCenter.y - view.y
 * 3. Two-Phase Execution:
 *    - Phase 1 (550ms): Staggered 56ms shell arrival, scaling 6.0x -> undershoot (0.975 - 0.990)
 *      with PathInterpolator(0.0f, 1.0f, 0.5f, 1.0f), alpha 0.0 -> 1.0 (300ms, PathInterpolator(0.33, 0.0, 0.67, 1.0)),
 *      Dock shoots from +900px -> -15px (delay 200ms, duration 400ms).
 *    - Phase 2 (400ms / 450ms): Elastic rebound from undershoot -> 1.0x with PathInterpolator(0.3, 0.0, 0.4, 1.0),
 *      Dock settles from -15px -> 0px.
 */
object OriginOSFlyInLayerPipeline {
    private const val TAG = "HyperBounce_OriginOS"

    // Flagship Profile Constants from OriginOS 7 (il.g, il.k, jj.a)
    private const val SCALE_START = 6.0f
    private const val UNDERSHOOT_MIN = 0.975f
    private const val UNDERSHOOT_MAX = 0.990f

    private const val PHASE1_BASE_DURATION = 550L
    private const val RING_STAGGER_STEP = 56L
    private const val ALPHA_DURATION = 300L

    private const val PHASE2_REBOUND_DURATION = 400L
    private const val DOCK_REBOUND_DURATION = 450L

    private const val DOCK_INITIAL_TY = 900.0f
    private const val DOCK_OVERSHOOT_TY = -15.0f

    // PathInterpolators from OriginOS 7:
    // Alpha: PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f)
    private val alphaInterpolator = PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f)
    // Phase 1 Scale & Dock: PathInterpolator(0.0f, 1.0f, 0.5f, 1.0f) (jj.a.b())
    private val phase1ScaleInterpolator = PathInterpolator(0.0f, 1.0f, 0.5f, 1.0f)
    // Phase 2 Rebound Scale & Dock: PathInterpolator(0.3f, 0.0f, 0.4f, 1.0f) (jj.a.c())
    private val phase2ReboundInterpolator = PathInterpolator(0.3f, 0.0f, 0.4f, 1.0f)

    private class RingTarget(
        val view: View,
        val ringLevel: Int,
        val cellX: Int,
        val cellY: Int,
        val undershootScale: Float,
        val startDelay: Long,
        var pivotX: Float = 0.0f,
        var pivotY: Float = 0.0f
    )

    private var masterAnimSet: AnimatorSet? = null
    private val activeTargets = mutableListOf<RingTarget>()
    private val activeDockViews = mutableListOf<View>()

    @Volatile
    var isRunning = false

    /**
     * Prepares views for unlock (called on screen off or when keyguard is preparing).
     */
    fun prepare(launcher: Activity) {
        launcher.runOnUiThread {
            try {
                resetInternal()
                val targets = collectTargets(launcher)
                if (targets.isEmpty()) return@runOnUiThread

                val dragLayer = getDragLayer(launcher)
                val screenW = dragLayer?.width?.takeIf { it > 0 } ?: launcher.resources.displayMetrics.widthPixels
                val screenH = dragLayer?.height?.takeIf { it > 0 } ?: launcher.resources.displayMetrics.heightPixels
                // OriginOS focal center: [measuredWidth / 2, measuredWidth / 2] (stock Vivo center)
                val focalCenterX = screenW / 2
                val focalCenterY = screenW / 2

                val cfg = LauncherConfigHolder.unlockConfig
                val startScale = cfg.originosDepthScale

                val pos = IntArray(2)
                for (target in targets) {
                    val v = target.view
                    v.getLocationOnScreen(pos)
                    val pX = (focalCenterX - pos[0]).toFloat()
                    val pY = (focalCenterY - pos[1]).toFloat()
                    target.pivotX = pX
                    target.pivotY = pY

                    v.pivotX = pX
                    v.pivotY = pY
                    v.scaleX = startScale
                    v.scaleY = startScale
                    v.alpha = 0.0f
                    v.translationX = 0.0f
                    v.translationY = 0.0f
                }

                val dockViews = collectDockViews(launcher)
                for (d in dockViews) {
                    d.translationY = DOCK_INITIAL_TY
                    d.alpha = 0.0f
                }

                activeTargets.clear()
                activeTargets.addAll(targets)
                activeDockViews.clear()
                activeDockViews.addAll(dockViews)
                Log.d(TAG, "Prepared OriginOS Fly in by layer: ${targets.size} icons across 6 rings, ${dockViews.size} dock elements")
            } catch (t: Throwable) {
                Log.e(TAG, "Error in prepare", t)
            }
        }
    }

    /**
     * Executes the center-pivoted OriginOS 7 "Fly in by layer" animation pipeline.
     * Preserves exact stock radial motion, pivots, undershoot formulas, and interpolators,
     * while giving each row independent, fluid, non-blocking 2-phase flight and elastic rebound.
     */
    fun start(launcher: Activity, userPresentCompat: Any? = null) {
        launcher.runOnUiThread {
            try {
                if (isRunning) {
                    resetInternal()
                }
                isRunning = true

                // Collect targets if not already prepared
                if (activeTargets.isEmpty()) {
                    prepare(launcher)
                }
                if (activeTargets.isEmpty()) {
                    Log.w(TAG, "No active targets found for OriginOS animation")
                    isRunning = false
                    finishStateMachine(launcher, userPresentCompat)
                    return@runOnUiThread
                }

                val targets = ArrayList(activeTargets)
                val dockViews = ArrayList(activeDockViews)

                // Enable hardware layers for 120Hz smooth rendering during animation
                for (t in targets) {
                    t.view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
                for (d in dockViews) {
                    d.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }

                val cfg = LauncherConfigHolder.unlockConfig
                val startScale = cfg.originosDepthScale
                val p1Dur = PHASE1_BASE_DURATION // 550ms stock
                val p2Dur = PHASE2_REBOUND_DURATION // 400ms stock
                val totalDur = p1Dur + p2Dur // 950ms total continuous flight

                val animators = mutableListOf<Animator>()

                // Group targets by startDelay (each row/delay group moves as a continuous 2-phase unit)
                val delayGroups = targets.groupBy { it.startDelay }
                for ((delay, groupItems) in delayGroups) {
                    val anim = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                        this.duration = totalDur
                        this.startDelay = delay
                        this.interpolator = LinearInterpolator()
                        addUpdateListener { va ->
                            val currentMs = (va.animatedFraction * totalDur).toLong()

                            // Scale: Continuous 2-phase flight (Phase 1 Zoom -> Phase 2 Rebound)
                            if (currentMs <= p1Dur) {
                                val frac = (currentMs.toFloat() / p1Dur).coerceIn(0.0f, 1.0f)
                                val spline = phase1ScaleInterpolator.getInterpolation(frac)
                                for (item in groupItems) {
                                    val s = startScale + ((item.undershootScale - startScale) * spline)
                                    item.view.scaleX = s
                                    item.view.scaleY = s
                                }
                            } else {
                                val frac = ((currentMs - p1Dur).toFloat() / p2Dur).coerceIn(0.0f, 1.0f)
                                val spline = phase2ReboundInterpolator.getInterpolation(frac)
                                for (item in groupItems) {
                                    val s = item.undershootScale + ((1.0f - item.undershootScale) * spline)
                                    item.view.scaleX = s
                                    item.view.scaleY = s
                                }
                            }

                            // Desktop icons strictly maintain translationX = 0, translationY = 0
                            for (item in groupItems) {
                                item.view.translationX = 0.0f
                                item.view.translationY = 0.0f
                            }

                            // Alpha: 300ms fade-in from the moment this row begins its movement
                            if (currentMs <= ALPHA_DURATION) {
                                val aFrac = (currentMs.toFloat() / ALPHA_DURATION).coerceIn(0.0f, 1.0f)
                                val a = alphaInterpolator.getInterpolation(aFrac)
                                for (item in groupItems) {
                                    item.view.alpha = a
                                }
                            } else {
                                for (item in groupItems) {
                                    item.view.alpha = 1.0f
                                }
                            }
                        }
                    }
                    animators.add(anim)
                }

                // Dock / Hotseat: shoots upward from +900px -> -15px overshoot -> 0px settle
                if (dockViews.isNotEmpty()) {
                    val dockP1 = 400L
                    val dockP2 = DOCK_REBOUND_DURATION // 450L
                    val dockTotal = dockP1 + dockP2
                    val rows = max(1, cfg.gridRows)
                    val centerRow = if (rows >= 6) 2 else (rows / 2)
                    val maxRow = targets.maxOfOrNull { it.cellY } ?: centerRow
                    val bottomRowDelay = ((maxRow - centerRow).coerceAtLeast(0) * cfg.originosLowerRowDelay).toLong()
                    val dockDelay = bottomRowDelay + 50L

                    val dockAnim = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                        this.duration = dockTotal
                        this.startDelay = dockDelay
                        this.interpolator = LinearInterpolator()
                        addUpdateListener { va ->
                            val currentMs = (va.animatedFraction * dockTotal).toLong()
                            var ty = 0.0f
                            var alpha = 1.0f
                            if (currentMs <= dockP1) {
                                val frac = (currentMs.toFloat() / dockP1).coerceIn(0.0f, 1.0f)
                                val spline = phase1ScaleInterpolator.getInterpolation(frac)
                                ty = DOCK_INITIAL_TY + ((DOCK_OVERSHOOT_TY - DOCK_INITIAL_TY) * spline)
                                alpha = alphaInterpolator.getInterpolation(frac)
                            } else {
                                val frac = ((currentMs - dockP1).toFloat() / dockP2).coerceIn(0.0f, 1.0f)
                                val spline = phase2ReboundInterpolator.getInterpolation(frac)
                                ty = DOCK_OVERSHOOT_TY + ((0.0f - DOCK_OVERSHOOT_TY) * spline)
                                alpha = 1.0f
                            }
                            for (d in dockViews) {
                                d.translationY = ty
                                d.alpha = alpha
                            }
                        }
                    }
                    animators.add(dockAnim)
                }

                val masterSet = AnimatorSet().apply {
                    playTogether(animators)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            Log.d(TAG, "OriginOS animation finished, restoring resting view state")
                            cleanup(targets, dockViews)
                            OriginOSFlyInLayerPipeline.isRunning = false
                            finishStateMachine(launcher, userPresentCompat)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            cleanup(targets, dockViews)
                            OriginOSFlyInLayerPipeline.isRunning = false
                        }
                    })
                }

                masterAnimSet = masterSet
                masterSet.start()
                Log.d(TAG, "Started OriginOS Fly in by layer animation successfully: ${targets.size} icons")
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting OriginOS animation", t)
                isRunning = false
                resetInternal()
            }
        }
    }

    /**
     * Cancels any running animators and resets all views back to standard resting state.
     */
    fun reset(launcher: Activity? = null) {
        if (launcher != null) {
            launcher.runOnUiThread {
                resetInternal()
                val targets = collectTargets(launcher)
                val dockViews = collectDockViews(launcher)
                cleanup(targets, dockViews)
            }
        } else {
            resetInternal()
        }
    }

    private fun resetInternal() {
        isRunning = false
        try {
            masterAnimSet?.cancel()
            masterAnimSet?.removeAllListeners()
            masterAnimSet = null
        } catch (_: Throwable) {}
    }

    private fun cleanup(targets: List<RingTarget>, dockViews: List<View>) {
        for (item in targets) {
            val v = item.view
            v.scaleX = 1.0f
            v.scaleY = 1.0f
            v.alpha = 1.0f
            v.translationX = 0.0f
            v.translationY = 0.0f
            // Restore pivot to view's own center
            v.pivotX = v.width / 2.0f
            v.pivotY = v.height / 2.0f
            v.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        for (d in dockViews) {
            d.translationY = 0.0f
            d.alpha = 1.0f
            d.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun finishStateMachine(launcher: Activity, userPresentCompat: Any?) {
        try {
            if (userPresentCompat != null) {
                val fIsShowing = try {
                    userPresentCompat.javaClass.getDeclaredField("mIsShowingAnimation").apply { isAccessible = true }
                } catch (_: Throwable) {
                    userPresentCompat.javaClass.superclass?.getDeclaredField("mIsShowingAnimation")?.apply { isAccessible = true }
                }
                fIsShowing?.setBoolean(userPresentCompat, false)
            }
            val fStateMachine = launcher.javaClass.getDeclaredField("mUnlockAnimationStateMachine").apply { isAccessible = true }
            val stateMachine = fStateMachine.get(launcher)
            if (stateMachine != null) {
                val fState = stateMachine.javaClass.getDeclaredField("mState").apply { isAccessible = true }
                val stateEnumClass = fState.type
                @Suppress("UNCHECKED_CAST")
                val idleEnum = java.lang.Enum.valueOf(stateEnumClass as Class<out Enum<*>>, "IDLE")
                fState.set(stateMachine, idleEnum)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Traverses the current active CellLayout and classifies each icon into rings 1..6.
     */
    private fun collectTargets(launcher: Activity): List<RingTarget> {
        val list = mutableListOf<RingTarget>()
        try {
            val workspace = getWorkspace(launcher) ?: return list
            val cellLayout = getCurrentCellLayout(workspace) ?: return list

            val cfg = LauncherConfigHolder.unlockConfig
            val cols = max(1, cfg.gridColumns)
            val rows = max(1, cfg.gridRows)

            val childCount = cellLayout.childCount
            for (i in 0 until childCount) {
                val child = cellLayout.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) continue

                val tagInfo = extractItemInfo(child.tag)
                val cellX = if (tagInfo != null && tagInfo.first in 0 until cols) tagInfo.first else 0
                val cellY = if (tagInfo != null && tagInfo.second in 0 until rows) tagInfo.second else 0
                val spanX = tagInfo?.third ?: 1
                val spanY = tagInfo?.fourth ?: 1

                val ring = classifyConcentricRing(cellX, cellY, spanX, spanY, cols, rows)
                // Undershoot target formula from il.c H(i10):
                // fH = 0.975f + (((0.990f - 0.975f) / rows) * (rows - ring))
                val undershoot = UNDERSHOOT_MIN + (((UNDERSHOOT_MAX - UNDERSHOOT_MIN) / rows.toFloat()) * (rows - ring))

                val centerRow = if (rows >= 6) 2 else (rows / 2)
                val delay = when {
                    cellY > centerRow -> ((cellY - centerRow) * cfg.originosLowerRowDelay).toLong()
                    cellY < centerRow -> ((centerRow - cellY) * cfg.originosUpperRowDelay).toLong()
                    else -> 0L
                }

                list.add(RingTarget(child, ring, cellX, cellY, undershoot, delay))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error collecting targets", t)
        }
        return list
    }

    /**
     * Exact 1:1 concentric shell ring classification from OriginOS 7 il.f:
     * Rings radiate outward from center rows 1-2.
     */
    private fun classifyConcentricRing(
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int
    ): Int {
        val iK9 = if (cellX <= (cols / 2) - 1 || spanX <= 1) cellX else (spanX + cellX) - 1
        val row = cellY

        // Level 1: Center Core (rows 1-2, inner columns)
        if (iK9 in 1..(cols - 2) && row in 1..2) {
            return 1
        }
        // Level 2: Inner Ring surrounding core
        if (row <= 3) {
            if ((iK9 == 0 || iK9 == cols - 1) && row in 1..2) return 2
            if ((row == 0 || row == 3) && iK9 in 1 until cols - 1) return 2
        }
        // Level 3: Corners / Mid Ring
        if (row <= 4) {
            if ((iK9 == 0 || iK9 == cols - 1) && (row == 0 || row == 3)) return 3
            if (row == 4 && iK9 in 1 until cols - 1) return 3
        }
        // Level 4, 5, 6: Outer perimeter & Bottom rows
        if (row == 4 && (iK9 == 0 || iK9 == cols - 1)) return 4
        if (row == 5 && iK9 in 1..(cols - 2)) return 4
        if (row == 5) return 5
        if (row >= 6) {
            return if (iK9 == 0 || iK9 == cols - 1) 6 else 5
        }
        return 3
    }

    private fun collectDockViews(launcher: Activity): List<View> {
        val list = mutableListOf<View>()
        try {
            val getHsMethod = try { launcher.javaClass.getMethod("getHotSeats") } catch (_: Throwable) { null }
            val hotseats = getHsMethod?.invoke(launcher) as? View
            if (hotseats != null && hotseats.visibility == View.VISIBLE) {
                list.add(hotseats)
            }

            val getIndMethod = try { launcher.javaClass.getMethod("getLauncherIndicator") } catch (_: Throwable) { null }
            val indicator = getIndMethod?.invoke(launcher) as? View
            if (indicator != null && indicator.visibility == View.VISIBLE) {
                list.add(indicator)
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun getDragLayer(launcher: Activity): ViewGroup? {
        return try {
            val method = launcher.javaClass.getMethod("getDragLayer")
            method.invoke(launcher) as? ViewGroup
        } catch (_: Throwable) {
            launcher.findViewById(android.R.id.content)
        }
    }

    private fun getWorkspace(launcher: Activity): ViewGroup? {
        return try {
            val method = launcher.javaClass.getMethod("getWorkspace")
            method.invoke(launcher) as? ViewGroup
        } catch (_: Throwable) { null }
    }

    private fun getCurrentCellLayout(workspace: ViewGroup): ViewGroup? {
        return try {
            val method = workspace.javaClass.getMethod("getCurrentCellLayout")
            method.invoke(workspace) as? ViewGroup
        } catch (_: Throwable) {
            // Fallback: get current screen child
            try {
                val getIndexMethod = workspace.javaClass.getMethod("getCurrentScreenIndex")
                val idx = getIndexMethod.invoke(workspace) as? Int ?: 0
                val screen = workspace.getChildAt(idx) as? ViewGroup
                if (screen != null) {
                    val getClMethod = try { screen.javaClass.getMethod("getCellLayout") } catch (_: Throwable) { null }
                    getClMethod?.invoke(screen) as? ViewGroup ?: screen
                } else null
            } catch (_: Throwable) { null }
        }
    }

    private fun extractItemInfo(tag: Any?): Quad<Int, Int, Int, Int>? {
        if (tag == null) return null
        var curr: Class<*>? = tag.javaClass
        var cx: Int? = null
        var cy: Int? = null
        var sx: Int? = null
        var sy: Int? = null
        while (curr != null && curr != Any::class.java) {
            if (cx == null) {
                try {
                    val f = curr.getDeclaredField("cellX").apply { isAccessible = true }
                    cx = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (cy == null) {
                try {
                    val f = curr.getDeclaredField("cellY").apply { isAccessible = true }
                    cy = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (sx == null) {
                try {
                    val f = curr.getDeclaredField("spanX").apply { isAccessible = true }
                    sx = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (sy == null) {
                try {
                    val f = curr.getDeclaredField("spanY").apply { isAccessible = true }
                    sy = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (cx != null && cy != null && sx != null && sy != null) break
            curr = curr.superclass
        }
        return Quad(cx ?: -1, cy ?: -1, sx ?: 1, sy ?: 1)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
