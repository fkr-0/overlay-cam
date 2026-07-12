package com.example.overlaycam

import kotlin.math.sqrt

internal data class LumaTranslationMatch(
    val dx: Int,
    val dy: Int,
    val score: Float,
    val comparedSamples: Int,
)

/**
 * Finds the translation that makes an already-rendered overlay luminance plane
 * best match a background luminance plane.
 *
 * The score is zero-mean normalized cross-correlation, so global exposure
 * differences hurt less than with raw pixel differences. The overlay plane and
 * validMask use the current overlay position; the returned dx/dy is the delta
 * that should be added to that position.
 */
internal fun findBestLumaTranslation(
    backgroundLuma: FloatArray,
    overlayLuma: FloatArray,
    validMask: BooleanArray,
    width: Int,
    height: Int,
    centerDx: Int = 0,
    centerDy: Int = 0,
    radiusX: Int,
    radiusY: Int,
    stepPixels: Int,
    sampleStride: Int,
    minComparedSamples: Int,
): LumaTranslationMatch? {
    require(width > 0 && height > 0)
    require(backgroundLuma.size == width * height)
    require(overlayLuma.size == width * height)
    require(validMask.size == width * height)
    require(stepPixels > 0)
    require(sampleStride > 0)

    var best: LumaTranslationMatch? = null
    val dxCandidates = translationCandidates(centerDx, radiusX, stepPixels)
    val dyCandidates = translationCandidates(centerDy, radiusY, stepPixels)

    for (dy in dyCandidates) {
        for (dx in dxCandidates) {
            val score = scoreTranslation(
                backgroundLuma = backgroundLuma,
                overlayLuma = overlayLuma,
                validMask = validMask,
                width = width,
                height = height,
                dx = dx,
                dy = dy,
                sampleStride = sampleStride,
                minComparedSamples = minComparedSamples,
            )
            if (score != null && isBetterMatch(score, best, centerDx, centerDy)) {
                best = score
            }
        }
    }

    return best
}

private fun translationCandidates(center: Int, radius: Int, stepPixels: Int): List<Int> {
    require(radius >= 0)

    val values = mutableListOf<Int>()
    var candidate = center - radius
    val max = center + radius
    while (candidate <= max) {
        values.add(candidate)
        candidate += stepPixels
    }
    values.add(center)
    return values.distinct().sorted()
}

private fun isBetterMatch(
    candidate: LumaTranslationMatch,
    currentBest: LumaTranslationMatch?,
    centerDx: Int,
    centerDy: Int,
): Boolean {
    if (currentBest == null) return true

    val scoreDelta = candidate.score - currentBest.score
    if (scoreDelta > 1e-6f) return true
    if (scoreDelta < -1e-6f) return false

    val candidateDistance = squaredDistance(candidate.dx, candidate.dy, centerDx, centerDy)
    val bestDistance = squaredDistance(currentBest.dx, currentBest.dy, centerDx, centerDy)
    if (candidateDistance != bestDistance) return candidateDistance < bestDistance

    return candidate.comparedSamples > currentBest.comparedSamples
}

private fun squaredDistance(dx: Int, dy: Int, centerDx: Int, centerDy: Int): Int {
    val x = dx - centerDx
    val y = dy - centerDy
    return x * x + y * y
}

private fun scoreTranslation(
    backgroundLuma: FloatArray,
    overlayLuma: FloatArray,
    validMask: BooleanArray,
    width: Int,
    height: Int,
    dx: Int,
    dy: Int,
    sampleStride: Int,
    minComparedSamples: Int,
): LumaTranslationMatch? {
    var count = 0
    var sumBg = 0.0
    var sumOverlay = 0.0
    var sumBgSq = 0.0
    var sumOverlaySq = 0.0
    var sumCross = 0.0

    var y = 0
    while (y < height) {
        val targetY = y + dy
        if (targetY in 0 until height) {
            var x = 0
            val overlayRow = y * width
            val backgroundRow = targetY * width
            while (x < width) {
                val overlayIndex = overlayRow + x
                val targetX = x + dx
                if (targetX in 0 until width && validMask[overlayIndex]) {
                    val ov = overlayLuma[overlayIndex].toDouble()
                    val bg = backgroundLuma[backgroundRow + targetX].toDouble()
                    sumOverlay += ov
                    sumBg += bg
                    sumOverlaySq += ov * ov
                    sumBgSq += bg * bg
                    sumCross += ov * bg
                    count += 1
                }
                x += sampleStride
            }
        }
        y += sampleStride
    }

    if (count < minComparedSamples) return null

    val numerator = sumCross - (sumOverlay * sumBg / count)
    val overlayVariance = sumOverlaySq - (sumOverlay * sumOverlay / count)
    val backgroundVariance = sumBgSq - (sumBg * sumBg / count)
    val denominator = sqrt(overlayVariance * backgroundVariance)
    if (denominator <= 1e-6) return null

    return LumaTranslationMatch(
        dx = dx,
        dy = dy,
        score = (numerator / denominator).toFloat().coerceIn(-1f, 1f),
        comparedSamples = count,
    )
}
