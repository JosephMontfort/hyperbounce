# Architectural & Implementation Plan: WhatsApp FAB Shifting Fix & 5th Swipeable "Profile" Tab

**Target Repository:** `/data/data/com.termux/files/home/hyperbounce-hos4` (branch `hos4`)  
**Scope:**
- **Part A:** Fix WhatsApp FAB upward shifting (`WhatsAppHook.kt`) so all FABs and tooltips stay positioned cleanly above the floating Liquid Glass navigation bar despite `LX/N8Z.onPreDraw()` resetting `translationY` every frame.
- **Part B:** Enable WhatsApp's native 5th swipeable `"You"` / Profile tab (Tab ID `900` -> `SettingsFragment`) via `X.0wf` hooks (`WhatsAppHook.kt`), map it to the authentic iOS Profile icons (`WhatsAppNavBarHost.kt`), ensure 5-tab bar width fits cleanly on screen (`ModuleDefaults.kt`, `WhatsAppNavBarHost.kt`), and handle dynamic `WDSBottomBar` menu population (`WhatsAppNavBarHost.kt`).

---

## Part A: WhatsApp FAB Shifting Fix (`WhatsAppHook.kt`)

### 1. Root Cause Analysis
1. **`LX/N8Z.onPreDraw()` resets `translationY` every frame:**
   - `HomeNavigationActivity` superclass `LX/0q2` registers a persistent `ViewTreeObserver.OnPreDrawListener` (`LX/N8Z`) on `@android:id/content`.
   - On every frame before draw, `LX/N8Z.onPreDraw()` iterates over children of `@android:id/content` matching `LX/0q2.A0P` (`[0x7f0b1646, 0x7f0b1649, 0x7f0b163c, 0x7f0b1647, 0x7f0b164b, 0x7f0b3489]`) and unconditionally calls `view.setTranslationY(0f)`.
   - Previously, `WhatsAppHook.kt` only adjusted `translationY` in `View.onAttachedToWindow` and `scanAndAdjustFabs(rootGroup)` inside `executeUIHeist()`, which was immediately overwritten by `LX/N8Z.onPreDraw()` on the next frame.
2. **XML Bottom-Margin Stacking Already Baked In:**
   - `fab` (`0x7f0b1646`, `WDSFab`): stock `layout_marginBottom = 16.0dp`
   - `fab_second` (`0x7f0b1649`, `WDSFab`, Updates/Calls secondary): stock `layout_marginBottom = 88.0dp`
   - `extended_mini_fab` (`0x7f0b163c`, `ExtendedMiniFab`, Ask Meta AI): stock `layout_marginBottom = 92.0dp`
   - `fabText` (`0x7f0b1647`, `WDSExtendedFab`)
   - `fab_tool_tip` (`0x7f0b164b`)
   - `second_fab_tool_tip` (`0x7f0b3489`)
   - `fab_request_payment` (`0x7f0b1648`)
   - Because `extended_mini_fab` (`92dp`) and `fab_second` (`88dp`) already include their vertical offset above `fab` (`16dp`) in XML, adding an extra `+ 56f + 12f` to `extended_mini_fab` double-stacks it. All target FABs and tooltips must receive the **exact same** `-fabShiftPx` upward `translationY`.

### 2. Implementation Specification (`WhatsAppHook.kt`)

#### 2.1 Update Target FAB IDs and Names
Add the missing IDs from `LX/0q2.A0P` to `WhatsAppHook.Companion`:
```kotlin
private const val ID_EXTENDED_MINI_FAB = 0x7f0b163c // extended_mini_fab (Ask Meta AI)
private const val ID_MAIN_FAB = 0x7f0b1646          // fab
private const val ID_FAB_TEXT = 0x7f0b1647          // fabText (WDSExtendedFab)
private const val ID_FAB_REQUEST_PAYMENT = 0x7f0b1648 // fab_request_payment
private const val ID_FAB_SECOND = 0x7f0b1649        // fab_second
private const val ID_FAB_TOOL_TIP = 0x7f0b164b      // fab_tool_tip
private const val ID_SECOND_FAB_TOOL_TIP = 0x7f0b3489 // second_fab_tool_tip
```
Include all 7 IDs in `TARGET_FAB_IDS` and corresponding entry names (`"fab"`, `"extended_mini_fab"`, `"fabText"`, `"fab_second"`, `"fab_request_payment"`, `"fab_tool_tip"`, `"second_fab_tool_tip"`) in `TARGET_FAB_NAMES`.

#### 2.2 O(1) Fast-Path `isTargetFab(view: View)`
`View.setTranslationY(float)` is a hot path called on many non-FAB views every frame. Avoid calling `getResourceEntryName()` or string operations on non-FAB views on every call:
- Maintain a `ConcurrentHashMap<Int, Boolean>` (or `SparseBooleanArray` guarded by main-thread / lock) `fabIdCache` for resolved `view.id != View.NO_ID` fallback lookups.
- Order of checks in `isTargetFab(view: View): Boolean`:
  1. `val id = view.id`
  2. `if (id in TARGET_FAB_IDS) return true` — **O(1) fast path** for known IDs.
  3. If `id != View.NO_ID`:
     - Check `fabIdCache[id]?.let { return it }` — **O(1) cached path** for non-FABs and version-shifted IDs.
     - Fallback (executed at most once per unique `id`): check `view.javaClass.name` (ending with `.WDSFab`, `.ExtendedMiniFab`, `.WDSExtendedFab`) or `view.resources?.getResourceEntryName(id)` in `TARGET_FAB_NAMES` / `"meta"` substring, store result in `fabIdCache[id]`, and return it.
  4. If `id == View.NO_ID`: check `val cls = view.javaClass.name` only for `.WDSFab`, `.ExtendedMiniFab`, `.WDSExtendedFab` (fast suffix check, no resource lookup).

#### 2.3 Unified `computeFabShiftPx(view: View): Float?` & `adjustFabTranslation(view: View)`
- Compute:
  ```kotlin
  private fun computeFabShiftPx(view: View): Float? {
      val density = view.resources?.displayMetrics?.density ?: return null
      val barHeightDp = hyperBouncePrefs.appBarHeight("whatsapp")
      val bottomMarginDp = hyperBouncePrefs.appBottomMargin("whatsapp")
      val fabShiftDp = ((barHeightDp + bottomMarginDp + 14f) - 16f).coerceAtLeast(0f)
      return fabShiftDp * density
  }
  ```
- In `adjustFabTranslation(view: View)`:
  - Remove the `isMetaAiFab(view)` `+ 56f + 12f` branch entirely.
  - All target FABs/tooltips receive `val targetTy = -(computeFabShiftPx(view) ?: return)`.
  - Only assign `view.translationY = targetTy` if `view.translationY != targetTy`.

#### 2.4 Hook `android.view.View.setTranslationY(float)`
In `WhatsAppHook.onPackageReady()`, add:
```kotlin
try {
    module.hook(
        View::class.java.getDeclaredMethod("setTranslationY", Float::class.javaPrimitiveType)
    ).intercept { chain ->
        val view = chain.thisObject as View
        if (isTargetFab(view)) {
            val ty = chain.args[0] as? Float ?: 0f
            if (ty >= 0f) {
                computeFabShiftPx(view)?.let { shiftPx ->
                    chain.args[0] = -shiftPx
                }
            }
        }
        chain.proceed()
    }
} catch (t: Throwable) {
    module.log(Log.WARN, "HyperiOS", "Failed to hook View.setTranslationY: ${t.message}")
}
```
- **Why `ty >= 0f`:** When `LX/N8Z.onPreDraw()` (or any stock reset) passes `0f`, `chain.args[0]` is replaced with `-shiftPx` before `View.setTranslationY` executes, eliminating flicker and preventing `N8Z` from ever resetting the FABs to the bottom. When `adjustFabTranslation` or an upward animation passes a negative `ty`, it passes through untouched.
- **Keep `View.onAttachedToWindow` and `scanAndAdjustFabs(rootGroup)`:** Ensures FABs are positioned immediately on attach even before `LX/N8Z.onPreDraw()` fires.

---

## Part B: 5th Swipeable "Profile" Tab + Authentic iOS Profile Icon

### 1. Native Tab `900` Enablement (`WhatsAppHook.kt`)
WhatsApp's `HomeActivityKt`, `TabsPager` (`X.C1DO`), `X.C18120rc.A06()`, and `X.C21270wx` natively support Tab ID `900` (`"You"` -> `SettingsFragment`), gated by `X.0wf`:
- `X.0wf.A05()` returns `A01() == 6 || A01() == 7`
- `X.0wf.A01()` returns `7` if `A00()` else `this.A02.A0a(18564)`

In `WhatsAppHook.onPackageReady()`, add safe `XposedModule` hooks on `X.0wf`:
```kotlin
try {
    val tabConfigClass = param.classLoader.loadClass("X.0wf")
    try {
        val a05 = tabConfigClass.getDeclaredMethod("A05")
        module.hook(a05).intercept { true }
    } catch (t: Throwable) {
        module.log(Log.WARN, "HyperiOS", "Failed to hook X.0wf.A05: ${t.message}")
    }
    try {
        val a01 = tabConfigClass.getDeclaredMethod("A01")
        module.hook(a01).intercept { 7 }
    } catch (t: Throwable) {
        module.log(Log.WARN, "HyperiOS", "Failed to hook X.0wf.A01: ${t.message}")
    }
} catch (t: Throwable) {
    module.log(Log.WARN, "HyperiOS", "X.0wf not found (WhatsApp version mismatch?): ${t.message}")
}
```
- **Null-icon safety note:** If `C18120rc.A04(900)` returns `null` inside WhatsApp, `WDSBottomBar` is already hidden (`stockBar.alpha = 0f`), and `WhatsAppNavBarHost.kt` resolves the iOS Profile icon via Tiers 1–3 before Tier 4 (`extractStockDrawables`, which is also null-safe). If runtime testing reveals an NPE inside `C21270wx.A01` or `WDSBottomBar` when `C18120rc.A04(900)` is `null`, a follow-up hook on `C18120rc.A04(int)` for `900` can return a non-null fallback `Drawable`.

### 2. Authentic iOS Profile Icons (`WhatsAppNavBarHost.kt`)
Drawables already exist in `app/src/main/res/drawable/`:
- `ic_wa_ios_profile_outline.png`
- `ic_wa_ios_profile_filled.png`

In `WhatsAppNavBarHost.kt`:
1. Add icon definitions:
   ```kotlin
   private val ICON_PROFILE_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_profile_outline, "ic_wa_ios_profile_outline")
   private val ICON_PROFILE_FILLED = IosIconDef(R.drawable.ic_wa_ios_profile_filled, "ic_wa_ios_profile_filled")
   ```
2. Update `resolveIosIconPair(index: Int, label: String, entryName: String)`:
   - **Tier 1 (Semantic label matching):** Add `"you"` / `"profile"` matching before/alongside settings:
     ```kotlin
     l == "you" || l.contains("profile") || l.contains("perfil") || l.contains("profil") ->
         return Pair(ICON_PROFILE_OUTLINE, ICON_PROFILE_FILLED)
     ```
     (Keep `setting` / `ajuste` / `configur` / `einstell` mapped to `ICON_SETTINGS_*` or `ICON_PROFILE_*` — note `l == "you"` avoids false positives on substrings.)
   - **Tier 2 (View resource ID entry name):**
     ```kotlin
     e.contains("you") || e.contains("profile") ->
         return Pair(ICON_PROFILE_OUTLINE, ICON_PROFILE_FILLED)
     ```
   - **Tier 3 (Index fallback):** Update index `4` from `ICON_SETTINGS_*` to `ICON_PROFILE_*`:
     ```kotlin
     4 -> Pair(ICON_PROFILE_OUTLINE, ICON_PROFILE_FILLED)
     ```

### 3. Dynamic Tab Re-Extraction (`WhatsAppNavBarHost.kt`)
Currently `WhatsAppNavBarHost` runs:
```kotlin
val tabs = remember(stockBar, isDark) { extractWhatsAppTabs(stockBar, isDark) }
```
If `WDSBottomBar` populates its menu children *after* `executeUIHeist(stockBar)` first composes (or if a tab's visibility changes), `remember(stockBar, isDark)` will not re-run because `stockBar` is the same object reference.

**Fix:**
- Track a `menuVersion` (`mutableIntStateOf(0)`) in `WhatsAppNavBarHost`:
  - Attach a `DisposableEffect(stockBar)` that registers a `View.OnLayoutChangeListener` (or `ViewGroup.OnHierarchyChangeListener` on `stockBar` and `stockBar.getChildAt(0) as? ViewGroup`).
  - When a layout/hierarchy change occurs, compute the current visible tab count (or signature) of `stockBar.getChildAt(0) as? ViewGroup`; if it differs from the last observed signature/count, increment `menuVersion`.
- Update `remember`:
  ```kotlin
  val tabs = remember(stockBar, isDark, menuVersion) { extractWhatsAppTabs(stockBar, isDark) }
  ```
- This guarantees `WhatsAppNavBarHost` immediately updates from 0 -> 4 -> 5 tabs if `WDSBottomBar` populates menus asynchronously, with zero polling overhead.

### 4. 5-Tab Width Sizing (`ModuleDefaults.kt` & `WhatsAppNavBarHost.kt`)
Currently `ModuleDefaults.NavApps.WhatsApp.TAB_WIDTH` is `92.0f` (designed for 4 tabs: `4 * 92 = 368dp`). With 5 tabs, `5 * 92 = 460dp` overflows typical phone screen widths (~392–412dp).
1. **`ModuleDefaults.kt` (`NavApps.WhatsApp.TAB_WIDTH`):**
   - Change `const val TAB_WIDTH: Float = 92.0f` to `74.0f` (`5 * 74 = 370dp`, matching the existing 4-tab bar width of `368dp` and aligned with Instagram `74.6f` / YouTube `74.7f`).
2. **`WhatsAppNavBarHost.kt` (Screen-Width Safety Clamp):**
   - Users upgrading from a previous version may have `92.0f` persisted in `SharedPreferences`.
   - In `WhatsAppNavBarHost`, read `LocalConfiguration.current.screenWidthDp` and clamp `tabWidth`:
     ```kotlin
     val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
     val tabCount = tabs.size.coerceAtLeast(1)
     val maxSafeTabWidth = ((screenWidthDp - 16f) / tabCount).coerceAtLeast(40f)
     val effectiveTabWidth = live.tabWidth.coerceAtMost(maxSafeTabWidth)
     ```
   - Pass `tabWidth = effectiveTabWidth.dp` to `LiquidNavigationBar`.

---

## Verification Checklist (Post-Implementation)
1. **CI Build:** Commit and push to `hos4`; verify GitHub Actions build succeeds without Kotlin/Compose/lint errors.
2. **FABs (`WhatsAppHook.kt`):**
   - Main `fab`, `fab_second`, `extended_mini_fab` (Ask Meta AI), `fabText`, and tooltips stay positioned cleanly above the floating bar (`14dp` gap) across tab switches, scrolls, and redraws (no `0f` snap-back from `LX/N8Z.onPreDraw()`).
   - `extended_mini_fab` and `fab_second` maintain their native stock vertical spacing above `fab` (no double-stacking).
3. **5th Profile Tab (`WhatsAppHook.kt` + `WhatsAppNavBarHost.kt`):**
   - Floating bar displays 5 tabs: Chats, Updates, Communities, Calls, and You/Profile.
   - Tapping or swiping to the 5th tab navigates to `SettingsFragment` (Profile + Settings), and tapping/swiping back works smoothly.
   - 5th tab renders `ic_wa_ios_profile_outline` (inactive) and `ic_wa_ios_profile_filled` (active, tinted with accent color).
   - Total bar width fits comfortably within screen bounds (`~370dp`) with no horizontal clipping.
