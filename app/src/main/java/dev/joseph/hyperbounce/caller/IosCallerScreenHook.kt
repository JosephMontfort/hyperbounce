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
    var lastAnswerKnobScreenY: Float = 0f

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

        // 3. Hook CallButtonFragment -> Seamless glide handover into center End Call button & custom sizing
        try {
            val callButtonFragmentClass = Class.forName("com.android.incallui.callbutton.CallButtonFragment", false, classLoader)

            // Hook onInflateView to immediately prepare bottom row buttons
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "onInflateView" && m.parameterTypes.size == 3) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        val fragment = chain.thisObject
                        if (fragment != null) {
                            try {
                                val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                (endBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                (audioBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                (toolBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                            } catch (_: Throwable) {}
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.onInflateView successfully.")
                    break
                }
            }

            // Hook setCallButtonVisible(boolean) -> the exact method called when call is answered
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "setCallButtonVisible" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept { chain ->
                        val show = chain.args[0] as? Boolean ?: false
                        val fragment = chain.thisObject
                        val config = IosCallerConfigHolder.config

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
                                    val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                    val endBtn = endBtnField.get(fragment) as? View
                                    val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                    val audioBtn = audioBtnField.get(fragment) as? View
                                    val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                    val toolBtn = toolBtnField.get(fragment) as? View

                                    if (endBtn != null) applyCallButtonSize(endBtn)
                                    if (audioBtn != null) applyCallButtonSize(audioBtn)
                                    if (toolBtn != null) applyCallButtonSize(toolBtn)

                                    // Align call button row vertically to match the exact screen position of the answer knob
                                    val alignButtons = {
                                        if (endBtn != null && lastAnswerKnobScreenY > 0f && endBtn.height > 0) {
                                            val endLoc = IntArray(2)
                                            endBtn.getLocationOnScreen(endLoc)
                                            val currentEndCenterY = endLoc[1] + (endBtn.height / 2f) - endBtn.translationY
                                            val deltaY = lastAnswerKnobScreenY - currentEndCenterY
                                            endBtn.translationY = deltaY
                                            audioBtn?.translationY = deltaY
                                            toolBtn?.translationY = deltaY
                                            Log.i(TAG, "Aligned call buttons with deltaY=$deltaY (lastKnobCenterY=$lastAnswerKnobScreenY)")
                                        }
                                    }

                                    if (endBtn != null && endBtn.height > 0) {
                                        alignButtons()
                                    } else {
                                        endBtn?.post { alignButtons() }
                                    }

                                    if (endBtn != null) {
                                        endBtn.visibility = View.VISIBLE
                                        endBtn.alpha = 1.0f
                                        endBtn.scaleX = 1.0f
                                        endBtn.scaleY = 1.0f
                                    }

                                    if (audioBtn != null) {
                                        audioBtn.visibility = View.VISIBLE
                                        audioBtn.scaleX = 1.0f
                                        audioBtn.scaleY = 1.0f
                                        audioBtn.alpha = 0f
                                        audioBtn.animate().alpha(1f).setDuration(250).start()
                                    }

                                    if (toolBtn != null) {
                                        toolBtn.visibility = View.VISIBLE
                                        toolBtn.scaleX = 1.0f
                                        toolBtn.scaleY = 1.0f
                                        toolBtn.alpha = 0f
                                        toolBtn.animate().alpha(1f).setDuration(250).start()
                                    }

                                    return@intercept null
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error in custom setCallButtonVisible handover", t)
                                }
                            }
                        }

                        val result = chain.proceed()
                        if (show && fragment != null) {
                            try {
                                val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                (endBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                (audioBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                (toolBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                            } catch (_: Throwable) {}
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.setCallButtonVisible successfully.")
                    break
                }
            }

            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "startCallButtonAnimator" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept { chain ->
                        val show = chain.args[0] as? Boolean ?: false
                        val fragment = chain.thisObject
                        val config = IosCallerConfigHolder.config

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
                                    val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                                    val endBtn = endBtnField.get(fragment) as? View
                                    val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                    val audioBtn = audioBtnField.get(fragment) as? View
                                    val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                    val toolBtn = toolBtnField.get(fragment) as? View

                                    if (endBtn != null) applyCallButtonSize(endBtn)
                                    if (audioBtn != null) applyCallButtonSize(audioBtn)
                                    if (toolBtn != null) applyCallButtonSize(toolBtn)

                                    // Align call button row vertically to match the exact screen position of the answer knob
                                    val alignButtons = {
                                        if (endBtn != null && lastAnswerKnobScreenY > 0f && endBtn.height > 0) {
                                            val endLoc = IntArray(2)
                                            endBtn.getLocationOnScreen(endLoc)
                                            val currentEndCenterY = endLoc[1] + (endBtn.height / 2f) - endBtn.translationY
                                            val deltaY = lastAnswerKnobScreenY - currentEndCenterY
                                            endBtn.translationY = deltaY
                                            audioBtn?.translationY = deltaY
                                            toolBtn?.translationY = deltaY
                                            Log.i(TAG, "Aligned call buttons with deltaY=$deltaY (lastKnobCenterY=$lastAnswerKnobScreenY)")
                                        }
                                    }

                                    if (endBtn != null && endBtn.height > 0) {
                                        alignButtons()
                                    } else {
                                        endBtn?.post { alignButtons() }
                                    }

                                    if (endBtn != null) {
                                        endBtn.visibility = View.VISIBLE
                                        endBtn.alpha = 1.0f
                                        endBtn.scaleX = 1.0f
                                        endBtn.scaleY = 1.0f
                                    }

                                    // Audio button and tool button fade in smoothly around it with matching enlarged scale
                                    if (audioBtn != null) {
                                        audioBtn.visibility = View.VISIBLE
                                        audioBtn.scaleX = 1.0f
                                        audioBtn.scaleY = 1.0f
                                        audioBtn.alpha = 0f
                                        audioBtn.animate().alpha(1f).setDuration(250).start()
                                    }

                                    if (toolBtn != null) {
                                        toolBtn.visibility = View.VISIBLE
                                        toolBtn.scaleX = 1.0f
                                        toolBtn.scaleY = 1.0f
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
                                (endBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                                (audioBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                                val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                                (toolBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                            } catch (_: Throwable) {}
                        }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.startCallButtonAnimator successfully.")
                    break
                }
            }

            // Hook CallButtonFragment.updateButtonHeight to enforce custom button sizing on relayout
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "updateButtonHeight" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val fragment = chain.thisObject
                            val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
                            (endBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                            val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
                            (audioBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                            val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
                            (toolBtnField.get(fragment) as? View)?.let { applyCallButtonSize(it) }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.updateButtonHeight successfully.")
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

        // 4. Hook CallOvalImageButton.onMeasure to ensure EVERY oval button in InCallUI measures at exact custom size
        try {
            val callOvalImageButtonClass = Class.forName("miuix.miuixbasewidget.widget.CallOvalImageButton", false, classLoader)
            for (m: Method in callOvalImageButtonClass.declaredMethods) {
                if (m.name == "onMeasure" && m.parameterTypes.size == 2 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                    module.hook(m).intercept { chain ->
                        val view = chain.thisObject as? View
                        val config = IosCallerConfigHolder.config
                        val d = view?.resources?.displayMetrics?.density ?: 3.0f
                        val targetPx = (config.buttonSize * d).toInt()
                        val exactSpec = View.MeasureSpec.makeMeasureSpec(targetPx, View.MeasureSpec.EXACTLY)
                        chain.args[0] = exactSpec
                        chain.args[1] = exactSpec
                        chain.proceed()
                    }
                    Log.d(TAG, "Hooked CallOvalImageButton.onMeasure successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook CallOvalImageButton.onMeasure", t)
        }

        // 5. Hook CallButton & ToolsButton to uniformly match enlarged iOS button & icon sizes
        try {
            val callButtonClass = Class.forName("com.android.incallui.callbutton.CallButton", false, classLoader)
            for (m: Method in callButtonClass.declaredMethods) {
                if (m.name == "init" && m.parameterTypes.size == 2) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { applyCallButtonSize(it) }
                        result
                    }
                    Log.d(TAG, "Hooked CallButton.init successfully.")
                } else if (m.name == "updateButton" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Context::class.java) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { applyCallButtonSize(it) }
                        result
                    }
                    Log.d(TAG, "Hooked CallButton.updateButton successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook CallButton", t)
        }

        try {
            val toolsButtonClass = Class.forName("com.android.incallui.calltools.ToolsButton", false, classLoader)
            for (m: Method in toolsButtonClass.declaredMethods) {
                if (m.name == "updateButton" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Context::class.java) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { applyToolsButtonSize(it) }
                        result
                    }
                    Log.d(TAG, "Hooked ToolsButton.updateButton successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ToolsButton", t)
        }

        try {
            val toolsButtonAdapterClass = Class.forName("com.android.incallui.calltools.ToolsButtonAdapter", false, classLoader)
            for (m: Method in toolsButtonAdapterClass.declaredMethods) {
                if (m.name == "getView" && m.parameterTypes.size == 3) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (result as? View)?.let { applyToolsButtonSize(it) }
                        result
                    }
                    Log.d(TAG, "Hooked ToolsButtonAdapter.getView successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ToolsButtonAdapter", t)
        }

        // 5. Hook Resources dimensions for call_oval_item_size
        try {
            val rDimenClass = Class.forName("com.android.incallui.R\$dimen", false, classLoader)
            val callOvalItemSizeResId = try { rDimenClass.getField("call_oval_item_size").getInt(null) } catch (_: Throwable) { 0 }
            if (callOvalItemSizeResId != 0) {
                for (m: Method in android.content.res.Resources::class.java.declaredMethods) {
                    if (m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                        if (m.name == "getDimensionPixelSize" || m.name == "getDimensionPixelOffset") {
                            module.hook(m).intercept { chain ->
                                val id = chain.args[0] as? Int ?: 0
                                if (id == callOvalItemSizeResId) {
                                    val res = chain.thisObject as? android.content.res.Resources
                                    val d = res?.displayMetrics?.density ?: 3.0f
                                    return@intercept (IosCallerConfigHolder.config.buttonSize * d).toInt()
                                }
                                chain.proceed()
                            }
                        }
                    }
                }
                Log.d(TAG, "Hooked Resources dimension methods for call_oval_item_size ($callOvalItemSizeResId)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook Resources dimensions", t)
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

    internal fun applyCallButtonSize(view: View) {
        val config = IosCallerConfigHolder.config
        val d = view.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()
        val iconScale = config.buttonSize / 68f

        try {
            // Resize any immediate child (e.g. child 0 FrameLayout containing the button)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    child?.layoutParams?.let { lp ->
                        if (lp.width != targetPx || lp.height != targetPx) {
                            lp.width = targetPx
                            lp.height = targetPx
                            child.layoutParams = lp
                        }
                    }
                }
            }

            // 1. Check mHyperView (audioButton, toolButton)
            val hyperViewField = try { view.javaClass.getDeclaredField("mHyperView").apply { isAccessible = true } } catch (_: Throwable) { null }
            val hyperView = hyperViewField?.get(view) as? View
            hyperView?.layoutParams?.let { lp ->
                if (lp.width != targetPx || lp.height != targetPx) {
                    lp.width = targetPx
                    lp.height = targetPx
                    hyperView.layoutParams = lp
                }
            }

            // 2. Check mOvalImageButton (audioButton, toolButton)
            val ovalBtnField = try { view.javaClass.getDeclaredField("mOvalImageButton").apply { isAccessible = true } } catch (_: Throwable) { null }
            val ovalBtn = ovalBtnField?.get(view) as? View
            ovalBtn?.layoutParams?.let { lp ->
                if (lp.width != targetPx || lp.height != targetPx) {
                    lp.width = targetPx
                    lp.height = targetPx
                    ovalBtn.layoutParams = lp
                }
            }

            // 3. Check mEndCallButton (endButton)
            val endBtnField = try { view.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true } } catch (_: Throwable) { null }
            val endBtn = endBtnField?.get(view) as? View
            endBtn?.layoutParams?.let { lp ->
                if (lp.width != targetPx || lp.height != targetPx) {
                    lp.width = targetPx
                    lp.height = targetPx
                    endBtn.layoutParams = lp
                }
            }

            // 4. Also search by ID for endCall and ovalButton if not found via fields
            view.findViewById<View>(view.resources.getIdentifier("endCall", "id", view.context.packageName))?.let { btn ->
                btn.layoutParams?.let { lp ->
                    if (lp.width != targetPx || lp.height != targetPx) {
                        lp.width = targetPx
                        lp.height = targetPx
                        btn.layoutParams = lp
                    }
                }
            }
            view.findViewById<View>(view.resources.getIdentifier("ovalButton", "id", view.context.packageName))?.let { btn ->
                btn.layoutParams?.let { lp ->
                    if (lp.width != targetPx || lp.height != targetPx) {
                        lp.width = targetPx
                        lp.height = targetPx
                        btn.layoutParams = lp
                    }
                }
            }

            // 5. Scale icons inside CallButton
            val endIconField = try { view.javaClass.getDeclaredField("mEndCallIconButton").apply { isAccessible = true } } catch (_: Throwable) { null }
            val endIcon = endIconField?.get(view) as? View ?: view.findViewById<View>(view.resources.getIdentifier("endCallIcon", "id", view.context.packageName))
            if (endIcon != null) {
                endIcon.scaleX = iconScale
                endIcon.scaleY = iconScale
            }

            val ovalIconField = try { view.javaClass.getDeclaredField("mOvalIconView").apply { isAccessible = true } } catch (_: Throwable) { null }
            val ovalIcon = ovalIconField?.get(view) as? View ?: view.findViewById<View>(view.resources.getIdentifier("ovalIcon", "id", view.context.packageName))
            if (ovalIcon != null) {
                ovalIcon.scaleX = iconScale
                ovalIcon.scaleY = iconScale
            }

            view.requestLayout()
        } catch (_: Throwable) {}
    }

    internal fun applyToolsButtonSize(view: View) {
        val config = IosCallerConfigHolder.config
        val d = view.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()
        val iconScale = config.buttonSize / 68f

        try {
            if (view is ViewGroup && view.childCount > 0) {
                val frameLayout = view.getChildAt(0)
                frameLayout?.layoutParams?.let { lp ->
                    if (lp.width != targetPx || lp.height != targetPx) {
                        lp.width = targetPx
                        lp.height = targetPx
                        frameLayout.layoutParams = lp
                    }
                }
            }

            val ovalBtnField = view.javaClass.getDeclaredField("mOvalImageButton").apply { isAccessible = true }
            val ovalBtn = ovalBtnField.get(view) as? View
            ovalBtn?.layoutParams?.let { lp ->
                if (lp.width != targetPx || lp.height != targetPx) {
                    lp.width = targetPx
                    lp.height = targetPx
                    ovalBtn.layoutParams = lp
                }
            }

            val ovalIconField = try { view.javaClass.getDeclaredField("mOvalIconView").apply { isAccessible = true } } catch (_: Throwable) { null }
            val ovalIcon = ovalIconField?.get(view) as? View
            if (ovalIcon != null) {
                ovalIcon.scaleX = iconScale
                ovalIcon.scaleY = iconScale
            }
        } catch (_: Throwable) {}
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
