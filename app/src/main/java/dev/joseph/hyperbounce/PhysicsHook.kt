package dev.joseph.hyperbounce

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) : XposedModule(base, param) {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        when {
            param.packageName.contains("instagram") ->
                InstagramHook(this, param).onPackageLoaded()

            param.packageName == "com.whatsapp" ->
                WhatsAppHook(this, param).onPackageLoaded()

            param.packageName == "com.google.android.youtube" ->
                YouTubeHook(this, param).onPackageLoaded()
        }
    }
}
