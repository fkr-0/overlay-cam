package com.example.overlaycam

import android.content.Context
import android.graphics.Bitmap
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

data class CameraHandle(val captureFrame: () -> Bitmap?)

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
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                (context as androidx.lifecycle.LifecycleOwner),
                cameraSelector,
                preview
            )
            bindHandle(CameraHandle(
                // PreviewView exposes the most recent rendered frame when available.
                captureFrame = { previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, false) }
            ))
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}
