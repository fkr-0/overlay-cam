package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayUiStateTest {
    @Test
    fun basicWorkflowAlwaysOffersOneObviousRecommendedAction() {
        val live = OverlayUiState(hasStillBackground = false)
        val backgroundReady = OverlayUiState(hasStillBackground = true)
        val editing = OverlayUiState(hasStillBackground = true, hasOverlay = true)
        val fixed = OverlayUiState(
            hasStillBackground = true,
            hasOverlay = true,
            isOverlayFixed = true,
        )

        assertEquals(OverlayRecommendedAction.FreezeFrame, live.recommendedAction)
        assertEquals("Freeze frame", live.recommendedActionLabel)
        assertEquals("Live camera", live.title)
        assertEquals("LIVE", live.stageLabel)

        assertEquals(OverlayRecommendedAction.PickOverlay, backgroundReady.recommendedAction)
        assertEquals("Add overlay image", backgroundReady.recommendedActionLabel)
        assertEquals("Background ready", backgroundReady.title)

        assertEquals(OverlayRecommendedAction.FinishAdjusting, editing.recommendedAction)
        assertEquals("Done adjusting", editing.recommendedActionLabel)
        assertEquals("Adjust overlay", editing.title)
        assertTrue(editing.showGestureHint)

        assertEquals(OverlayRecommendedAction.SaveImage, fixed.recommendedAction)
        assertEquals("Save image", fixed.recommendedActionLabel)
        assertEquals("Ready to save", fixed.title)
    }

    @Test
    fun photoOnlySetupStartsWithBackgroundSelection() {
        val state = OverlayUiState(
            hasStillBackground = false,
            cameraAvailable = false,
        )

        assertEquals(OverlayUiMode.Setup, state.mode)
        assertEquals("PHOTOS", state.stageLabel)
        assertEquals("Photo mode", state.title)
        assertEquals(OverlayRecommendedAction.PickBackground, state.recommendedAction)
        assertEquals("Choose background photo", state.recommendedActionLabel)
        assertTrue(state.guidance.contains("optional"))
    }

    @Test
    fun workflowCopyExplainsTheImmediateActionWithoutNumberedSteps() {
        val states = listOf(
            OverlayUiState(hasStillBackground = false),
            OverlayUiState(hasStillBackground = true),
            OverlayUiState(hasStillBackground = true, hasOverlay = true),
            OverlayUiState(hasStillBackground = true, hasOverlay = true, isOverlayFixed = true),
        )

        states.forEach { state ->
            assertFalse(state.stageLabel.any(Char::isDigit))
            assertFalse(state.recommendedActionHint.isBlank())
        }
        assertTrue(states[0].recommendedActionHint.contains("still"))
        assertTrue(states[1].recommendedActionHint.contains("overlay"))
        assertTrue(states[2].recommendedActionHint.contains("Drag"))
        assertTrue(states[3].recommendedActionHint.contains("Save"))
    }

    @Test
    fun expansionActionNamesTheToolsItReveals() {
        assertEquals("Options", OverlayUiState(hasStillBackground = false).expandActionLabel)
        assertEquals("Options", OverlayUiState(hasStillBackground = true).expandActionLabel)
        assertEquals(
            "Fine tune",
            OverlayUiState(hasStillBackground = true, hasOverlay = true).expandActionLabel,
        )
        assertEquals(
            "Point tools",
            OverlayUiState(
                hasStillBackground = true,
                hasOverlay = true,
                isPointFixMode = true,
            ).expandActionLabel,
        )
        assertEquals(
            "More actions",
            OverlayUiState(
                hasStillBackground = true,
                hasOverlay = true,
                isOverlayFixed = true,
            ).expandActionLabel,
        )
    }

    @Test
    fun collapsedEditingKeepsTheMostCommonTweakVisible() {
        val editing = OverlayUiState(hasStillBackground = true, hasOverlay = true)
        val fixed = OverlayUiState(
            hasStillBackground = true,
            hasOverlay = true,
            isOverlayFixed = true,
        )

        assertTrue(editing.showQuickOpacityControl)
        assertFalse(fixed.showQuickOpacityControl)
    }

    @Test
    fun pointMatchingHasACompactExitAction() {
        val state = OverlayUiState(
            hasStillBackground = true,
            hasOverlay = true,
            isPointFixMode = true,
        )

        assertEquals(OverlayUiMode.PointFix, state.mode)
        assertEquals("POINTS", state.stageLabel)
        assertEquals("Match points", state.title)
        assertEquals(OverlayRecommendedAction.FinishPointFix, state.recommendedAction)
        assertEquals("Finish matching", state.recommendedActionLabel)
        assertTrue(state.showPointFixPanel)
        assertFalse(state.showEditPanel)
    }

    @Test
    fun fixedModeWinsOverPointFixWhenBothFlagsAreSet() {
        val state = OverlayUiState(
            hasStillBackground = true,
            hasOverlay = true,
            isOverlayFixed = true,
            isPointFixMode = true,
        )

        assertEquals(OverlayUiMode.FixedOverlay, state.mode)
        assertEquals(OverlayRecommendedAction.SaveImage, state.recommendedAction)
        assertFalse(state.showPointFixPanel)
        assertTrue(state.showFixedOverlayActions)
    }

    @Test
    fun panelsMatchOnlyTheCurrentMode() {
        val setup = OverlayUiState(hasStillBackground = false)
        val ready = OverlayUiState(hasStillBackground = true)
        val editing = OverlayUiState(hasStillBackground = true, hasOverlay = true)
        val fixed = OverlayUiState(hasStillBackground = true, hasOverlay = true, isOverlayFixed = true)

        assertFalse(setup.showEditPanel)
        assertFalse(ready.showEditPanel)
        assertTrue(editing.showEditPanel)
        assertFalse(fixed.showEditPanel)
        assertTrue(fixed.showFixedOverlayActions)
    }
}
