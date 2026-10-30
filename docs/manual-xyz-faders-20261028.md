# Overlay Cam — Manual XYZ faders and frozen View zoom (2026-10-28)

Canonical OCP task: `OVERLAY-CAM-XYZ-FADERS-20261028`.

## Reference frames and ownership

- **X / Y** manipulate the **working image overlay**, as horizontal and vertical offsets measured in **preview-screen pixels**. Positive X is right; positive Y is down. The axes remain in screen space when the overlay rotates.
- **Z** manipulates the **working overlay's uniform scale**, a unitless multiplier (0.5×–2.0×), **not** 3D depth.
- **Frozen View** zooms the **inspection viewport** (background plus the visible overlays) from **0.5× to 20×**. This is not an edit to the stored bitmap or the composited export. **Reset View** restores exactly 1× and zero viewport pan. The range uses a logarithmic slider position, so movements around 1× remain practical and continuous.
- X/Y are bounded to two preview extents on their respective axes (minimum extent 256px, maximum offset magnitude 32768px); Z is bounded to 0.5×–2×. These are intentionally consistent with the existing working overlay adjustment bounds.
- A separate overlay reset returns X/Y to zero and Z to 1×. Adjusting one axis does not alter the other two or the overlay rotation.
- Pinch/touch gestures and faders share the editor state; neither should keep its own stale *committed* coordinate copy. In preview exports, frozen viewport pan and zoom are intentionally not baked in. The overlay transformation (X/Y/Z plus rotation) is shared between preview and export.

## Two fader behaviors

| Mode | Interaction | Release | Cancellation |
|---|---|---|---|
| Value (mod wheel) | Drag to an absolute X/Y/Z value; visible transform updates while dragging | Value retained | Restore pre-drag value |
| Step (pitch wheel) | Spring-centered wheel shows a signed preview delta; no committed transform change while dragging | Apply signed delta **once** to the latest committed value, then center at zero | No transform mutation; thumb returns to center |

Step range: X ±80 preview px, Y ±80 preview px, Z ±0.25× for full deflection. Center is always zero delta. An inactive/repeated release is a no-op. Returning to center *before* release commits a zero change. Switching mode mid-drag cancels the current gesture. A11y set-progress actions are atomic begin/move/release operations. Reset is separate and explicit.

The panel is accessible from the editor Tools → **Manual adjust XYZ**; the bottom **View** fader is explicitly a frozen-scene zoom control. The Tools sheet is vertically scrollable and the tracks are at least 44dp tall to support smaller phones. Content descriptions carry the target layer, selected mode and current numeric values.

## Verification

- `make test` — host Docker Android toolchain, JVM model and gesture tests.
- `make build` — host Docker Android toolchain, APK compile.
- Unit tests cover all three axes, Value and Step, repeated release, canceled drag, clipping, mode switching, reset, axis isolation and screen-axis rotation semantics.
- Manual physical-device acceptance: small screen / font scaling, drag-cancel by app backgrounding, 0.5× and 20× endpoint accessibility, 1× reset, gestures while switching edit/inspect modes, current preview and saved export alignment.

## Concurrency / rollout

`ImageOverlay.kt` and `OverlayEditorViewModel.kt` were owned by the concurrent recent-session/restart lane at implementation start. Once the recent/restart lease expired and its packet returned to ready, ws-bridge granted the XYZ lane its own exact packet and claim. The XYZ integration changes **only** the editor's `ImageOverlay.kt` call site: it wires the model directly to ViewModel-owned `offsetX`, `offsetY`, and `scaleF` fields already used by preview and export; no change to `OverlayEditorViewModel.kt` was needed. The pre-existing September streaming/OBS changes remain untouched. Do not tag or publish from this lane.
