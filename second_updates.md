# SECOND UPDATES

**true live-clipping (Canvas)** overlay

**Pause/Resume** camera output (freeze last frame) integrated cleanly. 

**Save** will composite exactly what you see (the frozen frame); when live, Save captures a fresh photo via `ImageCapture`.

---

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

data class CameraHandle(
    val imageCapture: ImageCapture,
    val previewView: PreviewView
)

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    bindHandle: (CameraHandle) -> Unit = {}
) {
    if (!isActive) return  // when paused, we skip rendering the camera view

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
            bindHandle(CameraHandle(imageCapture, previewView))
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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    CameraPermissionGate {
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

        // Background mode: live camera or frozen frame
        var paused by remember { mutableStateOf(false) }
        var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var frozenImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

        // Overlay image state (both Bitmap for saving and ImageBitmap for Canvas)
        var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
        var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var overlayImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

        // Transform state (center-anchored)
        var opacity by remember { mutableFloatStateOf(0.7f) }
        var scaleF by remember { mutableFloatStateOf(1f) }
        var rotationDeg by remember { mutableFloatStateOf(0f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val transformState: TransformableState = rememberTransformableState { zoom, pan, rot ->
            scaleF = (scaleF * zoom).coerceIn(0.2f, 8f)
            rotationDeg += rot
            offsetX += pan.x
            offsetY += pan.y
        }

        // Crop UI state (screen/preview coords)
        var cropMode by remember { mutableStateOf(false) }
        var cropRect by remember {
            mutableStateOf(android.graphics.RectF(120f, 160f, 800f, 1200f))
        }

        // Decode overlay (Bitmap + ImageBitmap) when URI changes
        LaunchedEffect(pickedImageUri) {
            val bmp = withContext(Dispatchers.IO) {
                pickedImageUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
            overlayBitmap = bmp
            overlayImage = bmp?.asImageBitmap()
        }

        // Pickers
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

        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { previewSize = it.size }
        ) {
            // Background: either live camera or frozen frame image
            if (!paused) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isActive = true,
                    bindHandle = { handle = it }
                )
            } else {
                frozenImage?.let {
                    // Draw frozen background (fits view)
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // TRUE LIVE-CLIPPED OVERLAY via Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        // Add single-finger drag convenience
                        detectDragGestures { change, drag ->
                            change.consumeAllChanges()
                            offsetX += drag.x
                            offsetY += drag.y
                        }
                    }
            ) {
                // Optional dim + crop hole while in cropMode
                if (cropMode) {
                    // Darken everything
                    drawRect(Color(0f, 0f, 0f, 0.35f))
                }

                // Clip region: either crop rect or full canvas
                val clip = if (cropMode) {
                    Rect(
                        left = cropRect.left,
                        top = cropRect.top,
                        right = cropRect.right,
                        bottom = cropRect.bottom
                    )
                } else {
                    Rect(0f, 0f, size.width, size.height)
                }

                clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                    // Draw overlay image centered on canvas with transforms
                    overlayImage?.let { img ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val iw = img.width.toFloat()
                        val ih = img.height.toFloat()
                        // draw centered
                        val dx = cx - iw / 2f
                        val dy = cy - ih / 2f

                        // Compose transforms: translate to center+offset, rotate, scale around center
                        translate(offsetX, offsetY) {
                            translate(cx, cy) {
                                rotate(rotationDeg) {
                                    scale(scaleF, scaleF) {
                                        translate(-cx, -cy) {
                                            // alpha: use drawImage with global alpha via Paint
                                            drawImage(
                                                image = img,
                                                topLeft = Offset(dx, dy),
                                                alpha = opacity
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // If in crop mode, clear the dim inside the crop rect to make a hole
                if (cropMode) {
                    // nothing to do here since we already clipped for the overlay;
                    // the dim background is still visible outside the clip
                }
            }

            // Crop rectangle guide + handles (UI)
            if (cropMode) {
                // Crop border
                Box(
                    Modifier
                        .offset(x = cropRect.left.dp, y = cropRect.top.dp)
                        .size((cropRect.width()).dp, (cropRect.height()).dp)
                        .border(2.dp, Color.Yellow)
                        .pointerInput(cropRect) {
                            // drag whole rect
                            detectDragGestures { change, drag ->
                                change.consumeAllChanges()
                                cropRect.offset(drag.x, drag.y)
                            }
                        }
                )
                // Corner handles
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

            // Top-right controls: Pick / Reset / Crop / Pause/Resume / Save
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
                    scaleF = 1f; rotationDeg = 0f; offsetX = 0f; offsetY = 0f; opacity = 0.7f
                }) { Text("Reset") }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(onClick = { cropMode = !cropMode }) {
                    Text(if (cropMode) "Exit Crop" else "Crop")
                }

                Spacer(Modifier.width(8.dp))

                // Pause / Resume camera
                OutlinedButton(
                    onClick = {
                        if (!paused) {
                            // Freeze: snapshot current preview
                            handle?.previewView?.bitmap?.let { bmp ->
                                frozenBitmap = bmp
                                frozenImage = bmp.asImageBitmap()
                                paused = true
                            }
                        } else {
                            // Resume live camera
                            paused = false
                            frozenBitmap = null
                            frozenImage = null
                        }
                    }
                ) { Text(if (paused) "Resume" else "Pause") }

                Spacer(Modifier.width(8.dp))

                // Save composite (uses frozen frame if paused, else fresh ImageCapture)
                val canSave = (overlayBitmap != null) && (handle != null) && (!paused || frozenBitmap != null)
                Button(
                    enabled = canSave,
                    onClick = {
                        val useFrozen = paused && frozenBitmap != null
                        saveComposite(
                            context = context,
                            imageCapture = handle!!.imageCapture,
                            previewSize = previewSize,
                            overlay = overlayBitmap,
                            opacity = opacity,
                            scale = scaleF,
                            rotation = rotationDeg,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            cropRect = cropMode.thenCrop(cropRect),
                            frozen = if (useFrozen) frozenBitmap else null
                        )
                    }
                ) { Text("Save") }
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

private fun Boolean.thenCrop(r: android.graphics.RectF): android.graphics.RectF? =
    if (this) r else null

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
 * Save composite to MediaStore.
 * - If [frozen] is provided (paused), we composite over that bitmap (what you see).
 * - Otherwise we capture via [imageCapture] for a fresh full-res photo.
 * Mapping from preview-space transforms to camera/frozen bitmap space is handled via scaling factors.
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
    cropRect: android.graphics.RectF?,
    frozen: Bitmap?
) {
    if (overlay == null) return

    // If we have a frozen frame, use it directly; else capture a photo
    if (frozen != null) {
        val camBitmap = frozen
        composeAndSave(context, camBitmap, previewSize, overlay, opacity, scale, rotation, offsetX, offsetY, cropRect)
        return
    }

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
                    val camBitmap = BitmapFactory.decodeFile(temp.absolutePath) ?: run {
                        temp.delete(); return
                    }
                    composeAndSave(context, camBitmap, previewSize, overlay, opacity, scale, rotation, offsetX, offsetY, cropRect)
                    temp.delete()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    )
}

private fun composeAndSave(
    context: android.content.Context,
    camBitmap: Bitmap,
    previewSize: IntSize,
    overlay: Bitmap,
    opacity: Float,
    scale: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    cropRect: android.graphics.RectF?
) {
    // Map preview space -> camera bitmap space
    val sx = camBitmap.width.toFloat() / previewSize.width
    val sy = camBitmap.height.toFloat() / previewSize.height

    // Prepare canvas
    val outBitmap = Bitmap.createBitmap(camBitmap.width, camBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)
    canvas.drawBitmap(camBitmap, 0f, 0f, null)

    // Build matrix to place overlay centered on preview, then apply transforms, then scale to camera space
    val m = Matrix()

    // Center the overlay bitmap on preview center
    val cx = previewSize.width / 2f
    val cy = previewSize.height / 2f
    val iw = overlay.width.toFloat()
    val ih = overlay.height.toFloat()
    val dx = cx - iw / 2f
    val dy = cy - ih / 2f
    m.postTranslate(dx, dy)

    // Apply user transforms in preview coords (center-anchored)
    // Translate by offsets
    m.postTranslate(offsetX, offsetY)
    // Rotate around preview center
    m.postTranslate(-cx, -cy)
    m.postRotate(rotation, 0f, 0f)
    // Scale around rotation origin
    m.postScale(scale, scale)
    m.postTranslate(cx, cy)

    // Scale to camera coords
    m.postScale(sx, sy)

    // Draw, with optional crop
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (opacity * 255).toInt().coerceIn(0, 255)
        isFilterBitmap = true
    }

    if (cropRect != null) {
        val camCrop = android.graphics.RectF(
            cropRect.left * sx,
            cropRect.top * sy,
            cropRect.right * sx,
            cropRect.bottom * sy
        )
        canvas.save()
        canvas.clipRect(camCrop)
        canvas.drawBitmap(overlay, m, p)
        canvas.restore()
    } else {
        canvas.drawBitmap(overlay, m, p)
    }

    // Save to MediaStore
    val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OverlayCam")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let { out ->
        context.contentResolver.openOutputStream(out)?.use { os ->
            outBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
        }
    }
}
// == end/ImageOverlay.kt ==
```

---

### Notes & tests

* **Save behavior**:

  * **Paused** → composites exactly the frozen frame you’re looking at.
  * **Live** → triggers `ImageCapture` for a fresh, full-res photo.
