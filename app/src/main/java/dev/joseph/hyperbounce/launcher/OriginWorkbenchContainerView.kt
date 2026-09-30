package dev.joseph.hyperbounce.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Origin Workbench Recents Container (Vivo OriginOS 7 "原子工作台" Architecture)
 *
 * A 100% custom recents screen layout replacing the stock Xiaomi recents view:
 * - Pure recents UI rendered directly inside RecentsContainer over the wallpaper blur.
 * - Live real-time Xiaomi Freeform window hosted for the Primary Hero task.
 *   Freeform window decorations (3 dots, bottom nav bar pill, resize handles) are stripped off
 *   and moving/resizing is disabled via SystemUI hooks so it sits firmly inside the Hero Stage.
 * - 4-Card Interactive Shelf Dock: Up to 4 parked recent apps with thumbnails, icons, and titles.
 * - Two-Way Reverse Physical Swap: Tapping any shelf card triggers physical spring animation
 *   to swap positions with the Hero card in real time, with smooth corner radius morphing and
 *   clean Z-order restoration.
 * - Dual Layout Styles:
 *     Style 0 (Side / Left Shelf): 4-card vertical dock on the left (~26% width), Hero Stage
 *     on the mid-right calibrated with natural screen aspect ratio and vertical centering.
 *     Style 1 (Top Shelf): 4-card horizontal dock on top (~136dp), Hero Stage below (~92% width).
 * - Header Controls:
 *     - Layout Switcher button: Toggles between Side Dock and Top Dock with animated bound morphing.
 *     - Hero Close ('X') button: MIUI-style circular close button that terminates the Hero task
 *       and immediately promotes the next shelf app into the Hero position.
 *     - Fullscreen button (⛶): Opens the Hero app fullscreen.
 * - Swipe-to-Dismiss: Shelf cards can be swiped away to terminate tasks with smooth reflow.
 * - Tap empty space: Dismisses recents to home desktop and exits freeform mode.
 */
@SuppressLint("ViewConstructor")
class OriginWorkbenchContainerView(
    context: Context,
    private val classLoader: ClassLoader,
    private val recentsContainer: ViewGroup
) : FrameLayout(context) {

    companion object {
        private const val TAG = "OriginWorkbenchView"
        const val WORKBENCH_VIEW_TAG = "hyperbounce_origin_workbench_view"

        const val STYLE_SIDE_SHELF = 0
        const val STYLE_TOP_SHELF = 1
        private const val MAX_SHELF_CARDS = 4

        /**
         * Calculates the exact spatial bounds of the Hero Stage for seamless visual continuity
         * during full-screen gesture enter transitions (TaskStackView.getTaskViewWithoutHeaderRectF).
         */
        fun calculateHeroBounds(context: Context, style: Int): RectF {
            val dm = context.resources.displayMetrics
            val totalW = dm.widthPixels
            val totalH = dm.heightPixels

            val statusBarResId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarH = if (statusBarResId > 0) context.resources.getDimensionPixelSize(statusBarResId) else (32 * dm.density).toInt()
            val navBarH = (24 * dm.density).toInt()
            val headerH = (48 * dm.density).toInt()
            val headerTop = statusBarH + (4 * dm.density).toInt()

            val contentTop = headerTop + headerH + (6 * dm.density).toInt()
            val contentBottom = totalH - navBarH - (12 * dm.density).toInt()
            val contentHeight = contentBottom - contentTop

            return if (style == STYLE_SIDE_SHELF) {
                val shelfW = (totalW * 0.26f).toInt()
                val shelfLeft = (totalW * 0.035f).toInt()
                val heroLeft = shelfLeft + shelfW + (totalW * 0.035f).toInt()
                val heroRight = (totalW * 0.965f).toInt()
                val heroW = heroRight - heroLeft
                val aspectRatio = totalH.toFloat() / totalW.toFloat()
                val idealH = (heroW * aspectRatio).toInt()
                val heroH = idealH.coerceAtMost(contentHeight)
                val heroTop = contentTop + (contentHeight - heroH) / 2
                RectF(heroLeft.toFloat(), heroTop.toFloat(), heroRight.toFloat(), (heroTop + heroH).toFloat())
            } else {
                val shelfH = (136 * dm.density).toInt().coerceAtMost((contentHeight * 0.22f).toInt())
                val shelfLeft = (totalW * 0.04f).toInt()
                val shelfRight = (totalW * 0.96f).toInt()
                val heroTop = contentTop + shelfH + (10 * dm.density).toInt()
                val heroH = contentBottom - heroTop
                RectF(shelfLeft.toFloat(), heroTop.toFloat(), shelfRight.toFloat(), (heroTop + heroH).toFloat())
            }
        }
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var currentStyle: Int = STYLE_SIDE_SHELF
        private set

    // Task data
    private var currentHeroTask: Any? = null
    private val shelfTasks = ArrayList<Any>()
    private val overflowTasks = ArrayList<Any>()

    // View bounds
    val currentHeroBounds = Rect()
    private val currentHeroRectF = RectF()
    private val currentShelfRects = Array(MAX_SHELF_CARDS) { RectF() }

    // UI Components
    private val headerLayout: LinearLayout
    private val modeSwitcherBtn: TextView
    private val heroFullscreenBtn: FrameLayout
    private val heroCloseBtn: FrameLayout

    private val heroCardContainer: FrameLayout
    private val heroSnapshotView: ImageView
    private val heroHeaderGradient: View
    private val heroInfoBar: LinearLayout
    private val heroAppIconBadge: ImageView
    private val heroAppTitleText: TextView

    private val shelfCardViews = ArrayList<ShelfCardView>()

    private var heroCornerRadiusDp: Float = 22f
    private var isAnimatingSwap = false

    init {
        tag = WORKBENCH_VIEW_TAG
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isClickable = true
        isFocusable = true

        // Tap empty background to dismiss recents to home
        setOnClickListener {
            Log.d(TAG, "Workbench empty background tapped -> dismiss to home")
            dismissRecentsToHome()
        }

        // Read saved layout style preference from module config holder
        currentStyle = LauncherConfigHolder.iosAnimConfig.workbenchLayoutStyle

        // 1. Header Layout (Controls Bar)
        headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(16f)
            val padV = dpToPx(8f)
            setPadding(padH, padV, padH, padV)
        }

        // Vivo OriginOS Workbench Branding Pill
        val brandingPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createPillDrawable(Color.parseColor("#35222638"), Color.parseColor("#44ffffff"), dpToPx(1f), dpToPx(14f))
            setPadding(dpToPx(10f), dpToPx(5f), dpToPx(12f), dpToPx(5f))
        }

        val dotView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#007aff")) // Vivo cyan/blue accent
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(7f), dpToPx(7f)).apply {
                marginEnd = dpToPx(7f)
            }
        }
        brandingPill.addView(dotView)

        val brandText = TextView(context).apply {
            text = "Origin Workbench"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            paint.isFakeBoldText = true
            letterSpacing = 0.03f
        }
        brandingPill.addView(brandText)
        headerLayout.addView(brandingPill)

        // Flexible space
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        headerLayout.addView(spacer)

        // Mode Switcher Button
        modeSwitcherBtn = TextView(context).apply {
            text = if (currentStyle == STYLE_SIDE_SHELF) "◫ Side Dock" else "▥ Top Dock"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = createPillDrawable(Color.parseColor("#38334460"), Color.parseColor("#50ffffff"), dpToPx(1f), dpToPx(14f))
            setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
            setOnClickListener {
                toggleLayoutStyle()
            }
        }
        headerLayout.addView(modeSwitcherBtn)

        val spacer2 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8f), 1)
        }
        headerLayout.addView(spacer2)

        // Hero Maximize / Fullscreen Button
        heroFullscreenBtn = createCircleButton("⛶", Color.parseColor("#35ffffff")) {
            currentHeroTask?.let { task ->
                Log.d(TAG, "Hero Maximize clicked -> Launch task fullscreen")
                launchTaskFullscreen(task)
            }
        }
        headerLayout.addView(heroFullscreenBtn)

        val spacer3 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8f), 1)
        }
        headerLayout.addView(spacer3)

        // Hero Close 'X' Button (MIUI style)
        heroCloseBtn = createCircleButton("✕", Color.parseColor("#d93b3b")) {
            closeHeroTaskAndPromoteNext()
        }
        headerLayout.addView(heroCloseBtn)

        addView(headerLayout)

        // 2. Hero Stage Container
        heroCardContainer = FrameLayout(context).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(heroCornerRadiusDp).toFloat())
                }
            }
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1b1b24"))
                setStroke(dpToPx(1.5f), Color.parseColor("#38ffffff"))
                cornerRadius = dpToPx(heroCornerRadiusDp).toFloat()
            }
            elevation = dpToPx(8f).toFloat()

            // Tapping Hero card launches that app into fullscreen
            setOnClickListener {
                currentHeroTask?.let { task ->
                    Log.d(TAG, "Hero card clicked -> Launch task fullscreen: ${TaskDataHelper.getTaskTitle(context, task)}")
                    launchTaskFullscreen(task)
                }
            }
        }

        heroSnapshotView = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        heroCardContainer.addView(heroSnapshotView)

        // Gradient overlay for Hero Header
        heroHeaderGradient = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(56f)).apply {
                gravity = Gravity.TOP
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#90000000"), Color.TRANSPARENT)
            )
        }
        heroCardContainer.addView(heroHeaderGradient)

        // Hero App Info Badge
        heroInfoBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dpToPx(14f), dpToPx(12f), 0, 0)
            }
        }

        heroAppIconBadge = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(22f), dpToPx(22f)).apply {
                marginEnd = dpToPx(8f)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(6f).toFloat())
                }
            }
            clipToOutline = true
        }
        heroInfoBar.addView(heroAppIconBadge)

        heroAppTitleText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            setShadowLayer(dpToPx(3f).toFloat(), 0f, dpToPx(1f).toFloat(), Color.parseColor("#99000000"))
        }
        heroInfoBar.addView(heroAppTitleText)
        heroCardContainer.addView(heroInfoBar)

        addView(heroCardContainer)

        // 3. 4 Shelf Cards (Direct children of OriginWorkbenchContainerView)
        for (i in 0 until MAX_SHELF_CARDS) {
            val card = ShelfCardView(context, i).apply {
                onCardClickListener = { slotIdx, task ->
                    onShelfCardTapped(slotIdx, task)
                }
                onCardDismissListener = { slotIdx, task ->
                    onShelfCardDismissed(slotIdx, task)
                }
            }
            shelfCardViews.add(card)
            addView(card)
        }
    }

    fun setHeroCornerRadius(dp: Float) {
        heroCornerRadiusDp = dp
        heroCardContainer.invalidateOutline()
        (heroCardContainer.background as? GradientDrawable)?.cornerRadius = dpToPx(dp).toFloat()
    }

    fun setHeroHeaderAlpha(alpha: Float) {
        heroInfoBar.alpha = alpha
        heroHeaderGradient.alpha = alpha
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            applyLayoutPositions(animate = false)
        }
    }

    /**
     * Toggles between Side Shelf (Style 0) and Top Shelf (Style 1).
     */
    private fun toggleLayoutStyle() {
        currentStyle = if (currentStyle == STYLE_SIDE_SHELF) STYLE_TOP_SHELF else STYLE_SIDE_SHELF
        modeSwitcherBtn.text = if (currentStyle == STYLE_SIDE_SHELF) "◫ Side Dock" else "▥ Top Dock"

        // Persist setting
        try {
            val prefs = context.getSharedPreferences("hyperbounce_settings", Context.MODE_PRIVATE)
            prefs.edit().putInt("ios_workbench_layout_style", currentStyle).apply()
        } catch (_: Throwable) {}
        try {
            val legacyPrefs = context.getSharedPreferences("hyperbounce_prefs", Context.MODE_PRIVATE)
            legacyPrefs.edit().putInt("ios_workbench_layout_style", currentStyle).apply()
        } catch (_: Throwable) {}

        // Recalculate and smoothly animate layout bounds
        applyLayoutPositions(animate = true)

        // Relocate live freeform window to the new bounds
        launchHeroAsFreeform(currentHeroTask)
    }

    /**
     * Calculates and applies spatial bounds for Hero Stage and Shelf Cards based on `currentStyle`.
     */
    private fun applyLayoutPositions(animate: Boolean) {
        val totalW = width
        val totalH = height
        if (totalW <= 0 || totalH <= 0) return

        val cfg = LauncherConfigHolder.iosAnimConfig
        val damping = cfg.workbenchFolmeDamping
        val response = cfg.workbenchFolmeResponse

        val statusBarH = getStatusBarHeight()
        val navBarH = dpToPx(24f)

        val headerH = dpToPx(48f)
        val headerTop = statusBarH + dpToPx(4f)
        headerLayout.layoutParams = (headerLayout.layoutParams as LayoutParams).apply {
            width = LayoutParams.MATCH_PARENT
            height = headerH
            topMargin = headerTop
            leftMargin = 0
        }

        val contentTop = headerTop + headerH + dpToPx(6f)
        val contentBottom = totalH - navBarH - dpToPx(12f)
        val contentHeight = contentBottom - contentTop
        val gap = dpToPx(8f)

        if (currentStyle == STYLE_SIDE_SHELF) {
            // ==========================================
            // STYLE 0: SIDE SHELF (Shelf Left, Hero Mid-Right)
            // ==========================================
            val shelfW = (totalW * 0.26f).toInt()
            val shelfLeft = (totalW * 0.035f).toInt()
            val cardH = (contentHeight - (MAX_SHELF_CARDS - 1) * gap) / MAX_SHELF_CARDS

            for (i in 0 until MAX_SHELF_CARDS) {
                val cardY = contentTop + i * (cardH + gap)
                currentShelfRects[i].set(shelfLeft.toFloat(), cardY.toFloat(), (shelfLeft + shelfW).toFloat(), (cardY + cardH).toFloat())

                val cardView = shelfCardViews[i]
                if (animate) {
                    FolmePhysicsHelper.animateBounds(cardView, shelfLeft.toFloat(), cardY.toFloat(), shelfW, cardH, damping, response)
                } else {
                    cardView.x = shelfLeft.toFloat()
                    cardView.y = cardY.toFloat()
                    val lp = cardView.layoutParams as? LayoutParams ?: LayoutParams(shelfW, cardH)
                    lp.width = shelfW
                    lp.height = cardH
                    lp.leftMargin = shelfLeft
                    lp.topMargin = cardY
                    cardView.layoutParams = lp
                }
            }

            // Hero Stage bounds: calibrated with natural device aspect ratio and centered vertically
            val heroLeft = shelfLeft + shelfW + (totalW * 0.035f).toInt()
            val heroRight = (totalW * 0.965f).toInt()
            val heroW = heroRight - heroLeft
            val aspectRatio = totalH.toFloat() / totalW.toFloat()
            val idealH = (heroW * aspectRatio).toInt()
            val heroH = idealH.coerceAtMost(contentHeight)
            val heroTop = contentTop + (contentHeight - heroH) / 2

            currentHeroBounds.set(heroLeft, heroTop, heroRight, heroTop + heroH)
            currentHeroRectF.set(heroLeft.toFloat(), heroTop.toFloat(), heroRight.toFloat(), (heroTop + heroH).toFloat())

            if (animate) {
                FolmePhysicsHelper.animateBounds(heroCardContainer, heroLeft.toFloat(), heroTop.toFloat(), heroW, heroH, damping, response)
            } else {
                heroCardContainer.x = heroLeft.toFloat()
                heroCardContainer.y = heroTop.toFloat()
                val lp = heroCardContainer.layoutParams as? LayoutParams ?: LayoutParams(heroW, heroH)
                lp.width = heroW
                lp.height = heroH
                lp.leftMargin = heroLeft
                lp.topMargin = heroTop
                heroCardContainer.layoutParams = lp
            }

        } else {
            // ==========================================
            // STYLE 1: TOP SHELF (Shelf Top, Hero Below)
            // ==========================================
            val shelfH = dpToPx(136f).coerceAtMost((contentHeight * 0.22f).toInt())
            val shelfLeft = (totalW * 0.04f).toInt()
            val shelfRight = (totalW * 0.96f).toInt()
            val shelfW = shelfRight - shelfLeft
            val cardW = (shelfW - (MAX_SHELF_CARDS - 1) * gap) / MAX_SHELF_CARDS

            for (i in 0 until MAX_SHELF_CARDS) {
                val cardX = shelfLeft + i * (cardW + gap)
                currentShelfRects[i].set(cardX.toFloat(), contentTop.toFloat(), (cardX + cardW).toFloat(), (contentTop + shelfH).toFloat())

                val cardView = shelfCardViews[i]
                if (animate) {
                    FolmePhysicsHelper.animateBounds(cardView, cardX.toFloat(), contentTop.toFloat(), cardW, shelfH, damping, response)
                } else {
                    cardView.x = cardX.toFloat()
                    cardView.y = contentTop.toFloat()
                    val lp = cardView.layoutParams as? LayoutParams ?: LayoutParams(cardW, shelfH)
                    lp.width = cardW
                    lp.height = shelfH
                    lp.leftMargin = cardX
                    lp.topMargin = contentTop
                    cardView.layoutParams = lp
                }
            }

            // Hero Stage bounds: below shelf, takes ~92% width and remaining height
            val heroTop = contentTop + shelfH + dpToPx(10f)
            val heroH = contentBottom - heroTop
            val heroW = shelfW

            currentHeroBounds.set(shelfLeft, heroTop, shelfRight, heroTop + heroH)
            currentHeroRectF.set(shelfLeft.toFloat(), heroTop.toFloat(), shelfRight.toFloat(), (heroTop + heroH).toFloat())

            if (animate) {
                FolmePhysicsHelper.animateBounds(heroCardContainer, shelfLeft.toFloat(), heroTop.toFloat(), heroW, heroH, damping, response)
            } else {
                heroCardContainer.x = shelfLeft.toFloat()
                heroCardContainer.y = heroTop.toFloat()
                val lp = heroCardContainer.layoutParams as? LayoutParams ?: LayoutParams(heroW, heroH)
                lp.width = heroW
                lp.height = heroH
                lp.leftMargin = shelfLeft
                lp.topMargin = heroTop
                heroCardContainer.layoutParams = lp
            }
        }
    }

    /**
     * Called when the workbench recents screen is opened.
     */
    fun onOpenWorkbench(tasks: List<Any>) {
        if (tasks.isEmpty()) {
            dismissRecentsToHome()
            return
        }

        shelfTasks.clear()
        overflowTasks.clear()

        currentHeroTask = tasks[0]

        // Populate shelf tasks (up to 4)
        for (i in 1 until tasks.size) {
            if (shelfTasks.size < MAX_SHELF_CARDS) {
                shelfTasks.add(tasks[i])
            } else {
                overflowTasks.add(tasks[i])
            }
        }

        // Layout views
        post {
            applyLayoutPositions(animate = false)

            // Bind Hero Stage snapshot & info
            bindHeroStage(currentHeroTask)

            // Launch Hero as real-time live Xiaomi Freeform window
            launchHeroAsFreeform(currentHeroTask)

            // Hero Stage entrance animation
            heroCardContainer.alpha = 0.4f
            heroCardContainer.scaleX = 0.96f
            heroCardContainer.scaleY = 0.96f
            heroCardContainer.translationX = if (currentStyle == STYLE_SIDE_SHELF) dpToPx(30f).toFloat() else 0f
            heroCardContainer.translationY = 0f
            heroCardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 1f, 0.4f, 1f))
                .start()

            // Bind and trigger staggered cascade entrance for shelf cards
            val cfg = LauncherConfigHolder.iosAnimConfig
            for (i in 0 until MAX_SHELF_CARDS) {
                val cardView = shelfCardViews[i]
                if (i < shelfTasks.size) {
                    val task = shelfTasks[i]
                    cardView.bindTask(task)
                    cardView.visibility = View.VISIBLE

                    val startTransX = if (currentStyle == STYLE_SIDE_SHELF) -dpToPx(120f).toFloat() else 0f
                    val startTransY = if (currentStyle == STYLE_TOP_SHELF) -dpToPx(140f).toFloat() else 0f
                    val delay = i * 40L

                    FolmePhysicsHelper.animateFlyIn(
                        view = cardView,
                        startTransX = startTransX,
                        startTransY = startTransY,
                        delayMs = delay,
                        damping = cfg.workbenchFolmeDamping,
                        response = cfg.workbenchFolmeResponse
                    )
                } else {
                    cardView.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Binds the visual snapshot and title/icon badge of the Hero Stage.
     */
    private fun bindHeroStage(task: Any?) {
        if (task == null) {
            heroSnapshotView.setImageDrawable(null)
            heroAppIconBadge.setImageDrawable(null)
            heroAppTitleText.text = ""
            return
        }

        val thumb = TaskDataHelper.getTaskThumbnail(context, classLoader, task)
        heroSnapshotView.setImageBitmap(thumb)

        val icon = TaskDataHelper.getTaskIcon(context, classLoader, task)
        heroAppIconBadge.setImageDrawable(icon)

        val title = TaskDataHelper.getTaskTitle(context, task)
        heroAppTitleText.text = title
    }

    /**
     * Launches or transitions the current Hero task to a live, real-time Xiaomi Freeform window
     * positioned precisely within the Hero Stage bounds.
     */
    fun launchHeroAsFreeform(task: Any?) {
        if (task == null) return
        val taskId = TaskDataHelper.getTaskId(task)
        if (taskId == -1) return

        try {
            if (currentHeroBounds.isEmpty) {
                val rf = calculateHeroBounds(context, currentStyle)
                currentHeroBounds.set(rf.left.toInt(), rf.top.toInt(), rf.right.toInt(), rf.bottom.toInt())
            }

            // Notify SystemUI to strip 3 dots, strip bottom pill, and disable moving/resizing
            Settings.System.putInt(context.contentResolver, "hb_workbench_hero_task_id", taskId)
            Settings.System.putInt(context.contentResolver, "hb_workbench_active", 1)

            val heroBoundsRect = Rect(currentHeroBounds)
            Log.i(TAG, "Launching live Freeform Hero window for task $taskId at bounds $heroBoundsRect")

            val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
            val amwInstance = amwClass.getMethod("getInstance").invoke(null)
            val options = ActivityOptions.makeBasic()
            val setLaunchWindowingModeMethod = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            setLaunchWindowingModeMethod.invoke(options, 5) // WINDOWING_MODE_FREEFORM = 5
            options.setLaunchBounds(heroBoundsRect)

            runCatching {
                val setMiuiConfigFlagMethod = ActivityOptions::class.java.getMethod("setMiuiConfigFlag", Int::class.javaPrimitiveType)
                setMiuiConfigFlagMethod.invoke(options, 2)
            }
            runCatching {
                val getInjectorMethod = options.javaClass.getMethod("getActivityOptionsInjector")
                val injector = getInjectorMethod.invoke(options)
                val setNormalMethod = injector.javaClass.getMethod("setNormalFreeForm", Boolean::class.javaPrimitiveType)
                setNormalMethod.invoke(injector, true)
            }

            val taskKey = runCatching { task.javaClass.getField("key").get(task) }.getOrNull()
            if (taskKey != null) {
                val startMethodKey = amwClass.declaredMethods.firstOrNull {
                    it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 &&
                            it.parameterTypes[0].name.contains("TaskKey")
                }
                if (startMethodKey != null) {
                    startMethodKey.invoke(amwInstance, taskKey, options)
                    Log.i(TAG, "Successfully started live Hero Freeform via ActivityManagerWrapper (TaskKey)")
                    return
                }
            }

            val startMethodInt = amwClass.declaredMethods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            startMethodInt?.invoke(amwInstance, taskId, options)
            Log.i(TAG, "Successfully started live Hero Freeform via ActivityManagerWrapper (int)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed launching Hero task in Freeform", t)
        }
    }

    /**
     * Handles tapping a shelf card: Executes two-way reverse physical spring swap with the Hero card.
     * Shelf card expands on the TOP LAYER, while Hero card shrinks on the UNDERLYING LAYER.
     * Smoothly morphs corner radii and crossfades headers during flight.
     */
    private fun onShelfCardTapped(slotIndex: Int, tappedTask: Any) {
        if (isAnimatingSwap || slotIndex !in 0 until shelfTasks.size) return
        isAnimatingSwap = true

        val oldHeroTask = currentHeroTask
        val cardView = shelfCardViews[slotIndex]

        val targetHeroRect = RectF(currentHeroRectF)
        val targetShelfRect = RectF(currentShelfRects[slotIndex])

        val cfg = LauncherConfigHolder.iosAnimConfig

        // Two-Way Reverse Physical Spring Swap Animation
        FolmePhysicsHelper.animateSwap(
            shelfView = cardView,
            heroView = heroCardContainer,
            shelfStartRect = targetShelfRect,
            heroStartRect = targetHeroRect,
            damping = cfg.workbenchFolmeDamping,
            response = cfg.workbenchFolmeResponse,
            onUpdateProgress = { progress ->
                // Smoothly morph corner radii & crossfade headers during flight
                val shelfRadius = 14f + (22f - 14f) * progress
                cardView.setCardCornerRadius(shelfRadius)
                cardView.setHeaderAlpha(1f - progress)

                val heroRadius = 22f - (22f - 14f) * progress
                setHeroCornerRadius(heroRadius)
                setHeroHeaderAlpha(1f - progress)
            },
            onComplete = {
                // 1. Wipe all animation properties cleanly
                FolmePhysicsHelper.cleanView(cardView)
                FolmePhysicsHelper.cleanView(heroCardContainer)

                // 2. Data model swap: tapped task becomes Hero, old Hero moves into this shelf slot
                currentHeroTask = tappedTask
                if (oldHeroTask != null) {
                    shelfTasks[slotIndex] = oldHeroTask
                }

                // 3. Update stage views
                bindHeroStage(currentHeroTask)
                if (slotIndex < shelfTasks.size) {
                    cardView.bindTask(shelfTasks[slotIndex])
                }

                // 4. Reset corner radii & header alphas
                cardView.setCardCornerRadius(14f)
                cardView.setHeaderAlpha(1f)
                setHeroCornerRadius(22f)
                setHeroHeaderAlpha(1f)

                // 5. Reset transforms & elevations
                cardView.translationX = 0f
                cardView.translationY = 0f
                cardView.scaleX = 1f
                cardView.scaleY = 1f
                cardView.alpha = 1f
                cardView.elevation = dpToPx(5f).toFloat()

                heroCardContainer.translationX = 0f
                heroCardContainer.translationY = 0f
                heroCardContainer.scaleX = 1f
                heroCardContainer.scaleY = 1f
                heroCardContainer.alpha = 1f
                heroCardContainer.elevation = dpToPx(8f).toFloat()

                // 6. RESTORE HIERARCHY Z-ORDER
                // heroCardContainer MUST be brought to front above shelf cards so it receives touches at the hero position!
                heroCardContainer.bringToFront()
                headerLayout.bringToFront()

                // 7. Launch / switch live freeform window to the new hero task!
                launchHeroAsFreeform(currentHeroTask)

                isAnimatingSwap = false
            }
        )
    }

    /**
     * Closes current Hero app via MIUI 'X' button and promotes next shelf app.
     */
    private fun closeHeroTaskAndPromoteNext() {
        val taskToKill = currentHeroTask ?: return
        Log.d(TAG, "Closing Hero task via 'X' button: ${TaskDataHelper.getTaskTitle(context, taskToKill)}")

        // Kill Hero task
        TaskDataHelper.killTask(context, classLoader, taskToKill)

        if (shelfTasks.isNotEmpty()) {
            // Promote slot 0 to Hero
            val promotedTask = shelfTasks.removeAt(0)
            if (overflowTasks.isNotEmpty()) {
                shelfTasks.add(overflowTasks.removeAt(0))
            }

            currentHeroTask = promotedTask
            bindHeroStage(currentHeroTask)

            // Rebind shelf cards
            for (i in 0 until MAX_SHELF_CARDS) {
                val cardView = shelfCardViews[i]
                if (i < shelfTasks.size) {
                    cardView.bindTask(shelfTasks[i])
                    cardView.visibility = View.VISIBLE
                } else {
                    cardView.visibility = View.GONE
                }
            }

            // Animate hero stage entrance & launch in freeform
            heroCardContainer.alpha = 0f
            heroCardContainer.scaleX = 0.92f
            heroCardContainer.scaleY = 0.92f
            heroCardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .start()

            launchHeroAsFreeform(currentHeroTask)
        } else {
            // Shelf is empty, dismiss to home
            currentHeroTask = null
            dismissRecentsToHome()
        }
    }

    /**
     * Handles swiping a shelf card away to dismiss/terminate its task.
     */
    private fun onShelfCardDismissed(slotIndex: Int, dismissedTask: Any) {
        if (slotIndex !in 0 until shelfTasks.size) return
        Log.d(TAG, "Shelf card dismissed at slot $slotIndex: ${TaskDataHelper.getTaskTitle(context, dismissedTask)}")

        // 1. Kill task
        TaskDataHelper.killTask(context, classLoader, dismissedTask)

        // 2. Remove from shelf
        shelfTasks.removeAt(slotIndex)

        // 3. Fill from overflow if available
        if (overflowTasks.isNotEmpty()) {
            shelfTasks.add(overflowTasks.removeAt(0))
        }

        // 4. Smoothly shift remaining cards into place
        for (i in 0 until MAX_SHELF_CARDS) {
            val cardView = shelfCardViews[i]
            if (i < shelfTasks.size) {
                cardView.bindTask(shelfTasks[i])
                cardView.visibility = View.VISIBLE
            } else {
                cardView.visibility = View.GONE
            }
        }
        applyLayoutPositions(animate = true)
    }

    /**
     * Launches the task in standard fullscreen mode (opening the app from recents).
     */
    private fun launchTaskFullscreen(task: Any) {
        val taskId = TaskDataHelper.getTaskId(task)
        try {
            Settings.System.putInt(context.contentResolver, "hb_workbench_active", 0)

            // Try launchFullScreenFromFreeform via SmallWindowStateHelper
            val helperClass = Class.forName("com.miui.home.smallwindow.SmallWindowStateHelper", false, classLoader)
            val getInstance = helperClass.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val launchFullMethod = helperClass.declaredMethods.firstOrNull {
                it.name == "launchFullScreenFromFreeform"
            }
            if (launchFullMethod != null && taskId != -1) {
                launchFullMethod.invoke(instance, context, taskId, null, 0)
                Log.i(TAG, "Launched task $taskId fullscreen from freeform via SmallWindowStateHelper")
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed launchFullScreenFromFreeform", t)
        }

        // Direct fallback: startActivityFromRecents fullscreen
        try {
            val taskKey = task.javaClass.getField("key").get(task)
            val options = ActivityOptions.makeBasic()

            val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
            val amwInstance = amwClass.getMethod("getInstance").invoke(null)

            val startMethodTaskKey = amwClass.declaredMethods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 &&
                        !it.parameterTypes[0].isPrimitive
            }
            val startMethodInt = amwClass.declaredMethods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
            }

            if (startMethodTaskKey != null) {
                startMethodTaskKey.invoke(amwInstance, taskKey, options)
                Log.i(TAG, "Launched task (TaskKey) fullscreen")
            } else if (startMethodInt != null && taskId != -1) {
                startMethodInt.invoke(amwInstance, taskId, options)
                Log.i(TAG, "Launched task (id=$taskId) fullscreen")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed launching fullscreen task", t)
        }
    }

    /**
     * Called when the workbench is closed or dismissed.
     */
    fun onCloseWorkbench() {
        try {
            Settings.System.putInt(context.contentResolver, "hb_workbench_active", 0)
            val taskId = currentHeroTask?.let { TaskDataHelper.getTaskId(it) } ?: -1
            if (taskId != -1) {
                // Minimize or move freeform task to back so it does not float over desktop
                val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
                val amw = amwClass.getMethod("getInstance").invoke(null)
                val moveMethod = amwClass.declaredMethods.firstOrNull { it.name == "moveTaskToBack" }
                moveMethod?.invoke(amw, taskId)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error in onCloseWorkbench", t)
        }
    }

    private fun dismissRecentsToHome() {
        onCloseWorkbench()
        runCatching {
            val method = recentsContainer.javaClass.getMethod("dismissRecentsToHome")
            method.invoke(recentsContainer)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else dpToPx(32f)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    private fun createPillDrawable(bgColor: Int, strokeColor: Int, strokeWidthPx: Int, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(strokeWidthPx, strokeColor)
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    private fun createCircleButton(symbol: String, bgColor: Int, onClick: () -> Unit): FrameLayout {
        val size = dpToPx(30f)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(dpToPx(1f), Color.parseColor("#44ffffff"))
            }
            elevation = dpToPx(4f).toFloat()

            val text = TextView(context).apply {
                text = symbol
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            addView(text)
            setOnClickListener { onClick() }
        }
    }

    // =========================================================================
    // INNER CLASS: ShelfCardView
    // Represents an interactive card on the 4-card shelf dock.
    // =========================================================================
    inner class ShelfCardView(context: Context, val slotIndex: Int) : FrameLayout(context) {
        var currentBoundTask: Any? = null
            private set

        var onCardClickListener: ((Int, Any) -> Unit)? = null
        var onCardDismissListener: ((Int, Any) -> Unit)? = null

        private val thumbnailView: ImageView
        private val header: LinearLayout
        private val iconView: ImageView
        private val titleView: TextView
        private var cardCornerRadiusDp: Float = 14f

        private var downX = 0f
        private var downY = 0f
        private var isDragging = false

        init {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(cardCornerRadiusDp).toFloat())
                }
            }
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1e1e26"))
                setStroke(dpToPx(1f), Color.parseColor("#30ffffff"))
                cornerRadius = dpToPx(cardCornerRadiusDp).toFloat()
            }
            elevation = dpToPx(5f).toFloat()

            // Thumbnail
            thumbnailView = ImageView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            addView(thumbnailView)

            // Top gradient overlay
            val scrim = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(36f)).apply {
                    gravity = Gravity.TOP
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#8c000000"), Color.TRANSPARENT)
                )
            }
            addView(scrim)

            // Header overlay (Icon + Title)
            header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = dpToPx(6f)
                val padV = dpToPx(5f)
                setPadding(padH, padV, padH, 0)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP
                }
            }

            iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(16f), dpToPx(16f)).apply {
                    marginEnd = dpToPx(5f)
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dpToPx(4f).toFloat())
                    }
                }
                clipToOutline = true
            }
            header.addView(iconView)

            titleView = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setShadowLayer(dpToPx(2f).toFloat(), 0f, 1f, Color.BLACK)
            }
            header.addView(titleView)

            addView(header)
        }

        fun setCardCornerRadius(dp: Float) {
            cardCornerRadiusDp = dp
            invalidateOutline()
            (background as? GradientDrawable)?.cornerRadius = dpToPx(dp).toFloat()
        }

        fun setHeaderAlpha(alpha: Float) {
            header.alpha = alpha
        }

        fun bindTask(task: Any) {
            currentBoundTask = task

            val thumb = TaskDataHelper.getTaskThumbnail(context, classLoader, task)
            thumbnailView.setImageBitmap(thumb)

            val icon = TaskDataHelper.getTaskIcon(context, classLoader, task)
            iconView.setImageDrawable(icon)

            val title = TaskDataHelper.getTaskTitle(context, task)
            titleView.text = title

            translationX = 0f
            translationY = 0f
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            setCardCornerRadius(14f)
            setHeaderAlpha(1f)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val task = currentBoundTask ?: return super.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY

                    if (!isDragging) {
                        if (currentStyle == STYLE_SIDE_SHELF && Math.abs(dx) > touchSlop && dx < 0) {
                            isDragging = true
                        } else if (currentStyle == STYLE_TOP_SHELF && Math.abs(dy) > touchSlop && dy < 0) {
                            isDragging = true
                        }
                    }

                    if (isDragging) {
                        if (currentStyle == STYLE_SIDE_SHELF) {
                            // Swiping left offscreen
                            translationX = dx.coerceAtMost(0f)
                            alpha = (1f - (Math.abs(dx) / (width * 1.5f))).coerceIn(0.2f, 1f)
                        } else {
                            // Swiping up offscreen
                            translationY = dy.coerceAtMost(0f)
                            alpha = (1f - (Math.abs(dy) / (height * 1.5f))).coerceIn(0.2f, 1f)
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val threshold = dpToPx(70f)

                        if (currentStyle == STYLE_SIDE_SHELF && Math.abs(dx) > threshold) {
                            // Dismissed left
                            FolmePhysicsHelper.animateDismiss(this, -width.toFloat() * 1.5f, 0f) {
                                onCardDismissListener?.invoke(slotIndex, task)
                            }
                        } else if (currentStyle == STYLE_TOP_SHELF && Math.abs(dy) > threshold) {
                            // Dismissed up
                            FolmePhysicsHelper.animateDismiss(this, 0f, -height.toFloat() * 1.5f) {
                                onCardDismissListener?.invoke(slotIndex, task)
                            }
                        } else {
                            // Snap back
                            animate().translationX(0f).translationY(0f).alpha(1f).setDuration(220L).start()
                        }
                        isDragging = false
                        return true
                    } else {
                        // Regular Tap Click
                        val dist = Math.hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                        if (dist < touchSlop) {
                            onCardClickListener?.invoke(slotIndex, task)
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        animate().translationX(0f).translationY(0f).alpha(1f).setDuration(220L).start()
                        isDragging = false
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
