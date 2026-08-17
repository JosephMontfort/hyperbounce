package dev.joseph.hyperbounce

import dev.joseph.hyperbounce.nav.InstagramHook
import dev.joseph.hyperbounce.nav.WhatsAppHook
import dev.joseph.hyperbounce.nav.YouTubeHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)
        
        when {
            param.packageName.contains("instagram") ->
                InstagramHook(this, param).onPackageReady()

            param.packageName == "com.whatsapp" ->
                WhatsAppHook(this, param).onPackageReady()

            param.packageName == "com.google.android.youtube" ->
                YouTubeHook(this, param).onPackageReady()
        }
    }
}
