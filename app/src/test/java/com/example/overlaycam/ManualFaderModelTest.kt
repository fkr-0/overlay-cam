package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualFaderModelTest {
    private val bounds = ManualFaderBounds(360f, 800f)
    private val axes = ManualAxis.entries

    @Test fun valueModeRetainsValuesForEveryAxis() {
        for (axis in axes) {
            val original = axis.baseline
            val started = ManualFaderState(axis).begin(original, bounds)
            val moved = started.dragTo(0.8f)
            val preview = moved.previewValue(original, bounds)
            val released = moved.release(preview, bounds)
            assertTrue(released.committed)
            assertEquals(axis.fromPosition(0.8f, bounds), released.value, 0.0005f)
            assertFalse(released.next.active)
            assertEquals(released.value, released.next.release(released.value, bounds).value)
        }
    }

    @Test fun relativeModeCommitsOneDeltaAndSpringReturnsToZero() {
        for (axis in axes) {
            val initial = axis.baseline
            val wheel = ManualFaderState(axis, ManualFaderMode.Relative)
                .begin(initial, bounds).dragTo(0.625f)
            val expected = axis.clamp(initial + axis.maxRelativeStep * 0.25f, bounds)
            val release = wheel.release(initial, bounds)
            assertEquals(expected, release.value, 0.0005f)
            assertTrue(release.committed)
            assertEquals(0f, release.next.relativeDelta)
            assertEquals(expected, release.next.release(expected, bounds).value)
            assertFalse(release.next.release(expected, bounds).committed)
        }
    }

    @Test fun returningRelativeWheelToCenterBeforeReleaseDoesNothing() {
        for (axis in axes) {
            val initial = axis.baseline
            val result = ManualFaderState(axis, ManualFaderMode.Relative)
                .begin(initial, bounds).dragTo(1f).dragTo(0.5f).release(initial, bounds)
            assertEquals(initial, result.value)
        }
    }

    @Test fun cancelDoesNotCommitOrLeakState() {
        for (axis in axes) {
            val initial = axis.baseline
            for (mode in ManualFaderMode.entries) {
                val started = ManualFaderState(axis, mode).begin(initial, bounds).dragTo(1f)
                val cancellation = started.cancel(initial, bounds)
                assertEquals(initial, cancellation.value)
                assertFalse(cancellation.committed)
                assertFalse(cancellation.next.active)
                assertEquals(initial, cancellation.next.release(initial, bounds).value)
            }
        }
    }

    @Test fun valueCancelRevertsAVisibleDrag() {
        val axis = ManualAxis.X
        val session = ManualFaderState(axis).begin(15f, bounds).dragTo(1f)
        val visible = session.previewValue(15f, bounds)
        assertEquals(720f, visible)
        assertEquals(15f, session.cancel(visible, bounds).value)
    }

    @Test fun clampAndNonFiniteInputs() {
        assertEquals(-720f, ManualAxis.X.clamp(-100000f, bounds))
        assertEquals(1600f, ManualAxis.Y.clamp(99999f, bounds))
        assertEquals(2f, ManualAxis.Z.clamp(9999f, bounds))
        assertEquals(1f, ManualAxis.Z.clamp(Float.NaN, bounds))
        assertEquals(0f, ManualAxis.X.clamp(Float.POSITIVE_INFINITY, bounds))
        assertEquals(1f, boundedFrozenViewScale(Float.NaN))
        assertEquals(0.5f, boundedFrozenViewScale(-20f))
        assertEquals(20f, boundedFrozenViewScale(1000f))
    }

    @Test fun logarithmicFrozenViewSliderRoundTrips() {
        for (scale in listOf(0.5f, 1f, 2f, 6f, 20f)) {
            assertEquals(scale, frozenZoomScaleAt(frozenZoomPosition(scale)), 0.0001f)
        }
        assertEquals(0f, frozenZoomPosition(0.5f))
        assertEquals(1f, frozenZoomPosition(20f))
    }

    @Test fun axisIsolationAndReset() {
        val transform = ManualOverlayTransform(14f, -3f, 1.4f)
        val changedX = transform.increment(ManualAxis.X, 40f, bounds)
        assertEquals(-3f, changedX.y)
        assertEquals(1.4f, changedX.z)
        val changedY = transform.withValue(ManualAxis.Y, 20f, bounds)
        assertEquals(14f, changedY.x)
        assertEquals(1.4f, changedY.z)
        val changedZ = transform.increment(ManualAxis.Z, 0.2f, bounds)
        assertEquals(14f, changedZ.x)
        assertEquals(-3f, changedZ.y)
        for (axis in axes) {
            assertEquals(axis.baseline, transform.withValue(axis, axis.baseline, bounds).value(axis))
        }
    }

    @Test fun modeSwitchCancelsRelativeDelta() {
        val wheel = ManualFaderState(ManualAxis.Y, ManualFaderMode.Relative)
            .begin(25f, bounds).dragTo(1f)
        val switched = wheel.switchTo(ManualFaderMode.Value)
        assertFalse(switched.active)
        assertEquals(0f, switched.relativeDelta)
        assertEquals(25f, switched.release(25f, bounds).value)
    }

    @Test fun relativeClippingAndOppositeDirectionsOnAllAxes() {
        for (axis in axes) {
            val top = axis.range(bounds).endInclusive
            val bottom = axis.range(bounds).start
            val positive = ManualFaderState(axis, ManualFaderMode.Relative).begin(top, bounds)
                .dragTo(1f).release(top, bounds)
            val negative = ManualFaderState(axis, ManualFaderMode.Relative).begin(bottom, bounds)
                .dragTo(0f).release(bottom, bounds)
            assertEquals(top, positive.value)
            assertEquals(bottom, negative.value)
            assertFalse(positive.next.active)
            assertFalse(negative.next.active)
        }
    }

    @Test fun rapidSequentialWheelDragsNeverReplayThePreviousDelta() {
        val axis = ManualAxis.Y
        val first = ManualFaderState(axis, ManualFaderMode.Relative).begin(0f, bounds)
            .dragTo(1f).release(0f, bounds)
        assertEquals(80f, first.value)
        val second = first.next.begin(first.value, bounds).dragTo(0f).release(first.value, bounds)
        assertEquals(0f, second.value)
        assertEquals(0f, second.next.relativeDelta)
        assertFalse(second.next.release(second.value, bounds).committed)
    }

    @Test fun stateRecreationNeverReplaysAnUnreleasedWheel() {
        for (axis in axes) {
            val oldGesture = ManualFaderState(axis, ManualFaderMode.Relative)
                .begin(axis.baseline, bounds).dragTo(1f)
            assertTrue(oldGesture.active)
            // A new UI/session must not inherit an uncommitted spring-wheel gesture.
            val recreated = ManualFaderState(axis, ManualFaderMode.Relative)
            val result = recreated.release(axis.baseline, bounds)
            assertFalse(result.committed)
            assertEquals(axis.baseline, result.value)
            assertEquals(0f, result.next.relativeDelta)
        }
    }

    @Test fun relativeCommitUsesFreshCommittedValueAfterExternalGesture() {
        val wheel = ManualFaderState(ManualAxis.X, ManualFaderMode.Relative)
            .begin(10f, bounds).dragTo(0.75f)
        assertEquals(85f, wheel.release(45f, bounds).value) // +40 to the latest X
    }

    @Test fun rotationDoesNotChangePreviewAxisReferenceFrame() {
        val transform = ManualOverlayTransform(40f, -12f, 1.25f)
        // Rotation is intentionally NOT part of the axis model (no local-axis swap).
        val changed = transform.increment(ManualAxis.X, 10f, bounds)
        assertEquals(50f, changed.x)
        assertEquals(-12f, changed.y)
        assertEquals(1.25f, changed.z)
    }
}
