# AGENTS.md - Overlay Cam Project

## Overview

**Overlay Cam** is a minimalist Android application that overlays a gallery image over a live camera preview with gesture-based transformations (drag, pinch-to-zoom, rotate) and opacity control. The project follows a "code-on-host, build-in-container, install-via-adb" development pattern.

**Tech Stack:**
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material3
- **Camera:** CameraX (Preview, ImageCapture)
- **Image Loading:** Coil
- **Build System:** Gradle (Kotlin DSL)
- **Containerization:** Docker (Android cmdline-tools + SDK + build-tools)
- **Platform:** Android API 24-34 (minSdk 24, targetSdk 34)

---

## Project Structure

```
overlay-cam/
├── app/
│   ├── build.gradle.kts           # App-level Gradle config
│   └── src/main/
│       ├── AndroidManifest.xml    # Permissions, activity declaration
│       ├── java/com/example/overlaycam/
│       │   ├── MainActivity.kt       # Activity entry point
│       │   ├── CameraPreview.kt      # CameraX Preview + ImageCapture binding
│       │   ├── ImageOverlay.kt       # Main UI composable (overlay, crop, save)
│       │   └── Permissions.kt        # Camera permission handling
│       └── res/
│           └── values/
│               ├── strings.xml        # UI strings
│               └── themes.xml         # Material3 theme
├── scripts/
│   ├── gradle-in-docker.sh         # Gradle wrapper script for container
│   └── devshell.sh                 # Interactive dev shell
├── Dockerfile                      # Android build environment
├── docker-compose.yml              # Compose convenience
├── Makefile                        # Build commands (docker, build, install)
├── build.gradle.kts                # Root Gradle config
└── settings.gradle.kts             # Gradle settings
```

---

## Core Components

### MainActivity.kt
- Entry point `ComponentActivity`
- Sets up `MaterialTheme` and renders `OverlayCamApp()`
- Enables edge-to-edge display

### CameraPreview.kt
- Composable that wraps CameraX `PreviewView` via `AndroidView`
- Binds `ProcessCameraProvider` with lifecycle awareness
- Exposes `CameraHandle` data class containing:
  - `imageCapture: ImageCapture` - for taking photos
  - `previewView: PreviewView` - for freezing frames
- Supports pause/resume via `isActive` parameter

### ImageOverlay.kt
- Main screen composable containing all UI logic:
  - Camera permission gate
  - Live/frozen camera background
  - Canvas-based overlay with real clipping
  - Transform state (scale, rotation, offset)
  - Crop mode with draggable rectangle
  - Image picker (Photo Picker SDK 33+, else OpenDocument)
  - Save to MediaStore (with composite transform mapping)

### Permissions.kt
- `CameraPermissionGate` composable
- Requests CAMERA permission on launch
- Shows permission dialog if denied

---

## Build & Development Workflow

### Quick Start
```bash
# Build Docker image with Android SDK
make docker

# Build APK inside container
make build

# Install to connected device
make install

# Interactive dev shell
make shell
```

### Docker Compose (alternative)
```bash
docker compose up --build --abort-on-container-exit
```

### CI/CD
- GitHub Actions workflow builds APK and uploads as artifact
- Located at `.github/workflows/android.yml`

---

## Key Features

1. **Live Camera Preview** - CameraX PreviewView filling the screen
2. **Image Overlay** - Pick from gallery, overlay on camera with:
   - Drag (single finger)
   - Pinch-to-zoom
   - Rotation gesture
   - Opacity slider
3. **Crop Mode** - Draggable/resizable crop rectangle with corner handles
4. **Pause/Resume** - Freeze current frame to work with static background
5. **Save Composite** - Capture photo + overlay with transforms applied:
   - Maps preview-space transforms to camera bitmap space
   - Respects crop rectangle when enabled
   - Saves to MediaStore Pictures/OverlayCam

---

## Architecture Notes for Agents

### Transform System
- **Center-anchored transforms**: Overlay is centered on screen, then transformed
- **Coordinate spaces**: Preview (screen) coords vs Camera (output) bitmap coords
- **Matrix composition**: Translate center → Apply user transforms → Scale to output

### Canvas vs Image Composable
- The overlay uses `Canvas` with `clipRect` for true live clipping
- Both `Bitmap` (for saving) and `ImageBitmap` (for Canvas) are maintained
- Transform state is shared between display and save logic

### State Management
- All state is local composable state (`remember`, `mutableStateOf`)
- No ViewModels or additional architecture layers (minimal approach)
- LaunchedEffect for side effects (image decoding, permission requests)

### Dependencies
- Compose BOM 2024.08.00
- CameraX 1.3.4
- Coil 2.7.0
- Material3 1.3.0

---

## Common Tasks

### Adding a New Transform
1. Add state variable in `ImageOverlay.kt`
2. Update `transformState` lambda
3. Apply in Canvas draw block
4. Update `composeAndSave()` matrix composition

### Modifying Crop Behavior
- Crop rectangle is in preview/screen coordinates
- Canvas clipping: `clipRect(clip.left, clip.top, clip.right, clip.bottom)`
- Save compositing: scales crop rect by `sx, sy` to camera bitmap space

### Testing Camera Changes
- Use `make shell` for interactive Docker container
- Build with `./gradlew assembleDebug`
- Install via `adb` on host

---

## Permissions

- `CAMERA` - Required for camera preview
- `READ_EXTERNAL_STORAGE` (maxSdkVersion 32) - Legacy for SDK < 33
- Photo Picker (SDK 33+) requires no storage permission

---

## Output Locations

- **APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Saved Images:** `Pictures/OverlayCam/` on device (via MediaStore)
