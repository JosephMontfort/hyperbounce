# HyperBounce UI — Phase 1 design notes

Phase 1 scope: a full settings UI for the module, built purely from AndroidLiquidGlass
components, with every control persisted to SharedPreferences — but nothing on the Xposed
hook side reads these values yet. That wiring, plus splitting the shared Instagram/
WhatsApp/YouTube nav-bar code apart per app, is Phase 2.

## What's new

- `ui/MainActivity.kt` — a real launcher Activity (`AndroidManifest.xml` gained one
  `<activity>` entry). Purely additive: LSPosed loads hooks via java_init.list/scope.list
  regardless of whether this Activity exists, so nothing about hook behavior changed.
- `ui/glass/` — reusable glass primitives, either ported verbatim from the library's own
  demo catalog (`InteractiveHighlight`, `DragGestureInspector`, `DampedDragAnimation`,
  `LiquidButton`, `LiquidToggle`, `LiquidSlider` — diffed line-by-line against the catalog
  source, only package/one bugfix import differ) or ported from your own
  `ExpandableGlassMenu`/`GlassEffectContainer`/`MenuAlignment`/`MenuAnimationPreset`
  catalog page (see below).
- `ui/components/` — this app's own composables built on those primitives:
  `CircularGlassBackButton`, `GlassCard`, `SettingsNavRow`/`SettingsToggleRow`/
  `SettingsSliderRow`, `SegmentedGlassPicker`, `NavBarPreview`, `MenuRow`, and a small
  hand-drawn icon set (`Icons.kt` — no icon-font dependency exists in this project).
- `ui/state/` — `SettingsModels.kt` (every setting's shape) + `SettingsRepository.kt`
  (SharedPreferences-backed, one primitive entry per field, Compose `State`-observable).
- `ui/screens/` — Home, per-app detail (Instagram/WhatsApp/YouTube), Engine, About.

## The expandable glass menu

The top-right menu on Home is your `ExpandableGlassMenu`/`GlassEffectContainer` catalog
page, ported with its animation/gesture/haptics logic unchanged: long-press-or-tap to
open, drag-to-hover-then-release-to-select, the zero-recomposition width/height morph via
`Modifier.layout`, the per-frame jelly/squish math, the haptic tick on row hover, and the
later "advanced physics tuning" pass (spring overshoot bulge, motion blur peak, drag jelly
tension, content pop amplitude, arc Y offset, animation speed multiplier). Two changes:

1. `MenuRow`'s `icon: ImageVector` became `icon: @Composable () -> Unit` — this project has
   no icon-font/vector-icon dependency, so there was no `ImageVector` to pass. Every other
   line of `MenuRow` is unchanged.
2. The demo-only "Properties"/"Advanced Physics Tuning" editor screen
   (`ExpandableGlassMenuContent`, `TuningControl`) wasn't ported — this app doesn't need a
   live-tuning UI for the menu's own parameters. The 8 physics constants it exposed
   (`animationSpeedMultiplier`, `containerBulgeMultiplier`, `contentBulgeMultiplier`,
   `contentPopX`, `contentPopY`, `dragJellyTension`, `motionBlurAmount`, `arcYOffsetDp`) are
   still real parameters on `ExpandableGlassMenu` with the same defaults — they're just
   code-level knobs now: change the default value in `ui/glass/ExpandableGlassMenu.kt` to
   retune the motion, no UI plumbing needed.

At the Home screen call site: `MenuAnimationPreset.Bouncy`, `MenuAlignment.TopTrailing`,
`verticalOffset = 30f` (the panel drops a deliberate 30dp below the button rather than
growing flush against it), `cornerRadius = 22.dp`. `isGlassEnabled`/`blurRadius`/
`refractionHeight`/`refractionAmount`/`chromaticAberration`/`isHapticsEnabled` all come
from `LocalGlassSettings` — the component already exposed exactly those as hoisted
parameters, so wiring it to this app's global glass settings needed zero internal changes.

## Global glass settings

Home's "Appearance" section is a new master switch ("Liquid Glass Effects") plus Blur/
Refraction Height/Refraction Amount sliders and a Chromatic Aberration toggle — provided
app-wide via `LocalGlassSettings` (a `CompositionLocal`, set once in `MainActivity.kt` from
`SettingsRepository.globalGlass`) rather than threaded as a parameter through every screen.

Every structural glass surface reads it: `CircularGlassBackButton`, `GlassCard`,
`SegmentedGlassPicker`'s sliding pill, `NavBarPreview`, and the expandable menu. When the
switch is off, each falls back to a flat translucent fill instead of real backdrop blur —
the jelly press/drag motion on the back button still works either way, only the material
changes.

This deliberately does **not** apply to `LiquidToggle`/`LiquidSlider` anywhere — not this
app's own on/off switches and sliders, and not the HyperOS toggle/slider engine on the
Engine screen (which has its own separate, dedicated controls). An interactive control
turning translucent-flat mid-drag would be confusing; static chrome doesn't have that
problem.

## Known trade-off: recomposition scope

Every slider reads its value via `holder.snapshot()` at the top of its screen, so a drag
recomposes the whole screen rather than just that row. Functionally correct, and fine for a
screen this size, but if a real device shows jank while dragging, the fix is switching
`SettingsSliderRow`'s `value: Float` to `value: () -> Float` so `LiquidSlider` can read it
at its own narrower scope — flagging it now rather than risking an unverified refactor I
can't compile-test in this environment.

## Everything else carried over from the last merge

Dependencies: added `org.jetbrains.compose.animation:animation:1.11.1` (for
`AnimatedContent` screen transitions — the rest of the app's animation already came
transitively via `compose.foundation`, matching the library's own dependency list, but
`AnimatedContent` lives in a separate artifact). Nothing else changed.
