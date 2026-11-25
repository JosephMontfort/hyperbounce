# Architectural Implementation Plan (Round 2): Preferences Commit Sync, Facebook Tab Tinting, and Reels FAB Shift

**Author:** Architectural Planner Subagent  
**Date:** November 25, 2026  
**Target Branch:** `hos4`  
**Workspace:** `/data/data/com.termux/files/home/hyperbounce-hos4`  
**Status:** READY FOR IMPLEMENTATION  

---

## Executive Summary

This specification defines the exact, root-cause fixes for three remaining defects following `e196444c` / `9075945c`:
1. **LibXposed API 102 Preferences Sync Bug (`SettingsRepository.kt`):** `RemotePreferences.Editor.apply()` dispatches asynchronously onto `RemotePreferences.EXECUTOR` and silently swallows `RemoteException`. Replacing `apply()` with synchronous `commit()` on our background `syncExecutor` (plus in `reconcilePreferences` and `resetToDefaults`) and removing redundant direct remote writes in `ScreenUnlockHolder` makes `dirtyKeyQueue` and error logging actually work.
2. **Facebook Tab Icons Tint in Light & Dark Mode (`FacebookNavBarHost.kt`):** `inactiveTint` used `#65676B` instead of `Color.BLACK` in light mode, `drawableToBitmap` only called `setTint()` (ignored by custom drawables or drawables with an existing `ColorFilter`), the `child.draw` fallback did not tint at all, and `outlineBitmapsCache` / `filledBitmapsCache` were not cleared when `isDark` or `accentColor` changed.
3. **Facebook Reels FAB Shifting Regression (`FacebookHook.kt` + `FacebookNavBarHost.kt`):** `hasTapControls()` checked `c.javaClass.name` for `"gesture"`, which always returned `false` on Litho `ComponentHost`/`FrameLayout`, causing `applyReelsTranslationToReelTree` to grab the top header/tap container, skip the bottom overlay, and cancel the `OnPreDrawListener` on frame 0. Additionally, `OnLayoutChangeListener` was removed in `e196444c`, and `isReelsActive` lacked an active-state setter to trigger shifting/resetting when `FacebookNavBarHost` resolves or switches tabs.

All changes are strictly scoped to `SettingsRepository.kt`, `FacebookNavBarHost.kt`, and `FacebookHook.kt`, ensuring **zero regressions** for Instagram, WhatsApp, and YouTube. Per `AGENT_RULES.md`, do not compile locally on Termux; push to GitHub Actions to build and verify.

---

## Module 1: LibXposed API 102 Preferences Sync (`SettingsRepository.kt`)

**Target File:** `app/src/main/java/dev/hyperios/app/ui/state/SettingsRepository.kt`

### 1.1 `PrefMirrorSyncManager.mirrorKeyToRemote` (lines 117–140)
- **Null remote handling:** When `XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)` returns `null`, log and enqueue:
  ```kotlin
  if (remote == null) {
      Log.w("HyperiOS", "mirrorKeyToRemote: remotePreferences is null, queuing '$key'")
      dirtyKeyQueue.add(key)
      return@execute
  }
  ```
- **Synchronous `commit()` on `syncExecutor`:** Replace `editor.apply()` (line 134) with `val ok = editor.commit()`.
  ```kotlin
  val ok = editor.commit()
  if (ok) {
      Log.d("HyperiOS", "mirrorKeyToRemote: committed '$key'")
  } else {
      Log.w("HyperiOS", "mirrorKeyToRemote: commit() returned false for '$key', re-queuing")
      dirtyKeyQueue.add(key)
  }
  ```
- Keep the `catch (t: Throwable)` block logging `Log.w("HyperiOS", "Failed to mirror key '$key': ${t.message}")` and `dirtyKeyQueue.add(key)`.

### 1.2 `PrefMirrorSyncManager.drainDirtyQueue` (lines 142–169)
- **Null remote logging:**
  ```kotlin
  val remote = XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)
  if (remote == null) {
      Log.w("HyperiOS", "drainDirtyQueue: remotePreferences is null, deferring")
      return@execute
  }
  ```
- **Synchronous `commit()` with re-queue on failure/exception:** Replace `editor.apply()` (line 166) with:
  ```kotlin
  try {
      val ok = editor.commit()
      if (ok) {
          Log.i("HyperiOS", "Drained ${keysToDrain.size} dirty keys to remote preferences (commit=true)")
      } else {
          Log.w("HyperiOS", "drainDirtyQueue: commit() returned false for ${keysToDrain.size} keys, re-queuing")
          dirtyKeyQueue.addAll(keysToDrain)
      }
  } catch (t: Throwable) {
      Log.w("HyperiOS", "drainDirtyQueue: commit() threw for ${keysToDrain.size} keys: ${t.message}")
      dirtyKeyQueue.addAll(keysToDrain)
  }
  ```

### 1.3 `reconcilePreferences()` and `resetToDefaults()`
- **`reconcilePreferences()`:**
  - Line 214: `remote.edit().clear().commit()` (was `.apply()`).
  - Line 249: `remoteEditor.commit()` (was `.apply()`).
  - Line 294: `remoteEditor.commit()` (was `.apply()`).
- **`resetToDefaults()`:**
  - Line 556: `XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.clear()?.commit()` (was `.apply()`).
  - Line 590: `XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putBoolean(KEY_STORAGE_INITIALIZED, true)?.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)?.commit()` (was `.apply()`).

### 1.4 Remove Redundant Direct Remote Writes in `ScreenUnlockHolder`
- `PrefMirrorSyncManager.init(prefs)` registers `OnSharedPreferenceChangeListener` on `prefs`, so every `prefs.edit()...apply()` automatically triggers `mirrorKeyToRemote(key, value)`.
- Remove the redundant `XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()` calls in:
  - `setRowZ` (line 1311)
  - `setRowCenterBounce` (line 1325)
  - `setRowOuterBounce` (line 1339)
  - `setRowCenterSettle` (line 1353)
  - `setRowOuterSettle` (line 1367)
- In `reloadFromDefaults()` (lines 1439–1456): remove `remoteEditor` (line 1440), lines 1447–1451 (`remoteEditor?.remove(...)`), and line 1454 (`remoteEditor?.apply()`). Removing the keys from `prefs` via `editor.apply()` notifies `prefChangeListener` for each removed key (`value == null` -> `editor.remove(key)` -> `commit()`).

---

## Module 2: Facebook Tab Icons Tint in Light & Dark Mode (`FacebookNavBarHost.kt`)

**Target File:** `app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`

### 2.1 Add `tintBitmap(src: Bitmap, tint: Int): Bitmap`
Add imports `android.graphics.Paint`, `android.graphics.PorterDuff`, and `android.graphics.PorterDuffColorFilter`, and add helper:
```kotlin
private fun tintBitmap(src: Bitmap, tint: Int): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}
```

### 2.2 Apply `tintBitmap` in `drawableToBitmap` and `child.draw` Fallback
- **`drawableToBitmap` (lines 172–193):**
  - Keep `mutated.setTint(tint)` when `tint != null`, draw into `bmp`, and return `if (tint != null) tintBitmap(bmp, tint) else bmp`. This guarantees custom drawables and drawables with pre-existing `ColorFilter`s (e.g., Tab 0 Reels in dark mode) are uniformly tinted.
- **`child.draw` fallback in `extractFacebookTabs` (lines 369–385):**
  - After `child.draw(Canvas(bmp))`, apply `tintBitmap`:
    ```kotlin
    val targetTint = if (child.isSelected) activeTint else inactiveTint
    val tintedBmp = tintBitmap(bmp, targetTint)
    if (child.isSelected) {
        if (!filledCache.containsKey(index)) filledCache[index] = tintedBmp
    } else {
        if (!outlineCache.containsKey(index)) outlineCache[index] = tintedBmp
    }
    ```

### 2.3 Correct Light-Mode `inactiveTint` & Clear Caches on Theme/Accent Change
- **Lines 341 & 451:** Change `inactiveTint` from `#65676B` to `android.graphics.Color.BLACK`:
  ```kotlin
  val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
  ```
- **Line 449 (`LaunchedEffect(tabBarContainer, isDark, accentColor)`):** Clear both caches at the top of the effect before `extractFacebookTabs`:
  ```kotlin
  outlineBitmapsCache.clear()
  filledBitmapsCache.clear()
  ```

---

## Module 3: Facebook Reels FAB Shifting Regression (`FacebookHook.kt` + `FacebookNavBarHost.kt`)

**Target Files:**
- `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`
- `app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`

### 3.1 `WeakReference<ViewGroup>` & `FacebookHook.setReelsActive(active: Boolean)`
- In `FacebookHook.kt` `companion object`:
  - Add `private var sContentViewRef: java.lang.ref.WeakReference<ViewGroup>? = null`.
  - Move `sShiftedReelViews`, `findReelContainers`, `hasSurfaceView`, `hasReelsOverlaySemantics`, `applyReelsTranslationToReelTree`, `resetShiftedReelsOverlays`, and `applyReelsTranslation` into `companion object` so `setReelsActive` can invoke them directly without leaking a `FacebookHook` instance.
  - Add `@JvmStatic fun setReelsActive(active: Boolean)`:
    ```kotlin
    @JvmStatic
    fun setReelsActive(active: Boolean) {
        if (isReelsActive == active) return
        isReelsActive = active
        val contentView = sContentViewRef?.get() ?: return
        contentView.post {
            if (isReelsActive) {
                applyReelsTranslation(contentView)
            } else {
                resetShiftedReelsOverlays()
            }
        }
    }
    ```
- In `setupReelsWatcher(activity: Activity)` (line 356):
  - Store `sContentViewRef = java.lang.ref.WeakReference(contentView)`.
  - If `isReelsActive` is already `true`, post `applyReelsTranslation(contentView)`.
- In `FacebookNavBarHost.kt` (line 447):
  - Replace `FacebookHook.isReelsActive = isReelsTab` with:
    ```kotlin
    LaunchedEffect(isReelsTab) {
        FacebookHook.setReelsActive(isReelsTab)
    }
    ```

### 3.2 Semantic Bottom Overlay Detection (`hasReelsOverlaySemantics`)
- Remove `hasTapControls(view: View)` (lines 396–399).
- Add `hasReelsOverlaySemantics(view: View): Boolean`:
  ```kotlin
  private fun hasReelsOverlaySemantics(view: View): Boolean {
      val desc = view.contentDescription?.toString()?.lowercase()
      if (desc != null && (
              desc.contains("like") ||
              desc.contains("comment") ||
              desc.contains("share") ||
              desc.contains("audio") ||
              desc.contains("follow")
          )
      ) {
          return true
      }
      if (view is ViewGroup) {
          for (i in 0 until view.childCount) {
              if (hasReelsOverlaySemantics(view.getChildAt(i))) return true
          }
      }
      return false
  }
  ```
- In `applyReelsTranslationToReelTree(root: ViewGroup)` (lines 413–423), select `overlayContainer` in two passes:
  1. **Primary (semantic):** First child `c` of `reelButton` where `c is ViewGroup && !hasSurfaceView(c) && hasReelsOverlaySemantics(c)`.
  2. **Fallback (geometry/index):** If Pass 1 returns `null`, pick child `c` at index `i` where `c is ViewGroup && !hasSurfaceView(c) && (c.top > reelButton.top + reelButton.height * 0.3f || (reelButton.childCount >= 3 && i == 2))` (preferring `c.childCount > 0`).
- **Preserve strict subchild shifting rules (lines 429–452):**
  - `sc is ViewGroup && sc.childCount > 0` -> `translationY = -190f`, `sShiftedReelViews.add(sc)`, `didShiftAny = true`.
  - `(sc !is ViewGroup || sc.childCount == 0) && h in 1..120` -> `translationY = -190f`, `sShiftedReelViews.add(sc)`, `didShiftAny = true`.
  - Scrim (`height > 120`, non-leaf or >120px) -> keep `translationY = 0f` grounded.

### 3.3 Bounded `OnPreDrawListener` + Deduplicated `OnLayoutChangeListener`
- In `RecyclerView.onChildAttachedToWindow` hook (`FacebookHook.kt` lines 175–203):
  - Keep the bounded `OnPreDrawListener` (up to 15 frames until `applyReelsTranslationToReelTree(child)` returns `true`).
  - Also attach a deduplicated `View.OnLayoutChangeListener` keyed by tag `0x7F0B9903`:
    ```kotlin
    val layoutTagKey = 0x7F0B9903
    if (child.getTag(layoutTagKey) == null) {
        child.setTag(layoutTagKey, true)
        child.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (isReelsActive && v is ViewGroup && v.isAttachedToWindow) {
                applyReelsTranslationToReelTree(v)
            }
        }
    }
    ```

---

## Verification & Non-Regression Checklist
- **No local APK build** (`AGENT_RULES.md` §2): Push to `hos4` and verify via GitHub Actions.
- **Zero regressions for other apps:** `InstagramNavBarHost.kt`, `InstagramHook.kt`, `WhatsAppNavBarHost.kt`, `WhatsAppHook.kt`, `YouTubeNavBarHost.kt`, and `YouTubeHook.kt` are unmodified. `PrefMirrorSyncManager` continues to serve all apps synchronously on its background `syncExecutor`.
- **Logcat verification targets:**
  - Tag `"HyperiOS"`: `"mirrorKeyToRemote: committed"` / `"Drained N dirty keys to remote preferences (commit=true)"` without silent drops.
  - Facebook light/dark mode: all 5 tab icons crisp black in light mode and crisp white in dark mode, updating immediately on theme/accent toggle.
  - Facebook Reels: FABs (Like/Comment/Share/Audio/Follow) and scrubber shift up `-190f` on initial open, tab switch, and fast scroll while bottom scrim remains at `0f`.
