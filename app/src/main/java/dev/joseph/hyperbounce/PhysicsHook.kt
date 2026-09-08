package dev.joseph.hyperbounce

import android.util.Log
import dev.joseph.hyperbounce.nav.InstagramHook
import dev.joseph.hyperbounce.nav.WhatsAppHook
import dev.joseph.hyperbounce.nav.YouTubeHook
import dev.joseph.hyperbounce.safemode.SafeModeHandler
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        try {
            val prefs = try {
                getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
            } catch (t: Throwable) {
                null
            }

            // 1. Safe Mode Check (Hyperceiler Architecture)
            if (SafeModeHandler.isSafeMode(param.packageName, prefs)) {
                Log.w("HyperBounce", "[SAFE MODE ACTIVE] Skipping all hooks for ${param.packageName}")
                return
            }

            // 2. Rapid PID restart check (Engages Safe Mode if PID changes 3 times within 60s)
            if (SafeModeHandler.checkAndTrackPidRestart(param.packageName)) {
                Log.e("HyperBounce", "[SAFE MODE TRIGGERED] Rapid restart detected for ${param.packageName}! Skipping hooks.")
                return
            }

            // 3. Install Crash Watchdog (Auto-triggers Safe Mode on uncaught exceptions)
            SafeModeHandler.installCrashWatchdog(param.packageName)

            when {
                param.packageName.contains("instagram") ->
                    InstagramHook(this, param).onPackageReady()

                param.packageName == "com.whatsapp" ->
                    WhatsAppHook(this, param).onPackageReady()

                param.packageName == "com.google.android.youtube" ->
                    YouTubeHook(this, param).onPackageReady()

                param.packageName == "com.android.systemui" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                            dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                                dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                                dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder.updateFromPrefs(prefs)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in SystemUI", t)
                        }
                    }
                    dev.joseph.hyperbounce.lockscreen.LockScreenSheetHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.lockscreen.KeypadPhysicsHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.controlcenter.ControlCenterHook.initSystemUIHooks(this, param.classLoader)
                    dev.joseph.hyperbounce.notification.NotificationSheetHook.init(this, param.classLoader)
                }

                param.packageName == "miui.systemui.plugin" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in Control Center Plugin", t)
                        }
                    }
                    dev.joseph.hyperbounce.controlcenter.ControlCenterHook.initPluginHooks(this, param.classLoader)
                }

                param.packageName == "com.miui.home" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in MIUI Home", t)
                        }
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
