package dev.joseph.hyperbounce.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * 1:1 Port of Transsion XOS 16.3 SpringAnimationBuilder (com.android.launcher3.anim.SpringAnimationBuilder).
 * Computes exact closed-form analytical solutions of under-damped harmonic oscillators:
 * y(t) = y_end + e^(-beta*t/2) * (a*cos(gamma*t) + b*sin(gamma*t))
 */
class XosSpringAnimationBuilder(
    private val context: Context? = null
) {
    private var a: Double = 0.0
    private var b: Double = 0.0
    private var beta: Double = 0.0
    private var gamma: Double = 0.0
    private var va: Double = 0.0
    private var vb: Double = 0.0

    private var startValue: Float = 0.0f
    private var endValue: Float = 0.0f
    private var velocity: Float = 0.0f
    private var stiffness: Float = 150.0f
    private var dampingRatio: Float = 0.70f
    private var minVisibleChange: Float = 1.0f
    private var durationSeconds: Float = 0.0f

    private var valueThreshold: Double = 0.0
    private var velocityThreshold: Double = 0.0

    fun setStiffness(k: Float): XosSpringAnimationBuilder {
        stiffness = max(1.0f, k)
        return this
    }

    fun setDampingRatio(zeta: Float): XosSpringAnimationBuilder {
        dampingRatio = zeta.coerceIn(0.01f, 0.99f)
        return this
    }

    fun setStartValue(v: Float): XosSpringAnimationBuilder {
        startValue = v
        return this
    }

    fun setEndValue(v: Float): XosSpringAnimationBuilder {
        endValue = v
        return this
    }

    fun setStartVelocity(v: Float): XosSpringAnimationBuilder {
        velocity = v
        return this
    }

    fun setMinimumVisibleChange(v: Float): XosSpringAnimationBuilder {
        minVisibleChange = max(0.001f, v)
        return this
    }

    fun computeParams() {
        val singleFrameMs = 16.0
        val sqrtK = sqrt(stiffness.toDouble())
        val zeta = dampingRatio.toDouble()
        val sqrt1MinusZetaSq = sqrt(1.0 - (zeta * zeta)) * sqrtK

        beta = 2.0 * zeta * sqrtK
        gamma = sqrt1MinusZetaSq

        if (gamma.isNaN() || gamma.isInfinite() || gamma <= 0.0) {
            durationSeconds = 0.0f
            return
        }

        val delta = (startValue - endValue).toDouble()
        a = delta
        val betaTimesDelta = beta * delta
        b = (velocity.toDouble() / gamma) + (betaTimesDelta / (gamma * 2.0))
        va = (betaTimesDelta / 2.0) - (b * gamma)
        vb = ((beta * b) / 2.0) + (gamma * delta)

        valueThreshold = minVisibleChange * 0.65
        velocityThreshold = (valueThreshold * 1000.0) / singleFrameMs

        val dAtan2 = atan2(-delta, b)
        var dMax = dAtan2 / gamma
        val period = Math.PI / gamma
        val startUptime = SystemClock.uptimeMillis()

        var iterations = 0
        while (true) {
            if (dMax >= 0.0) {
                val expTerm = exp((-beta * dMax) / 2.0)
                val gammaT = gamma * dMax
                val currentV = abs(((sin(gammaT) * vb) + (cos(gammaT) * va)) * expTerm)
                if (currentV < velocityThreshold) {
                    break
                }
            }
            if (SystemClock.uptimeMillis() - startUptime > 200L || iterations++ > 20) {
                dMax = max(0.1, abs(dMax))
                break
            }
            dMax += period
            if (dMax.isNaN() || dMax.isInfinite() || dMax > 5.0) {
                break
            }
        }

        if (dMax.isNaN() || dMax.isInfinite() || dMax <= 0.0) {
            dMax = 0.6
        }

        var tLow = max(0.0, dMax - (period / 2.0))
        var tHigh = dMax
        val dtTol = singleFrameMs / 2000.0
        var bsIter = 0
        while (tHigh - tLow >= dtTol && bsIter++ < 20) {
            val tMid = (tLow + tHigh) / 2.0
            val expMid = exp((-beta * tMid) / 2.0)
            val gammaMid = gamma * tMid
            val posMid = abs(((sin(gammaMid) * b) + (cos(gammaMid) * a)) * expMid)
            val velMid = abs(((sin(gammaMid) * vb) + (cos(gammaMid) * va)) * expMid)

            if (posMid < valueThreshold && velMid < velocityThreshold) {
                tHigh = tMid
            } else {
                tLow = tMid
            }
        }

        durationSeconds = tHigh.coerceIn(0.1, 2.5).toFloat()
    }

    fun getValue(tSeconds: Float): Float {
        val t = tSeconds.toDouble()
        val expTerm = exp((-beta * t) / 2.0)
        val gammaT = gamma * t
        val disp = ((sin(gammaT) * b) + (cos(gammaT) * a)) * expTerm
        return (disp + endValue).toFloat()
    }

    fun build(view: View, startDelayMs: Long, onEnd: (() -> Unit)? = null): ValueAnimator {
        computeParams()
        val durationMs = (durationSeconds * 1000.0f).roundToLong().coerceAtLeast(50L)

        val animator = ValueAnimator.ofFloat(0.0f, durationSeconds).apply {
            duration = durationMs
            startDelay = startDelayMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val currentT = va.animatedValue as Float
                view.translationY = getValue(currentT)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(anim: Animator) {
                    view.translationY = endValue
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(anim: Animator) {
                    view.translationY = endValue
                    onEnd?.invoke()
                }
            })
        }
        return animator
    }
}
