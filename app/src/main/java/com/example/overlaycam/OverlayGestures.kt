package com.example.overlaycam

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput

internal enum class OverlayGestureOperation(
    val label: String,
    val instruction: String,
    val symbol: String,
) {
    Idle(
        label = "Overlay ready",
        instruction = "Use one finger to move, or two fingers to resize and rotate.",
        symbol = "",
    ),
    Move(
        label = "Move",
        instruction = "One finger moves the overlay.",
        symbol = "↕  ↔",
    ),
    Transform(
        label = "Resize + rotate",
        instruction = "Two fingers resize and rotate the overlay.",
        symbol = "↻  ◉—◉",
    ),
}

internal fun gestureOperationForPointerCount(pointerCount: Int): OverlayGestureOperation = when {
    pointerCount <= 0 -> OverlayGestureOperation.Idle
    pointerCount == 1 -> OverlayGestureOperation.Move
    else -> OverlayGestureOperation.Transform
}

internal fun Modifier.overlayTransformGestures(
    enabled: Boolean,
    onOperationChange: (OverlayGestureOperation) -> Unit,
    onMove: (Offset) -> Unit,
    onTransform: (zoom: Float, rotationDegrees: Float) -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled) {
        detectOverlayTransformGestures(
            onOperationChange = onOperationChange,
            onMove = onMove,
            onTransform = onTransform,
        )
    }
}

private suspend fun PointerInputScope.detectOverlayTransformGestures(
    onOperationChange: (OverlayGestureOperation) -> Unit,
    onMove: (Offset) -> Unit,
    onTransform: (zoom: Float, rotationDegrees: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var operation = OverlayGestureOperation.Move
        onOperationChange(operation)

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pointerCount = event.changes.count { it.pressed }
                val nextOperation = gestureOperationForPointerCount(pointerCount)
                val operationChanged = nextOperation != operation

                if (operationChanged) {
                    operation = nextOperation
                    onOperationChange(operation)
                }

                // The first event after adding or lifting a finger establishes the new
                // gesture baseline. Skipping its delta prevents a visible transform jump.
                if (!operationChanged) {
                    when (operation) {
                        OverlayGestureOperation.Idle -> Unit
                        OverlayGestureOperation.Move -> {
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) onMove(pan)
                        }
                        OverlayGestureOperation.Transform -> {
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            if (zoom != 1f || rotation != 0f) {
                                onTransform(zoom, rotation)
                            }
                        }
                    }
                }

                event.changes.forEach { change ->
                    if (change.position != change.previousPosition) change.consume()
                }

                if (pointerCount == 0) break
            }
        } finally {
            if (operation != OverlayGestureOperation.Idle) {
                onOperationChange(OverlayGestureOperation.Idle)
            }
        }
    }
}
