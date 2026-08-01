package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageGeometryTest {
    @Test
    fun centerCropRemovesEqualMarginsFromWideSource() {
        assertEquals(
            PixelCropRect(left = 500, top = 0, width = 1000, height = 1000),
            centerCropRect(
                sourceWidth = 2000,
                sourceHeight = 1000,
                targetWidth = 1,
                targetHeight = 1,
            ),
        )
    }

    @Test
    fun centerCropRemovesEqualMarginsFromTallSource() {
        assertEquals(
            PixelCropRect(left = 0, top = 500, width = 1000, height = 1000),
            centerCropRect(
                sourceWidth = 1000,
                sourceHeight = 2000,
                targetWidth = 1,
                targetHeight = 1,
            ),
        )
    }

    @Test
    fun centerCropKeepsMatchingAspectRatio() {
        assertEquals(
            PixelCropRect(left = 0, top = 0, width = 1920, height = 1080),
            centerCropRect(
                sourceWidth = 1920,
                sourceHeight = 1080,
                targetWidth = 1280,
                targetHeight = 720,
            ),
        )
    }

    @Test
    fun centerCropRejectsInvalidDimensions() {
        assertFailsWith<IllegalArgumentException> {
            centerCropRect(0, 1080, 1280, 720)
        }
        assertFailsWith<IllegalArgumentException> {
            centerCropRect(1920, 1080, 0, 720)
        }
    }

    @Test
    fun bitmapSamplingUsesPowerOfTwoReduction() {
        assertEquals(1, bitmapSampleSize(1920, 1080, 4096))
        assertEquals(2, bitmapSampleSize(6000, 4000, 4096))
        assertEquals(4, bitmapSampleSize(12000, 8000, 4096))
        assertEquals(4, bitmapSampleSize(6000, 4000, 2048))
    }

    @Test
    fun overlayLandmarksMustBeInsideTheBitmap() {
        assertTrue(isPixelInside(width = 100, height = 50, x = 0f, y = 0f))
        assertTrue(isPixelInside(width = 100, height = 50, x = 99.9f, y = 49.9f))
        assertFalse(isPixelInside(width = 100, height = 50, x = -0.1f, y = 20f))
        assertFalse(isPixelInside(width = 100, height = 50, x = 100f, y = 20f))
        assertFalse(isPixelInside(width = 100, height = 50, x = 20f, y = 50f))
    }

    @Test
    fun guidanceRenderingPreservesAspectRatioWithinItsPerformanceBudget() {
        assertEquals(
            androidx.compose.ui.unit.IntSize(720, 360),
            guidanceRenderSize(androidx.compose.ui.unit.IntSize(1440, 720)),
        )
        assertEquals(
            androidx.compose.ui.unit.IntSize(360, 720),
            guidanceRenderSize(androidx.compose.ui.unit.IntSize(720, 1440)),
        )
        assertEquals(
            androidx.compose.ui.unit.IntSize(640, 480),
            guidanceRenderSize(androidx.compose.ui.unit.IntSize(640, 480)),
        )
    }
}
