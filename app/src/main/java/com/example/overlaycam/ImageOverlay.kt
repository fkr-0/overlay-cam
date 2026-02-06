package com.example.overlaycam

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image // 🔧 for frozen image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    CameraPermissionGate {
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

        // pause/freeze state
        var paused by remember { mutableStateOf(false) }
        var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var frozenImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

        // overlay image state
        var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
        var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var overlayImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

        // transforms
        val minScale = 0.2f
        val maxScale = 8f
        var opacity by remember { mutableFloatStateOf(0.7f) }
        var scaleF by remember { mutableFloatStateOf(1f) }
        var rotationDeg by remember { mutableFloatStateOf(0f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        // slider state that mirrors scaleF (kept in sync both ways)
        var scaleSlider by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(scaleF) { scaleSlider = scaleF.coerceIn(minScale, maxScale) }

        val transformState: TransformableState = rememberTransformableState { zoom, pan, rot ->
            scaleF = (scaleF * zoom).coerceIn(minScale, maxScale)
            rotationDeg += rot
            offsetX += pan.x
            offsetY += pan.y
        }

        // cropping
        var cropMode by remember { mutableStateOf(false) }
        var cropRect by remember {
            mutableStateOf(android.graphics.RectF(120f, 160f, 800f, 1200f))
        }

        // decode overlay when URI changes
        LaunchedEffect(pickedImageUri) {
            overlayBitmap = withContext(Dispatchers.IO) {
                pickedImageUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }
            overlayImage = overlayBitmap?.asImageBitmap()
        }

        // separate pickers (type-safe)
        val photoPickerLauncher =
            if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    pickedImageUri = uri
                }
            } else null

        val openDocLauncher =
            if (Build.VERSION.SDK_INT < 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    pickedImageUri = uri
                }
            } else null

        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { previewSize = it.size }
        ) {
            // background: live preview or frozen frame (draw correctly)
            if (!paused) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isActive = true,
                    bindHandle = { handle = it }
                )
            } else {
                frozenImage?.let {
                    Image( // 🔧 replace AsyncImage with Image(bitmap=…)
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // live-clipped overlay (drawn with Canvas)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectDragGestures { change: PointerInputChange, drag ->
                            change.consume()
                            offsetX += drag.x
                            offsetY += drag.y
                        }
                    }
            ) {
                if (cropMode) drawRect(Color(0f, 0f, 0f, 0.35f))

                val clip = if (cropMode)
                    Rect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
                else
                    Rect(0f, 0f, size.width, size.height)

                clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                    overlayImage?.let { img ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val iw = img.width.toFloat()
                        val ih = img.height.toFloat()
                        val dx = cx - iw / 2f
                        val dy = cy - ih / 2f

                        translate(offsetX, offsetY) {
                            translate(cx, cy) {
                                rotate(rotationDeg) {
                                    scale(scaleF, scaleF) {
                                        translate(-cx, -cy) {
                                            drawImage(image = img, topLeft = Offset(dx, dy), alpha = opacity)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // crop guides
            if (cropMode) {
                Box(
                    Modifier
                        .offset(x = cropRect.left.dp, y = cropRect.top.dp)
                        .size((cropRect.width()).dp, (cropRect.height()).dp)
                        .border(2.dp, Color.Yellow)
                        .pointerInput(cropRect) {
                            detectDragGestures { change: PointerInputChange, drag ->
                                change.consume()
                                cropRect.offset(drag.x, drag.y)
                            }
                        }
                )
                CornerHandle(cropRect.left, cropRect.top) { dx, dy ->
                    cropRect = android.graphics.RectF(cropRect.left + dx, cropRect.top + dy, cropRect.right, cropRect.bottom)
                }
                CornerHandle(cropRect.right, cropRect.top) { dx, dy ->
                    cropRect = android.graphics.RectF(cropRect.left, cropRect.top + dy, cropRect.right + dx, cropRect.bottom)
                }
                CornerHandle(cropRect.left, cropRect.bottom) { dx, dy ->
                    cropRect = android.graphics.RectF(cropRect.left + dx, cropRect.top, cropRect.right, cropRect.bottom + dy)
                }
                CornerHandle(cropRect.right, cropRect.bottom) { dx, dy ->
                    cropRect = android.graphics.RectF(cropRect.left, cropRect.top, cropRect.right + dx, cropRect.bottom + dy)
                }
            }

            // top controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        photoPickerLauncher?.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        openDocLauncher?.launch(arrayOf("image/*"))
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

                // Pause/Resume button - captures current camera frame
                OutlinedButton(
                    onClick = {
                        if (!paused) {
                            // Use captureFrame from CameraHandle for reliable frame capture
                            val bmp = handle?.captureFrame?.invoke()
                            if (bmp != null) {
                                frozenBitmap = bmp
                                frozenImage = bmp.asImageBitmap()
                                paused = true
                                Toast.makeText(context, "Frame frozen", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No frame yet — try again in a moment.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            paused = false
                            frozenBitmap?.recycle()
                            frozenBitmap = null
                            frozenImage = null
                            Toast.makeText(context, "Live camera resumed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(if (paused) "▶ Resume" else "⏸ Pause") }

                Spacer(Modifier.width(8.dp))

                val canSave = overlayBitmap != null
                Button(
                    enabled = canSave,
                    onClick = {
                        val useFrozen = paused && frozenBitmap != null
                        saveComposite(
                            context = context,
                            imageCapture = handle?.imageCapture,
                            previewSize = previewSize,
                            overlay = overlayBitmap,
                            opacity = opacity,
                            scale = scaleF,
                            rotation = rotationDeg,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            cropRect = if (cropMode) cropRect else null,
                            frozen = if (useFrozen) frozenBitmap else null
                        )
                    }
                ) { Text("💾 Save") }
            }

            // bottom controls (Opacity + Scale)
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
                Spacer(Modifier.height(8.dp))
                Text("Scale (${String.format("%.2f×", scaleSlider)})")
                Slider(
                    value = scaleSlider,
                    onValueChange = { v ->
                        val clamped = v.coerceIn(minScale, maxScale)
                        scaleSlider = clamped
                        scaleF = clamped
                    },
                    valueRange = minScale..maxScale
                )
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
                detectDragGestures { change: PointerInputChange, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
    )
}


private fun saveComposite(
    context: android.content.Context,
    imageCapture: ImageCapture?,
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

    // If we have a frozen frame, use it directly
    if (frozen != null) {
        composeAndSave(context, frozen, previewSize, overlay, opacity, scale, rotation, offsetX, offsetY, cropRect)
        return
    }

    // No frozen frame and no imageCapture - can't save
    if (imageCapture == null) {
        Toast.makeText(context, "Pause first to save frozen frame", Toast.LENGTH_SHORT).show()
        return
    }

    Toast.makeText(context, "Capturing...", Toast.LENGTH_SHORT).show()

    val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val temp = File(context.cacheDir, "$name.tmp.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(temp).build()
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(options, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val camBitmap = BitmapFactory.decodeFile(temp.absolutePath) ?: run {
                        temp.delete()
                        Toast.makeText(context, "Failed to decode captured image", Toast.LENGTH_LONG).show()
                        return
                    }
                    composeAndSave(context, camBitmap, previewSize, overlay, opacity, scale, rotation, offsetX, offsetY, cropRect)
                    temp.delete()
                } catch (t: Throwable) {
                    Toast.makeText(context, "Save failed: ${t.message}", Toast.LENGTH_LONG).show()
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
    val sx = camBitmap.width.toFloat() / previewSize.width
    val sy = camBitmap.height.toFloat() / previewSize.height

    val outBitmap = Bitmap.createBitmap(camBitmap.width, camBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)
    canvas.drawBitmap(camBitmap, 0f, 0f, null)

    val m = Matrix()
    val cx = previewSize.width / 2f
    val cy = previewSize.height / 2f
    val iw = overlay.width.toFloat()
    val ih = overlay.height.toFloat()
    val dx = cx - iw / 2f
    val dy = cy - ih / 2f

    m.postTranslate(dx, dy)
    m.postTranslate(offsetX, offsetY)
    m.postTranslate(-cx, -cy)
    m.postRotate(rotation, 0f, 0f)
    m.postScale(scale, scale)
    m.postTranslate(cx, cy)
    m.postScale(sx, sy)

    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (opacity * 255).toInt().coerceIn(0, 255)
        isFilterBitmap = true
    }

    if (cropRect != null) {
        val camCrop = android.graphics.RectF(
            cropRect.left * sx, cropRect.top * sy,
            cropRect.right * sx, cropRect.bottom * sy
        )
        canvas.save()
        canvas.clipRect(camCrop)
        canvas.drawBitmap(overlay, m, p)
        canvas.restore()
    } else {
        canvas.drawBitmap(overlay, m, p)
    }

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
        Toast.makeText(context, "Saved to Pictures/OverlayCam", Toast.LENGTH_SHORT).show()
    } ?: Toast.makeText(context, "Failed to create media entry", Toast.LENGTH_LONG).show()
}
