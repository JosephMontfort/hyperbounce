# Architectural Implementation Plan — Round 4

**Scope:**
1. **Part 1:** Morphing Circular Button Positioning, Trajectory & 120 FPS Optimization (`FacebookHook.kt`, `FacebookNavBarHost.kt`, `LiquidNavigationBar.kt`, `LiquidGlassViewBackdrop.kt`)
2. **Part 2:** Reels Dimmed Scrim, Progress Bar / SeekBar & Pause/Resume Persistence (`FacebookHook.kt`)

> **CRITICAL CONSTRAINT:** Do **NOT** modify `SettingsRepository.kt` or `HyperBounceApp.kt`.

---

## Part 1: Morphing Circular Button Positioning, Trajectory & 120 FPS Optimization

### 1.1 `FacebookHook.kt` — Track `bookmarks_panel` Width
- **File:** `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`
- **Companion object (`~line 44`):**
  - Add `val drawerWidthPxState = mutableIntStateOf(0)` (ensure `import androidx.compose.runtime.mutableIntStateOf` is present).
- **`X.01zQ` constructor hook (`~line 567`):**
  - Reset `drawerWidthPxState.intValue = 0` alongside `drawerProgressState.floatValue = 0f`.
- **`X.01zQ.onScrollChanged` hook (`~line 646`):**
  - Inside `if (bookmarksView != null && mainView != null)`, update `drawerWidthPxState.intValue = bookmarksView.width` whenever `bookmarksView.width > 0` (or unconditionally `bookmarksView.width`).

### 1.2 `FacebookNavBarHost.kt` — Pass Providers & Memoize Tab Lists
- **File:** `app/src/main/java/dev/hyperios/app/nav/FacebookNavBarHost.kt`
- **Memoize `tabs` projections (`~lines 548–580`):**
  - Replace inline `tabs.map { ... }` allocations on every recomposition with `remember(tabs)`:
    ```kotlin
    val inactiveBitmaps = remember(tabs) { tabs.map { it.inactiveBitmap } }
    val activeBitmaps = remember(tabs) { tabs.map { it.activeBitmap ?: it.inactiveBitmap } }
    val badgeCountList = remember(tabs) { tabs.map { it.badgeCount } }
    val badgeDotList = remember(tabs) { tabs.map { it.hasBadgeDot } }
    ```
- **State-read deferral (`~line 544`, `~line 582`):**
  - Do **not** read `FacebookHook.drawerProgressState.floatValue` in `FacebookNavBarHost` composition.
  - Pass `drawerProgressProvider = { FacebookHook.drawerProgressState.floatValue }` and `drawerWidthPx = FacebookHook.drawerWidthPxState.intValue` to `LiquidNavigationBar(...)`.

### 1.3 `LiquidNavigationBar.kt` — Drawer-Flush Positioning, Trajectory & Composition-Free 120 FPS Morph
- **File:** `app/src/main/java/dev/hyperios/app/nav/LiquidNavigationBar.kt`
- **Function signature (`lines 81–112`):**
  - Add `drawerProgressProvider: (() -> Float)? = null` and `drawerWidthPx: Int = 0` (retain `drawerProgress: Float = 0f` for backward compatibility with other callers).
- **Remove `animateFloatAsState(spring)` fighting `X.01zQ.onScrollChanged` (`lines 150–159`):**
  - Define a state-backed provider:
    ```kotlin
    val progressProvider: () -> Float = remember(drawerProgressProvider, drawerProgress) {
        { (drawerProgressProvider?.invoke() ?: drawerProgress).coerceIn(0f, 1f) }
    }
    val isMorphed by remember(progressProvider) {
        derivedStateOf { progressProvider() > 0.5f }
    }
    ```
  - Use `isMorphed` only for discrete click-routing / `onGloballyPositioned` gating (`!isMorphed`) and `clickable` enablement.
- **Vertical `bottomMargin`-aware lower resting position (`lines 175–180`):**
  - Compute:
    ```kotlin
    val maxSafeDropDp = (bottomPadding - 8.dp).coerceAtLeast(0.dp)
    val targetMorphedDropDp = minOf(
        maxSafeDropDp,
        10.dp + (maxSafeDropDp - 10.dp).coerceAtLeast(0.dp) * 0.25f,
    )
    val targetMorphedDropPx = with(density) { targetMorphedDropDp.toPx() }
    ```
- **Flush inside `bookmarks_panel` right edge + "Right-Then-Down" trajectory (`lines 198–210`):**
  - Replace `arcLiftPx = sin(morphProgress * PI) * 28.dp` with decoupled X/Y easing evaluated inside `graphicsLayer` (and `Modifier.layout` / `draw`):
    - `easeX = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)` (leads horizontally)
    - `easeY = CubicBezierEasing(0.50f, 0f, 0.75f, 1f)` (glides downward slightly later)
  - Inside `BoxWithConstraints` (`containerWidthPx = constraints.maxWidth.toFloat()`):
    - `val effectiveDrawerWidthPx = if (drawerWidthPx > 0) drawerWidthPx.toFloat() else (containerWidthPx - with(density) { 4.dp.toPx() })`
    - `val circleDiameterPx = with(density) { 52.dp.toPx() }`
    - `val innerMarginPx = with(density) { 12.dp.toPx() }`
    - `val targetTx = if (isRootLtr) { (effectiveDrawerWidthPx - innerMarginPx - circleDiameterPx / 2f) - (containerWidthPx / 2f) } else { (containerWidthPx / 2f) - (effectiveDrawerWidthPx - innerMarginPx - circleDiameterPx / 2f) }`
  - Inside `graphicsLayer` on the outer `Box` (`~line 214`):
    ```kotlin
    val p = progressProvider()
    val effShrink = shrinkAnim.value * (1f - p)
    val currentShiftY = lerp(0f, maxShiftY, effShrink)
    translationX = lerp(0f, targetTx, easeX.transform(p))
    translationY = lerp(currentShiftY, targetMorphedDropPx, easeY.transform(p))
    ```
- **Eliminate per-frame recomposition & relayout of inner tab `Row`s:**
  - Keep `tabsWidthDp` and `currentBarHeight` computed from `shrinkAnim.value` (or fixed base + scale) and give the inner tab `Row`s fixed `.requiredWidth(tabsWidthDp).requiredHeight(currentBarHeight)` (and `requiredHeight(currentBarHeight - 8.dp * currentScale)` for the pill/overlay rows) so tab icons/badges never re-measure during morph.
  - Size & clip the outer morphing `Box` via `Modifier.layout { measurable, constraints -> ... }` reading `progressProvider()` during the layout/draw phases to interpolate `morphedWidthPx` (`tabsWidthPx -> 52.dp`) and `morphedHeightPx` (`currentBarHeightPx -> 52.dp`), and update `shape`/`clip` (`RoundedCornerShape(lerp(currentCornerRadius.value, 26f, p).dp)`) inside `graphicsLayer` / `drawBackdrop` `shape` lambda rather than recomposing `Box`.
  - Apply `tabsAlpha = (1f - p * 2.2f).coerceIn(0f, 1f)` and `hamburgerAlpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)` inside `graphicsLayer { alpha = ... }` and `Canvas` `drawLine(..., alpha = ...)` so the hamburger `Canvas` stays composed and draws with `alpha = 0f` when closed instead of adding/removing composition nodes mid-gesture.

### 1.4 `LiquidGlassViewBackdrop.kt` — Zero-Allocation 120 FPS Capture Loop
- **File:** `app/src/main/java/dev/hyperios/app/nav/glass/LiquidGlassViewBackdrop.kt`
- **Pre-allocate per-instance scratch objects (near `line 58`):**
  - `private val locationBuf = IntArray(2)`
  - `private val surfaceLocationBuf = IntArray(2)`
  - `private val bgPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER); color = Color.parseColor("#40000000") }`
  - Replace `val location = IntArray(2)` (`line 122`), `val surfaceLocation = IntArray(2)` (`lines 210, 349`), and `val bgPaint = Paint()...` (`line 155`) with these pre-allocated fields.
- **Reuse larger bitmaps in `ensureBuffers(width, height)` (`lines 428–433`):**
  - Change exact-equality check to capacity check so shrinking the nav bar to `52.dp` does not allocate 2 new bitmaps per frame:
    ```kotlin
    private fun ensureBuffers(width: Int, height: Int) {
        val f = front
        val b = back
        if (f == null || f.isRecycled || f.width < width || f.height < height ||
            b == null || b.isRecycled || b.width < width || b.height < height
        ) {
            front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            back = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }
    ```
  - Note: `captureOncePerFrame()` already calls `target.eraseColor(Color.TRANSPARENT)` and `canvas.clipRect(0, 0, width, height)`, and `drawBackdrop` draws at `topLeft = -drawOffset * 0.5f`, so larger backing bitmaps remain transparent outside `(scaledWidth, scaledHeight)` without visual artifacts.

---

## Part 2: Reels Dimmed Scrim, Progress Bar / SeekBar & Pause/Resume Persistence (`FacebookHook.kt`)

All changes in Part 2 are in `app/src/main/java/dev/hyperios/app/nav/FacebookHook.kt`.

### 2.1 Tags & Constants (`companion object`)
- Add view tag constants:
  - `private const val TAG_REELS_SHIFTED = 0x7F0B9906`
  - `private const val TAG_REELS_UNCLIPPED = 0x7F0B9907`
  - `private val REELS_SEEKBAR_IDS = intArrayOf(0x7f0a1cab, 0x7f0a1ca3, 0x7f0a1c63, 0x7f0a1c6b, 0x7f0a088e)`

### 2.2 Dimmed Gradient Scrim Fix (`Subchild 0` Full-Width Litho `ComponentHost`)
- **Problem:** `Subchild 0` is a full-width (`1220px`) Litho `ComponentHost` carrying the gradient `Drawable` MountItem (`C446609rv` / `C406109kB`) directly on `Subchild 0`, while wrapping narrower `Left metadata` (`1076px`) and `Right buttons` (`144px`) children. Shifting `Subchild 0` lifts the gradient scrim.
- **Helpers to add:**
  - `private fun hasSemanticViewGroupChild(vg: ViewGroup): Boolean` — returns `true` if any direct child `c` is a `ViewGroup` with `hasReelsOverlaySemantics(c)`.
  - `private fun shiftOverlayView(v: View, reelButton: ViewGroup): Boolean` — sets `v.translationY = -190f`, `v.setTag(TAG_REELS_SHIFTED, true)`, adds `v` to `sShiftedReelViews`, and attaches `attachReelsLayoutListener(v, reelButton)`.
  - `private fun shiftSemanticOverlayChildren(container: ViewGroup, overlayWidth: Int, reelButton: ViewGroup): Boolean`:
    - Iterates `i in 0 until container.childCount`.
    - For each child `c` where `c is ViewGroup && hasReelsOverlaySemantics(c)`:
      - If `overlayWidth > 0 && c.width >= (overlayWidth * 0.95f).toInt() && hasSemanticViewGroupChild(c)`:
        - Keep `c.translationY = 0f` (do **not** add to `sShiftedReelViews`, clear `TAG_REELS_SHIFTED` if set), unclip `c` (`c.clipChildren = false`, `c.clipToPadding = false`, `c.setTag(TAG_REELS_UNCLIPPED, true)`), and recurse `shiftSemanticOverlayChildren(c, overlayWidth, reelButton)`.
      - Else:
        - Call `shiftOverlayView(c, reelButton)`.
- **Update `applyReelsTranslationToReelTree` (`~lines 292–317`):**
  - Set `val overlayWidth = overlayContainer.width.takeIf { it > 0 } ?: reelButton.width`.
  - When `sc is ViewGroup && hasReelsOverlaySemantics(sc)`:
    - If `overlayWidth > 0 && sc.width >= (overlayWidth * 0.95f).toInt() && hasSemanticViewGroupChild(sc)`:
      - Keep `sc.translationY = 0f`, unclip `sc` (`clipChildren = false`, `clipToPadding = false`, `sc.setTag(TAG_REELS_UNCLIPPED, true)`), attach `attachReelsLayoutListener(sc, reelButton)` & `attachReelsHierarchyListener(sc, overlayContainer, reelButton)`, and call `shiftSemanticOverlayChildren(sc, overlayWidth, reelButton)`.
    - Else:
      - Shift `sc` via `shiftOverlayView(sc, reelButton)`.

### 2.3 Progress Bar / SeekBar Fix
- **Unclipping helper:**
  - `private fun unclipAncestors(view: View, stopAt: View)`:
    - Walks `var p = view.parent` up through `stopAt` (inclusive), setting `p.clipChildren = false`, `p.clipToPadding = false`, and `p.setTag(TAG_REELS_UNCLIPPED, true)` on each `ViewGroup`.
- **Update `shiftUnshiftedProgressBars` (`lines 108–140`):**
  - Match `view is SeekBar || (view is ProgressBar && !view.isIndeterminate) || view.id in REELS_SEEKBAR_IDS`.
  - Raise height cap from `1..150` to `1..300` (accommodating `SeekBar` `0x7f0a1ca3` at `75.0dp` = `225px`–`300px`).
  - When matched & shifted: call `unclipAncestors(view, reelButton)`, set `view.translationY = -190f`, `view.setTag(TAG_REELS_SHIFTED, true)`, and add to `sShiftedReelViews`.
- **Add recursive ID fallback `shiftAllSeekbarIds(view: View, root: ViewGroup): Boolean`:**
  - Replaces single-match `root.findViewById<View>(id)` loop (`lines 324–334`) so **all** visible instances in `ViewPager2` / `RecyclerView` recycled pages are shifted, tagged with `TAG_REELS_SHIFTED`, added to `sShiftedReelViews`, and unclipped via `unclipAncestors(view, root)`.

### 2.4 Pause -> Resume Persistence (`View.setTranslationY`, `ViewGroup.setClipChildren`, `onChildViewRemoved`)
- **In `onPackageReady()`:**
  - Hook `View::class.java.getDeclaredMethod("setTranslationY", Float::class.javaPrimitiveType)`:
    - Fast-path: `if (!isReelsActive) return@intercept chain.proceed()`
    - If `(chain.thisObject as? View)?.getTag(TAG_REELS_SHIFTED) != null && (chain.args[0] as? Float) != -190f`, clamp `chain.args[0] = -190f`.
  - Hook `ViewGroup::class.java.getDeclaredMethod("setClipChildren", Boolean::class.javaPrimitiveType)`:
    - Fast-path: `if (!isReelsActive) return@intercept chain.proceed()`
    - If `(chain.args[0] as? Boolean) == true && (chain.thisObject as? ViewGroup)?.getTag(TAG_REELS_UNCLIPPED) != null`, force `chain.args[0] = false`.
- **Update `attachReelsHierarchyListener.onChildViewRemoved` (`line 183`):**
  - Post re-application when Litho/Reels swaps children on pause/resume:
    ```kotlin
    override fun onChildViewRemoved(parent: View?, child: View?) {
        if (isReelsActive && reelButton.isAttachedToWindow) {
            reelButton.post {
                if (isReelsActive && reelButton.isAttachedToWindow) {
                    applyReelsTranslationToReelTree(reelButton)
                }
            }
        }
    }
    ```
- **Update `resetShiftedReelsOverlays()` (`lines 338–351`):**
  - Note: `isReelsActive` is already `false` before `resetShiftedReelsOverlays()` runs (`setReelsActive` sets `isReelsActive = active` at `line 190`).
  - Clear `v.setTag(TAG_REELS_SHIFTED, null)` on every view in `sShiftedReelViews` before resetting `v.translationY = 0f` and clearing `sShiftedReelViews`.
- **Also mark `unclipAncestors` / `TAG_REELS_UNCLIPPED` on `reelButton`, `reelButton.parent`, and `overlayContainer`** inside `applyReelsTranslationToReelTree` (`lines 252–257, 284–285`) so `setClipChildren(true)` is blocked while Reels is active.

---

## Verification Plan
After implementation, run `./gradlew assembleDebug` (or the project's Gradle build task) from `/data/data/com.termux/files/home/hyperbounce-hos4` and verify zero compilation errors and that `git status` / `git diff` confirms `SettingsRepository.kt` and `HyperBounceApp.kt` are untouched.
