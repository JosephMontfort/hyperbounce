package dev.joseph.hyperbounce.ui

import dev.joseph.hyperbounce.ui.state.NavApp

/**
 * Tuning and Settings are no longer stack destinations — they're pages 0 and 1 of the root
 * HorizontalPager in MainActivity.kt, swipeable between each other and synced with the
 * floating tab bar via pagerState.currentPage. This sealed interface now only covers the
 * subpages that genuinely drill down (push/pop), layered on top of the pager.
 */
sealed interface Destination {
    data object AppsList : Destination
    data class AppDetail(val app: NavApp) : Destination
    data class AppAppearance(val app: NavApp) : Destination
    data class AppLayout(val app: NavApp) : Destination
    data object Engine : Destination
    data object ToggleAppearance : Destination
    data object TogglePhysics : Destination
    data object SliderAppearance : Destination
    data object SliderPhysics : Destination
    data object LockscreenStuffs : Destination
    data object LockScreenSheet : Destination
    data object LockScreenSheetGlassTuning : Destination
    data object LockScreenSheetLighting : Destination
    data object LockScreenSheetHomeIndicator : Destination
    data object NotificationCenterSheet : Destination
    data object NotificationCenterSheetGlassTuning : Destination
    data object NotificationCenterSheetLighting : Destination
    data object NotificationCenterSheetHomeIndicator : Destination
    data object Keypad : Destination
    data object KeypadDelays : Destination
    data object KeypadPhysics : Destination
    data object KeypadStretchPhysics : Destination
    data object KeypadReleasePhysics : Destination
    data object KeypadIosBloomPhysics : Destination
    data object KeypadGlassTuning : Destination
    data object KeypadButtonsLayout : Destination
    data object KeypadPadLayout : Destination
    data object SystemLauncherStuffs : Destination
    data object ScreenUnlock : Destination
    data object ScreenUnlockRadialKinetics : Destination
    data object ScreenUnlockRadialDock : Destination
    data object ScreenUnlockCascadeGlobals : Destination
    data object ScreenUnlockCascadeDock : Destination
    data object ScreenUnlockCascadeZDepth : Destination
    data object ScreenUnlockCascadeBounce : Destination
    data object ScreenUnlockCascadeSettle : Destination
    data object AppTransitions : Destination
    data object ControlCenter : Destination
    data object MorphPhysics : Destination
    data object CallerScreen : Destination
    data object SafeMode : Destination
    data object HapticStudio : Destination
    data object About : Destination
}

