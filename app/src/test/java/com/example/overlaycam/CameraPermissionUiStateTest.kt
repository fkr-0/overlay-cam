package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraPermissionUiStateTest {
    @Test
    fun firstLaunchOffersAnExplicitAccessChoice() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = false,
            usePhotoOnlyMode = false,
        )

        assertEquals(CameraPermissionStage.ChooseAccess, state.stage)
        assertFalse(state.editorAvailable)
        assertFalse(state.cameraAvailable)
    }

    @Test
    fun inFlightRequestShowsOnlyTheSystemWaitingStage() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = true,
            usePhotoOnlyMode = false,
        )

        assertEquals(CameraPermissionStage.RequestingSystemPermission, state.stage)
        assertFalse(state.editorAvailable)
    }

    @Test
    fun photoOnlyModeOpensTheEditorWithoutCameraAccess() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = false,
            usePhotoOnlyMode = true,
        )

        assertEquals(CameraPermissionStage.PhotoOnly, state.stage)
        assertTrue(state.editorAvailable)
        assertFalse(state.cameraAvailable)
    }

    @Test
    fun grantedPermissionAlwaysWinsOverPhotoOnlyPreference() {
        val state = CameraPermissionUiState(
            isGranted = true,
            isSystemRequestInFlight = false,
            usePhotoOnlyMode = true,
        )

        assertEquals(CameraPermissionStage.Granted, state.stage)
        assertTrue(state.editorAvailable)
        assertTrue(state.cameraAvailable)
    }
}
