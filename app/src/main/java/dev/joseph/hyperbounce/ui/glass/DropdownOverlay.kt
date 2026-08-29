package dev.joseph.hyperbounce.ui.glass

/*
 * DropdownPicker's expanded panel must render above every sibling/cousin on screen without
 * making its own small inline label push its parent Row/Card/Column around as it "grows"
 * (Compose sizes a parent from its children's *reported* measured size, regardless of
 * where they actually draw — so a child that reports a small size but draws a panel past
 * its own bounds doesn't resize its parent, but it also only reliably draws over its own
 * siblings, not over unrelated cousins further down a scrolling column).
 *
 * A Compose `Popup` would solve the z-order problem by rendering in a genuinely separate
 * Android window — but this app's glass effects are real backdrop captures
 * (layerBackdrop/drawBackdrop), which are RenderNode-based and scoped to one rendering
 * surface. A Popup's separate window has its own surface, so a glass panel drawn inside it
 * couldn't correctly sample the app's backdrop. Instead, each scaffold (ScreenScaffold,
 * RootScaffold) hosts one shared overlay slot near the top of its own Box — the same
 * surface as everything else — and DropdownPicker instances render into that slot instead
 * of inline, so the panel is guaranteed to draw above all of that screen's content while
 * never influencing any layout.
 */

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/** One currently-open dropdown panel: who owns it, where its label anchor is, and what to draw. */
class DropdownOverlayRequest(
    val ownerId: Any,
    val anchorPosition: Offset,
    val anchorSize: IntSize,
    val content: @Composable () -> Unit
)

class DropdownOverlayHostState {
    var request by mutableStateOf<DropdownOverlayRequest?>(null)
        private set

    fun show(request: DropdownOverlayRequest) {
        this.request = request
    }

    fun dismiss(ownerId: Any) {
        if (request?.ownerId == ownerId) request = null
    }

    fun dismissAny() {
        request = null
    }
}

val LocalDropdownOverlayHost = compositionLocalOf<DropdownOverlayHostState> {
    error("No DropdownOverlayHostState provided — wrap content in ScreenScaffold or RootScaffold")
}

/**
 * Wrap a scaffold's content in this once; provides [LocalDropdownOverlayHost] and renders
 * whatever DropdownPicker currently has open as the very last (topmost) child — call this
 * as the last thing inside the scaffold's outermost Box.
 */
@Composable
fun DropdownOverlayHost(content: @Composable () -> Unit) {
    val hostState = remember { DropdownOverlayHostState() }
    CompositionLocalProvider(LocalDropdownOverlayHost provides hostState) {
        content()
        val request = hostState.request
        if (request != null) {
            // Full-screen invisible scrim to dismiss on outside tap, drawn just below the
            // panel itself (declared first here, so the panel below it in this same block
            // draws on top).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(request.ownerId) {
                        detectTapGestures { hostState.dismissAny() }
                    }
            )
            request.content()
        }
    }
}
