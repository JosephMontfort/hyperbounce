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

// ── Tab icon extraction ───────────────────────────────────────────────────────
data class TabData(val bitmap: Bitmap?)

fun extractTabs(stockBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    val menu = stockBar.getChildAt(0) as? ViewGroup ?: return out
    for (i in 0 until menu.childCount) {
        val tab = menu.getChildAt(i) as? ViewGroup ?: continue
        val icon = findImageView(tab)
        val drawable = icon?.drawable?.mutate()
        var bmp: Bitmap? = null
        if (drawable != null) {
            drawable.setTintList(null)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bmp))
        }
        out.add(TabData(bmp))
    }
    return out
}

fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

fun clickStockTab(stockBar: ViewGroup, index: Int) {
    (stockBar.getChildAt(0) as? ViewGroup)?.getChildAt(index)?.performClick()
}

// ── Window resolver ───────────────────────────────────────────────────────────
private fun resolveWindow(context: Context): Window? {
    var ctx: Context = context
    while (true) {
        if (ctx is Activity) return ctx.window
        ctx = (ctx as? ContextWrapper)?.baseContext ?: return null
    }
}

// ── Real-time PixelCopy blur backdrop ────────────────────────────────────────
//
// PixelCopy reads directly from the GPU front-buffer (what is actually
// rendered on screen), so it captures hardware-accelerated RecyclerView
// items — contact avatars, names, timestamps — all perfectly.
//
// It DOES include our own ComposeView in the capture, but since the pill
// is now only 20–28% opaque, the feedback contribution is small and
// converges to a stable steady-state within 2–3 frames:
//
// steady_state ≈ 83% blur(real WhatsApp content) + 17% surface tint
//
// This is real frosted liquid glass — the chat list is genuinely blurred
// and updates frame-by-frame as the user scrolls.
//
// Scale-down factor (sf = 8) doubles as the blur kernel: downsampling from
// pill-resolution → 1/8 size averages 64 pixels into 1, producing a
// Gaussian-quality blur. FilterQuality.High bilinear upscale at draw
// time adds smoothing → the result looks like a 15–20 dp blur radius.
//
private class PixelCopyBlurBackdrop(
    windowRef: WeakReference<Window>,
    private val handler: Handler,
) : Backdrop {
    private val ref = windowRef
    override val isCoordinatesDependent: Boolean = false
    override val offsetResidualX: Float = 0f
    override val offsetResidualY: Float = 0f

    // Pill bounds in window-pixel coordinates (set from onGloballyPositioned)
    @Volatile var bX = 0
    @Volatile var bY = 0
    @Volatile var bW = 0
    @Volatile var bH = 0
    @Volatile private var latest: ImageBitmap? = null
    @Volatile private var inFlight = false
    private var prev: Bitmap? = null

    /**
     * Request one PixelCopy frame. Called from Choreographer on UI thread.
     * Guards against overlapping requests with [inFlight].
     */
    fun tick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (inFlight) return
        val window = ref.get() ?: return
        val w = bW
        val h = bH
        if (w <= 0 || h <= 0) return

        // Downscale → the GPU will blit and average the srcRect into the
        // smaller bitmap, acting as a hardware-accelerated box blur.
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
        // Upscale with bilinear filtering — this final interpolation adds
        // a secondary smoothing pass and eliminates block artifacts.
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

// ── Composable host ───────────────────────────────────────────────────────────
@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>, // driven by WhatsApp swipe detection
) {
    val tabs = remember { extractTabs(stockBar) }
    // Our indicator's selected index.
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    // ── External tab sync (WhatsApp swipe → our indicator) ───────────────
    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        // ── Backdrop: PixelCopy for real GPU-rendered blur ────────────────
        val window: Window? = remember(stockBar) { resolveWindow(stockBar.context) }
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val captureBackdrop: PixelCopyBlurBackdrop? = remember(window) {
            window?.let { PixelCopyBlurBackdrop(WeakReference(it), mainHandler) }
        }

        // Frame-synced capture loop — Choreographer fires once per display
        // frame on the UI thread. The inFlight guard prevents piling up
        // requests when PixelCopy is slower than the display refresh.
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

        // Fallback (API < 26 or no window): empty transparent backdrop so
        // the 20 % tint + specular highlight still look like glass.
        val backdrop: Backdrop = captureBackdrop ?: rememberLayerBackdrop { /* transparent */ }

        // ── UI ────────────────────────────────────────────────────────────
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
                    // ── Drag + tap bug fix ────────────────────────────────
                    // Call clickStockTab if and only if WhatsApp is NOT
                    // already on this tab.
                    //
                    // This handles three cases correctly:
                    // • User taps our pill → WhatsApp is on old tab
                    //   → externalTabState != idx
                    //   → clickStockTab called ✓
                    // • User drags indicator → same as tap ✓
                    // • WhatsApp swipe → WhatsApp already on idx
                    //   → externalTabState == idx
                    //   → NO redundant click ✓
                    if (externalTabState.value != idx) {
                        clickStockTab(stockBar, idx)
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
