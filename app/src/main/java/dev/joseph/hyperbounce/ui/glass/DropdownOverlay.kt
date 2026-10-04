package dev.joseph.hyperbounce.ui.glass

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
    DropdownOverlayHostState()
}

@Composable
fun DropdownOverlayHost(content: @Composable () -> Unit) {
    val hostState = remember { DropdownOverlayHostState() }
    CompositionLocalProvider(LocalDropdownOverlayHost provides hostState) {
        content()
        val request = hostState.request
        if (request != null) {
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
