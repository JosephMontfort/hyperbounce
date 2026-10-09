package dev.joseph.hyperbounce.controlcenter

import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap

/**
 * High-performance iOS 26 Jelly Physics Engine for HyperOS Control Center.
 * Analyzed from frame-by-frame 30fps capture of iOS Control Center.
 *
 * Implements:
 * 1. Unified physical strain: directly coupled to pulldown progress, velocity, and overscroll.
 * 2. Continuous spring recoil: at release, immediately recoils with initial strain and negative velocity.
 * 3. Exact iOS Damped Harmonic Oscillator:
 *    Duration ~250ms (7-8 frames at 30fps), zeta = 0.65, omega = 26 rad/s.
 *    Single crisp overshoot into squash/compression (scaleY < 1.0, scaleX > 1.0),
 *    Poisson volume conservation (nu = 0.32), followed by smooth settling to 1.0.
 * 4. Asymmetric Right-to-Left Wave propagation: items on the right react first,
 *    left items ripple with ~25ms organic phase delay.
 * 5. Persistent frame scale application: updates on EVERY frame of onFrameCallback
 *    to prevent stock HyperOS from overwriting scale with 1.0f.
 * 6. Self-driven ticker: smoothly finishes the settling animation even if HyperOS halts onFrameCallback.
 */
object JellyPhysicsEngine {

    private const val POISSON_RATIO = 0.32f
    private const val SPRING_OMEGA = 26.0f      // Natural frequency (rad/s) ~ 4.1 Hz
    private const val DAMPING_RATIO = 0.65f     // Sub-critically damped: exactly one visible rebound
    private const val GAMMA = DAMPING_RATIO * SPRING_OMEGA // ~ 16.9 decay rate
    private val OMEGA_D = (SPRING_OMEGA * Math.sqrt((1.0 - DAMPING_RATIO * DAMPING_RATIO).toDouble())).toFloat() // ~ 19.76 rad/s
    private const val MAX_SETTLE_TIME_SEC = 0.26f // Settle cleanly in 260ms (matches video!)

    class ReboundState(val view: View) : Runnable {
        var isRebounding: Boolean = false
        var isPulling: Boolean = false
        var peakStrain: Float = 0.0f
        var initialStrain: Float = 0.0f
        var releaseTimeMs: Long = 0L
        var rtlDelayMs: Long = 0L
        var lastExpandTransY: Float = 0.0f

        fun triggerRebound(strain: Float, delayMs: Long) {
            isPulling = false
            isRebounding = true
            initialStrain = strain
            peakStrain = strain
            releaseTimeMs = SystemClock.uptimeMillis()
            rtlDelayMs = delayMs
            view.removeCallbacks(this)
            view.postOnAnimation(this)
        }

        fun tick(now: Long = SystemClock.uptimeMillis()) {
            if (!isRebounding) return
            if (!view.isAttachedToWindow) {
                stopAndReset()
                return
            }

            val elapsedSec = (now - releaseTimeMs - rtlDelayMs) / 1000.0f

            if (elapsedSec < 0.0f) {
                // Wave has not reached this column yet; hold stretch until wave arrives
                applyScale(view, initialStrain, 0.0f)
                return
            }

            if (elapsedSec >= MAX_SETTLE_TIME_SEC) {
                stopAndReset()
                return
            }

            // Exact iOS damped harmonic oscillator:
            // x(t) = A0 * e^(-gamma*t) * [cos(omega_d*t) - (gamma/omega_d)*sin(omega_d*t)]
            // At t=0: x(0) = A0. v(0) < 0 (immediately recoils towards neutral and squashes)
            val decay = Math.exp((-GAMMA * elapsedSec).toDouble()).toFloat()
            val osc = Math.cos((OMEGA_D * elapsedSec).toDouble()).toFloat() -
                    (GAMMA / OMEGA_D) * Math.sin((OMEGA_D * elapsedSec).toDouble()).toFloat()
            val strain = initialStrain * decay * osc

            val settleWeight = (elapsedSec / MAX_SETTLE_TIME_SEC).coerceIn(0.0f, 1.0f)
            applyScale(view, strain, settleWeight)
        }

        override fun run() {
            if (!isRebounding) return
            tick(SystemClock.uptimeMillis())
            if (isRebounding) {
                view.postOnAnimation(this)
            }
        }

        fun stopAndReset() {
            isRebounding = false
            isPulling = false
            peakStrain = 0.0f
            initialStrain = 0.0f
            lastExpandTransY = 0.0f
            view.removeCallbacks(this)
            view.pivotX = view.width / 2.0f
            view.pivotY = view.height / 2.0f
            view.scaleX = 1.0f
            view.scaleY = 1.0f
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

        val scaleY = (1.0f + strainY).coerceIn(0.88f, 1.25f)
        val scaleX = (1.0f - strainY * POISSON_RATIO).coerceIn(0.90f, 1.15f)

        view.pivotX = w / 2.0f
        // Top-anchored during stretch, smoothly centering as spring settles
        view.pivotY = h * (0.10f + 0.40f * settleWeight)
        view.scaleX = scaleX
        view.scaleY = scaleY
    }

    /**
     * Called on each frame from MainPanelItemViewHolder.onFrameCallback and MultipleAnimViewHolder.onFrameCallback
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
            if (state != null && (state.isRebounding || state.isPulling)) {
                state.stopAndReset()
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
        val now = SystemClock.uptimeMillis()

        // Calculate Right-to-Left wave factor (0 = right edge, 1 = left edge)
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
            state.stopAndReset()
            return
        }

        // Height dampener to normalize tall cards (sliders) vs standard circular/squircle tiles
        val density = targetView.resources.displayMetrics.density
        val standardCardHeight = 110.0f * density
        val heightDampener = Math.max(0.40f, Math.min(1.0f, standardCardHeight / h))

        // Check if currently actively pulling:
        // 1. Expanding pulldown entry (progress in 0.01..0.99)
        // 2. Overscroll pulldown when already open (finger is down and expandTransY > 1.5f,
        //    or during fling while expandTransY is increasing)
        val isExpandingPull = progress in 0.01f..0.99f && (isFlicking || isRecentGesture)
        val isOverscrollPull = (isFlicking && expandTransY > 1.5f) ||
                (isRecentGesture && expandTransY > 1.5f && expandTransY > state.lastExpandTransY)

        if (isExpandingPull || isOverscrollPull) {
            state.isRebounding = false
            state.isPulling = true
            targetView.removeCallbacks(state)

            val absVel = Math.abs(ControlCenterConfigHolder.sCurrentVelocityY)
            val velFactor = if (absVel > 0.1f) Math.max(0.35f, Math.min(1.15f, absVel / 3500.0f)) else 0.70f

            var enterStretchY = 0.0f
            if (isExpandingPull) {
                // Right-to-left wave during entry
                val effectiveProgress = (progress - rtlFactor * 0.04f).coerceIn(0.0f, 1.0f)
                val momentum = Math.sin(effectiveProgress * Math.PI).toFloat()
                val rowStagger = 1.0f + (row * 0.030f)
                enterStretchY = momentum * 0.085f * velFactor * heightDampener * rowStagger * (1.0f - rtlFactor * 0.10f)
            }

            var overscrollStretchY = 0.0f
            if (expandTransY > 0.0f) {
                // Right-to-left wave during overscroll
                val effectiveTransY = (expandTransY - rtlFactor * 16.0f).coerceAtLeast(0.0f)
                val strain = Math.min(0.28f, effectiveTransY / 900.0f)
                overscrollStretchY = strain * 0.32f * heightDampener * (1.0f - rtlFactor * 0.08f)
            }

            val totalStrain = enterStretchY + overscrollStretchY
            state.peakStrain = Math.max(state.peakStrain, totalStrain)
            state.lastExpandTransY = expandTransY

            applyScale(targetView, totalStrain, 0.0f)
            return
        }

        // Transition from pull to release rebound!
        if (state.isPulling) {
            val peak = state.peakStrain
            state.isPulling = false
            if (peak > 0.012f) {
                val delay = (rtlFactor * 25.0f).toLong()
                state.triggerRebound(peak, delay)
                state.tick(now)
                return
            } else {
                state.stopAndReset()
                return
            }
        }

        // Actively rebounding! CRUCIAL: Must update and apply on EVERY frame of onFrameCallback
        // to prevent HyperOS from overwriting scale with 1.0f!
        if (state.isRebounding) {
            state.tick(now)
            return
        }

        // Resting state
        if (!isFlicking && !isRecentGesture && Math.abs(expandTransY) < 1.0f) {
            if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                state.stopAndReset()
            }
        }
    }
}
