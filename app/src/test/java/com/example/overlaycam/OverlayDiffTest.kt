package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverlayDiffTest {
    @Test
    fun summarizesMatchingAndDifferentOverlayPixelsWithChangedBounds() {
        val width = 3
        val height = 2
        val background = intArrayOf(
            rgb(20, 20, 20), rgb(100, 100, 100), rgb(200, 200, 200),
            rgb(50, 50, 50), rgb(90, 90, 90), rgb(160, 160, 160),
        )
        val overlay = intArrayOf(
            transparent(), rgb(100, 100, 100), rgb(20, 20, 20),
            transparent(), rgb(220, 220, 220), transparent(),
        )

        val analysis = analyzeOverlayDiff(
            backgroundPixels = background,
            overlayPixels = overlay,
            width = width,
            height = height,
            similarityThreshold = 0.9f,
            palette = HeatmapPalette.Mono,
        )

        assertEquals(3, analysis.summary.comparedPixels)
        assertEquals(1, analysis.summary.matchingPixels)
        assertEquals(2, analysis.summary.differentPixels)
        assertEquals(OverlayDiffBounds(left = 1, top = 0, rightExclusive = 3, bottomExclusive = 2), analysis.summary.changedBounds)
        assertEquals(DrawingInstructionKind.RefineSimilarity, analysis.instruction.kind)
    }

    @Test
    fun reportsAlignedInstructionWhenEveryComparedPixelMatches() {
        val background = intArrayOf(rgb(1, 2, 3), rgb(40, 50, 60))
        val overlay = intArrayOf(rgb(1, 2, 3), rgb(41, 51, 61))

        val analysis = analyzeOverlayDiff(
            backgroundPixels = background,
            overlayPixels = overlay,
            width = 2,
            height = 1,
            similarityThreshold = 0.98f,
            palette = HeatmapPalette.Color,
        )

        assertEquals(2, analysis.summary.comparedPixels)
        assertEquals(2, analysis.summary.matchingPixels)
        assertEquals(0, analysis.summary.differentPixels)
        assertEquals(null, analysis.summary.changedBounds)
        assertEquals(DrawingInstructionKind.LooksAligned, analysis.instruction.kind)
        assertTrue(analysis.summary.matchRatio > 0.99f)
    }

    @Test
    fun reportsNoOverlayInstructionWhenOverlayHasNoVisiblePixels() {
        val analysis = analyzeOverlayDiff(
            backgroundPixels = intArrayOf(rgb(1, 2, 3)),
            overlayPixels = intArrayOf(transparent()),
            width = 1,
            height = 1,
            similarityThreshold = 0.9f,
            palette = HeatmapPalette.Color,
        )

        assertEquals(0, analysis.summary.comparedPixels)
        assertEquals(null, analysis.summary.changedBounds)
        assertEquals(DrawingInstructionKind.NoOverlay, analysis.instruction.kind)
    }

    @Test
    fun producesHeatmapPixelsForNoOverlayMatchAndDifference() {
        val analysis = analyzeOverlayDiff(
            backgroundPixels = intArrayOf(rgb(10, 10, 10), rgb(100, 100, 100), rgb(200, 200, 200)),
            overlayPixels = intArrayOf(transparent(), rgb(100, 100, 100), rgb(10, 10, 10)),
            width = 3,
            height = 1,
            similarityThreshold = 0.9f,
            palette = HeatmapPalette.Mono,
        )

        assertEquals(0x00000000, analysis.guidancePixels[0])
        assertTrue(analysis.guidancePixels[1] ushr 24 > 0)
        assertTrue(analysis.guidancePixels[2] ushr 24 > 0)
        assertNotNull(analysis.summary.changedBounds)
    }

    @Test
    fun edgeGuideSeparatesMissingExtraAndMatchingContours() {
        val background = intArrayOf(
            rgb(0, 0, 0), rgb(255, 255, 255), rgb(255, 255, 255),
            rgb(0, 0, 0), rgb(255, 255, 255), rgb(0, 0, 0),
        )
        val overlay = intArrayOf(
            rgb(0, 0, 0), rgb(255, 255, 255), rgb(0, 0, 0),
            rgb(0, 0, 0), rgb(255, 255, 255), rgb(255, 255, 255),
        )

        val analysis = analyzeOverlayDiff(
            backgroundPixels = background,
            overlayPixels = overlay,
            width = 3,
            height = 2,
            similarityThreshold = 0.9f,
            palette = HeatmapPalette.Color,
            visualization = GuidanceVisualization.Edges,
        )

        assertTrue(analysis.summary.edgeComparedPixels > 0)
        assertTrue(analysis.summary.matchingEdgePixels > 0)
        assertTrue(analysis.summary.missingEdgePixels + analysis.summary.extraEdgePixels > 0)
        assertEquals(DrawingInstructionKind.RefineEdges, analysis.instruction.kind)
    }

    private fun transparent(): Int = 0x00000000

    private fun rgb(r: Int, g: Int, b: Int): Int = opaqueRgb(r, g, b)

    private fun opaqueRgb(r: Int, g: Int, b: Int): Int =
        (255 shl 24) or (r shl 16) or (g shl 8) or b
}
