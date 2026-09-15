package dev.joseph.hyperbounce.caller

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Xposed hook for com.android.incallui that transforms Xiaomi stock caller screen into
 * an authentic iOS incoming call screen with dual horizontal sliders, morphing attend transition,
 * and quick message reply.
 */
object IosCallerScreenHook {

    private const val TAG = "HyperBounce_Caller"
    private const val TAG_IOS_VIEW = "hyperbounce_ios_incoming_call_view"

    private var activeIosCallView: WeakReference<IosIncomingCallView>? = null
    private var isMessageRejectAllowed = true

    fun init(module: XposedModule, classLoader: ClassLoader) {
        val prefs = HyperBouncePrefs(module)
        if (!prefs.moduleEnabled || !prefs.callerScreenEnabled) {
            Log.d(TAG, "iOS Caller Screen feature is disabled in preferences.")
            return
        }

        Log.i(TAG, "Initializing iOS Caller Screen Hook for com.android.incallui...")

        // 1. Hook AnswerFragment.onViewCreated -> Replace Xiaomi vertical slide with iOS dual sliders
        try {
            val answerFragmentClass = Class.forName("com.android.incallui.AnswerFragment", false, classLoader)
            for (m: Method in answerFragmentClass.declaredMethods) {
                if (m.name == "onViewCreated" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == View::class.java &&
                    m.parameterTypes[1] == Bundle::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val view = chain.args[0] as? ViewGroup
                            val fragment = chain.thisObject
                            if (view != null && fragment != null) {
                                setupIosAnswerUi(view, fragment)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in AnswerFragment.onViewCreated hook", t)
                        }
                        result
                    }
                    Log.d(TAG, "Hooked AnswerFragment.onViewCreated successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AnswerFragment.onViewCreated", t)
        }

        // 2. Hook CallCardFragment.onViewCreated -> Style iOS typography and hide stock message button
        try {
            val callCardFragmentClass = Class.forName("com.android.incallui.CallCardFragment", false, classLoader)
            for (m: Method in callCardFragmentClass.declaredMethods) {
                if (m.name == "onViewCreated" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == View::class.java &&
                    m.parameterTypes[1] == Bundle::class.java
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val view = chain.args[0] as? View
                            if (view != null) {
                                setupIosCallCardUi(view)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in CallCardFragment.onViewCreated hook", t)
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallCardFragment.onViewCreated successfully.")
                    break
                }
            }

            // Hook CallCardFragment.setCanMessageReject
            for (m: Method in callCardFragmentClass.declaredMethods) {
                if (m.name == "setCanMessageReject" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val canReject = chain.args[0] as? Boolean ?: true
                            isMessageRejectAllowed = canReject
                            activeIosCallView?.get()?.setMessageButtonVisible(canReject)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in CallCardFragment.setCanMessageReject hook", t)
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallCardFragment.setCanMessageReject successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook CallCardFragment", t)
        }
    }

    private fun setupIosAnswerUi(container: ViewGroup, fragment: Any) {
        val res = container.resources

        // Hide Xiaomi's stock vertical sliding layout
        val slidingLayoutId = res.getIdentifier("sliding_layout", "id", "com.android.incallui")
        if (slidingLayoutId != 0) {
            val stockSliding = container.findViewById<View>(slidingLayoutId)
            stockSliding?.visibility = View.INVISIBLE
        }

        // Remove old iOS view if already attached
        val oldView = container.findViewWithTag<View>(TAG_IOS_VIEW)
        if (oldView != null) {
            container.removeView(oldView)
        }

        // Create and attach iOS Dual-Slider Incoming Call View
        val iosView = IosIncomingCallView(container.context).apply {
            tag = TAG_IOS_VIEW
            setMessageButtonVisible(isMessageRejectAllowed)

            // 1. Answer Action (Slider 2)
            onAnswer = {
                try {
                    val onAnswerMethod = fragment.javaClass.getMethod("onAnswer", Int::class.javaPrimitiveType)
                    onAnswerMethod.invoke(fragment, 0)
                    Log.i(TAG, "Dispatched onAnswer(0) to AnswerFragment.")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to invoke onAnswer", t)
                }
            }

            // 2. Reject Action (Slider 1)
            onReject = {
                try {
                    val onRejectMethod = fragment.javaClass.getMethod("onReject")
                    onRejectMethod.invoke(fragment)
                    Log.i(TAG, "Dispatched onReject() to AnswerFragment.")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to invoke onReject", t)
                }
            }

            // 3. Message Quick Reply Action
            onMessage = {
                triggerStockMessageReject(container)
            }
        }

        activeIosCallView = WeakReference(iosView)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(iosView, params)
        Log.i(TAG, "Attached IosIncomingCallView to AnswerFragment container.")
    }

    private fun setupIosCallCardUi(view: View) {
        val res = view.resources

        // 1. Hide stock middle message button (to prevent duplicate button clutter)
        val msgRejectId = res.getIdentifier("callCardMessageReject", "id", "com.android.incallui")
        if (msgRejectId != 0) {
            val stockMsg = view.findViewById<View>(msgRejectId)
            stockMsg?.alpha = 0f
            val lp = stockMsg?.layoutParams
            if (lp != null) {
                lp.height = 0
                stockMsg.layoutParams = lp
            }
        }

        // 2. Style Caller Name with iOS Bold Typography
        val nameId = res.getIdentifier("name", "id", "com.android.incallui")
        if (nameId != 0) {
            val nameView = view.findViewById<TextView>(nameId)
            nameView?.apply {
                textSize = 32f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setShadowLayer(10f, 0f, 2f, Color.argb(90, 0, 0, 0))
            }
        }

        // 3. Style Number / Subtext with iOS Translucent Typography
        val numberId = res.getIdentifier("number", "id", "com.android.incallui")
        if (numberId != 0) {
            val numView = view.findViewById<TextView>(numberId)
            numView?.apply {
                textSize = 16f
                setTextColor(Color.argb(200, 255, 255, 255))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setShadowLayer(6f, 0f, 1f, Color.argb(70, 0, 0, 0))
            }
        }
    }

    private fun triggerStockMessageReject(view: View) {
        try {
            val activity = findActivity(view.context) ?: return
            val msgRejectId = view.resources.getIdentifier("callCardMessageReject", "id", "com.android.incallui")
            if (msgRejectId != 0) {
                val stockMsg = activity.findViewById<View>(msgRejectId)
                stockMsg?.performClick()
                Log.i(TAG, "Triggered stock callCardMessageReject click.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to trigger stock message reject", t)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
