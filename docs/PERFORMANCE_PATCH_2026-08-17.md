# HyperBounce Compose performance patch — 2026-08-17

This patch is intentionally limited to high-impact scheduling/interception overhead.
The visual implementations in `toggles/glass/` and the retained legacy renderer were
not changed.

## Changes

1. All injected toggle and slider `ComposeView`s now share one isolated `Recomposer`
   per host process instead of creating one `Recomposer` and coroutine scheduler per
   widget.
2. Toggle checked-state polling every 500 ms was removed. The existing `setChecked`
   hook remains the synchronization path.
3. Slider polling is intentionally retained for behavioral compatibility; the slider
   still uses the shared Compose scheduler, but its existing 500 ms external-state sync
   is not changed by this conservative patch.
4. The global `View.dispatchTouchEvent` hook is installed only for the legacy
   non-liquid path. The active liquid path already receives touches through the
   target widget's `onTouchEvent` hook.

## Intentionally unchanged

- `LiquidToggle.kt`
- `LiquidSlider.kt`
- Liquid glass/backdrop capture behavior
- Jelly/drag physics
- Toggle/slider dimensions and colors
- Legacy/non-Compose renderer
- Native widget state/interaction routing
