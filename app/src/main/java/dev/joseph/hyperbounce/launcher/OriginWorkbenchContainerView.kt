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
 * - NO stock freeform floating windows.
 * - 1 Primary Hero Card: Large high-resolution card showing the latest active app snapshot.
 *   Tapping it smoothly launches that app fullscreen into the foreground.
 * - 4-Card Interactive Shelf Dock: Up to 4 parked recent apps with thumbnails, icons, and titles.
 * - Two-Way Reverse Folme Swap: Tapping any shelf card triggers Xiaomi's Folme physical spring
 *   animation to swap positions with the Hero card in real time.
 * - Dual Layout Styles:
 *     Style 0 (Side / Left Shelf): 4-card vertical dock on the left (~22% width), Hero Stage
 *     on the mid-right (~72% width, full height) maximizing usable space.
 *     Style 1 (Top Shelf): 4-card horizontal dock on top (~136dp), Hero Stage below (~92% width).
 * - Header Controls:
 *     - Layout Switcher button: Toggles between Side Dock and Top Dock with animated bound morphing.
 *     - Hero Close ('X') button: MIUI-style circular close button that terminates the Hero task
 *       and immediately promotes the next shelf app into the Hero position.
 *     - Fullscreen button (⛶): Opens the Hero app fullscreen.
 * - Swipe-to-Dismiss: Shelf cards can be swiped away to terminate tasks with smooth reflow.
 * - Tap empty space: Dismisses recents to home desktop.
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
    }

    private val mainHandler = Handler(Looper.getMainLooper())
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
    private val heroAppIconBadge: ImageView
    private val heroAppTitleText: TextView

    private val shelfDockLayout: FrameLayout
    private val shelfCardViews = ArrayList<ShelfCardView>()

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

        // Read saved layout style preference
        val prefs = context.getSharedPreferences("hyperbounce_prefs", Context.MODE_PRIVATE)
        currentStyle = prefs.getInt("ios_workbench_layout_style", STYLE_SIDE_SHELF)

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
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(22f).toFloat())
                }
            }
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1b1b24"))
                setStroke(dpToPx(1.5f), Color.parseColor("#38ffffff"))
                cornerRadius = dpToPx(22f).toFloat()
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
        val heroHeaderGradient = View(context).apply {
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
        val heroInfoBar = LinearLayout(context).apply {
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

        // 3. Shelf Dock Container & Cards
        shelfDockLayout = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

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
            shelfDockLayout.addView(card)
        }

        addView(shelfDockLayout)
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
        val prefs = context.getSharedPreferences("hyperbounce_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("ios_workbench_layout_style", currentStyle).apply()

        // Recalculate and smoothly animate layout bounds
        applyLayoutPositions(animate = true)
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
        }

        val contentTop = headerTop + headerH + dpToPx(6f)
        val contentBottom = totalH - navBarH - dpToPx(12f)
        val contentHeight = contentBottom - contentTop

        if (currentStyle == STYLE_SIDE_SHELF) {
            // ==========================================
            // STYLE 0: SIDE SHELF (Shelf Left, Hero Mid-Right)
            // ==========================================
            val shelfW = (totalW * 0.22f).toInt()
            val shelfLeft = (totalW * 0.03f).toInt()
            val gap = dpToPx(8f)

            // Shelf Dock bounds
            shelfDockLayout.layoutParams = (shelfDockLayout.layoutParams as LayoutParams).apply {
                width = shelfW
                height = contentHeight
                leftMargin = shelfLeft
                topMargin = contentTop
            }

            // 4 Shelf Cards vertically stacked
            val cardH = (contentHeight - (MAX_SHELF_CARDS - 1) * gap) / MAX_SHELF_CARDS
            for (i in 0 until MAX_SHELF_CARDS) {
                val cardY = i * (cardH + gap)
                val targetRectF = RectF(shelfLeft.toFloat(), (contentTop + cardY).toFloat(), (shelfLeft + shelfW).toFloat(), (contentTop + cardY + cardH).toFloat())
                currentShelfRects[i].set(targetRectF)

                val cardView = shelfCardViews[i]
                if (animate) {
                    FolmePhysicsHelper.animateBounds(cardView, 0f, cardY.toFloat(), shelfW, cardH, damping, response)
                } else {
                    cardView.x = 0f
                    cardView.y = cardY.toFloat()
                    val lp = cardView.layoutParams ?: LayoutParams(shelfW, cardH)
                    lp.width = shelfW
                    lp.height = cardH
                    cardView.layoutParams = lp
                }
            }

            // Hero Stage bounds: takes ~72% width and full available height
            val heroLeft = shelfLeft + shelfW + (totalW * 0.03f).toInt()
            val heroRight = (totalW * 0.97f).toInt()
            val heroW = heroRight - heroLeft
            val heroH = contentHeight

            currentHeroBounds.set(heroLeft, contentTop, heroRight, contentTop + heroH)
            currentHeroRectF.set(heroLeft.toFloat(), contentTop.toFloat(), heroRight.toFloat(), (contentTop + heroH).toFloat())

            if (animate) {
                FolmePhysicsHelper.animateBounds(heroCardContainer, heroLeft.toFloat(), contentTop.toFloat(), heroW, heroH, damping, response)
            } else {
                heroCardContainer.x = heroLeft.toFloat()
                heroCardContainer.y = contentTop.toFloat()
                val lp = heroCardContainer.layoutParams ?: LayoutParams(heroW, heroH)
                lp.width = heroW
                lp.height = heroH
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
            val gap = dpToPx(8f)

            // Shelf Dock bounds
            shelfDockLayout.layoutParams = (shelfDockLayout.layoutParams as LayoutParams).apply {
                width = shelfW
                height = shelfH
                leftMargin = shelfLeft
                topMargin = contentTop
            }

            // 4 Shelf Cards horizontally arranged
            val cardW = (shelfW - (MAX_SHELF_CARDS - 1) * gap) / MAX_SHELF_CARDS
            for (i in 0 until MAX_SHELF_CARDS) {
                val cardX = i * (cardW + gap)
                val targetRectF = RectF((shelfLeft + cardX).toFloat(), contentTop.toFloat(), (shelfLeft + cardX + cardW).toFloat(), (contentTop + shelfH).toFloat())
                currentShelfRects[i].set(targetRectF)

                val cardView = shelfCardViews[i]
                if (animate) {
                    FolmePhysicsHelper.animateBounds(cardView, cardX.toFloat(), 0f, cardW, shelfH, damping, response)
                } else {
                    cardView.x = cardX.toFloat()
                    cardView.y = 0f
                    val lp = cardView.layoutParams ?: LayoutParams(cardW, shelfH)
                    lp.width = cardW
                    lp.height = shelfH
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
                val lp = heroCardContainer.layoutParams ?: LayoutParams(heroW, heroH)
                lp.width = heroW
                lp.height = heroH
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

            // Bind Hero Stage
            bindHeroStage(currentHeroTask)

            // Hero Stage entrance animation
            heroCardContainer.alpha = 0f
            heroCardContainer.scaleX = 0.94f
            heroCardContainer.scaleY = 0.94f
            heroCardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
                .start()

            // Bind and trigger staggered cascade entrance for shelf cards
            val cfg = LauncherConfigHolder.iosAnimConfig
            for (i in 0 until MAX_SHELF_CARDS) {
                val cardView = shelfCardViews[i]
                if (i < shelfTasks.size) {
                    val task = shelfTasks[i]
                    cardView.bindTask(task)
                    cardView.visibility = View.VISIBLE

                    val targetX = cardView.x
                    val targetY = cardView.y
                    val startX = if (currentStyle == STYLE_SIDE_SHELF) -cardView.width.toFloat() else targetX
                    val startY = if (currentStyle == STYLE_TOP_SHELF) -cardView.height.toFloat() else targetY
                    val delay = i * 40L

                    FolmePhysicsHelper.animateFlyIn(
                        view = cardView,
                        startX = startX,
                        startY = startY,
                        targetX = targetX,
                        targetY = targetY,
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
     * Handles tapping a shelf card: Executes two-way reverse Folme swap with the Hero card.
     */
    private fun onShelfCardTapped(slotIndex: Int, tappedTask: Any) {
        if (isAnimatingSwap || slotIndex !in 0 until shelfTasks.size) return
        isAnimatingSwap = true

        val oldHeroTask = currentHeroTask
        val cardView = shelfCardViews[slotIndex]

        val targetHeroRect = RectF(currentHeroRectF)
        val targetShelfRect = RectF(currentShelfRects[slotIndex])

        val cfg = LauncherConfigHolder.iosAnimConfig

        // 1. Two-Way Reverse Physical Spring Swap Animation
        FolmePhysicsHelper.animateSwap(
            shelfView = cardView,
            heroView = heroCardContainer,
            targetHeroBounds = targetHeroRect,
            targetShelfBounds = targetShelfRect,
            targetHeroScale = 1.0f,
            targetShelfScale = 1.0f,
            damping = cfg.workbenchFolmeDamping,
            response = cfg.workbenchFolmeResponse,
            onComplete = {
                isAnimatingSwap = false
            }
        )

        // 2. Data model swap: tapped task becomes Hero, old Hero moves into this shelf slot
        currentHeroTask = tappedTask
        if (oldHeroTask != null) {
            shelfTasks[slotIndex] = oldHeroTask
        }

        // 3. Update stage views
        mainHandler.postDelayed({
            bindHeroStage(currentHeroTask)
            if (slotIndex < shelfTasks.size) {
                cardView.bindTask(shelfTasks[slotIndex])
            }
            // Reset transforms
            applyLayoutPositions(animate = false)
        }, 320L)
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

            // Animate hero stage entrance
            heroCardContainer.alpha = 0f
            heroCardContainer.scaleX = 0.92f
            heroCardContainer.scaleY = 0.92f
            heroCardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .start()
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
        try {
            val taskKey = task.javaClass.getField("key").get(task)
            val taskId = TaskDataHelper.getTaskId(task)
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

    private fun dismissRecentsToHome() {
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
        private val iconView: ImageView
        private val titleView: TextView

        private var downX = 0f
        private var downY = 0f
        private var isDragging = false

        init {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(14f).toFloat())
                }
            }
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1e1e26"))
                setStroke(dpToPx(1f), Color.parseColor("#30ffffff"))
                cornerRadius = dpToPx(14f).toFloat()
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
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(38f)).apply {
                    gravity = Gravity.TOP
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#8c000000"), Color.TRANSPARENT)
                )
            }
            addView(scrim)

            // Header overlay (Icon + Title)
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = dpToPx(6f)
                setPadding(pad, pad, pad, 0)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setShadowLayer(dpToPx(2f).toFloat(), 0f, 1f, Color.BLACK)
            }
            header.addView(titleView)

            addView(header)
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
