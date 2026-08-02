package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
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
        val tab  = menu.getChildAt(i) as? ViewGroup ?: continue
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

// ── Real-time blur backdrop ───────────────────────────────────────────────────
//
// Strategy: downscale-blur via View.draw()
//
// We draw WhatsApp's content view (the first child of the DecorView, which
// contains the entire app UI without our overlay) into a Bitmap that is 1/8
// the pill's on-screen size.  Downscaling IS the blur: pixel averaging during
// scale-down acts like a box blur.  When this small bitmap is displayed at
// full pill size with FilterQuality.High (bilinear interpolation), the result
// looks like a smooth Gaussian blur — identical in appearance to the frosted
// glass effect on iOS.
//
// The capture is driven by Choreographer so it runs every display frame on
// the UI thread, giving true real-time blur.
//
// Rendering pipeline per frame:
//   [ContentCaptureBackdrop.drawBackdrop] → draws blurred WhatsApp content
//   [miuix vibrancy effect]               → saturates the blurred colours
//   [miuix blur effect]                   → secondary softening pass
//   [miuix lens effect]                   → edge refraction (iOS "lens bump")
//   [onDrawSurface containerColor@40%]    → frosted tint overlay
//   [BloomStroke specular highlight]      → specular glass shine on rim
//
private class ContentCaptureBackdrop(
    srcRef: WeakReference<View>,
) : Backdrop {

    private val ref = srcRef

    override val isCoordinatesDependent: Boolean = false
    override val offsetResidualX: Float           = 0f
    override val offsetResidualY: Float           = 0f

    // Pill bounds in WINDOW px set from onGloballyPositioned each layout pass
    @Volatile var bX = 0
    @Volatile var bY = 0
    @Volatile var bW = 0
    @Volatile var bH = 0

    @Volatile private var latest: ImageBitmap? = null
    private var prevBmp: Bitmap? = null

    /**
     * Called from Choreographer.FrameCallback on the UI thread every frame.
     *
     * Draws WhatsApp's content view into a 1/8-scale software Bitmap clipped
     * to the pill bounds.  Because the canvas is clipped to a small region
     * near the bottom of the screen, the view-tree traversal skips everything
     * above the pill (toolbar, chat list header, etc.) and only rasterises
     * the last 1–2 visible chat rows — making each capture very fast.
     */
    fun tick() {
        val src = ref.get() ?: return
        val w = bW; val h = bH
        if (w <= 0 || h <= 0) return

        // Downscale factor.  8 → strong frosted blur (matches iOS).
        // Lower (4) → sharper; higher (12) → more diffuse.
        val sf   = 8
        val outW = (w / sf).coerceAtLeast(1)
        val outH = (h / sf).coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            // Scale first so the clip region aligns with the downscaled bitmap
            c.scale(outW.toFloat() / w, outH.toFloat() / h)
            // Translate so that the pill's top-left maps to canvas (0,0)
            c.translate(-bX.toFloat(), -bY.toFloat())
            // Draw the WhatsApp content.  Hardware-accelerated views fall
            // back to software rendering here; text/backgrounds always draw
            // correctly; avatar images may appear as colour blocks if the
            // system uses HARDWARE Bitmaps — still looks like frosted glass.
            src.draw(c)

            prevBmp?.recycle()
            prevBmp = bmp
            latest  = bmp.asImageBitmap()
        } catch (_: Exception) {
            bmp.recycle()
            // If software canvas draw fails, latest stays as-is (previous
            // frame or null).  The pill remains glass-looking via the tint.
        }
    }

    override fun DrawScope.drawBackdrop(
        density       : Density,
        coordinates   : LayoutCoordinates?,
        layerBlock    : (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        val bmp = latest ?: return
        // Draw the tiny bitmap at full pill size.
        // FilterQuality.High (bilinear) produces the smooth, slightly-blurry
        // glass look — this upscaling step IS the visual "blur".
        drawImage(
            image         = bmp,
            srcOffset     = IntOffset.Zero,
            srcSize       = IntSize(bmp.width, bmp.height),
            dstOffset     = IntOffset.Zero,
            dstSize       = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

// ── Composable host ───────────────────────────────────────────────────────────

@Composable
fun LiquidNavBarHost(
    stockBar        : ViewGroup,
    whatsAppContent : View?,          // DecorView's first child — no overlay
    externalTabState: State<Int>,     // set by WhatsApp swipe detection
) {
    val tabs  = remember { extractTabs(stockBar) }

    // Our local selected index.  Sources of truth (in priority order):
    //   1. User taps OUR pill  → tabClickedByUs + selectedIndex update
    //   2. WhatsApp swipe      → externalTabState → selectedIndex update
    var selectedIndex  by remember { mutableIntStateOf(externalTabState.value) }
    // Tracks which index the USER tapped on OUR pill (–1 = none pending).
    // Used to distinguish user-tap from WhatsApp-swipe so we don't call
    // clickStockTab redundantly when WhatsApp already switched the tab.
    var tabClickedByUs by remember { mutableIntStateOf(-1) }

    // Sync WhatsApp swipe navigation → our indicator
    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) {
            selectedIndex = externalIdx
            // Mark as external so onSelected won't re-click the tab
            tabClickedByUs = -1
        }
    }

    MiuixTheme {
        // ── Backdrop ─────────────────────────────────────────────────────
        // ContentCaptureBackdrop provides real-time blurred WhatsApp content.
        // Falls back to an empty (transparent) backdrop if no content view is
        // available; the 40 % containerColor tint from onDrawSurface + the
        // BloomStroke highlight still give a convincing glass look.
        val captureBackdrop: ContentCaptureBackdrop? = remember(whatsAppContent) {
            whatsAppContent?.let { ContentCaptureBackdrop(WeakReference(it)) }
        }

        // Choreographer drives real-time capture at display frame rate.
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

        val backdrop: Backdrop = captureBackdrop
            ?: rememberLayerBackdrop { /* empty → transparent fallback */ }

        // ── Layout ───────────────────────────────────────────────────────
        Box(
            modifier           = Modifier.fillMaxSize(),
            contentAlignment   = Alignment.BottomCenter,
        ) {
            FloatingBottomBar(
                modifier = Modifier
                    // Feed pill window-coords to the capture backdrop
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
                onSelected    = { idx ->
                    selectedIndex = idx
                    // Only relay the click to WhatsApp's hidden tab if WE
                    // initiated it.  External swipes already switched the
                    // WhatsApp page — redundant clicks can scroll to top.
                    if (tabClickedByUs == idx) {
                        clickStockTab(stockBar, idx)
                        tabClickedByUs = -1
                    }
                },
                backdrop   = backdrop,
                tabsCount  = tabs.size,
                isBlurEnabled = true,
            ) {
                tabs.forEachIndexed { index, tabData ->
                    FloatingBottomBarItem(
                        onClick = {
                            // Mark as user-initiated BEFORE changing selectedIndex
                            // so onSelected sees the flag when it fires.
                            tabClickedByUs = index
                            selectedIndex  = index
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                    ) {
                        if (tabData.bitmap != null) {
                            Image(
                                bitmap        = tabData.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier      = Modifier.size(24.dp),
                                colorFilter   = ColorFilter.tint(LocalContentColor.current),
                            )
                        }
                    }
                }
            }
        }
    }
}
