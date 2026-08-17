# SystemUI Compose crash: Recomposer isolation fix

## Observed crash

`WindowRecomposer_androidKt.getAnimationScaleFlowFor()` receives a null
`applicationContext` and crashes when `MotionDurationScaleImpl` first evaluates an
animation. The call originates from `DampedDragAnimation.updateValue()` ->
`Animatable.animateTo()`.

## Why the previous context fix did not work

Overriding `ContextThemeWrapper.getApplicationContext()` only changes the View's
Android `Context`. It does **not** necessarily change the Compose `CompositionContext`
selected when `ComposeView` creates its composition.

ComposeView resolves its parent composition context from:

1. an explicitly supplied `setParentCompositionContext()`
2. an ancestor/ViewTree CompositionContext
3. a cached context
4. the window Recomposer

SystemUI is itself a Compose-heavy process, so an injected ComposeView can inherit
SystemUI's Recomposer. That Recomposer supplies Compose animations with its own
`MotionDurationScale`. The crash therefore bypassed the previous Context wrapper.

## Fix

The Host now creates a dedicated Recomposer:

- `AndroidUiDispatcher.Main`
- fixed `MotionDurationScale(scaleFactor = 1f)`
- `ComposeView.setParentCompositionContext(isolatedRecomposer)`
- a dedicated coroutine scope running `runRecomposeAndApplyChanges()`
- cancellation of the Recomposer/scope during Host disposal

This keeps the injected LiquidToggle animation system independent of the SystemUI
window Recomposer while still using Android's main-thread frame clock.

## API 102 relevance

This particular NPE is a Jetpack Compose/Recomposer integration failure, not an
Xposed hook registration failure. API 102 does not automatically repair it.

The API 102 migration does, however, give us the modern `XposedModule`/hooking
architecture needed to make this isolation cleanly and to use modern framework
facilities such as deoptimization where ART inlining affects hook points.

## Expected result

`Animatable.animateTo()` should now see the injected Recomposer's non-null
`MotionDurationScale`, so it should no longer enter the failing
`getAnimationScaleFlowFor(applicationContext)` path belonging to SystemUI's window
Recomposer.

This needs on-device validation in the actual HyperOS SystemUI process.
