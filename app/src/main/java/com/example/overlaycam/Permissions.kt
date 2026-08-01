package com.example.overlaycam

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun CameraPermissionGate(
    content: @Composable (
        cameraAvailable: Boolean,
        cameraSupported: Boolean,
        requestCameraAccess: () -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    val permission = Manifest.permission.CAMERA
    val cameraSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    if (!cameraSupported) {
        content(false, false) {}
        return
    }

    var granted by rememberSaveable { mutableStateOf(isCameraPermissionGranted(context)) }
    var systemRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(granted) }
    var usePhotoOnlyMode by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted || isCameraPermissionGranted(context)
        systemRequestInFlight = false
        hasRequestedPermission = true
    }

    fun launchSystemPermissionRequest() {
        if (granted || systemRequestInFlight) return
        systemRequestInFlight = true
        hasRequestedPermission = true
        launcher.launch(permission)
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isCameraPermissionGranted(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val permissionState = CameraPermissionUiState(
        isGranted = granted,
        isSystemRequestInFlight = systemRequestInFlight,
        usePhotoOnlyMode = usePhotoOnlyMode,
    )

    when (permissionState.stage) {
        CameraPermissionStage.Granted -> content(true, true, ::launchSystemPermissionRequest)
        CameraPermissionStage.PhotoOnly -> content(false, true, ::launchSystemPermissionRequest)
        CameraPermissionStage.RequestingSystemPermission -> PermissionRequestInProgress()
        CameraPermissionStage.ChooseAccess -> CameraAccessChoice(
            wasDenied = hasRequestedPermission,
            onRequest = ::launchSystemPermissionRequest,
            onContinueWithPhotos = { usePhotoOnlyMode = true },
            onOpenSettings = { context.openOverlayCamSettings() },
        )
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openOverlayCamSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
