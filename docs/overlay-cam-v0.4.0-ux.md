# Overlay Cam v0.4.0 — camera-first inspection and alignment

Version: **0.4.0** (Android `versionCode=5`), a minor release following 0.3.0.
This is a **debug distribution**, not a production-signed Play Store release.

## Product workflow

1. **Live**: full-screen rear-camera preview. Toggle the rule-of-thirds grid
   for framing. Hold the phone to align the real scene; tap **Freeze**.
   An existing image can be selected as the background instead.
2. **Frozen scene**: the frozen bitmap occupies the canvas, with no dialog
   covering the shot. **Add image** selects a transparent/reference overlay.
3. **Align**: one finger translates the overlay; two fingers resize and rotate.
   The bottom dock has a **Blend** fader, independent **Frozen View 0.5–20×**
   fader, and a compact **Size** action opening an **Overlay 0.5–2×**
   fader in-place. Size changes the export; View never changes export geometry.
   Manual X/Y/Z value/step faders remain available in advanced Tools.
4. **Set / Inspect**: lock placement, then pan the composite with one finger
   and zoom the composite with two fingers. The right-hand Reposition button
   returns to editing the overlay. Tapping the canvas does not reopen a dialog.
5. **Recent**: app-private archived camera JPEGs and gallery URI pairs appear
   as small background and overlay thumbnails. A committed Set also stores
   the overlay's X/Y position, scale, rotation and blend in the recent
   record, and reopening such a record returns to locked inspection mode.
   Reopening a non-committed pair returns to editable placement.
   Both files are decoded before replacing the active composition. If either
   file disappears or permissions expire, the current image is preserved.
6. **Back / New**: one-stage Back and confirmed New Frame, without force
   terminating the app or deleting recent records.
7. **Hide**: hide routine controls for image inspection; the small **Controls**
   recovery button remains visible. Advanced Tools remains optional and
   scrollable, capped to roughly half the screen.

## Stability changes

CameraX now uses a lifecycle-owned Preview use case. Late callbacks after
disposal cannot bind the previous camera, and disposal only unbinds its own
Preview, rather than unbinding every CameraX use case. COMPATIBLE PreviewView
uses a TextureView to improve frame capture reliability at a performance cost.
This needs **physical-device verification**, especially rapid Freeze→New,
repeated rotation/recreation, permissions and low-memory conditions.

Recent-scene reopening is transactional: both URI decodes and request
generation checks finish before state is replaced. User-cancelled or failed
restores cannot trash the current editor bitmap. A newer capture or background
selection invalidates any in-flight recent restore.

A locked overlay's transform is now saved with the history pair. This is a
single-overlay snapshot; it does **not** recreate multiple locked layers,
landmark points, streaming effects or a full project file. The original
bitmaps/URIs are retained only for items still accessible to the app.

## Regression guarantees and remaining limitations

- Do not erase/replace the September OBS/streaming dirty work.
- The installed older APK may have shown different controls; source inspection
  did not prove the zoom capability was deleted.
- Overlay Scale 0.5–2× and inspection View 0.5–20× must remain distinct.
- Saved composite does not bake in inspection zoom/pan.
- Recent gallery read permissions may expire for external provider URIs;
  errors must be non-destructive.
- Camera preview snapshots are not a high-resolution ImageCapture pipeline.
- An optical perspective-correction/homography tool is **not** implemented.
- Android device QA and separate independent code review are still required.

## Verification checklist

- `make test` and `make build` in project Docker image; capture exact exit.
- Check APK zip integrity, SHA-256 and package version / debug signing.
- Test grid and framing, Freeze/New repetition, background photo fallback,
  overlay drag/pinch/rotate, size slider, opacity slider, 0.5× and 20× View,
  Save alignment, Hide/Restore chrome, Recent pair restore including committed
  position, revoked URI survival, Back, and narrow display/TalkBack.
- No release/tag/publish from this task. Deliver APK as tgtext document.
