# Streaming effects and OBS integration

Overlay Cam 0.4 adds a lightweight streaming-effects layer on top of the existing camera/background compositor.

## Effects

The tools sheet includes:

- **Chroma key** for PiP and logo media, with selectable green/blue/magenta key colors, tolerance, and edge softness.
- **Picture in picture** with independent position, size, opacity, and rounded corners.
- **Image/logo overlay** with position, size, and opacity controls.
- **Text overlay** with built-in sans/serif/monospace/cursive faces, an OpenDocument custom-font picker, placement, size, opacity, and pulse/slide animations.
- **Effect presets** persisted in app preferences. Presets retain effect configuration and persisted content URIs, but deliberately do not retain OBS credentials and do not auto-start network output.

The main preview, saved composites, and MJPEG output use the same effect state.

## OBS WebSocket v5

OBS control uses the native OBS WebSocket v5 protocol over ws:// or wss://. The integration supports:

- authenticated connection using the OBS challenge/salt handshake;
- switching the current program scene;
- starting and stopping OBS streaming;
- starting and stopping the OBS virtual camera.

OBS WebSocket normally listens on port 4455. Enter the OBS host, port, password, and optional scene name in the Overlay Cam tools sheet.

The password is kept only in the running UI state and is never written to an effect preset.

## Streaming-ready output

Overlay Cam serves a multipart MJPEG feed over HTTP:

    http://<device-ip>:8088/stream.mjpeg

Port 8088 is the default and can be changed in the tools sheet. The output is intentionally dependency-light and open: OBS can consume it as a network/browser-compatible source without bundling a proprietary NDI SDK, while OBS itself can expose the resulting scene as a platform virtual camera.

The stream defaults to:

- up to 30 fps;
- 1280 px maximum frame edge;
- JPEG quality 82.

These limits bound CPU, memory bandwidth, and Wi-Fi traffic. Chroma-keyed media is recomputed only when its source or key settings change rather than once per video frame.

## OBS setup

1. Enable OBS WebSocket in OBS and note the host/password.
2. In Overlay Cam, open **More actions**, configure the effects, and enable **Serve composited stream**.
3. Copy the shown MJPEG URL into an OBS source capable of consuming an HTTP MJPEG feed. If the OBS browser source on the target platform does not display a bare multipart URL, use a minimal browser page containing an img element whose source is that URL, or use a media/network source that supports MJPEG.
4. Connect Overlay Cam to OBS WebSocket when scene/stream/virtual-camera control is desired.
5. Start OBS virtual camera from Overlay Cam or OBS if another application needs a camera device.

Android does not provide a portable public API for an ordinary application to register itself as a system virtual camera. Routing through OBS therefore keeps the phone-side implementation cross-platform and avoids device/vendor-specific camera-provider hacks.

## Performance and validation

The implementation is designed around a 30 fps target. The preview uses the existing Compose/Canvas path; network frames are JPEG encoded off the main dispatcher; effect images are decoded and chroma keyed off the main dispatcher. Actual sustained frame rate depends on device camera resolution, SoC, thermal state, and network conditions, so physical-device validation remains required for a measured 30+ fps claim.
