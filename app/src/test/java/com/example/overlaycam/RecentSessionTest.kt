package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentSessionTest {
    @Test fun committedAlignmentIsRetainedWhenThePairIsRevisited() {
        val alignment = RecentAlignment(1.4f, 33f, 120f, -45f, 0.4f)
        val stored = RecentScene("file:///frame.jpg", "content://overlay/1", "Aligned", 10L, alignment)
        val next = recentScenesWith(listOf(RecentScene("file:///frame.jpg", "content://overlay/1")), stored)
        assertEquals(1, next.size)
        assertEquals(alignment, next.first().alignment)
    }

    @Test fun malformedAlignmentComponentsCannotPoisonTheEditor() {
        val safe = RecentAlignment(Float.NaN, Float.POSITIVE_INFINITY, 10000000f, Float.NaN, -10f).normalized()
        assertEquals(1f, safe.scale)
        assertEquals(0f, safe.rotation)
        assertEquals(32768f, safe.offsetX)
        assertEquals(0f, safe.offsetY)
        assertEquals(0f, safe.blend)
    }

    @Test fun latestPairMovesFirstAndIsDeduplicated() {
        val a = RecentScene("file:///camera-a.jpg", null, "Camera A", 1L)
        val b = RecentScene("content://photos/background-b", "content://photos/overlay-b", "B", 2L)
        val updated = recentScenesWith(listOf(a, b), b.copy(lastUsedAt = 3L))
        assertEquals(2, updated.size)
        assertEquals(b.backgroundUri, updated.first().backgroundUri)
        assertEquals(3L, updated.first().lastUsedAt)
    }

    @Test fun aBackgroundWithDifferentOverlaysProducesDistinctRecoverablePairs() {
        val first = RecentScene("file:///freeze.jpg", "content://overlay/one", "Pair 1")
        val second = first.copy(overlayUri = "content://overlay/two", label = "Pair 2")
        val entries = recentScenesWith(listOf(first), second)
        assertEquals(listOf(second, first), entries)
    }

    @Test fun blankUriIsExcludedAndBoundApplies() {
        val old = (1..30).map { RecentScene("file:///frame_$it.jpg") }
        val result = recentScenesWith(old, RecentScene("file:///latest.jpg"))
        assertEquals(12, result.size)
        assertEquals("file:///latest.jpg", result.first().backgroundUri)
        assertFalse(result.any { it.backgroundUri.isBlank() })
        assertTrue(recentScenesWith(emptyList(), RecentScene("")).isEmpty())
    }
}
