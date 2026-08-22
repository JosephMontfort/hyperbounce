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

## Round 2: crash/build fixes, floating tab bar, flat aesthetic, progressive blur

- **Build errors** (missing `getValue`/`setValue` imports in `MenuRow.kt`/
  `SegmentedGlassPicker.kt`, a bare `0.6.sp()` needing an `f` suffix, three
  `padding(horizontal=..., top/bottom=...)` calls mixing incompatible overloads) — all
  fixed; see the individual files' diffs.
- **Platform declaration clash** — every `var x by ... private set` property in
  `SettingsRepository.kt` also has an explicit `fun setX(...)` (so callers can take a bound
  method reference). Both compile to the same JVM `setX(...)` signature regardless of the
  property setter's Kotlin visibility, which the JVM rejects as a duplicate method. Fixed
  with `@JvmName("...")` on all 26 explicit setters — same Kotlin-level name, distinct JVM
  symbol.
- **RenderThread SIGSEGV on launch** — `.layerBackdrop(backdrop)` was on the root `Box`
  that also contained every glass consumer as a descendant, so each captured frame included
  the previous frame's glass rendering, which included the capture again: an unbounded
  recursive RenderNode graph. Fixed by tagging only a plain background `Box` with
  `layerBackdrop`, as a sibling of the screen content rather than its parent — exactly how
  the library's own catalog `HomeContent` structures it.
- **Floating bottom tab bar** — `ui/glass/LiquidBottomTabs.kt` is a byte-for-byte port of
  the catalog's `LiquidBottomTab`/`LiquidBottomTabs` (verified via diff against the source),
  wrapped in a fixed-width `Box` (184dp) pinned to `Alignment.BottomStart` so it floats as a
  compact pill instead of the catalog demo's full-width bar — no internal change needed,
  since `LiquidBottomTabs` already sizes itself off whatever width constraint it's given.
  Two tabs: **Tuning** (per-app nav-bar settings + the toggle/slider engine — "what the
  hooks actually do") and **Settings** (module switch, global glass appearance, about —
  "HyperBounce itself"). The bar hides while drilled into a subpage and reappears on
  popping back to a root tab, matching iOS tab bars. `Destination.Home` was replaced with
  `Destination.Tuning`/`Destination.Settings`; `backStack[0]` is always one of the two, and
  switching tabs resets the stack to that tab's root.
- **Flat aesthetic** — glass/backdrop is now reserved for toggles, sliders, the floating
  tab bar, and dropdown/popup menus only. `GlassCard`, `CircularGlassBackButton` (which
  keeps its jelly touch-and-drag motion — that's a graphicsLayer transform, not a
  rendering effect, so it doesn't need a backdrop), `NavBarPreview`, and the About screen's
  icon badge are now flat solid fills. `SegmentedGlassPicker` was removed outright (see
  below). The colorful gradient wallpaper is gone too — the background is now a plain
  `colors.background` fill (near-white in light mode, true black in dark mode), matching
  real iOS chrome instead of a decorative backdrop.
- **Every "choose one of several options" control is now a dropdown menu** —
  `ui/components/DropdownPicker.kt` wraps `ExpandableGlassMenu` (compact button showing the
  current selection + chevron, opens into the same glass menu style as the top-right "...")
  and replaced `SegmentedGlassPicker` everywhere: the Theme picker on each app's detail
  screen, and both engine pickers on the Engine screen. `SegmentedGlassPicker.kt` was
  deleted once nothing referenced it.
- **Menu: 3 items + real drag-to-select** — the top-right menu now has Reset to Defaults,
  Copy Debug Info (version + current engine state, via `LocalClipboardManager`), and About.
  Fixed a real bug in the ported gesture handler: the release branch that fires
  `onRowSelected` was gated on `isExpanded`, which is captured *before* the gesture starts —
  so a single continuous long-press → drag across options → release over one never
  actually selected anything, only a drag on an *already-open* menu did. Removed that gate;
  a continuous long-press-drag-release now correctly triggers the hovered row's action.
- **Progressive blur** — `ui/glass/ProgressiveBlurHeader.kt` ports the catalog's
  `ProgressiveBlurContent` (a real AGSL shader chained after `blur()`, not a fade
  overlay): full strength at the very top, smoothly masked to nothing by the middle of the
  header zone. Both scaffolds now tag their scrolling column with its own local
  `rememberLayerBackdrop()` (separate from the app-wide `backdrop` the toggles/sliders/menu
  use) so the header blurs the actual list content passing underneath it, and both got a
  matching collapsing-title treatment (`RootScaffold` now fades/collapses its large title
  into a small fixed one on scroll, the same pattern `ScreenScaffold` already had).
