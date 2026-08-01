package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayGesturesTest {
    @Test
    fun pointerCountSelectsAnUnambiguousOperation() {
        assertEquals(OverlayGestureOperation.Idle, gestureOperationForPointerCount(0, true))
        assertEquals(OverlayGestureOperation.MoveOverlay, gestureOperationForPointerCount(1, true))
        assertEquals(OverlayGestureOperation.TransformOverlay, gestureOperationForPointerCount(2, true))
        assertEquals(OverlayGestureOperation.PanView, gestureOperationForPointerCount(1, false))
        assertEquals(OverlayGestureOperation.ZoomView, gestureOperationForPointerCount(2, false))
    }

    @Test
    fun operationCopyExplainsTheActiveTouchContract() {
        assertEquals("Move overlay", OverlayGestureOperation.MoveOverlay.label)
        assertEquals("Resize + rotate overlay", OverlayGestureOperation.TransformOverlay.label)
        assertEquals("One finger moves the overlay.", OverlayGestureOperation.MoveOverlay.instruction)
        assertEquals(
            "Two fingers resize and rotate the overlay.",
            OverlayGestureOperation.TransformOverlay.instruction,
        )
    }

    @Test
    fun zoomKeepsTheTouchedContentUnderTheGestureCentroid() {
        val next = zoomViewportAround(
            currentScale = 1f,
            currentOffset = androidx.compose.ui.geometry.Offset.Zero,
            zoom = 2f,
            centroid = androidx.compose.ui.geometry.Offset(75f, 50f),
            viewportCenter = androidx.compose.ui.geometry.Offset(50f, 50f),
        )

        assertEquals(2f, next.scale)
        assertEquals(androidx.compose.ui.geometry.Offset(-25f, 0f), next.offset)
    }
}
