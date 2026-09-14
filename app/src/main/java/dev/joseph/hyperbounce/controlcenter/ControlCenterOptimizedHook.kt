package dev.joseph.hyperbounce.controlcenter

import android.view.View
import com.github.kyuubiran.ezxhelper.misc.XposedModule

/**
 * Universal compatibility alias delegating to ControlCenterHook.
 * All optimizations are now wired directly and universally into ControlCenterHook.
 */
object ControlCenterOptimizedHook {
    val sMediaPlayerViewHolder: java.lang.ref.WeakReference<Any>?
        get() = ControlCenterHook.sMediaPlayerViewHolder

    var sLastMediaPlayerMetaData: Any?
        get() = ControlCenterHook.sLastMediaPlayerMetaData
        set(value) { ControlCenterHook.sLastMediaPlayerMetaData = value }

    fun initSystemUIHooks(module: XposedModule, classLoader: ClassLoader?) {
        ControlCenterHook.initSystemUIHooks(module, classLoader)
    }

    fun initPluginHooks(module: XposedModule, classLoader: ClassLoader?) {
        ControlCenterHook.initPluginHooks(module, classLoader)
    }

    fun applyMediaPlayerLayout(holder: Any) {
        ControlCenterHook.applyMediaPlayerLayout(holder)
    }

    fun setCircularTileLabelVisibility(itemView: View, visible: Boolean = true) {
        ControlCenterHook.setCircularTileLabelVisibility(itemView, visible)
    }
}
