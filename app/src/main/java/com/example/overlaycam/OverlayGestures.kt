package com.example.overlaycam

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
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
        instruction = "Touch the canvas to manipulate the overlay or inspect details.",
        symbol = "",
    ),
    MoveOverlay(
        label = "Move overlay",
        instruction = "One finger moves the overlay.",
        symbol = "↕  ↔",
    ),
    TransformOverlay(
        label = "Resize + rotate overlay",
        instruction = "Two fingers resize and rotate the overlay.",
        symbol = "↻  ◉—◉",
    ),
    PanView(
        label = "Pan drawing",
        instruction = "One finger pans the frozen comparison.",
        symbol = "HAND  ↔",
    ),
    ZoomView(
        label = "Zoom drawing",
        instruction = "Two fingers zoom the frozen comparison.",
        symbol = "◉  ↔  ◉",
    ),
}

internal fun gestureOperationForPointerCount(
    pointerCount: Int,
    repositionEnabled: Boolean,
): OverlayGestureOperation = when {
    pointerCount <= 0 -> OverlayGestureOperation.Idle
    pointerCount == 1 && repositionEnabled -> OverlayGestureOperation.MoveOverlay
    pointerCount >= 2 && repositionEnabled -> OverlayGestureOperation.TransformOverlay
    pointerCount == 1 -> OverlayGestureOperation.PanView
    else -> OverlayGestureOperation.ZoomView
}

internal data class ViewportTransform(
    val scale: Float,
    val offset: Offset,
)

internal fun zoomViewportAround(
    currentScale: Float,
    currentOffset: Offset,
    zoom: Float,
    centroid: Offset,
    viewportCenter: Offset,
    minScale: Float = MIN_FROZEN_VIEW_SCALE,
    maxScale: Float = MAX_FROZEN_VIEW_SCALE,
): ViewportTransform {
    val safeScale = currentScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(minScale, maxScale) ?: 1f
    val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
    val nextScale = (safeScale.toDouble() * safeZoom).coerceIn(minScale.toDouble(), maxScale.toDouble()).toFloat()
    fun clean(value: Float): Float = if (value.isFinite()) value.coerceIn(-1000000f, 1000000f) else 0f
    val safeOffset = Offset(clean(currentOffset.x), clean(currentOffset.y))
    if (nextScale == safeScale) return ViewportTransform(nextScale, safeOffset)
    val ratio = nextScale / safeScale
    val center = Offset(clean(centroid.x - viewportCenter.x), clean(centroid.y - viewportCenter.y))
    val nextOffset = Offset(
        clean(center.x * (1f - ratio) + safeOffset.x * ratio),
        clean(center.y * (1f - ratio) + safeOffset.y * ratio),
    )
    return ViewportTransform(nextScale, nextOffset)
}

internal fun Modifier.overlayCanvasGestures(
    enabled: Boolean,
    repositionEnabled: Boolean,
    onOperationChange: (OverlayGestureOperation) -> Unit,
    onOneFingerPan: (Offset) -> Unit,
    onTwoFingerTransform: (zoom: Float, rotationDegrees: Float, centroid: Offset) -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, repositionEnabled) {
        detectOverlayCanvasGestures(
            repositionEnabled = repositionEnabled,
            onOperationChange = onOperationChange,
            onOneFingerPan = onOneFingerPan,
            onTwoFingerTransform = onTwoFingerTransform,
        )
    }
}

private suspend fun PointerInputScope.detectOverlayCanvasGestures(
    repositionEnabled: Boolean,
    onOperationChange: (OverlayGestureOperation) -> Unit,
    onOneFingerPan: (Offset) -> Unit,
    onTwoFingerTransform: (zoom: Float, rotationDegrees: Float, centroid: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var operation = gestureOperationForPointerCount(1, repositionEnabled)
        onOperationChange(operation)

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pointerCount = event.changes.count { it.pressed }
                val nextOperation = gestureOperationForPointerCount(pointerCount, repositionEnabled)
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
                        OverlayGestureOperation.MoveOverlay,
                        OverlayGestureOperation.PanView -> {
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) onOneFingerPan(pan)
                        }
                        OverlayGestureOperation.TransformOverlay,
                        OverlayGestureOperation.ZoomView -> {
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            if (zoom != 1f || rotation != 0f) {
                                onTwoFingerTransform(
                                    zoom,
                                    rotation,
                                    event.calculateCentroid(useCurrent = true),
                                )
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
