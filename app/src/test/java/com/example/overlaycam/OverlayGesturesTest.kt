package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayGesturesTest {
    @Test
    fun pointerCountSelectsAnUnambiguousOperation() {
        assertEquals(OverlayGestureOperation.Idle, gestureOperationForPointerCount(0))
        assertEquals(OverlayGestureOperation.Move, gestureOperationForPointerCount(1))
        assertEquals(OverlayGestureOperation.Transform, gestureOperationForPointerCount(2))
        assertEquals(OverlayGestureOperation.Transform, gestureOperationForPointerCount(3))
    }

    @Test
    fun operationCopyExplainsTheActiveTouchContract() {
        assertEquals("Move", OverlayGestureOperation.Move.label)
        assertEquals("Resize + rotate", OverlayGestureOperation.Transform.label)
        assertEquals("One finger moves the overlay.", OverlayGestureOperation.Move.instruction)
        assertEquals(
            "Two fingers resize and rotate the overlay.",
            OverlayGestureOperation.Transform.instruction,
        )
    }
}
