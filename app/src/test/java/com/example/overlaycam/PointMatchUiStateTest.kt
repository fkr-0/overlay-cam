package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointMatchUiStateTest {
    @Test
    fun startsByRequestingAnOverlayLandmark() {
        val state = PointMatchUiState(
            completedPointCount = 0,
            hasPendingOverlayLandmark = false,
        )

        assertEquals(PointMatchStep.PickOverlayLandmark, state.step)
        assertTrue(state.title.contains("overlay"))
    }

    @Test
    fun pendingLandmarkRequestsTheMatchingBackgroundPosition() {
        val state = PointMatchUiState(
            completedPointCount = 1,
            hasPendingOverlayLandmark = true,
        )

        assertEquals(PointMatchStep.PickBackgroundLandmark, state.step)
        assertTrue(state.title.contains("background"))
    }

    @Test
    fun twoCompletedPairsStopRequestingMorePoints() {
        val state = PointMatchUiState(
            completedPointCount = 2,
            hasPendingOverlayLandmark = false,
        )

        assertEquals(PointMatchStep.Complete, state.step)
        assertTrue(state.instruction.contains("Finish"))
    }
}
