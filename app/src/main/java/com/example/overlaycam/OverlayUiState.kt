package com.example.overlaycam

enum class OverlayUiMode {
    Setup,
    BackgroundReady,
    EditOverlay,
    PointFix,
    FixedOverlay,
}

enum class OverlayRecommendedAction {
    FreezeFrame,
    PickBackground,
    PickOverlay,
    FinishAdjusting,
    FinishPointFix,
    SaveImage,
}

data class OverlayUiState(
    val hasStillBackground: Boolean,
    val cameraAvailable: Boolean = true,
    val cameraSupported: Boolean = true,
    val hasOverlay: Boolean = false,
    val isOverlayFixed: Boolean = false,
    val isPointFixMode: Boolean = false,
) {
    val mode: OverlayUiMode = when {
        !hasStillBackground -> OverlayUiMode.Setup
        isOverlayFixed && hasOverlay -> OverlayUiMode.FixedOverlay
        isPointFixMode && hasOverlay -> OverlayUiMode.PointFix
        hasOverlay -> OverlayUiMode.EditOverlay
        else -> OverlayUiMode.BackgroundReady
    }

    val stageLabel: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) "LIVE" else "PHOTOS"
        OverlayUiMode.BackgroundReady -> "BACKGROUND"
        OverlayUiMode.EditOverlay -> "ALIGN"
        OverlayUiMode.PointFix -> "POINTS"
        OverlayUiMode.FixedOverlay -> "REVIEW"
    }

    val title: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) "Live camera" else "Photo mode"
        OverlayUiMode.BackgroundReady -> "Background ready"
        OverlayUiMode.EditOverlay -> "Adjust overlay"
        OverlayUiMode.PointFix -> "Match points"
        OverlayUiMode.FixedOverlay -> "Ready to save"
    }

    val guidance: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            "Hold the camera still, then freeze the frame. You can also use a photo as the background."
        } else if (!cameraSupported) {
            "This device has no usable camera. Choose a background photo to start."
        } else {
            "Choose a background photo to start. Camera access remains optional and can be enabled from Options."
        }
        OverlayUiMode.BackgroundReady -> "Add the image you want to compare or trace over the frozen background."
        OverlayUiMode.EditOverlay -> "Drag to move, pinch to resize, and twist to rotate. Use the sliders for precise adjustments."
        OverlayUiMode.PointFix -> "For each pair, tap a landmark on the overlay first, then tap where it belongs on the background."
        OverlayUiMode.FixedOverlay -> "Save the composite, or reopen the overlay to make another adjustment."
    }

    val recommendedAction: OverlayRecommendedAction = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            OverlayRecommendedAction.FreezeFrame
        } else {
            OverlayRecommendedAction.PickBackground
        }
        OverlayUiMode.BackgroundReady -> OverlayRecommendedAction.PickOverlay
        OverlayUiMode.EditOverlay -> OverlayRecommendedAction.FinishAdjusting
        OverlayUiMode.PointFix -> OverlayRecommendedAction.FinishPointFix
        OverlayUiMode.FixedOverlay -> OverlayRecommendedAction.SaveImage
    }

    val recommendedActionLabel: String = when (recommendedAction) {
        OverlayRecommendedAction.FreezeFrame -> "Freeze frame"
        OverlayRecommendedAction.PickBackground -> "Choose background photo"
        OverlayRecommendedAction.PickOverlay -> "Add overlay image"
        OverlayRecommendedAction.FinishAdjusting -> "Done adjusting"
        OverlayRecommendedAction.FinishPointFix -> "Finish matching"
        OverlayRecommendedAction.SaveImage -> "Save image"
    }

    val recommendedActionHint: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            "Hold still, then freeze the camera frame."
        } else {
            "Choose a photo to use as the background."
        }
        OverlayUiMode.BackgroundReady -> "Choose an overlay image to begin adjusting it."
        OverlayUiMode.EditOverlay -> "Drag, pinch, or twist directly on the image."
        OverlayUiMode.PointFix -> "Finish when the matching points are placed correctly."
        OverlayUiMode.FixedOverlay -> "Save the finished image to your gallery."
    }

    val expandActionLabel: String = when (mode) {
        OverlayUiMode.Setup,
        OverlayUiMode.BackgroundReady -> "Options"
        OverlayUiMode.EditOverlay -> "Fine tune"
        OverlayUiMode.PointFix -> "Point tools"
        OverlayUiMode.FixedOverlay -> "More actions"
    }

    val showQuickOpacityControl: Boolean = mode == OverlayUiMode.EditOverlay
    val showGestureHint: Boolean = mode == OverlayUiMode.EditOverlay
    val showPointFixPanel: Boolean = mode == OverlayUiMode.PointFix
    val showEditPanel: Boolean = mode == OverlayUiMode.EditOverlay
    val showFixedOverlayActions: Boolean = mode == OverlayUiMode.FixedOverlay
}
