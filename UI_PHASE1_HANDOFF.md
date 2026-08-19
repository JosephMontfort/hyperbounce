# HyperBounce UI — Phase 1 Handoff

## Scope

This ZIP is the UI-only phase requested for the HyperBounce LSPosed module.

### Implemented

- Full Compose settings surface with iOS-style glass-first visual hierarchy.
- Kyant Liquid Glass catalog implementations vendored into the app module:
  - `LiquidButton`
  - `LiquidSlider`
  - `LiquidToggle`
  - `InteractiveHighlight`
  - `DampedDragAnimation`
  - Morph dropdown implementation based directly on the uploaded `DropdownMenuContent.kt`.
- Top-right morphing dropdown using the uploaded implementation's bouncy animation path.
- The dropdown's `verticalOffset` is preserved at `30dp`.
- Bouncy tuning values are exposed as code-level state and as UI controls:
  - Animation Speed
  - Container Bulge Ratio
  - Inner Content Bulge Ratio
  - Content Pop X Amplitude
  - Content Pop Y Amplitude
  - Drag Jelly Tension
  - Motion Blur Peak
  - Arc Y-Axis Limit
- Glass tuning controls are exposed in the UI:
  - Blur radius
  - Refraction height
  - Refraction amount
  - Highlight
  - Surface alpha
  - Chromatic aberration
- Master liquid-effects switch. The UI passes the enabled/disabled state through its visual model; toggle/slider widgets themselves remain liquid controls as requested.
- Dedicated app pages for Instagram, YouTube, and WhatsApp.
- Per-app state is already isolated at the UI model layer.
- Floating circular back control on subpages uses the library's `LiquidButton`, preserving its built-in press/drag jelly response.
- Toggle/slider implementation-choice controls are represented in each app page for the phase-2 backend binding.

## Intentionally not changed in Phase 1

- Xposed hooks and current runtime behavior.
- Instagram, YouTube and WhatsApp backend hosts.
- Persistent settings storage.
- Actual backend application of the new controls.
- Backend selection between the module's alternative toggle/slider implementations.

Those are deliberately reserved for Phase 2 so the UI can be compiled/tested independently first.

## Build verification

A local Gradle build was attempted in the sandbox, but the wrapper could not download Gradle 9.6 because outbound network access is unavailable in this environment. The source was therefore checked structurally and packaged without claiming a successful compile.
