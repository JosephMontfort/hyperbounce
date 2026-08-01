package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ── Tab icon extraction ───────────────────────────────────────────────────────

data class TabData(val bitmap: Bitmap?)

fun extractTabs(stockBar: ViewGroup): List<TabData> {
    val tabs = mutableListOf<TabData>()
    val menuContainer = stockBar.getChildAt(0) as? ViewGroup ?: return tabs
    for (i in 0 until menuContainer.childCount) {
        val tabView = menuContainer.getChildAt(i) as? ViewGroup ?: continue
        val iconView = findImageView(tabView)
        val drawable = iconView?.drawable?.mutate()
        var bmp: Bitmap? = null
        if (drawable != null) {
            drawable.setTintList(null)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        tabs.add(TabData(bmp))
    }
    return tabs
}

fun findImageView(group: ViewGroup): ImageView? {
    for (i in 0 until group.childCount) {
        val v = group.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) {
            val iv = findImageView(v)
            if (iv != null) return iv
        }
    }
    return null
}

fun clickStockTab(stockBar: ViewGroup, index: Int) {
    val menuContainer = stockBar.getChildAt(0) as? ViewGroup ?: return
    menuContainer.getChildAt(index)?.performClick()
}

// ── Composable host ───────────────────────────────────────────────────────────

@Composable
fun LiquidNavBarHost(stockBar: ViewGroup) {
    var selectedIndex by remember { mutableStateOf(0) }
    val tabs = remember { extractTabs(stockBar) }

    MiuixTheme {
        // ── Glass backdrop ────────────────────────────────────────────────
        // We can't capture WhatsApp's Compose tree from here, so we use the
        // surfaceContainer colour as the base plate for the glass effects.
        // The hardware-blur view in PhysicsHook.kt (RenderEffect) sits below
        // this ComposeView and provides the real blurred-WhatsApp content
        // visible through the pill's 40 % alpha surface colour.
        //
        // Even with a solid-colour backdrop, the BloomStroke specular
        // highlight, lens edge distortion, and inner-shadow press effect
        // are fully rendered and make the pill look like real liquid glass.
        val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
        }

        // ── Root: fills the full-screen ComposeView ───────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FloatingBottomBar(
                modifier = Modifier
                    .padding(
                        // 12 dp clearance above the system nav bar,
                        // matching the KernelSU BottomBarMiuix positioning.
                        bottom = 12.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                selectedIndex = { selectedIndex },
                onSelected = { idx ->
                    selectedIndex = idx
                    clickStockTab(stockBar, idx)
                },
                backdrop = backdrop,
                tabsCount = tabs.size,
                isBlurEnabled = true,
            ) {
                tabs.forEachIndexed { index, tabData ->
                    FloatingBottomBarItem(
                        onClick = {
                            selectedIndex = index
                            clickStockTab(stockBar, index)
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                    ) {
                        if (tabData.bitmap != null) {
                            Image(
                                bitmap = tabData.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(LocalContentColor.current),
                            )
                        }
                    }
                }
            }
        }
    }
}
