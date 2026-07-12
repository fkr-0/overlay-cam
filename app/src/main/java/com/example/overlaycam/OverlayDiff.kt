package com.example.overlaycam

internal data class OverlayDiffBounds(
    val left: Int,
    val top: Int,
    val rightExclusive: Int,
    val bottomExclusive: Int,
)

internal data class OverlayDiffSummary(
    val comparedPixels: Int,
    val matchingPixels: Int,
    val differentPixels: Int,
    val averageSimilarity: Float,
    val changedBounds: OverlayDiffBounds?,
) {
    val matchRatio: Float = if (comparedPixels == 0) 0f else matchingPixels.toFloat() / comparedPixels
}

internal enum class DrawingInstructionKind {
    NoOverlay,
    LooksAligned,
    RefineHighlightedArea,
}

internal data class DrawingInstruction(
    val kind: DrawingInstructionKind,
    val title: String,
    val detail: String,
    val bounds: OverlayDiffBounds?,
)

internal data class OverlayDiffAnalysis(
    val heatmapPixels: IntArray,
    val summary: OverlayDiffSummary,
    val instruction: DrawingInstruction,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverlayDiffAnalysis) return false
        return heatmapPixels.contentEquals(other.heatmapPixels) &&
            summary == other.summary &&
            instruction == other.instruction
    }

    override fun hashCode(): Int {
        var result = heatmapPixels.contentHashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + instruction.hashCode()
        return result
    }
}

internal fun analyzeOverlayDiff(
    backgroundPixels: IntArray,
    overlayPixels: IntArray,
    width: Int,
    height: Int,
    similarityThreshold: Float,
    palette: HeatmapPalette,
): OverlayDiffAnalysis {
    require(width > 0 && height > 0)
    require(backgroundPixels.size == width * height)
    require(overlayPixels.size == width * height)

    val threshold = similarityThreshold.coerceIn(0.5f, 0.99f)
    val outPixels = IntArray(width * height)

    var compared = 0
    var matching = 0
    var different = 0
    var similaritySum = 0f
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for (i in overlayPixels.indices) {
        val ov = overlayPixels[i]
        val overlayAlpha = alpha(ov)
        if (overlayAlpha < 8) {
            outPixels[i] = opaqueRgb(0, 0, 0)
            continue
        }

        val similarity = pixelSimilarity(backgroundPixels[i], ov).coerceIn(0f, 1f)
        val isMatch = similarity >= threshold
        compared += 1
        similaritySum += similarity
        if (isMatch) {
            matching += 1
        } else {
            different += 1
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        outPixels[i] = heatmapColor(similarity, threshold, palette)
    }

    val bounds = if (different == 0) {
        null
    } else {
        OverlayDiffBounds(
            left = minX,
            top = minY,
            rightExclusive = maxX + 1,
            bottomExclusive = maxY + 1,
        )
    }
    val summary = OverlayDiffSummary(
        comparedPixels = compared,
        matchingPixels = matching,
        differentPixels = different,
        averageSimilarity = if (compared == 0) 0f else similaritySum / compared,
        changedBounds = bounds,
    )

    return OverlayDiffAnalysis(
        heatmapPixels = outPixels,
        summary = summary,
        instruction = drawingInstructionFor(summary),
    )
}

private fun drawingInstructionFor(summary: OverlayDiffSummary): DrawingInstruction {
    return when {
        summary.comparedPixels == 0 -> DrawingInstruction(
            kind = DrawingInstructionKind.NoOverlay,
            title = "Add an overlay",
            detail = "No visible overlay pixels are available for comparison.",
            bounds = null,
        )
        summary.differentPixels == 0 -> DrawingInstruction(
            kind = DrawingInstructionKind.LooksAligned,
            title = "Overlay looks aligned",
            detail = "All compared overlay pixels are within the selected similarity threshold.",
            bounds = null,
        )
        else -> DrawingInstruction(
            kind = DrawingInstructionKind.RefineHighlightedArea,
            title = "Refine highlighted area",
            detail = "${summary.differentPixels} of ${summary.comparedPixels} compared pixels differ. Adjust the marked bounds or add point-fix anchors.",
            bounds = summary.changedBounds,
        )
    }
}

private fun pixelSimilarity(background: Int, overlay: Int): Float {
    val dr = kotlin.math.abs(red(background) - red(overlay))
    val dg = kotlin.math.abs(green(background) - green(overlay))
    val db = kotlin.math.abs(blue(background) - blue(overlay))
    return 1f - ((dr + dg + db) / (3f * 255f))
}

private fun heatmapColor(similarity: Float, threshold: Float, palette: HeatmapPalette): Int {
    if (palette == HeatmapPalette.Mono) {
        return if (similarity >= threshold) opaqueRgb(255, 255, 255) else opaqueRgb(0, 0, 0)
    }

    if (similarity >= threshold) return opaqueRgb(0, 255, 90)

    val t = (similarity / threshold).coerceIn(0f, 1f)
    val hue = 55f * t
    val value = (0.45f + 0.55f * t).coerceIn(0f, 1f)
    return hsvToOpaqueRgb(hue, 1f, value)
}

private fun hsvToOpaqueRgb(hue: Float, saturation: Float, value: Float): Int {
    val chroma = value * saturation
    val huePrime = (hue / 60f) % 6f
    val x = chroma * (1f - kotlin.math.abs(huePrime % 2f - 1f))
    val (r1, g1, b1) = when {
        huePrime < 1f -> Triple(chroma, x, 0f)
        huePrime < 2f -> Triple(x, chroma, 0f)
        huePrime < 3f -> Triple(0f, chroma, x)
        huePrime < 4f -> Triple(0f, x, chroma)
        huePrime < 5f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = value - chroma
    return opaqueRgb(
        ((r1 + m) * 255f).toInt().coerceIn(0, 255),
        ((g1 + m) * 255f).toInt().coerceIn(0, 255),
        ((b1 + m) * 255f).toInt().coerceIn(0, 255),
    )
}

private fun alpha(pixel: Int): Int = pixel ushr 24 and 0xFF
private fun red(pixel: Int): Int = pixel ushr 16 and 0xFF
private fun green(pixel: Int): Int = pixel ushr 8 and 0xFF
private fun blue(pixel: Int): Int = pixel and 0xFF

private fun opaqueRgb(r: Int, g: Int, b: Int): Int =
    (255 shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
