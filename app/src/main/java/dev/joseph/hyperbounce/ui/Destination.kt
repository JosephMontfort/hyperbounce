package dev.joseph.hyperbounce.ui

import dev.joseph.hyperbounce.ui.state.NavApp

/**
 * This app has too few screens to justify a Navigation-Compose dependency — a small
 * hand-rolled back stack (see MainActivity.kt) is simpler and keeps every screen
 * transition built from the same AnimatedContent + spring primitives as everything else.
 */
sealed interface Destination {
    data object Home : Destination
    data class AppDetail(val app: NavApp) : Destination
    data object Engine : Destination
    data object About : Destination
}
