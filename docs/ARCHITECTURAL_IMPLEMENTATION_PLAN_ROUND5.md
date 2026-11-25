# Architectural Implementation Plan — Round 5

## Overview

This plan covers three coordinated fixes in `/data/data/com.termux/files/home/hyperbounce-hos4`:
1. **Part 1**: Revert the morphing nav bar back to commit `6e5b601a` and remove `drawerWidthPxState` from `FacebookHook.kt`.
2. **Part 2**: Preserve the side-drawer overlap fix already present in `FacebookHook.kt`.
3. **Part 3**: Fix Reels scrubber touch handling, soundwave peaks (`0x7f0a0563`), pause-state metadata overlap, and the dimmed bottom strip (`X.09rv` / `X.09rf`) in `FacebookHook.kt`.

---

## Part 1: Morphing Nav Bar Revert to `6e5b601a`

1. Restore these three files verbatim from commit `6e5b601a`:
   ```bash
   git checkout 6e5b601a -- \
     app/src/main/java/dev/hyperios/app/nav/LiquidNavigationBar.kt \
     app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt \
     app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt
   ```
2. In `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`, remove ONLY `drawerWidthPxState` (4 hunks):
   - Line 16: `import androidx.compose.runtime.mutableIntStateOf`
   - Line 52: `val drawerWidthPxState = mutableIntStateOf(0)`
   - Line 709 (inside `X.01zQ` constructor hook): `drawerWidthPxState.intValue = 0`
   - Lines 793–795 (inside `X.01zQ.onScrollChanged` hook):
     ```kotlin
     if (bookmarksView.width > 0) {
         drawerWidthPxState.intValue = bookmarksView.width
     }
     ```
3. Leave `SettingsRepository.kt` and `HyperBounceApp.kt` untouched.

---

## Part 2: Side Drawer Overlap Fix (Preserve Existing Logic)

Keep the existing drawer-clipping protection in `FacebookHook.kt` intact:
- `isClipBoundary(vg)` (lines 120–136): stops unclipping at `0x7f0a13db`, `0x7f0a1b88`, `android.R.id.content`, `sMainPanelRef?.get()`, `sBookmarksPanelRef?.get()`, `sDrawerScrollViewRef?.get()`, `vg.parent === sDrawerScrollViewRef?.get()`, `HorizontalScrollView`, `X.01zQ`, `ViewPager`, `ViewPager2`, and `RecyclerView`.
- `sUnclippedReelViews` (lines 45–47, 140–143, 478–491): records `(clipChildren, clipToPadding)` and restores them in `resetShiftedReelsOverlays()`.
- `sMainPanelRef` in `X.01zQ.onScrollChanged` (lines 788–791): enforces `mainView.clipChildren = true` and clears `TAG_REELS_UNCLIPPED`.

---

## Part 3: Reels SeekBar Touch, Soundwave Peaks, Pause Overlap & Dimmed Strip (`FacebookHook.kt`)

### 3.1 Scrubber + Soundwave Peaks (`0x7f0a0563`) + Timestamps via `paddingBottom` on `0x7f0a1cab`

**Verified Decompiled Signatures:**
- `X.0Aoc` (extends `X.0DtI`, `classes6.dex`):
  - `public final void A1W()` (0 args, returns `void`)
  - Reads `ViewGroup viewGroup = this.A0H.A09;` (`A0H` is `X.0Aok` / `C05250Aok`, `A09` is `public final ViewGroup A09;` = `0x7f0a1cab`) and calls `viewGroup.setPaddingRelative(0, 0, 0, dimension + iA02);`.

**Implementation in `FacebookHook.kt`:**
1. Add state & tag constant in `companion object`:
   - `private const val TAG_REELS_SCRUBBER_PADDED = 0x7F0B9908`
   - `private val sPaddedScrubberViews = java.util.Collections.synchronizedMap(java.util.WeakHashMap<ViewGroup, Int>())`
   - Define scrubber IDs set/array:
     - `REELS_SCRUBBER_CONTAINER_ID = 0x7f0a1cab`
     - `REELS_SCRUBBER_IDS = intArrayOf(0x7f0a1cab, 0x7f0a1ca3, 0x7f0a1c63, 0x7f0a1c6b, 0x7f0a088e, 0x7f0a0563)`
2. Hook `X.0Aoc.A1W()` in `onPackageReady()`:
   - Load `X.0Aoc` via `param.classLoader.loadClass("X.0Aoc")` and hook `getDeclaredMethod("A1W")`.
   - After `val result = chain.proceed()`:
     - If `isReelsActive`:
       - Reflect `A0H` on `chain.thisObject`, then `A09` (`ViewGroup`) on `A0H`.
       - Record `vg.paddingBottom` in `sPaddedScrubberViews` if not already present (`synchronized(sPaddedScrubberViews)`).
       - Call `vg.setPaddingRelative(vg.paddingStart, vg.paddingTop, vg.paddingEnd, vg.paddingBottom + 190)` (idempotent because `chain.proceed()` resets `paddingBottom` to stock `dimension + iA02` on every `A1W()` call).
       - Call `vg.setTag(TAG_REELS_SCRUBBER_PADDED, true)`.
3. Update `applyReelsTranslationToReelTree(root: ViewGroup)`:
   - **Remove `translationY = -190f`** from all scrubber views (`0x7f0a1cab`, `0x7f0a1ca3`, `0x7f0a088e`, `0x7f0a0563`, `0x7f0a1c63`, `0x7f0a1c6b`) and from `SeekBar`/`ProgressBar` so touch events stay 100% inside `0x7f0a1cab`'s real layout bounds:
     - Remove the `else if (h in 1..120)` shift branch in `applyReelsTranslationToReelTree`.
     - Replace `shiftUnshiftedProgressBars` / `shiftAllSeekbarIds` with `padScrubberContainerIfNeeded(root)` (skipping `sBookmarksPanelRef?.get()`):
       - Locate `0x7f0a1cab` (`ViewGroup`) when `isShown`:
         - Ensure `translationY == 0f` on `0x7f0a1cab` and its scrubber descendants (`0x7f0a1ca3`, `0x7f0a088e`, `0x7f0a0563`, `SeekBar`, `ProgressBar`).
         - If `vg.getTag(TAG_REELS_SCRUBBER_PADDED) == null`:
           - Record `vg.paddingBottom` in `sPaddedScrubberViews` if not already present.
           - Call `vg.setPaddingRelative(vg.paddingStart, vg.paddingTop, vg.paddingEnd, vg.paddingBottom + 190)`.
           - Call `vg.setTag(TAG_REELS_SCRUBBER_PADDED, true)`.
           - Call `unclipAncestors(vg, root)`.
4. Update `resetShiftedReelsOverlays()`:
   - Synchronize on `sPaddedScrubberViews`, iterate entries `(vg, origPaddingBottom)`:
     - `vg.setTag(TAG_REELS_SCRUBBER_PADDED, null)`
     - If `vg.paddingBottom != origPaddingBottom`, call `vg.setPaddingRelative(vg.paddingStart, vg.paddingTop, vg.paddingEnd, origPaddingBottom)`.
   - Clear `sPaddedScrubberViews`.

### 3.2 Pause Overlap Fix

In `hasReelsOverlaySemantics(view: View)` and `shiftSemanticOverlayChildren(...)`:
- Never match or shift scrubber views (`view.id in REELS_SCRUBBER_IDS`) or views containing `0x7f0a1cab` / `SeekBar`.
- Extend `hasReelsOverlaySemantics(view: View)`:
  - If `view.id in REELS_SCRUBBER_IDS` return `false`.
  - Keep `contentDescription` checks (`"like"`, `"comment"`, `"share"`, `"audio"`, `"follow"`).
  - Also return `true` when `view is android.widget.TextView && !view.text.isNullOrBlank()` so paused-state metadata `ViewGroup`s (captions, author/timestamp labels) are shifted up without shifting the full-width dimming scrim.
- In `shiftSemanticOverlayChildren(container, overlayWidth, reelButton)`:
  - If a child `c` is in `REELS_SCRUBBER_IDS` or contains `0x7f0a1cab` / `SeekBar`:
    - Do NOT call `shiftOverlayView(c, reelButton)`.
    - If `c is ViewGroup` and has non-scrubber semantic `ViewGroup` children, recurse into `shiftSemanticOverlayChildren(c, overlayWidth, reelButton)`.

### 3.3 Dimmed Strip Removal (`X.09rv` & `X.09rf`)

**Verified Decompiled Signatures (`classes5.dex`):**
- `X.09rv` (`C446609rv`):
  - Field: `public final float A03;` (controls `dynamic_bar_background_transition_key` dimmed bar when `f > 0.0f` in `render(02E7)`).
  - Constructor: `C446609rv(FbUserSession, InterfaceC469309w7, 01I1, EnumC439809qm, InterfaceC53360AHh, float, boolean x 10)`.
  - Static method: `public static final ShapeDrawable A0B(138N r6, boolean z, boolean z2)` (returns non-null `ShapeDrawable`).
- `X.09rf` (`AbstractC445009rf`):
  - Static method: `public static final GradientDrawable A04(01Of r6, InterfaceC53360AHh interfaceC53360AHh, boolean z)` (returns `GradientDrawable`).

**Implementation in `onPackageReady()` of `FacebookHook.kt`:**
1. Hook `X.09rv` declared constructors:
   - After `chain.proceed()`, if `isReelsActive`:
     - Set `A03` on `chain.thisObject` to `0f` via `findFieldRecursively(cls, "A03")?.setFloat(chain.thisObject, 0f)`.
2. Hook `X.09rv.A0B(...)` (match `m.name == "A0B" && m.parameterTypes.size == 3`):
   - After `val result = chain.proceed()`:
     - If `isReelsActive`, `(result as? android.graphics.drawable.Drawable)?.alpha = 0` (never return `null`).
     - Return `result`.
3. Hook `X.09rf.A04(...)` (match `m.name == "A04" && m.parameterTypes.size == 3`):
   - After `val result = chain.proceed()`:
     - If `isReelsActive`, `(result as? android.graphics.drawable.Drawable)?.alpha = 0` (never return `null`).
     - Return `result`.

---

## Verification Plan

1. Run `git diff --stat 6e5b601a` on the 3 nav files to confirm exact revert.
2. Build the project with `./gradlew assembleDebug` (or project's Gradle build task) to verify zero compilation errors.
