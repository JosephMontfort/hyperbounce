package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * Xiaomi Folme Physics Animation Engine helper for Origin Workbench Recents.
 *
 * Integrates with Xiaomi's proprietary `miuix.animation.Folme` physics engine
 * (`miuix.animation.base.AnimConfig`, `AnimState`, `ViewProperty`) to execute
 * authentic physical spring animations when swapping task cards between the
 * Hero stage and the 4-card interactive shelf dock.
 *
 * Automatically falls back to high-fidelity spring interpolators if Folme is
 * temporarily unavailable or throws.
 */
object FolmePhysicsHelper {
    private const val TAG = "FolmePhysics"

    @Volatile
    private var isFolmeInitialized = false

    private var folmeUseAtMethod: Method? = null
    private var animConfigClass: Class<*>? = null
    private var setEaseMethod: Method? = null
    private var viewPropertyClass: Class<*>? = null
    private var viewPropertyX: Any? = null
    private var viewPropertyY: Any? = null
    private var viewPropertyScaleX: Any? = null
    private var viewPropertyScaleY: Any? = null
    private var viewPropertyAlpha: Any? = null

    fun init(classLoader: ClassLoader) {
        if (isFolmeInitialized) return
        try {
            val folmeClass = Class.forName("miuix.animation.Folme", false, classLoader)
            folmeUseAtMethod = folmeClass.getMethod("useAt", Array<View>::class.java)

            animConfigClass = Class.forName("miuix.animation.base.AnimConfig", false, classLoader)
            setEaseMethod = animConfigClass?.getMethod(
                "setEase",
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            viewPropertyClass = Class.forName("miuix.animation.property.ViewProperty", false, classLoader)
            viewPropertyX = viewPropertyClass?.getField("X")?.get(null)
            viewPropertyY = viewPropertyClass?.getField("Y")?.get(null)
            viewPropertyScaleX = viewPropertyClass?.getField("SCALE_X")?.get(null)
            viewPropertyScaleY = viewPropertyClass?.getField("SCALE_Y")?.get(null)
            viewPropertyAlpha = viewPropertyClass?.getField("ALPHA")?.get(null)

            isFolmeInitialized = true
            Log.i(TAG, "FolmePhysicsHelper initialized successfully with Xiaomi Folme engine")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize Xiaomi Folme classes, will use spring fallback", t)
        }
    }

    /**
     * Executes a physical spring card swap between a tapped shelf card and the Hero card.
     *
     * @param shelfView The shelf task view to animate into the Hero stage
     * @param heroView The current hero task view to animate down into the vacated shelf slot
     * @param targetHeroBounds Target RectF for the shelfView (the Hero stage viewport)
     * @param targetShelfBounds Target RectF for the heroView (the shelf slot viewport)
     * @param targetHeroScale Target scale factor for shelfView in Hero stage
     * @param targetShelfScale Target scale factor for heroView in shelf dock
     * @param damping Folme physical spring damping ratio (e.g. 0.85)
     * @param response Folme physical spring response time (e.g. 0.45s)
     * @param onComplete Callback invoked when swap settles
     */
    fun animateSwap(
        shelfView: View,
        heroView: View,
        targetHeroBounds: RectF,
        targetShelfBounds: RectF,
        targetHeroScale: Float,
        targetShelfScale: Float,
        damping: Float,
        response: Float,
        onComplete: (() -> Unit)? = null
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Try Xiaomi Folme Proprietary Physics Engine
        try {
            if (isFolmeInitialized && animConfigClass != null) {
                val config = animConfigClass!!.getDeclaredConstructor().newInstance()
                // EaseManager type -2 = Physical Spring Style
                setEaseMethod?.invoke(config, -2, damping, response)

                // Animate shelf card -> Hero stage
                val folmeShelf = folmeUseAtMethod?.invoke(null, arrayOf(shelfView))
                val stateShelf = folmeShelf?.javaClass?.getMethod("state")?.invoke(folmeShelf)
                val toMethodShelf = stateShelf?.javaClass?.getMethod("to", Array<Any>::class.java)

                // Animate hero card -> Shelf slot
                val folmeHero = folmeUseAtMethod?.invoke(null, arrayOf(heroView))
                val stateHero = folmeHero?.javaClass?.getMethod("state")?.invoke(folmeHero)
                val toMethodHero = stateHero?.javaClass?.getMethod("to", Array<Any>::class.java)

                if (toMethodShelf != null && toMethodHero != null && viewPropertyX != null) {
                    val shelfArgs = arrayOf(
                        viewPropertyX!!, targetHeroBounds.left,
                        viewPropertyY!!, targetHeroBounds.top,
                        viewPropertyScaleX!!, targetHeroScale,
                        viewPropertyScaleY!!, targetHeroScale,
                        config
                    )
                    toMethodShelf.invoke(stateShelf, shelfArgs)

                    val heroArgs = arrayOf(
                        viewPropertyX!!, targetShelfBounds.left,
                        viewPropertyY!!, targetShelfBounds.top,
                        viewPropertyScaleX!!, targetShelfScale,
                        viewPropertyScaleY!!, targetShelfScale,
                        config
                    )
                    toMethodHero.invoke(stateHero, heroArgs)

                    val delayMs = ((response * 700f).toLong()).coerceIn(240L, 500L)
                    mainHandler.postDelayed({ onComplete?.invoke() }, delayMs)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Folme swap failed, falling back to spring ViewPropertyAnimator", t)
        }

        // 2. High-fidelity Fallback: ViewPropertyAnimator with natural spring timing
        shelfView.animate().cancel()
        heroView.animate().cancel()

        val animDuration = ((response * 650f).toLong()).coerceIn(220L, 450L)

        shelfView.animate()
            .x(targetHeroBounds.left)
            .y(targetHeroBounds.top)
            .scaleX(targetHeroScale)
            .scaleY(targetHeroScale)
            .setDuration(animDuration)
            .withEndAction { onComplete?.invoke() }
            .start()

        heroView.animate()
            .x(targetShelfBounds.left)
            .y(targetShelfBounds.top)
            .scaleX(targetShelfScale)
            .scaleY(targetShelfScale)
            .setDuration(animDuration)
            .start()
    }
}
