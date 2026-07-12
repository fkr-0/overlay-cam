package com.example.overlaycam

/**
 * Small, testable state machine for the camera permission gate.
 *
 * The app must not show its own permission prompt while the Android runtime
 * permission sheet is opening. Keeping that transition explicit prevents the
 * startup UX from stacking multiple modal-looking prompts on top of each other.
 */
enum class CameraPermissionStage {
    Granted,
    RequestingSystemPermission,
    NeedsUserAction,
}

data class CameraPermissionUiState(
    val isGranted: Boolean,
    val isSystemRequestInFlight: Boolean,
    val hasRequestedPermission: Boolean,
) {
    val stage: CameraPermissionStage = when {
        isGranted -> CameraPermissionStage.Granted
        isSystemRequestInFlight -> CameraPermissionStage.RequestingSystemPermission
        !hasRequestedPermission -> CameraPermissionStage.RequestingSystemPermission
        else -> CameraPermissionStage.NeedsUserAction
    }

    val shouldLaunchInitialSystemRequest: Boolean =
        !isGranted && !isSystemRequestInFlight && !hasRequestedPermission

    val showSystemRequestWaitingScreen: Boolean =
        stage == CameraPermissionStage.RequestingSystemPermission

    val showManualPermissionPrompt: Boolean =
        stage == CameraPermissionStage.NeedsUserAction
}
