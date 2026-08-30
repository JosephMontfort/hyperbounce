package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object AppTransitionHook {
    private const val TAG = "HyperBounce_Transition"

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        try {
            val companionClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider\$Companion", false, classLoader)
            val animTypeEnum = Class.forName("com.miui.home.recents.util.RectFSpringAnim\$AnimType", false, classLoader)
            val springParamClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider\$SpringParam", false, classLoader)

            val springParamConst: Constructor<*> = springParamClass.getDeclaredConstructor(
                String::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            try {
                sKeyField = springParamClass.getDeclaredField("key").apply { isAccessible = true }
                sImpField = springParamClass.getDeclaredField("impulse").apply { isAccessible = true }
                sDField = springParamClass.getDeclaredField("damping").apply { isAccessible = true }
                sRField = springParamClass.getDeclaredField("response").apply { isAccessible = true }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to reflect SpringParam fields", t)
            }

            val generateMethod: Method = companionClass.getDeclaredMethod(
                "generateSpringBundlesByAnimType",
                animTypeEnum,
                RectF::class.java,
                RectF::class.java
            )

            module.hook(generateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed() as? Array<Any?> ?: return chain.proceed()
                    val cfg = LauncherConfigHolder.transitionConfig
                    if (!cfg.enabled) {
                        return result
                    }

                    try {
                        val posDamping = cfg.posDamping
                        val posResponse = cfg.posResponse
                        val scaleDamping = cfg.scaleDamping
                        val scaleResponse = cfg.scaleResponse
                        val cornerDamping = cfg.cornerDamping
                        val cornerResponse = cfg.cornerResponse
                        val alphaDamping = cfg.alphaDamping
                        val alphaResponse = cfg.alphaResponse

                        for (i in result.indices) {
                            val param = result[i] ?: continue
                            val key = sKeyField?.get(param) as? String
                            val impulse = (sImpField?.get(param) as? Float) ?: 0.0f

                            val d: Float
                            val r: Float

                            when (key) {
                                "centerX", "centerY" -> {
                                    d = posDamping
                                    r = posResponse
                                }
                                "Width", "Height", "Ratio" -> {
                                    d = scaleDamping
                                    r = scaleResponse
                                }
                                "RadiusRatio", "Radius" -> {
                                    d = cornerDamping
                                    r = cornerResponse
                                }
                                "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> {
                                    d = alphaDamping
                                    r = alphaResponse
                                }
                                else -> {
                                    d = (sDField?.get(param) as? Float) ?: 0.9f
                                    r = (sRField?.get(param) as? Float) ?: 0.3f
                                }
                            }

                            result[i] = springParamConst.newInstance(key, d, r, impulse)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error remapping spring parameters", t)
                    }

                    return result
                }
            })

            Log.i(TAG, "Hooked generateSpringBundlesByAnimType successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook SpringParamsProvider", t)
        }
    }
}
