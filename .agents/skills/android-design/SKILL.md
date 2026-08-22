---
name: android-design
description: Design, implement, modernize, or review native Android interfaces against the current Google I/O 2026 baseline. Use for Compose-first work involving Android 17 adaptive layouts, Material 3 Expressive, Navigation 3, shared element transitions, motion schemes, state-based Styles, non-touch input, edge-to-edge, predictive back, Compose graphics, AGSL RuntimeShader effects, accessibility, or performance. Build new UI in Jetpack Compose; treat XML layouts and the View system as maintenance or migration concerns rather than the default. Do not use for web interfaces.
---

# Android Design

Build current Android UI with Jetpack Compose first, Material semantics, and adaptive behavior across every window and input mode.

## Currency Gate

Treat July 2026 as the knowledge baseline:

- Google I/O 2026
- Android 17 adaptive-first guidance
- Compose-first UI development
- Compose 1.11 stable from the April 2026 release
- Material 3 1.4 stable, with newer expressive motion and Styles APIs still version-dependent or experimental

Before selecting a version-dependent API, inspect the project's Compose BOM and current Android Developers release notes. Do not copy an alpha API into a stable project without explicit opt-in, an availability plan, and confirmation that the current signature still matches.

Use official Android and Google sources for volatile guidance:

- [Android UI Development is Compose First](https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html)
- [Adaptive development — Google I/O 2026](https://android-developers.googleblog.com/2026/05/android-adaptive-development-ecosystem.html)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- [Shared element transitions](https://developer.android.com/develop/ui/compose/animation/shared-elements)
- [Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview)

## Current Baseline

- Build all new UI and all newly touched feature UI in Jetpack Compose.
- Migrate View or XML surfaces incrementally when work enters them.
- Use Material 3 components and theming before custom controls.
- Model navigation with Navigation 3 for new Compose architecture when project constraints allow it.
- Design adaptive-first for phones, foldables, tablets, desktop windows, Googlebook, connected displays, cars, TV, Wear OS, and XR as relevant.
- Support touch, keyboard, mouse, trackpad, stylus, accessibility services, and rotary or spatial input where the target requires them.
- Keep edge-to-edge, system bars, cutouts, IME, predictive back, and window resizing correct by construction.

## Material 3 Expressive

Use expressiveness to reinforce hierarchy and meaning, not to animate every component.

- Start with Material 3 components, dynamic color, typography, shapes, and state layers.
- Keep utilitarian and frequent interactions on the standard motion character.
- Reserve expressive motion for prominent elements, hero transitions, and meaningful moments.
- Use component defaults before inventing per-component springs.
- If the project uses a Material version with `MotionScheme`, read motion through `MaterialTheme.motionScheme`.
- Do not introduce deprecated `LocalMotionScheme`; current alpha documentation routes motion through `MaterialTheme`.
- Treat `MotionScheme.standard()` and `MotionScheme.expressive()` as version-dependent expressive APIs and verify the current opt-in annotation.
- Distinguish spatial specs, which change bounds or shape, from effects specs, which change color or alpha.
- Keep dynamic color accessible and preserve brand hierarchy through semantic roles.
- Use variable typography or shape morphing only when legibility and interaction state remain clear.

## Adaptive-First Structure

- Make layout decisions from the app window and current capabilities, never physical device labels alone.
- Use `NavigationSuiteScaffold` for adaptive primary navigation.
- Use `ListDetailPaneScaffold` and `SupportingPaneScaffold` for canonical multi-pane tasks.
- Preserve navigation state and selected content as panes appear or collapse.
- Support Large and Extra-large width classes when the project's adaptive library version exposes them.
- Use Navigation 3 scenes and scene decorators for layout-owned bars, rails, dialogs, and multi-destination presentation when appropriate.
- Consider the experimental MediaQuery API only when window size classes do not express a needed capability such as pointer precision.
- Treat new Grid, FlexBox, and Styles APIs as experimental until the selected dependency makes them stable.
- Never lock orientation, aspect ratio, or resizability as a substitute for an adaptive layout.
- Test live window resizing, fold posture, desktop windowing, external displays, and configuration continuity.

## Compose Components and State

- Prefer semantic components such as `Button`, selection controls, Material sheets, and adaptive scaffolds.
- Prefer high-level gesture APIs and components over raw `pointerInput`.
- Hoist state to the lowest owner that needs coordination.
- Keep state stable and save user-visible navigation or task state across recreation.
- Use stable keys for lazy content, reorderable items, and shared transitions.
- Use `AnimatedVisibility` when hidden content must leave composition and semantics.
- Use `AnimatedContent` for a meaningful content identity change.
- Use `animate*AsState` for one value, `updateTransition` for coordinated state, and `Animatable` for cancelable or velocity-aware motion.
- Use current stable APIs before experimental replacements unless the user explicitly wants the new experimental path.

## Navigation and Shared Transitions

- Use Navigation 3 back-stack keys and `NavDisplay` for new compatible architecture.
- Preserve predictive back and system back semantics.
- Use `SharedTransitionLayout` with `sharedElement` only for the same conceptual content.
- Use `sharedBounds` when the container persists but its internal content differs.
- Use unique typed keys, consistent modifier ordering, and the correct animated visibility scope.
- Choose `ScaleToBounds` for text where reflow during transition would be distracting; use remeasurement only when the layout relationship requires it.
- Account for overlay, clipping, and current interoperability limitations.
- Remove caller-managed invisible shared elements after the transition; do not leave them active in the UI or semantics tree.

## Motion

- Name the purpose: feedback, spatial continuity, state explanation, or a bridge over a jarring change.
- Keep frequent actions fast and quiet.
- Let gesture-driven motion interrupt and continue with velocity.
- Use anchors, resistance, touch slop, and nested-scroll contracts.
- Keep predictive back synchronized with visual progress.
- Use Material motion specs and the current theme instead of scattering hardcoded timing constants.
- Never delay navigation, back, or input to finish decorative motion.
- Keep the final state correct when system animations are disabled.

## Compose Graphics and Shaders

Use custom graphics only when the design need is not served by Material components or normal drawing.

- Use `Canvas`, `DrawScope`, `drawBehind`, and `drawWithContent` for custom drawing.
- Use `drawWithCache` only when it actually caches objects such as a brush, path, or shader; otherwise avoid the extra lambda.
- Use `graphicsLayer` deliberately and remember that alpha, `RenderEffect`, and some compositing modes allocate an offscreen layer.
- Use `ShaderBrush(RuntimeShader)` with AGSL for custom GPU fragment effects on Android 13 and later.
- Use `RenderEffect` for content filtering on Android 12 and later.
- Provide a Compose fallback for every lower API level.
- Keep `RuntimeShader`, brushes, paths, and immutable inputs stable; update uniforms instead of rebuilding shader objects per frame.
- Bound overdraw, offscreen buffers, blur, shadows, and shader sampling.
- Stop or simplify continuous effects when offscreen, animation-disabled, or power-sensitive.
- Profile release builds with Perfetto, Macrobenchmark, JankStats, and representative low- and high-refresh devices.

## Styles API

Use the state-based Styles API only when the project intentionally adopts the current experimental Compose version.

- Use Styles for visual configuration and state-driven visual properties.
- Keep behavior, gestures, and accessibility in modifiers and semantic components.
- Prefer Styles when they remove recomposition from pressed, focused, hovered, or selected visual transitions.
- Do not migrate stable production code solely to chase an experimental API.
- Isolate the opt-in so future API changes remain local.

## Accessibility and Input

- Keep touch targets at least 48 dp and text in scalable units.
- Preserve TalkBack focus and semantics through visibility, navigation, and shared transitions.
- Provide accessibility actions and visible alternatives for swipe-only or drag-only behavior.
- Support focus rings and logical tab order for keyboard, mouse, and trackpad.
- Test Remove animations, large font scale, display size, high contrast, Switch Access, and TalkBack.
- Do not make motion, color, haptics, sound, shaders, or shape the only carrier of essential state.

## Reject Legacy-First Solutions

Do not introduce these as the preferred solution for new UI:

- XML layout files or new View hierarchies
- `RecyclerView` for a new Compose feature
- Navigation 2 when a new architecture can adopt Navigation 3
- fixed phone-only breakpoints or device-name checks
- orientation and resizability restrictions
- new custom components that duplicate Material 3
- manually coordinated shared transitions when current Compose APIs fit
- per-frame object allocation or broad recomposition for visual effects
- experimental APIs presented as stable

## Workflow

1. Inspect the Compose BOM, Material version, Android target, min SDK, navigation version, and current architecture.
2. Verify version-dependent APIs against current official documentation.
3. Build semantic Compose content and adaptive pane behavior before visual customization.
4. Adopt Material 3 Expressive selectively and keep standard interactions restrained.
5. Add motion or shaders only with a named purpose, API fallback, and accessibility outcome.
6. Implement only when requested; otherwise return a critique or implementation-ready plan.
7. Validate previews and screenshot tests, then run on phones, foldables, resizable desktop windows, and representative hardware.
8. Measure jank, input behavior, back navigation, state restoration, accessibility, and GPU effects in release builds.

## Output

Lead with the Compose-first, adaptive decision. Cite `file:line` evidence. Label stable, beta, and experimental APIs. Separate required modernization, Material and adaptive correctness, motion and shader quality, accessibility, performance, and optional polish.
