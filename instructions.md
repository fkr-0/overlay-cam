# Overlayer-Cam

a lean, dockerized, “code-on-host, build-in-container, install-via-adb” Android setup plus a minimal Kotlin/Compose + CameraX app that overlays a gallery image over a live camera preview with move/scale/rotate + opacity.

---

# Overview

* **Idiomatic stack:** Kotlin + Jetpack Compose + CameraX + Android Photo Picker (fallback to SAF on older SDKs), all built with **Gradle** inside a **Docker** container (Android cmdline-tools + SDK + build-tools).
* **Dev loop:** edit on host → `make build` (in docker) → `make install` (host ADB) → test.
* **Features implemented now:**

  * Camera preview as **background**
  * Pick image from gallery (Android 13+ Photo Picker; fallback ACTION\_OPEN\_DOCUMENT)
  * **Drag / pinch-to-zoom / rotate** the overlay image
  * **Opacity slider**
* **Extensible:** easy to add crop, bounded transforms, or perspective transforms later.

---

# Project layout (host filesystem)

```
overlay-cam/
├─ Dockerfile
├─ Makefile
├─ scripts/
│  ├─ devshell.sh
│  └─ gradle-in-docker.sh
├─ settings.gradle.kts
├─ build.gradle.kts
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/example/overlaycam/
      │  ├─ MainActivity.kt
      │  ├─ CameraPreview.kt
      │  ├─ ImageOverlay.kt
      │  └─ Permissions.kt
      └─ res/values/strings.xml
```

ASCII sketch of the UI:

```
+------------------------------------------------------+
| [TopAppBar: "Overlay Cam"]     [Pick Image] [Reset] |
|                                                      |
|  (CameraX PreviewView; fills screen, behind)         |
|   \                                                 /|
|    \  Overlay Image (transformable w/ gestures)    / |
|     \   - drag / pinch zoom / rotate              /  |
|      \   - opacity via bottom slider             /   |
|       \                                         /    |
|        +---------------------------------------+     |
|  [ Opacity: 0% ----------------------o 100% ]        |
+------------------------------------------------------+
```

---

# Build & run quickstart

```sh
# 1) Build the Docker image with Android SDK/Build-Tools/Platform-Tools
make docker

# 2) Build the APK inside Docker (outputs to app/build/outputs/apk/debug/app-debug.apk)
make build

# 3) Connect a device (USB or emulator), verify:
adb devices

# 4) Install:
make install

# 5) (Optional) launch:
adb shell monkey -p dev.fkr.overlaycam 1
```

ADB is run on the **host**; all Gradle/SDK heavy lifting happens in **Docker**.

---

## Files

```kotlin
// == overlay-cam/Dockerfile ==
# Minimal Android build image w/ OpenJDK 17 + cmdline-tools
FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_SDK_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator:/opt/android-sdk/tools:/opt/android-sdk/tools/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl unzip zip git ca-certificates openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# Install Android cmdline-tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
 && cd /tmp \
 && curl -sSLo cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip \
 && mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
 && unzip -q cmdtools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
 && rm cmdtools.zip

# Pre-accept licenses & install packages (adjust platform/build-tools as needed)
RUN yes | sdkmanager --licenses
RUN sdkmanager "platform-tools" \
               "platforms;android-34" \
               "build-tools;34.0.0" \
               "cmake;3.22.1" \
               "ndk;26.1.10909125" || true

WORKDIR /work
# Gradle runs with cache volumes (see Makefile). No global cache in image.

# Default command (overridden by Makefile scripts)
CMD ["/bin/bash"]
// == end/Dockerfile ==
```

```make
# == overlay-cam/Makefile ==
PROJECT ?= overlay-cam
IMAGE   ?= overlay-cam:android
WORKDIR ?= /work

.PHONY: docker build clean install shell

docker:
	docker build -t $(IMAGE) .

# Gradle build inside Docker; mount project and gradle caches for speed
build:
	docker run --rm \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) \
	  bash -lc "./scripts/gradle-in-docker.sh assembleDebug"

clean:
	docker run --rm \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) \
	  bash -lc "./scripts/gradle-in-docker.sh clean"

shell:
	docker run --rm -it \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) bash

# Install APK via host adb
install:
	adb install -r app/build/outputs/apk/debug/app-debug.apk
# == end/Makefile ==
```

```bash
# == overlay-cam/scripts/gradle-in-docker.sh ==
#!/usr/bin/env bash
set -euo pipefail
# Run Gradle via wrapper; download wrapper if needed (rare)
if [ ! -f "./gradlew" ]; then
  echo "Gradle wrapper missing. Initializing..."
  gradle wrapper --gradle-version 8.9
fi
chmod +x ./gradlew
./gradlew --no-daemon "$@"
# == end/scripts/gradle-in-docker.sh ==
```

```bash
# == overlay-cam/scripts/devshell.sh ==
#!/usr/bin/env bash
# convenience: jump into the docker dev shell
set -euo pipefail
IMAGE="${1:-overlay-cam:android}"
docker run --rm -it \
  -v "$PWD":/work \
  -v "$HOME/.gradle":/.gradle \
  -w /work \
  "$IMAGE" bash
# == end/scripts/devshell.sh ==
```

```kotlin
// == overlay-cam/settings.gradle.kts ==
rootProject.name = "overlay-cam"
include(":app")
// == end/settings.gradle.kts ==
```

```kotlin
// == overlay-cam/build.gradle.kts ==
plugins {
    // Root buildscript placeholder
}

tasks.register("cleanAll") {
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}
// == end/build.gradle.kts ==
```

```kotlin
// == overlay-cam/app/build.gradle.kts ==
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.overlaycam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.overlaycam"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0", "META-INF/LGPL2.1"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.3.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Permissions helpers (optional) — we’ll do minimal manual request; keeping deps lean
    // implementation("com.google.accompanist:accompanist-permissions:...")

    // Coil (image loading) if you want URI decode into ImageBitmap later
    implementation("io.coil-kt:coil-compose:2.7.0")
}
// == end/app/build.gradle.kts ==
```

```xml
<!-- == overlay-cam/app/src/main/AndroidManifest.xml == -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.CAMERA"/>
  <!-- For SDK < 33; for 33+ we use Photo Picker (no permission needed) -->
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                   android:maxSdkVersion="32"/>

  <application
      android:label="@string/app_name"
      android:allowBackup="true"
      android:supportsRtl="true"
      android:theme="@style/Theme.Material3.DayNight.NoActionBar">
    <activity
        android:name=".MainActivity"
        android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>
<!-- == end/AndroidManifest.xml == -->
```

```xml
<!-- == overlay-cam/app/src/main/res/values/strings.xml == -->
<resources>
    <string name="app_name">Overlay Cam</string>
    <string name="pick_image">Pick Image</string>
    <string name="reset">Reset</string>
    <string name="camera_permission_rationale">Camera permission is required.</string>
    <string name="opacity">Opacity</string>
</resources>
<!-- == end/strings.xml == -->
```

```kotlin
// == overlay-cam/app/src/main/java/com/example/overlaycam/MainActivity.kt ==
package com.example.overlaycam

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                OverlayCamApp()
            }
        }
    }
}

@Composable
fun OverlayCamApp() {
    // simple host: camera + overlay composables
    Scaffold(
        topBar = {
            SmallTopAppBar(title = { Text("Overlay Cam") })
        }
    ) { padding ->
        OverlayScreen(Modifier.padding(padding))
    }
}
// == end/MainActivity.kt ==
```

```kotlin
// == overlay-cam/app/src/main/java/com/example/overlaycam/CameraPreview.kt ==
package com.example.overlaycam

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            bindCamera(ctx, previewView)
            previewView
        }
    )
}

private fun bindCamera(context: Context, previewView: PreviewView) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                (context as androidx.lifecycle.LifecycleOwner),
                cameraSelector,
                preview
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}
// == end/CameraPreview.kt ==
```

```kotlin
// == overlay-cam/app/src/main/java/com/example/overlaycam/ImageOverlay.kt ==
package com.example.overlaycam

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.detectDragGestures

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Camera permission gate
    CameraPermissionGate {
        Box(modifier = modifier.fillMaxSize()) {
            // Background: Camera preview
            CameraPreview(Modifier.fillMaxSize())

            // Foreground controls + overlay
            var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
            var opacity by remember { mutableFloatStateOf(0.7f) }

            // Transformation state
            var scale by remember { mutableFloatStateOf(1f) }
            var rotation by remember { mutableFloatStateOf(0f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            val transformState: TransformableState = rememberTransformableState { zoomChange, panChange, rotationChange ->
                scale = (scale * zoomChange).coerceIn(0.2f, 8f)
                rotation += rotationChange
                offsetX += panChange.x
                offsetY += panChange.y
            }

            // Image picker (Photo Picker on 33+, else ACTION_OPEN_DOCUMENT)
            val photoPicker = if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri -> pickedImageUri = uri }
            } else {
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    pickedImageUri = uri
                }
            }

            // Overlay image layer
            pickedImageUri?.let { uri ->
                // Easy path: let Coil render and we transform the composable (fast path)
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = opacity
                            translationX = offsetX
                            translationY = offsetY
                            scaleX = scale
                            scaleY = scale
                            rotationZ = rotation
                        }
                        .transformable(transformState)
                        .pointerInput(Unit) {
                            // Allow single-finger drag as well (transformable handles pinch/rotate)
                            detectDragGestures { change, drag ->
                                change.consumeAllChanges()
                                offsetX += drag.x
                                offsetY += drag.y
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Top-right controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        photoPicker.launch(arrayOf("image/*"))
                    }
                }) {
                    Text(text = "Pick Image")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scale = 1f; rotation = 0f; offsetX = 0f; offsetY = 0f; opacity = 0.7f
                }) {
                    Text("Reset")
                }
            }

            // Bottom opacity slider
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Opacity")
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it.coerceIn(0f, 1f) },
                    valueRange = 0f..1f
                )
            }
        }
    }
}
// == end/ImageOverlay.kt ==
```

```kotlin
// == overlay-cam/app/src/main/java/com/example/overlaycam/Permissions.kt ==
package com.example.overlaycam

import android.Manifest
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val permission = Manifest.permission.CAMERA
    var granted by rememberSaveable { mutableStateOf(false) }
    var shouldExplain by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (!isGranted) shouldExplain = true
    }

    LaunchedEffect(Unit) {
        // Kick off request on first launch
        launcher.launch(permission)
    }

    if (granted) {
        content()
    } else {
        PermissionDialog(
            onRequest = { launcher.launch(permission) }
        )
    }
}

@Composable
private fun PermissionDialog(onRequest: () -> Unit) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("Grant") }
        }
    }
}
// == end/Permissions.kt ==
```

---

## Why this is “idiomatic, low overhead”

* **Compose + CameraX** is the current, stable Android path. No legacy Views aside from the CameraX `PreviewView` interop, which is the recommended route.
* **Photo Picker** for SDK 33+ avoids READ\_MEDIA\_IMAGES permission; we still support older devices via `OpenDocument`.
* **Dockerized SDK** removes host bloat: you only need Docker + ADB on host.
* **Makefile targets** give a clean flow (`make build` / `make install`). Gradle cache is volume-mounted for speed.

---

## Notes & Extensions

* **Cropping:** add a clipping rect around the overlay using `Modifier.clip(RectangleShape)` after mapping transforms; or render to an offscreen `Canvas` and crop the `ImageBitmap`.
* **Bounds & inertia:** use `rememberScrollableState` / physics-based fling for nicer UX; clamp translation to viewport.
* **Persistence:** store last transform in `DataStore` so it survives process death.
* **Transforms (perspective):** for true perspective warp, you’ll need a custom `DrawScope` + matrix math or an OpenGL/CameraX + GLSurfaceView pipeline. For most “compare by overlay” use-cases, affine transforms (translate/scale/rotate) are enough.

---

## Troubleshooting

* **`adb devices` empty:** enable USB debugging, check cable, `adb kill-server && adb start-server`.
* **Build-tools mismatch:** update the `build-tools;34.0.0` and `platforms;android-34` in Dockerfile & `compileSdk`.
* **Mac (M-series) + emulators:** this setup is device-agnostic; you can still `adb` to a physical device. Emulators require host virtualization; they’re not provided by this Docker image.

---
