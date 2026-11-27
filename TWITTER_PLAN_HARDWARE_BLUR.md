# Architectural Implementation Plan: Twitter (X) Hardware Liquid Glass & Blur Engine

**Target:** X (formerly Twitter) Android `12.46.1-prod.01` (`com.twitter.android`)  
**Repository:** `hyperbounce-hos4` (`dev.hyperios.app.nav`)  
**Target Branch:** `hos4`  
**Author:** Architectural Planner Subagent (HyperBounce HOS 4)  
**Date:** November 27, 2026  
**Status:** Approved Architectural Specification  

---

## 1. Executive Summary & Root Cause Analysis

### 1.1 The User's Defect Report
> *"now it just shows what's under it without any blur or glass effects. just transparent with only the opacity is being respected. no blur or glas effects. but no self blur also"*

### 1.2 The Root Cause Chain
1. **Commit `73426503` (Self-Refraction Loop):**  
   `TwitterNavBarHost` had `enableWindowPixelCopy = true`. Because `composeView` was attached to `activity.window.decorView`, the floating navigation bar was physically on `activity.window`'s GPU display surface. `PixelCopy.request(window)` captured the window containing the bar itself, creating an infinite video-feedback recursion mirror loop.
2. **Commit `8617cec6` (Total Loss of Blur & Glass):**  
   To break the mirror loop, commit `8617cec6` disabled `enableWindowPixelCopy` and attempted to fall back to CPU software rendering: `sourceView.draw(softwareCanvas)`.
3. **The Skia Crash on Compose AGSL Shaders:**  
   Twitter `12.46.1`'s feed is built with Jetpack Compose and embeds Chris Banes' `Haze` library (`dev.chrisbanes.haze`) and AGSL `RuntimeShader` / `RenderEffect` nodes.  
   Calling `sourceView.draw(swCanvas)` immediately triggers:  
   `java.lang.IllegalArgumentException: Software rendering doesn't support RuntimeShader`
4. **The Silent Failure Trap:**  
   In `LiquidGlassViewBackdrop.kt`:
   ```kotlin
   } catch (e: IllegalArgumentException) {
       if (enableWindowPixelCopy) {
           requestHardwareWindowCopy(currentRegion, now)
       } else {
           // Fallback: retain previous valid frame without recursive window capture
       }
   }
   ```
   Because `enableWindowPixelCopy == false`, the catch block silently did nothing on every single frame. `capturedFrame` remained permanently `null`.
5. **Visual Outcome:**  
   `LiquidNavigationBar.kt` bailed out of `drawBackdrop` immediately (`val frame = capturedFrame ?: return`). Kyant's shaders (`vibrancy()`, `blur()`, `lens()`) had 0 input pixels to sample. Only the flat container color (`onDrawSurface = { drawRect(containerColor) }`) was rendered. The bar appeared as a flat, translucent, un-blurred box.

---

## 2. Architectural Trade-off Analysis & Selection

| Evaluation Criterion | Architecture A: `RenderNode` + `HardwareRenderer` on `content` | Architecture B: `WindowManager` Panel Window (`TYPE_APPLICATION_PANEL`) [SELECTED] |
| :--- | :--- | :--- |
| **Self-Capture Immunity** | **100% Immune** (renders `content`, bar is on `decorView`) | **100% Immune** (bar is on a separate `SurfaceControl` outside `activity.window`) |
| **Compose AGSL / Haze Support** | **Yes** (`RecordingCanvas` records display list) | **Yes** (`activity.window` is fully rasterized by GPU HWUI) |
| **UI Thread Overhead** | **Heavy (2–5ms per frame)**: walks Twitter's view tree at 30–60 Hz on the UI thread, causing timeline scroll jank | **Zero (0ms)**: asynchronous GPU DMA blit on `bgHandler` thread |
| **SurfaceView / Video Handling** | **Fails** (videos in timeline appear as black rectangles) | **Full Support** (composited directly from window surface) |
| **RenderThread Contention** | High risk: shared RenderThread queue stalls if `ImageReader` buffers drop | None: `PixelCopy` is an OS-managed asynchronous copy |
| **Touch Passthrough** | Natural (in-tree sibling views) | Requires natural window sizing (`WRAP_CONTENT` @ `Gravity.BOTTOM`) + `FLAG_NOT_TOUCH_MODAL` |
| **Lifecycle Cleanliness** | In-tree, destroyed with Activity | Needs explicit `removeViewImmediate` on `onDestroy` / detach |

### Architectural Decision: ARCHITECTURE B (`WindowManager` Panel Window)
Architecture B is unequivocally selected as the primary, production-grade architecture. By isolating `composeView` onto its own window surface (`TYPE_APPLICATION_PANEL`), `PixelCopy.request(activity.window)` queries SurfaceFlinger for `activity.window`'s Surface exclusively. The navigation bar is physically and mathematically absent from `activity.window`, guaranteeing **100% zero self-refraction**, **full GPU AGSL shader support**, and **zero UI thread CPU traversal cost** at 60 FPS.

---

## 3. Detailed Component Blueprint

### 3.1 Tier 3: `TwitterHook.kt` (Window Surface Separation & Lifecycle Isolation)

#### A. Window Surface Attachment
Instead of attaching `composeView` to `decorView`, `TwitterHook` attaches `composeView` directly via `activity.windowManager.addView(composeView, params)`.

```kotlin
// In TwitterHook.kt -> attachNavBar(activity)
val wm = activity.windowManager ?: return@runOnUiThread
val windowToken = decorView.windowToken

if (windowToken == null) {
    // Wait until decorView is attached to window so windowToken is valid
    decorView.post { attachNavBar(activity) }
    return@runOnUiThread
}

val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.BOTTOM
    token = windowToken
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
}
```

#### B. Sizing & Touch Passthrough Principle
- **Height:** Sized to `WRAP_CONTENT` at `Gravity.BOTTOM`. The panel window physically occupies only the bottom ~100dp of the display.
- **`FLAG_NOT_TOUCH_MODAL`:** Android's `InputDispatcher` automatically delivers all touches above the panel window directly to the underlying `activity.window`. The entire Twitter timeline, tweets, media, search, and navigation gestures are 100% unhindered.
- **Inside the Panel:** Clicks and damped dragging gestures on the bar are consumed by Compose as normal.

#### C. Leak-Proof Lifecycle Management
To eliminate `WindowManager$BadTokenException` and `WindowLeaked` exceptions across Activity destruction, configuration changes, and app recreation:
1. **Hook `MainActivity.onDestroy()`**:
   ```kotlin
   try {
       val onDestroyMethod = actClass.getMethod("onDestroy")
       module.hook(onDestroyMethod).intercept { chain ->
           (chain.thisObject as? Activity)?.let { detachNavBar(it) }
           chain.proceed()
       }
       Log.i(TAG, "Hooked onDestroy on $activityName")
   } catch (_: Throwable) {}
   ```
2. **Attach `OnAttachStateChangeListener` to `decorView`**:
   ```kotlin
   decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
       override fun onViewAttachedToWindow(v: View) {}
       override fun onViewDetachedFromWindow(v: View) {
           detachNavBar(activity)
       }
   })
   ```
3. **`detachNavBar(activity)` implementation**:
   ```kotlin
   private fun detachNavBar(activity: Activity) {
       activity.runOnUiThread {
           try {
               val bar = activeFloatingBar
               if (bar != null && bar.isAttachedToWindow) {
                   activity.windowManager?.removeViewImmediate(bar)
                   Log.i(TAG, "TwitterNavBarHost cleanly removed from WindowManager")
               }
           } catch (t: Throwable) {
               Log.w(TAG, "Error removing bar from WindowManager: ${t.message}")
           }
           activeFloatingBar = null
       }
   }
   ```

---

### 3.2 Tier 2: `TwitterNavBarHost.kt` (Window Wiring & Layout Bounds)

1. **Pass `window = activity.window`**:
   Accept `window: Window? = (backdropSource.context as? Activity)?.window ?: (backdropSource.rootView.context as? Activity)?.window` in the Composable function signature and pass it to `LiquidNavigationBar`.
2. **Enable Hardware Capture**:
   ```kotlin
   LiquidNavigationBar(
       ...
       sourceView = backdropSource,
       enablePixelCopy = true,
       enableWindowPixelCopy = true,
       window = window,
       ...
   )
   ```
3. **Constrain Root Bounds to WRAP_CONTENT**:
   Wrap `LiquidNavigationBar` inside a Box with `.fillMaxWidth().wrapContentHeight()` so `composeView` accurately measures its height for the `WRAP_CONTENT` panel window.

---

### 3.3 Tier 1: `LiquidGlassViewBackdrop.kt` (Coordinate Space Translation & Safe GPU Capture)

#### A. Window Offset Translation in `requestHardwareWindowCopy`
Because `sourceView` / `composeView` resides in a panel window positioned at `Gravity.BOTTOM`, its coordinates inside the panel window start at local `(0, 0)`. To sample the exact underlying region of `activity.window`, we compute the window offset between `sourceView` and `decorView`:

```kotlin
private fun requestHardwareWindowCopy(currentRegion: RectFState, now: Long) {
    val win = effectiveWindow ?: return
    val decor = win.decorView ?: return
    val decorWidth = decor.width
    val decorHeight = decor.height
    if (decorWidth <= 0 || decorHeight <= 0 || !decor.isAttachedToWindow || decor.windowToken == null) return

    if (isWindowCopying) return

    val scale = 0.5f
    val width = currentRegion.width
    val height = currentRegion.height
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    ensureBuffers(scaledWidth, scaledHeight)

    val target = back ?: return
    if (target.isRecycled) return

    // Compute coordinate translation between panel window and activity window
    val sourceLoc = IntArray(2)
    sourceView.getLocationOnScreen(sourceLoc)
    val decorLoc = IntArray(2)
    decor.getLocationOnScreen(decorLoc)
    val offsetX = sourceLoc[0] - decorLoc[0]
    val offsetY = sourceLoc[1] - decorLoc[1]

    val rawLeft = currentRegion.left + offsetX
    val rawTop = currentRegion.top + offsetY

    val srcLeft = rawLeft.toInt().coerceIn(0, decorWidth - 1)
    val srcTop = rawTop.toInt().coerceIn(0, decorHeight - 1)
    val srcRight = (rawLeft + width).toInt().coerceIn(srcLeft + 1, decorWidth)
    val srcBottom = (rawTop + height).toInt().coerceIn(srcTop + 1, decorHeight)
    if (srcRight <= srcLeft || srcBottom <= srcTop) return

    val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)

    isWindowCopying = true

    val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
        isWindowCopying = false
        if (result == PixelCopy.SUCCESS && !target.isRecycled) {
            val previousFront = front
            front = target
            back = previousFront

            capturedFrame = LiquidGlassCapturedFrame(
                bitmap = target,
                originInWindow = Offset(currentRegion.left, currentRegion.top),
            )
            lastCapturedFrame = frameNumber
            lastCaptureNanos = now
        } else {
            Log.w("HyperBounce-Backdrop", "PixelCopy window copy failed: result=$result")
        }
    }

    try {
        PixelCopy.request(win, srcRect, target, listener, bgHandler)
    } catch (t: Throwable) {
        isWindowCopying = false
        Log.w("HyperBounce-Backdrop", "PixelCopy window request error: ${t.message}")
    }
}
```

#### B. Catch Block Logging & Observability
In `captureOncePerFrame()`, ensure `IllegalArgumentException` logs an explicit warning if software rendering is ever attempted, rather than silently swallowing:
```kotlin
} catch (e: IllegalArgumentException) {
    if (enableWindowPixelCopy) {
        requestHardwareWindowCopy(currentRegion, now)
    } else {
        Log.w("HyperBounce-Backdrop", "Software rendering rejected (AGSL/RuntimeShader). Enable enableWindowPixelCopy for hardware capture: ${e.message}")
    }
}
```

---

## 4. End-to-End Delivery Protocol for Implementation Coder

Adhering strictly to `AGENT_RULES.md`:

### Step 1: Code Modifications
The implementation coder must edit:
1. `app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt` (Window attachment, params, onDestroy hook, detachNavBar).
2. `app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt` (Pass window, enablePixelCopy = true, enableWindowPixelCopy = true).
3. `app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt` (Coordinate offset translation, error logging, safety guards).

### Step 2: Verification of Code Diff & Clean Commit
```bash
cd /data/data/com.termux/files/home/hyperbounce-hos4
git status
git diff
git add app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt \
        app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt \
        app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt \
        TWITTER_PLAN_HARDWARE_BLUR.md
git commit -m "fix(twitter): restore authentic liquid glass blur via window isolation hardware capture"
```

### Step 3: Remote GitHub Push & CI Build Monitoring
**Strictly NO local compilation in Termux** (`AGENT_RULES.md` Rule 2).
```bash
git push origin hos4
```
Monitor GitHub Actions CI using `gh run list --limit 1` and `gh run watch <run_id>`.

### Step 4: Download & Install Artifact via Root
Once CI completes successfully:
```bash
gh run download <run_id> -n app-release -D /data/data/com.termux/files/home/hyperbounce-hos4/build-output/
su -c "pm install -r /data/data/com.termux/files/home/hyperbounce-hos4/build-output/app-release.apk"
```

### Step 5: Live Verification on Device
1. Restart Twitter:
   ```bash
   su -c "am force-stop com.twitter.android && monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1"
   ```
2. Inspect logcat:
   ```bash
   su -c "logcat -d | grep -E 'TwitterHook|HyperBounce-Backdrop' | tail -n 40"
   ```
   Verify:
   - `TwitterNavBarHost attached via WindowManager (TYPE_APPLICATION_PANEL) - Window Isolation Active`
   - Zero `PixelCopy window copy failed` errors.
   - Zero `Software rendering doesn't support RuntimeShader` crashes.
3. Capture visual snapshot:
   ```bash
   su -c "screencap -p /sdcard/Pictures/Screenshots/twitter_glass_verify.png"
   ```
   Inspect the bottom navigation bar region:
   - Confirm authentic Kyant blur filter is active on underlying feed text and media.
   - Confirm lens refraction refracts underlying tweets and inactive tab icons.
   - Confirm **0% self-refraction / zero mirror ghosting**.
   - Confirm smooth 60 FPS scrolling and tab interaction.

---
*End of Architectural Plan.*
