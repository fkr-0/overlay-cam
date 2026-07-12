package com.example.overlaycam

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permission = Manifest.permission.CAMERA

    var granted by rememberSaveable { mutableStateOf(isCameraPermissionGranted(context)) }
    var systemRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(granted) }

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

    val permissionState = CameraPermissionUiState(
        isGranted = granted,
        isSystemRequestInFlight = systemRequestInFlight,
        hasRequestedPermission = hasRequestedPermission,
    )

    LaunchedEffect(permissionState.shouldLaunchInitialSystemRequest) {
        if (permissionState.shouldLaunchInitialSystemRequest) {
            launchSystemPermissionRequest()
        }
    }

    when (permissionState.stage) {
        CameraPermissionStage.Granted -> content()
        CameraPermissionStage.RequestingSystemPermission -> PermissionRequestInProgress()
        CameraPermissionStage.NeedsUserAction -> PermissionDeniedPrompt(
            onRequest = { launchSystemPermissionRequest() },
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
