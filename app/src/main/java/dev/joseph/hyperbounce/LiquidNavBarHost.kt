package dev.joseph.hyperbounce

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.lang.ref.WeakReference

data class TabData(val sourceView: View, val iconSymbol: String)

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        
        // Zero-dependency bulletproof emoji symbols for the tabs
        val fallbackIcons = listOf("🏠", "▶", "➕", "📋", "👤")
        
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

private fun resolveWindow(context: Context): Window? {
    var ctx: Context = context
    while (true) {
        if (ctx is Activity) return ctx.window
        ctx = (ctx as? ContextWrapper)?.baseContext ?: return null
    }
}

private class PixelCopyBlurBackdrop(
    windowRef: WeakReference<Window>,
    private val handler: Handler,
) : Backdrop {
    private val ref = windowRef
    override val isCoordinatesDependent: Boolean = false
    override val offsetResidualX: Float = 0f
    override val offsetResidualY: Float = 0f

    @Volatile var bX = 0
    @Volatile var bY = 0
    @Volatile var bW = 0
    @Volatile var bH = 0
    @Volatile private var latest: ImageBitmap? = null
    @Volatile private var inFlight = false
    private var prev: Bitmap? = null

    fun tick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (inFlight) return
        val window = ref.get() ?: return
        
        val decor = window.peekDecorView()
        if (decor == null || decor.windowToken == null) return

        val w = bW
        val h = bH
        if (w <= 0 || h <= 0) return

        val sf = 8
        val outW = (w / sf).coerceAtLeast(1)
        val outH = (h / sf).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(bX, bY, bX + w, bY + h)
        inFlight = true

        try {
            @Suppress("DEPRECATION")
            PixelCopy.request(window, srcRect, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    prev?.recycle()
                    prev = bmp
                    latest = bmp.asImageBitmap()
                } else {
                    bmp.recycle()
                }
                inFlight = false
            }, handler)
        } catch (e: Exception) {
            bmp.recycle()
            inFlight = false
        }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        val bmp = latest ?: return
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
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
        while (tabs.isEmpty() && attempts < 10) {
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.isNotEmpty()) {
                tabs = extracted
                break
            }
            attempts++
            delay(300)
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        val window: Window? = remember(stockBar) { resolveWindow(stockBar.context) }
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val captureBackdrop: PixelCopyBlurBackdrop? = remember(window) {
            window?.let { PixelCopyBlurBackdrop(WeakReference(it), mainHandler) }
        }

        if (captureBackdrop != null) {
            DisposableEffect(captureBackdrop) {
                val ch = Choreographer.getInstance()
                val cb = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        captureBackdrop.tick()
                        ch.postFrameCallback(this)
                    }
                }
                ch.postFrameCallback(cb)
                onDispose { ch.removeFrameCallback(cb) }
            }
        }

        val backdrop: Backdrop = captureBackdrop ?: rememberLayerBackdrop { }

        if (tabs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingBottomBar(
                    modifier = Modifier
                        .onGloballyPositioned { coords: LayoutCoordinates ->
                            captureBackdrop?.let {
                                val pos = coords.positionInWindow()
                                it.bX = pos.x.toInt()
                                it.bY = pos.y.toInt()
                                it.bW = coords.size.width
                                it.bH = coords.size.height
                            }
                        }
                        .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                    selectedIndex = { selectedIndex },
                    onSelected = { idx ->
                        selectedIndex = idx
                        if (externalTabState.value != idx && idx < tabs.size) {
                            tabs[idx].sourceView.performClick()
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = tabs.size,
                    isBlurEnabled = true,
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        FloatingBottomBarItem(
                            onClick = { selectedIndex = index },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                        ) {
                            Text(
                                text = tabData.iconSymbol,
                                fontSize = 20.sp,
                                color = LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}
