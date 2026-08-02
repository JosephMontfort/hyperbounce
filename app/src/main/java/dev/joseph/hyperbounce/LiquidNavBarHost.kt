package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TabData(val sourceView: View, val icon: ImageVector)

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        
        val fallbackIcons = listOf(
            Icons.Default.Home,
            Icons.Default.PlayArrow,
            Icons.Default.Add,
            Icons.Default.List,
            Icons.Default.Person
        )
        
        var iconIdx = 0
        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue
            // Ensure we are grabbing an actual tab container, not a 0-width spacer
            if (tab.childCount > 0) {
                val vectorIcon = fallbackIcons.getOrElse(iconIdx) { Icons.Default.Home }
                out.add(TabData(tab, vectorIcon))
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
        // CRITICAL FIX: Wait for ALL 5 tabs to load, not just the first one!
        while (tabs.size < 4 && attempts < 20) {
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.isNotEmpty()) {
                tabs = extracted // Update UI dynamically as they load
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
        // DRM-SAFE DUMMY BACKDROP: Satisfies Miuix compiler but performs ZERO screen recording.
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
                ) {
                    // Do nothing. DRM safe!
                }
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
                    modifier = Modifier.padding(bottom = 24.dp),
                    selectedIndex = { selectedIndex },
                    onSelected = { idx ->
                        selectedIndex = idx
                        if (externalTabState.value != idx && idx < tabs.size) {
                            tabs[idx].sourceView.performClick()
                        }
                    },
                    backdrop = safeDummyBackdrop, // Inject safe backdrop
                    tabsCount = tabs.size,
                    isBlurEnabled = false, // STRIP BLUR TO BYPASS DRM
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        FloatingBottomBarItem(
                            onClick = { selectedIndex = index },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                        ) {
                            Icon(
                                imageVector = tabData.icon,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}
