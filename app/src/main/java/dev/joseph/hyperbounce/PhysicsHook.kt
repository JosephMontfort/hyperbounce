package dev.joseph.hyperbounce

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Router entry point for the HyperBounce Xposed/LSPosed module.
 *
 * This class only decides *which* app was loaded and hands off to that app's
 * dedicated handler. All hooking logic, overlay injection, and view-shifting
 * behavior lives in [InstagramHook], [WhatsAppHook], and [YouTubeHook].
 *
 * Match semantics are preserved exactly as they existed in each original branch:
 * Instagram used a `contains` check (covers com.instagram.android and related
 * variants), while WhatsApp and YouTube used exact package-name equality.
 */
class PhysicsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        when {
            lpparam.packageName.contains("instagram") ->
                InstagramHook().handleLoadPackage(lpparam)

            lpparam.packageName == "com.whatsapp" ->
                WhatsAppHook().handleLoadPackage(lpparam)

            lpparam.packageName == "com.google.android.youtube" ->
                YouTubeHook().handleLoadPackage(lpparam)
        }
    }
}
