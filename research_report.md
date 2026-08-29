# Comprehensive Research Report: Kyant0 Backdrop (AndroidLiquidGlass) & Video Surfaces

---

### 1. Repository Identification & Coordinates

* **GitHub Repository URL:** [https://github.com/Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
* **Gradle Dependency Coordinates:** `io.github.kyant0:backdrop` (also published as platform artifacts like `io.github.kyant0:backdrop-android`)
* **Current Architecture:** Originally built for Jetpack Compose on Android, as of **v2.0.0+** it was transitioned to a **Compose Multiplatform (KMP)** library supporting Android (JVM), JVM Desktop (Linux, macOS, Windows), Kotlin/Native (iOS), Wasm, and JS.
* **Official Documentation:** [https://kyant.gitbook.io/backdrop](https://kyant.gitbook.io/backdrop)

---

### 2. Core Mechanism of `io.github.kyant0:backdrop`

* **`rememberLayerBackdrop()`**: Initializes a backdrop state handle.
* **`Modifier.layerBackdrop(backdrop)`**: Applied to background composables to capture/record their drawing into an internal `GraphicsLayer` / DisplayList.
* **`Modifier.drawBackdrop(backdrop, shape, effects)`**: Applied to foreground UI components (buttons, tabs, sheets) to sample the recorded layer and apply AGSL/RuntimeShader effects (blur, refraction lens, chromatic aberration, vibrancy).

---

### 3. Open & Closed GitHub Issues in `Kyant0/AndroidLiquidGlass`

Here is the breakdown of issues tracked in `Kyant0/AndroidLiquidGlass`:

#### A. Video & AndroidView Specific Issues
* **Issue #82: `androidview(videoview)`**
  * **Core Problem:** Users attempting to wrap `AndroidView` (specifically `VideoView` or media player views like ExoPlayer's `PlayerView`) with `Modifier.layerBackdrop(backdrop)` find that video content is **not captured** or appears as an empty/black hole in the backdrop glass/blur effect.
  * **Cause:** `VideoView` / `SurfaceView` creates an independent hardware surface managed directly by Android's `SurfaceFlinger` rather than drawing into the Compose / View display list.

#### B. Other Rendering, Layering & Platform Issues
* **Issue #100: `Circular rendering crash`**
  * **Details:** Occurs when multiple backdrop elements are nested or reference backdrops that are not strictly layered linearly, causing recursive/circular display list recording loops.
* **Issue #91: `drawBackdrop samples incorrect position in Popup / Window`**
  * **Details:** When `drawBackdrop` is used inside a `Popup` or `Dialog` (which resides in a separate Android `Window`), coordinate translation fails, causing the shader to sample the wrong screen area or render a transparent/black patch.
* **Issue #116: `Flickering on physical iOS devices`**
  * **Details:** Liquid glass components flicker on physical iOS devices despite rendering correctly in iOS simulators and on Android.
* **Issue #59: `Liquidglass in text`**
  * **Details:** Discussion regarding applying refraction/glass to dynamic text glyphs. Kyant0 confirmed that without system-level Signed Distance Field (SDF) text rendering, per-character glass refraction cannot achieve 60/120fps performance on Android.
* **Issue #43: `Catalog issue with liquid bottom tabs`**
  * **Details:** Bottom tabs showed a blank solid panel instead of frosted glass due to backdrop source registration issues.
* **Issue #24: `Support adaptive luminance`**
  * **Details:** Enhancement to dynamically adjust shader brightness/contrast based on the average luminance of sampled background pixels.
* **Issue #85: `Liquid Glass In Linux`** (Closed)
  * **Details:** Fixed desktop Skia rendering differences on Linux JVM targets.

---

### 4. Why Backdrop Does Not Work With Video Content (`SurfaceView` vs `TextureView`)

#### Why `SurfaceView` Completely Fails:
1. **SurfaceFlinger Punch-Through:** A `SurfaceView` does not render into the standard View hierarchy or Compose's `GraphicsLayer` / `RenderNode`. It allocates a dedicated hardware surface behind (or in front of) the window and punches a transparent "hole" through the View tree.
2. **GraphicsLayer Invisibility:** When `Modifier.layerBackdrop` records the Compose drawing tree into a `GraphicsLayer`, the `SurfaceView` area is recorded as completely transparent pixels.
3. **`View.draw(Canvas)` Ineffectiveness:** Even calling standard draw passes on `SurfaceView` fails to capture video frames.

#### Why `TextureView` Has Challenges in Compose:
1. **DisplayList Synchronization:** `TextureView` renders as an OpenGLES texture inside the View hierarchy. While `TextureView` *can* be rendered to a Canvas, Compose `GraphicsLayer` display list recording does not automatically know when a new video frame is ready on the media codec thread.
2. **Missing Invalidation:** Video frames update asynchronously at 24/30/60fps. Unless the Compose layer is forced to invalidate/re-record on every video frame, the backdrop will only capture the initial frame (frozen backdrop) or miss updates entirely.

---

### 5. Workarounds, Solutions & Best Practices Across Ecosystem

Based on research across `Kyant0/AndroidLiquidGlass`, `chrisbanes/haze` (Issue #571, #717), `skydoves/Cloudy`, and `QmDeve/AndroidLiquidGlassView`:

#### Workaround 1: Force ExoPlayer / Media3 to use `TextureView`
* In ExoPlayer / Media3 `PlayerView`, change the surface type from `surface_view` to `texture_view`:
  ```xml
  app:surface_type="texture_view"
  ```
  or in Compose `AndroidView`:
  ```kotlin
  AndroidView(
      factory = { context ->
          PlayerView(context).apply {
              // TextureView ensures pixels are in the View rendering tree
              setUseController(false)
          }
      },
      modifier = Modifier.layerBackdrop(backdrop)
  )
  ```

#### Workaround 2: Continuous Invalidation / Re-recording
* Because video decoding occurs on a background codec thread, trigger an invalidation callback each time a frame renders:
  ```kotlin
  player.addListener(object : Player.Listener {
      override fun onRenderedFirstFrame() { /* invalidate */ }
  })
  ```
  In Compose, you can tie a frame loop (`withFrameNanos`) to a ticking state to force the `GraphicsLayer` of `layerBackdrop` to re-record every frame.

#### Workaround 3: PixelCopy Snapshotting (For SurfaceView)
* If `SurfaceView` is mandatory (e.g. 4K HDR or DRM-protected content where `TextureView` is prohibited):
  * Use `PixelCopy.request(surfaceView, bitmap, callback, handler)` to copy the hardware surface to an `ImageBitmap`.
  * Note: `PixelCopy` is asynchronous and adds ~16ms latency, so maintain a double-buffered bitmap to feed into a custom shader background.
  * *(Caution: `skydoves/Cloudy` experienced `RuntimeException: Failed to copy pixels` when PixelCopy was called during view recycling or unattached windows).*

#### Workaround 4: Traditional View System Alternative (`LiquidGlassView`)
* For XML/View-based apps (non-Compose), use `com.qmdeve.liquidglass:core` (`QmDeve/AndroidLiquidGlassView`):
  * Explicitly set `glassView.backdropSource = playerContainerView`
  * Disable window animations (`window.setWindowAnimations(0)`) to avoid freezing refractions.
  * Call `glassView.invalidate()` in the video playback frame callback.

---

### Summary Checklist for Resolving Video Backdrop Issues

1. **Never use `SurfaceView` directly inside `layerBackdrop`** — it will always result in a transparent/black background.
2. **Use `TextureView`** inside `AndroidView` so video pixels exist inside the View/Compose graphics hierarchy.
3. **Invalidate each frame** to prevent the backdrop effect from freezing on the first video frame.
4. **Beware of Popups / Dialogs** — explicit global root coordinate mapping is required if the glass UI is in a separate Window from the video surface.
