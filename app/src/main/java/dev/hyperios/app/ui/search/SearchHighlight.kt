package dev.hyperios.app.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import dev.hyperios.app.ui.theme.HyperBounceColors
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

object SearchHighlightState {
    var activeHighlightKey by mutableStateOf<String?>(null)
        private set
    var targetScrollIndex by mutableStateOf<Int?>(null)
        private set

    fun highlight(key: String, scrollIndex: Int? = null) {
        activeHighlightKey = key.trim().lowercase()
        targetScrollIndex = scrollIndex
    }

    fun isMatch(target: String?): Boolean {
        val active = activeHighlightKey ?: return false
        val t = target?.trim()?.lowercase() ?: return false
        return active == t || active.contains(t) || t.contains(active)
    }

    fun clear() {
        activeHighlightKey = null
        targetScrollIndex = null
    }
}

/**
 * Highlights a tuning component with standard 2-pulse flash animation and
 * automatically scrolls it into view via BringIntoViewRequester.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.searchHighlight(
    key: String,
    colors: HyperBounceColors,
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier = composed {
    val activeKey = SearchHighlightState.activeHighlightKey
    val isMatch = activeKey != null && SearchHighlightState.isMatch(key)
    val requester = remember { BringIntoViewRequester() }
    val animAlpha = remember { Animatable(0f) }
    val componentStyle = LocalComponentStyle.current
    val highlightColor = if (componentStyle == ComponentStyle.MIUIX) {
        MiuixTheme.colorScheme.primary
    } else {
        colors.accentBlue
    }

    LaunchedEffect(isMatch) {
        if (isMatch) {
            delay(320) // Allow navigation slide transition and list scrolling to finish
            try {
                requester.bringIntoView()
            } catch (_: Exception) {}
            repeat(2) {
                animAlpha.animateTo(0.28f, tween(300))
                animAlpha.animateTo(0f, tween(300))
            }
            SearchHighlightState.clear()
        }
    }

    this
        .bringIntoViewRequester(requester)
        .then(
            if (animAlpha.value > 0.001f) {
                Modifier
                    .clip(shape)
                    .background(highlightColor.copy(alpha = animAlpha.value))
            } else {
                Modifier
            }
        )
}
