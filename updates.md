# UPDATES

* `docker-compose.yml` with named Gradle cache & one-shot build container
* **GitHub Actions** CI that builds the APK using your Dockerfile and uploads it as an artifact
* **Cropping UI** (draggable/resizeable rectangle guide) + **Save Composite** action

  * saves a composited image (camera frame + transformed overlay) to **MediaStore/Pictures**
  * cropping is **honored in the saved output**; on-screen we show a precise crop guide (the live preview remains un-clipped for performance simplicity)

---

## What’s included

* Compose UI additions: **Crop mode** toggle, draggable **crop handles**, **Save** button
* CameraX: add **ImageCapture** to take a still image for compositing
* Compositor: maps preview-space transforms to camera output size, applies **clipRect** and draws overlay bitmap with **translation/scale/rotation/opacity** onto camera frame, then writes to **MediaStore**

---

## Files / patches

```yaml
# == overlay-cam/docker-compose.yml ==
version: "3.9"

services:
  build:
    build:
      context: .
      dockerfile: Dockerfile
    image: overlay-cam:android
    working_dir: /work
    volumes:
      - ./:/work
      - gradle-cache:/.gradle
    command: bash -lc "./scripts/gradle-in-docker.sh assembleDebug"

volumes:
  gradle-cache:
# == end/docker-compose.yml ==
```

```yaml
# == overlay-cam/.github/workflows/android.yml ==
name: android-apk

on:
  push:
    branches: [ main, master ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-22.04

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Build Docker image
        run: docker build -t overlay-cam:android .

      - name: Build APK in Docker
        run: |
          docker run --rm \
            -v "$PWD":/work \
            -v "$HOME/.gradle":/.gradle \
            -w /work \
            overlay-cam:android \
            bash -lc "./scripts/gradle-in-docker.sh assembleDebug"

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
# == end/.github/workflows/android.yml ==
```

```make
// == overlay-cam/Makefile ==
PROJECT ?= overlay-cam
IMAGE   ?= overlay-cam:android
WORKDIR ?= /work

.PHONY: docker build clean install shell up ci

docker:
	docker build -t $(IMAGE) .

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

install:
	adb install -r app/build/outputs/apk/debug/app-debug.apk

# Compose: convenient cache + one-shot build
up:
	docker compose up --build --abort-on-container-exit

# Simulate CI locally
ci: docker build
# == end/Makefile ==
```

```kotlin
// == overlay-cam/app/src/main/java/com/example/overlaycam/CameraPreview.kt ==
package com.example.overlaycam

import android.content.Context
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

data class CameraHandle(val imageCapture: ImageCapture)

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    bindHandle: (CameraHandle) -> Unit = {}
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            bindCamera(ctx, previewView, bindHandle)
            previewView
        }
    )
}

private fun bindCamera(
    context: Context,
    previewView: PreviewView,
    bindHandle: (CameraHandle) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                (context as androidx.lifecycle.LifecycleOwner),
                cameraSelector,
                preview,
                imageCapture
            )
            bindHandle(CameraHandle(imageCapture))
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

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.detectDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    CameraPermissionGate {
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { previewSize = it.size }
        ) {
            // Background camera
            CameraPreview(
                Modifier.fillMaxSize(),
                bindHandle = { handle = it }
            )

            // Overlay states
            var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
            var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var opacity by remember { mutableFloatStateOf(0.7f) }

            // Transform state (screen/pixel space of preview)
            var scale by remember { mutableFloatStateOf(1f) }
            var rotation by remember { mutableFloatStateOf(0f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            val transformState: TransformableState = rememberTransformableState { zoom, pan, rot ->
                scale = (scale * zoom).coerceIn(0.2f, 8f)
                rotation += rot
                offsetX += pan.x
                offsetY += pan.y
            }

            // Crop UI state (in preview/screen coordinates)
            var cropMode by remember { mutableStateOf(false) }
            var cropRect by remember {
                mutableStateOf(android.graphics.RectF(100f, 100f, 600f, 900f))
            }
            val handleRadius = 16.dp.value

            // Picker
            val picker = if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    pickedImageUri = uri
                }
            } else {
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    pickedImageUri = uri
                }
            }

            // Decode overlay bitmap when selected
            LaunchedEffect(pickedImageUri) {
                overlayBitmap = withContext(Dispatchers.IO) {
                    pickedImageUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                }
            }

            // Visual overlay (fast path via Image + graphicsLayer)
            pickedImageUri?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context).data(uri).build()
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
                            detectDragGestures { change, drag ->
                                change.consumeAllChanges()
                                offsetX += drag.x
                                offsetY += drag.y
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Crop overlay guide + handles
            if (cropMode) {
                // Dim outside the crop rect
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color(0f, 0f, 0f, 0.35f)
                    )
                    val clip = android.graphics.Path().apply {
                        addRect(cropRect, android.graphics.Path.Direction.CW)
                    }
                    drawIntoCanvas { c ->
                        c.nativeCanvas.save()
                        c.nativeCanvas.clipPath(clip)
                        c.nativeCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        c.nativeCanvas.restore()
                    }
                }

                // Crop rectangle border & draggable handles
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(cropRect) {
                            detectDragGestures { change, drag ->
                                change.consumeAllChanges()
                                // Drag whole rect when drag starts inside
                                if (cropRect.contains(change.position.x, change.position.y)) {
                                    cropRect.offset(drag.x, drag.y)
                                }
                            }
                        }
                ) {
                    // Border
                    Box(
                        Modifier
                            .offset(x = cropRect.left.dp, y = cropRect.top.dp)
                            .size((cropRect.width()).dp, (cropRect.height()).dp)
                            .border(2.dp, Color.Yellow)
                    )

                    // Four corner handles (drag to resize)
                    CornerHandle(cropRect.left, cropRect.top) { dx, dy ->
                        cropRect = android.graphics.RectF(
                            (cropRect.left + dx),
                            (cropRect.top + dy),
                            cropRect.right,
                            cropRect.bottom
                        )
                    }
                    CornerHandle(cropRect.right, cropRect.top) { dx, dy ->
                        cropRect = android.graphics.RectF(
                            cropRect.left,
                            (cropRect.top + dy),
                            (cropRect.right + dx),
                            cropRect.bottom
                        )
                    }
                    CornerHandle(cropRect.left, cropRect.bottom) { dx, dy ->
                        cropRect = android.graphics.RectF(
                            (cropRect.left + dx),
                            cropRect.top,
                            cropRect.right,
                            (cropRect.bottom + dy)
                        )
                    }
                    CornerHandle(cropRect.right, cropRect.bottom) { dx, dy ->
                        cropRect = android.graphics.RectF(
                            cropRect.left,
                            cropRect.top,
                            (cropRect.right + dx),
                            (cropRect.bottom + dy)
                        )
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        picker.launch(arrayOf("image/*"))
                    }
                }) { Text("Pick Image") }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(onClick = {
                    scale = 1f; rotation = 0f; offsetX = 0f; offsetY = 0f; opacity = 0.7f
                }) { Text("Reset") }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(onClick = { cropMode = !cropMode }) {
                    Text(if (cropMode) "Exit Crop" else "Crop")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = overlayBitmap != null && handle != null,
                    onClick = {
                        handle?.let { h ->
                            saveComposite(
                                context = context,
                                imageCapture = h.imageCapture,
                                previewSize = previewSize,
                                overlay = overlayBitmap,
                                opacity = opacity,
                                scale = scale,
                                rotation = rotation,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                cropRect = cropRect.takeIf { cropMode } // only apply when crop mode is on
                            )
                        }
                    }
                ) { Text("Save") }
            }

            // Opacity slider
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Opacity")
                Slider(value = opacity, onValueChange = { opacity = it.coerceIn(0f, 1f) }, valueRange = 0f..1f)
            }
        }
    }
}

@Composable
private fun CornerHandle(
    x: Float,
    y: Float,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    Box(
        Modifier
            .offset(x = (x - 10).dp, y = (y - 10).dp)
            .size(20.dp)
            .drawBehind { drawRect(Color.Yellow) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consumeAllChanges()
                    onDrag(drag.x, drag.y)
                }
            }
    )
}

/**
 * Take a camera photo, composite the overlay bitmap with given transform & crop (all in preview coords),
 * and save to MediaStore.
 */
private fun saveComposite(
    context: android.content.Context,
    imageCapture: ImageCapture,
    previewSize: IntSize,
    overlay: Bitmap?,
    opacity: Float,
    scale: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    cropRect: android.graphics.RectF?
) {
    if (overlay == null) return

    // 1) Capture photo to a temp file
    val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val temp = File(context.cacheDir, "$name.tmp.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(temp).build()
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(options, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    // 2) Load captured bitmap
                    val camBitmap = BitmapFactory.decodeFile(temp.absolutePath)
                    if (camBitmap == null) {
                        temp.delete()
                        return
                    }

                    // 3) Compute mapping from preview space -> camera bitmap space
                    val sx = camBitmap.width.toFloat() / previewSize.width
                    val sy = camBitmap.height.toFloat() / previewSize.height

                    // 4) Prepare canvas
                    val outBitmap = Bitmap.createBitmap(camBitmap.width, camBitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(outBitmap)
                    canvas.drawBitmap(camBitmap, 0f, 0f, null)

                    // 5) Prepare overlay paint with opacity
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = (opacity * 255).toInt().coerceIn(0, 255)
                        isFilterBitmap = true
                    }

                    // 6) Build matrix: map overlay bitmap to preview with given transform, then scale to camera space
                    val m = Matrix()
                    // Move to preview center
                    // (Using FIT content; for exact mapping you could take overlay intrinsic size into account)
                    m.postTranslate(0f, 0f)
                    // Apply user transforms in preview coords
                    m.postTranslate(offsetX, offsetY)
                    m.postRotate(rotation)
                    m.postScale(scale, scale)
                    // Scale up to camera bitmap coords
                    m.postScale(sx, sy)

                    // 7) Optional crop: in camera coords
                    if (cropRect != null) {
                        val camCrop = android.graphics.RectF(
                            cropRect.left * sx,
                            cropRect.top * sy,
                            cropRect.right * sx,
                            cropRect.bottom * sy
                        )
                        canvas.save()
                        canvas.clipRect(camCrop)
                        canvas.drawBitmap(overlay, m, paint)
                        canvas.restore()
                    } else {
                        canvas.drawBitmap(overlay, m, paint)
                    }

                    // 8) Save to MediaStore
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OverlayCam")
                        }
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )
                    uri?.let { out ->
                        context.contentResolver.openOutputStream(out)?.use { os ->
                            outBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
                        }
                    }

                    // Cleanup
                    temp.delete()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    )
}
// == end/ImageOverlay.kt ==
```

---

## Notes / trade-offs

* **Crop behavior:** to keep the preview buttery-smooth, the live overlay is not hard-clipped; you get a precise crop **guide** while editing, and the **saved** composite applies the crop exactly. If you prefer true live clipping, we can switch the overlay to a `Canvas` draw with a `clipRect` (slightly more code, marginal perf cost).
* **Transform origin:** current transform composes translate→rotate→scale in preview space and maps to camera space; for pixel-perfect matching against diverse aspect ratios, we can refine the matrix (e.g., center-anchored transforms and accounting for image intrinsic size and `ContentScale`).
* **Permissions:** saving via **MediaStore** avoids `WRITE_EXTERNAL_STORAGE` on modern Android.

---

## Usage

```sh
# build via docker-compose (with named Gradle cache)
docker compose up --build --abort-on-container-exit

# or via Makefile
make docker
make build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On CI, your APK appears in the workflow **Artifacts** as `app-debug-apk`.

---

## Next steps (if you want)

* switch to **center-anchored** transform matrix for intuitive rotate/scale around image center
* add **snap-to-edges** and **bounds clamping**
* implement **true live crop** via Compose `Canvas` (clipRect + drawImage + gesture layer)
* expose a **reset crop** button + aspect-ratio presets (1:1, 4:3, 16:9)
* “**Save overlay only**” (transparent PNG) mode

if you want me to convert the on-screen overlay to a Canvas with true live clipping, say the word and i’ll drop in that variant too.
