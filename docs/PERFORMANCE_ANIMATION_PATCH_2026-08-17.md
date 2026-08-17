# HyperBounce Toggle Animation + Performance Patch — 2026-08-17

This patch is intentionally limited to the active Compose liquid-toggle path.

## Animation changes

- Tap/state-change animation now uses a deliberately slower spring.
- The press/bloom phase is allowed to reach its full state before the thumb settles.
- Drag settling uses a softer spring than the old 800-stiffness spring, giving a small premium follow delay without changing the visual geometry.
- Added edge overscroll consumption:
  - when the finger pulls beyond the left/right edge, the thumb remains pinned;
  - reversing direction first consumes that out-of-bounds finger travel;
  - only after crossing back over the edge does the thumb move again.
- Existing jelly scale, glass effects, haptics, dimensions, colors, and threshold behavior are preserved.

## Performance changes

- Removed the intermediate Compose `fraction` state that was updated for every pointer-move sample.
- Pointer movement now updates the existing `Animatable` target directly.
- Drag bookkeeping (`didDrag`, edge overscroll) is plain remembered mutable data, not snapshot state.
- Replaced the haptic `LaunchedEffect(value)` key with one long-lived `snapshotFlow` collector, avoiding coroutine/effect restarts every animation frame.
- Replaced the `LaunchedEffect(selectedLambda)` observer with one stable long-lived effect keyed by `Unit`.

## Intentionally unchanged

- `LiquidToggleHost` shared Recomposer architecture from the previous performance patch.
- `LiquidSlider` implementation and synchronization polling.
- `LiquidToggleViewBackdrop` capture implementation.
- Legacy/non-Compose implementation.
- Liquid-glass visual primitives and library dependency.

## Verification

- Kotlin source brace/parenthesis balance checked.
- Standalone `kotlinc` parsing was attempted; it cannot resolve AndroidX/Compose dependencies outside the project build.
- Full Gradle compilation could not be run in this environment because Gradle 9.6.0 is not cached and network access to services.gradle.org is unavailable.

## Build-fix follow-up (2026-08-17)
Corrected Kotlin API/import issues introduced by the animation/performance patch:
- `snapshotFlow(...).first()` now imports `kotlinx.coroutines.flow.first`.
- Parallel settle animations now run inside `coroutineScope { ... }`.
- Added `androidx.compose.ui.graphics.drawscope.scale` for the existing backdrop transform.
No animation constants or visual behavior were changed in this correction.
