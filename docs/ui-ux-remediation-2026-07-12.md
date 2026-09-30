# Overlay Cam Android UI/UX remediation

## Assessment

The app's core camera and compositing capabilities are strong, but the former interaction model exposed too many controls at once. Multiple floating menus competed for the same screen area, obscured the image being aligned, and made it unclear which action advanced the workflow.

The revised experience is based on five explicit stages:

1. Live camera or photo setup
2. Background ready
3. Overlay alignment
4. Optional point matching
5. Review and save

`OverlayUiState` is the single source of truth for the active stage, title, guidance, primary action, and available secondary controls. This prevents incompatible panels from being visible together.

## Implemented interaction model

- One compact bottom dock/fader rail is the only persistent control surface.
- One dominant action advances the current workflow stage.
- Secondary and precision controls live in one mutually exclusive Material 3 bottom sheet.
- Opening the sheet hides the dock, eliminating stacked or overlapping menus.
- Android back dismisses the tools sheet before leaving point-matching mode or the screen.
- The dock has a maximum width for tablets and landscape layouts.
- The 0.4.0 correction restores only the two high-frequency faders to the preview surface: compact **Blend** and **View zoom** rails. Advanced comparison modes, metrics, alignment, and point tools remain in the sheet.
- A frozen camera frame or background photo is now directly pannable/zoomable before an overlay is selected; the explicit View zoom fader remains available even while overlay-position gestures are unlocked.
- Comparison modes and heatmap palettes now use selected Material 3 filter chips instead of visually identical outlined buttons with textual checkmarks.
- Long-running load, match, and save operations disable conflicting actions and expose progress in the primary action.
- Snackbars remain in the top safe area, away from the bottom workflow controls.

## Accessibility and ergonomics

- The canvas exposes mode-specific semantics and state descriptions.
- Sliders have explicit content descriptions and visible numeric values.
- Gesture controls have slider-based alternatives.
- Touch targets use Material components rather than small custom hit regions.
- The UI respects status-bar and navigation-bar insets.
- Camera permission denial does not block photo-based operation.

## Architecture findings

The UI is now substantially safer than a collection of independent booleans and popups, but `ImageOverlay.kt` remains oversized and coordinates rendering, persistence, image decoding, workflow transitions, and asynchronous operations. A future architectural pass should split it into:

- `OverlayEditorRoute`: launchers, permission integration, and snackbar events
- `OverlayEditorScreen`: stateless screen composition
- `OverlayCanvas`: camera/background/overlay rendering and gestures
- `OverlayEditorViewModel`: all workflow state and intents
- media repository/use cases for decode, alignment, heatmap, and save

That refactor should preserve the current `OverlayUiState` stage contract and use a sealed intent/event model rather than reintroducing independent menu visibility flags.

## Validation

- Docker-backed `testDebugUnitTest` succeeds.
- Kotlin compilation succeeds with the revised Material 3 components.
- Existing geometry, diff, permission-state, point-match, pattern-match, and workflow-state tests pass.

Physical-device validation is still required for camera startup timing, gesture feel, TalkBack traversal, compact-height landscape devices, and OEM photo-picker behavior.
