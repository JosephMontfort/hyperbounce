# Liquid Glass Toggle Experiment

This branch keeps the original HyperBounce toggle renderer and the original
custom slider renderer intact. The build currently enables only the imported
AndroidLiquidGlass-kmp `LiquidToggle`.

## Toggle paths preserved

1. Standard HyperOS/Miuix `SlidingButton` / `SlidingSwitch` hooks.
2. The Preference-bound Dialer/Mobile Network "nuclear replacement" path,
   where a fresh interactive `SlidingButton` is injected into the preference
   widget frame.
3. Late-loaded SystemUI / MIUI SystemUI plugin `SlidingButton` classes found
   through the ClassLoader hook.

All three paths ultimately use the native `CompoundButton` as the real state
and click authority. The Compose LiquidToggle is only the visual/interaction
surface.

## Legacy implementation

The old Canvas/RenderNode/AGSL toggle is still present in `PhysicsHook.java`
and is guarded by `USE_LIQUID_GLASS_TOGGLE`. Set that constant to `false` to
restore the old toggle renderer without removing it.

The legacy slider renderer is intentionally unchanged.

## Liquid Glass backdrop bridge

`LiquidToggleViewBackdrop` implements the library's `Backdrop` interface and
feeds the imported Kyant backdrop engine from a live, double-buffered View
render. It does not use PixelCopy.

The Compose overlay is attached to the window decor as a small bounded
`ComposeView`, while the backdrop source is selected from the highest app
content ancestor below the decor. Because the native toggle's `onDraw` is
suppressed in liquid mode, the live source cannot recursively capture the
liquid surface.

The library's own `LiquidToggle` source, `DampedDragAnimation`, and
`DragGestureInspector` are included verbatim apart from package relocation so
their visual/interaction behavior remains directly comparable with the
upstream AndroidLiquidGlass-kmp 2.0.0 sources.

The control is rendered at 97.5% of the library's 64dp x 28dp outer size, which
is slightly larger than the previous 95% HyperBounce toggle while remaining
slightly smaller than the library's full size.

## Dependencies

The module uses the same Compose / Kotlin / AGP / Gradle toolchain that was
already used successfully by the Liquid Glass floating-navigation experiment:
AGP 9.3.0, Kotlin Compose 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.6.0,
Backdrop 2.0.0, Shapes 1.2.0.


## Follow-up efficiency fix

The initial decor-level overlay used `ViewTreeObserver.OnScrollChangedListener`
to call `refreshGeometry()` on every scroll callback and rewrote `LayoutParams`.
That solved alignment but was unnecessarily expensive.

The current implementation keeps the decor-level overlay (needed to escape
ancestor clipping and keep the backdrop source separate), but coalesces scroll
callbacks with `postOnAnimation()` and moves the already-laid-out ComposeView
with `translationX/Y`. This restores the native toggle's scroll-following
behavior without causing a parent relayout on every scroll event.

The backdrop bridge also no longer runs a perpetual Choreographer callback.
Its existing pre-draw capture plus 60 FPS minimum capture interval provides the
same real-time source with less always-on scheduling overhead.

## Scroll/attachment architecture (fix3)

The liquid toggle is intentionally attached to the native toggle's immediate
parent via `ViewGroupOverlay`, not to the window/decor. This preserves the
native row/container coordinate system: when RecyclerView/ListView/ScrollView
moves the row, the overlay moves automatically with it.

The backdrop captures only that same parent/container. The native toggle's
`onDraw()` is suppressed by `PhysicsHook.java`, and the injected overlay is
temporarily hidden during the local container capture, preventing recursive
self-capture.

Do not reintroduce a global `OnScrollChangedListener` or per-frame window
position tracking for toggle placement. The overlay should remain parent-bound.
