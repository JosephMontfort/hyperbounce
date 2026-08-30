package dev.joseph.hyperbounce

import android.util.Log
import dev.joseph.hyperbounce.nav.InstagramHook
import dev.joseph.hyperbounce.nav.WhatsAppHook
import dev.joseph.hyperbounce.nav.YouTubeHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        // Each branch is its own try-catch, not one around the whole `when`: an exception
        // anywhere in here (including HyperBouncePrefs' remote-preferences read, which
        // Phase 2 added to every one of these hooks' construction/onPackageReady) must
        // never propagate out of this function uncaught — this runs inside the *hooked*
        // app's own process (Instagram/WhatsApp/YouTube), so an uncaught exception here
        // risks crashing that app, not just this module.
        try {
            when {
                param.packageName.contains("instagram") ->
                    InstagramHook(this, param).onPackageReady()

                param.packageName == "com.whatsapp" ->
                    WhatsAppHook(this, param).onPackageReady()

                param.packageName == "com.google.android.youtube" ->
                    YouTubeHook(this, param).onPackageReady()

                param.packageName == "com.android.systemui" -> {
                    try {
                        val prefs = getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
                        dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                            dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                        }
                    } catch (t: Throwable) {
                        Log.e("HyperBounce", "Failed to bind remote preferences in SystemUI", t)
                    }
                    dev.joseph.hyperbounce.lockscreen.LockScreenSheetHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.lockscreen.KeypadPhysicsHook.init(this, param.classLoader)
                }

                param.packageName == "com.miui.home" -> {
                    try {
                        val prefs = getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
                        dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                            dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                        }
                    } catch (t: Throwable) {
                        Log.e("HyperBounce", "Failed to bind remote preferences in MIUI Home", t)
                    }
                    dev.joseph.hyperbounce.launcher.ScreenUnlockHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.launcher.AppTransitionHook.init(this, param.classLoader)
                }
            }
        } catch (t: Throwable) {
            Log.e("HyperBounce", "PhysicsHook failed for ${param.packageName}, leaving stock UI untouched", t)
        }
    }
}
