
package com.example.overlaycam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
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
import java.nio.ByteBuffer

data class CameraHandle(
    val imageCapture: ImageCapture,
    val previewView: PreviewView,
    val captureFrame: () -> Bitmap?
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

        // Latest frame holder for freeze functionality
        var latestBitmap: Bitmap? = null
        var latestBitmapRotation: Int = 0

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                    val buffer = proxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    // Decode to Bitmap
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    // Rotate if needed (camera sensors are usually rotated)
                    val rotation = proxy.imageInfo.rotationDegrees
                    val rotatedBitmap = if (rotation != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotation.toFloat())
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        bitmap
                    }

                    // Recycle old bitmap
                    latestBitmap?.recycle()
                    latestBitmap = rotatedBitmap
                    latestBitmapRotation = rotation

                    proxy.close()
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                (context as androidx.lifecycle.LifecycleOwner),
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            bindHandle(CameraHandle(
                imageCapture = imageCapture,
                previewView = previewView,
                captureFrame = { latestBitmap?.copy(Bitmap.Config.ARGB_8888, false) }
            ))
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}