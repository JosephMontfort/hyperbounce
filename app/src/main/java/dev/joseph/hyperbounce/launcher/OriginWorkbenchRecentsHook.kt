package dev.joseph.hyperbounce.launcher

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Origin Workbench Recents Hook (Vivo OriginOS 7 "原子工作台" Architecture)
 *
 * Implements a 5-task workspace inside Xiaomi HyperOS System Launcher:
 * - 1 Hero App: Centered in the primary stage (~72% viewport), running live in the foreground
 *   in Freeform mode (`WINDOWING_MODE_FREEFORM = 5`) at custom window bounds.
 * - 4 Parked Shelf Apps: Arranged along an interactive bottom dock (or side column in landscape).
 * - Xiaomi Folme Physical Animation Engine: Drives smooth physical spring transitions when
 *   swapping cards between the shelf and the Hero stage.
 * - Non-Fullscreen Launch: Tapping any shelf card smoothly swaps it into the Hero stage and
 *   launches it directly into the Hero viewport bounds without triggering fullscreen.
 */
object OriginWorkbenchRecentsHook {
    private const val TAG = "OriginWorkbench"

    // WINDOWING_MODE_FREEFORM = 5 in Android / HyperOS
    private const val WINDOWING_MODE_FREEFORM = 5

    @Volatile
    var currentHeroBounds: Rect = Rect()
        private set

    @Volatile
    var currentHeroRectF: RectF = RectF()
        private set

    private val currentShelfBounds = Array(4) { RectF() }

    @Volatile
    private var isShelfSwapLaunchInProgress = false

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        FolmePhysicsHelper.init(classLoader)

        hookAlgorithmTransforms(module, classLoader)
        hookTaskViewInteractions(module, classLoader)
        hookActivityManagerWrapper(module, classLoader)
    }

    /**
     * Hook 1: Transforms and Coordinate System
     * Intercepts `getTaskViewTransform` and `calculateTaskViewOffsetXAndY` on layout algorithms.
     */
    private fun hookAlgorithmTransforms(module: XposedInterface, classLoader: ClassLoader) {
        val algoClasses = listOf(
            "com.miui.home.recents.views.TaskStackViewsAlgorithmHorizontal",
            "com.miui.home.recents.views.TaskStackViewsAlgorithmVertical",
            "com.miui.home.recents.views.TaskStackViewsAlgorithmStack"
        )

        for (algoClassName in algoClasses) {
            try {
                val clazz = Class.forName(algoClassName, false, classLoader)

                // 1. Hook getTaskViewTransform(int i, float f, TaskViewTransform taskViewTransform, TaskViewTransform taskViewTransform2)
                val transformMethod = clazz.declaredMethods.firstOrNull {
                    it.name == "getTaskViewTransform" && it.parameterTypes.size == 4 &&
                            it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                            it.parameterTypes[1] == Float::class.javaPrimitiveType
                }

                if (transformMethod != null) {
                    transformMethod.isAccessible = true
                    module.hook(transformMethod).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LauncherConfigHolder.iosAnimConfig
                            if (!cfg.enabled || !cfg.workbenchRecentsEnabled) {
                                return chain.proceed()
                            }

                            val taskIndex = (chain.args[0] as? Number)?.toInt() ?: 0
                            val scrollDeltaP = (chain.args[1] as? Number)?.toFloat() ?: 0f
                            val transformObj = chain.args[2] ?: return chain.proceed()

                            val applied = applyWorkbenchTransform(
                                algoInstance = chain.thisObject,
                                taskIndex = taskIndex,
                                scrollDeltaP = scrollDeltaP,
                                transformObj = transformObj,
                                cfg = cfg
                            )

                            return if (applied) null else chain.proceed()
                        }
                    })
                }

                // 2. Hook calculateTaskViewOffsetXAndY(int i)
                val offsetMethod = clazz.declaredMethods.firstOrNull {
                    it.name == "calculateTaskViewOffsetXAndY" && it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == Int::class.javaPrimitiveType
                }

                if (offsetMethod != null) {
                    offsetMethod.isAccessible = true
                    module.hook(offsetMethod).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LauncherConfigHolder.iosAnimConfig
                            if (!cfg.enabled || !cfg.workbenchRecentsEnabled) {
                                return chain.proceed()
                            }

                            val taskIndex = (chain.args[0] as? Number)?.toInt() ?: 0
                            val offsets = getWorkbenchOffsets(chain.thisObject, taskIndex, cfg)
                            return offsets ?: chain.proceed()
                        }
                    })
                }

                Log.i(TAG, "Hooked algorithm transforms for $algoClassName")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed hooking algorithm $algoClassName", t)
            }
        }
    }

    /**
     * Calculates the exact Origin Workbench spatial layout for task index `taskIndex`.
     */
    private fun applyWorkbenchTransform(
        algoInstance: Any,
        taskIndex: Int,
        scrollDeltaP: Float,
        transformObj: Any,
        cfg: IOSTransitionConfig
    ): Boolean {
        return try {
            val algoClass = algoInstance.javaClass
            val stackRect = getFieldVal<Rect>(algoInstance, "mTaskStackViewRect") ?: return false
            val baseRectF = getFieldVal<RectF>(algoInstance, "mTaskViewRectF") ?: return false
            val numTasks = getFieldVal<Int>(algoInstance, "mNumStackTasks") ?: 1

            val stackW = stackRect.width().toFloat()
            val stackH = stackRect.height().toFloat()
            val baseW = baseRectF.width()
            val baseH = baseRectF.height()

            if (stackW <= 0f || stackH <= 0f || baseW <= 0f || baseH <= 0f) {
                return false
            }

            val isLandscape = stackW > stackH

            val heroScale = cfg.workbenchHeroScale.coerceIn(0.55f, 0.90f)
            val shelfScale = cfg.workbenchShelfScale.coerceIn(0.14f, 0.35f)

            if (!isLandscape) {
                // Portrait Layout
                val heroW = stackW * heroScale
                val heroH = (heroW * (stackH / stackW) * 0.76f).coerceAtMost(stackH * 0.58f)
                val heroLeft = (stackW - heroW) / 2f
                val heroTop = stackRect.top + stackH * 0.10f

                // Cache Hero bounds for Freeform windowing
                currentHeroBounds = Rect(
                    heroLeft.toInt(),
                    heroTop.toInt(),
                    (heroLeft + heroW).toInt(),
                    (heroTop + heroH).toInt()
                )
                currentHeroRectF = RectF(heroLeft, heroTop, heroLeft + heroW, heroTop + heroH)

                val heroCenterX = heroLeft + heroW / 2f
                val heroCenterY = heroTop + heroH / 2f

                if (taskIndex == 0) {
                    // HERO TASK (Primary Live Stage)
                    val offsetX = heroCenterX - baseRectF.centerX()
                    val offsetY = heroCenterY - baseRectF.centerY()

                    setTransformFields(
                        transformObj = transformObj,
                        baseRectF = baseRectF,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = heroScale,
                        alpha = 1.0f,
                        translationZ = 24.0f,
                        visible = true
                    )
                    return true
                } else if (taskIndex in 1..4) {
                    // SHELF TASKS (Up to 4 parked recents apps in bottom dock)
                    val slot = taskIndex - 1
                    val visibleShelfCount = (numTasks - 1).coerceIn(1, 4)

                    val cardVisualW = baseW * shelfScale
                    val cardVisualH = baseH * shelfScale
                    val gap = 18f

                    val totalShelfW = visibleShelfCount * cardVisualW + (visibleShelfCount - 1) * gap
                    val startCenterX = stackRect.centerX() - (totalShelfW / 2f) + (cardVisualW / 2f)

                    val slotCenterX = startCenterX + slot * (cardVisualW + gap)
                    val slotCenterY = stackRect.bottom - (cardVisualH / 2f) - 68f

                    // Cache shelf slot bounds for Folme swap
                    val slotLeft = slotCenterX - cardVisualW / 2f
                    val slotTop = slotCenterY - cardVisualH / 2f
                    currentShelfBounds[slot].set(slotLeft, slotTop, slotLeft + cardVisualW, slotTop + cardVisualH)

                    val offsetX = slotCenterX - baseRectF.centerX()
                    val offsetY = slotCenterY - baseRectF.centerY()

                    setTransformFields(
                        transformObj = transformObj,
                        baseRectF = baseRectF,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = shelfScale,
                        alpha = 1.0f,
                        translationZ = (12 - slot).toFloat(),
                        visible = true
                    )
                    return true
                } else {
                    // Older tasks (> 4 in recents history): parked out of view
                    setFieldVal(transformObj, "visible", false)
                    return true
                }
            } else {
                // Landscape / Foldable Layout (Hero on left, 4-card column on right)
                val heroW = stackW * 0.65f
                val heroH = stackH * 0.82f
                val heroLeft = stackRect.left + stackW * 0.04f
                val heroTop = stackRect.top + (stackH - heroH) / 2f

                currentHeroBounds = Rect(
                    heroLeft.toInt(),
                    heroTop.toInt(),
                    (heroLeft + heroW).toInt(),
                    (heroTop + heroH).toInt()
                )
                currentHeroRectF = RectF(heroLeft, heroTop, heroLeft + heroW, heroTop + heroH)

                val heroCenterX = heroLeft + heroW / 2f
                val heroCenterY = heroTop + heroH / 2f

                if (taskIndex == 0) {
                    val offsetX = heroCenterX - baseRectF.centerX()
                    val offsetY = heroCenterY - baseRectF.centerY()

                    setTransformFields(
                        transformObj = transformObj,
                        baseRectF = baseRectF,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = heroScale * 0.88f,
                        alpha = 1.0f,
                        translationZ = 24.0f,
                        visible = true
                    )
                    return true
                } else if (taskIndex in 1..4) {
                    val slot = taskIndex - 1
                    val visibleShelfCount = (numTasks - 1).coerceIn(1, 4)

                    val cardVisualW = baseW * shelfScale * 0.85f
                    val cardVisualH = baseH * shelfScale * 0.85f
                    val gap = 14f

                    val totalShelfH = visibleShelfCount * cardVisualH + (visibleShelfCount - 1) * gap
                    val startCenterY = stackRect.centerY() - (totalShelfH / 2f) + (cardVisualH / 2f)

                    val slotCenterX = stackRect.right - (cardVisualW / 2f) - 42f
                    val slotCenterY = startCenterY + slot * (cardVisualH + gap)

                    val slotLeft = slotCenterX - cardVisualW / 2f
                    val slotTop = slotCenterY - cardVisualH / 2f
                    currentShelfBounds[slot].set(slotLeft, slotTop, slotLeft + cardVisualW, slotTop + cardVisualH)

                    val offsetX = slotCenterX - baseRectF.centerX()
                    val offsetY = slotCenterY - baseRectF.centerY()

                    setTransformFields(
                        transformObj = transformObj,
                        baseRectF = baseRectF,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = shelfScale * 0.85f,
                        alpha = 1.0f,
                        translationZ = (12 - slot).toFloat(),
                        visible = true
                    )
                    return true
                } else {
                    setFieldVal(transformObj, "visible", false)
                    return true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error calculating workbench transform", t)
            false
        }
    }

    private fun getWorkbenchOffsets(algoInstance: Any, taskIndex: Int, cfg: IOSTransitionConfig): IntArray? {
        val stackRect = getFieldVal<Rect>(algoInstance, "mTaskStackViewRect") ?: return null
        val baseRectF = getFieldVal<RectF>(algoInstance, "mTaskViewRectF") ?: return null
        val numTasks = getFieldVal<Int>(algoInstance, "mNumStackTasks") ?: 1

        val stackW = stackRect.width().toFloat()
        val stackH = stackRect.height().toFloat()
        if (stackW <= 0f || stackH <= 0f) return null

        val isLandscape = stackW > stackH
        val heroScale = cfg.workbenchHeroScale.coerceIn(0.55f, 0.90f)
        val shelfScale = cfg.workbenchShelfScale.coerceIn(0.14f, 0.35f)

        return if (!isLandscape) {
            if (taskIndex == 0) {
                val heroW = stackW * heroScale
                val heroH = (heroW * (stackH / stackW) * 0.76f).coerceAtMost(stackH * 0.58f)
                val heroLeft = (stackW - heroW) / 2f
                val heroTop = stackRect.top + stackH * 0.10f
                val heroCenterX = heroLeft + heroW / 2f
                val heroCenterY = heroTop + heroH / 2f
                intArrayOf(
                    (heroCenterX - baseRectF.centerX()).toInt(),
                    (heroCenterY - baseRectF.centerY()).toInt()
                )
            } else if (taskIndex in 1..4) {
                val slot = taskIndex - 1
                val visibleShelfCount = (numTasks - 1).coerceIn(1, 4)
                val cardVisualW = baseRectF.width() * shelfScale
                val cardVisualH = baseRectF.height() * shelfScale
                val gap = 18f
                val totalShelfW = visibleShelfCount * cardVisualW + (visibleShelfCount - 1) * gap
                val startCenterX = stackRect.centerX() - (totalShelfW / 2f) + (cardVisualW / 2f)
                val slotCenterX = startCenterX + slot * (cardVisualW + gap)
                val slotCenterY = stackRect.bottom - (cardVisualH / 2f) - 68f
                intArrayOf(
                    (slotCenterX - baseRectF.centerX()).toInt(),
                    (slotCenterY - baseRectF.centerY()).toInt()
                )
            } else {
                intArrayOf(-9999, -9999)
            }
        } else {
            if (taskIndex == 0) {
                val heroW = stackW * 0.65f
                val heroLeft = stackRect.left + stackW * 0.04f
                val heroCenterX = heroLeft + heroW / 2f
                val heroCenterY = stackRect.centerY().toFloat()
                intArrayOf(
                    (heroCenterX - baseRectF.centerX()).toInt(),
                    (heroCenterY - baseRectF.centerY()).toInt()
                )
            } else if (taskIndex in 1..4) {
                val slot = taskIndex - 1
                val visibleShelfCount = (numTasks - 1).coerceIn(1, 4)
                val cardVisualW = baseRectF.width() * shelfScale * 0.85f
                val cardVisualH = baseRectF.height() * shelfScale * 0.85f
                val gap = 14f
                val totalShelfH = visibleShelfCount * cardVisualH + (visibleShelfCount - 1) * gap
                val startCenterY = stackRect.centerY() - (totalShelfH / 2f) + (cardVisualH / 2f)
                val slotCenterX = stackRect.right - (cardVisualW / 2f) - 42f
                val slotCenterY = startCenterY + slot * (cardVisualH + gap)
                intArrayOf(
                    (slotCenterX - baseRectF.centerX()).toInt(),
                    (slotCenterY - baseRectF.centerY()).toInt()
                )
            } else {
                intArrayOf(-9999, -9999)
            }
        }
    }

    private fun setTransformFields(
        transformObj: Any,
        baseRectF: RectF,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        alpha: Float,
        translationZ: Float,
        visible: Boolean
    ) {
        val rectObj = getFieldVal<RectF>(transformObj, "rect")
        rectObj?.set(baseRectF)
        rectObj?.offset(offsetX, offsetY)

        setFieldVal(transformObj, "scale", scale)
        setFieldVal(transformObj, "alpha", alpha)
        setFieldVal(transformObj, "dimming", 0.0f)
        setFieldVal(transformObj, "titleAlpha", 1.0f)
        setFieldVal(transformObj, "headerAlpha", 1.0f)
        setFieldVal(transformObj, "shadowAlpha", 0.20f)
        setFieldVal(transformObj, "translationZ", translationZ)
        setFieldVal(transformObj, "visible", visible)
    }

    /**
     * Hook 2: TaskView Click Interactions & Folme Physical Card Swapping
     */
    private fun hookTaskViewInteractions(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val taskViewClass = Class.forName("com.miui.home.recents.views.TaskView", false, classLoader)

            val onClickMethod = taskViewClass.getDeclaredMethod("onClick", View::class.java).apply {
                isAccessible = true
            }

            module.hook(onClickMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled || !cfg.workbenchRecentsEnabled) {
                        return chain.proceed()
                    }

                    val taskView = chain.thisObject as? View ?: return chain.proceed()
                    val taskStackView = taskView.parent as? ViewGroup ?: return chain.proceed()

                    val clickedTask = runCatching {
                        taskViewClass.getMethod("getTask").invoke(taskView)
                    }.getOrNull() ?: return chain.proceed()

                    val taskIndex = findTaskIndex(taskStackView, clickedTask)

                    if (taskIndex == 0) {
                        // Tapped Hero Task: proceed normally into fullscreen
                        return chain.proceed()
                    } else if (taskIndex in 1..4) {
                        // Tapped Shelf Task: Execute Folme Physical Card Swap & Non-Fullscreen Launch!
                        val heroTaskView = findTaskViewByIndex(taskStackView, 0)
                        val slot = taskIndex - 1

                        val heroTargetBounds = RectF(currentHeroRectF)
                        val shelfTargetBounds = RectF(currentShelfBounds[slot])

                        val heroScale = cfg.workbenchHeroScale
                        val shelfScale = cfg.workbenchShelfScale

                        isShelfSwapLaunchInProgress = true

                        // 1. Physical Folme Card Swap Animation
                        if (heroTaskView != null) {
                            FolmePhysicsHelper.animateSwap(
                                shelfView = taskView,
                                heroView = heroTaskView,
                                targetHeroBounds = heroTargetBounds,
                                targetShelfBounds = shelfTargetBounds,
                                targetHeroScale = heroScale,
                                targetShelfScale = shelfScale,
                                damping = cfg.workbenchFolmeDamping,
                                response = cfg.workbenchFolmeResponse
                            )
                        }

                        // 2. Launch tapped app in Freeform mode at Hero Bounds (non-fullscreen)
                        launchTaskInHeroBounds(taskView.context, classLoader, clickedTask)

                        return null // Do NOT trigger stock fullscreen launch!
                    }

                    return chain.proceed()
                }
            })

            Log.i(TAG, "Hooked TaskView.onClick for Origin Workbench card swaps")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking TaskView.onClick", t)
        }
    }

    /**
     * Hook 3: Ensures any launch originated from the shelf uses Freeform mode and Hero bounds.
     */
    private fun hookActivityManagerWrapper(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
            val taskKeyClass = Class.forName("com.android.systemui.shared.recents.model.Task\$TaskKey", false, classLoader)

            val startMethod = amwClass.declaredMethods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 &&
                        taskKeyClass.isAssignableFrom(it.parameterTypes[0]) &&
                        ActivityOptions::class.java.isAssignableFrom(it.parameterTypes[1])
            }

            if (startMethod != null) {
                startMethod.isAccessible = true
                module.hook(startMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        if (isShelfSwapLaunchInProgress) {
                            val options = chain.args[1] as? ActivityOptions
                            if (options != null) {
                                applyFreeformHeroOptions(options)
                            }
                            isShelfSwapLaunchInProgress = false
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Hooked ActivityManagerWrapper.startActivityFromRecents")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking ActivityManagerWrapper", t)
        }
    }

    /**
     * Launches the task in Freeform mode positioned exactly in the Hero stage bounds.
     */
    private fun launchTaskInHeroBounds(context: Context, classLoader: ClassLoader, task: Any) {
        try {
            val taskKey = task.javaClass.getField("key").get(task)
            val taskId = runCatching { taskKey.javaClass.getField("id").getInt(taskKey) }.getOrNull() ?: -1
            val options = ActivityOptions.makeBasic()

            applyFreeformHeroOptions(options)

            val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
            val getInstanceMethod = amwClass.getMethod("getInstance")
            val amwInstance = getInstanceMethod.invoke(null)

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
                Log.i(TAG, "Successfully launched shelf task (TaskKey) into Hero bounds: $currentHeroBounds")
            } else if (startMethodInt != null && taskId != -1) {
                startMethodInt.invoke(amwInstance, taskId, options)
                Log.i(TAG, "Successfully launched shelf task (id=$taskId) into Hero bounds: $currentHeroBounds")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch task in hero bounds", t)
        }
    }

    private fun applyFreeformHeroOptions(options: ActivityOptions) {
        try {
            // WINDOWING_MODE_FREEFORM = 5
            val setModeMethod = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            setModeMethod.invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (_: Throwable) {}

        try {
            if (!currentHeroBounds.isEmpty) {
                options.launchBounds = currentHeroBounds
            }
        } catch (_: Throwable) {}
    }

    private fun findTaskIndex(taskStackView: ViewGroup, task: Any): Int {
        return try {
            val taskIndexMapField = runCatching {
                val algoField = taskStackView.javaClass.getDeclaredField("mTaskStackLayoutAlgorithm").apply { isAccessible = true }
                val algo = algoField.get(taskStackView)
                algo?.javaClass?.superclass?.getDeclaredField("mTaskIndexMap")?.apply { isAccessible = true }?.get(algo) as? SparseIntArray
            }.getOrNull()

            val taskId = runCatching {
                val key = task.javaClass.getField("key").get(task)
                key.javaClass.getField("id").getInt(key)
            }.getOrNull() ?: -1

            taskIndexMapField?.get(taskId, -1) ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    private fun findTaskViewByIndex(taskStackView: ViewGroup, targetIndex: Int): View? {
        val childCount = taskStackView.childCount
        for (i in 0 until childCount) {
            val child = taskStackView.getChildAt(i)
            val childTask = runCatching {
                child.javaClass.getMethod("getTask").invoke(child)
            }.getOrNull() ?: continue

            val idx = findTaskIndex(taskStackView, childTask)
            if (idx == targetIndex) {
                return child
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFieldVal(obj: Any, name: String): T? {
        return try {
            var curr: Class<*>? = obj.javaClass
            while (curr != null && curr != Any::class.java) {
                try {
                    val field = curr.getDeclaredField(name)
                    field.isAccessible = true
                    return field.get(obj) as? T
                } catch (_: NoSuchFieldException) {
                    curr = curr.superclass
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun setFieldVal(obj: Any, name: String, value: Any?) {
        try {
            var curr: Class<*>? = obj.javaClass
            while (curr != null && curr != Any::class.java) {
                try {
                    val field = curr.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(obj, value)
                    return
                } catch (_: NoSuchFieldException) {
                    curr = curr.superclass
                }
            }
        } catch (_: Throwable) {}
    }
}
