package dev.joseph.hyperbounce

import android.util.Log
import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Keypad"

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (e: NoSuchMethodException) { current = current.superclass }
        }
        throw NoSuchMethodException(name)
    }

    fun apply(module: XposedModule, classLoader: ClassLoader) {
        try {
            val pinViewClass = Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader)

            // Inject the Compose Fullscreen Overlay on attach
            val onAttachedMethod = findMethod(pinViewClass, "onAttachedToWindow")
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    view.post {
                        LiquidKeypadHost.ensure(view)
                    }
                    return result
                }
            })

            // Route all raw touches to Compose for Liquid Jelly Physics processing
            val dispatchTouchMethod = findMethod(pinViewClass, "dispatchTouchEvent", MotionEvent::class.java)
            module.hook(dispatchTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    
                    val handledByCompose = LiquidKeypadHost.forwardTouchEvent(view, event)
                    
                    // If Compose consumes the jelly gesture/click, swallow it natively
                    if (handledByCompose) return true 
                    
                    return chain.proceed()
                }
            })

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook MiuiKeyguardPINView", e)
        }
    }
}
