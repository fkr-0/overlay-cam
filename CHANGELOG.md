# Changelog

All notable changes to Overlay Cam are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-10-30

### Added

- **Streaming effects** composited in-app and shared by the preview, saved images, and stream output: chroma key (green/blue/magenta, tolerance, edge softness), picture-in-picture, image/logo overlay, and an animated text overlay (built-in or custom font), plus named effect presets.
- **OBS-ready MJPEG output** that serves the composited frame on a user-chosen port, and an **OBS WebSocket v5** client (authenticated) to switch scenes and start/stop the stream and virtual camera.
- **Manual X / Y / Z faders** (value and spring-centered step modes) for precise overlay offset and scale, and an independent **Frozen View** zoom fader (0.5×–20×) that never alters exported geometry.
- **Recent frozen scenes** on the top rail. Locked placements are stored with the scene and reopen in inspection mode; reopening is transactional, so a failed restore never replaces the current image.
- **Start-new-frame confirmation** before discarding an alignment, and a one-stage **Back** action.
- **Hide / Controls** toggle that clears routine chrome for unobstructed inspection.
- Light haptic tick when switching between *Position unlocked* and *Position frozen*.
- Unit tests for the manual faders, session restart, recent sessions, streaming effects, and OBS authentication.

### Changed

- The advanced Tools sheet is capped to roughly half of the screen height and scrolls, instead of covering most of the screen.
- Streaming panel is accessibility-first: whole-row switch targets (≥48 dp), TalkBack labels and values on every slider, section headings, and live-region status messages.
- OBS controls reflect connection state: Connect is disabled until a host is entered, and scene/stream/virtual-camera actions are enabled only while connected. A rejected OBS request no longer drops a live session out of the connected state.
- Input fields use the right keyboards (numeric ports, URI host, password) with autocorrect disabled for host and password; *Save preset* requires a name.
- Streaming, top-rail, recent-scene and restart-dialog text moved to Android string resources to enable localization. Other editor strings still need migration.
- Recent-scene thumbnails are decorative so TalkBack reads each row once instead of announcing duplicate image labels.
- Camera grid toggle exposes its on/off state to assistive technology.
- Version numbering is back on the 0.x SemVer line (`versionName` `0.4.0`, `versionCode` 5). Intermediate unreleased builds labelled `4.1.1` / `411` are superseded.

### Security & privacy

- The app now declares `INTERNET` and allows cleartext traffic so it can reach an OBS host on the local network over `ws://`. Use the secure-connection (WSS) switch when OBS is configured for TLS.
- While MJPEG output is enabled, **anyone on the same network who knows the address can view the stream** (no authentication). The panel warns about this; turn output off when not streaming.
- OBS credentials are held in memory only and are not saved with presets.

### Verification

- Docker-backed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- All 62 unit tests pass.
- The generated APK reports package `com.example.overlaycam`, version code `5`, and version name `0.4.0`.
- Physical-device validation remains recommended for multi-touch feel, TalkBack traversal of the new streaming panel, large font scales, and OBS interoperability.

## [0.3.0] - 2026-08-01

### Added

- A persistent comparison workspace that continuously blends from the drawing/background to the selected reference or analysis guide.
- Three drawing-assistance guides available directly from the bottom control surface:
  - **Similarity** marks close areas in green and increasingly different areas in yellow and red.
  - **Value difference** emphasizes the largest lightness and color errors while keeping close areas visually quiet.
  - **Contours** distinguishes aligned, missing, and extra detected edges with separate labels and colors.
- Live accuracy, average-similarity, and contour-overlap metrics with practical drawing instructions.
- A floating **Position unlocked / Position frozen** control that switches between alignment and inspection without opening a sheet.
- Independent inspection viewport state with one-finger panning, centroid-preserving two-finger zoom, and a reset-view action.
- Responsive narrow-screen action placement and tests for guide sizing, operation selection, viewport zoom, and contour analysis.

### Changed

- Replaced the standalone opacity control with a continuous **Drawing ↔ comparison** fader that controls overlay or guide strength.
- Reposition mode keeps one-finger movement and two-finger resize/rotation, while frozen mode reserves one finger for panning and two fingers for zooming.
- Combined edit/review actions, tools, filters, metrics, and blend control into one adaptive bottom surface to preserve canvas space.
- Promoted comparison filters from the advanced post-edit sheet into the main editing and review workflow.
- Debounced live guidance generation and limited its longest edge to 720 pixels; exported guides use a separate 1440-pixel analysis budget before high-quality scaling.
- Renamed the primary alignment action to **Use this position** and kept repositioning available from the floating control after review.
- Increased Android `versionCode` from 3 to 4 and `versionName` from `0.2.0` to `0.3.0`.

### Fixed

- Prevented visible jumps when changing between one- and two-finger operations by establishing a fresh gesture baseline.
- Recycled temporary comparison bitmaps on cancellation, failure, and successful ownership transfer.
- Kept guide pixels transparent outside the reference image instead of obscuring unrelated drawing areas.
- Capped exported guide analysis to avoid excessive memory use on high-resolution camera frames.

### Verification

- Docker-backed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- All 36 unit tests pass; Android lint reports 0 errors and 11 non-blocking dependency-update warnings.
- The generated APK reports package `com.example.overlaycam`, version code `4`, and version name `0.3.0`.
- Physical-device validation remains recommended for multi-touch feel, compact landscape layout, large fonts, and guide legibility over varied artwork.

## [0.2.0] - 2026-08-01

### Added

- Live gesture-operation feedback while touching the overlay: a labeled status badge and a mode-specific canvas border distinguish moving from resizing/rotating.
- A permanently visible opacity fader at the physical bottom of the overlay editing screen.
- Unit coverage for the pointer-count-to-operation contract.

### Changed

- One active finger now exclusively moves the overlay.
- Two or more active fingers now exclusively resize and rotate the overlay, avoiding accidental translation during a pinch or twist.
- Gesture-mode transitions establish a fresh baseline so adding or lifting the second finger does not cause a visible jump.
- Removed the duplicate opacity slider from the fine-tuning sheet; opacity is always directly available during overlay editing.
- Increased Android `versionCode` from 2 to 3 and `versionName` from `0.1.1` to `0.2.0`.

### Verification

- Docker-backed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- All 32 unit tests pass; Android lint reports 0 errors and 11 non-blocking dependency-update warnings.
- The generated APK reports package `com.example.overlaycam`, version code `3`, and version name `0.2.0`.
- Physical-device multi-touch feel and compact-height layout validation remains recommended.

## [0.1.1] - 2026-08-01

### Added

- A guided five-stage editor workflow with one compact action dock and one exclusive tools sheet.
- Photo-only onboarding so camera permission is optional, including a camera-less-device path.
- Sampled image decoding with legacy EXIF orientation correction to reduce memory pressure.
- Auto-alignment, point matching, two overlay layers, comparison variants, and heatmap guidance.
- A launcher icon, adaptive tablet-width controls, explicit selected-state chips, and expanded unit coverage.

### Changed

- Reduced imported image working size to a 2048-pixel maximum edge.
- Moved expensive matching, comparison, composition, and file output away from the main thread.
- Made numeric UI labels locale-aware and simplified the collapsed opacity control.
- Increased Android `versionCode` from 1 to 2 and normalized `versionName` from `0.1` to `0.1.1`.

### Fixed

- Recycled temporary auto-alignment and heatmap bitmaps on success, cancellation, early return, and failure.
- Preserved coroutine cancellation during image export instead of converting cancellation into a save error.
- Prevented malformed or inaccessible image providers from crashing image import.
- Rejected point-match landmarks outside the visible overlay instead of clamping them to an image edge.
- Avoided offering a nonfunctional camera action on devices without camera hardware.
- Cleared stale camera handles after switching to a still background and made the tools sheet dismiss predictably with Back.

### Verification

- Docker-backed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- The generated debug APK reports package `com.example.overlaycam`, version code `2`, and version name `0.1.1`.
- Physical-device camera, gesture, TalkBack, and OEM picker validation remains recommended.
