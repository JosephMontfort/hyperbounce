package dev.joseph.hyperbounce.controlcenter

import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap

/**
 * High-performance iOS 26 Jelly Physics Engine for HyperOS Control Center.
 *
 * Implements:
 * 1. Poisson volume conservation: vertical tension generates lateral squish (nu approx 0.38).
 * 2. Asymmetric Right-to-Left Wave propagation: items on the right react first;
 *    items to the left experience a subtle, organic phase delay wave.
 * 3. Dynamic Pulldown: stretches top-anchored components during entry and
 *    overscroll pull-down when CC is already fully open.
 * 4. Damped Harmonic Oscillator Rebound: upon release of pull/overscroll, items
 *    spring past neutral 1.0f into compression (scaleY < 1.0, scaleX > 1.0),
 *    oscillate with realistic decay, and settle cleanly like a physical jelly bubble.
 * 5. Self-driven frame ticker: guarantees zero view freezing even if SystemUI
 *    stops calling onFrameCallback before spring settling completes.
 */
object JellyPhysicsEngine {

    private const val POISSON_RATIO = 0.38f
    private const val SPRING_OMEGA = 22.0f      // Natural frequency (rad/s) ~ 3.5 Hz
    private const val DAMPING_RATIO = 0.44f     // Sub-critically damped oscillation
    private const val GAMMA = DAMPING_RATIO * SPRING_OMEGA // ~ 9.68 decay rate
    private const val MAX_SETTLE_TIME_SEC = 0.52f

    class ReboundState(val view: View) : Runnable {
        var isRebounding: Boolean = false
        var initialStrain: Float = 0.0f
        var releaseTimeMs: Long = 0L
        var rtlDelayMs: Long = 0L
        var peakStretchY: Float = 0.0f
        var lastExpandTransY: Float = 0.0f
        var isPulling: Boolean = false

        override fun run() {
            if (!isRebounding || !view.isAttachedToWindow) {
                stopAndReset(view)
                return
            }
            val now = SystemClock.uptimeMillis()
            val elapsedSec = (now - releaseTimeMs - rtlDelayMs) / 1000.0f

            if (elapsedSec < 0.0f) {
                // Wave hasn't arrived at this column yet; maintain stretch until wave arrives
                applyScale(view, initialStrain, 0.0f)
                view.postOnAnimation(this)
                return
            }

            if (elapsedSec >= MAX_SETTLE_TIME_SEC) {
                // Settling complete
                stopAndReset(view)
                return
            }

            // Damped harmonic oscillator: x(t) = A0 * e^(-gamma*t) * cos(omega*t)
            val decay = Math.exp((-GAMMA * elapsedSec).toDouble()).toFloat()
            val oscillation = Math.cos((SPRING_OMEGA * elapsedSec).toDouble()).toFloat()
            val strain = initialStrain * decay * oscillation

            // Pivot progression from top anchor (0.10f) to center (0.50f) as oscillation settles
            val settleWeight = (elapsedSec / MAX_SETTLE_TIME_SEC).coerceIn(0.0f, 1.0f)
            applyScale(view, strain, settleWeight)

            // Continue self-driven ticking for fluid 120Hz/90Hz/60Hz animation
            view.postOnAnimation(this)
        }

        fun stopAndReset(v: View) {
            isRebounding = false
            peakStretchY = 0.0f
            lastExpandTransY = 0.0f
            isPulling = false
            v.removeCallbacks(this)
            v.pivotX = v.width / 2.0f
            v.pivotY = v.height / 2.0f
            v.scaleX = 1.0f
            v.scaleY = 1.0f
        }
    }

    private val viewStates = WeakHashMap<View, ReboundState>()
    private val sTempLocation = ThreadLocal<IntArray>()

    private fun getState(view: View): ReboundState {
        return viewStates.getOrPut(view) { ReboundState(view) }
    }

    private fun applyScale(view: View, strainY: Float, settleWeight: Float) {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return

        val scaleY = (1.0f + strainY).coerceIn(0.85f, 1.35f)
        val scaleX = (1.0f - strainY * POISSON_RATIO).coerceIn(0.85f, 1.25f)

        view.pivotX = w / 2.0f
        view.pivotY = h * (0.10f + 0.40f * settleWeight)
        view.scaleX = scaleX
        view.scaleY = scaleY
    }

    /**
     * Called on each Choreographer frame from MainPanelItemViewHolder.onFrameCallback
     */
    fun onFrame(
        targetView: View,
        holder: Any?,
        progress: Float,
        expandTransY: Float,
        row: Int
    ) {
        if (!ControlCenterConfigHolder.config.isJellyActive) {
            val state = viewStates[targetView]
            if (state != null && state.isRebounding) {
                state.stopAndReset(targetView)
            } else {
                targetView.scaleX = 1.0f
                targetView.scaleY = 1.0f
            }
            return
        }

        val w = targetView.width
        val h = targetView.height
        if (w <= 0 || h <= 0) return

        val state = getState(targetView)

        // Calculate spatial Right-to-Left normalized coordinate (0 = rightmost, 1 = leftmost)
        val screenW = targetView.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        var loc = sTempLocation.get()
        if (loc == null) {
            loc = IntArray(2)
            sTempLocation.set(loc)
        }
        targetView.getLocationOnScreen(loc)
        val centerX = loc[0] + w / 2.0f
        val rtlFactor = (1.0f - (centerX / screenW)).coerceIn(0.0f, 1.0f)

        val isFlicking = ControlCenterConfigHolder.sIsUserFlicking
        val isRecentGesture = (System.currentTimeMillis() - ControlCenterConfigHolder.sLastTouchTime) < 700
        val isClosing = (progress < 0.99f && ControlCenterConfigHolder.sCurrentVelocityY < -100.0f)

        if (isClosing) {
            state.stopAndReset(targetView)
            return
        }

        // Height dampener to keep large/small cards feeling proportionally weighted
        val density = targetView.resources.displayMetrics.density
        val standardCardHeight = 110.0f * density
        val heightDampener = Math.max(0.42f, Math.min(1.0f, standardCardHeight / h))

        // Check if currently pulling down:
        // 1. Expanding pulldown: progress in (0.01..0.99) while flicking or recent gesture
        // 2. Overscroll pulldown: while finger is down (isFlicking) and expandTransY > 1.5f,
        //    or during fling expansion while expandTransY is actively increasing
        val isExpandingPull = progress in 0.01f..0.99f && (isFlicking || isRecentGesture)
        val isOverscrollPull = (isFlicking && expandTransY > 1.5f) ||
                (isRecentGesture && expandTransY > 1.5f && expandTransY > state.lastExpandTransY)

        if (isExpandingPull || isOverscrollPull) {
            // Actively pulling down! Cancel any active rebound and track strain.
            state.isRebounding = false
            state.isPulling = true
            targetView.removeCallbacks(state)

            val absVel = Math.abs(ControlCenterConfigHolder.sCurrentVelocityY)
            val velFactor = if (absVel > 0.1f) Math.max(0.35f, Math.min(1.20f, absVel / 3500.0f)) else 0.70f

            var enterStretchY = 0.0f
            if (isExpandingPull) {
                // Right-to-left wave during pulldown entry
                val effectiveProgress = (progress - rtlFactor * 0.05f).coerceIn(0.0f, 1.0f)
                val momentum = Math.sin(effectiveProgress * Math.PI).toFloat()
                val rowStagger = 1.0f + (row * 0.035f)
                enterStretchY = momentum * 0.095f * velFactor * heightDampener * rowStagger * (1.0f - rtlFactor * 0.12f)
            }

            var overscrollStretchY = 0.0f
            if (expandTransY > 0.0f) {
                // Right-to-left wave during overscroll pull-down
                val effectiveTransY = (expandTransY - rtlFactor * 20.0f).coerceAtLeast(0.0f)
                val strain = Math.min(0.45f, effectiveTransY / 750.0f)
                overscrollStretchY = strain * 0.30f * heightDampener * (1.0f - rtlFactor * 0.10f)
            }

            val totalStrain = enterStretchY + overscrollStretchY
            state.peakStretchY = Math.max(state.peakStretchY, totalStrain)
            state.lastExpandTransY = expandTransY

            applyScale(targetView, totalStrain, 0.0f)
            return
        }

        // Detect Release: transition from pull to release
        if (state.isPulling) {
            state.isPulling = false
            if (state.peakStretchY > 0.018f) {
                // Trigger Damped Spring Rebound!
                state.isRebounding = true
                state.initialStrain = state.peakStretchY
                state.releaseTimeMs = SystemClock.uptimeMillis()
                // Right-to-left stagger delay for the rebound ripple (up to 50ms)
                state.rtlDelayMs = (rtlFactor * 50.0f).toLong()

                // Launch self-driven animation ticker on the view
                targetView.removeCallbacks(state)
                targetView.postOnAnimation(state)
                return
            } else {
                state.stopAndReset(targetView)
                return
            }
        }

        if (state.isRebounding) {
            // Spring is currently ticking via state.run()
            return
        }

        // At resting state with no active stretch
        if (!isFlicking && !isRecentGesture && Math.abs(expandTransY) < 1.0f) {
            if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                state.stopAndReset(targetView)
            }
        }
    }
}
