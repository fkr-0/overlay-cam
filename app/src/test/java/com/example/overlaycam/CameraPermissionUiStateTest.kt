package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraPermissionUiStateTest {
    @Test
    fun grantedPermissionShowsContentStage() {
        val state = CameraPermissionUiState(
            isGranted = true,
            isSystemRequestInFlight = false,
            hasRequestedPermission = true,
        )

        assertEquals(CameraPermissionStage.Granted, state.stage)
        assertFalse(state.shouldLaunchInitialSystemRequest)
        assertFalse(state.showManualPermissionPrompt)
    }

    @Test
    fun firstLaunchRequestsOnlyTheSystemPermissionSheet() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = false,
            hasRequestedPermission = false,
        )

        assertEquals(CameraPermissionStage.RequestingSystemPermission, state.stage)
        assertTrue(state.shouldLaunchInitialSystemRequest)
        assertTrue(state.showSystemRequestWaitingScreen)
        assertFalse(state.showManualPermissionPrompt)
    }

    @Test
    fun inFlightRequestKeepsManualPromptHidden() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = true,
            hasRequestedPermission = true,
        )

        assertEquals(CameraPermissionStage.RequestingSystemPermission, state.stage)
        assertFalse(state.shouldLaunchInitialSystemRequest)
        assertTrue(state.showSystemRequestWaitingScreen)
        assertFalse(state.showManualPermissionPrompt)
    }

    @Test
    fun deniedPermissionShowsOnlyManualRetryPrompt() {
        val state = CameraPermissionUiState(
            isGranted = false,
            isSystemRequestInFlight = false,
            hasRequestedPermission = true,
        )

        assertEquals(CameraPermissionStage.NeedsUserAction, state.stage)
        assertFalse(state.shouldLaunchInitialSystemRequest)
        assertFalse(state.showSystemRequestWaitingScreen)
        assertTrue(state.showManualPermissionPrompt)
    }
}
