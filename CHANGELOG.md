# Changelog

All notable changes to Overlay Cam are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
