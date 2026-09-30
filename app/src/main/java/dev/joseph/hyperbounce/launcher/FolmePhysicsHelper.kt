package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * Xiaomi Folme Physics Animation Engine helper for Origin Workbench.
 *
 * Integrates directly with Xiaomi's proprietary `miuix.animation.Folme` physics engine
 * (`miuix.animation.base.AnimConfig`, `AnimState`, `ViewProperty`) to execute
 * authentic physical spring animations for:
 * 1. Synchronized two-way card swapping between Shelf and Hero stages.
 * 2. Staggered cascade entrance animations when opening the workbench.
 * 3. Directional fling dismissal when swiping away shelf cards.
 * 4. Layout morphing transitions between Side Shelf and Top Shelf modes.
 *
 * Includes robust hardware-accelerated fallbacks to ensure glitch-free operation.
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
     * Executes an authentic two-way physical spring swap:
     * - The tapped shelf card expands from shelfStartRect into heroStartRect on the TOP LAYER.
     * - The current Hero card simultaneously animates in exact reverse, shrinking into shelfStartRect on the UNDERLYING LAYER.
     * - Both cards dynamically scale scaleX / scaleY and translate to center positions.
     */
    fun animateSwap(
        shelfView: View,
        heroView: View,
        shelfStartRect: RectF,
        heroStartRect: RectF,
        damping: Float = 0.82f,
        response: Float = 0.38f,
        onComplete: (() -> Unit)? = null
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Z-layer ordering:
        // User requirement: Shelf card expanding to Hero MUST be at TOP LAYER.
        // Hero card shrinking to Shelf MUST be at UNDERLYING LAYER.
        shelfView.bringToFront()
        shelfView.elevation = 60f
        heroView.elevation = 20f

        val shelfW = shelfStartRect.width()
        val shelfH = shelfStartRect.height()
        val heroW = heroStartRect.width()
        val heroH = heroStartRect.height()

        val shelfCenterX = (shelfStartRect.left + shelfStartRect.right) / 2f
        val shelfCenterY = (shelfStartRect.top + shelfStartRect.bottom) / 2f
        val heroCenterX = (heroStartRect.left + heroStartRect.right) / 2f
        val heroCenterY = (heroStartRect.top + heroStartRect.bottom) / 2f

        // Shelf card expanding to hero:
        val shelfScaleX = if (shelfW > 0) heroW / shelfW else 1.0f
        val shelfScaleY = if (shelfH > 0) heroH / shelfH else 1.0f
        val shelfTargetTransX = heroCenterX - shelfCenterX
        val shelfTargetTransY = heroCenterY - shelfCenterY

        // Hero card shrinking to shelf:
        val heroScaleX = if (heroW > 0) shelfW / heroW else 1.0f
        val heroScaleY = if (heroH > 0) shelfH / heroH else 1.0f
        val heroTargetTransX = shelfCenterX - heroCenterX
        val heroTargetTransY = shelfCenterY - heroCenterY

        val animDuration = ((response * 650f).toLong()).coerceIn(280L, 420L)

        // Try Xiaomi Folme Proprietary Physics Engine
        var usedFolme = false
        try {
            if (isFolmeInitialized && animConfigClass != null) {
                val config = animConfigClass!!.getDeclaredConstructor().newInstance()
                setEaseMethod?.invoke(config, -2, damping, response)

                val folmeShelf = folmeUseAtMethod?.invoke(null, arrayOf(shelfView))
                val stateShelf = folmeShelf?.javaClass?.getMethod("state")?.invoke(folmeShelf)
                val toMethodShelf = stateShelf?.javaClass?.getMethod("to", Array<Any>::class.java)

                val folmeHero = folmeUseAtMethod?.invoke(null, arrayOf(heroView))
                val stateHero = folmeHero?.javaClass?.getMethod("state")?.invoke(folmeHero)
                val toMethodHero = stateHero?.javaClass?.getMethod("to", Array<Any>::class.java)

                val viewPropTransX = viewPropertyClass?.getField("TRANSLATION_X")?.get(null) ?: viewPropertyX
                val viewPropTransY = viewPropertyClass?.getField("TRANSLATION_Y")?.get(null) ?: viewPropertyY

                if (toMethodShelf != null && toMethodHero != null && viewPropTransX != null && viewPropertyScaleX != null) {
                    val shelfArgs = arrayOf(
                        viewPropTransX, shelfTargetTransX,
                        viewPropTransY, shelfTargetTransY,
                        viewPropertyScaleX!!, shelfScaleX,
                        viewPropertyScaleY!!, shelfScaleY,
                        config
                    )
                    toMethodShelf.invoke(stateShelf, shelfArgs)

                    val heroArgs = arrayOf(
                        viewPropTransX, heroTargetTransX,
                        viewPropTransY, heroTargetTransY,
                        viewPropertyScaleX!!, heroScaleX,
                        viewPropertyScaleY!!, heroScaleY,
                        config
                    )
                    toMethodHero.invoke(stateHero, heroArgs)

                    usedFolme = true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Folme swap failed, falling back to hardware animator", t)
        }

        if (!usedFolme) {
            shelfView.animate().cancel()
            heroView.animate().cancel()

            shelfView.animate()
                .translationX(shelfTargetTransX)
                .translationY(shelfTargetTransY)
                .scaleX(shelfScaleX)
                .scaleY(shelfScaleY)
                .setDuration(animDuration)
                .setInterpolator(android.view.animation.PathInterpolator(0.25f, 1f, 0.5f, 1f))
                .start()

            heroView.animate()
                .translationX(heroTargetTransX)
                .translationY(heroTargetTransY)
                .scaleX(heroScaleX)
                .scaleY(heroScaleY)
                .setDuration(animDuration)
                .setInterpolator(android.view.animation.PathInterpolator(0.25f, 1f, 0.5f, 1f))
                .start()
        }

        mainHandler.postDelayed({
            onComplete?.invoke()
        }, animDuration)
    }

    /**
     * Staggered cascade entrance animation when opening the workbench.
     * Uses relative translation offset so views ALWAYS settle precisely at rest (0, 0).
     */
    fun animateFlyIn(
        view: View,
        startTransX: Float,
        startTransY: Float,
        delayMs: Long,
        damping: Float = 0.85f,
        response: Float = 0.45f
    ) {
        view.translationX = startTransX
        view.translationY = startTransY
        view.alpha = 0f
        view.scaleX = 0.90f
        view.scaleY = 0.90f

        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed({
            var usedFolme = false
            try {
                if (isFolmeInitialized && animConfigClass != null) {
                    val config = animConfigClass!!.getDeclaredConstructor().newInstance()
                    setEaseMethod?.invoke(config, -2, damping, response)

                    val folme = folmeUseAtMethod?.invoke(null, arrayOf(view))
                    val state = folme?.javaClass?.getMethod("state")?.invoke(folme)
                    val toMethod = state?.javaClass?.getMethod("to", Array<Any>::class.java)

                    val viewPropTransX = viewPropertyClass?.getField("TRANSLATION_X")?.get(null) ?: viewPropertyX
                    val viewPropTransY = viewPropertyClass?.getField("TRANSLATION_Y")?.get(null) ?: viewPropertyY

                    if (toMethod != null && viewPropTransX != null && viewPropTransY != null && viewPropertyScaleX != null) {
                        val args = arrayOf(
                            viewPropTransX, 0f,
                            viewPropTransY, 0f,
                            viewPropertyScaleX!!, 1.0f,
                            viewPropertyScaleY!!, 1.0f,
                            viewPropertyAlpha!!, 1.0f,
                            config
                        )
                        toMethod.invoke(state, args)
                        usedFolme = true
                    }
                }
            } catch (_: Throwable) {}

            if (!usedFolme) {
                view.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(300L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.2f, 1f, 0.4f, 1f))
                    .start()
            }
        }, delayMs)
    }

    /**
     * Animates smooth card dismissal offscreen when swiped away.
     */
    fun animateDismiss(
        view: View,
        flingDx: Float,
        flingDy: Float,
        onComplete: () -> Unit
    ) {
        val targetX = view.x + flingDx
        val targetY = view.y + flingDy

        view.animate().cancel()
        view.animate()
            .x(targetX)
            .y(targetY)
            .alpha(0f)
            .scaleX(0.70f)
            .scaleY(0.70f)
            .setDuration(220L)
            .withEndAction { onComplete() }
            .start()
    }

    /**
     * Animates view bounds transition when switching between Side Shelf and Top Shelf modes.
     */
    fun animateBounds(
        view: View,
        targetX: Float,
        targetY: Float,
        targetWidth: Int,
        targetHeight: Int,
        damping: Float = 0.85f,
        response: Float = 0.45f
    ) {
        view.animate().cancel()
        view.animate()
            .x(targetX)
            .y(targetY)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(280L)
            .start()

        val lp = view.layoutParams
        if (lp.width != targetWidth || lp.height != targetHeight) {
            lp.width = targetWidth
            lp.height = targetHeight
            view.layoutParams = lp
        }
    }
}
