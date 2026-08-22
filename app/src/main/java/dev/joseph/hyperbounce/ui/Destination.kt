package dev.joseph.hyperbounce.ui

import dev.joseph.hyperbounce.ui.state.NavApp

/**
 * This app has too few screens to justify a Navigation-Compose dependency — a small
 * hand-rolled back stack (see MainActivity.kt) is simpler and keeps every screen
 * transition built from the same AnimatedContent + spring primitives as everything else.
 *
 * Tuning and Settings are the two root tabs on the floating bottom bar; backStack[0] is
 * always one of these two. AppDetail/Engine push from Tuning; About pushes from either.
 */
sealed interface Destination {
    data object Tuning : Destination
    data object Settings : Destination
    data class AppDetail(val app: NavApp) : Destination
    data object Engine : Destination
    data object About : Destination
}
