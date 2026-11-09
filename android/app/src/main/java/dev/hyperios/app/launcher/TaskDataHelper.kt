package dev.hyperios.app.launcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Helper utility for inspecting, loading, and terminating recent tasks
 * within Xiaomi HyperOS System Launcher.
 */
object TaskDataHelper {
    private const val TAG = "TaskDataHelper"

    fun getTaskId(task: Any): Int {
        return runCatching {
            val key = task.javaClass.getField("key").get(task)
            key.javaClass.getField("id").getInt(key)
        }.getOrDefault(-1)
    }

    fun getTaskPackageName(task: Any): String? {
        return runCatching {
            val key = task.javaClass.getField("key").get(task)
            val comp = key.javaClass.getMethod("getComponent").invoke(key) as? ComponentName
            comp?.packageName
        }.getOrNull()
    }

    fun getTaskTitle(context: Context, task: Any): String {
        val titleField = runCatching { task.javaClass.getField("title").get(task) as? String }.getOrNull()
        if (!titleField.isNullOrEmpty()) return titleField

        val descField = runCatching { task.javaClass.getField("titleDescription").get(task) as? String }.getOrNull()
        if (!descField.isNullOrEmpty()) return descField

        val pkg = getTaskPackageName(task) ?: return "Application"
        return runCatching {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }

    fun getTaskIcon(context: Context, classLoader: ClassLoader, task: Any): Drawable? {
        val iconField = runCatching { task.javaClass.getField("icon").get(task) as? Drawable }.getOrNull()
        if (iconField != null) return iconField

        // Trigger task loader
        loadTaskData(context, classLoader, task)
        val loadedIcon = runCatching { task.javaClass.getField("icon").get(task) as? Drawable }.getOrNull()
        if (loadedIcon != null) return loadedIcon

        val pkg = getTaskPackageName(task) ?: return null
        return runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    fun getTaskThumbnail(context: Context, classLoader: ClassLoader, task: Any): Bitmap? {
        val thumbField = runCatching { task.javaClass.getField("thumbnail").get(task) as? Bitmap }.getOrNull()
        if (thumbField != null) return thumbField

        // Trigger task loader
        loadTaskData(context, classLoader, task)
        val loadedThumb = runCatching { task.javaClass.getField("thumbnail").get(task) as? Bitmap }.getOrNull()
        if (loadedThumb != null) return loadedThumb

        // Query ActivityManagerWrapper
        return runCatching {
            val amwClass = Class.forName("com.android.systemui.shared.recents.system.ActivityManagerWrapper", false, classLoader)
            val amwInstance = amwClass.getMethod("getInstance").invoke(null)
            val key = task.javaClass.getField("key").get(task)
            val getThumbMethod = amwClass.methods.firstOrNull { it.name == "getTaskThumbnail" }
            val thumbData = getThumbMethod?.invoke(amwInstance, key)
            thumbData?.javaClass?.getField("thumbnail")?.get(thumbData) as? Bitmap
        }.getOrNull()
    }

    fun loadTaskData(context: Context, classLoader: ClassLoader, task: Any) {
        runCatching {
            val rmClass = Class.forName("com.miui.home.recents.RecentsModel", false, classLoader)
            val rmInstance = rmClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            val loader = rmClass.getMethod("getTaskLoader").invoke(rmInstance)
            loader?.javaClass?.getMethod("loadTaskData", task.javaClass)?.invoke(loader, task)
        }
    }

    /**
     * Terminates a task and removes it from the recent tasks list across all layers.
     */
    fun killTask(context: Context, classLoader: ClassLoader, task: Any) {
        val taskId = getTaskId(task)
        Log.i(TAG, "killTask: taskId=$taskId")

        // 1. Xiaomi ProcessManagerWrapper.doSwapUPClean(task)
        runCatching {
            val pmwClass = Class.forName("com.android.systemui.shared.recents.system.ProcessManagerWrapper", false, classLoader)
            val method = pmwClass.declaredMethods.firstOrNull { it.name == "doSwapUPClean" }
            method?.invoke(null, task)
            Log.d(TAG, "Killed task process via doSwapUPClean: taskId=$taskId")
        }

        // 2. Android ActivityManager.removeTask(taskId)
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.javaClass?.getMethod("removeTask", Int::class.javaPrimitiveType)?.invoke(am, taskId)
            Log.d(TAG, "Removed task via ActivityManager.removeTask: taskId=$taskId")
        }

        // 3. Xiaomi RecentsModel TaskLoader.deleteTaskData(task, false)
        runCatching {
            val rmClass = Class.forName("com.miui.home.recents.RecentsModel", false, classLoader)
            val rmInstance = rmClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            val loader = rmClass.getMethod("getTaskLoader").invoke(rmInstance)
            loader?.javaClass?.getMethod("deleteTaskData", task.javaClass, Boolean::class.javaPrimitiveType)?.invoke(loader, task, false)
            Log.d(TAG, "Deleted task data from RecentsModel: taskId=$taskId")
        }

        // 4. Android ActivityTaskManager.getService().removeTask(taskId)
        runCatching {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val service = atmClass.getMethod("getService").invoke(null)
            service?.javaClass?.getMethod("removeTask", Int::class.javaPrimitiveType)?.invoke(service, taskId)
            Log.d(TAG, "Removed task via ActivityTaskManager: taskId=$taskId")
        }
    }
}
