# HyperBounce Performance Patch — Phase 2

## Goal
Reduce scroll-time frame work without changing LiquidToggle/LiquidSlider visuals or interaction.

## Change
The liquid Compose replacements are placed in `ViewGroupOverlay`. Unlike the native child
views, those overlay views can remain renderable even when their target is completely clipped
off-screen by a `RecyclerView`/`ScrollView`.

Phase 2 adds a target-local `OnPreDrawListener` that mirrors the target's actual
`getGlobalVisibleRect()` state onto the Compose overlay (`VISIBLE`/`GONE`).

This is intentionally local rather than a global frame scanner:
- it runs only while the native target participates in the draw traversal;
- it avoids iterating every registered liquid host every frame;
- the actual LiquidToggle/LiquidSlider composables and glass effects are unchanged.

Explicit native visibility continues to be respected.

## Files changed
- `LiquidToggleHost.kt`
- `LiquidSliderHost.kt`

## Not changed
- `LiquidToggle.kt`
- `LiquidSlider.kt`
- backdrop capture/effects
- jelly/bloom animation
- non-Compose implementation
- state synchronization semantics
