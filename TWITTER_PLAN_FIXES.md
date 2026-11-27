# HyperBounce (HOS 4) - Twitter (X) Integration Fix Plan
**Target Branch:** `hos4`  
**Target Package:** `com.twitter.android` (Twitter / X `12.46.1-prod.01`)  
**Specification File:** `TWITTER_PLAN_FIXES.md`  
**Date:** November 27, 2026  

---

## Executive Overview & Problem Statement

Three distinct defects prevent the Twitter floating liquid navigation bar in HyperBounce HOS 4 from operating at production grade:

1. **Stock Navigation Bar Suppression Failure (`UnsupportedOperationException`):**
   - **Symptom:** The stock bottom navigation bar remains fully visible with all 5 native icons positioned beneath HyperBounce's floating bar.
   - **Root Cause:** In `TwitterHook.kt`, the suppression interceptors attempted in-place mutation of `chain.args`:
     `chain.args[6] = true` and `chain.args[3] = true`.
     In LibXposed (`io.github.libxposed.api.XposedInterface.Chain`), `chain.args` is an `UnmodifiableList`. Calling `set()` throws `java.lang.UnsupportedOperationException`. The LSPosed hook runtime (`Vector`) catches the crash, skips the hook, and proceeds with the original unmodified arguments (`p6 = false`, visible).
2. **Backdrop & Blur Pipeline Starvation (`Software rendering doesn't support RuntimeShader`):**
   - **Symptom:** The floating bar renders as a flat opaque or semi-translucent shape with 0% blur, no vibrancy, and no lens refraction.
   - **Root Cause:** Twitter's UI is 100% Jetpack Compose embedding Chris Banes' `Haze` library (`dev.chrisbanes.haze`) and AGSL shaders (`android.graphics.RuntimeShader`). In `LiquidGlassViewBackdrop.kt`, `sourceView.draw(softwareCanvas)` throws `IllegalArgumentException: Software rendering doesn't support RuntimeShader / RenderEffect`. The exception was silently caught and swallowed every frame. As a result, `capturedFrame` remained permanently `null`, starving Kyant's backdrop engine (`drawBackdrop`) and causing it to bail out immediately.
3. **Center Tab (Grok) Inactive Icon Displays a "Clock" Face:**
   - **Symptom:** When inactive, the Grok tab icon appears like an analog clock face or gauge.
   - **Root Cause:** `TWITTER_TAB_SPECS[2].inactiveName` was set to `ic_vector_grok_stroke` (`0x7f0805aa`), which is an internal 24dp square box with a diagonal slash that visually mimics a clock face at tab icon scale.
   - **Authentic Asset:** Decompilation of Twitter's Jetfuel design system (`com.x.jetfuel.element.d` and `com.x.icons.a`) reveals the authentic bottom tab drawables:
     - Inactive: `ic_vector_xai_grok_tab_stroke` (`0x7f080816`)
     - Active: `ic_vector_xai_grok_tab` (`0x7f080815`)

This document provides the exhaustive architectural specification and exact implementation diffs to resolve all three issues cleanly and permanently.

---

## 1. Stock Navigation Bar Suppression Specification

### 1.1 Root Cause & LibXposed Contract
LibXposed's `XposedInterface.Chain` defines:
```java
List<Object> getArgs(); // UnmodifiableList
Object proceed(Object[] args) throws Throwable;
```
In Kotlin, `chain.args[6] = true` compiles to `chain.args.set(6, true)`. Because `chain.args` is an `UnmodifiableList`, it throws `java.lang.UnsupportedOperationException`.

To pass modified arguments to the next interceptor or target method, the arguments must be copied to a typed array, modified in the array, and passed via `chain.proceed(args: Array<Any?>)`:
```kotlin
val args = chain.args.toTypedArray()
if (args.size >= 7 && args[6] is Boolean) {
    args[6] = true
    chain.proceed(args)
} else {
    chain.proceed()
}
```

### 1.2 Multi-Layer Suppression Architecture in `TwitterHook.kt`

The fix applies across all three layers in `TwitterHook.kt`:

#### Layer 1: Root Navigation Shell (`com.x.main.k0.j`)
- **Target:** Composable function `j` in `com.x.main.k0` (takes 35 parameters).
- **Target Param:** Parameter index 6 (`z4` / `isBottomBarHidden: Boolean`).
- **Mechanism:** Intercept and copy args to array, set `args[6] = true`, call `chain.proceed(args)`. Setting `p6 = true` forces both Pipeline A (Glass) and Pipeline B (p3) to collapse.

#### Layer 2: Pipeline A Glass Hooks (`com.x.ui.common.glass.c`)
- **Method `e` (Container):**
  - Parameter index 3 (`z2` / `isBottomBarHidden: Boolean`).
  - Copy args to array, set `args[3] = true`, call `chain.proceed(args)`.
- **Method `c` (Glass Bar Composable Rendering):**
  - Parameter index 4 (`z2` / hidden flag).
  - Copy args to array, set `args[4] = true`, call `chain.proceed(args)`.
  - *Safety Note:* Do NOT simply return `null` without proceeding, because Compose runtime requires composer group balance. Proceeding with hidden flag ensures proper group teardown without crashing Compose.

#### Layer 3: Pipeline B Standard Bottom Bar Hook (`com.x.ui.common.p3.a`)
- **Target:** Composable function `a` in `com.x.ui.common.p3` (takes 11 parameters).
- **Target Param:** Parameter index 6 (`isBottomBarHidden: Boolean`).
- **Smali Evidence:** Lines 285–300 & 740 of `p3.smali`:
  ```smali
  # v8 is p6 (isBottomBarHidden)
  if-nez v8, :cond_8
  ...
  :cond_8
  move v10, v11   # v11 is false (0); v10 becomes false
  :cond_9
  ...
  invoke-static {..., v21, ...}, Landroidx/compose/animation/p;->h(...) # AnimatedVisibility(visible = v21)
  ```
  When `args[6] = true`, `v10` is forced to `false`, and `AnimatedVisibility` collapses the bar completely to zero height and skips rendering tab items.
- **Mechanism:** Copy args to array, set `args[6] = true`, call `chain.proceed(args)`.

### 1.3 Exact Code Diff for `TwitterHook.kt`

```kotlin
    private fun hookMultiLayerSuppression(cl: ClassLoader) {
        // LAYER 1: Root Navigation Shell Hook: com.x.main.k0.j(...)
        // Forces param 6 (z4 / isBottomBarHidden) to true
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val jMethods = k0Class.declaredMethods.filter { it.name == "j" && it.parameterTypes.size >= 30 }
            for (m in jMethods) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 7 && args[6] is Boolean) {
                        args[6] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 1 Hooked: com.x.main.k0.j (Root Shell Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.j", t)
        }

        // LAYER 2: Pipeline A Hooks: com.x.ui.common.glass.c
        try {
            val glassClass = cl.loadClass("com.x.ui.common.glass.c")

            // Method e: Container - force param 3 (z2 / isBottomBarHidden) = true
            for (m in glassClass.declaredMethods.filter { it.name == "e" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 4 && args[3] is Boolean) {
                        args[3] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.e (Glass Container Suppression)")
            }

            // Method c: Glass Bar Rendering - direct parameter neutralization with group balance
            for (m in glassClass.declaredMethods.filter { it.name == "c" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 5 && args[4] is Boolean) {
                        args[4] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.c (Glass Bar Neutralization)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.glass.c", t)
        }

        // LAYER 3: Pipeline B Hook: com.x.ui.common.p3.a
        // Forces param 6 (isBottomBarHidden) = true
        try {
            val p3Class = cl.loadClass("com.x.ui.common.p3")
            for (m in p3Class.declaredMethods.filter { it.name == "a" && it.parameterTypes.size >= 10 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 7 && args[6] is Boolean) {
                        args[6] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 3 Hooked: com.x.ui.common.p3.a (Legacy Bottom Bar Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.p3.a", t)
        }
    }
```

---

## 2. Backdrop & Blur Pipeline Specification (Compose AGSL & PixelCopy)

### 2.1 The AGSL / RuntimeShader Barrier
When `sourceView.draw(softwareCanvas)` runs on Twitter's Compose view tree, Skia attempts to draw `HazeNode` components containing `android.graphics.RuntimeShader`. Android does not permit rendering `RuntimeShader` or `RenderEffect` onto a CPU/software `Canvas`. It immediately throws `java.lang.IllegalArgumentException: Software rendering doesn't support RuntimeShader`.

Swallowing this exception left `capturedFrame` as `null`. When `LiquidNavigationBar` invoked `drawBackdrop(...)`:
```kotlin
val frame = capturedFrame ?: return // Returns immediately!
```
The backdrop draw phase aborted, leaving zero source bitmap for blur or refraction.

### 2.2 Architectural Resolution: Window-Level Hardware PixelCopy
`android.view.PixelCopy.request(window, srcRect, dstBitmap, listener, handler)` operates at the GPU framebuffer layer (SurfaceFlinger / HWUI). Shaders and AGSL effects have already been rasterized by the GPU into the display buffer. Therefore, `PixelCopy` succeeds 100% of the time, completely immune to `RuntimeShader` restrictions.

### 2.3 Implementation Details in `LiquidGlassViewBackdrop.kt`

1. **Window Resolution:**
   Add an optional `window: Window?` parameter to `LiquidGlassViewBackdrop` constructor. If not supplied, automatically resolve it via:
   ```kotlin
   private val effectiveWindow: Window?
       get() = explicitWindow
           ?: (sourceView.context as? Activity)?.window
           ?: (sourceView.rootView.context as? Activity)?.window
   ```
2. **Dual-Mode Fallback & Auto-Detection:**
   Add a boolean flag `var hasSoftwareRenderingFailed: Boolean = false`.
   In `captureOncePerFrame()`:
   - If `hasSoftwareRenderingFailed` or `enablePixelCopy` is true: bypass software drawing and directly call `requestHardwareWindowCopy(currentRegion, now)`.
   - If software drawing is attempted and catches `IllegalArgumentException`: immediately set `hasSoftwareRenderingFailed = true` and call `requestHardwareWindowCopy(currentRegion, now)`.
3. **Double-Buffering & Throttling:**
   - Double-buffered bitmaps (`front` / `back`) ensure no tearing while Compose draws `front`.
   - In-flight guard: `@Volatile private var isWindowCopying = false`. If a copy is already pending on `bgHandler`, skip subsequent ticks until it finishes.
   - Throttle interval: `minCaptureIntervalNanos = 33_333_333L` (~30 Hz) or `16_666_667L` (~60 Hz). A 33ms interval guarantees smooth backdrop blur while avoiding GPU bus saturation.
4. **Coordinate Mapping:**
   `srcRect` in Window coordinates:
   ```kotlin
   val decor = win.decorView
   val srcLeft = currentRegion.left.toInt().coerceIn(0, decor.width)
   val srcTop = currentRegion.top.toInt().coerceIn(0, decor.height)
   val srcRight = (currentRegion.left + currentRegion.width).toInt().coerceIn(0, decor.width)
   val srcBottom = (currentRegion.top + currentRegion.height).toInt().coerceIn(0, decor.height)
   val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
   ```
5. **Updating `capturedFrame`:**
   When `PixelCopy` reports `PixelCopy.SUCCESS`:
   ```kotlin
   val previousFront = front
   front = target
   back = previousFront

   capturedFrame = LiquidGlassCapturedFrame(
       bitmap = target,
       originInWindow = Offset(currentRegion.left, currentRegion.top),
   )
   lastCapturedFrame = frameNumber
   lastCaptureNanos = now
   ```

### 2.4 Updates in `LiquidNavigationBar.kt` and `TwitterNavBarHost.kt`

- In `LiquidNavigationBar.kt`:
  Pass `window: Window? = (sourceView.context as? Activity)?.window` into `LiquidGlassViewBackdrop(sourceView, window)`.
- In `TwitterNavBarHost.kt`:
  - Pass `enablePixelCopy = true` to `LiquidNavigationBar(...)`.
  - Pass `window: Window? = (backdropSource.context as? Activity)?.window` to `LiquidNavigationBar(...)`.
- In `TwitterHook.kt`:
  In `attachNavBar(activity: Activity)`:
  - Wire `backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView`.
  - Pass `window = activity.window` to `TwitterNavBarHost`.

---

## 3. Center Tab (Grok) Authentic Tab Icons Specification

### 3.1 Smali & Resource Evidence
From Twitter `12.46.1-prod.01` decompilation:
- Design Enum (`smali_classes7/com/x/jetfuel/element/d.smali`):
  `XAI_GROK_TAB` and `XAI_GROK_TAB_STROKE`.
- Icon Table (`smali_classes7/com/x/icons/a.smali`):
  - Inactive (Stroke): `0x7f080816` -> `ic_vector_xai_grok_tab_stroke`
  - Active (Filled): `0x7f080815` -> `ic_vector_xai_grok_tab`
- Vector Geometry:
  Authentic 32dp vector containing the official slanted Grok slash symbol with "GROK" text integrated at the bottom.

### 3.2 Update in `TwitterNavBarHost.kt`

Update `TWITTER_TAB_SPECS`:
```kotlin
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
    // Tab 2: Authentic Grok Tab
    TwitterTabSpec(
        inactiveName = "ic_vector_xai_grok_tab_stroke",
        inactiveStaticId = 0x7f080816,
        activeName = "ic_vector_xai_grok_tab",
        activeStaticId = 0x7f080815
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
```

Update `loadTwitterHostDrawable` fallback aliases:
```kotlin
val fallbackNames = when (resName) {
    "ic_vector_chat" -> listOf("ic_vector_messages")
    "ic_vector_chat_stroke" -> listOf("ic_vector_messages_stroke")
    "ic_vector_xai_grok_tab_stroke" -> listOf(
        "ic_vector_grok_stroke",
        "ic_vector_grok_icon",
        "ic_vector_grok_shortcut",
        "ic_vector_grok_bot_logo"
    )
    "ic_vector_xai_grok_tab" -> listOf(
        "ic_vector_grok_icon_filled",
        "ic_vector_grok_logo"
    )
    else -> emptyList()
}
```

---

## 4. Complete Code Modification Blueprint

### File 1: `dev/hyperios/app/nav/TwitterHook.kt`
1. Replace `hookMultiLayerSuppression` with the array-safe implementation:
   - For `k0Class` method `j`: `val args = chain.args.toTypedArray(); args[6] = true; chain.proceed(args)`.
   - For `glassClass` method `e`: `val args = chain.args.toTypedArray(); args[3] = true; chain.proceed(args)`.
   - For `glassClass` method `c`: `val args = chain.args.toTypedArray(); if (args.size >= 5 && args[4] is Boolean) args[4] = true; chain.proceed(args)`.
   - For `p3Class` method `a`: `val args = chain.args.toTypedArray(); args[6] = true; chain.proceed(args)`.
2. In `attachNavBar(activity: Activity)`:
   - Pass `window = activity.window` to `TwitterNavBarHost`.

### File 2: `dev/hyperios/app/nav/TwitterNavBarHost.kt`
1. Add `window: Window? = (backdropSource.context as? Activity)?.window` parameter to `TwitterNavBarHost` composable and view constructor.
2. In `TWITTER_TAB_SPECS[2]`, set `inactiveName = "ic_vector_xai_grok_tab_stroke"`, `inactiveStaticId = 0x7f080816`, `activeName = "ic_vector_xai_grok_tab"`, `activeStaticId = 0x7f080815`.
3. In `loadTwitterHostDrawable`, add `ic_vector_grok_stroke` and `ic_vector_grok_icon_filled` as fallbacks.
4. In `LiquidNavigationBar(...)`, pass:
   - `enablePixelCopy = true`
   - `window = window`

### File 3: `dev/hyperios/app/nav/LiquidNavigationBar.kt`
1. Add `window: android.view.Window? = (sourceView.context as? Activity)?.window ?: (sourceView.rootView.context as? Activity)?.window` parameter to `LiquidNavigationBar`.
2. Update `remember(sourceView, window) { LiquidGlassViewBackdrop(sourceView, window) }`.

### File 4: `dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt`
1. Update constructor:
   ```kotlin
   internal class LiquidGlassViewBackdrop(
       private val sourceView: View,
       private val explicitWindow: Window? = null,
   ) : Backdrop
   ```
2. Add `effectiveWindow` property:
   ```kotlin
   private val effectiveWindow: Window?
       get() = explicitWindow
           ?: (sourceView.context as? Activity)?.window
           ?: (sourceView.rootView.context as? Activity)?.window
   ```
3. Add `private var hasSoftwareRenderingFailed: Boolean = false` and `private var isWindowCopying: Boolean = false`.
4. Implement `requestHardwareWindowCopy(currentRegion: RectFState, now: Long)`.
5. In `captureOncePerFrame()`:
   - If `hasSoftwareRenderingFailed || enablePixelCopy`: call `requestHardwareWindowCopy(currentRegion, now)` and return.
   - Wrap `sourceView.draw(canvas)` in `try { ... } catch (e: IllegalArgumentException) { hasSoftwareRenderingFailed = true; requestHardwareWindowCopy(currentRegion, now) }`.

---

## 5. End-to-End Delivery & Verification Flow

Per `AGENT_RULES.md`, local APK compilation is strictly prohibited. The Coder Subagent must follow this precise deployment routine:

### Step 1: Clean Git Commit & Push
```bash
cd /data/data/com.termux/files/home/hyperbounce-hos4
git status
git add app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt \
        app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt \
        app/src/main/java/dev/hyperios/app/nav/LiquidNavigationBar.kt \
        app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt
git commit -m "fix(twitter): resolve stock nav suppression, compose haze backdrop blur, and grok authentic icon"
git push origin hos4
```

### Step 2: GitHub Actions CI Monitoring
```bash
# Get active run ID
RUN_ID=$(gh run list --branch hos4 -L 1 --json databaseId -q '.[0].databaseId')
echo "Watching GitHub Actions Run ID: $RUN_ID"

# Watch run until completion
gh run watch "$RUN_ID"
```
If the build fails, view logs via `gh run view "$RUN_ID" --log-failed`, resolve errors, and push again.

### Step 3: Download Release APK Artifact
```bash
# Download artifact from successful run
gh run download "$RUN_ID" --dir /data/data/com.termux/files/home/artifacts
APK_PATH=$(find /data/data/com.termux/files/home/artifacts -name "*.apk" | head -n 1)
echo "Downloaded APK: $APK_PATH"
```

### Step 4: Root Installation on Device
```bash
su -c "pm install -r $APK_PATH"
```

### Step 5: Twitter Restart & LSPosed Hook Application
```bash
su -c "am force-stop com.twitter.android"
su -c "monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1"
```

### Step 6: Live Verification Checklist
Execute `su -c "logcat -d"` and grep for relevant tags:
1. **Zero Suppression Hook Crashes:**
   Verify no `UnsupportedOperationException` appears from `TwitterHook`. Confirm logcat contains:
   - `Layer 1 Hooked: com.x.main.k0.j (Root Shell Suppression)`
   - `Layer 2 Hooked: com.x.ui.common.glass.c.e (Glass Container Suppression)`
   - `Layer 3 Hooked: com.x.ui.common.p3.a (Legacy Bottom Bar Suppression)`
2. **Stock Bar Invisible:**
   Verify the stock bottom bar does not render. Only HyperBounce's floating bar is visible.
3. **Grok Icon Authentic:**
   Verify center tab displays the authentic XAI Grok tab icon (stylized slash with "GROK" text) in both inactive and active states, with no clock/gauge appearance.
4. **Backdrop & Blur Active:**
   Verify `LiquidGlassCapturedFrame` is continuously populated via hardware `PixelCopy`, Kyant's backdrop engine renders real-time liquid blur and lens refraction underneath the floating bar as timelines scroll.
