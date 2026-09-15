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
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Xposed hook for com.android.incallui that transforms Xiaomi stock caller screen into
 * an authentic iOS incoming call screen with enlarged 68dp buttons matching stock end-call dimensions,
 * fluid unmasking track swipe physics, and a continuous rotation & glide-to-center morph transition.
 */
object IosCallerScreenHook {

    private const val TAG = "HyperBounce_Caller"
    private const val TAG_IOS_VIEW = "hyperbounce_ios_incoming_call_view"

    private var activeIosCallView: WeakReference<IosIncomingCallView>? = null
    private var isMessageRejectAllowed = true
    private var isReceiverRegistered = false
    var isAttendingTransitionActive = false

    fun init(module: XposedModule, classLoader: ClassLoader) {
        val prefs = HyperBouncePrefs(module)
        if (!prefs.moduleEnabled || !prefs.callerScreenEnabled) {
            Log.d(TAG, "iOS Caller Screen feature is disabled in preferences.")
            return
        }

        IosCallerConfigHolder.updateFromPrefs(prefs.prefs)
        Log.i(TAG, "Initializing iOS Caller Screen Hook for com.android.incallui with config: ${IosCallerConfigHolder.config}...")

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
                                registerSettingsReceiver(view.context, prefs)
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

        // 3. Hook CallButtonFragment.startCallButtonAnimator -> Seamless glide handover into center End Call button
        try {
            val callButtonFragmentClass = Class.forName("com.android.incallui.callbutton.CallButtonFragment", false, classLoader)
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "startCallButtonAnimator" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept { chain ->
                        val show = chain.args[0] as? Boolean ?: false
                        val fragment = chain.thisObject
                        val config = IosCallerConfigHolder.config
                        val scale = config.buttonSize / 68f

                        if (fragment != null && show) {
                            try {
                                val rootViewField = fragment.javaClass.getDeclaredField("mRootView").apply { isAccessible = true }
                                val rootView = rootViewField.get(fragment) as? View
                                if (rootView != null) {
                                    val d = rootView.resources.displayMetrics.density
                                    val bottomPadding = (config.bottomOffset * d).toInt()
                                    rootView.setPadding(0, 0, 0, bottomPadding)
                                }
                            } catch (_: Throwable) {}
                        }

                        if (show && isAttendingTransitionActive) {
                            isAttendingTransitionActive = false
                            if (fragment != null) {
                                try {
                                    // End Call button is already rotated, red, enlarged, and resting at the center
                                    val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                    val endBtn = endBtnField.get(fragment) as? View
                                    if (endBtn != null) {
                                        endBtn.visibility = View.VISIBLE
                                        endBtn.alpha = 1.0f
                                        endBtn.scaleX = scale
                                        endBtn.scaleY = scale
                                    }

                                    // Audio button and tool button fade in smoothly around it with matching enlarged scale
                                    val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                    val audioBtn = audioBtnField.get(fragment) as? View
                                    if (audioBtn != null) {
                                        audioBtn.visibility = View.VISIBLE
                                        audioBtn.scaleX = scale
                                        audioBtn.scaleY = scale
                                        audioBtn.alpha = 0f
                                        audioBtn.animate().alpha(1f).setDuration(250).start()
                                    }

                                    val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                    val toolBtn = toolBtnField.get(fragment) as? View
                                    if (toolBtn != null) {
                                        toolBtn.visibility = View.VISIBLE
                                        toolBtn.scaleX = scale
                                        toolBtn.scaleY = scale
                                        toolBtn.alpha = 0f
                                        toolBtn.animate().alpha(1f).setDuration(250).start()
                                    }

                                    return@intercept null
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error in custom startCallButtonAnimator handover", t)
                                }
                            }
                        }

                        val result = chain.proceed()
                        if (show && fragment != null) {
                            try {
                                val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                (endBtnField.get(fragment) as? View)?.apply {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                (audioBtnField.get(fragment) as? View)?.apply {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                (toolBtnField.get(fragment) as? View)?.apply {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            } catch (_: Throwable) {}
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.startCallButtonAnimator successfully.")
                    break
                }
            }

            // Hook CallButtonFragment.updatePanelPadding to enforce custom bottom offset
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "updatePanelPadding" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            val config = IosCallerConfigHolder.config
                            val rootViewField = fragment.javaClass.getDeclaredField("mRootView").apply { isAccessible = true }
                            val rootView = rootViewField.get(fragment) as? View
                            if (rootView != null) {
                                val d = rootView.resources.displayMetrics.density
                                val bottomPadding = (config.bottomOffset * d).toInt()
                                rootView.setPadding(0, 0, 0, bottomPadding)
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.updatePanelPadding successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook CallButtonFragment", t)
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

            // 1. Answer Action (Bottom Slider - 68dp button)
            onAnswer = {
                try {
                    isAttendingTransitionActive = true
                    val onAnswerMethod = fragment.javaClass.getMethod("onAnswer", Int::class.javaPrimitiveType)
                    onAnswerMethod.invoke(fragment, 0)
                    Log.i(TAG, "Dispatched onAnswer(0) to AnswerFragment with center glide active.")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to invoke onAnswer", t)
                }
            }

            // 2. Reject Action (Top Slider - 68dp button)
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
        Log.i(TAG, "Attached IosIncomingCallView with enlarged 68dp sliders to AnswerFragment.")
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
                textSize = 34f
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
                textSize = 16.5f
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

    internal fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun registerSettingsReceiver(context: Context, prefs: HyperBouncePrefs) {
        if (isReceiverRegistered) return
        try {
            val filter = android.content.IntentFilter("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED")
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                    try {
                        IosCallerConfigHolder.updateFromPrefs(prefs.prefs)
                        Log.d(TAG, "Updated IosCallerConfig from broadcast: ${IosCallerConfigHolder.config}")
                    } catch (_: Throwable) {}
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register settings receiver", t)
        }
    }
}
