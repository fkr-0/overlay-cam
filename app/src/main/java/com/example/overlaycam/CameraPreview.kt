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

    DisposableEffect(cameraProviderFuture) {
        onDispose {
            cameraProviderFuture.addListener(
                { cameraProviderFuture.get().unbindAll() },
                executor,
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                    bindHandle(
                        CameraHandle(
                            captureFrame = {
                                previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            },
                        ),
                    )
                } catch (error: Exception) {
                    Log.e("CameraPreview", "Camera binding failed", error)
                }
            }, executor)

            previewView
        },
    )
}
