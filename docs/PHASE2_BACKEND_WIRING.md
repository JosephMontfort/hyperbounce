# Phase 2 — backend wiring

## The cross-process mechanism

This module targets the "Modern Xposed API" (`io.github.libxposed.api`), not the classic
`de.robv.android.xposed` API — so the classic `XSharedPreferences` class (a Legacy-API-only
compatibility path) isn't available here. The modern API's own equivalent is **remote
preferences**, confirmed against the official libxposed javadocs:

- **Hook side** (`prefs/HyperBouncePrefs.kt`) — any class holding an `XposedInterface`/
  `XposedModule` reference (every hook class here does) can call
  `module.getRemotePreferences(group)` to get a **read-only** `SharedPreferences`, backed
  by the LSPosed framework's own storage.
- **App side** (`ui/state/XposedServiceBridge.kt`) — the companion app calls
  `XposedServiceHelper.registerListener(...)` once; when LSPosed hands back an
  `XposedService`, that service's own `getRemotePreferences(group)` returns a **writable**
  `SharedPreferences` for the same group/keys.

Both sides use the literal group name `"hyperbounce_settings"`. Every key name and default
value is duplicated between `SettingsRepository.kt` (writer) and `HyperBouncePrefs.kt`
(reader) — they're independent readers/writers of one schema, not a shared class, since one
compiles against the (compileOnly) hook API and the other runs as a normal app. Verified
these match exactly, key for key, before shipping this.

The app's own local `SharedPreferences` file remains the UI's always-available source of
truth — nothing in the UI ever blocks on the Xposed service being bound. Every write mirrors
itself out to remote preferences immediately if the service is already bound; if it isn't
yet (common right after install, since binding is asynchronous), `SettingsRepository` pushes
every current local value to remote in one batch the moment the service does bind.

## What's live vs. what needs an app restart

Visual parameters (blur, lens, highlight, shadow, corner radius, bar height, tab width,
theme override, accent, drag sensitivity, spring feel) update **live** — `HyperBouncePrefs`
exposes them through `rememberLiveNavBarSettings`/`rememberLiveGlassFeel`
(`prefs/LivePrefs.kt`), which register a `SharedPreferences.OnSharedPreferenceChangeListener`
and feed a Compose `State`, so a slider drag in the companion app shows up in Instagram/
WhatsApp/YouTube's nav bar (or a HyperOS toggle/slider) without force-stopping the app.

Structural switches (module enabled, per-app enabled, which render engine, keypad enabled)
are read **once**, at hook-install time (`onPackageReady`), since they gate whether a hook
installs at all — changing them still needs the target app restarted. This is a deliberate
scope boundary: making these live would mean dynamically installing/uninstalling hooks at
runtime, which is a materially bigger undertaking than reading a value on every frame.

## Per-app defaults preserve current behavior exactly

Before wiring, WhatsApp's nav bar already used different hardcoded values than Instagram/
YouTube (70dp bar height / 92dp tab width / 36dp corner radius vs. the shared 64/64/32, and
its own brand green `#21C063` for "auto" accent instead of the adaptive black/white the
other two use). Both `SettingsRepository`'s defaults and `HyperBouncePrefs`' fallback
defaults were changed to preserve these exact per-app values, so wiring this up doesn't
silently change anyone's current on-device behavior — every slider starts exactly where
the hook already was.

## What's wired

- **Module master switch** — gates all of `PhysicsHook`'s nav dispatch and all of
  `LiquidTogglesHook`'s toggle/slider/keypad dispatch.
- **Per-app enable** — gates each of `InstagramHook`/`WhatsAppHook`/`YouTubeHook`'s
  `onPackageReady()` individually (already-independent per-app code from the original
  merge — this was just adding the read, not restructuring anything).
- **Per-app visual settings** — glass intensity, container opacity, corner radius, bar
  height, tab width, theme override, accent — flow into each `LiquidNavigationBar` call.
- **Toggle/slider render engine** — `USE_LIQUID_GLASS_TOGGLE`/`USE_LIQUID_GLASS_SLIDER` in
  `LiquidTogglesHook.java` (no longer `final`, set once per process from prefs).
- **Toggle/slider glass feel** — blur, lens size/depth, highlight, shadow, and (for the
  toggle only, matching what was already parameterized) drag sensitivity + primary spring
  damping/stiffness now flow into `toggles/glass/LiquidToggle.kt` and `LiquidSlider.kt` via
  `LiquidToggleHost`/`LiquidSliderHost`.
- **Keypad** — only the on/off gate, per explicit instruction. The keypad's actual hook
  logic is being replaced with a different implementation later, so no visual tuning was
  added for it — `ui/screens/KeypadScreen.kt` is deliberately just the one switch.

## What's still Phase 1 only

The Settings screen's "Appearance" section (global glass master switch + blur/refraction/
chromatic-aberration/haptics) only affects the companion app's *own* UI chrome (the
dropdown menu, cards, back button) — it was never meant to reach into the hooks, so nothing
there needed backend wiring.
