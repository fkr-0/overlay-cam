package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRestartTest {
    @Test fun backAlwaysMovesOneStage() {
        assertEquals(EditorBackAction.None, editorBackActionFor(OverlayUiMode.Setup))
        assertEquals(EditorBackAction.ConfirmNewCamera, editorBackActionFor(OverlayUiMode.BackgroundReady))
        assertEquals(EditorBackAction.RemoveWorkingOverlay, editorBackActionFor(OverlayUiMode.EditOverlay))
        assertEquals(EditorBackAction.ExitPointMatch, editorBackActionFor(OverlayUiMode.PointFix))
        assertEquals(EditorBackAction.UnlockOverlay, editorBackActionFor(OverlayUiMode.FixedOverlay))
    }
    @Test fun newSessionConfirmationProtectsAnActiveCapture() {
        assertEquals(false, needsRestartConfirmation(
            OverlayUiState(hasStillBackground = false, hasOverlay = false)))
        assertEquals(true, needsRestartConfirmation(
            OverlayUiState(hasStillBackground = true, hasOverlay = false)))
        assertEquals(true, needsRestartConfirmation(
            OverlayUiState(hasStillBackground = true, hasOverlay = true)))
    }
}
