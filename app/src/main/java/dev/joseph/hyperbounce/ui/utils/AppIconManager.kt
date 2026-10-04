package dev.joseph.hyperbounce.ui.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.joseph.hyperbounce.ui.state.AppIconStyle

object AppIconManager {
    private const val TAG = "AppIconManager"

    fun applyIconStyle(context: Context, style: AppIconStyle) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName

            val monetAlias = ComponentName(pkg, "$pkg.ui.MainActivityMonet")
            val flatAlias = ComponentName(pkg, "$pkg.ui.MainActivityFlat")

            val (enableTarget, disableTarget) = when (style) {
                AppIconStyle.Monet -> monetAlias to flatAlias
                AppIconStyle.Flat -> flatAlias to monetAlias
            }

            pm.setComponentEnabledSetting(
                enableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            pm.setComponentEnabledSetting(
                disableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            Log.i(TAG, "Applied icon style: ${style.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to apply icon style: ${t.message}", t)
        }
    }
}
