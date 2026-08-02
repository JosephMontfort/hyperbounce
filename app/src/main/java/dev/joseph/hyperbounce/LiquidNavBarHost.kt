package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TabData(val sourceView: View, val iconSymbol: String)

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        
        // Anti-Crash guarantee. No external dependencies, ClassLoader is safe.
        val fallbackIcons = listOf("🏠", "▶️", "➕", "📚", "👤")
        
        var iconIdx = 0
        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue
            if (tab.childCount > 0) {
                val symbol = fallbackIcons.getOrElse(iconIdx) { "🏠" }
                out.add(TabData(tab, symbol))
                iconIdx++
            }
        }
    } catch (e: Exception) {}
    return out
}

@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
) {
    var tabs by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    LaunchedEffect(stockBar) {
        var attempts = 0
        // CRITICAL FIX 3: Wait until AT LEAST 4 tabs have populated in the layout!
        while (tabs.size < 4 && attempts < 20) {
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.isNotEmpty()) {
                tabs = extracted 
            }
            if (extracted.size >= 4) break
            attempts++
            delay(300)
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        // DRM-SAFE DUMMY BACKDROP
        val safeDummyBackdrop = remember {
            object : Backdrop {
                override val isCoordinatesDependent = false
                override val offsetResidualX = 0f
                override val offsetResidualY = 0f
                override fun DrawScope.drawBackdrop(
                    density: Density,
                    coordinates: LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                    downscaleFactor: Int
                ) {}
            }
        }

        if (tabs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingBottomBar(
                    modifier = Modifier.padding(
                        bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    selectedIndex = { selectedIndex },
                    onSelected = { idx ->
                        selectedIndex = idx
                        if (externalTabState.value != idx && idx < tabs.size) {
                            tabs[idx].sourceView.performClick()
                        }
                    },
                    backdrop = safeDummyBackdrop,
                    tabsCount = tabs.size,
                    isBlurEnabled = false, // STRIP BLUR TO BYPASS DRM BLACKOUT
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        FloatingBottomBarItem(
                            onClick = { selectedIndex = index },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                        ) {
                            Text(
                                text = tabData.iconSymbol,
                                fontSize = 22.sp,
                                color = LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}
