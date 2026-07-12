package com.example.overlaycam

/** A small, testable state machine for optional live-camera access. */
internal enum class CameraPermissionStage {
    ChooseAccess,
    RequestingSystemPermission,
    Granted,
    PhotoOnly,
}

internal data class CameraPermissionUiState(
    val isGranted: Boolean,
    val isSystemRequestInFlight: Boolean,
    val usePhotoOnlyMode: Boolean,
) {
    val stage: CameraPermissionStage = when {
        isGranted -> CameraPermissionStage.Granted
        isSystemRequestInFlight -> CameraPermissionStage.RequestingSystemPermission
        usePhotoOnlyMode -> CameraPermissionStage.PhotoOnly
        else -> CameraPermissionStage.ChooseAccess
    }

    val cameraAvailable: Boolean = stage == CameraPermissionStage.Granted
    val editorAvailable: Boolean =
        stage == CameraPermissionStage.Granted || stage == CameraPermissionStage.PhotoOnly
}
