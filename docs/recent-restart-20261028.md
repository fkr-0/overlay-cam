# Overlay Cam — intended experience, regression review and recent/restart

## Product purpose (the primary workflow, not OBS/streaming)

Overlay Cam is a **camera-assisted image alignment and inspection tool**, not a
general-purpose editor or a streaming control panel. The main deliverable is an
accurate frozen camera view aligned to one or more semitransparent reference
images, with enough full-screen space to examine correspondence.

1. **Live scene**: The rear camera owns the canvas, with an optional
   rule-of-thirds alignment grid. Move the phone until perspective/framing
   matches the object or drawing, then tap **Freeze**. The photo picker remains
   an alternative background source. A grid is a *visual aid*, not an automatic
   perspective correction or homography.
2. **Frozen background**: The camera frame stays still. **Add image** opens the
   system picker. The user's frozen scene must not disappear just because the
   overlay image is loading.
3. **Align reference**: A translucent overlay appears over the frozen scene.
   One finger moves the **overlay**; two fingers simultaneously scale and
   rotate it. The Blend fader remains available directly on the bottom dock.
   A separate explicit **View** zoom (0.5–20×) enlarges the inspection
   viewport without changing the overlay transform or exported image. A
   floating **Freeze position / Reposition** toggle switches gesture targets:
   in inspection mode, one finger pans the composite, two fingers zoom it.
4. **Inspect**: Hide all chrome to maximize the picture; a small persistent
   Controls affordance restores it. A compact bottom dock hosts the frequent
   faders; the advanced Tools sheet is optional and never the primary workflow.
   Save exports the frozen background with its overlay transform (not the
   inspection View zoom).
5. **Revisit / retry**: **Back** goes one editing stage backwards; **New**
   starts a new live capture without force-closing the app. Destructive reset
   is confirmed if a composition is in progress. **Recent** lists paired
   frozen backgrounds and overlays in recency order with a clear current
   marker, and permits reopening old work. URI failures are nonfatal.

## Regression audit baseline (2026-10-29)

The checked-out main HEAD is `de7a9f5` (2026-09-30); today's work remains
uncommitted. The September streaming/OBS files must NOT be reset or replaced.
The 2026-10-28 XYZ task is independently approved and its current source
contains the 0.5–20× Frozen View fader and the manual X/Y/Z controls. A prior
APK or an unsuitable editing mode can nevertheless hide these controls.
The native recent/restart lane had reverted to ready without a submitted
implementation; it must not be treated as completed.

Identified UX gaps before this patch: no live alignment grid; no recent
capture/overlay pair browser; no in-app new-session action; full-height (90%)
Tools sheet mixes routine alignment with secondary streaming features;
failure to distinguish overlay zoom and inspection zoom can look like a
regression. Automatic optical perspective correction is not implemented.

## Interaction invariants

- Controls are hidden deliberately; never silently obscure most of the view.
- Camera freeze is immutable for the current editing session until recapture.
- Reopening a recent image never drops the existing scene after a failed URI
  decode; loading errors return to an operable editor.
- Returning to live camera is separate from toggling reposition mode.
- Never apply viewport pan/zoom to the exported image.
- Never delete/reset unrelated September OBS/streaming changes.
- A debug-signed APK is NOT a production release-signed APK.

## Verification / limits

`make test` and `make build` execute in Docker per AGENTS.md.
CameraX rear-camera behavior, display cutouts, physical two-finger motion,
Android process-death history recovery, and actual tgtext attachment delivery
require external device/integration verification.
