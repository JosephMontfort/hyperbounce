package dev.hyperios.app.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Insets
import android.graphics.Rect
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.PhysicsHook
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.Collections
import java.util.WeakHashMap

/**
 * Xposed Hook & Shifter for TikTok (com.ss.android.ugc.trill and com.zhiliaoapp.musically).
 *
 * Responsibilities:
 * 1. Suppress native bottom bar (X.0eaq / @id/moi / 0x7f0b4587) and hairline divider (@id/c22 / 0x7f0b0caa).
 * 2. UI Heist: Inject HyperBounce Compose floating liquid glass navigation bar (TikTokNavBarHost).
 * 3. Video Feed Overlay Shifter: Dynamically elevate captions (@id/b9i / 0x7f0b12bc), audio disk (@id/nlf / 0x7f0b5032),
 *    and seek bar scrubber (@id/zll / 0x7f0b8c4d, @id/mon / 0x7f0b458c) above the floating bar.
 * 4. Expand seek bar touch bounds via TouchDelegate for seamless scrubbing interaction.
 */
object TikTokHook {
    private const val TAG = "TikTokHook"

    // View IDs from TikTok 43.5.4 cbw.xml and layout inspection
    private const val ID_STOCK_TAB_BAR = 0x7f0b4587 // @id/moi (X.0eaq)
    private const val ID_HAIRLINE_DIVIDER = 0x7f0b0caa // @id/c22
    private const val ID_SPACER_BOTTOM = 0x7f0b0ca5 // @id/c1x (spacer in zy_)
    private const val ID_TAB_HOST = 0x7f0b45a2 // @id/mp9 (FragmentTabHost)
    private const val ID_ACTION_RAIL_CONTAINER = 0x7f0b3708 // @id/jz8
    private const val ID_CAPTION_CONTAINER = 0x7f0b12bc // @id/b9i
    private const val ID_CAPTION_CONTAINER_ALT = 0x7f0b0865 // @id/b9i alt
    private const val ID_AUDIO_DISK = 0x7f0b5032 // @id/nlf
    private const val ID_FLOATING_NOTES = 0x7f0b8af3 // @id/znw
    private const val ID_SEEK_BAR_SCRUBBER = 0x7f0b8c4d // @id/zll (0ODk)
    private const val ID_SEEK_BAR_SCRUBBER_ALT = 0x7f0b8a9e // @id/zll alt
    private const val ID_FEED_VIEW_PAGER = 0x7f0b8c70 // @id/zy7 (VerticalViewPager)

    private const val TAG_TIKTOK_SHIFTED = 0x7F0B9910
    private const val TAG_TIKTOK_UNCLIPPED = 0x7F0B9911
    private const val TAG_FEED_WATCHER_KEY = 0x7F0B9912
    private const val TAG_SEEK_BAR_TOUCH_DELEGATE = 0x7F0B9913

    private lateinit var module: XposedModule
    private lateinit var prefs: HyperBouncePrefs
    private var classLoader: ClassLoader? = null

    val externalTabState = mutableIntStateOf(0)
    val isBarVisibleState = mutableStateOf(true)

    private var activeFloatingBar: ComposeView? = null
    private var isOverlayHidden = false

    private val sShiftedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val sUnclippedViews = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, Pair<Boolean, Boolean>>()
    )

    // --- Dispatch Entry Points ---

    @JvmStatic
    fun init(param: XposedModuleInterface.PackageReadyParam, prefs: SharedPreferences? = null) {
        val mod = PhysicsHook.instance ?: run {
            Log.e("HyperBounce", "TikTokHook.init: PhysicsHook instance not available")
            return
        }
        init(mod, param, HyperBouncePrefs(mod))
    }

    @JvmStatic
    fun init(param: XposedModuleInterface.PackageReadyParam, hyperBouncePrefs: HyperBouncePrefs) {
        val mod = PhysicsHook.instance ?: run {
            Log.e("HyperBounce", "TikTokHook.init: PhysicsHook instance not available")
            return
        }
        init(mod, param, hyperBouncePrefs)
    }

    @JvmStatic
    fun init(
        module: XposedModule,
        param: XposedModuleInterface.PackageReadyParam,
        hyperBouncePrefs: HyperBouncePrefs = HyperBouncePrefs(module)
    ) {
        this.module = module
        this.prefs = hyperBouncePrefs
        this.classLoader = param.classLoader
        onPackageReady(module, param.classLoader, hyperBouncePrefs)
    }

    @JvmStatic
    fun init(module: XposedModule, hyperBouncePrefs: HyperBouncePrefs) {
        this.module = module
        this.prefs = hyperBouncePrefs
        onPackageReady(module, null, hyperBouncePrefs)
    }

    private fun onPackageReady(
        module: XposedModule,
        cl: ClassLoader?,
        prefs: HyperBouncePrefs
    ) {
        val enabled = prefs.appEnabled("tiktok")
        Log.i("HyperBounce", "TikTokHook: onPackageReady called, enabled=$enabled")
        if (!enabled) {
            module.log(Log.INFO, TAG, "disabled for TikTok, skipping injection")
            return
        }

        Log.i("HyperBounce", "TikTokHook: Initializing TikTok Floating Liquid Navigation Bar Hook")
        module.log(Log.INFO, TAG, "Initializing TikTok Floating Liquid Navigation Bar Hook")

        val targetClassLoader = cl ?: javaClass.classLoader ?: return

        // 1. Hook dispatchApplyWindowInsets to eliminate duplicate bottom padding on main content
        hookWindowInsets(module)

        // 2. Hook stock bottom bar (X.0eaq) constructors & onMeasure
        hookStockTabBar(module, targetClassLoader)

        // 3. Hook FragmentTabHost for tab switching notifications and view attachment
        hookFragmentTabHost(module, targetClassLoader)

        // 4. Hook SplashActivity & MainActivity lifecycle for UI Heist fallback
        hookActivities(module, targetClassLoader)

        // 5. Hook comment fragments to slide floating bar down on comment sheet presentation
        hookCommentFragments(module, targetClassLoader)

        // 6. Hook Feed VerticalViewPager onLayout
        hookFeedShifter(module, targetClassLoader)
    }

    // --- Tab Bar Suppression ---

    private fun neutralizeSpacers(parent: ViewGroup?) {
        if (parent == null) return
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if (c.id == ID_HAIRLINE_DIVIDER || c.id == ID_SPACER_BOTTOM) {
                c.visibility = View.GONE
                c.layoutParams?.let { lp ->
                    if (lp.height != 0) {
                        lp.height = 0
                        c.requestLayout()
                    }
                }
            }
        }
    }

    private val COMMENT_FRAGMENT_CLASSES = listOf(
        "com.ss.android.ugc.aweme.commentv2.commentlist.ui.CommentListFragmentV2",
        "com.ss.android.ugc.aweme.commentv2.commentpage.commonvideo.BaseCommentPageContainerFragment",
        "com.ss.android.ugc.aweme.commentv2.commentpage.commonvideo.DefaultCommentPageContainerFragment",
        "com.ss.android.ugc.aweme.comment.ui.CommentListFragment",
        "com.ss.android.ugc.aweme.comment.commentlist.CommentListFragmentV2"
    )
    @Volatile
    private var isCommentSheetVisible = false

    private fun isCommentSheetOpen(activity: Activity?): Boolean {
        if (activity != null) {
            try {
                val getFmMethod = activity.javaClass.getMethod("getSupportFragmentManager")
                val fm = getFmMethod.invoke(activity)
                if (fm != null) {
                    val getFragmentsMethod = fm.javaClass.getMethod("getFragments")
                    val fragments = getFragmentsMethod.invoke(fm) as? List<*>
                    if (fragments != null) {
                        for (frag in fragments) {
                            if (frag != null) {
                                val isAdded = try {
                                    frag.javaClass.getMethod("isAdded").invoke(frag) as? Boolean ?: false
                                } catch (_: Throwable) { false }
                                val isVisible = try {
                                    frag.javaClass.getMethod("isVisible").invoke(frag) as? Boolean ?: false
                                } catch (_: Throwable) { false }
                                val isHidden = try {
                                    frag.javaClass.getMethod("isHidden").invoke(frag) as? Boolean ?: false
                                } catch (_: Throwable) { false }

                                if (isAdded && isVisible && !isHidden) {
                                    val className = frag.javaClass.name
                                    if (COMMENT_FRAGMENT_CLASSES.contains(className) ||
                                        className.contains("CommentList") ||
                                        className.contains("CommentPage") ||
                                        className.contains("Comment")
                                    ) {
                                        isCommentSheetVisible = true
                                        return true
                                    }
                                }
                            }
                        }
                        isCommentSheetVisible = false
                        return false
                    }
                }
            } catch (_: Throwable) {}
        }
        return isCommentSheetVisible
    }

    private fun suppressNativeTabBar(tabBar: ViewGroup) {
        tabBar.alpha = 0f
        tabBar.translationY = -9999f
        tabBar.layoutParams?.let { lp ->
            if (lp.height != 0) {
                lp.height = 0
                tabBar.requestLayout()
            }
        }
        val parent = tabBar.parent as? ViewGroup
        neutralizeSpacers(parent)
    }

    // --- UI Heist / ComposeView Injection ---

    private fun findActivity(view: View): Activity? {
        var ctx = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun findTabBar(root: ViewGroup): ViewGroup? {
        val byId = root.findViewById<ViewGroup>(ID_STOCK_TAB_BAR)
        if (byId != null) return byId

        fun search(v: View): ViewGroup? {
            if (v is ViewGroup) {
                if (v.id == ID_STOCK_TAB_BAR) return v
                val resName = try {
                    if (v.id != View.NO_ID && v.resources != null) {
                        v.resources.getResourceEntryName(v.id)
                    } else null
                } catch (_: Throwable) { null }
                if (resName == "moi") return v

                if (v.javaClass.name.endsWith(".0eaq")) return v

                // Check if this container contains moa (0x7f0b457f) and moc (0x7f0b4581)
                if (v.findViewById<View>(0x7f0b457f) != null && v.findViewById<View>(0x7f0b4581) != null) {
                    return v
                }

                for (i in 0 until v.childCount) {
                    val found = search(v.getChildAt(i))
                    if (found != null) return found
                }
            }
            return null
        }

        return search(root)
    }

    fun executeUIHeist(tabBarContainer: ViewGroup) {
        val act = findActivity(tabBarContainer) ?: return
        executeUIHeist(act, tabBarContainer)
    }

    private fun executeUIHeist(activity: Activity, tabBarContainer: ViewGroup) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "executeUIHeist aborted - activity is finishing or destroyed")
            return
        }

        val decorView = (activity.window?.decorView as? ViewGroup) ?: run {
            Log.w(TAG, "executeUIHeist aborted - decorView is null")
            return
        }
        if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) {
            Log.d(TAG, "HyperFloatingBar already attached to decorView")
            return
        }

        Log.i(TAG, "executeUIHeist running on activity ${activity.javaClass.name}")

        activity.runOnUiThread {
            try {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return@runOnUiThread

                val lifecycleOwner = FakeLifecycleOwner().apply {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }

                if (decorView.findViewTreeLifecycleOwner() == null) decorView.setViewTreeLifecycleOwner(lifecycleOwner)
                if (decorView.findViewTreeSavedStateRegistryOwner() == null) decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                if (decorView.findViewTreeViewModelStoreOwner() == null) decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)

                val backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView

                val composeView = ComposeView(activity).apply {
                    tag = "HyperFloatingBar"
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setContent {
                        TikTokNavBarHost(
                            tabBarContainer = tabBarContainer,
                            backdropSource = backdropSource,
                            hyperBouncePrefs = prefs,
                            externalTabState = externalTabState
                        )
                    }
                }

                activeFloatingBar = composeView
                decorView.addView(composeView)
                Log.i(TAG, "TikTok composeView attached to decorView successfully")
                module.log(Log.INFO, TAG, "Attached TikTok Liquid Navigation Bar overlay to decorView")

                composeView.setOnApplyWindowInsetsListener { v, insets ->
                    val imeBottom = try {
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                    } catch (_: Throwable) { 0 }

                    val targetVisibility = if (imeBottom > 200 || isOverlayHidden) View.GONE else View.VISIBLE
                    if (v.visibility != targetVisibility) {
                        v.visibility = targetVisibility
                    }
                    insets
                }

                setupFeedShifterWatcher(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception in executeUIHeist: ${e.message}", e)
                module.log(Log.ERROR, TAG, "Exception in executeUIHeist: ${e.message}")
            }
        }
    }

    private fun setBarVisibility(activity: Activity?, visibility: Int, isOverlay: Boolean = false) {
        if (isOverlay) {
            isOverlayHidden = (visibility == View.GONE)
        }
        isBarVisibleState.value = (visibility == View.VISIBLE)
        activity?.runOnUiThread {
            val bar = activeFloatingBar ?: activity.window?.decorView?.findViewWithTag<View>("HyperFloatingBar")
            bar?.visibility = visibility
        }
    }

    // --- Feed Layout & Margin Shifter ---

    private fun getShiftY(context: Context): Float {
        val density = context.resources.displayMetrics.density
        val barHeightDp = prefs.appBarHeight("tiktok")
        val bottomMarginDp = prefs.appBottomMargin("tiktok")
        val stockBarHeightDp = 47.0f
        // Shift upward: totalBarTop - stockBarHeight
        val shiftDeltaDp = (barHeightDp + bottomMarginDp - stockBarHeightDp).coerceAtLeast(15.0f)
        val shiftPx = (shiftDeltaDp + 35f) * density
        return -shiftPx.coerceIn(160f * (density / 3f), 220f * (density / 3f))
    }

    private fun markUnclipped(vg: ViewGroup) {
        if (vg.id == android.R.id.content) return
        synchronized(sUnclippedViews) {
            if (!sUnclippedViews.containsKey(vg)) {
                sUnclippedViews[vg] = vg.clipChildren to vg.clipToPadding
            }
        }
        if (vg.clipChildren) vg.clipChildren = false
        if (vg.clipToPadding) vg.clipToPadding = false
        vg.setTag(TAG_TIKTOK_UNCLIPPED, true)
    }

    private fun unclipAncestors(view: View, stopAt: View) {
        var p = view.parent
        while (p is ViewGroup) {
            markUnclipped(p)
            if (p === stopAt) break
            p = p.parent
        }
    }

    private fun shiftTargetView(view: View, shiftY: Float, root: ViewGroup) {
        if (view.translationY != shiftY) {
            view.translationY = shiftY
        }
        if (view.getTag(TAG_TIKTOK_SHIFTED) == null) {
            view.setTag(TAG_TIKTOK_SHIFTED, shiftY)
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.translationY != shiftY) {
                    v.translationY = shiftY
                }
            }
        }
        sShiftedViews.add(view)
        unclipAncestors(view, root)
    }

    private fun isTargetOverlayView(v: View): Boolean {
        if (v.id == 0x7f0b458c) return false
        val id = v.id
        val resName = try {
            if (id != View.NO_ID && v.resources != null) {
                v.resources.getResourceEntryName(id)
            } else null
        } catch (_: Throwable) { null }
        if (resName == "mon") return false

        if (id == ID_CAPTION_CONTAINER || id == ID_CAPTION_CONTAINER_ALT ||
            id == ID_AUDIO_DISK || id == ID_FLOATING_NOTES ||
            id == ID_SEEK_BAR_SCRUBBER || id == ID_SEEK_BAR_SCRUBBER_ALT ||
            id == ID_ACTION_RAIL_CONTAINER) {
            return true
        }
        if (resName == "b9i" || resName == "nlf" || resName == "znw" || resName == "zll" || resName == "jz8") {
            return true
        }
        val clsName = v.javaClass.name
        if (clsName.endsWith(".0ODk")) {
            return true
        }
        return false
    }

    private fun setupSeekBarTouchDelegate(view: View) {
        if (view.getTag(TAG_SEEK_BAR_TOUCH_DELEGATE) != null) return
        view.setTag(TAG_SEEK_BAR_TOUCH_DELEGATE, true)
        val parent = view.parent as? ViewGroup ?: return
        parent.post {
            val rect = Rect()
            view.getHitRect(rect)
            val density = view.resources.displayMetrics.density
            val expandPx = (25f * density).toInt()
            rect.top -= expandPx
            rect.bottom += expandPx
            parent.touchDelegate = TouchDelegate(rect, view)
        }
    }

    private fun shiftFeedOverlaysInRoot(root: ViewGroup) {
        val shiftY = getShiftY(root.context)

        fun traverse(v: View) {
            if (v.tag == "HyperFloatingBar") return

            if (isTargetOverlayView(v)) {
                shiftTargetView(v, shiftY, root)
                if (v.id == ID_SEEK_BAR_SCRUBBER || v.id == ID_SEEK_BAR_SCRUBBER_ALT ||
                    v.javaClass.name.endsWith(".0ODk")
                ) {
                    setupSeekBarTouchDelegate(v)
                }
            }

            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    traverse(v.getChildAt(i))
                }
            }
        }

        traverse(root)
    }

    private fun setupFeedShifterWatcher(activity: Activity) {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (contentView.getTag(TAG_FEED_WATCHER_KEY) == null) {
            contentView.setTag(TAG_FEED_WATCHER_KEY, true)

            val updateShift = {
                shiftFeedOverlaysInRoot(contentView)
            }

            contentView.viewTreeObserver.addOnGlobalLayoutListener(updateShift)
        }
        contentView.post {
            shiftFeedOverlaysInRoot(contentView)
        }
    }

    // --- Hook Registrations ---

    private fun hookWindowInsets(module: XposedModule) {
        try {
            module.hook(
                View::class.java.getDeclaredMethod("dispatchApplyWindowInsets", WindowInsets::class.java)
            ).intercept { chain ->
                val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                if (view.id == android.R.id.content) {
                    try {
                        val insets = chain.args[0] as? WindowInsets
                        if (insets != null) {
                            val type = WindowInsets.Type.systemBars()
                            val current = insets.getInsets(type)
                            if (current.bottom > 0) {
                                val fakeInsets = WindowInsets.Builder(insets)
                                    .setInsets(type, Insets.of(current.left, current.top, current.right, 0))
                                    .build()
                                chain.args[0] = fakeInsets
                            }
                        }
                    } catch (_: Throwable) {}
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook dispatchApplyWindowInsets: ${t.message}")
        }
    }

    private fun hookStockTabBar(module: XposedModule, classLoader: ClassLoader) {
        try {
            val tabBarClass = classLoader.loadClass("X.0eaq")
            for (ctor in tabBarClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    val tabBar = chain.thisObject as? ViewGroup ?: return@intercept result
                    Log.i(TAG, "X.0eaq constructor intercepted: $tabBar")
                    suppressNativeTabBar(tabBar)
                    tabBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            Log.i(TAG, "X.0eaq onViewAttachedToWindow listener fired")
                            suppressNativeTabBar(tabBar)
                            tabBar.post { executeUIHeist(tabBar) }
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    })
                    result
                }
            }

            try {
                val onMeasureMethod = tabBarClass.getDeclaredMethod(
                    "onMeasure",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                module.hook(onMeasureMethod).intercept { chain ->
                    chain.proceed()
                    val view = chain.thisObject as? View
                    val setMeasuredDimension = View::class.java.getDeclaredMethod(
                        "setMeasuredDimension",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    setMeasuredDimension.isAccessible = true
                    setMeasuredDimension.invoke(view, 0, 0)
                }
            } catch (_: Throwable) {}

            try {
                val setVisibilityMethod = tabBarClass.getMethod("setVisibility", Int::class.javaPrimitiveType)
                module.hook(setVisibilityMethod).intercept { chain ->
                    val visibility = chain.args[0] as? Int ?: View.VISIBLE
                    val tabBar = chain.thisObject as? View
                    val isMoi = tabBar?.let { v ->
                        if (v.id == ID_STOCK_TAB_BAR) true
                        else {
                            val resName = try {
                                if (v.id != View.NO_ID && v.resources != null) {
                                    v.resources.getResourceEntryName(v.id)
                                } else null
                            } catch (_: Throwable) { null }
                            resName == "moi"
                        }
                    } ?: false

                    if (isMoi) {
                        isBarVisibleState.value = (visibility == View.VISIBLE)
                    }
                    val result = chain.proceed()
                    if (isMoi && tabBar != null) {
                        tabBar.alpha = 0f
                        tabBar.translationY = -9999f
                        tabBar.layoutParams?.let { lp ->
                            if (lp.height != 0) {
                                lp.height = 0
                                tabBar.requestLayout()
                            }
                        }
                    }
                    result
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook X.0eaq.setVisibility: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "X.0eaq class hook not found or failed: ${t.message}")
        }
    }

    private fun hookFragmentTabHost(module: XposedModule, classLoader: ClassLoader) {
        try {
            val fthClass = classLoader.loadClass("com.ss.android.ugc.aweme.ui.FragmentTabHost")

            // Hook onTabChanged(String tabId)
            try {
                val onTabChangedMethod = fthClass.getMethod("onTabChanged", String::class.java)
                module.hook(onTabChangedMethod).intercept { chain ->
                    val tabId = chain.args[0] as? String
                    if (tabId != null) {
                        val idx = when (tabId) {
                            "HOME" -> 0
                            "FRIENDS" -> 1
                            "PUBLISH" -> 2
                            "NOTIFICATION" -> 3
                            "USER" -> 4
                            else -> -1
                        }
                        if (idx >= 0) {
                            externalTabState.intValue = idx
                        }
                    }
                    chain.proceed()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "FragmentTabHost.onTabChanged hook failed: ${t.message}")
            }

            // Hook onViewAttachedToWindow to ensure early suppression and UI heist
            try {
                val onAttachedMethod = fthClass.getDeclaredMethod("onAttachedToWindow")
                module.hook(onAttachedMethod).intercept { chain ->
                    val result = chain.proceed()
                    val fth = chain.thisObject as? ViewGroup
                    if (fth != null) {
                        val tabBar = findTabBar(fth)
                        if (tabBar != null) {
                            suppressNativeTabBar(tabBar)
                            fth.post { executeUIHeist(tabBar) }
                        }
                    }
                    result
                }
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.w(TAG, "FragmentTabHost class hook not found: ${t.message}")
        }
    }

    private fun hookActivities(module: XposedModule, classLoader: ClassLoader) {
        val targetActivities = listOf(
            "com.ss.android.ugc.aweme.splash.SplashActivity",
            "com.ss.android.ugc.aweme.main.MainActivity"
        )

        for (activityName in targetActivities) {
            try {
                val actClass = classLoader.loadClass(activityName)

                // onPostCreate
                try {
                    val onPostCreateMethod = actClass.getDeclaredMethod("onPostCreate", android.os.Bundle::class.java)
                    module.hook(onPostCreateMethod).intercept { chain ->
                        val result = chain.proceed()
                        val act = chain.thisObject as? Activity
                        if (act != null) {
                            act.window?.decorView?.post {
                                val decor = act.window?.decorView as? ViewGroup ?: return@post
                                val tabBar = findTabBar(decor)
                                if (tabBar != null) {
                                    suppressNativeTabBar(tabBar)
                                    executeUIHeist(act, tabBar)
                                }
                            }
                        }
                        result
                    }
                } catch (_: Throwable) {}

                // onResume
                try {
                    val onResumeMethod = actClass.getDeclaredMethod("onResume")
                    module.hook(onResumeMethod).intercept { chain ->
                        val result = chain.proceed()
                        val act = chain.thisObject as? Activity
                        if (act != null) {
                            val targetVisibility = if (isCommentSheetOpen(act)) View.GONE else View.VISIBLE
                            setBarVisibility(act, targetVisibility)
                            val decor = act.window?.decorView as? ViewGroup
                            if (decor != null) {
                                val tabBar = findTabBar(decor)
                                if (tabBar != null) {
                                    suppressNativeTabBar(tabBar)
                                    executeUIHeist(act, tabBar)
                                }
                            }
                        }
                        result
                    }
                } catch (_: Throwable) {}
            } catch (t: Throwable) {
                Log.w(TAG, "Activity hook failed for $activityName: ${t.message}")
            }
        }
    }

    private fun hookCommentFragments(module: XposedModule, classLoader: ClassLoader) {
        val showMethodNames = setOf("onResume")
        val hideMethodNames = setOf("onPause", "onStop", "onDestroy", "onDestroyView", "dismiss", "dismissAllowingStateLoss")
        val hookedMethods = mutableSetOf<java.lang.reflect.Method>()

        for (fragName in COMMENT_FRAGMENT_CLASSES) {
            try {
                val fragClass = classLoader.loadClass(fragName)
                var curr: Class<*>? = fragClass
                while (curr != null && curr != Any::class.java) {
                    val cName = curr.name
                    if (cName.startsWith("androidx.fragment.app.") || cName.startsWith("android.")) {
                        break
                    }
                    for (m in curr.declaredMethods) {
                        if (m.parameterTypes.isNotEmpty()) continue
                        if (!hookedMethods.add(m)) continue

                        val mName = m.name
                        if (mName in showMethodNames) {
                            try {
                                module.hook(m).intercept { chain ->
                                    val thisClassName = chain.thisObject?.javaClass?.name
                                    if (COMMENT_FRAGMENT_CLASSES.contains(thisClassName) || thisClassName?.contains("Comment") == true) {
                                        isCommentSheetVisible = true
                                        isBarVisibleState.value = false
                                    }
                                    chain.proceed()
                                }
                            } catch (_: Throwable) {}
                        } else if (mName in hideMethodNames) {
                            try {
                                module.hook(m).intercept { chain ->
                                    val thisClassName = chain.thisObject?.javaClass?.name
                                    if (COMMENT_FRAGMENT_CLASSES.contains(thisClassName) || thisClassName?.contains("Comment") == true) {
                                        isCommentSheetVisible = false
                                        isBarVisibleState.value = true
                                    }
                                    chain.proceed()
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    curr = curr.superclass
                }
            } catch (_: Throwable) {}
        }
    }

    private fun hookFeedShifter(module: XposedModule, classLoader: ClassLoader) {
        // Hook VerticalViewPager onLayout to trigger shift updates
        try {
            val vvpClass = classLoader.loadClass("com.ss.android.ugc.aweme.common.widget.VerticalViewPager")
            val onLayoutMethod = vvpClass.getDeclaredMethod(
                "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            module.hook(onLayoutMethod).intercept { chain ->
                val result = chain.proceed()
                val vvp = chain.thisObject as? ViewGroup
                if (vvp != null) {
                    shiftFeedOverlaysInRoot(vvp)
                }
                result
            }
        } catch (t: Throwable) {
            Log.w(TAG, "VerticalViewPager onLayout hook failed: ${t.message}")
        }
    }
}
