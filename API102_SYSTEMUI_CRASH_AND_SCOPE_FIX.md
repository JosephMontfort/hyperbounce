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


## Follow-up fix: isolate the injected Compose Recomposer

The earlier `ContextThemeWrapper.getApplicationContext()` override was not sufficient.
The crash can originate from Compose's *parent/window CompositionContext* rather than
from the `ComposeView`'s raw `Context`. ComposeView normally resolves its composition
parent from an ancestor CompositionContext or the window Recomposer.

The port now explicitly installs a dedicated `Recomposer` on the injected ComposeView
via `setParentCompositionContext()`. The dedicated Recomposer uses
`AndroidUiDispatcher.Main` plus a fixed `MotionDurationScale(1f)`, so Compose animation
code does not consult SystemUI's potentially broken WindowRecomposer/applicationContext.

The dedicated Recomposer is cancelled together with the Host to avoid leaking a
composition worker into SystemUI.
