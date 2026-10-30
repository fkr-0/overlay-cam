package com.example.overlaycam

import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

data class CameraHandle(val captureFrame: () -> Bitmap?)

/**
 * The preview's lifetime is owned by its Compose effect, not by a future's
 * callback. A previous preview must never unbind a newer one after Restart.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    bindHandle: (CameraHandle) -> Unit = {},
) {
    if (!isActive) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }
    // COMPATIBLE mode renders to a TextureView, which supports bitmap capture
    // more reliably than the default SurfaceView implementation.
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(cameraProviderFuture, lifecycleOwner, previewView) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        var disposed = false
        cameraProviderFuture.addListener({
            if (!disposed) {
                try {
                    val provider = cameraProviderFuture.get()
                    if (!disposed) {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                        )
                        if (!disposed) {
                            bindHandle(CameraHandle(captureFrame = {
                                previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            }))
                        }
                    }
                } catch (error: Exception) {
                    Log.e("CameraPreview", "Camera binding failed", error)
                }
            }
        }, executor)

        onDispose {
            disposed = true
            // Only unbind our use case. unbindAll() can kill the replacement
            // camera after a rapid freeze -> restart transition.
            if (cameraProviderFuture.isDone) {
                try {
                    cameraProviderFuture.get().unbind(preview)
                } catch (error: Exception) {
                    Log.w("CameraPreview", "Unable to release old preview", error)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}
