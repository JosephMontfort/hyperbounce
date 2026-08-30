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
    data object Engine : Destination
    data object LockScreenSheet : Destination
    data object Keypad : Destination
    data object KeypadDelays : Destination
    data object ScreenUnlock : Destination
    data object AppTransitions : Destination
    data object ControlCenter : Destination
    data object About : Destination
}

