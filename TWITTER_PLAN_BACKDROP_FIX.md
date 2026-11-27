# Architectural Plan: Twitter (X) Backdrop Architecture & Zero-Recursion Isolation

**Target Document:** `TWITTER_PLAN_BACKDROP_FIX.md`  
**Author:** Architectural Planner Subagent (HyperBounce HOS 4)  
**Target Package:** `com.twitter.android` (X / Twitter Android 12.46.1+)  
**Branch:** `hos4`  
**Date:** November 27, 2026  
**Status:** **READY FOR IMPLEMENTATION**  

---

## 1. Executive Summary & Problem Formulation

### 1.1 Current State
Stock navigation bar neutralization in X (Twitter) is **100% complete and verified**:
- Root Navigation Shell hook (`com.x.main.k0.j` forcing `z4 = true`) cleanly suppresses both legacy stock bar (`com.x.ui.common.p3.a`) and modern Compose glass bar (`com.x.ui.common.glass.c.e` / `c.c`).
- Content smoothly extends to the bottom screen edge without dead spacer insets or touch interceptor leaks.

However, the floating liquid navigation bar (`HyperFloatingBar_Twitter`) suffers from a critical visual flaw:
- **Self-Refraction Recursion (Infinite Mirror Feedback Loop):** The floating navigation bar refracts and blurs **its own internal composables** (the active cyan selection pill, white tab icons, and pill container outline) rather than purely blurring and refracting the underlying Twitter timeline/feed. Ghost silhouettes of the search icon, grok tab, bell, and DM bubble appear burned into the glass lens.

### 1.2 The Root Cause
In commit `734265034bf15a50d0b4dcb700a6716489cda45d` (`TwitterNavBarHost.kt`), the following configuration was committed:
```kotlin
LiquidNavigationBar(
    ...
    sourceView = backdropSource,
    enablePixelCopy = true,         // <-- ROOT CAUSE #1
    enableWindowPixelCopy = true,   // <-- ROOT CAUSE #2
    window = window,
    ...
)
```
Setting `enableWindowPixelCopy = true` forced `LiquidGlassViewBackdrop.kt` on every single frame callback to execute:
```kotlin
PixelCopy.request(window, srcRect, target, ...)
```
Because `TwitterNavBarHost` is attached as a direct overlay child of `activity.window.decorView`, the floating bar physically exists on the window surface buffer. `PixelCopy.request(window)` captures the composited GPU output of the **entire window** (including the floating bar). `LiquidNavigationBar` then feeds this captured bitmap into Kyant's backdrop refraction shaders, rendering the blurred bar back onto the screen, which is recaptured on the next frame—forming a recursive optical feedback loop.

### 1.3 The Solution Principle (The Proven Facebook Architecture)
In `FacebookNavBarHost.kt` and `FacebookHook.kt`, clean backdrop refraction with zero self-refraction is achieved via **Sibling View Isolation**:
1. `backdropSource` is assigned strictly to `activity.findViewById(android.R.id.content)`.
2. `ComposeView` (the floating bar) is added as a child of `activity.window.decorView`.
3. In Android's view tree, `android.R.id.content` and `ComposeView` are **siblings** under `decorView`.
4. `LiquidNavigationBar` captures via `sourceView.draw(canvas)`. Because `sourceView` is `android.R.id.content`, the traversal draws **strictly the host app's feed, tweets, images, and profile UI**. `ComposeView` is never traversed.
5. **Self-refraction is mathematically and topologically impossible.**

---

## 2. Geometric & Topological View Hierarchy Proof

```
DecorView (Window Root Surface)
  │
  ├── LinearLayout
  │     └── FrameLayout
  │           └── FitWindowsLinearLayout (app:id/action_bar_root)
  │                 └── ContentFrameLayout (android.R.id.content)  <--- backdropSource (sourceView)
  │                       └── ComposeView
  │                             └── AndroidComposeView
  │                                   └── Twitter Timeline (Tweets, Media, Text, Cards)
  │
  └── ComposeView (tag = "HyperFloatingBar_Twitter")              <--- Overlay Sibling
        └── AndroidComposeView
              └── LiquidNavigationBar (Kyant Lens Shader + Floating Pill)
```

### Topological Isolation Proof:
- Let $T_{content}$ be the sub-tree rooted at `android.R.id.content`.
- Let $V_{bar}$ be the `ComposeView` containing `LiquidNavigationBar`.
- $V_{bar} \notin T_{content}$ since $V_{bar}$ is attached directly to `DecorView` and is a sibling of the ancestor chain of `android.R.id.content`.
- When `sourceView.draw(canvas)` executes where `sourceView == android.R.id.content`:
  $$\text{RenderedViews} = \text{Subtree}(sourceView) = T_{content}$$
  $$V_{bar} \cap T_{content} = \emptyset \implies \text{PixelBuffer}(T_{content}) \cap \text{PixelBuffer}(V_{bar}) = \emptyset$$
- Therefore, the backdrop buffer contains **0% floating bar pixels** and **100% Twitter feed pixels**.

---

## 3. Targeted Source Code Modifications

### 3.1 Tier 2: `TwitterNavBarHost.kt`
**File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt`

#### Changes:
1. In `@Composable fun TwitterNavBarHost`, remove `enablePixelCopy = true`, `enableWindowPixelCopy = true`, and `window = window`.
2. Allow `LiquidNavigationBar` to use its default arguments (`enablePixelCopy = false`, `enableWindowPixelCopy = false`).
3. Clean up the `window: Window?` parameter from `TwitterNavBarHost` composable and class (or leave it defaulted/unused) so caller code remains clean and decoupled from window buffer capture.

#### Exact Diff Specification:
```diff
--- a/app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt
+++ b/app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt
@@ -172,7 +172,6 @@ class TwitterNavBarHost @JvmOverloads constructor(
     context: Context,
     val prefs: HyperBouncePrefs,
     val backdropSource: View = (context as? Activity)?.findViewById(android.R.id.content) ?: View(context),
-    val window: Window? = (context as? Activity)?.window,
     attrs: AttributeSet? = null,
     defStyleAttr: Int = 0
 ) : FrameLayout(context, attrs, defStyleAttr) {
@@ -192,8 +191,7 @@ class TwitterNavBarHost @JvmOverloads constructor(
             setContent {
                 TwitterNavBarHost(
                     backdropSource = backdropSource,
-                    hyperBouncePrefs = prefs,
-                    window = window
+                    hyperBouncePrefs = prefs
                 )
             }
         }
@@ -204,8 +202,7 @@ class TwitterNavBarHost @JvmOverloads constructor(
 @Composable
 fun TwitterNavBarHost(
     backdropSource: View,
-    hyperBouncePrefs: HyperBouncePrefs,
-    window: Window? = (backdropSource.context as? Activity)?.window
+    hyperBouncePrefs: HyperBouncePrefs
 ) {
     val live = rememberLiveNavBarSettings(hyperBouncePrefs, "twitter")
     val isDark = when (live.themeMode) {
@@ -256,9 +253,6 @@ fun TwitterNavBarHost(
             TwitterHook.dispatchTabClick(index)
         },
         sourceView = backdropSource,
-        enablePixelCopy = true,
-        enableWindowPixelCopy = true,
-        window = window,
         forceLightTheme = live.themeMode == "Light",
         forceDarkTheme = live.themeMode == "Dark",
         containerAlpha = live.containerOpacity,
```

---

### 3.2 Tier 3: `TwitterHook.kt`
**File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt`

#### Changes:
1. Verify `backdropSource` acquisition:
   ```kotlin
   val backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView
   ```
2. Remove `window = activity.window` argument from `TwitterNavBarHost(...)` call inside `ComposeView.setContent`.
3. Confirm `composeView` is attached strictly to `decorView` outside `android.R.id.content`.

#### Exact Diff Specification:
```diff
--- a/app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt
+++ b/app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt
@@ -191,8 +191,7 @@ object TwitterHook {
                     setContent {
                         TwitterNavBarHost(
                             backdropSource = backdropSource,
-                            hyperBouncePrefs = prefs,
-                            window = activity.window
+                            hyperBouncePrefs = prefs
                         )
                     }
                 }
```

---

### 3.3 Tier 1: `LiquidGlassViewBackdrop.kt` (Safety Hardening & Fallback Isolation)
**File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt`

#### Problem in Current Implementation:
1. **Unconditional Window Copy on Failure:**
   Lines 130 and 184:
   ```kotlin
   if (hasSoftwareRenderingFailed || enableWindowPixelCopy) {
       requestHardwareWindowCopy(currentRegion, now)
       return
   }
   ```
   If any transient software drawing exception occurs, `hasSoftwareRenderingFailed` is latched permanently to `true`, forcing window `PixelCopy` even if `enableWindowPixelCopy` was explicitly `false`!
2. **Logcat Spam on Window Surface Detach:**
   When an activity is backgrounded or minimized, `PixelCopy.request(win, ...)` fails with:
   `PixelCopy window request error: Window doesn't have a backing surface!`
   flooding logcat with thousands of lines.
3. **Surface State Clean-Up:**
   Ensure clean release of buffers and cancellation of pending copies when stopped/disposed.

#### Hardening Rules:
1. `requestHardwareWindowCopy()` must **ONLY** be invoked if `enableWindowPixelCopy == true`. It must NEVER default on, and software rendering failure must NOT hijack execution into window copy unless explicitly permitted.
2. If `sourceView.draw(canvas)` throws (e.g., transient `IllegalArgumentException` or `NullPointerException`), log a throttled warning, keep the previous valid frame (`front`), and retry software rendering on subsequent frames instead of latching into a broken state.
3. In `requestHardwareWindowCopy()`, guard against surface deallocation:
   ```kotlin
   val win = effectiveWindow ?: return
   val decor = win.decorView ?: return
   if (!decor.isAttachedToWindow || decor.windowToken == null) return
   ```

#### Exact Diff Specification:
```diff
--- a/app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt
+++ b/app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt
@@ -127,7 +127,7 @@ internal class LiquidGlassViewBackdrop(
         val currentRegion = region ?: return
         if (currentRegion.width <= 0 || currentRegion.height <= 0) return
 
-        if (hasSoftwareRenderingFailed || enableWindowPixelCopy) {
+        if (enableWindowPixelCopy) {
             requestHardwareWindowCopy(currentRegion, now)
             return
         }
@@ -183,8 +183,11 @@ internal class LiquidGlassViewBackdrop(
             lastCaptureNanos = now
         } catch (e: IllegalArgumentException) {
             // Software rendering does not support RuntimeShader (e.g. if sourceView contains RuntimeShader / AGSL)
-            hasSoftwareRenderingFailed = true
-            requestHardwareWindowCopy(currentRegion, now)
+            if (enableWindowPixelCopy) {
+                requestHardwareWindowCopy(currentRegion, now)
+            } else {
+                // Fallback: retain previous valid frame without recursive window capture
+            }
         } catch (t: Throwable) {
             Log.e(
                 "HyperBounce-Backdrop",
@@ -197,6 +200,7 @@ internal class LiquidGlassViewBackdrop(
         val win = effectiveWindow ?: return
         val decor = win.decorView ?: return
         val decorWidth = decor.width
         val decorHeight = decor.height
-        if (decorWidth <= 0 || decorHeight <= 0) return
+        if (decorWidth <= 0 || decorHeight <= 0 || !decor.isAttachedToWindow || decor.windowToken == null) return
 
         if (isWindowCopying) return
```

---

## 4. End-to-End Delivery & Deployment Pipeline

In strict compliance with `AGENT_RULES.md` (Section 2: *No Local APK Compilation*, Section 4: *Mandatory Deployment Routine*):

```
┌─────────────────┐       ┌─────────────────┐       ┌──────────────────────┐
│  Apply Changes  │ ───>  │  Commit & Push  │ ───>  │  GitHub Actions CI   │
│  (Coder Agent)  │       │   to 'hos4'     │       │   Remote Build       │
└─────────────────┘       └─────────────────┘       └──────────────────────┘
                                                                │
                                                                ▼
┌─────────────────┐       ┌─────────────────┐       ┌──────────────────────┐
│  Live App Test  │ <───  │  Root Install   │ <───  │ Download Release APK │
│  & Verification │       │  via Termux su  │       │  from CI Artifacts   │
└─────────────────┘       └─────────────────┘       └──────────────────────┘
```

### 4.1 Step 1: Code Modifications
The Coder subagent edits:
1. `app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt`
2. `app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt`
3. `app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt`

### 4.2 Step 2: Git Commit & Remote Push
```bash
cd /data/data/com.termux/files/home/hyperbounce-hos4
git add app/src/main/java/dev/hyperios/app/nav/TwitterNavBarHost.kt \
        app/src/main/java/dev/hyperios/app/nav/TwitterHook.kt \
        app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt \
        TWITTER_PLAN_BACKDROP_FIX.md
git commit -m "fix(twitter): eliminate backdrop recursion loop via sibling view isolation"
git push origin hos4
```

### 4.3 Step 3: CI Build Monitoring & Artifact Download
```bash
# Monitor the remote build workflow
gh run list --branch hos4 --limit 1
gh run watch <run-id>

# Once build succeeds, download the artifact
gh run download <run-id> -n app-release -D /data/data/com.termux/files/home/hyperbounce-hos4/build-output/
```

### 4.4 Step 4: Device Installation & Application Restart
```bash
# Install the updated HyperBounce module via root
su -c "pm install -r /data/data/com.termux/files/home/hyperbounce-hos4/build-output/app-release.apk"

# Force stop and re-launch Twitter (X)
su -c "am force-stop com.twitter.android && monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1"
```

### 4.5 Step 5: Live Verification Criteria
1. **Backdrop Blur & Refraction Quality:**
   - Scroll through Twitter timeline. Tweet cards, user avatars, and media passing beneath the floating navigation bar must blur smoothly.
   - The glass refraction must distort the underlying feed text/images only.
2. **Zero Self-Refraction:**
   - Active Tab (Tab 0: Home with cyan pill indicator): Inspect the glass lens around the indicator. There must be **ZERO** cyan ghost halo or smear inside the glass.
   - Other Tabs (Search, Grok, Bell, DM): Tab icons must remain crisp stencils on top of the bar; no ghost icons or mirrored outlines may appear inside the blurred backdrop.
3. **Logcat Stability:**
   - Run `logcat -d | grep "HyperBounce-Backdrop"`.
   - Verify zero occurrences of:
     `PixelCopy window request error: Window doesn't have a backing surface!`
   - Verify zero crashes or ANRs during app pause, resume, and rapid scrolling.

---

## 5. Architectural Checklist for Coder Subagent

| Step | Component | Action | Verification |
| :--- | :--- | :--- | :--- |
| 1 | `TwitterNavBarHost.kt` | Remove `enablePixelCopy = true`, `enableWindowPixelCopy = true`, `window = window` | Defaults to `false`, eliminating whole-window capture |
| 2 | `TwitterHook.kt` | Ensure `backdropSource` is `android.R.id.content` and `composeView` is attached to `decorView` | Sibling isolation verified |
| 3 | `LiquidGlassViewBackdrop.kt` | Guard `requestHardwareWindowCopy` behind `enableWindowPixelCopy` and check `windowToken != null` | Safety fallback & logcat silence verified |
| 4 | GitHub CI | Push commit to `hos4`, verify CI pass, install APK via `su -c "pm install -r"` | Successful deployment |
| 5 | Live Testing | Restart Twitter, inspect screenshot/device screen for clean feed blur | Zero self-refraction verified |

---
*End of Architectural Plan.*
