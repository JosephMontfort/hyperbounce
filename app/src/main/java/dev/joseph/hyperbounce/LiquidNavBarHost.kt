package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
fun LiquidNavBarHost(stockBar: ViewGroup?) {
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    
    // Surface color matches Kyant's backdrop docs styling
    val containerColor = if (isDark) Color(0xFF121212).copy(alpha = 0.45f) else Color(0xFFFAFAFA).copy(alpha = 0.65f)
    val accentColor = if (isDark) Color.White else Color.Black
    
    var selectedIndex by remember { mutableIntStateOf(0) }
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.PlayArrow, Icons.Default.Send, Icons.Default.Person)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 28.dp)
                .width(300.dp)
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(24f.dp.toPx(), 36f.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f)) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.3f) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icons.forEachIndexed { index, icon ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            selectedIndex = index
                            try {
                                val tabs = listOf("feed_tab", "search_tab", "clips_tab", "direct_tab", "profile_tab")
                                stockBar?.let { bar ->
                                    val context = bar.context
                                    val resId = context.resources.getIdentifier(tabs[index], "id", context.packageName)
                                    if (resId != 0) {
                                        bar.findViewById<View>(resId)?.performClick()
                                    }
                                }
                            } catch (e: Exception) {}
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
