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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * 1:1 Precision Implementation of OriginOS 7 "Fly in by layer" (逐层飞入) / iOS 26 Screen Unlock Animation.
 * Extracted and ported directly from OriginOS 7 Launcher (il.j, il.k, il.f, il.c, jj.a).
 *
 * Visual Characteristics:
 * 1. Bottom-Up Ascending Stagger Wave:
 *    Rows travel from bottom upward with progressive delay:
 *    Row at the very bottom (Row 6) arrives first, then row 5, row 4, row 3, row 2, row 1,
 *    and row 0 (top row) arrives latest with the most pronounced vertical bounce!
 * 2. Transverse Ripple (Inner vs Outer Stagger at the top):
 *    Within the top rows, inner columns settle cleanly first, while the outer corner columns
 *    arrive with an extra delay (~40-60ms) and deeper elastic overshoot, settling last.
 * 3. Smooth Two-Phase Flight & Elastic Rebound:
 *    - Phase 1: Upward travel from +160..+300px into negative overshoot (-6..-32px)
 *      with PathInterpolator(0.0f, 1.0f, 0.45f, 1.0f) + alpha fade.
 *    - Phase 2: Elastic rebound settling from overshoot back to resting 0px
 *      using PathInterpolator(0.3f, 0.0f, 0.4f, 1.0f) (inner) and PathInterpolator(0.25f, 0.08f, 0.2f, 1.0f) (outer).
 * 4. Perspective Camera Zoom:
 *    Dynamic pivot shift to screen focal center (W/2, W/2) for genuine 3D perspective zoom.
 * 5. Zero-Drop 120Hz Hardware Layer Acceleration:
 *    Uses hardware layers during motion and transitions smoothly without any single-sheet snapping.
 */
object OriginOSFlyInLayerPipeline {
    private const val TAG = "HyperBounce_OriginOS"

    // OriginOS 7 PathInterpolators (il.k, il.h, jj.a)
    private val alphaInterpolator = PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f)
    private val phase1FlightInterpolator = PathInterpolator(0.0f, 1.0f, 0.45f, 1.0f)
    private val phase2InnerReboundInterpolator = PathInterpolator(0.30f, 0.0f, 0.40f, 1.0f)
    private val phase2OuterReboundInterpolator = PathInterpolator(0.25f, 0.08f, 0.20f, 1.0f)

    private class StaggerTarget(
        val view: View,
        val actualCol: Int,
        val actualRow: Int,
        val isOuter: Boolean,
        val normDistFromCenter: Float,
        val topFactor: Float,
        val startDelay: Long,
        val p1Duration: Long,
        val p2Duration: Long,
        val initialTY: Float,
        val overshootTY: Float,
        val startScale: Float,
        val undershootScale: Float,
        var pivotX: Float = 0.0f,
        var pivotY: Float = 0.0f
    )

    private var masterAnimSet: AnimatorSet? = null
    private val activeTargets = mutableListOf<StaggerTarget>()
    private val activeDockViews = mutableListOf<View>()

    @Volatile
    var isRunning = false

    /**
     * Prepares views for unlock (called on screen off or keyguard preparation).
     */
    fun prepare(launcher: Activity) {
        launcher.runOnUiThread {
            try {
                resetInternal()
                val targets = collectTargets(launcher)
                if (targets.isEmpty()) return@runOnUiThread

                val dragLayer = getDragLayer(launcher)
                val screenW = dragLayer?.width?.takeIf { it > 0 } ?: launcher.resources.displayMetrics.widthPixels
                val focalCenterX = screenW / 2
                val focalCenterY = screenW / 2

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
                    v.translationY = target.initialTY
                    v.scaleX = target.startScale
                    v.scaleY = target.startScale
                    v.alpha = 0.0f
                }

                val dockViews = collectDockViews(launcher)
                for (d in dockViews) {
                    d.translationY = 450.0f
                    d.alpha = 0.0f
                }

                activeTargets.clear()
                activeTargets.addAll(targets)
                activeDockViews.clear()
                activeDockViews.addAll(dockViews)
                Log.d(TAG, "Prepared OriginOS Fly in by layer: ${targets.size} icons staggered bottom-up, ${dockViews.size} dock elements")
            } catch (t: Throwable) {
                Log.e(TAG, "Error in prepare", t)
            }
        }
    }

    /**
     * Executes the true bottom-up + inner-to-outer staggered unlock animation pipeline.
     */
    fun start(launcher: Activity, userPresentCompat: Any? = null) {
        launcher.runOnUiThread {
            try {
                if (isRunning) {
                    resetInternal()
                }
                isRunning = true

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

                // Enable hardware layers for zero-drop 120Hz smooth rendering
                for (t in targets) {
                    t.view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
                for (d in dockViews) {
                    d.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }

                val animators = mutableListOf<Animator>()

                // Build independent animator for each icon
                for (item in targets) {
                    val v = item.view
                    val totalDuration = item.p1Duration + item.p2Duration
                    val p2Interp = if (item.isOuter && item.topFactor > 0.35f) {
                        phase2OuterReboundInterpolator
                    } else {
                        phase2InnerReboundInterpolator
                    }

                    val anim = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                        this.duration = totalDuration
                        this.startDelay = item.startDelay
                        this.interpolator = LinearInterpolator()
                        addUpdateListener { va ->
                            val currentMs = (va.animatedFraction * totalDuration).toLong()
                            val p1Dur = item.p1Duration

                            // Translation Y & Scale
                            if (currentMs <= p1Dur) {
                                val frac = (currentMs.toFloat() / p1Dur).coerceIn(0.0f, 1.0f)
                                val spline = phase1FlightInterpolator.getInterpolation(frac)
                                v.translationY = item.initialTY + ((item.overshootTY - item.initialTY) * spline)
                                val s = item.startScale + ((item.undershootScale - item.startScale) * spline)
                                v.scaleX = s
                                v.scaleY = s
                            } else {
                                val p2Dur = item.p2Duration
                                val frac = ((currentMs - p1Dur).toFloat() / p2Dur).coerceIn(0.0f, 1.0f)
                                val spline = p2Interp.getInterpolation(frac)
                                v.translationY = item.overshootTY + ((0.0f - item.overshootTY) * spline)
                                val s = item.undershootScale + ((1.0f - item.undershootScale) * spline)
                                v.scaleX = s
                                v.scaleY = s
                            }

                            // Alpha Fade In (250ms from start of this item's movement)
                            val alphaDur = 250L
                            if (currentMs <= alphaDur) {
                                val aFrac = (currentMs.toFloat() / alphaDur).coerceIn(0.0f, 1.0f)
                                v.alpha = alphaInterpolator.getInterpolation(aFrac)
                            } else {
                                v.alpha = 1.0f
                            }
                        }
                    }
                    animators.add(anim)
                }

                // Dock / Hotseat: Launches from bottom at t=0
                if (dockViews.isNotEmpty()) {
                    val dockP1 = 200L
                    val dockP2 = 280L
                    val dockTotal = dockP1 + dockP2
                    val dockInitTY = 450.0f
                    val dockOverTY = -12.0f

                    val dockAnim = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                        this.duration = dockTotal
                        this.startDelay = 0L
                        this.interpolator = LinearInterpolator()
                        addUpdateListener { va ->
                            val currentMs = (va.animatedFraction * dockTotal).toLong()
                            var ty: Float = 0.0f
                            var alpha: Float = 1.0f
                            if (currentMs <= dockP1) {
                                val frac = (currentMs.toFloat() / dockP1).coerceIn(0.0f, 1.0f)
                                val spline = phase1FlightInterpolator.getInterpolation(frac)
                                ty = dockInitTY + ((dockOverTY - dockInitTY) * spline)
                                alpha = alphaInterpolator.getInterpolation(frac)
                            } else {
                                val frac = ((currentMs - dockP1).toFloat() / dockP2).coerceIn(0.0f, 1.0f)
                                val spline = phase2InnerReboundInterpolator.getInterpolation(frac)
                                ty = dockOverTY + ((0.0f - dockOverTY) * spline)
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
                            Log.d(TAG, "OriginOS Fly in by layer animation completed successfully")
                            cleanup(targets, dockViews)
                            isRunning = false
                            finishStateMachine(launcher, userPresentCompat)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            cleanup(targets, dockViews)
                            isRunning = false
                        }
                    })
                }

                masterAnimSet = masterSet
                masterSet.start()
                Log.d(TAG, "Started OriginOS Fly in by layer staggered animation: ${targets.size} icons")
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting OriginOS animation", t)
                isRunning = false
                resetInternal()
            }
        }
    }

    /**
     * Cancels any running animators and resets all views back to resting state.
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

    private fun cleanup(targets: List<StaggerTarget>, dockViews: List<View>) {
        for (item in targets) {
            val v = item.view
            v.scaleX = 1.0f
            v.scaleY = 1.0f
            v.alpha = 1.0f
            v.translationX = 0.0f
            v.translationY = 0.0f
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
     * Traverses the current active CellLayout, extracts exact positions, and calculates
     * staggered bottom-up delays and inner-to-outer top row settling timings.
     */
    private fun collectTargets(launcher: Activity): List<StaggerTarget> {
        val list = mutableListOf<StaggerTarget>()
        try {
            val workspace = getWorkspace(launcher) ?: return list
            val cellLayout = getCurrentCellLayout(workspace) ?: return list

            val cfg = LauncherConfigHolder.unlockConfig
            val cols = max(1, cfg.gridColumns)
            val rows = max(1, cfg.gridRows)

            val dm = launcher.resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()
            val cellWidth = screenW / cols.toFloat()
            val cellHeight = screenH / rows.toFloat()

            val childCount = cellLayout.childCount
            val loc = IntArray(2)

            for (i in 0 until childCount) {
                val child = cellLayout.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) continue

                val tagInfo = extractItemInfo(child.tag)
                child.getLocationOnScreen(loc)
                val vx = loc[0] + (child.width / 2.0f)
                val vy = loc[1] + (child.height / 2.0f)

                // Precise column & row coordinates with physical screen fallback
                val cellX = if (tagInfo != null && tagInfo.first in 0 until cols) tagInfo.first else (vx / cellWidth).toInt().coerceIn(0, cols - 1)
                val cellY = if (tagInfo != null && tagInfo.second in 0 until rows) tagInfo.second else floor(vy / cellHeight).toInt().coerceIn(0, rows - 1)

                // Bottom-Up Rank: lowest row on screen is rank 0 (starts first)
                val bottomUpRank = (rows - 1 - cellY).coerceAtLeast(0)
                val topFactor = (rows - 1 - cellY).toFloat() / max(1, rows - 1).toFloat()

                // Transverse position (inner vs outer columns)
                val centerCol = (cols - 1) / 2.0f
                val normDistFromCenter = abs(cellX - centerCol) / max(1.0f, centerCol)
                val isOuter = (cellX == 0 || cellX == cols - 1 || normDistFromCenter > 0.65f)

                // 1. Bottom-up wave delay: lower rows start first, top row starts latest
                val rowDelay = bottomUpRank * 42L

                // 2. Transverse delay: within the top rows, inner columns start & settle first,
                //    outer corner columns receive an extra delay so they arrive and bounce later
                val colDelay = if (isOuter) {
                    (28L + (topFactor * 32L)).toLong()
                } else {
                    0L
                }

                val startDelay = rowDelay + colDelay

                // Physical trajectories:
                // Initial translation Y: higher rows travel from further below
                val initialTY = 160.0f + (topFactor * 110.0f) + (if (isOuter) 25.0f * topFactor else 0.0f)

                // Overshoot: bottom rows barely overshoot (-6px), top rows overshoot deeper (-22px inner, -32px outer)
                val overshootTY = - (6.0f + (topFactor * 16.0f) + (if (isOuter) 10.0f * topFactor else 0.0f))

                // Phase 1 flight duration (220ms - 270ms)
                val p1Duration = 220L + (topFactor * 50L).toLong()

                // Phase 2 rebound duration: bottom rows settle fast (240ms), top inner (330ms),
                // top outer corners float and settle last (420ms)
                val p2Duration = 240L + (topFactor * 90L).toLong() + (if (isOuter) (topFactor * 90L).toLong() else 0L)

                // Scale: starts enlarged (1.22x - 1.38x), undershoots to 0.975x, settles to 1.000x
                val startScale = 1.22f + (topFactor * 0.16f)
                val undershootScale = 0.975f + (0.015f * (1.0f - topFactor))

                list.add(
                    StaggerTarget(
                        view = child,
                        actualCol = cellX,
                        actualRow = cellY,
                        isOuter = isOuter,
                        normDistFromCenter = normDistFromCenter,
                        topFactor = topFactor,
                        startDelay = startDelay,
                        p1Duration = p1Duration,
                        p2Duration = p2Duration,
                        initialTY = initialTY,
                        overshootTY = overshootTY,
                        startScale = startScale,
                        undershootScale = undershootScale
                    )
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error collecting targets", t)
        }
        return list
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
