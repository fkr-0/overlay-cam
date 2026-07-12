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
    PickOverlay,
    FinishAdjusting,
    FinishPointFix,
    SaveImage,
}

data class OverlayUiState(
    val hasStillBackground: Boolean,
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
        OverlayUiMode.Setup -> "LIVE"
        OverlayUiMode.BackgroundReady -> "READY"
        OverlayUiMode.EditOverlay -> "EDITING"
        OverlayUiMode.PointFix -> "POINTS"
        OverlayUiMode.FixedOverlay -> "DONE"
    }

    val title: String = when (mode) {
        OverlayUiMode.Setup -> "Live camera"
        OverlayUiMode.BackgroundReady -> "Background ready"
        OverlayUiMode.EditOverlay -> "Adjust overlay"
        OverlayUiMode.PointFix -> "Match points"
        OverlayUiMode.FixedOverlay -> "Ready to save"
    }

    val guidance: String = when (mode) {
        OverlayUiMode.Setup -> "Hold the camera still, then freeze the frame. You can also use a photo as the background."
        OverlayUiMode.BackgroundReady -> "Add the image you want to compare or trace over the frozen background."
        OverlayUiMode.EditOverlay -> "Drag to move, pinch to resize, and twist to rotate. Use the sliders for precise adjustments."
        OverlayUiMode.PointFix -> "Tap the same landmark on both images, then finish matching."
        OverlayUiMode.FixedOverlay -> "Save the composite, or reopen the overlay to make another adjustment."
    }

    val recommendedAction: OverlayRecommendedAction = when (mode) {
        OverlayUiMode.Setup -> OverlayRecommendedAction.FreezeFrame
        OverlayUiMode.BackgroundReady -> OverlayRecommendedAction.PickOverlay
        OverlayUiMode.EditOverlay -> OverlayRecommendedAction.FinishAdjusting
        OverlayUiMode.PointFix -> OverlayRecommendedAction.FinishPointFix
        OverlayUiMode.FixedOverlay -> OverlayRecommendedAction.SaveImage
    }

    val recommendedActionLabel: String = when (recommendedAction) {
        OverlayRecommendedAction.FreezeFrame -> "Freeze frame"
        OverlayRecommendedAction.PickOverlay -> "Add overlay image"
        OverlayRecommendedAction.FinishAdjusting -> "Done adjusting"
        OverlayRecommendedAction.FinishPointFix -> "Finish matching"
        OverlayRecommendedAction.SaveImage -> "Save image"
    }

    val recommendedActionHint: String = when (mode) {
        OverlayUiMode.Setup -> "Hold still, then freeze the camera frame."
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
