# Facebook Floating Liquid Navigation Bar - Implementation Specification

This specification defines the exact technical implementation details for adding the Floating Liquid Navigation Bar to Facebook (`com.facebook.katana`) in HyperBounce. It is designed to be executed directly by an implementation subagent once approved.

---

## 1. Architectural Overview & File Structure

Following HyperBounce's strict 4-tier modular architecture:
1. **Dispatcher (`PhysicsHook.kt`)**: Add package routing for `com.facebook.katana`.
2. **Preferences & Defaults**:
   - `app/src/main/java/dev/hyperios/app/common/ModuleDefaults.kt`: Add `ModuleDefaults.NavApps.Facebook` configuration object.
   - `app/src/main/java/dev/hyperios/app/prefs/HyperBouncePrefs.kt`: Add `app_facebook_*` getters.
   - `app/src/main/java/dev/hyperios/app/prefs/LivePrefs.kt`: Ensure live reactivity via `rememberLiveNavBarSettings(hyperBouncePrefs, "facebook")`.
   - `app/src/main/java/dev/hyperios/app/ui/state/SettingsRepository.kt`: Companion app UI controls and toggle for Facebook.
3. **Hook Entry Point (`FacebookHook.kt`)**:
   - Suppresses native top navigation bar (`TabBarContainerLayout`).
   - Hooks lifecycle callbacks in `FbMainTabActivity` and window insets.
   - Auto-hides on fullscreen overlays, bottom sheets, and soft keyboard (IME).
   - Injects `ComposeView` into `decorView`.
4. **Host UI Bridge (`FacebookNavBarHost.kt`)**:
   - Reads tabs, drawables, labels, and badges from `TabBarContainerLayout`'s `C02LI` child views.
   - Dispatches tab clicks directly to `C02LI.performClick()`.
   - Bridges to `LiquidNavigationBar(...)`.

---

## 2. Reverse Engineered Hook Points & Target Signatures

### 2.1 Native Top Navigation Bar Suppression
* **Target Container Class:** `com.facebook.navigation.tabbar.ui.TabBarContainerLayout` (extends `LinearLayout` via `C023M`, in `classes2.dex`).
* **Individual Tab Widget:** `X.C02LI` (direct children of `TabBarContainerLayout`).
* **Hook Strategy:**
  Hook `View.onAttachedToWindow()`:
  When an attached view is an instance of `com.facebook.navigation.tabbar.ui.TabBarContainerLayout`:
  ```kotlin
  tabBar.alpha = 0f
  tabBar.translationY = -9999f
  tabBar.layoutParams?.let { lp ->
      if (lp.height != 0) {
          lp.height = 0
          tabBar.requestLayout()
      }
  }
  ```
  *Note:* The view remains attached to window and its children remain in memory, allowing click dispatch and selection detection to function with 100% native fidelity.

### 2.2 Tab Extraction & Interaction Bridge (`C02LI`)
* **Icon Extraction:**
  1. `child.javaClass.getMethod("DxG").invoke(child) as? Drawable` (or reflect field `A02`).
  2. For active/inactive variants: reflect field `A0T` (`C03LV`):
     - `A00` -> Selected/Active Drawable (Filled)
     - `A01` -> Unselected/Inactive Drawable (Outline)
  3. Draw to `Bitmap` using Android `Canvas`.
* **Selection State:**
  - `child.isSelected` returns true for active tab.
  - `tabBarContainer.A00` holds the current selected tab index integer.
* **Click Dispatch:**
  - `child.performClick()` or synthetic touch events (`ACTION_DOWN` + `ACTION_UP`).
* **Badge Extraction:**
  - Count: `child.javaClass.getMethod("HNF").invoke(child) as? Int` (or reflect `child.A0K.A07`).
  - Dot: `child.javaClass.getMethod("CP0").invoke(child) == "dot"`.

### 2.3 Window Inset Normalization & Content Clearance
* **Target Activity:** `com.facebook.katana.activity.FbMainTabActivity`
* **Content Container View ID:** `2131366875` (hex `0x7f0a13db`).
* **Hook Strategy:**
  Hook `View.dispatchApplyWindowInsets(WindowInsets)`:
  When `view.id == 0x7f0a13db || view.id == android.R.id.content`:
  Nullify system navigation bar bottom inset (`insets.getInsets(WindowInsets.Type.systemBars()).bottom = 0`) to prevent duplicate bottom padding.

### 2.4 Auto-Hide Triggers
* **Fullscreen Overlays & Video (FbShorts / Watch):**
  Hook `com.facebook.katana.activity.FbMainTabActivity`:
  - `onEnterFullscreenOverlay(boolean, boolean, boolean, boolean)`:
    Set `HyperFloatingBar.visibility = View.GONE`.
  - `onReturnFromFullscreenOverlay(boolean, boolean)`:
    Set `HyperFloatingBar.visibility = View.VISIBLE`.
* **Dialogs & Bottom Sheets:**
  Hook `com.facebook.katana.activity.FbMainTabActivity`:
  - `onDialogShown(Dialog, Fragment)`:
    Set `HyperFloatingBar.visibility = View.GONE`.
  - `onDialogDidDismiss(Dialog, Fragment)`:
    If `activity.getOpenDialogs().isEmpty()`, set `HyperFloatingBar.visibility = View.VISIBLE`.
* **Soft Keyboard (IME):**
  In `decorView.viewTreeObserver.addOnPreDrawListener`:
  If `rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom > 200`:
  Set `HyperFloatingBar.visibility = View.GONE`.

### 2.5 Layout Shifts (Reels Footers & Floating Action Buttons)
* **Reels Controls:**
  Hook `View.onAttachedToWindow()`:
  For views matching `fb_shorts_viewer_footer`, `reels_navigation_footer`, `reels_comments_floating_cta`:
  Apply `translationY = -190.0f` so video scrubbers and interaction buttons float safely above the bottom navigation bar.
* **Floating Action Buttons:**
  For views matching `fab` or `extended_mini_fab`:
  Apply `bottomMargin += 200` or `translationY = -190.0f`.

---

## 3. Implementation Plan: Code Changes by File

### 3.1 `dev/hyperios/app/common/ModuleDefaults.kt`
Add under `object NavApps`:
```kotlin
object Facebook {
    const val BAR_HEIGHT: Float = 50.0f
    const val TAB_WIDTH: Float = 58.0f
    val TAB_WIDTH_RANGE = 40.0f..100.0f
    const val CORNER_RADIUS: Float = 32.0f
    val CORNER_RADIUS_RANGE = 0.0f..50.0f
    const val BOTTOM_MARGIN: Float = 20.0f
    val BOTTOM_MARGIN_RANGE = 0.0f..80.0f
    const val CONTAINER_OPACITY: Float = 0.40f
    const val PILL_CONTAINER_OPACITY: Float = 1.0f
    const val PILL_BLUR_INTENSITY: Float = 0.0f
    const val AUTO_SHRINK: Boolean = false
    const val AUTO_SHRINK_SCALE: Float = 0.85f
    const val REELS_FULLSCREEN_ZOOM: Boolean = true
    const val ACCENT: String = "Blue"
    const val CUSTOM_ACCENT_COLOR: Long = 0xFF1877F2L
}
```

### 3.2 `dev/hyperios/app/prefs/HyperBouncePrefs.kt`
Update per-app nav bar getters to support `"facebook"`:
- Update `appCornerRadius`, `appBarHeight`, `appTabWidth`, `appBottomMargin`, `appAccent`, `appCustomAccentColor` with fallback defaults to `ModuleDefaults.NavApps.Facebook`.

### 3.3 `dev/hyperios/app/nav/FacebookHook.kt` (New File)
- Implements `onPackageReady()`.
- Hooks `TabBarContainerLayout` constructor and `onAttachedToWindow` to suppress top chrome.
- Hooks `FbMainTabActivity` lifecycle and overlay methods (`onEnterFullscreenOverlay`, `onReturnFromFullscreenOverlay`, `onDialogShown`, `onDialogDidDismiss`).
- Injects `ComposeView` tagged `"HyperFloatingBar"` into `activity.window.decorView`.

### 3.4 `dev/hyperios/app/nav/FacebookNavBarHost.kt` (New File)
- Composable function `FacebookNavBarHost(...)`.
- Observes `TabBarContainerLayout`'s `C02LI` children.
- Converts drawables to Compose/Bitmap format.
- Passes live state to `LiquidNavigationBar`.
- Relays tab taps to `child.performClick()`.

### 3.5 `dev/hyperios/app/PhysicsHook.kt`
Add routing branch:
```kotlin
param.packageName == "com.facebook.katana" ->
    FacebookHook(this, param).onPackageReady()
```

### 3.6 Companion App Settings UI (`SettingsRepository.kt` & Nav screens)
- Add Facebook toggle switch and customization sliders (height, width, corner radius, bottom margin, theme, accent).

---

## 4. Verification & Validation Protocol

1. **Compilation Validation via GitHub Actions**:
   - Push commits cleanly to `hos4` branch on GitHub.
   - Monitor GitHub Actions workflow for zero compilation or lint errors.
2. **Device Deployment**:
   - Download built APK release from GitHub Actions.
   - Install via root: `su -c "pm install -r <apk_path>"`.
   - Force stop Facebook: `su -c "am force-stop com.facebook.katana"`.
3. **Runtime Functionality Checks**:
   - Verify top tab bar is completely suppressed.
   - Verify floating liquid glass navigation bar renders at the bottom.
   - Verify all 5-6 tabs display correct icons, active/inactive highlights, and unread badges.
   - Verify tapping each tab smoothly switches Facebook pages.
   - Verify Reels, full-screen Watch videos, dialogs, and soft keyboard auto-hide the bar seamlessly.
