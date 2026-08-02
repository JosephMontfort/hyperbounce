package dev.joseph.hyperbounce

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.lang.ref.WeakReference

data class TabData(val bitmap: Bitmap?)

fun extractTabs(stockBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    for (i in 0 until stockBar.childCount) {
        val child = stockBar.getChildAt(i)
        val icon = if (child is ViewGroup) findImageView(child) else (child as? ImageView)
        var bmp: Bitmap? = null
        if (icon?.drawable != null) {
            try {
                val drawable = icon.drawable.mutate()
                drawable.setTintList(null)
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
            } catch (e: Exception) {
                // Ignore extraction failures on non-drawable nodes
            }
        }
        out.add(TabData(bmp))
    }
    return out
}

fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) {
            val res = findImageView(v)
            if (res != null) return res
        }
    }
    return null
}

fun clickStockTab(stockBar: ViewGroup, index: Int) {
    if (index in 0 until stockBar.childCount) {
        stockBar.getChildAt(index)?.performClick()
    }
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
        val w = bW
        val h = bH
        if (w <= 0 || h <= 0) return

        val sf = 8
        val outW = (w / sf).coerceAtLeast(1)
        val outH = (h / sf).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(bX, bY, bX + w, bY + h)
        inFlight = true

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
    var tabs by remember { mutableStateOf(extractTabs(stockBar)) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    // Dynamically update tab icons if stockBar populates children late
    LaunchedEffect(stockBar.childCount) {
        tabs = extractTabs(stockBar)
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

        Box(
            modifier = Modifier.fillMaxSize(),
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
                    .padding(
                        bottom = 12.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                selectedIndex = { selectedIndex },
                onSelected = { idx ->
                    selectedIndex = idx
                    if (externalTabState.value != idx) {
                        clickStockTab(stockBar, idx)
                    }
                },
                backdrop = backdrop,
                tabsCount = tabs.size.coerceAtLeast(1),
                isBlurEnabled = true,
            ) {
                tabs.forEachIndexed { index, tabData ->
                    FloatingBottomBarItem(
                        onClick = { selectedIndex = index },
                        modifier = Modifier.defaultMinSize(minWidth = 60.dp),
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
