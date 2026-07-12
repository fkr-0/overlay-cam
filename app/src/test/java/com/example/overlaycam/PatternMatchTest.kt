package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatternMatchTest {
    @Test
    fun findsKnownTranslationOnTexturedPatch() {
        val width = 36
        val height = 32
        val dx = 6
        val dy = -4
        val background = FloatArray(width * height) { index ->
            val x = index % width
            val y = index / width
            ((x * 17 + y * 29 + (x * y % 23) * 11) % 255).toFloat()
        }
        val overlay = FloatArray(width * height)
        val valid = BooleanArray(width * height)

        for (y in 10 until 22) {
            for (x in 9 until 23) {
                val overlayIndex = y * width + x
                val backgroundIndex = (y + dy) * width + (x + dx)
                overlay[overlayIndex] = background[backgroundIndex]
                valid[overlayIndex] = true
            }
        }

        val match = findBestLumaTranslation(
            backgroundLuma = background,
            overlayLuma = overlay,
            validMask = valid,
            width = width,
            height = height,
            radiusX = 10,
            radiusY = 10,
            stepPixels = 1,
            sampleStride = 1,
            minComparedSamples = 40,
        )

        assertNotNull(match)
        assertEquals(dx, match.dx)
        assertEquals(dy, match.dy)
        assertTrue(match.score > 0.99f)
    }

    @Test
    fun returnsNullWhenThereAreTooFewComparableSamples() {
        val background = FloatArray(16 * 16)
        val overlay = FloatArray(16 * 16)
        val valid = BooleanArray(16 * 16)
        valid[8 * 16 + 8] = true

        val match = findBestLumaTranslation(
            backgroundLuma = background,
            overlayLuma = overlay,
            validMask = valid,
            width = 16,
            height = 16,
            radiusX = 3,
            radiusY = 3,
            stepPixels = 1,
            sampleStride = 1,
            minComparedSamples = 5,
        )

        assertEquals(null, match)
    }

    @Test
    fun includesCenterCandidateWhenStepGridWouldSkipIt() {
        val width = 28
        val height = 28
        val background = FloatArray(width * height) { index ->
            val x = index % width
            val y = index / width
            ((x * 31 + y * 17 + (x * x + y * y) * 7) % 251).toFloat()
        }
        val overlay = FloatArray(width * height)
        val valid = BooleanArray(width * height)

        for (y in 8 until 20) {
            for (x in 8 until 20) {
                val index = y * width + x
                overlay[index] = background[index]
                valid[index] = true
            }
        }

        val match = findBestLumaTranslation(
            backgroundLuma = background,
            overlayLuma = overlay,
            validMask = valid,
            width = width,
            height = height,
            radiusX = 3,
            radiusY = 3,
            stepPixels = 2,
            sampleStride = 1,
            minComparedSamples = 40,
        )

        assertNotNull(match)
        assertEquals(0, match.dx)
        assertEquals(0, match.dy)
        assertTrue(match.score > 0.99f)
    }

    @Test
    fun prefersCenteredTranslationWhenScoresTie() {
        val width = 24
        val height = 24
        val background = FloatArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x + y) % 2 == 0) 32f else 224f
        }
        val overlay = FloatArray(width * height)
        val valid = BooleanArray(width * height)

        for (y in 6 until 18) {
            for (x in 6 until 18) {
                val index = y * width + x
                overlay[index] = background[index]
                valid[index] = true
            }
        }

        val match = findBestLumaTranslation(
            backgroundLuma = background,
            overlayLuma = overlay,
            validMask = valid,
            width = width,
            height = height,
            radiusX = 6,
            radiusY = 6,
            stepPixels = 2,
            sampleStride = 1,
            minComparedSamples = 40,
        )

        assertNotNull(match)
        assertEquals(0, match.dx)
        assertEquals(0, match.dy)
        assertTrue(match.score > 0.99f)
    }

}
