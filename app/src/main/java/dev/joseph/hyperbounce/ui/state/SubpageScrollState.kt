package dev.joseph.hyperbounce.ui.state

import androidx.compose.runtime.compositionLocalOf

class SubpageScrollStateHolder {
    data class ScrollPos(
        val index: Int = 0,
        val offset: Int = 0,
        val miuixHeightOffset: Float = 0f,
        val miuixContentOffset: Float = 0f
    )

    private val scrollPositions = mutableMapOf<String, ScrollPos>()

    fun get(key: String): ScrollPos = scrollPositions[key] ?: ScrollPos()

    fun update(
        key: String,
        index: Int,
        offset: Int,
        miuixHeightOffset: Float = 0f,
        miuixContentOffset: Float = 0f
    ) {
        scrollPositions[key] = ScrollPos(index, offset, miuixHeightOffset, miuixContentOffset)
    }

    fun clear(key: String) {
        scrollPositions.remove(key)
    }
}

val LocalSubpageScrollState = compositionLocalOf { SubpageScrollStateHolder() }
val LocalDestinationKey = compositionLocalOf<String?> { null }
