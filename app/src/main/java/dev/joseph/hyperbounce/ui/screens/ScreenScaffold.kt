package dev.joseph.hyperbounce.ui.screens

/*
 * Every subpage in this app (AppDetail x3, Engine, About) shares this scaffold: a
 * floating circular glass back button (jelly touch-and-drag, see
 * ui/components/CircularGlassBackButton.kt) pinned to the top-start corner, and a large
 * iOS-style title that collapses into the top bar as the user scrolls — mirroring
 * UINavigationBar's large-title behavior.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.CircularGlassBackButton
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun ScreenScaffold(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val collapseProgress = (scrollState.value / 220f).fastCoerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(64.dp))
            BasicText(
                title,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = 1f - collapseProgress * 0.85f
                        translationY = -collapseProgress * 6.dp.toPx()
                    },
                style = HyperBounceType.largeTitle.copy(color = colors.label)
            )
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .graphicsLayer { alpha = 1f - collapseProgress },
                    style = HyperBounceType.subhead.copy(color = colors.secondaryLabel)
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(56.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGlassBackButton(onClick = onBack, backdrop = backdrop)
            Spacer(Modifier.width(12.dp))
            BasicText(
                title,
                modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                style = HyperBounceType.headline.copy(color = colors.label)
            )
        }
    }
}
