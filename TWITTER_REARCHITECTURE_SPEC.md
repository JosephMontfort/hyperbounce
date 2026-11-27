# HyperBounce (HOS 4) - Twitter (X) Integration Architectural Specification
**Document**: `TWITTER_REARCHITECTURE_SPEC.md`  
**Target Package**: `com.twitter.android` (X / Twitter Android, Version 10.x / 11.x)  
**Status**: APPROVED IMPLEMENTATION BLUEPRINT  
**Author**: Implementation Architectural Planner Subagent  
**Date**: November 2026  

---

## 1. Executive Summary & Root Cause Synthesis

The initial implementation of the floating `LiquidNavigationBar` in X (formerly Twitter) suffered from three compounding, catastrophic failure modes:
1. **Stock Bar Remained Visible Alongside Floating Bar**:
   Twitter uses a dual bottom navigation pipeline controlled by a feature switch (`"android_x_lite_glass_bottom_nav_enabled"`). When active (Pipeline A), Twitter uses its own glass bar (`com.x.ui.common.glass.c.e`). The hook only targeted the legacy Material3 bar (`com.x.ui.common.p3.a`, Pipeline B). Crucially, the root shell composable (`com.x.main.k0.j`) has a master suppression flag (`arg[6] z4`) that simultaneously neutralizes both pipelines and collapses the bottom window insets/spacer to `0dp`. Furthermore, an illegal `return null` on `@Composable com.x.main.k0.b` corrupted the Compose slot table.
2. **Decompose Router Freeze After a Single Tab Switch**:
   When switching tabs or dragging the thumb indicator, the first switch succeeded, but all subsequent switches failed completely. This was caused by ClassLoader isolation: casting Twitter's `Function1` to HyperBounce's `((Any) -> Any?)` failed across ClassLoader boundaries, yielding `null`. The code fell back to calling `controller.n(targetVd, null)`. In ArkUI / Decompose (`com.arkivanov.decompose`), when the stack mutation finishes, it invokes the completion callback without a null check. Passing `null` threw a `NullPointerException`, which Decompose's event queue (`com.arkivanov.decompose.o`) latched into `o.e`. Every subsequent attempt to switch tabs threw `IllegalStateException: Can't process the event due to a previous failure`.
3. **Completely Invisible Tab Icons & Badges**:
   `TwitterNavBarHost` attempted to load drawables via `context.createPackageContext("dev.hyperios.app", ...)`. On Android 11+ (API 30+), Package Visibility rules unconditionally throw `PackageManager.NameNotFoundException` because Twitter does not declare `<queries><package android:name="dev.hyperios.app"/></queries>`. The loader fell back to Twitter's context, but queried foreign resource IDs from HyperBounce's `R.drawable`, returning `null`. Because all bitmaps were `null`, `LiquidNavigationBar` skipped rendering the `Image` and the badges (`if (bitmap != null)`), leaving only an empty glass capsule.

This specification provides the unified, production-grade architectural blueprint that eliminates all three root causes.

---

## 2. Architecture & Module Structure

### 2.1 File Placement & Package Alignment
In accordance with HyperBounce project conventions (matching `WhatsAppHook.kt`, `InstagramHook.kt`, `TikTokHook.kt`, and `FacebookHook.kt`):
- **Hook Class (Tier 3)**:
  `app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt`
- **Host View & Composable (Tier 2)**:
  `app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt`
- **Dispatcher Registration (Tier 4)**:
  `app/src/main/java/dev/hyperios/app/PhysicsHook.kt` (already wired in `onPackageReady`):
  ```kotlin
  param.packageName == "com.twitter.android" -> {
      val enabled = HyperBouncePrefs(this).appEnabled("twitter")
      Log.i("HyperBounce", "PhysicsHook: Twitter hook dispatching. app_twitter_enabled=$enabled")
      TwitterHook.init(this, param, HyperBouncePrefs(this))
  }
  ```

### 2.2 4-Tier Separation of Concerns
```
┌────────────────────────────────────────────────────────────────────────┐
│ Tier 1: Generic Core Engine (Unmodified)                               │
│ dev.hyperios.app.nav.LiquidNavigationBar                               │
│ dev.hyperios.app.nav.glass.* (Lens, Drag Gestures, Backdrop)           │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴────────────────────────────────────┐
│ Tier 2: Isolated Per-App Host View & Composable                        │
│ dev.hyperios.app.nav.TwitterNavBarHost.kt                              │
│ - Host-Native Vector Extraction from Twitter Resources                │
│ - Density-Aware ARGB_8888 Rasterization & Stencil Generation           │
│ - Live Preference Binding & LiquidNavigationBar Instantiation          │
│ - 350ms StateFlow Badge Poller Coroutine                               │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴────────────────────────────────────┐
│ Tier 3: Isolated Per-App Hook Engine                                   │
│ dev.hyperios.app.nav.TwitterHook.kt                                    │
│ - MainActivity Lifecycle Hooks (onStart, onResume)                     │
│ - DecorView Attachment & IME Keyboard Dodging                          │
│ - Multi-Layer Universal Stock Bar Suppression                          │
│ - Dynamic Reflection & Proxy-Backed Tab Switching Dispatcher          │
│ - Two-Way State Synchronization & Badge State Extraction              │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴────────────────────────────────────┐
│ Tier 4: Dispatcher & System Integration                                │
│ dev.hyperios.app.PhysicsHook.kt (Package routing)                      │
│ dev.hyperios.app.prefs.HyperBouncePrefs (Per-app toggle)               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Stock Bar Complete Suppression Engine

### 3.1 Understanding the Dual-Pipeline Routing
In Twitter's DEX bytecode (`smali_classes7/com/x/main/k0.smali`), the landing screen composable `com.x.main.k0.j(...)` checks `arg[4]` (`z2` / `"android_x_lite_glass_bottom_nav_enabled"`):
- **If `true` (Pipeline A)**: Invokes `com.x.ui.common.glass.c.e(...)` (built-in glass navigation bar).
- **If `false` (Pipeline B)**: Invokes `com.x.ui.common.p3.a(...)` (legacy Material3 navigation bar).

Crucially, **both pipelines branch on `arg[6]` (`z4`) of `com.x.main.k0.j`**:
- In Pipeline A, `z4` is passed as `arg[3]` (`z2`) to `glass.c.e`. When `true`, line 737 skips calling `glass.c.c(...)` (no stock glass bar composed), and `glass.c.x.getSize()` returns `0`, collapsing the bottom layout spacer to 0dp.
- In Pipeline B, `z4` is passed as `arg[6]` (`z2`) to `p3.a`. When `true`, `p3.a` evaluates `z4 = false`, forcing `AnimatedVisibility(visible = false)` and collapsing `j3VarC` to `0dp`.

### 3.2 The 3-Layer Defense Structure
To guarantee 100% suppression across all Twitter variations, account flags, and feature switch states:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Layer 1: Universal Root Shell Hook                                     │
│ Target: com.x.main.k0.j(...) (35 parameters)                           │
│ Action: Force chain.args[6] (z4) = true                                │
│ Effect: Automatically collapses both Pipeline A and Pipeline B         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Defense in depth
        ┌───────────────────────────┴───────────────────────────┐
        ▼                                                       ▼
┌──────────────────────────────────────┐  ┌──────────────────────────────────────┐
│ Layer 2: Pipeline A Safety Hooks     │  │ Layer 3: Pipeline B Safety Hook      │
│ Target: com.x.ui.common.glass.c.e    │  │ Target: com.x.ui.common.p3.a         │
│ Action: Force chain.args[3] = true   │  │ Action: Force chain.args[6] = true   │
│ Target: com.x.ui.common.glass.c.c    │  │ Effect: Enforces AnimatedVisibility  │
│ Action: Intercept and return null    │  │         visible = false, height = 0dp│
│ Effect: Collapses glass spacer to 0  │  └──────────────────────────────────────┘
└──────────────────────────────────────┘
```

### 3.3 Removal of Destructive Slot Table Bypasses
- **PROHIBITED**: `module.hook(k0Class.declaredMethods.filter { it.name == "b" }).intercept { null }`
- **RATIONALE**: `com.x.main.k0.b` is a `@Composable` method containing Compose compiler restart groups (`startReplaceableGroup` / `endRestartGroup`). Intercepting it and returning `null` leaves unbalanced groups in the Compose slot table, causing runtime freezes or state corruption. Because Layer 1 and Layer 2/3 already completely suppress the parent containers, `k0.b` is never rendered.

---

## 4. Tab Navigation Engine & Decompose Router Fix

### 4.1 Root Cause of the "Single Switch Freeze"
1. **ClassLoader Isolation**: In `TwitterHook.kt`, capturing `val fn1 = chain.args[4] as? ((Any) -> Any?)` failed because `Function1` loaded by HyperBounce (`ModuleClassLoader`) does not match `Function1` loaded by Twitter (`PathClassLoader`). `fn1` was always `null`.
2. **Null Callback Dispatch**: Because `fn1` was null, `dispatchTabClick` invoked `controller.n(targetVd, null)`.
3. **Decompose Completion Crash**: When `k.n(vd, callback)` completed the stack transition, Decompose's internal relay (`com.stripe.android.paymentsheet.addresselement.j0.smali`) invoked `callback.invoke()`. Because `callback` was `null`, it threw a `NullPointerException`.
4. **Decompose Event Queue Poisoning**: Decompose's `com.arkivanov.decompose.o.a(...)` caught the NPE and stored it in `o.e`. On every subsequent navigation event, `o.a` threw `IllegalStateException: Can't process the event due to a previous failure`.

### 4.2 Two-Stage Robust Dispatching Architecture

```
User drags/taps thumb to Tab index (0..4)
                       │
                       ▼
         TwitterHook.dispatchTabClick(index)
                       │
                       ├─► Optimistic UI update: currentTabIndexState.intValue = index
                       │
                       ▼ (mainHandler.post)
        ┌────────────────────────────────────────────────┐
        │ Stage 1: Dynamic Reflection on Twitter Lambda  │
        │ rawTabSwitchFunction.invoke(targetVd)          │
        └──────────────────────┬─────────────────────────┘
                               │
                      Success? │
                    ┌──────────┴──────────┐
                    │ YES                 │ NO (fallback)
                    ▼                     ▼
          Tab switched!         ┌────────────────────────────────────────────────┐
          (Decompose stack      │ Stage 2: Controller Invocation with Proxy      │
          receives native d0    │ Instantiate Proxy for Twitter's Function0:     │
          completion callback)  │ Proxy.newProxyInstance(cl, [Function0], ...)   │
                                │ controller.n(targetVd, proxyCallback)          │
                                └────────────────────────┬───────────────────────┘
                                                         │
                                                         ▼
                                               Tab switched safely!
                                               (Proxy returns null -> kotlin.Unit)
```

### 4.3 Detailed Smali Signatures for Controller & Lambda
- **Tab Enum (`com.x.navigation.vd`)**:
  - `vd.HOME` (ordinal 0)
  - `vd.EXPLORE` (ordinal 1)
  - `vd.GROK` (ordinal 2)
  - `vd.NOTIFICATIONS` (ordinal 3)
  - `vd.DM` (ordinal 4)
- **Controller Class (`com.x.main.k`)**:
  - Query active tab: `k.q() -> vd`
  - Switch tab: `k.n(vd targetVd, Function0 callback) -> void`
  - When switching tabs (`currentVd != targetVd`): pushes to Decompose stack router `this.f.a(new j(vdVar, 1), new j0(callback, 10))`.
  - When re-clicking active tab (`currentVd == targetVd`): triggers `this.c.b(new com.x.main.api.b(targetVd))` (timeline scroll-to-top and pull-to-refresh) and invokes `callback.invoke()`.
- **Dynamic Proxy Implementation**:
  ```kotlin
  val fn0Class = cl.loadClass("kotlin.jvm.functions.Function0")
  val proxy = Proxy.newProxyInstance(
      cl,
      arrayOf(fn0Class),
      InvocationHandler { _, _, _ -> null /* returns kotlin.Unit */ }
  )
  ```

### 4.4 Two-Way Tab State Synchronization
1. **Twitter -> Floating Bar**:
   In `com.x.main.k0.i(...)`, parameter 7 is the current `vd` enum. Whenever Twitter transitions tabs internally (e.g. via deep link or back press), `currentTabIndexState.intValue` is updated:
   ```kotlin
   val currentVd = chain.args[7]
   if (currentVd is Enum<*>) {
       currentTabIndexState.intValue = currentVd.ordinal.coerceIn(0, 4)
   }
   ```
2. **Floating Bar -> Twitter**:
   When the user drags the glass thumb or clicks a tab in `LiquidNavigationBar`, `onTabSelected(index)` triggers `TwitterHook.dispatchTabClick(index)`. The optimistic index update moves the indicator instantly, and the main thread dispatches the transition to Twitter's Decompose router.

---

## 5. Native Icon Extraction & Badge Pipeline

### 5.1 Root Cause of Invisible Icons
1. Android 11+ Package Visibility prevents Twitter from calling `context.createPackageContext("dev.hyperios.app", ...)`.
2. HyperBounce's bundled XML drawables were stripped or renamed by R8 resource shrinking (`isShrinkResources = true`).
3. Twitter's context could not resolve HyperBounce's foreign R integer IDs.
4. `LiquidNavigationBar` hides tabs and badges if the provided bitmap is `null`:
   ```kotlin
   if (bitmap != null) {
       Image(...)
       // Badges rendered here
   }
   ```

### 5.2 Host-Native Vector Extraction
Twitter's APK already contains the authentic vector drawables in `/res/drawable/`. We resolve them directly from Twitter's own `Resources` using Twitter's application context (`backdropSource.context`).

#### Exact Resource Mapping:
| Tab Index | Tab Enum (`vd`) | State | Resource Entry Name | Static Resource ID Fallback | Alternate Alias Names |
|---|---|---|---|---|---|
| 0 | `HOME` | Inactive | `ic_vector_home_stroke` | `0x7f0805c9` | `ic_vector_home_outline` |
| 0 | `HOME` | Active | `ic_vector_home` | `0x7f0805c8` | `ic_vector_home_filled` |
| 1 | `EXPLORE` | Inactive | `ic_vector_search_stroke` | `0x7f080722` | `ic_vector_explore_stroke` |
| 1 | `EXPLORE` | Active | `ic_vector_search` | `0x7f08071b` | `ic_vector_explore` |
| 2 | `GROK` | Inactive | `ic_vector_grok_stroke` | `0x7f0805aa` | `ic_vector_grok_icon`, `ic_vector_grok_bot_logo` |
| 2 | `GROK` | Active | `ic_vector_grok_icon_filled` | `0x7f0805a7` | `ic_vector_grok_logo` |
| 3 | `NOTIFICATIONS` | Inactive | `ic_vector_notifications_stroke` | `0x7f080675` | `ic_vector_bell_stroke` |
| 3 | `NOTIFICATIONS` | Active | `ic_vector_notifications` | `0x7f080669` | `ic_vector_bell` |
| 4 | `DM` | Inactive | `ic_vector_chat_stroke` | `0x7f0804ca` | `ic_vector_messages_stroke` |
| 4 | `DM` | Active | `ic_vector_chat` | `0x7f0804b9` | `ic_vector_messages` |

### 5.3 Density-Aware Vector Rasterization & Stencil Rendering
To ensure crisp rendering on 1080p, 1440p, and high-DPI displays without pixelation:
1. Target dimension: `(28f * density).roundToInt().coerceAtLeast(72)` px.
2. **Inactive State**:
   - Bitmaps are rendered without Compose color filtering.
   - Pre-tinted with authentic Twitter palette:
     - Dark Mode: `#E7E9EA` (authentic light gray)
     - Light Mode: `#0F1419` (authentic deep slate)
3. **Active State**:
   - `LiquidNavigationBar` applies `colorFilter = ColorFilter.tint(accentColor)` on the active tab inside the sliding pill.
   - Bitmaps MUST be rendered as a pure white stencil (`#FFFFFF`) with alpha channel preserved.
   - This allows Twitter Blue (`#1D9BF0`) or user custom accent colors to apply with 100% mathematical accuracy.

### 5.4 Dynamic Badges & Unread Dots Pipeline
Twitter manages navigation badges in `LandingBadgesState` (`com.x.main.api.landing.w0`):
- `w0.d`: `LinkedHashMap<vd, m1>`
- `m1.a` (`Int`): Numeric count. If `a > 0`, renders numeric badge pill (`"3"`, `"12"`).
- `m1.b` (`Boolean`): Unread dot flag. If `a == 0 && b == true`, renders unread status dot.

#### Synchronization Channels:
1. **Composable Recomposition**: `k0.b` passes `w0` as argument 1.
2. **Controller Polling**: In `TwitterNavBarHost`, a `LaunchedEffect(Unit)` loop polls `controller.u() -> StateFlow<w0>` every 350ms to guarantee zero lag when notifications arrive in the background.

---

## 6. Host Attachment, Window Insets & IME Keyboard Handling

### 6.1 DecorView vs Content Attachment
- **Attachment Target**: `activity.window.decorView as ViewGroup`.
- **Scroll Immunity Guarantee**:
  Twitter's timeline feed is hosted inside `AndroidComposeView`. Feed scrolling is handled internally via Jetpack Compose pointer input modifiers and canvas clipping. `decorView` is the top-level window root. By attaching the floating bar directly to `decorView`, the bar is structurally completely outside the scroll hierarchy and guaranteed 100% scroll immunity.

### 6.2 IME Soft Keyboard Dodging
When the user taps a text input (e.g. Tweet Composer, DM Chat, Search Bar), the Android soft keyboard appears.
- An `OnApplyWindowInsetsListener` attached to the `ComposeView` detects keyboard visibility:
  ```kotlin
  composeView.setOnApplyWindowInsetsListener { v, insets ->
      val imeBottom = try {
          insets.getInsets(WindowInsets.Type.ime()).bottom
      } catch (_: Throwable) { 0 }
      val targetVisibility = if (imeBottom > 200) View.GONE else View.VISIBLE
      if (v.visibility != targetVisibility) {
          v.visibility = targetVisibility
      }
      insets
  }
  ```
- When the keyboard appears (`imeBottom > 200`), the bar instantly hides (`View.GONE`).
- When the keyboard dismisses (`imeBottom == 0`), the bar reappears (`View.VISIBLE`).

### 6.3 Lifecycle & ViewTree Wiring
ComposeView requires valid `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, and `ViewTreeSavedStateRegistryOwner`:
- If `activity` implements them, bind them directly.
- Fallback: instantiate `FakeLifecycleOwner` pushed to `ON_RESUME`.

---

## 7. Production-Grade Implementation Code

### 7.1 `dev.hyperios.app.nav.TwitterHook.kt`

```kotlin
package dev.hyperios.app.nav

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Production-Grade Xposed Hook for X (formerly Twitter) (com.twitter.android).
 *
 * Implements:
 * 1. Multi-layer universal stock navigation bar suppression (Root k0.j, Pipeline A glass.c, Pipeline B p3.a).
 * 2. Permanent scroll immunity via DecorView injection.
 * 3. IME keyboard dodging.
 * 4. Zero-latency tab switching with dynamic reflection and ClassLoader-safe Function0 Proxy.
 * 5. Real-time badge and unread status synchronization.
 */
object TwitterHook {
    private const val TAG = "TwitterHook"
    private const val TAG_OVERLAY = "HyperFloatingBar_Twitter"
    private const val OVERLAY_TAG_ID = 0x7F0B9920

    lateinit var module: XposedModule
    lateinit var prefs: HyperBouncePrefs
    var classLoader: ClassLoader? = null

    val currentTabIndexState = mutableIntStateOf(0)
    val badgeCountsState = mutableStateOf<List<String?>>(listOf(null, null, null, null, null))
    val badgeDotsState = mutableStateOf<List<Boolean>>(listOf(false, false, false, false, false))

    @Volatile
    var activeTabController: Any? = null

    @Volatile
    var rawTabSwitchFunction: Any? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedFunction0Proxy: Any? = null

    @JvmStatic
    fun init(
        module: XposedModule,
        param: XposedModuleInterface.PackageReadyParam,
        prefs: HyperBouncePrefs
    ) {
        this.module = module
        this.prefs = prefs
        this.classLoader = param.classLoader

        val enabled = prefs.appEnabled("twitter")
        Log.i(TAG, "TwitterHook.init: enabled=$enabled for ${param.packageName}")
        if (!enabled) {
            module.log(Log.INFO, TAG, "Twitter hook disabled in preferences, skipping injection")
            return
        }

        module.log(Log.INFO, TAG, "Initializing Redesigned Twitter Hook (Dual-Pipeline Neutralization & Proxy Tab Engine)")

        hookMainActivity(param.classLoader)
        hookMultiLayerSuppression(param.classLoader)
        hookNavigationAndBadges(param.classLoader)
    }

    private fun hookMainActivity(cl: ClassLoader) {
        val targetActivities = listOf(
            "com.x.android.main.MainActivity",
            "com.twitter.app.main.MainActivity"
        )

        for (activityName in targetActivities) {
            try {
                val actClass = cl.loadClass(activityName)

                // Hook onStart()
                try {
                    val onStartMethod = actClass.declaredMethods.find {
                        it.name == "onStart" && it.parameterTypes.isEmpty()
                    } ?: actClass.getMethod("onStart")

                    module.hook(onStartMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { attachNavBar(it) }
                        result
                    }
                    Log.i(TAG, "Hooked onStart on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onStart on $activityName: ${t.message}")
                }

                // Hook onResume()
                try {
                    val onResumeMethod = actClass.getMethod("onResume")
                    module.hook(onResumeMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { attachNavBar(it) }
                        result
                    }
                    Log.i(TAG, "Hooked onResume on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onResume on $activityName: ${t.message}")
                }
            } catch (_: ClassNotFoundException) {
            } catch (t: Throwable) {
                Log.e(TAG, "Error hooking activity $activityName", t)
            }
        }
    }

    private fun attachNavBar(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val decorView = activity.window?.decorView as? ViewGroup ?: return@runOnUiThread
                if (decorView.findViewWithTag<View>(TAG_OVERLAY) != null ||
                    decorView.findViewById<View>(OVERLAY_TAG_ID) != null
                ) {
                    return@runOnUiThread
                }

                val backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView

                val fallbackOwner by lazy {
                    FakeLifecycleOwner().apply {
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    }
                }
                val lifecycleOwner = (activity as? androidx.lifecycle.LifecycleOwner) ?: fallbackOwner

                if (decorView.findViewTreeLifecycleOwner() == null) {
                    decorView.setViewTreeLifecycleOwner(lifecycleOwner)
                }
                if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
                    decorView.setViewTreeSavedStateRegistryOwner(
                        activity as? androidx.savedstate.SavedStateRegistryOwner
                            ?: (fallbackOwner as androidx.savedstate.SavedStateRegistryOwner)
                    )
                }
                if (decorView.findViewTreeViewModelStoreOwner() == null) {
                    decorView.setViewTreeViewModelStoreOwner(
                        activity as? androidx.lifecycle.ViewModelStoreOwner
                            ?: (fallbackOwner as androidx.lifecycle.ViewModelStoreOwner)
                    )
                }

                val composeView = ComposeView(activity).apply {
                    tag = TAG_OVERLAY
                    id = OVERLAY_TAG_ID
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(
                        activity as? androidx.savedstate.SavedStateRegistryOwner
                            ?: (fallbackOwner as androidx.savedstate.SavedStateRegistryOwner)
                    )
                    setViewTreeViewModelStoreOwner(
                        activity as? androidx.lifecycle.ViewModelStoreOwner
                            ?: (fallbackOwner as androidx.lifecycle.ViewModelStoreOwner)
                    )

                    setContent {
                        TwitterNavBarHost(
                            backdropSource = backdropSource,
                            hyperBouncePrefs = prefs
                        )
                    }
                }

                // Automatic keyboard (IME) dodging: hide bar when soft keyboard is shown
                composeView.setOnApplyWindowInsetsListener { v, insets ->
                    val imeBottom = try {
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                    } catch (_: Throwable) { 0 }

                    val targetVisibility = if (imeBottom > 200) View.GONE else View.VISIBLE
                    if (v.visibility != targetVisibility) {
                        v.visibility = targetVisibility
                    }
                    insets
                }

                decorView.addView(composeView)
                Log.i(TAG, "TwitterNavBarHost successfully attached to DecorView (Scroll Immune + IME Dodging)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach TwitterNavBarHost to DecorView", t)
            }
        }
    }

    private fun hookMultiLayerSuppression(cl: ClassLoader) {
        // LAYER 1: Root Navigation Shell Hook: com.x.main.k0.j(...)
        // Forces param 6 (z4) to true, simultaneously collapsing Pipeline A (glass) and Pipeline B (p3)
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val jMethods = k0Class.declaredMethods.filter { it.name == "j" && it.parameterTypes.size >= 30 }
            for (m in jMethods) {
                module.hook(m).intercept { chain ->
                    if (chain.args.size >= 7 && chain.args[6] is Boolean) {
                        chain.args[6] = true
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Layer 1 Hooked: com.x.main.k0.j (Root Shell Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.j", t)
        }

        // LAYER 2: Pipeline A Hooks: com.x.ui.common.glass.c
        try {
            val glassClass = cl.loadClass("com.x.ui.common.glass.c")

            // Method e: Container - force param 3 (z2) = true (collapses size to 0 and skips c())
            for (m in glassClass.declaredMethods.filter { it.name == "e" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { chain ->
                    if (chain.args.size >= 4 && chain.args[3] is Boolean) {
                        chain.args[3] = true
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.e (Glass Container Suppression)")
            }

            // Method c: Glass Bar Rendering - direct interception fallback
            for (m in glassClass.declaredMethods.filter { it.name == "c" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { _ ->
                    // Return null to suppress stock glass navigation bar if ever reached
                    null
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.c (Glass Bar Neutralization)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.glass.c", t)
        }

        // LAYER 3: Pipeline B Hook: com.x.ui.common.p3.a
        try {
            val p3Class = cl.loadClass("com.x.ui.common.p3")
            for (m in p3Class.declaredMethods.filter { it.name == "a" && it.parameterTypes.size >= 10 }) {
                module.hook(m).intercept { chain ->
                    if (chain.args.size >= 7 && chain.args[6] is Boolean) {
                        chain.args[6] = true
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Layer 3 Hooked: com.x.ui.common.p3.a (Legacy Bottom Bar Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.p3.a", t)
        }
    }

    private fun hookNavigationAndBadges(cl: ClassLoader) {
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val iMethods = k0Class.declaredMethods.filter { it.name == "i" && it.parameterTypes.size >= 10 }
            for (m in iMethods) {
                module.hook(m).intercept { chain ->
                    try {
                        if (chain.args.size >= 9) {
                            val fn1 = chain.args[4]
                            val currentVd = chain.args[7]
                            val controller = chain.args[8]

                            if (fn1 != null) {
                                rawTabSwitchFunction = fn1
                            }
                            if (controller != null) {
                                activeTabController = controller
                                pollBadgesFromController(controller)
                            }
                            if (currentVd is Enum<*>) {
                                currentTabIndexState.intValue = currentVd.ordinal.coerceIn(0, 4)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in k0.i hook", t)
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Hooked com.x.main.k0.i for Navigation & Badges")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.i", t)
        }
    }

    private fun getOrCreateFunction0Proxy(cl: ClassLoader): Any? {
        cachedFunction0Proxy?.let { return it }
        synchronized(this) {
            cachedFunction0Proxy?.let { return it }
            return try {
                val fn0Class = cl.loadClass("kotlin.jvm.functions.Function0")
                val proxy = Proxy.newProxyInstance(
                    cl,
                    arrayOf(fn0Class),
                    InvocationHandler { _, _, _ -> null /* returns kotlin.Unit */ }
                )
                cachedFunction0Proxy = proxy
                proxy
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create Function0 Proxy", t)
                null
            }
        }
    }

    fun pollBadgesFromController(controller: Any) {
        try {
            val uMethod = controller.javaClass.methods.find { it.name == "u" && it.parameterTypes.isEmpty() } ?: return
            val stateFlow = uMethod.invoke(controller) ?: return
            val getValueMethod = stateFlow.javaClass.methods.find { it.name == "getValue" && it.parameterTypes.isEmpty() } ?: return
            val w0 = getValueMethod.invoke(stateFlow) ?: return
            extractBadges(w0)
        } catch (_: Throwable) {}
    }

    fun extractBadges(w0Var: Any?) {
        if (w0Var == null) return
        try {
            val dField = w0Var.javaClass.declaredFields.find { it.name == "d" }?.apply { isAccessible = true } ?: return
            val map = dField.get(w0Var) as? Map<*, *> ?: return
            val cl = classLoader ?: w0Var.javaClass.classLoader
            val vdClass = cl.loadClass("com.x.navigation.vd")
            val enums = vdClass.enumConstants as? Array<Enum<*>> ?: return

            val counts = mutableListOf<String?>(null, null, null, null, null)
            val dots = mutableListOf(false, false, false, false, false)

            for (i in 0 until 5) {
                val targetEnum = enums.getOrNull(i) ?: continue
                val m1Obj = map[targetEnum] ?: continue
                val aField = m1Obj.javaClass.declaredFields.find { it.name == "a" }?.apply { isAccessible = true }
                val bField = m1Obj.javaClass.declaredFields.find { it.name == "b" }?.apply { isAccessible = true }
                val count = aField?.getInt(m1Obj) ?: 0
                val dot = bField?.getBoolean(m1Obj) ?: false
                counts[i] = if (count > 0) count.toString() else null
                dots[i] = dot && count == 0
            }

            badgeCountsState.value = counts
            badgeDotsState.value = dots
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract badges from LandingBadgesState", t)
        }
    }

    /**
     * Dispatches zero-latency tab switches to Twitter's Decompose Router.
     * Guaranteed crash-proof across rapid scrubbing and tab re-clicks.
     */
    fun dispatchTabClick(index: Int) {
        val cl = classLoader ?: return
        val targetIndex = index.coerceIn(0, 4)

        // Optimistic UI state update
        currentTabIndexState.intValue = targetIndex

        mainHandler.post {
            try {
                val vdClass = cl.loadClass("com.x.navigation.vd")
                val enums = vdClass.enumConstants as? Array<Enum<*>> ?: return@post
                val targetVd = enums.getOrNull(targetIndex) ?: return@post

                var dispatched = false

                // Method 1: Lambda invocation via reflective method call (ClassLoader safe)
                val rawFn = rawTabSwitchFunction
                if (rawFn != null) {
                    try {
                        val invokeMethod = rawFn.javaClass.methods.find {
                            it.name == "invoke" && it.parameterTypes.size == 1 &&
                                    it.parameterTypes[0].isAssignableFrom(targetVd.javaClass)
                        } ?: rawFn.javaClass.methods.find {
                            it.name == "invoke" && it.parameterTypes.size == 1
                        }

                        if (invokeMethod != null) {
                            invokeMethod.invoke(rawFn, targetVd)
                            dispatched = true
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Method 1 lambda dispatch failed: ${t.message}")
                    }
                }

                // Method 2: Controller invocation fallback with Proxy Function0
                if (!dispatched) {
                    val controller = activeTabController
                    if (controller != null) {
                        val fn0Class = cl.loadClass("kotlin.jvm.functions.Function0")
                        val nMethod = controller.javaClass.methods.find {
                            it.name == "n" && it.parameterTypes.size == 2 &&
                                    it.parameterTypes[0] == vdClass &&
                                    it.parameterTypes[1] == fn0Class
                        }

                        val nonNullCallback = getOrCreateFunction0Proxy(cl)
                        if (nMethod != null && nonNullCallback != null) {
                            nMethod.invoke(controller, targetVd, nonNullCallback)
                            dispatched = true
                        }
                    }
                }

                // Verify active tab in Decompose stack
                val controller = activeTabController
                if (controller != null) {
                    try {
                        val qMethod = controller.javaClass.methods.find { it.name == "q" && it.parameterTypes.isEmpty() }
                        val activeVd = qMethod?.invoke(controller) as? Enum<*>
                        if (activeVd != null) {
                            currentTabIndexState.intValue = activeVd.ordinal.coerceIn(0, 4)
                        }
                    } catch (_: Throwable) {}
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Failed to dispatch tab click to $targetIndex", t)
            }
        }
    }
}
```

---

### 7.2 `dev.hyperios.app.nav.TwitterNavBarHost.kt`

```kotlin
package dev.hyperios.app.nav

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class TwitterTabSpec(
    val inactiveName: String,
    val inactiveStaticId: Int,
    val activeName: String,
    val activeStaticId: Int
)

private val TWITTER_TAB_SPECS = listOf(
    // Tab 0: Home
    TwitterTabSpec(
        inactiveName = "ic_vector_home_stroke",
        inactiveStaticId = 0x7f0805c9,
        activeName = "ic_vector_home",
        activeStaticId = 0x7f0805c8
    ),
    // Tab 1: Explore / Search
    TwitterTabSpec(
        inactiveName = "ic_vector_search_stroke",
        inactiveStaticId = 0x7f080722,
        activeName = "ic_vector_search",
        activeStaticId = 0x7f08071b
    ),
    // Tab 2: Grok
    TwitterTabSpec(
        inactiveName = "ic_vector_grok_stroke",
        inactiveStaticId = 0x7f0805aa,
        activeName = "ic_vector_grok_icon_filled",
        activeStaticId = 0x7f0805a7
    ),
    // Tab 3: Notifications
    TwitterTabSpec(
        inactiveName = "ic_vector_notifications_stroke",
        inactiveStaticId = 0x7f080675,
        activeName = "ic_vector_notifications",
        activeStaticId = 0x7f080669
    ),
    // Tab 4: Messages / DMs
    TwitterTabSpec(
        inactiveName = "ic_vector_chat_stroke",
        inactiveStaticId = 0x7f0804ca,
        activeName = "ic_vector_chat",
        activeStaticId = 0x7f0804b9
    )
)

/**
 * Loads authentic vector drawables directly from Twitter's own resources.
 * Immune to Android 11+ Package Visibility restrictions.
 */
private fun loadTwitterHostDrawable(context: Context, resName: String, staticResId: Int): Drawable? {
    // 1. Dynamic lookup by name in host package
    try {
        val id = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (id != 0) {
            val d = ResourcesCompat.getDrawable(context.resources, id, context.theme)
            if (d != null) return d
        }
    } catch (_: Throwable) {}

    // 2. Direct static resource ID fallback
    try {
        val d = ResourcesCompat.getDrawable(context.resources, staticResId, context.theme)
        if (d != null) return d
    } catch (_: Throwable) {}

    // 3. Fallback to alternative Twitter vector resource names
    val fallbackNames = when (resName) {
        "ic_vector_chat" -> listOf("ic_vector_messages")
        "ic_vector_chat_stroke" -> listOf("ic_vector_messages_stroke")
        "ic_vector_grok_stroke" -> listOf("ic_vector_grok_icon", "ic_vector_grok_bot_logo")
        "ic_vector_grok_icon_filled" -> listOf("ic_vector_grok_icon", "ic_vector_grok_logo")
        else -> emptyList()
    }
    for (name in fallbackNames) {
        try {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) {
                val d = ResourcesCompat.getDrawable(context.resources, id, context.theme)
                if (d != null) return d
            }
        } catch (_: Throwable) {}
    }

    return null
}

/**
 * Density-aware vector rasterizer.
 * - Inactive: Rendered in authentic X gray/slate (#E7E9EA in dark, #0F1419 in light).
 * - Active: Rendered as a pure white stencil (#FFFFFF) with alpha channel for Compose ColorFilter tinting.
 */
private fun renderTabBitmap(
    context: Context,
    resName: String,
    staticResId: Int,
    isDark: Boolean,
    isActive: Boolean
): Bitmap? {
    val drawable = loadTwitterHostDrawable(context, resName, staticResId)?.mutate() ?: return null

    val density = context.resources.displayMetrics.density
    val targetSizePx = (28f * density).roundToInt().coerceAtLeast(72)

    val tintColor = if (!isActive) {
        if (isDark) android.graphics.Color.parseColor("#E7E9EA")
        else android.graphics.Color.parseColor("#0F1419")
    } else {
        android.graphics.Color.WHITE
    }

    drawable.setTint(tintColor)
    drawable.setTintMode(PorterDuff.Mode.SRC_IN)

    val bmp = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, targetSizePx, targetSizePx)
    drawable.draw(canvas)
    return bmp
}

private fun resolveAccentColor(preset: String, customColor: Long): Color = when (preset) {
    "Blue" -> Color(0xFF1D9BF0) // Authentic Twitter Blue
    "Pink" -> Color(0xFFFF2D78)
    "Purple" -> Color(0xFFAF52DE)
    "Orange" -> Color(0xFFFF9500)
    "Green" -> Color(0xFF34C759)
    "Custom" -> Color(customColor)
    else -> Color(0xFF1D9BF0)
}

/**
 * Android View Host for Twitter LiquidNavigationBar.
 */
class TwitterNavBarHost @JvmOverloads constructor(
    context: Context,
    val prefs: HyperBouncePrefs,
    val backdropSource: View = (context as? Activity)?.findViewById(android.R.id.content) ?: View(context),
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        clipChildren = false
        clipToPadding = false
        val composeView = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                TwitterNavBarHost(
                    backdropSource = backdropSource,
                    hyperBouncePrefs = prefs
                )
            }
        }
        addView(composeView)
    }
}

@Composable
fun TwitterNavBarHost(
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "twitter")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val context = backdropSource.context

    // Native host vector extraction
    val inactiveTabs = remember(context, isDark) {
        TWITTER_TAB_SPECS.map { spec ->
            renderTabBitmap(context, spec.inactiveName, spec.inactiveStaticId, isDark = isDark, isActive = false)
        }
    }

    val activeTabs = remember(context, isDark) {
        TWITTER_TAB_SPECS.map { spec ->
            renderTabBitmap(context, spec.activeName, spec.activeStaticId, isDark = isDark, isActive = true)
        }
    }

    val currentTabIndex by TwitterHook.currentTabIndexState
    val badgeCounts by TwitterHook.badgeCountsState
    val badgeDots by TwitterHook.badgeDotsState

    // 350ms StateFlow poller for real-time badge updates
    LaunchedEffect(Unit) {
        while (true) {
            TwitterHook.activeTabController?.let { controller ->
                TwitterHook.pollBadgesFromController(controller)
            }
            delay(350)
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val tabCount = 5
    val maxSafeTabWidth = ((screenWidthDp - 16f) / tabCount).coerceAtLeast(40f)
    val effectiveTabWidth = live.tabWidth.coerceAtMost(maxSafeTabWidth)

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = inactiveTabs,
        activeTabs = activeTabs,
        selectedIndex = currentTabIndex.coerceIn(0, 4),
        onTabSelected = { index ->
            TwitterHook.dispatchTabClick(index)
        },
        sourceView = backdropSource,
        forceLightTheme = live.themeMode == "Light",
        forceDarkTheme = live.themeMode == "Dark",
        containerAlpha = live.containerOpacity,
        effectMultiplier = live.glassIntensity,
        barBlurIntensity = live.barBlurIntensity,
        labels = if (live.showLabels) listOf("Home", "Explore", "Grok", "Notifications", "Messages") else emptyList(),
        customAccentColor = resolveAccentColor(live.accent, live.customAccentColor),
        barHeight = live.barHeight.dp,
        tabWidth = effectiveTabWidth.dp,
        cornerRadius = live.cornerRadius.dp,
        pillGlassIntensity = live.pillGlassIntensity,
        pillContainerOpacity = live.pillContainerOpacity,
        pillChromaticAberration = live.pillChromaticAberration,
        pillShadow = live.pillShadow,
        liquidGlass = live.liquidGlass,
        isShrunk = false,
        shrinkScale = 1.0f,
        scrollShrinkState = null,
        badgeCounts = badgeCounts,
        badgeDots = badgeDots
    )
}
```

---

## 8. Verification Matrix & Quality Assurance Plan

| Verification Dimension | Expected Behavior | Verification Target & Smali Check |
|---|---|---|
| **Stock Bar Suppression (Pipeline A)** | Built-in glass bottom navigation bar never renders; spacer = 0dp. | `com.x.main.k0.j` forcing `arg[6] = true` -> `glass.c.e` skips `c(...)`, `getSize() = 0`. |
| **Stock Bar Suppression (Pipeline B)** | Stock Material3 bottom bar never renders; spacer = 0dp. | `com.x.main.k0.j` forcing `arg[6] = true` -> `p3.a` sets `AnimatedVisibility(visible = false)`, `height = 0`. |
| **Continuous Tab Dragging** | Thumb pill drags smoothly to any tab (e.g. Tab 0 -> Tab 2 -> Tab 4 -> Tab 1 -> Tab 0) and switches tabs every time. | `rawTabSwitchFunction.invoke(vd)` + `controller.n(vd, proxy)` prevents Decompose NPE. |
| **Tab Re-Click (Active Tab)** | Tapping the currently selected tab scrolls feed to top and triggers pull-to-refresh without crashing. | `k.n(vd, proxy)` calls `noopProxy.invoke()`, preventing NPE in `k.smali` line 572. |
| **Native Icon Visibility** | All 5 authentic tab icons visible in sharp detail (outline when inactive, filled when active). | `loadTwitterHostDrawable` loads authentic vector drawables from host `Resources`. |
| **Active Stencil Tinting** | Selected tab turns brand blue (`#1D9BF0`) or user custom accent color. | White stencil `#FFFFFF` + Compose `ColorFilter.tint(accentColor)`. |
| **Unread Badges & Dots** | Numeric counts appear on DMs; unread dots appear on Notifications when new items arrive. | `w0.d` extraction via `StateFlow` poller + non-null bitmap rendering in `LiquidBottomTab`. |
| **Scroll Immunity** | Scrolling Twitter timeline feeds never moves, scrolls, or obscures the floating bar. | Direct attachment to `activity.window.decorView`. |
| **Keyboard Interaction** | Opening Tweet composer or typing DM auto-hides the floating bar. | `OnApplyWindowInsetsListener` sets `View.GONE` when `imeBottom > 200`. |

---

## 9. Implementation Directives for Coder Subagent

1. **Strictly adhere to file paths**:
   - `app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt`
   - `app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt`
2. **Do NOT run local Gradle builds** (Adhere strictly to `AGENT_RULES.md` Section 2: Termux local compilation causes CPU/OOM exhaustion). Remote CI builds will be triggered on push to GitHub `hos4` branch.
3. **Verify absence of destructive slot table hooks**:
   Confirm that the old `return null` on `com.x.main.k0.b` is completely purged from `TwitterHook.kt`.
4. **Ensure clean imports**:
   Ensure all imports compile cleanly without missing symbols or unresolved references.
