# API 102 system-app scope and SystemUI crash fixes

## Scope
The original legacy module declared 24 default scope packages. The port now preserves all 24 in `META-INF/xposed/scope.list`.

`staticScope=false` is intentional: the listed packages are the default/recommended scope, but users are not blocked from selecting additional packages outside that list. This follows the modern API's `module.prop` `staticScope` semantics.

## CI
`.github/workflows/build.yml` now runs automatically on pushes to `liquid-toggles-102`.

## SystemUI crash
The reported crash is:

`WindowRecomposer_androidKt.getAnimationScaleFlowFor()`
-> `MotionDurationScaleImpl.getScaleFactor()`
-> `Animatable.animateTo()`

The NPE is on `Context.getContentResolver()` because Compose's window recomposer obtains an application context from the injected Compose context. Some HyperOS/SystemUI/plugin context implementations can return a null `applicationContext`.

API 102 does **not** itself fix this NPE. Its `deoptimize()` feature is valuable for ART inlining problems, but this stack occurs after the hook has already succeeded and while Compose starts an animation. The fix is to guarantee that the context supplied to `ComposeView` returns a non-null application context.

The injected `ContextThemeWrapper` now overrides `getApplicationContext()` and falls back to the resolved framework application context.

This fix is deliberately localized to the Compose injection boundary and does not modify the liquid-glass animation implementation.
