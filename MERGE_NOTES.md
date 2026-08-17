# HyperBounce merge notes

This repo is a manual, line-by-line merge of two unrelated branches:

- **`hyperbounce-liquid-glass-floating-nav-102`** — floating liquid-glass nav bar
  injected into Instagram, WhatsApp and YouTube.
- **`hyperbounce-liquid-toggles-102`** — liquid-glass toggles/sliders/keypad for
  HyperOS/MIUI SystemUI, Settings, Phone, Contacts, etc.

Every source file from both branches is present below, unmodified in logic. Only
package declarations, imports, two file/class names, and a handful of config files
changed. No method body, hook, animation constant, or drawing routine was rewritten.

## 1. The one real conflict: two `PhysicsHook` entry classes

Both branches independently kept a `dev.joseph.hyperbounce.PhysicsHook` entry class
(same name, same package) — one Kotlin (`XposedModule` dispatching to
Instagram/WhatsApp/YouTube by package name), one Java (`XposedModule` hooking MIUI
toggle/slider/keypad classes). That's a hard duplicate-class compile error once both
are in one module, so exactly one had to be renamed. Everything else in both branches
turned out to have zero naming collisions (verified by grepping every top-level
symbol in both branches against each other).

**Decision:** kept the floating-nav class as `dev.joseph.hyperbounce.PhysicsHook`
(unchanged) and renamed the toggles entry class to
`dev.joseph.hyperbounce.toggles.LiquidTogglesHook`. Both are registered as
independent entries in `java_init.list` — confirmed via the libxposed docs that this
file supports multiple fully-qualified entry classes, each instantiated once per
process. Their `onPackageReady()` bodies are byte-for-byte the same logic as before,
just repackaged; their target scopes don't overlap (Instagram/WhatsApp/YouTube vs.
the MIUI system-app list), so nothing changes about when either fires.

If you'd rather have a single combined entry class instead of two, that's a
mechanical follow-up — say the word and I'll fold them together.

## 2. What was deduplicated vs. kept deliberately separate

Both branches had their own copy of a few "looks-identical" utility files. I diffed
every one of these line-by-line before touching anything:

- **`FakeLifecycleOwner`** — genuinely identical in both branches (only whitespace/
  visibility-modifier differences). Deduplicated to one shared copy at
  `dev.joseph.hyperbounce.FakeLifecycleOwner`, used by both `nav` and `toggles`.
- **`DampedDragAnimation` / drag-gesture detection** — these looked like the same
  utility (both "ported from AndroidLiquidGlass-kmp") but the spring constants,
  press/release semantics, and gesture-slop behavior are **different** between the
  nav bar's copy and the toggle/slider's copy (e.g. nav uses a stiff snap-to-value
  update and a 4px drag threshold so taps still reach the tab underneath; toggles use
  a softer spring and start dragging on first touch). Merging these would have
  changed one feature's feel to match the other's, which is exactly what "same
  runtime behavior" rules out. **Kept as two separate copies**, one per feature:
  `dev.joseph.hyperbounce.nav.glass.*` and `dev.joseph.hyperbounce.toggles.glass.*`.
- **View-backdrop capture classes** (`LiquidGlassViewBackdrop`,
  `LiquidToggleViewBackdrop`, `LiquidSliderViewBackdrop`) — each has feature-specific
  logic (the nav one additionally composites `SurfaceView`/`TextureView` frames for
  Reels video; the toggle/slider ones hide the native widget while capturing). Already
  uniquely named, so no rename was needed — kept as three separate files.

## 3. Folder/package layout (the "modularize" ask)

```
dev.joseph.hyperbounce/
├── PhysicsHook.kt                 entry point 1 — floating nav dispatcher
├── FakeLifecycleOwner.kt          shared by both feature packages
├── nav/                           dev.joseph.hyperbounce.nav
│   ├── InstagramHook.kt / InstagramNavBarHost.kt
│   ├── WhatsAppHook.kt / WhatsAppNavBarHost.kt
│   ├── YouTubeHook.kt / YouTubeNavBarHost.kt
│   ├── LiquidNavigationBar.kt
│   └── glass/                     dev.joseph.hyperbounce.nav.glass (nav's private physics engine)
└── toggles/                       dev.joseph.hyperbounce.toggles
    ├── LiquidTogglesHook.java     entry point 2 — toggles/sliders/keypad dispatcher
    ├── LiquidToggleHost.kt / LiquidToggleViewBackdrop.kt
    ├── LiquidSliderHost.kt / LiquidSliderViewBackdrop.kt
    ├── LiquidKeypadHook.kt
    └── glass/                     dev.joseph.hyperbounce.toggles.glass (renamed from bare "liquidglass")
```

Every cross-file reference that crossed a new package boundary was checked with an
exhaustive grep of every top-level symbol against every file in both branches, then
given an explicit import (never a wildcard) so nothing relies on accidental same-
package resolution. Full list of files touched only for a package/import change
(no logic change) vs. files with an actual rename is in the diff — ask if you want
that spelled out per-file.

## 4. Dependencies

Turned out to need almost no reconciliation — both branches already used identical
versions of everything (AGP 9.3.0, Kotlin Compose plugin 2.4.10, Compose Multiplatform
1.11.1, `io.github.kyant0:backdrop:2.0.0` + `:shapes:1.2.0`, same AndroidX
activity/lifecycle/savedstate versions). The one real difference:

- **libxposed API artifact source** — floating-nav fetched
  `io.github.libxposed:api:102.0.0` from the `api.xposed.info` Maven repo; toggles
  vendored the identical artifact as a local `libs/api-102.0.0.aar`. Standardized on
  the local AAR (already in the repo, no third-party Maven host needed at build
  time). This is a `compileOnly` dependency — not packaged into the module — so this
  is a build-time-only change with zero effect on compiled bytecode.
- Dropped the now-unused `api.xposed.info` Maven repo from `settings.gradle.kts`.

## 5. Small config decisions (flagged, not hidden)

- **`minSdk`**: nav declared 33, toggles declared 31. Used **33** (the higher/more
  conservative bound) so neither feature ends up installable on an OS level its own
  branch never declared/tested support for. Easy to change to 31 if you'd rather
  maximize install base for the toggles half.
- **`minApiVersion`** (libxposed framework version, in `module.prop` — unrelated to
  Android `minSdk`): nav declared 102, toggles declared 101. Same reasoning — used
  **102** (the higher bound). `targetApiVersion=102` was already agreed by both.
  `staticScope=false` was carried over from the toggles branch (nav didn't set it).
- **`scope.list`**: straight union of both branches' package lists — no overlap
  existed, so nothing was dropped or merged.
- **`app_description`** string: both branches had their own single-purpose
  description. Both original strings are kept in `strings.xml`
  (`xposed_description_nav_experiment`, `xposed_description_toggles_experiment`) for
  reference; the manifest now points at a new combined `app_description` string.

## 6. Everything else

`proguard-rules.pro` (from toggles; nav had none), `LICENSE_AndroidLiquidGlass`
(attribution for the upstream Kyant backdrop library, from toggles), and all five
`.md` design-note files from both branches were carried over unchanged into `docs/`.
The CI workflow was merged using the floating-nav branch's version (broader trigger,
`action-gh-release@v3`, `target_commitish` set) with an updated release-body string.
