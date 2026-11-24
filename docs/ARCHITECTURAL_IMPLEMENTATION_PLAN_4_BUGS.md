# Architectural Implementation Plan: HyperBounce 4-Bug Root Cause Resolution

**Author:** Architectural Planner Subagent  
**Date:** November 24, 2026  
**Target Branch:** `hos4`  
**Workspace:** `/data/data/com.termux/files/home/hyperbounce-hos4`  
**Status:** READY FOR IMPLEMENTATION

---

## Executive Summary

This document specifies the complete, production-grade architectural resolution for four critical defects in the HyperBounce (`hos4`) codebase:
1. **Preferences Sync Lifecycle Defect:** Desynchronization between `hyperbounce_settings.xml` and `/data/adb/lspd/config/modules_config.db`, caused by uncoordinated callsites, unreset `hasPendingPush` flags, and 300-key synchronous Binder dumps on the main thread.
2. **Reels Shifting Timing & Dimming Scrim:** Litho mounting race conditions on `RecyclerView.onChildAttachedToWindow`, fragile child indexing in Reel 2, and accidental shifting of the 300px dark bottom dimming scrim.
3. **Tab Icons Filled vs. Outline Architecture:** Icon contamination caused by `inactive ?: active` fallback logic on startup Tab 0, locking Tab 0 into a permanent filled state and preventing clean state transitions.
4. **Profile Tab & Hover Pill Styling:** Unwanted artificial sparkle overlay animation and border-only restrictions preventing proper active fill/accent styling on the Facebook profile/menu tab.

All fixes adhere strictly to `AGENT_RULES.md` (no local compilation, GitHub Actions CI/CD pipeline, root cause resolution with zero shallow workarounds, and non-regression guarantees across Instagram, WhatsApp, and YouTube).

---

## Module 1: Preferences Sync Lifecycle Defect

### 1.1 Root Cause Analysis
- **Symptom:** UI toggles in HyperBounce update `hyperbounce_settings.xml`, but the changes fail to reach the hook processes (stored in `/data/adb/lspd/config/modules_config.db` via `RemotePreferences`). Changes only take effect after a full app restart.
- **Architectural Flaw:**
  1. `syncRemote` in `SettingsRepository.kt` set `@Volatile var hasPendingPush: Boolean = true` when remote was unavailable or when `commit()` failed, but **never reset** `hasPendingPush = false` upon single-key write success (`success == true`).
  2. Because `hasPendingPush` was latched to `true` on process boot before `XposedService` connected, every subsequent preference change across all delegates (`PrefBooleanDelegate`, `PrefLongDelegate`, etc.) triggered:
     ```kotlin
     if (SettingsRepository.hasPendingPush) {
         SettingsRepository.flushPendingPush()
     }
     ```
  3. `flushPendingPush()` iterated through `localPrefs.all` (300+ keys) and invoked `editor.commit()` synchronously on the Android main thread, thrashing the Binder IPC buffer and causing UI stutter or dropped IPC transactions.
  4. Direct writes to `localPrefs` bypassing delegates were completely unmirrored.
  5. The only reason app restart "fixed" it was that `pushAllLocalToRemote()` ran unconditionally in `HyperBounceApp.onCreate()` / `SettingsRepository.init`.

### 1.2 Target Files & Signatures
- **Target File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/ui/state/SettingsRepository.kt`
- **Supporting File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/ui/state/XposedServiceBridge.kt`
- **Application Boot:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/HyperBounceApp.kt`

### 1.3 Architectural Design: Centralized Write-Through Mirror

```
+-------------------------------------------------------------------------+
|                              HyperBounce UI                             |
|  (Compose Toggles, Sliders, Dropdowns, Import/Export, Reset Defaults)   |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                  Local SharedPreferences (Source of Truth)              |
|                     `prefs.edit().put*(key, value)`                     |
+-------------------------------------------------------------------------+
                                    |
            OnSharedPreferenceChangeListener (Automatic Hook)
                                    |
                                    v
+-------------------------------------------------------------------------+
|                    PrefMirrorSyncManager (Thread-Safe)                  |
|                                                                         |
|   +-----------------------------------------------------------------+   |
|   |   Is XposedServiceBridge bound & RemotePreferences available?   |   |
|   +-----------------------------------------------------------------+   |
|                 /                                   \                   |
|           YES  /                                     \  NO              |
|               v                                       v                 |
|   +-----------------------+              +--------------------------+   |
|   | Async Single-Key Push |              | Enqueue to Dirty Queue   |   |
|   | (Single-Thread Pool)  |              | (ConcurrentLinkedQueue)  |   |
|   | remote.edit().put*()  |              +--------------------------+   |
|   +-----------------------+                           |                 |
|                                                       | onServiceBind() |
|                                                       v                 |
|                                          +--------------------------+   |
|                                          | Drain Dirty Queue Async  |   |
|                                          +--------------------------+   |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|           LSPosed SQLite DB (/data/adb/lspd/config/modules_config.db)   |
+-------------------------------------------------------------------------+
```

### 1.4 Step-by-Step Implementation Details

1. **Create `PrefMirrorSyncManager` in `SettingsRepository.kt`:**
   - Dedicated background worker using `Executors.newSingleThreadExecutor()` to prevent main-thread Binder blocking.
   - Maintain a thread-safe `ConcurrentLinkedQueue<String>` for dirty keys that were modified before the service bound.
   - Attach an `OnSharedPreferenceChangeListener` to `localPrefs` inside `SettingsRepository.init`:
     ```kotlin
     private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
         if (key == null || key in DEPRECATED_KEYS) return@OnSharedPreferenceChangeListener
         mirrorKeyToRemote(key, sp.all[key])
     }
     ```
   - Register listener: `localPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)`.

2. **Single-Key Asynchronous Write-Through Function:**
   ```kotlin
   fun mirrorKeyToRemote(key: String, value: Any?) {
       syncExecutor.execute {
           val remote = XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)
           if (remote == null) {
               dirtyKeyQueue.add(key)
               return@execute
           }
           try {
               val editor = remote.edit()
               when (value) {
                   is Boolean -> editor.putBoolean(key, value)
                   is Float -> editor.putFloat(key, value)
                   is String -> editor.putString(key, value)
                   is Int -> editor.putInt(key, value)
                   is Long -> editor.putLong(key, value)
                   null -> editor.remove(key)
               }
               editor.apply()
           } catch (t: Throwable) {
               Log.w("HyperiOS", "Failed to mirror key '$key': ${t.message}")
               dirtyKeyQueue.add(key)
           }
       }
   }
   ```

3. **Lifecycle Drain on Service Bind:**
   - In `XposedServiceBridge.init()` and `onServiceBind`:
     ```kotlin
     XposedServiceBridge.onBind {
         drainDirtyQueue()
     }
     ```
   - `drainDirtyQueue()`:
     ```kotlin
     fun drainDirtyQueue() {
         syncExecutor.execute {
             val remote = XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME) ?: return@execute
             val keysToDrain = mutableSetOf<String>()
             while (true) {
                 val k = dirtyKeyQueue.poll() ?: break
                 keysToDrain.add(k)
             }
             if (keysToDrain.isEmpty()) return@execute

             val localPrefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return@execute
             val editor = remote.edit()
             for (key in keysToDrain) {
                 val value = localPrefs.all[key]
                 when (value) {
                     is Boolean -> editor.putBoolean(key, value)
                     is Float -> editor.putFloat(key, value)
                     is String -> editor.putString(key, value)
                     is Int -> editor.putInt(key, value)
                     is Long -> editor.putLong(key, value)
                     null -> editor.remove(key)
                 }
             }
             editor.apply()
             Log.i("HyperiOS", "Drained ${keysToDrain.size} dirty keys to remote preferences")
         }
     }
     ```

4. **Purge Fragile Delegate Sync Logic:**
   - In `PrefBooleanDelegate`, `PrefLongDelegate`, `PrefIntDelegate`, `PrefFloatDelegate`, `PrefStringDelegate`, and `PrefEnumDelegate`:
     - Keep `prefs.edit().put*(key, value).apply()` (or `.commit()`).
     - **Delete** `SettingsRepository.syncRemote { ... }`.
     - **Delete** `if (SettingsRepository.hasPendingPush) { SettingsRepository.flushPendingPush() }`.
     - Retain `SettingsRepository.sendSettingsChangedBroadcast(key, value)` for live UI hot-reloads.
   - Deprecate or remove `hasPendingPush` and `flushPendingPush(context)`.

### 1.5 Edge-Case Mitigations
- **Rapid Slider Dragging:** Single-thread executor queues tasks sequentially; `editor.apply()` in Android SharedPreferences batches in-memory edits immediately and writes to disk asynchronously, eliminating IPC bottlenecks.
- **Process Termination:** Local SharedPreferences commits synchronously on critical delegate writes, and on reboot, initial reconciliation catches any unstored keys without stalling UI boot.

---

## Module 2: Reels Shifting Timing & Dimming Scrim

### 2.1 Root Cause Analysis
- **Symptom 1 (Timing Lag / Failure to Shift):** Entering the Facebook Reels tab often resulted in the bottom reels metadata and buttons being covered by the floating navigation bar. The overlay shift failed to execute or lagged.
  - *Cause:* In `FacebookHook.kt`, `RecyclerView.onChildAttachedToWindow` executed when the Litho component was attached, but before Litho mounted its internal views (`childCount == 0`). `addOnLayoutChangeListener` on the recycled ViewHolder never triggered because the ViewHolder’s root bounds did not change during scroll rebinding.
- **Symptom 2 (Target Container Missed):** In Reel 2, the overlay was at `Child 0` (not `Child 2`). The previous heuristic `c.top > reelButton.top + (reelButton.height * 0.3f)` evaluated `322 > 1039`, which was false, so `child2` resolved to null.
- **Symptom 3 (Scrim Floating Upwards):** The 300px dark bottom dimming scrim shifted upward by -190f, creating an ugly dark band across the center of the video.
  - *Cause:* Line 421 of `FacebookHook.kt`:
    `else if (sc.height in 1..120 || j == child2.childCount - 1)`
    In reels with only 2 subchildren (Subchild 0: controls, Subchild 1: 300px scrim), `j = 1` and `j == 2 - 1` evaluated to `1 == 1` (true). The 300px scrim was treated as a scrubber and shifted by -190f!

### 2.2 Target Files & Signatures
- **Target File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`
- **Methods:** `onPackageReady()`, `applyReelsTranslationToReelTree(root: ViewGroup)`, `findReelContainers(root: ViewGroup, outList: MutableList<ViewGroup>)`

### 2.3 Architectural Design: PreDraw Litho Lifecycle & Strict Scrim Pinning

```
+-------------------------------------------------------------------------+
|                  RecyclerView.onChildAttachedToWindow                   |
+-------------------------------------------------------------------------+
                                    |
                 child.viewTreeObserver.addOnPreDrawListener
                                    |
                                    v
+-------------------------------------------------------------------------+
|                       Litho Measure & Mount Phase                       |
|           (Subchildren generated, laid out, and measured)               |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                   OnPreDraw Callback Fired (One-Shot)                   |
|                    Remove listener immediately                          |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                    Identify Reel Container & Overlay                    |
|   Invariant: `c is ViewGroup && !hasSurfaceView(c) && !hasTapControls(c)`|
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                     Categorize Subchildren (child2)                     |
|                                                                         |
|  * Subchild with children (sc.childCount > 0):                          |
|      -> Action Buttons + Metadata                                       |
|      -> translationY = -190f                                            |
|                                                                         |
|  * Subchild without children AND height in 1..120px:                    |
|      -> Scrubber / Progress Bar                                         |
|      -> translationY = -190f                                            |
|                                                                         |
|  * Subchild with height > 120px:                                        |
|      -> 300px Dark Dimming Scrim                                        |
|      -> translationY = 0f (GROUNDED AT BOTTOM)                          |
+-------------------------------------------------------------------------+
```

### 2.4 Step-by-Step Implementation Details

1. **One-Shot `OnPreDrawListener` on Recycled Children:**
   - In `FacebookHook.onPackageReady()`, update the `RecyclerView.onChildAttachedToWindow` hook:
     ```kotlin
     val child = chain.args[0] as? View
     if (child is ViewGroup && isReelsActive) {
         child.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
             override fun onPreDraw(): Boolean {
                 child.viewTreeObserver.removeOnPreDrawListener(this)
                 if (isReelsActive) {
                     applyReelsTranslationToReelTree(child)
                 }
                 return true
             }
         })
     }
     ```

2. **Accurate Overlay Container Identification (`!hasSurfaceView && !hasTapControls`):**
   - Add helper function `hasTapControls(view: View): Boolean` to detect video playback tap gesture consumers (or surface overlays).
   - In `applyReelsTranslationToReelTree`:
     ```kotlin
     var overlayContainer: ViewGroup? = null
     for (i in 0 until reelButton.childCount) {
         val c = reelButton.getChildAt(i)
         if (c is ViewGroup && !hasSurfaceView(c) && !hasTapControls(c)) {
             // Look for container containing action buttons or metadata
             if (c.childCount >= 2) {
                 overlayContainer = c
                 break
             }
         }
     }
     if (overlayContainer == null && reelButton.childCount >= 1) {
         for (i in 0 until reelButton.childCount) {
             val c = reelButton.getChildAt(i)
             if (c is ViewGroup && !hasSurfaceView(c)) {
                 overlayContainer = c
                 break
             }
         }
     }
     ```

3. **Strict Subchild Translation Rules:**
   - Update lines 416-427 in `FacebookHook.kt`:
     ```kotlin
     if (overlayContainer != null) {
         overlayContainer.clipChildren = false
         overlayContainer.clipToPadding = false
         overlayContainer.translationY = 0f // Root container stays at 0f

         for (j in 0 until overlayContainer.childCount) {
             val sc = overlayContainer.getChildAt(j) ?: continue
             val h = sc.height

             if (sc is ViewGroup && sc.childCount > 0) {
                 // Metadata, Creator title, Side Action buttons
                 sc.translationY = -190f
                 sShiftedReelViews.add(sc)
             } else if (sc.childCount == 0 && h in 1..120) {
                 // Scrubber bar / playback progress bar
                 sc.translationY = -190f
                 sShiftedReelViews.add(sc)
             } else {
                 // 300px dark bottom dimming scrim (height > 120 or fallback)
                 if (sc.translationY != 0f) {
                     sc.translationY = 0f
                 }
             }
         }
     }
     ```
   - **Critical:** Permanently delete `|| j == child2.childCount - 1`.

### 2.5 Edge-Case Mitigations
- **Fast Scrolling / View Recycling:** `OnPreDrawListener` cleans itself up in `onPreDraw()`, preventing memory leaks and avoiding repeated per-frame layout overhead.
- **Tab Switching Away from Reels:** `resetShiftedReelsOverlays()` resets all elements in `sShiftedReelViews` back to `translationY = 0f`.

---

## Module 3: Tab Icons Filled vs. Outline Architecture

### 3.1 Root Cause Analysis
- **Symptom:** Facebook Tab 0 (Feed) permanently displays a FILLED icon even when unselected, while tabs 1-5 display outline icons when active or fail to switch to filled dynamically.
- **Root Cause in `FacebookNavBarHost.kt`:**
  1. `extractTabDrawables(child)` returned `Pair(inactiveDrawable ?: activeDrawable, activeDrawable ?: inactiveDrawable)`.
  2. At Facebook boot, Tab 0 was active. `inactiveDrawable` was null, so `inactiveDrawable ?: activeDrawable` assigned the active FILLED drawable into `inactiveDrawable`.
  3. Tabs 1-5 started inactive; their `activeDrawable` was null, falling back to OUTLINE.
  4. The 150ms polling loop updated `activeBitmap`, but `tab[0].inactiveBitmap` remained permanently poisoned with the filled white bitmap.

### 3.2 Target Files & Signatures
- **Target File:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`
- **Methods:**
  - `extractTabDrawables(child: View): Pair<Drawable?, Drawable?>`
  - `extractFacebookTabs(tabBarContainer: ViewGroup, isDark: Boolean, accentColor: Color): List<FacebookTabData>`
  - `FacebookNavBarHost(...)` Compose lifecycle & polling loop

### 3.3 Architectural Design: Dual-Cache Isolation & Reflection Bridge

```
+-------------------------------------------------------------------------+
|                  Facebook Tab View (`X.C02LI` child)                    |
+-------------------------------------------------------------------------+
                                    |
          Reflective Extraction via `C02LI.A00(a0s, variant, child)`
                                    |
                  +-----------------+-----------------+
                  |                                   |
                  v                                   v
           `C01b7.OUTLINE`                     `C01b7.FILLED`
                  |                                   |
                  v                                   v
          outlineDrawable                      filledDrawable
                  |                                   |
                  v                                   v
       drawableToBitmap(outline)           drawableToBitmap(filled)
                  |                                   |
                  v                                   v
     outlineBitmapsCache[index]           filledBitmapsCache[index]
                  |                                   |
                  +-----------------+-----------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                       Zero-Contamination Guarantee                      |
|                                                                         |
|  * Inactive Tab Icon: strictly reads from `outlineBitmapsCache[index]`  |
|  * Active Tab Icon: strictly reads from `filledBitmapsCache[index]`     |
|  * When Tab is selected/unselected: update cache without cross-fallback |
+-------------------------------------------------------------------------+
```

### 3.4 Step-by-Step Implementation Details

1. **Reflective `C02LI` Variant Extraction:**
   - Inspect `child.javaClass` for `X.C02LI`:
     - Load `X.C01b7` enum class via `child.context.classLoader.loadClass("X.C01b7")`.
     - Obtain `C01b7.FILLED` and `C01b7.OUTLINE` enum constants.
     - Find the icon definition field `A0S` (or `A0s` / `A0T`) on `child`.
     - Find method `A00` on `child.javaClass` (or static method on `C02LI` taking `(a0s, variant, child)`).
     - Invoke to obtain both Drawables synchronously without waiting for user interaction.
   - Fallback methods (field `A0T` / `C03LV`, `DxG()`, `A02`) must return `Pair(outlineDrawable, filledDrawable)` **without** fallback assignment (`?:`).

2. **Dual-Cache Initialization & Update in `FacebookNavBarHost`:**
   ```kotlin
   val outlineBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }
   val filledBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }
   ```

3. **Strict Separation in Extraction:**
   ```kotlin
   private fun extractFacebookTabs(
       tabBarContainer: ViewGroup,
       isDark: Boolean,
       accentColor: Color,
       outlineCache: MutableMap<Int, Bitmap>,
       filledCache: MutableMap<Int, Bitmap>
   ): List<FacebookTabData> {
       val out = mutableListOf<FacebookTabData>()
       val tabViews = findFacebookTabViews(tabBarContainer)
       val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#65676B")
       val activeTint = accentColor.toArgb()

       for ((index, child) in tabViews.withIndex()) {
           val (outlineDrawable, filledDrawable) = extractTabDrawables(child)

           if (outlineDrawable != null && !outlineCache.containsKey(index)) {
               drawableToBitmap(outlineDrawable, tint = inactiveTint)?.let { outlineCache[index] = it }
           }
           if (filledDrawable != null && !filledCache.containsKey(index)) {
               drawableToBitmap(filledDrawable, tint = activeTint)?.let { filledCache[index] = it }
           }

           val inactiveBmp = outlineCache[index]
           val activeBmp = filledCache[index] ?: inactiveBmp

           val label = child.contentDescription?.toString()
           val (badgeCount, hasBadgeDot) = extractBadge(child)

           out.add(FacebookTabData(
               tabView = child,
               inactiveBitmap = inactiveBmp,
               activeBitmap = activeBmp,
               label = label,
               badgeCount = badgeCount,
               hasBadgeDot = hasBadgeDot
           ))
       }
       return out
   }
   ```

4. **Dynamic Transition in Polling Loop:**
   - When `selectedIndex` changes or tab clicks occur:
     - For the newly selected tab `curSelected`: extract its filled drawable and place it into `filledBitmapsCache[curSelected]`.
     - For the previously selected tab `prevSelected`: ensure its `inactiveBitmap` displays `outlineBitmapsCache[prevSelected]`.
     - Never allow `inactiveBitmap` of an unselected tab to be populated with a filled icon.

---

## Module 4: Profile Tab & Hover Pill Styling

### 4.1 Root Cause Analysis
- **Symptom 1 (Artifact Sparkle Overlay):** In commit `6d8df756`, artificial `sparkleIndices` and `isSparkle` parameters were introduced, creating an animated rotating 4-pointed star overlay over the profile tab.
- **Symptom 2 (Unfilled Profile Tab):** In `LiquidNavigationBar.kt`, `isCircular` forced `colorFilter` to `null` and added a border ring:
  ```kotlin
  colorFilter = if (isCircular || accentColor == null) null else ColorFilter.tint(accentColor),
  modifier = Modifier.size(baseSize * scaleFactor).then(
      if (isCircular) {
          if (accentColor != null) {
              Modifier.border((2 * scaleFactor).dp, accentColor, CircleShape)
                  .padding((2 * scaleFactor).dp)
                  .clip(CircleShape)
          } else Modifier.clip(CircleShape)
      } else Modifier
  )
  ```
  In Facebook, the Profile/Menu tab is a native vector glyph (or icon), NOT a photo avatar. Forcing `isCircular` prevented it from being filled with the active accent color inside the moving hover pill.

### 4.2 Target Files & Signatures
- **Target File 1:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/LiquidNavigationBar.kt`
- **Target File 2:** `/data/data/com.termux/files/home/hyperbounce-hos4/app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`

### 4.3 Architectural Fix

1. **Clean Removal of Sparkle Artifacts:**
   - In `LiquidNavigationBar.kt`:
     - Remove `sparkleIndices: Set<Int> = emptySet()` from `LiquidNavigationBar` parameter list.
     - Remove `isSparkle: Boolean = false` from `LiquidBottomTab`.
     - Delete lines 769–832 containing `rememberInfiniteTransition(label = "profileSparkle")`, `sparkleScale`, `sparkleAlpha`, `sparkleRotation`, and the sparkle `Canvas`.
   - In `FacebookNavBarHost.kt`:
     - Delete `sparkleIndices = if (profileIndex >= 0) setOf(profileIndex) else emptySet()`.

2. **Unified Pill Accent & Profile Styling:**
   - In `FacebookNavBarHost.kt`:
     - Remove `circularIndices` configuration for the profile tab (`circularIndices = emptySet()`). The Facebook Menu/Profile tab is an icon and must be styled uniformly with all other tabs, receiving the filled accent color when active inside the hover pill.
   - In `LiquidNavigationBar.kt`:
     - Preserve `circularIndices` solely for photo avatars in apps that provide actual user photo bitmaps (e.g., Instagram and YouTube), where circular clipping and border rings are appropriate and desirable.

### 4.4 Multi-App Non-Regression Verification Matrix

| Application | Navigation Bar Implementation | Circular Avatar Behavior | Accent Pill Fill Behavior | Sparkle State |
| :--- | :--- | :--- | :--- | :--- |
| **Facebook** | `FacebookNavBarHost.kt` | Disabled (`emptySet()`) | Uniformly filled with accent color | Completely removed |
| **Instagram** | `InstagramNavBarHost.kt` | User photo avatar clipped to `CircleShape` | Photo preserved, un-tinted inside pill | Untouched (not used) |
| **WhatsApp** | `WhatsAppNavBarHost.kt` | No circular tabs (`emptySet()`) | Standard icon tinting | Untouched (not used) |
| **YouTube** | `YouTubeNavBarHost.kt` | User profile avatar clipped to `CircleShape` | Border on active photo avatar | Untouched (not used) |

---

## Execution Sequence for Implementation Subagent

The implementation subagent should execute changes in four sequential phases:

```
+---------------------------------------------------------------------+
| Phase 1: Implement Centralized Write-Through Mirror                 |
|  - Modify `SettingsRepository.kt` (Add PrefMirrorSyncManager,       |
|    single-thread executor, dirty queue, drain on bind).             |
|  - Remove `hasPendingPush` and 300-key synchronous Binder dumps     |
|    from preference delegates.                                       |
+---------------------------------------------------------------------+
                                  |
                                  v
+---------------------------------------------------------------------+
| Phase 2: Refactor Reels Shifting & Ground Scrim                     |
|  - Modify `FacebookHook.kt`.                                        |
|  - Add one-shot `OnPreDrawListener` to `onChildAttachedToWindow`.   |
|  - Improve overlay container detection (`!hasSurfaceView &&        |
|    !hasTapControls`).                                               |
|  - Enforce strict `h in 1..120` scrubber shift and ground 300px     |
|    scrim at `translationY = 0f`. Remove `j == childCount - 1`.      |
+---------------------------------------------------------------------+
                                  |
                                  v
+---------------------------------------------------------------------+
| Phase 3: Implement Dual-Cache Tab Drawables Extraction              |
|  - Modify `FacebookNavBarHost.kt`.                                  |
|  - Reflect `C02LI.A00(a0s, variant, child)` with `C01b7` variants.  |
|  - Eliminate `inactive ?: active` fallback contamination.           |
|  - Maintain independent `outlineBitmapsCache` and `filledCache`.    |
+---------------------------------------------------------------------+
                                  |
                                  v
+---------------------------------------------------------------------+
| Phase 4: Purge Sparkle & Unify Hover Pill Profile Styling           |
|  - Modify `LiquidNavigationBar.kt`: remove `sparkleIndices`,        |
|    `isSparkle`, and animated canvas.                                |
|  - Modify `FacebookNavBarHost.kt`: remove sparkle and set           |
|    `circularIndices = emptySet()`.                                  |
+---------------------------------------------------------------------+
```

---

## Validation & Verification Protocol

In compliance with `AGENT_RULES.md`:
1. **Zero Local Compilation:** Do not run `./gradlew assembleRelease` on Termux.
2. **Atomic Commits:** Commit code changes with clear descriptive messages following Conventional Commits (`fix(prefs): ...`, `fix(nav): ...`).
3. **CI/CD Build Pipeline:** Push commits to `hos4` branch on GitHub. Monitor GitHub Actions CI workflow to completion.
4. **Device Installation & Functional Test:** Download APK from GitHub Actions release/artifact and install via `su -c "pm install -r <apk_path>"`.
5. **Runtime Verification:**
   - **Bug 1:** Change preferences in HyperBounce UI. Inspect logcat (`logcat -s HyperiOS HyperBounce`) to confirm immediate single-key async mirror without Binder dumps or UI stutter. Verify changes persist across hook queries without requiring app restart.
   - **Bug 2:** Open Facebook Reels. Verify instant zero-lag shifting of action buttons and scrubber without covering text. Verify 300px dark bottom dimming scrim remains firmly grounded at `0f`.
   - **Bug 3:** Boot Facebook. Verify Tab 0 (Feed) shows filled icon when active, and transitions cleanly to outline icon when another tab is selected.
   - **Bug 4:** Select Facebook Profile/Menu tab. Verify smooth active pill hover transition with accent color fill and zero sparkle artifacts. Verify Instagram and YouTube navigation bars remain unaffected.
