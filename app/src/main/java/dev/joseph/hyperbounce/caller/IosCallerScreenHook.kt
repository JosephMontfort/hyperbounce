package dev.joseph.hyperbounce.caller

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.animation.PathInterpolator
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
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
    var lastAnswerKnobScreenX: Float = 0f
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
                        (chain.thisObject)?.let { handleCallButtonVisibleHandover(it, false) }
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
                        val result = chain.proceed()
                        (chain.thisObject)?.let { handleCallButtonVisibleHandover(it, show) }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.setCallButtonVisible successfully.")
                    break
                }
            }

            // Hook startCallButtonAnimator(boolean)
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "startCallButtonAnimator" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept { chain ->
                        val show = chain.args[0] as? Boolean ?: false
                        val result = chain.proceed()
                        (chain.thisObject)?.let { handleCallButtonVisibleHandover(it, show) }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.startCallButtonAnimator successfully.")
                    break
                }
            }

            // Hook updateButtonHeight()
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "updateButtonHeight" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject)?.let { handleCallButtonVisibleHandover(it, false) }
                        result
                    }
                    Log.d(TAG, "Hooked CallButtonFragment.updateButtonHeight successfully.")
                    break
                }
            }

            // Hook updatePanelPadding()
            for (m: Method in callButtonFragmentClass.declaredMethods) {
                if (m.name == "updatePanelPadding" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject)?.let { handleCallButtonVisibleHandover(it, false) }
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
                        (view?.parent as? View)?.let { disableClippingRecursively(it) }
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

        // 6. Hook DialpadFragment & DialpadButton to prevent keypad number buttons clipping at top and bottom
        try {
            val dialpadFragmentClass = Class.forName("com.android.incallui.DialpadFragment", false, classLoader)
            for (m: Method in dialpadFragmentClass.declaredMethods) {
                if (m.name == "onInflateView" && m.parameterTypes.size == 3) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (result as? View)?.let { v ->
                            applyDialpadLayout(v)
                            v.post { applyDialpadLayout(v) }
                        }
                        result
                    }
                    Log.d(TAG, "Hooked DialpadFragment.onInflateView successfully.")
                } else if (m.name == "showDialer" || m.name == "setDialpadVisible" || m.name == "updateButtonHeight" || m.name == "setupKeypad") {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        val fragment = chain.thisObject
                        try {
                            val dialpadField = dialpadFragmentClass.getDeclaredField("mDialPad").apply { isAccessible = true }
                            val dialpadView = dialpadField.get(fragment) as? View
                            if (dialpadView != null) {
                                applyDialpadLayout(dialpadView)
                                dialpadView.post { applyDialpadLayout(dialpadView) }
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                    Log.d(TAG, "Hooked DialpadFragment.${m.name} successfully.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DialpadFragment", t)
        }

        try {
            val dialpadButtonClass = Class.forName("com.android.incallui.view.DialpadButton", false, classLoader)
            for (m: Method in dialpadButtonClass.declaredMethods) {
                if ((m.name == "updateButton" || m.name == "updateContainer") && m.parameterTypes.size == 1 && m.parameterTypes[0] == Context::class.java) {
                    module.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { applyDialpadButtonSize(it) }
                        result
                    }
                    Log.d(TAG, "Hooked DialpadButton.${m.name} successfully.")
                }
            }
            for (ctor in dialpadButtonClass.declaredConstructors) {
                if (ctor.parameterTypes.size == 2) {
                    module.hook(ctor).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { v ->
                            applyDialpadButtonSize(v)
                            v.post { applyDialpadButtonSize(v) }
                        }
                        result
                    }
                    Log.d(TAG, "Hooked DialpadButton(Context, AttributeSet) constructor successfully.")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DialpadButton", t)
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
                    val slider = activeIosCallView?.get()?.answerSlider
                    if (slider != null && slider.width > 0) {
                        val loc = IntArray(2)
                        slider.getLocationOnScreen(loc)
                        lastAnswerKnobScreenX = loc[0] + (slider.width / 2f)
                        lastAnswerKnobScreenY = loc[1] + (slider.height / 2f)
                        Log.i(TAG, "Recorded final answer knob center: X=$lastAnswerKnobScreenX, Y=$lastAnswerKnobScreenY")
                    }
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

    internal fun disableClippingRecursively(view: View?) {
        if (view == null) return
        var curr: View? = view
        while (curr != null) {
            if (curr is ViewGroup) {
                curr.clipChildren = false
                curr.clipToPadding = false
                for (i in 0 until curr.childCount) {
                    val child = curr.getChildAt(i)
                    if (child is ViewGroup) {
                        child.clipChildren = false
                        child.clipToPadding = false
                    }
                }
            }
            curr = curr.parent as? View
        }
    }

    private fun handleCallButtonVisibleHandover(fragment: Any, show: Boolean) {
        val config = IosCallerConfigHolder.config
        try {
            val rootViewField = fragment.javaClass.getDeclaredField("mRootView").apply { isAccessible = true }
            val rootView = rootViewField.get(fragment) as? View
            if (rootView != null) {
                val d = rootView.resources.displayMetrics.density
                val bottomPadding = (config.bottomOffset * d).toInt()
                rootView.setPadding(0, 0, 0, bottomPadding)
                disableClippingRecursively(rootView)
            }

            val endBtnField = fragment.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }
            val endBtn = endBtnField.get(fragment) as? View
            val audioBtnField = fragment.javaClass.getDeclaredField("mAudioButton").apply { isAccessible = true }
            val audioBtn = audioBtnField.get(fragment) as? View
            val toolBtnField = fragment.javaClass.getDeclaredField("mToolButton").apply { isAccessible = true }
            val toolBtn = toolBtnField.get(fragment) as? View

            if (endBtn != null) {
                applyCallButtonSize(endBtn)
                disableClippingRecursively(endBtn)
            }
            if (audioBtn != null) {
                applyCallButtonSize(audioBtn)
                disableClippingRecursively(audioBtn)
            }
            if (toolBtn != null) {
                applyCallButtonSize(toolBtn)
                disableClippingRecursively(toolBtn)
            }

            if (show && isAttendingTransitionActive && endBtn != null) {
                isAttendingTransitionActive = false

                endBtn.visibility = View.VISIBLE
                endBtn.alpha = 1.0f
                endBtn.scaleX = 1.0f
                endBtn.scaleY = 1.0f

                if (audioBtn != null) {
                    audioBtn.visibility = View.VISIBLE
                    audioBtn.alpha = 0f
                    audioBtn.scaleX = 1.0f
                    audioBtn.scaleY = 1.0f
                    audioBtn.animate().alpha(1f).setDuration(250).start()
                }

                if (toolBtn != null) {
                    toolBtn.visibility = View.VISIBLE
                    toolBtn.alpha = 0f
                    toolBtn.scaleX = 1.0f
                    toolBtn.scaleY = 1.0f
                    toolBtn.animate().alpha(1f).setDuration(250).start()
                }

                // 1:1 Precise Alignment to exact answer knob center (both X and Y)
                val alignButtons = {
                    val ovalView = endBtn.findViewById<View>(endBtn.resources.getIdentifier("endCall", "id", endBtn.context.packageName))
                        ?: try { endBtn.javaClass.getDeclaredField("mEndCallButton").apply { isAccessible = true }.get(endBtn) as? View } catch (_: Throwable) { null }
                        ?: endBtn

                    if (lastAnswerKnobScreenY > 0f && ovalView.height > 0) {
                        val ovalLoc = IntArray(2)
                        ovalView.getLocationOnScreen(ovalLoc)
                        // Calculate the UNTRANSLATED center of the circular button on screen
                        val currentCenterY = ovalLoc[1] + (ovalView.height / 2f) - endBtn.translationY
                        val currentCenterX = ovalLoc[0] + (ovalView.width / 2f) - endBtn.translationX
                        val deltaY = lastAnswerKnobScreenY - currentCenterY
                        val deltaX = if (lastAnswerKnobScreenX > 0f) lastAnswerKnobScreenX - currentCenterX else 0f

                        endBtn.translationY = deltaY
                        endBtn.translationX = deltaX
                        audioBtn?.translationY = deltaY
                        toolBtn?.translationY = deltaY

                        applyCallButtonSize(endBtn)
                        disableClippingRecursively(endBtn)
                        disableClippingRecursively(audioBtn)
                        disableClippingRecursively(toolBtn)
                        if (rootView != null) disableClippingRecursively(rootView)
                        Log.i(TAG, "1:1 Aligned call buttons: deltaX=$deltaX, deltaY=$deltaY (target: $lastAnswerKnobScreenX, $lastAnswerKnobScreenY, current: $currentCenterX, $currentCenterY)")
                    }
                }

                alignButtons()
                endBtn.post { alignButtons() }

                val vto = endBtn.viewTreeObserver
                if (vto.isAlive) {
                    vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        private var layoutPasses = 0
                        override fun onGlobalLayout() {
                            layoutPasses++
                            alignButtons()
                            if (layoutPasses >= 6) {
                                try {
                                    endBtn.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                } catch (_: Throwable) {}
                            }
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in handleCallButtonVisibleHandover", t)
        }
    }

    internal fun applyCallButtonSize(view: View) {
        val config = IosCallerConfigHolder.config
        val d = view.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()
        val iconScale = config.buttonSize / 68f

        disableClippingRecursively(view)

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

            // 5. Replace stock Xiaomi end call hangup icon with authentic iOS phone handset icon, perfectly matching slider
            val endIconField = try { view.javaClass.getDeclaredField("mEndCallIconButton").apply { isAccessible = true } } catch (_: Throwable) { null }
            val endIcon = endIconField?.get(view) as? View ?: view.findViewById<View>(view.resources.getIdentifier("endCallIcon", "id", view.context.packageName))
            if (endIcon is ImageView) {
                endIcon.scaleX = 1.0f
                endIcon.scaleY = 1.0f
                endIcon.scaleType = ImageView.ScaleType.CENTER
                endIcon.setPadding(0, 0, 0, 0)
                endIcon.layoutParams?.let { lp ->
                    if (lp.width != targetPx || lp.height != targetPx) {
                        lp.width = targetPx
                        lp.height = targetPx
                        endIcon.layoutParams = lp
                    }
                }
                if (endIcon.drawable !is IosEndCallIconDrawable) {
                    endIcon.setImageDrawable(IosEndCallIconDrawable(d, config.buttonSize))
                }
                if (endIcon.tag != "ios_end_icon_hooked") {
                    endIcon.tag = "ios_end_icon_hooked"
                    endIcon.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        if (endIcon.drawable !is IosEndCallIconDrawable) {
                            endIcon.setImageDrawable(IosEndCallIconDrawable(d, config.buttonSize))
                        }
                    }
                }
            } else if (endIcon != null) {
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
            disableClippingRecursively(view)
        } catch (_: Throwable) {}
    }

    internal fun applyToolsButtonSize(view: View) {
        val config = IosCallerConfigHolder.config
        val d = view.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()
        val iconScale = config.buttonSize / 68f

        disableClippingRecursively(view)

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

            view.requestLayout()
            disableClippingRecursively(view)
        } catch (_: Throwable) {}
    }

    internal fun applyDialpadButtonSize(view: View) {
        val config = IosCallerConfigHolder.config
        val d = view.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()

        disableClippingRecursively(view)

        try {
            view.layoutParams?.let { lp ->
                if (lp.height != targetPx) {
                    lp.height = targetPx
                    view.layoutParams = lp
                }
            }

            val containerField = try { view.javaClass.getDeclaredField("mContainer").apply { isAccessible = true } } catch (_: Throwable) { null }
            val container = containerField?.get(view) as? View
            if (container != null) {
                disableClippingRecursively(container)
                container.layoutParams?.let { clp ->
                    if (clp.height != targetPx) {
                        clp.height = targetPx
                        container.layoutParams = clp
                    }
                }
            }

            val ovalField = try { view.javaClass.getDeclaredField("mOvalImageButton").apply { isAccessible = true } } catch (_: Throwable) { null }
            val oval = ovalField?.get(view) as? View
            if (oval != null) {
                disableClippingRecursively(oval)
                oval.layoutParams?.let { olp ->
                    if (olp.width != targetPx || olp.height != targetPx) {
                        olp.width = targetPx
                        olp.height = targetPx
                        oval.layoutParams = olp
                    }
                }
            }

            view.requestLayout()
        } catch (_: Throwable) {}
    }

    internal fun applyDialpadLayout(rootOrDialpadView: View) {
        val config = IosCallerConfigHolder.config
        val d = rootOrDialpadView.resources.displayMetrics.density
        val targetPx = (config.buttonSize * d).toInt()

        disableClippingRecursively(rootOrDialpadView)

        try {
            val dialpadResId = rootOrDialpadView.resources.getIdentifier("dialpad", "id", rootOrDialpadView.context.packageName)
            val dialpadView = if (rootOrDialpadView.id == dialpadResId) {
                rootOrDialpadView
            } else {
                rootOrDialpadView.findViewById<View>(dialpadResId) ?: rootOrDialpadView
            }

            disableClippingRecursively(dialpadView)

            // Adjust the 4 row LinearLayouts
            val rowNames = arrayOf("dialpad_button_row_first", "dialpad_button_row_second", "dialpad_button_row_third", "dialpad_button_row_fourth")
            for (rowName in rowNames) {
                val rowId = dialpadView.resources.getIdentifier(rowName, "id", dialpadView.context.packageName)
                if (rowId != 0) {
                    dialpadView.findViewById<View>(rowId)?.let { row ->
                        disableClippingRecursively(row)
                        row.layoutParams?.let { lp ->
                            if (lp.height != targetPx) {
                                lp.height = targetPx
                                row.layoutParams = lp
                            }
                        }
                    }
                }
            }

            // Adjust all dialpad buttons
            val buttonNames = arrayOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "star", "zero", "pound")
            for (btnName in buttonNames) {
                val btnId = dialpadView.resources.getIdentifier(btnName, "id", dialpadView.context.packageName)
                if (btnId != 0) {
                    dialpadView.findViewById<View>(btnId)?.let { btn ->
                        applyDialpadButtonSize(btn)
                    }
                }
            }

            dialpadView.requestLayout()
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

/**
 * Custom Drawable that renders the authentic iOS handset icon rotated 135 degrees in pure white.
 * Exactly matches the geometry, scaling, and orientation of the knob's morphed phone icon in [IosSliderView].
 */
class IosEndCallIconDrawable(density: Float, buttonSizeDp: Float) : Drawable() {
    private val iconTargetSize = (buttonSizeDp * density) / 2f
    private val intrinsicSize = (buttonSizeDp * density).toInt()
    private val phonePath = createIosPhonePath()

    private val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(135f)
        val scale = iconTargetSize / 24f
        canvas.scale(scale, scale)
        canvas.translate(-12f, -12f)
        canvas.drawPath(phonePath, phonePaint)
        canvas.restore()
    }

    override fun getIntrinsicWidth(): Int = intrinsicSize
    override fun getIntrinsicHeight(): Int = intrinsicSize

    override fun setAlpha(alpha: Int) {
        phonePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        phonePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

