package com.example.overlaycam

internal enum class PointMatchStep {
    PickOverlayLandmark,
    PickBackgroundLandmark,
    Complete,
}

internal data class PointMatchUiState(
    val completedPointCount: Int,
    val hasPendingOverlayLandmark: Boolean,
) {
    val step: PointMatchStep = when {
        hasPendingOverlayLandmark -> PointMatchStep.PickBackgroundLandmark
        completedPointCount >= 2 -> PointMatchStep.Complete
        else -> PointMatchStep.PickOverlayLandmark
    }

    val title: String = when (step) {
        PointMatchStep.PickOverlayLandmark -> "Tap a landmark on the overlay"
        PointMatchStep.PickBackgroundLandmark -> "Tap the same landmark on the background"
        PointMatchStep.Complete -> "Two landmark pairs are ready"
    }

    val instruction: String = when (step) {
        PointMatchStep.PickOverlayLandmark ->
            "Choose a sharp corner or recognizable detail in the reference image."
        PointMatchStep.PickBackgroundLandmark ->
            "Now tap where that exact detail belongs in the background image."
        PointMatchStep.Complete ->
            "Finish matching, or clear the pairs and place them again."
    }
}
