# Liquid Glass Floating Navigation Bar — Experimental

This branch ports the visual/interaction implementation of `LiquidBottomTabs`
from AndroidLiquidGlass-kmp into HyperBounce.

## Library integration
- `io.github.kyant0:backdrop:2.0.0`
- `io.github.kyant0:shapes:1.2.0`
- Compose Multiplatform `1.11.1`
- Kotlin `2.4.10`
- AGP `9.3.0`
- Gradle `9.6.0`

## Backdrop architecture
The actual Kyant `Backdrop` renderer is used for:
- vibrancy
- blur
- lens/refraction
- chromatic aberration
- highlights
- shadows
- inner shadows
- combined tab-content backdrop

Because the target apps are native View hierarchies rather than the demo app's
single Compose hierarchy, `LiquidGlassViewBackdrop` supplies the live application
pixels to the library's `Backdrop` interface. It uses a double-buffered `View.draw`
capture immediately before the target window draws; it intentionally does not use
PixelCopy.

The injected Compose overlay is attached to the window decor, while each app's
native content View is supplied as the backdrop source. This keeps the liquid bar
out of its own backdrop.

## App-specific behavior preserved
- Instagram tab resource-name extraction and profile-circle handling
- WhatsApp menu/icon extraction and native tab-click behavior
- YouTube pivot-bar/icon extraction and native tab-click behavior
- Existing fullscreen/inset/layout workarounds
- `PhysicsHook.kt` untouched
- No slider code is modified
