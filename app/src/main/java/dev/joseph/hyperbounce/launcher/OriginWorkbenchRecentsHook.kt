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
 * - Injects [OriginWorkbenchContainerView] directly into [RecentsContainer].
 * - When Origin Workbench is ENABLED:
 *     - Hijacks and hides `mTaskStackView` / `mRecentsView` (`View.INVISIBLE`).
 *     - Hijacks and removes `mMemoryAndClearContainer` (Clear All button completely suppressed).
 *     - Launches the active app as a live foreground Freeform window (`WINDOWING_MODE_FREEFORM = 5`)
 *       at the Hero Stage bounds.
 *     - Populates the 4-card interactive shelf dock with authentic Folme physics.
 * - When Origin Workbench is DISABLED:
 *     - Completely restores stock Xiaomi recents views (`mTaskStackView`, Clear All button)
 *       with zero side effects.
 */
object OriginWorkbenchRecentsHook {
    private const val TAG = "OriginWorkbenchHook"

    @Volatile
    private var sWorkbenchViewRef: WeakReference<OriginWorkbenchContainerView>? = null

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        FolmePhysicsHelper.init(classLoader)

        hookRecentsContainer(module, classLoader)
        hookClearContainerSuppression(module, classLoader)
    }

    private fun hookRecentsContainer(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val recentsContainerClass = Class.forName("com.miui.home.recents.views.RecentsContainer", false, classLoader)

            // 1. Hook onFinishInflate to attach OriginWorkbenchContainerView
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

            // 2. Hook setOverviewStateEnabled(boolean z)
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
                            } else {
                                handleWorkbenchExit(container)
                            }
                        } else {
                            // Workbench disabled: make sure workbench view is hidden
                            hideWorkbenchView(container)
                        }

                        return chain.proceed()
                    }
                })
            }

            // 3. Hook onExitState()
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
     * Suppresses the stock Clear All button in RecentsContainer when Workbench is active.
     */
    private fun hookClearContainerSuppression(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val recentsContainerClass = Class.forName("com.miui.home.recents.views.RecentsContainer", false, classLoader)

            // Hook isClearContainerVisible() -> return false when workbench is enabled
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

            // Hook setupVisible() to ensure clear container is GONE in workbench mode
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

            Log.i(TAG, "Successfully hooked Clear All button suppression for Workbench")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking Clear All button suppression", t)
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
        val workbenchView = ensureWorkbenchViewAttached(container, classLoader) ?: return

        // 1. Hide stock RecentsView and its TaskStackView
        hideStockRecentsView(container)

        // 2. Hide stock Clear All container
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
            // Find recents_view
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
