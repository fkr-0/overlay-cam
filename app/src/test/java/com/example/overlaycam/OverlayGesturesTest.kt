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
    fun zoomHonorsNewBoundsAndRetainsFiniteViewport() {
        val low = zoomViewportAround(1f, androidx.compose.ui.geometry.Offset.Zero, 0.01f,
            androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.Zero)
        val high = zoomViewportAround(1f, androidx.compose.ui.geometry.Offset.Zero, 1000f,
            androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(0.5f, low.scale)
        assertEquals(20f, high.scale)
        val hostile = zoomViewportAround(Float.NaN, androidx.compose.ui.geometry.Offset(Float.NaN, Float.POSITIVE_INFINITY),
            Float.POSITIVE_INFINITY, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(1f, hostile.scale)
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, hostile.offset)
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
