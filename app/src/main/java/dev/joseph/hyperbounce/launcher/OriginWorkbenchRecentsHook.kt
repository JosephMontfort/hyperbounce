package dev.joseph.hyperbounce.launcher

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference

/**
 * Origin Workbench Recents Hook (Vivo OriginOS 7 "原子工作台" Architecture)
 *
 * Implements a complete, standalone replacement of the Recents screen:
 * - Hooks both Full-Screen Gesture navigation (OverviewState) and 3-Button navigation
 *   (setOverviewStateEnabled / setVisibility) so Workbench activates seamlessly in all modes.
 * - Pure recents UI inside RecentsContainer over the wallpaper blur. NO stock freeform windows.
 * - Injects [OriginWorkbenchContainerView] directly into [RecentsContainer].
 * - When Origin Workbench is ENABLED:
 *     - Hijacks and hides `mTaskStackView` / `mRecentsView` (`View.INVISIBLE`).
 *     - Hijacks and removes `mMemoryAndClearContainer` (Clear All button completely suppressed).
 *     - Shows 1 Hero card (large) and 4 Shelf cards with authentic Folme physics and swap animations.
 * - When Origin Workbench is DISABLED:
 *     - Completely restores stock Xiaomi recents views (`mTaskStackView`, Clear All button)
 *       with zero side effects.
 */
object OriginWorkbenchRecentsHook {
    private const val TAG = "OriginWorkbenchHook"

    @Volatile
    private var sWorkbenchViewRef: WeakReference<OriginWorkbenchContainerView>? = null

    @Volatile
    private var isWorkbenchActive = false

    @Volatile
    private var lastEnterTimestamp = 0L

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        FolmePhysicsHelper.init(classLoader)

        hookOverviewState(module, classLoader)
        hookRecentsContainer(module, classLoader)
        hookRecentsView(module, classLoader)
        hookClearContainerSuppression(module, classLoader)
        hookTaskStackViewHeroTarget(module, classLoader)
    }

    /**
     * Hook 1: OverviewState Lifecycle
     * OverviewState.onStateEnabled and onExitState are ALWAYS called when entering/exiting
     * recents in both gesture navigation and button navigation.
     */
    private fun hookOverviewState(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val overviewStateClass = Class.forName("com.miui.home.recents.OverviewState", false, classLoader)

            val onStateEnabledMethod = overviewStateClass.declaredMethods.firstOrNull {
                it.name == "onStateEnabled" && it.parameterTypes.size == 1
            }
            if (onStateEnabledMethod != null) {
                onStateEnabledMethod.isAccessible = true
                module.hook(onStateEnabledMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val res = chain.proceed()
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                            val baseLauncher = chain.args[0]
                            val container = runCatching {
                                baseLauncher?.javaClass?.getMethod("getRecentsContainer")?.invoke(baseLauncher) as? ViewGroup
                            }.getOrNull()
                            if (container != null) {
                                Log.i(TAG, "OverviewState.onStateEnabled -> Entering Workbench")
                                handleWorkbenchEnter(container, classLoader)
                            }
                        }
                        return res
                    }
                })
            }

            val onExitStateMethod = overviewStateClass.declaredMethods.firstOrNull {
                it.name == "onExitState" && it.parameterTypes.size == 1
            }
            if (onExitStateMethod != null) {
                onExitStateMethod.isAccessible = true
                module.hook(onExitStateMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val baseLauncher = chain.args[0]
                        val container = runCatching {
                            baseLauncher?.javaClass?.getMethod("getRecentsContainer")?.invoke(baseLauncher) as? ViewGroup
                        }.getOrNull()
                        if (container != null) {
                            Log.i(TAG, "OverviewState.onExitState -> Exiting Workbench")
                            handleWorkbenchExit(container)
                        }
                        return chain.proceed()
                    }
                })
            }

            Log.i(TAG, "Successfully hooked OverviewState lifecycle")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking OverviewState", t)
        }
    }

    /**
     * Hook 2: RecentsContainer Lifecycle
     */
    private fun hookRecentsContainer(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val recentsContainerClass = Class.forName("com.miui.home.recents.views.RecentsContainer", false, classLoader)

            // onFinishInflate: attach workbench overlay
            val onFinishInflateMethod = recentsContainerClass.declaredMethods.firstOrNull {
                it.name == "onFinishInflate" && it.parameterTypes.isEmpty()
            }
            if (onFinishInflateMethod != null) {
                onFinishInflateMethod.isAccessible = true
                module.hook(onFinishInflateMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val res = chain.proceed()
                        val container = chain.thisObject as? ViewGroup ?: return res
                        ensureWorkbenchViewAttached(container, classLoader)
                        return res
                    }
                })
            }

            // setVisibility(int): backup trigger for recents container visibility
            val setVisibilityMethod = recentsContainerClass.getMethod("setVisibility", Int::class.javaPrimitiveType)
            module.hook(setVisibilityMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val visibility = chain.args[0] as? Int ?: 0
                    val container = chain.thisObject as? ViewGroup
                    val res = chain.proceed()

                    if (container != null) {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                            if (visibility == View.VISIBLE) {
                                handleWorkbenchEnter(container, classLoader)
                            } else {
                                handleWorkbenchExit(container)
                            }
                        }
                    }
                    return res
                }
            })

            // setOverviewStateEnabled(boolean z)
            val setOverviewMethod = recentsContainerClass.declaredMethods.firstOrNull {
                it.name == "setOverviewStateEnabled" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            if (setOverviewMethod != null) {
                setOverviewMethod.isAccessible = true
                module.hook(setOverviewMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val isEnabled = chain.args[0] as? Boolean ?: false
                        val container = chain.thisObject as? ViewGroup ?: return chain.proceed()

                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                            if (isEnabled) {
                                handleWorkbenchEnter(container, classLoader)
                            }
                            // Note: do not exit on isEnabled == false, because OverviewState passes false
                            // on gesture enter!
                        } else {
                            hideWorkbenchView(container)
                        }

                        return chain.proceed()
                    }
                })
            }

            // onExitState()
            val onExitStateMethod = recentsContainerClass.declaredMethods.firstOrNull {
                it.name == "onExitState" && it.parameterTypes.isEmpty()
            }
            if (onExitStateMethod != null) {
                onExitStateMethod.isAccessible = true
                module.hook(onExitStateMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val container = chain.thisObject as? ViewGroup ?: return chain.proceed()
                        handleWorkbenchExit(container)
                        return chain.proceed()
                    }
                })
            }

            Log.i(TAG, "Successfully hooked RecentsContainer lifecycle")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking RecentsContainer", t)
        }
    }

    /**
     * Hook 3: RecentsView suppression
     * Ensures RecentsView remains invisible, does not draw cards/tasks, and does not intercept touches when Workbench is active.
     */
    private fun hookRecentsView(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val recentsViewClass = Class.forName("com.miui.home.recents.views.RecentsView", false, classLoader)

            // 1. setVisibility: Block stock RecentsView from becoming VISIBLE when Workbench is active
            val setVisibilityMethod = recentsViewClass.getMethod("setVisibility", Int::class.javaPrimitiveType)
            module.hook(setVisibilityMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    val vis = chain.args[0] as? Int ?: 0
                    if (cfg.enabled && cfg.workbenchRecentsEnabled && isWorkbenchActive && vis == View.VISIBLE) {
                        return null // Suppress becoming visible!
                    }
                    return chain.proceed()
                }
            })

            // 2. dispatchDraw: Completely suppress drawing stock cards/tasks while Workbench is active
            val dispatchDrawMethod = recentsViewClass.declaredMethods.firstOrNull {
                it.name == "dispatchDraw" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == android.graphics.Canvas::class.java
            }
            if (dispatchDrawMethod != null) {
                dispatchDrawMethod.isAccessible = true
                module.hook(dispatchDrawMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled && isWorkbenchActive) {
                            return null // Suppress drawing stock recents view and task cards completely!
                        }
                        return chain.proceed()
                    }
                })
            }

            // 3. onInterceptTouchEvent: Prevent stock recents view from stealing touch events
            val onInterceptTouchEventMethod = recentsViewClass.declaredMethods.firstOrNull {
                it.name == "onInterceptTouchEvent" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == android.view.MotionEvent::class.java
            }
            if (onInterceptTouchEventMethod != null) {
                onInterceptTouchEventMethod.isAccessible = true
                module.hook(onInterceptTouchEventMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled && isWorkbenchActive) {
                            return false
                        }
                        return chain.proceed()
                    }
                })
            }

            Log.i(TAG, "Successfully hooked RecentsView suppression & touch handling")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking RecentsView", t)
        }
    }

    /**
     * Hook 4: Suppresses the stock Clear All button and RAM text in RecentsContainer when Workbench is active.
     */
    private fun hookClearContainerSuppression(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val recentsContainerClass = Class.forName("com.miui.home.recents.views.RecentsContainer", false, classLoader)

            val isClearVisibleMethod = recentsContainerClass.declaredMethods.firstOrNull {
                it.name == "isClearContainerVisible" && it.parameterTypes.isEmpty()
            }
            if (isClearVisibleMethod != null) {
                isClearVisibleMethod.isAccessible = true
                module.hook(isClearVisibleMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                            return false // Always hide Clear All button in workbench mode!
                        }
                        return chain.proceed()
                    }
                })
            }

            // Suppress RAM / memory info text
            for (m in recentsContainerClass.declaredMethods) {
                if (m.name.contains("canTxtMemInfoShow")) {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LauncherConfigHolder.iosAnimConfig
                            if (cfg.enabled && cfg.workbenchRecentsEnabled && isWorkbenchActive) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                }
            }

            val setupVisibleMethod = recentsContainerClass.declaredMethods.firstOrNull {
                it.name == "setupVisible" && it.parameterTypes.isEmpty()
            }
            if (setupVisibleMethod != null) {
                setupVisibleMethod.isAccessible = true
                module.hook(setupVisibleMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val res = chain.proceed()
                        val container = chain.thisObject as? ViewGroup ?: return res
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                            hideStockClearContainer(container)
                        }
                        return res
                    }
                })
            }

            Log.i(TAG, "Successfully hooked Clear All button & RAM text suppression for Workbench")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking Clear All button suppression", t)
        }
    }

    /**
     * Hook 5: Full-screen gesture visual continuity.
     * Hooks TaskStackView.getTaskViewWithoutHeaderRectF to return the exact Hero Stage bounds,
     * ensuring the foreground app window surface animates smoothly and directly into the Hero Stage!
     */
    private fun hookTaskStackViewHeroTarget(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val taskStackViewClass = Class.forName("com.miui.home.recents.views.TaskStackView", false, classLoader)
            for (m in taskStackViewClass.declaredMethods) {
                if (m.name == "getTaskViewWithoutHeaderRectF") {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LauncherConfigHolder.iosAnimConfig
                            if (cfg.enabled && cfg.workbenchRecentsEnabled) {
                                val tsv = chain.thisObject as? View
                                val context = tsv?.context
                                if (context != null) {
                                    return OriginWorkbenchContainerView.calculateHeroBounds(
                                        context,
                                        cfg.workbenchLayoutStyle
                                    )
                                }
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Successfully hooked TaskStackView.getTaskViewWithoutHeaderRectF for gesture visual continuity")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking TaskStackView.getTaskViewWithoutHeaderRectF", t)
        }
    }

    /**
     * Attaches [OriginWorkbenchContainerView] as an overlay inside [RecentsContainer].
     */
    private fun ensureWorkbenchViewAttached(container: ViewGroup, classLoader: ClassLoader): OriginWorkbenchContainerView? {
        val existing = container.findViewWithTag<OriginWorkbenchContainerView>(OriginWorkbenchContainerView.WORKBENCH_VIEW_TAG)
        if (existing != null) {
            sWorkbenchViewRef = WeakReference(existing)
            return existing
        }

        return try {
            val workbenchView = OriginWorkbenchContainerView(container.context, classLoader, container).apply {
                visibility = View.GONE
            }
            container.addView(workbenchView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            sWorkbenchViewRef = WeakReference(workbenchView)
            Log.i(TAG, "Successfully attached OriginWorkbenchContainerView to RecentsContainer")
            workbenchView
        } catch (t: Throwable) {
            Log.e(TAG, "Error instantiating OriginWorkbenchContainerView", t)
            null
        }
    }

    /**
     * Called when Recents opens with Origin Workbench enabled.
     */
    private fun handleWorkbenchEnter(container: ViewGroup, classLoader: ClassLoader) {
        val now = android.os.SystemClock.uptimeMillis()
        if (isWorkbenchActive && now - lastEnterTimestamp < 500L) {
            return // Debounce rapid multi-hook triggers
        }
        isWorkbenchActive = true
        lastEnterTimestamp = now

        val workbenchView = ensureWorkbenchViewAttached(container, classLoader) ?: return

        // 1. Hide stock RecentsView and its TaskStackView
        hideStockRecentsView(container)

        // 2. Hide stock Clear All container and RAM text
        hideStockClearContainer(container)

        // 3. Show Origin Workbench view
        workbenchView.visibility = View.VISIBLE
        workbenchView.alpha = 1.0f

        // 4. Retrieve recent tasks from RecentsModel
        val tasks = getRecentTasks(container.context, classLoader)
        Log.i(TAG, "Origin Workbench opened with ${tasks.size} recent tasks")

        // 5. Open and animate workbench
        workbenchView.onOpenWorkbench(tasks)
    }

    /**
     * Called when Recents exits.
     */
    private fun handleWorkbenchExit(container: ViewGroup) {
        isWorkbenchActive = false
        hideWorkbenchView(container)
        restoreStockViews(container)
    }

    private fun hideWorkbenchView(container: ViewGroup) {
        val workbenchView = container.findViewWithTag<View>(OriginWorkbenchContainerView.WORKBENCH_VIEW_TAG)
            ?: sWorkbenchViewRef?.get()
        workbenchView?.visibility = View.GONE
    }

    private fun hideStockRecentsView(container: ViewGroup) {
        try {
            val recentsViewId = container.resources.getIdentifier("recents_view", "id", container.context.packageName)
            val recentsView = if (recentsViewId != 0) container.findViewById<View>(recentsViewId) else null
            recentsView?.visibility = View.INVISIBLE
            recentsView?.alpha = 0f
        } catch (_: Throwable) {}
    }

    private fun hideStockClearContainer(container: ViewGroup) {
        try {
            val clearContainerId = container.resources.getIdentifier("memoryAndClearContainer", "id", container.context.packageName)
            val clearContainer = if (clearContainerId != 0) container.findViewById<View>(clearContainerId) else null
            clearContainer?.visibility = View.GONE

            val clearAnimViewId = container.resources.getIdentifier("clearAnimView", "id", container.context.packageName)
            val clearAnimView = if (clearAnimViewId != 0) container.findViewById<View>(clearAnimViewId) else null
            clearAnimView?.visibility = View.GONE

            val txtMemId = container.resources.getIdentifier("txtMemoryContainer", "id", container.context.packageName)
            val txtMem = if (txtMemId != 0) container.findViewById<View>(txtMemId) else null
            txtMem?.visibility = View.GONE
        } catch (_: Throwable) {}
    }

    private fun restoreStockViews(container: ViewGroup) {
        try {
            val recentsViewId = container.resources.getIdentifier("recents_view", "id", container.context.packageName)
            val recentsView = if (recentsViewId != 0) container.findViewById<View>(recentsViewId) else null
            recentsView?.visibility = View.VISIBLE
            recentsView?.alpha = 1f

            val clearContainerId = container.resources.getIdentifier("memoryAndClearContainer", "id", container.context.packageName)
            val clearContainer = if (clearContainerId != 0) container.findViewById<View>(clearContainerId) else null
            clearContainer?.visibility = View.VISIBLE

            val txtMemId = container.resources.getIdentifier("txtMemoryContainer", "id", container.context.packageName)
            val txtMem = if (txtMemId != 0) container.findViewById<View>(txtMemId) else null
            txtMem?.visibility = View.VISIBLE
        } catch (_: Throwable) {}
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRecentTasks(context: Context, classLoader: ClassLoader): List<Any> {
        return try {
            val rmClass = Class.forName("com.miui.home.recents.RecentsModel", false, classLoader)
            val rmInstance = rmClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            val method = rmClass.getMethod("getTaskList", Boolean::class.javaPrimitiveType)
            val list = method.invoke(rmInstance, false) as? List<Any>
            list ?: emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching recent task list from RecentsModel", t)
            emptyList()
        }
    }
}
