package dev.joseph.hyperbounce.liquidtoggle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Bridges an arbitrary native Android View subtree into a Compose [Backdrop].
 *
 * LiquidToggle's blur/lens effects need real content behind them to refract --
 * without it the "glass" just distorts a flat color. The toggle lives inside a
 * hooked system app's classic View hierarchy (a Settings/Dialer/Control Center
 * row), not a Compose tree, so there is no [com.kyant.backdrop.backdrops.layerBackdrop]
 * available for it the way the library's own demo app has. Instead, [refresh]
 * periodically rasterizes the row behind the toggle into a bitmap and exposes that
 * as the backdrop, which is enough to sample for [LiquidToggle]'s lens/blur.
 *
 * Refresh cadence is intentionally decoupled from the draw loop -- see
 * LiquidToggleHost's refresh scheduling -- rather than re-rastering the row every
 * frame, since the row's own content (labels, icons, background) is static almost
 * all of the time and a full `View.draw()` call is comparatively expensive.
 */
class ViewSnapshotBackdrop {

    private val bitmapState = mutableStateOf<Bitmap?>(null)

    /**
     * Rasterizes [rowRoot] into an internally-owned bitmap. [selfWrapper] -- the
     * view hosting this toggle's own overlay -- is briefly hidden for the duration
     * of the synchronous draw call so the snapshot captures the row *behind* the
     * toggle rather than a stale copy of the toggle overlaying itself.
     */
    fun refresh(rowRoot: View, selfWrapper: View) {
        val w = rowRoot.width
        val h = rowRoot.height
        if (w <= 0 || h <= 0) return

        val previousVisibility = selfWrapper.visibility
        selfWrapper.visibility = View.INVISIBLE
        try {
            var bmp = bitmapState.value
            if (bmp == null || bmp.isRecycled || bmp.width != w || bmp.height != h) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
            val canvas = Canvas(bmp)
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            rowRoot.draw(canvas)
            bitmapState.value = bmp
        } catch (_: Throwable) {
            // Fail-soft: keep whatever snapshot we already have rather than crash
            // the hooked app's draw pass.
        } finally {
            selfWrapper.visibility = previousVisibility
        }
    }

    fun recycle() {
        bitmapState.value?.let { if (!it.isRecycled) it.recycle() }
        bitmapState.value = null
    }

    @Composable
    fun asBackdrop(): Backdrop = remember(this) {
        object : Backdrop {
            override val isCoordinatesDependent: Boolean = false

            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?
            ) {
                val bmp = bitmapState.value
                if (bmp != null && !bmp.isRecycled) {
                    drawImage(bmp.asImageBitmap())
                }
            }
        }
    }
}
