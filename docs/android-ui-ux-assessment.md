# Overlay Cam — Android UI/UX assessment and remediation

Status: implemented and unit-tested
Scope: Android application shell, camera/photo onboarding, overlay workflow, gestures, tool presentation, export feedback, accessibility, lifecycle behavior, and high-cost image operations.

## Executive assessment

The original editor exposed a capable image-comparison engine through an unstable interaction model. The most serious problem was not visual styling; it was competing ownership of the screen and of pointer input:

- a custom expanded control surface permanently occupied the same canvas as the editor;
- additional menus and action rows could extend beyond the visible viewport;
- transform, drag, and effect-swipe detectors competed for the same gesture stream;
- state-changing work provided little or no progress feedback;
- several visible actions did not correspond to a valid current state;
- camera permission was treated as mandatory even though photo-to-photo editing works without it;
- export geometry could differ from preview geometry;
- the landmark-matching interaction did not create a meaningful source/target pair.

The remediation replaces this with a staged editor and one transient tool surface. The canvas owns direct manipulation; the bottom dock owns the single recommended next action; the modal sheet owns detailed tools. Long-running work is explicit, cancellable through normal coroutine lifecycle behavior, and does not execute image composition or file I/O on the UI thread.

## Severity assessment

| Area | Prior severity | Prior failure mode | Resolution |
|---|---:|---|---|
| Control surfaces | Critical | Custom sheet and menus could visually cover or overlap one another | One compact dock plus one Material modal bottom sheet |
| Gesture ownership | Critical | Drag, transform, tap, and vertical effect swipe competed on the same canvas | Transformable owns edit gestures; taps are reserved for landmark mode; effects are explicit buttons |
| Export responsiveness | Critical | Bitmap composition and MediaStore writes could block the main thread | Composition on Default dispatcher; output on IO dispatcher; visible progress state |
| Landmark matching | Critical | A tap generated identical source and target positions, producing an identity correction | Two-phase source-then-target landmark pairing with explicit guidance |
| Preview/export fidelity | High | Loaded photos were displayed center-cropped but exported using different geometry | Shared center-crop geometry for preview-related matching, heatmap, and export |
| Camera lifecycle | High | Camera could remain bound after leaving live preview; stale capture handles remained available | LifecycleOwner-based CameraX binding, disposal unbind, stale handle clearing |
| Permission onboarding | High | Runtime camera permission launched before the user understood why and blocked photo mode | Explicit “live camera” or “continue with photos” choice; optional later enablement |
| Image import memory | High | Full-resolution phone images were decoded directly | Sampled decoding capped to a 2048-pixel edge; EXIF orientation handling |
| Multi-overlay workflow | High | First layer disappeared while aligning the second; a third add could overwrite layer two | First layer remains visible; supported two-layer limit is represented and enforced |
| Save reliability | High | Picker cancel could clear state; older Android export permission was absent; partial MediaStore entries could remain | Cancel preserves state; API 24–28 permission path; atomic pending publish on API 29+ |
| Configuration changes | High | Rotation destroyed the composition | Editor state and bitmap ownership moved to an Activity ViewModel |
| Accessibility | Medium | Hidden gestures and undersized custom crop handles had no equivalent accessible controls | Explicit Material actions/sliders, canvas semantics, no custom 20 dp handles |
| Feedback | Medium | Toast-only and silent operations | Snackbar feedback, loading labels, disabled conflicting actions, progress indicator |
| Visual hierarchy | Medium | Dense controls with weak stage/primary-action hierarchy | Stage pill, title, supporting instruction, full-width primary action, wrapped secondary actions |

## Resulting interaction model

```text
                          ┌──────────────────────┐
                          │ Access choice        │
                          │ camera | photos      │
                          └──────────┬───────────┘
                                     │
                 ┌───────────────────┴───────────────────┐
                 │                                       │
        ┌────────▼────────┐                     ┌────────▼────────┐
        │ Live camera     │                     │ Photo mode      │
        │ Freeze frame    │                     │ Pick background │
        └────────┬────────┘                     └────────┬────────┘
                 └───────────────────┬───────────────────┘
                                     │
                          ┌──────────▼───────────┐
                          │ Background ready     │
                          │ Add overlay          │
                          └──────────┬───────────┘
                                     │
                          ┌──────────▼───────────┐
                          │ Align overlay        │
                          │ move / scale / rotate│
                          │ sliders / auto-align │
                          └──────┬─────────┬─────┘
                                 │         │
                    ┌────────────▼──┐   ┌──▼─────────────┐
                    │ Match points  │   │ Done adjusting │
                    │ source→target │   └──┬─────────────┘
                    └────────────┬──┘      │
                                 └──────┬───┘
                                        │
                             ┌──────────▼───────────┐
                             │ Review               │
                             │ effects / compare    │
                             │ add second / save    │
                             └──────────────────────┘
```

### Surface ownership

```yaml
canvas:
  owns:
    - camera/background preview
    - overlay rendering
    - move, pinch-scale, and rotation gestures
    - landmark taps only while landmark mode is active
  never_owns:
    - hidden effect switching
    - menu expansion
    - save feedback

compact_dock:
  owns:
    - current stage
    - current instruction
    - exactly one recommended primary action
    - quick opacity while aligning
    - entry into detailed tools

modal_tool_sheet:
  owns:
    - secondary workflow actions
    - precision sliders
    - auto-align
    - landmark tools
    - review effects and comparison settings
  constraints:
    - only one sheet can exist
    - bounded to 90 percent of available height
    - vertically scrollable
    - dismissible by drag, Done, or system back
```

## Implemented architecture

### Presentation state

`OverlayUiState` derives the stage, title, guidance, recommended action, tool label, and visible panels from a small set of workflow facts. UI copy and primary-action selection are therefore deterministic rather than distributed across branches in the composable.

`PointMatchUiState` separately represents the landmark subflow:

```text
Pick overlay landmark
        ↓
Pick corresponding background landmark
        ↓
Pair complete
        ↓
Repeat once or finish
```

### Long-lived editor state

`OverlayEditorViewModel` owns the composition across configuration changes:

- background and overlay references;
- translation, scale, rotation, and opacity;
- fixed landmark pairs and pending landmark selection;
- fixed layers and multi-layer state;
- display variant and heatmap preferences.

Transient presentation concerns remain local to Compose:

- sheet visibility;
- Snackbar host;
- current loading/saving indicator;
- generated heatmap preview cache;
- temporary auto-align status.

This split prevents a rotation from discarding work without incorrectly placing one-shot UI events in the ViewModel.

### Image pipeline

```text
content URI
   │
   ├─ API 28+: ImageDecoder, software bitmap, sampled decode
   └─ API 24–27: bounds decode → power-of-two sample → EXIF transform
   │
   ▼
editor bitmap, maximum edge 2048 px
   │
   ├─ preview: center crop
   ├─ auto-align: scaled center crop
   ├─ heatmap: scaled center crop
   └─ export: center crop matching preview aspect
          │
          ├─ compose on Dispatchers.Default
          └─ MediaStore write on Dispatchers.IO
```

### Export behavior

- API 29 and newer: creates a pending MediaStore item, writes the JPEG, then publishes it; failures delete the partial item.
- API 24–28: requests the narrowly scoped legacy write permission only when the user saves.
- The save action is disabled while active and reports success or failure through the Snackbar host.

## Accessibility and ergonomic decisions

- Core controls use Material buttons and sliders rather than hand-built pointer-only targets.
- Secondary actions wrap to additional rows instead of moving offscreen horizontally.
- The editor canvas exposes a stage-specific content description and state description.
- Every precision slider has an accessible label and a visible numeric value.
- Hidden vertical swipe effects were removed; every display variant is a named action.
- Landmark mode communicates the exact next tap and renders completed and pending markers distinctly.
- Busy states disable edits that would invalidate the running operation.
- Bottom controls include navigation-bar insets; transient feedback includes status-bar insets.

## Performance and fluidity decisions

- Full image composition and storage writes are removed from the main thread.
- Imported images are sampled to a bounded working resolution.
- CameraX is unbound as soon as live preview leaves composition.
- Heatmap review is reset to Normal when returning to alignment or adding another overlay, preventing expensive heatmap regeneration during every transform gesture.
- The editor no longer stacks independent gesture detectors for translation, transform, and effect switching.
- ViewModel bitmap ownership avoids decode/reload churn on rotation.

## Reliability corrections beyond visual design

1. Cancelling an Android picker no longer clears the existing background or overlay.
2. Changing the background clears incompatible overlay state and generated comparison state.
3. The first fixed overlay remains visible while aligning a second overlay.
4. A third overlay cannot silently replace the supported second layer.
5. Export uses the same crop model seen during editing.
6. Old capture handles are cleared when the preview is removed.
7. Photo-only operation is a first-class mode rather than a permission error state.
8. Permission state is rechecked after returning from Android settings.
9. Landmark matching now produces real source/target constraints.

## Verification performed

```yaml
unit_tests:
  command: testDebugUnitTest
  environment: Docker Android build image with isolated Gradle cache
  result: passing

compile:
  kotlin_debug: passing
  warnings_from_changed_code: none observed in the final unit-test compile

covered_state_machines:
  - camera access choice and photo-only mode
  - workflow primary actions and panel visibility
  - point-matching phases
  - center-crop geometry
  - decode sample sizing
  - permission presentation behavior
```

The final validation pass also runs Android lint and `assembleDebug`; record their results in the project checkpoint and delivery summary.

## Manual device validation matrix

A physical-device or emulator pass remains necessary because unit tests cannot prove gesture feel, CameraX hardware behavior, OEM permission presentation, or TalkBack traversal.

| Scenario | Expected result |
|---|---|
| First launch, camera path | Explanation appears before system permission; grant opens live preview |
| First launch, photo path | No system permission appears; background picker becomes primary action |
| Camera denied | Retry, photo-only, and settings routes remain available without stacked dialogs |
| Rotate during alignment | Background, overlay, transforms, point pairs, and review state remain intact |
| Small phone / large font | Tool sheet scrolls; actions wrap; primary action remains reachable |
| Gesture edit | One-finger move, two-finger scale/rotate remain stable without effect changes |
| TalkBack | Dock, primary action, sheet controls, and sliders announce meaningful labels |
| Landmark pair | First tap marks overlay source; second tap fixes target; two pairs correct scale/rotation |
| Add second layer | First layer stays visible; third layer action is disabled |
| Background aspect mismatch | Preview and saved composite preserve the same alignment |
| API 24–28 save | Legacy storage request appears only at save; successful image reaches gallery |
| API 29+ save | No storage permission prompt; pending item is published only after a complete write |
| Leave live preview | Camera indicator turns off and CameraX releases the device |

## Remaining non-blocking debt

These items are intentionally not represented as completed:

- A process killed in the background does not reconstruct in-memory bitmaps and transforms; configuration changes are preserved, full process-death restoration is not yet implemented.
- User-facing strings are still largely embedded in Kotlin and should move to localized string resources before localization work.
- Automated Compose semantics tests and Macrobenchmark/Baseline Profile coverage are not yet present.
- The two-layer limit is now explicit and safe, but a future arbitrary-layer model should use a list with stable IDs and a dedicated layer manager.
- A physical-device pass is still required before making a release-quality claim about hardware camera behavior and gesture feel.

## Release recommendation

The redesigned application is suitable for a debug/user-acceptance build once lint and APK assembly pass. It should not be described as empirically flawless until the manual device matrix—especially large-font, TalkBack, low-memory, camera lifecycle, and API 24–28 export cases—has been executed on representative devices.
