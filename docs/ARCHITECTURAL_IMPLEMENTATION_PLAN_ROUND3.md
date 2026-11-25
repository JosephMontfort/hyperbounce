# Architectural Implementation Plan (Round 3): Reels Scrim/Progress/Pause-Resume & Left Drawer Morphing Nav Bar

**Author:** Architectural Planner Subagent  
**Date:** November 25, 2026  
**Target Branch:** `hos4`  
**Workspace:** `/data/data/com.termux/files/home/hyperbounce-hos4`  
**Status:** READY FOR IMPLEMENTATION  

---

## Executive Summary

This specification defines the exact, root-cause implementation for Round 3 across two parts:
1. **Facebook Reels Overlay Polish (`FacebookHook.kt`):**
   - **Dimming Scrim Fix:** Stop shifting `Subchild 1` (300px bottom gradient scrim, a Litho `ComponentHost` with `childCount > 0`) by classifying `Subchild 0` via `sc is ViewGroup && hasReelsOverlaySemantics(sc)` (`-190f`), scrubber/progress container via `h in 1..120` (`-190f`), and everything else (`0f`).
   - **Progress Bar / SeekBar Fix:** Remove `sc.childCount == 0` from the `h in 1..120` branch, add real APK IDs `0x7f0a1ca3` (`C25440Bm9 extends SeekBar`) and `0x7f0a1cab` (container) to `seekbarIds`, and add a targeted `reelButton` fallback for `SeekBar` or horizontal `!isIndeterminate` `ProgressBar` (`height in 1..150`, `bottom >= reelButton.height - 250` in `reelButton`-relative coordinates) guarded against double-shifting via `sShiftedReelViews` ancestor checks.
   - **Pause -> Resume Persistence:** Attach deduplicated `OnLayoutChangeListener` (tag `0x7F0B9904`) on `overlayContainer` and shifted `sc`, plus deduplicated `OnHierarchyChangeListener` (tag `0x7F0B9905`) on `overlayContainer` and `reelButton` that re-runs `applyReelsTranslationToReelTree(reelButton)` and arms a bounded 5-frame self-removing `OnPreDrawListener` on `overlayContainer`.
2. **Facebook Left Side Drawer & Morphing Circular Nav Bar (`FacebookHook.kt`, `LiquidNavigationBar.kt`, `FacebookNavBarHost.kt`):**
   - **Drawer Hooks (`FacebookHook.kt`):** Hook `X.01zQ` (`SlidingPanelScrollView`) constructors and `onScrollChanged(int, int, int, int)` to track `drawerProgressState.floatValue` (`0f..1f`) from `A0C` panel bounds, and expose `@JvmStatic fun closeDrawer()`.
   - **Morphing Circular Nav Bar (`LiquidNavigationBar.kt` & `FacebookNavBarHost.kt`):** Add backward-compatible `drawerProgress: Float = 0f` and `onDrawerCloseClick: (() -> Unit)? = null` parameters to `LiquidNavigationBar`. Smoothly morph the floating pill into a `52.dp` circular hamburger button at bottom-right (`16.dp` margin, RTL-aware) with a curved upward Y-arc, cross-fading tabs/pill out and a `Canvas` 3-dash hamburger icon in.

**Scope Constraint:** Do **NOT** modify `SettingsRepository.kt` or `HyperBounceApp.kt`. All changes are strictly scoped to `FacebookHook.kt`, `LiquidNavigationBar.kt`, and `FacebookNavBarHost.kt`, preserving full backward compatibility for Instagram, WhatsApp, and YouTube.

---

## Part 1: Reels Scrim, Progress Bar, & Pause/Resume Fix (`FacebookHook.kt`)

**Target File:** `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`

### 1.1 Dimming Scrim & Scrubber Container Classification (`applyReelsTranslationToReelTree`, lines 142–166)
Replace the `overlayContainer.childCount` loop body so `Subchild 1` (300px dimming scrim) is never shifted:
```kotlin
for (j in 0 until overlayContainer.childCount) {
    val sc = overlayContainer.getChildAt(j) ?: continue
    val h = if (sc.height > 0) sc.height else (sc.layoutParams?.height ?: 0)
    if (sc is ViewGroup && hasReelsOverlaySemantics(sc)) {
        // Subchild 0: Metadata + Side Buttons -> Shift Up
        if (sc.translationY != -190f) {
            sc.translationY = -190f
            sShiftedReelViews.add(sc)
        }
        attachReelsLayoutListener(sc, reelButton)
        didShiftAny = true
    } else if (h in 1..120) {
        // Subchild 2: Progress bar / Scrubber container (height 1..120, regardless of childCount) -> Shift Up
        if (sc.translationY != -190f) {
            sc.translationY = -190f
            sShiftedReelViews.add(sc)
        }
        attachReelsLayoutListener(sc, reelButton)
        didShiftAny = true
    } else {
        // Subchild 1: 300px Dark Dimming Scrim -> MUST REMAIN AT 0f GROUNDED!
        if (sc.translationY != 0f) {
            sc.translationY = 0f
        }
    }
}
```

### 1.2 Progress Bar / SeekBar IDs & Targeted Fallback (`applyReelsTranslationToReelTree`, lines 169–180)
1. Update `seekbarIds` to include the actual APK IDs (`0x7f0a1ca3`, `0x7f0a1cab`) alongside legacy IDs:
   ```kotlin
   val seekbarIds = intArrayOf(0x7f0a1ca3, 0x7f0a1cab, 0x7f0a1c63, 0x7f0a1c6b, 0x7f0a088e)
   ```
   When shifting `v = root.findViewById<View>(id)`, guard against double-shifting by checking `!hasShiftedAncestor(v, root)`.
2. Add helper `hasShiftedAncestor(view: View, stopAt: View): Boolean`:
   ```kotlin
   private fun hasShiftedAncestor(view: View, stopAt: View): Boolean {
       var p = view.parent
       while (p is View) {
           if (sShiftedReelViews.contains(p)) return true
           if (p === stopAt) break
           p = p.parent
       }
       return false
   }
   ```
3. Add targeted fallback inside each `reelButton` for `SeekBar` or horizontal non-indeterminate `ProgressBar` (`FbShortsProgressBar` / `C0xDO` mounts an `android.widget.ProgressBar` with no ID):
   ```kotlin
   private fun shiftUnshiftedProgressBars(
       view: View,
       reelButton: ViewGroup,
       offsetTopInReel: Int
   ): Boolean {
       var shifted = false
       val isTarget = view is android.widget.SeekBar ||
           (view is android.widget.ProgressBar && !view.isIndeterminate)
       if (isTarget) {
           val h = if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)
           val bottomInReel = offsetTopInReel + view.height
           val rbHeight = reelButton.height
           if (h in 1..150 && (rbHeight <= 0 || bottomInReel >= rbHeight - 250)) {
               if (!sShiftedReelViews.contains(view) && !hasShiftedAncestor(view, reelButton)) {
                   if (view.translationY != -190f) {
                       view.translationY = -190f
                       sShiftedReelViews.add(view)
                   }
                   shifted = true
               }
           }
           return shifted
       }
       if (view is ViewGroup) {
           for (i in 0 until view.childCount) {
               val child = view.getChildAt(i) ?: continue
               if (shiftUnshiftedProgressBars(child, reelButton, offsetTopInReel + child.top)) {
                   shifted = true
               }
           }
       }
       return shifted
   }
   ```
   Call `if (shiftUnshiftedProgressBars(reelButton, reelButton, 0)) didShiftAny = true` at the end of each `reelButton` iteration.

### 1.3 Pause -> Resume Persistence Listeners (Tags `0x7F0B9904` & `0x7F0B9905`)
Litho remounts/resets `overlayContainer` and `Subchild 0` on pause/resume without changing the outer `RecyclerView` item bounds. Add two deduplicated listener helpers in `FacebookHook.companion object`:
- **`attachReelsLayoutListener(target: View, reelButton: ViewGroup)` (Tag `0x7F0B9904`):**
  - If `target.getTag(0x7F0B9904) != null`, return.
  - Set `target.setTag(0x7F0B9904, true)` and call `target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (isReelsActive && reelButton.isAttachedToWindow) applyReelsTranslationToReelTree(reelButton) }`.
  - Attach to `overlayContainer` and to each shifted `sc`.
- **`attachReelsHierarchyListener(target: ViewGroup, overlayContainer: ViewGroup, reelButton: ViewGroup)` (Tag `0x7F0B9905`):**
  - If `target.getTag(0x7F0B9905) != null`, return.
  - Set `target.setTag(0x7F0B9905, true)` and attach `ViewGroup.OnHierarchyChangeListener`:
    - `onChildViewAdded(parent: View?, child: View?)`:
      - If `!isReelsActive || !reelButton.isAttachedToWindow`, return.
      - Call `applyReelsTranslationToReelTree(reelButton)`.
      - Arm a bounded 5-frame self-removing `ViewTreeObserver.OnPreDrawListener` on `overlayContainer`:
        ```kotlin
        val vto = overlayContainer.viewTreeObserver
        if (vto.isAlive) {
            var frames = 0
            vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!isReelsActive || !overlayContainer.isAttachedToWindow || frames++ >= 5) {
                        if (overlayContainer.viewTreeObserver.isAlive) {
                            overlayContainer.viewTreeObserver.removeOnPreDrawListener(this)
                        }
                        return true
                    }
                    applyReelsTranslationToReelTree(reelButton)
                    return true
                }
            })
        }
        ```
    - `onChildViewRemoved(parent: View?, child: View?)`: no-op.
  - Attach to both `overlayContainer` and `reelButton` inside `applyReelsTranslationToReelTree`.

---

## Part 2: Facebook Left Side Drawer & Morphing Circular Nav Bar

### 2.1 Drawer Hooks (`FacebookHook.kt`)
**Target File:** `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`

1. **Companion State & `closeDrawer()`:**
   Add to `FacebookHook.companion object`:
   ```kotlin
   val drawerProgressState = androidx.compose.runtime.mutableFloatStateOf(0f)
   private var sDrawerScrollViewRef: java.lang.ref.WeakReference<View>? = null
   private var sBookmarksPanelRef: java.lang.ref.WeakReference<View>? = null
   private var sMainPanelRef: java.lang.ref.WeakReference<View>? = null

   @JvmStatic
   fun closeDrawer() {
       val sv = sDrawerScrollViewRef?.get() ?: return
       sv.post {
           try {
               val closeMethod = sv.javaClass.methods.firstOrNull { m ->
                   m.name == "A0C" &&
                       m.parameterTypes.size == 3 &&
                       m.parameterTypes[0] == String::class.java &&
                       m.parameterTypes[1] == String::class.java &&
                       (m.parameterTypes[2] == Boolean::class.javaPrimitiveType || m.parameterTypes[2] == Boolean::class.javaObjectType)
               } ?: generateSequence<Class<*>>(sv.javaClass) { it.superclass }
                   .flatMap { it.declaredMethods.asSequence() }
                   .firstOrNull { m ->
                       m.name == "A0C" &&
                           m.parameterTypes.size == 3 &&
                           m.parameterTypes[0] == String::class.java &&
                           m.parameterTypes[1] == String::class.java &&
                           (m.parameterTypes[2] == Boolean::class.javaPrimitiveType || m.parameterTypes[2] == Boolean::class.javaObjectType)
                   }
               if (closeMethod != null) {
                   closeMethod.isAccessible = true
                   closeMethod.invoke(sv, "main_tab_panel", "sliding_panel_tap_button", true)
                   return@post
               }
           } catch (t: Throwable) {
               Log.w(TAG, "closeDrawer A0C invoke failed: ${t.message}")
           }
           val mainView = sMainPanelRef?.get()
           if (mainView != null && sv is android.widget.HorizontalScrollView) {
               sv.smoothScrollTo(mainView.left, 0)
           }
       }
   }
   ```
2. **Hook `X.01zQ` (`SlidingPanelScrollView`) in `onPackageReady()` (Section 5):**
   - Load `val slidingPanelClass = param.classLoader.loadClass("X.01zQ")` inside a `try/catch`.
   - **Constructors:** Hook all `slidingPanelClass.declaredConstructors`:
     - Store `sDrawerScrollViewRef = java.lang.ref.WeakReference(sv)`
     - Clear `sBookmarksPanelRef = null`, `sMainPanelRef = null`
     - Reset `drawerProgressState.floatValue = 0f`
   - **`onScrollChanged(Int, Int, Int, Int)`:** Hook `slidingPanelClass.getDeclaredMethod("onScrollChanged", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)` (falling back to superclass if declared on `HorizontalScrollView`/`View`, guarding `sv.javaClass.name == "X.01zQ"`):
     - Ensure `sDrawerScrollViewRef?.get() === sv` (update if stale).
     - If `sBookmarksPanelRef?.get()?.isAttachedToWindow != true` or `sMainPanelRef?.get()?.isAttachedToWindow != true`, reflect `A0C` (`List<*>`) on `sv` (recursively via `sv.javaClass` hierarchy):
       - For each entry `item` in `A0C`:
         - Resolve panel ID string from `item`'s `A07` field (`String?`), and fallback `A06` -> `A00` (`String?`) for `main_tab_panel`.
         - Resolve panel `View`: `item as? View`, or first `View`-typed declared field on `item` (e.g., `A02`/`A03`/`A04`).
         - If `a07 == "bookmarks_panel"` -> `sBookmarksPanelRef = WeakReference(panelView)`.
         - If `a07 == "main_tab_panel"` or `a06A00 == "main_tab_panel"` -> `sMainPanelRef = WeakReference(panelView)`.
     - Compute progress when both `bookmarksView` and `mainView` are non-null:
       ```kotlin
       val span = kotlin.math.abs(mainView.left - bookmarksView.left).toFloat()
       if (span > 1f) {
           drawerProgressState.floatValue =
               (kotlin.math.abs(sv.scrollX - mainView.left).toFloat() / span).coerceIn(0f, 1f)
       }
       ```

---

### 2.2 Morphing Circular Nav Bar (`LiquidNavigationBar.kt`)
**Target File:** `app/src/main/java/dev/hyperios/app/nav/LiquidNavigationBar.kt`

1. **Function Signature (lines 74–103):**
   Add two optional parameters at the end (preserving full backward compatibility for Instagram, WhatsApp, YouTube):
   ```kotlin
   drawerProgress: Float = 0f,
   onDrawerCloseClick: (() -> Unit)? = null,
   ```
2. **Smooth Morph & Shrink Coordination (around lines 141–164):**
   - Add `morphProgress`:
     ```kotlin
     val morphProgress by animateFloatAsState(
         targetValue = drawerProgress.coerceIn(0f, 1f),
         animationSpec = spring(
             dampingRatio = 0.68f,
             stiffness = 420f,
             visibilityThreshold = 0.001f,
         ),
         label = "drawerMorph",
     )
     val isMorphed = morphProgress > 0.5f
     ```
   - Coordinate shrink with morph:
     ```kotlin
     val effectiveShrinkProgress = shrinkProgress * (1f - morphProgress)
     val currentScale = lerp(1f, shrinkScale.coerceIn(0.3f, 0.95f), effectiveShrinkProgress)
     val currentShiftY = lerp(0f, maxShiftY, effectiveShrinkProgress)
     ```
   - Compute morphed dimensions:
     ```kotlin
     val tabsWidthDp = (tabs.size * currentTabWidth.value).dp
     val morphedWidth = lerp(tabsWidthDp.value, 52f, morphProgress).dp
     val morphedHeight = lerp(currentBarHeight.value, 52f, morphProgress).dp
     val morphedCornerRadius = lerp(currentCornerRadius.value, 26f, morphProgress).dp
     ```
3. **Horizontal Ease, Curved Upward Y-Arc, & Outer Container (lines 165–186):**
   - Change the outer `Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter)` to `BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter)` so `constraints.maxWidth` is available.
   - Compute RTL-aware `targetTx`, `morphTx`, and `morphTy`:
     ```kotlin
     val isRootLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
     val containerWidthPx = constraints.maxWidth.toFloat()
     val morphedWidthPx = with(density) { morphedWidth.toPx() }
     val edgeMarginPx = with(density) { 16.dp.toPx() }
     val targetTx = if (isRootLtr) {
         (containerWidthPx / 2f) - edgeMarginPx - (morphedWidthPx / 2f)
     } else {
         -((containerWidthPx / 2f) - edgeMarginPx - (morphedWidthPx / 2f))
     }
     val morphTx = lerp(0f, targetTx, FastOutSlowInEasing.transform(morphProgress))
     val arcLiftPx = with(density) { (kotlin.math.sin(morphProgress * kotlin.math.PI).toFloat() * 28f).dp.toPx() }
     val morphTy = lerp(currentShiftY, 0f, morphProgress) - arcLiftPx
     ```
   - Apply on the inner container `Box`:
     - `.padding(bottom = bottomPadding)`
     - `.graphicsLayer { translationX = morphTx; translationY = morphTy }`
     - `.onGloballyPositioned { coordinates -> if (!isMorphed) scrollShrinkState?.updateBounds(...) }`
     - `.width(morphedWidth)`
     - `.height(morphedHeight)`
     - When `isMorphed && onDrawerCloseClick != null`, attach `.clip(RoundedCornerShape(morphedCornerRadius)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDrawerCloseClick() }`.
4. **Shape, Cross-Fade, Hamburger Canvas, & Gesture Gating (lines 286–691):**
   - Replace `currentCornerRadius` in `barShape` (`!liquidGlass`, line 287) and `RoundedCornerShape(...)` (`liquidGlass`, lines 463, 481, 529, 570) with `morphedCornerRadius`, and replace `currentBarHeight` on the bar surface `Row`/`Box` height (lines 352, 488) with `morphedHeight`.
   - Compute cross-fade alphas:
     ```kotlin
     val tabsAlpha = (1f - morphProgress * 2.2f).coerceIn(0f, 1f)
     val hamburgerAlpha = ((morphProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
     ```
   - **Tabs & Pill Alpha + Gesture Gating:**
     - Apply `.graphicsLayer { alpha = tabsAlpha }` to the tab `Row`s and pill `Box`es (keeping the bar background surface at full opacity so the circular glass/classic surface stays solid).
     - Gate gestures with `if (!isMorphed)`:
       - Do not attach `.then(interactiveHighlight.gestureModifier).then(dampedDragAnimation.modifier)` when `isMorphed`.
       - Pass `onClick = if (!isMorphed) { { onTabSelected(index); dampedDragAnimation.animateToValue(index.toFloat()) } } else null` to `LiquidBottomTab`.
   - **Hamburger Icon Overlay:**
     Inside the inner `BoxWithConstraints` (after both `!liquidGlass` and `liquidGlass` branches), when `hamburgerAlpha > 0.001f`, draw a centered `Canvas(Modifier.fillMaxSize())`:
     ```kotlin
     if (hamburgerAlpha > 0.001f) {
         val iconColor = (if (isLightTheme) Color(0xFF1C1C1E) else Color.White).copy(alpha = hamburgerAlpha)
         Canvas(modifier = Modifier.fillMaxSize()) {
             val lineW = 18.dp.toPx()
             val strokeW = 2.2.dp.toPx()
             val gap = 5.5.dp.toPx()
             val cx = size.width / 2f
             val cy = size.height / 2f
             val startX = cx - lineW / 2f
             val endX = cx + lineW / 2f
             for (offset in floatArrayOf(-gap, 0f, gap)) {
                 drawLine(
                     color = iconColor,
                     start = Offset(startX, cy + offset),
                     end = Offset(endX, cy + offset),
                     strokeWidth = strokeW,
                     cap = StrokeCap.Round,
                 )
             }
         }
     }
     ```

---

### 2.3 Wiring in `FacebookNavBarHost.kt`
**Target File:** `app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`

In `FacebookNavBarHost` (lines 544–580), pass `drawerProgress` and `onDrawerCloseClick` to `LiquidNavigationBar`:
```kotlin
val drawerProgress = FacebookHook.drawerProgressState.floatValue

LiquidNavigationBar(
    ...,
    enablePixelCopy = false,
    drawerProgress = drawerProgress,
    onDrawerCloseClick = { FacebookHook.closeDrawer() },
)
```

---

## Verification Plan
- Confirm `SettingsRepository.kt` and `HyperBounceApp.kt` are **untouched** (`git status` / `git diff`).
- Confirm no other callers of `LiquidNavigationBar` (Instagram, WhatsApp, YouTube) are broken—new parameters have defaults `drawerProgress: Float = 0f` and `onDrawerCloseClick: (() -> Unit)? = null`.
- Per `AGENT_RULES.md`, do not compile locally on Termux; push to GitHub Actions to build and verify.
