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
    val edgeComparedPixels: Int = 0,
    val matchingEdgePixels: Int = 0,
    val missingEdgePixels: Int = 0,
    val extraEdgePixels: Int = 0,
) {
    val matchRatio: Float = if (comparedPixels == 0) 0f else matchingPixels.toFloat() / comparedPixels
    val edgeMatchRatio: Float =
        if (edgeComparedPixels == 0) 0f else matchingEdgePixels.toFloat() / edgeComparedPixels
}

internal enum class DrawingInstructionKind {
    NoOverlay,
    LooksAligned,
    RefineSimilarity,
    RefineValues,
    RefineEdges,
}

internal data class DrawingInstruction(
    val kind: DrawingInstructionKind,
    val title: String,
    val detail: String,
    val bounds: OverlayDiffBounds?,
)

internal data class OverlayDiffAnalysis(
    val guidancePixels: IntArray,
    val summary: OverlayDiffSummary,
    val instruction: DrawingInstruction,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverlayDiffAnalysis) return false
        return guidancePixels.contentEquals(other.guidancePixels) &&
            summary == other.summary &&
            instruction == other.instruction
    }

    override fun hashCode(): Int {
        var result = guidancePixels.contentHashCode()
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
    visualization: GuidanceVisualization = GuidanceVisualization.Similarity,
): OverlayDiffAnalysis {
    require(width > 0 && height > 0)
    require(backgroundPixels.size == width * height)
    require(overlayPixels.size == width * height)

    val threshold = similarityThreshold.coerceIn(0.5f, 0.99f)
    val outPixels = IntArray(width * height)
    val backgroundLuma = FloatArray(width * height)
    val overlayLuma = FloatArray(width * height)
    val overlayAlpha = IntArray(width * height)

    var compared = 0
    var matching = 0
    var different = 0
    var similaritySum = 0f
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for (i in overlayPixels.indices) {
        backgroundLuma[i] = pixelLuma(backgroundPixels[i])
        overlayLuma[i] = pixelLuma(overlayPixels[i])
        overlayAlpha[i] = alpha(overlayPixels[i])
    }

    for (i in overlayPixels.indices) {
        val ov = overlayPixels[i]
        val alpha = overlayAlpha[i]
        if (alpha < 8) {
            outPixels[i] = 0x00000000
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

        outPixels[i] = when (visualization) {
            GuidanceVisualization.Similarity -> similarityColor(similarity, threshold, palette)
            GuidanceVisualization.Difference -> differenceColor(similarity, threshold, palette)
            GuidanceVisualization.Edges -> 0x00000000
        }
    }

    var edgeCompared = 0
    var matchingEdges = 0
    var missingEdges = 0
    var extraEdges = 0
    var edgeMinX = width
    var edgeMinY = height
    var edgeMaxX = -1
    var edgeMaxY = -1

    for (i in overlayPixels.indices) {
        if (overlayAlpha[i] < 8) continue
        val x = i % width
        val y = i / width
        val backgroundEdge = gradientAt(backgroundLuma, width, x, y) >= 28f
        val overlayEdge = gradientAt(overlayLuma, width, x, y) >= 28f ||
            isVisibleBoundary(overlayAlpha, width, height, x, y)
        if (!backgroundEdge && !overlayEdge) continue

        edgeCompared += 1
        when {
            backgroundEdge && overlayEdge -> matchingEdges += 1
            backgroundEdge -> missingEdges += 1
            else -> extraEdges += 1
        }

        if (backgroundEdge != overlayEdge) {
            if (x < edgeMinX) edgeMinX = x
            if (y < edgeMinY) edgeMinY = y
            if (x > edgeMaxX) edgeMaxX = x
            if (y > edgeMaxY) edgeMaxY = y
        }

        if (visualization == GuidanceVisualization.Edges) {
            outPixels[i] = edgeColor(backgroundEdge, overlayEdge, palette)
        }
    }

    val pixelBounds = if (different == 0) {
        null
    } else {
        OverlayDiffBounds(
            left = minX,
            top = minY,
            rightExclusive = maxX + 1,
            bottomExclusive = maxY + 1,
        )
    }
    val edgeBounds = if (missingEdges + extraEdges == 0) {
        null
    } else {
        OverlayDiffBounds(
            left = edgeMinX,
            top = edgeMinY,
            rightExclusive = edgeMaxX + 1,
            bottomExclusive = edgeMaxY + 1,
        )
    }
    val bounds = if (visualization == GuidanceVisualization.Edges) edgeBounds else pixelBounds
    val summary = OverlayDiffSummary(
        comparedPixels = compared,
        matchingPixels = matching,
        differentPixels = different,
        averageSimilarity = if (compared == 0) 0f else similaritySum / compared,
        changedBounds = bounds,
        edgeComparedPixels = edgeCompared,
        matchingEdgePixels = matchingEdges,
        missingEdgePixels = missingEdges,
        extraEdgePixels = extraEdges,
    )

    return OverlayDiffAnalysis(
        guidancePixels = outPixels,
        summary = summary,
        instruction = drawingInstructionFor(summary, visualization),
    )
}

private fun drawingInstructionFor(
    summary: OverlayDiffSummary,
    visualization: GuidanceVisualization,
): DrawingInstruction {
    val accuracy = (summary.matchRatio * 100f).toInt().coerceIn(0, 100)
    val similarity = (summary.averageSimilarity * 100f).toInt().coerceIn(0, 100)
    val edgeMatch = (summary.edgeMatchRatio * 100f).toInt().coerceIn(0, 100)
    return when {
        summary.comparedPixels == 0 -> DrawingInstruction(
            kind = DrawingInstructionKind.NoOverlay,
            title = "Add an overlay",
            detail = "No visible overlay pixels are available for comparison.",
            bounds = null,
        )
        summary.differentPixels == 0 && visualization != GuidanceVisualization.Edges -> DrawingInstruction(
            kind = DrawingInstructionKind.LooksAligned,
            title = "Overlay looks aligned",
            detail = "All compared overlay pixels are within the selected similarity threshold.",
            bounds = null,
        )
        visualization == GuidanceVisualization.Similarity -> DrawingInstruction(
            kind = DrawingInstructionKind.RefineSimilarity,
            title = "Refine warm-colored areas",
            detail = "Accuracy $accuracy% · average similarity $similarity%. Green areas match; yellow and red areas need more attention.",
            bounds = summary.changedBounds,
        )
        visualization == GuidanceVisualization.Difference -> DrawingInstruction(
            kind = DrawingInstructionKind.RefineValues,
            title = "Correct the brightest value errors",
            detail = "Average similarity is $similarity%. Bright warm marks show the largest lightness and color differences; quiet areas are already close.",
            bounds = summary.changedBounds,
        )
        summary.edgeComparedPixels == 0 -> DrawingInstruction(
            kind = DrawingInstructionKind.RefineEdges,
            title = "No strong contours detected",
            detail = "Increase contrast or use the similarity guide to compare broad shapes and values.",
            bounds = null,
        )
        summary.missingEdgePixels + summary.extraEdgePixels == 0 -> DrawingInstruction(
            kind = DrawingInstructionKind.LooksAligned,
            title = "Contours look aligned",
            detail = "All detected contour pixels overlap at the current view.",
            bounds = null,
        )
        else -> DrawingInstruction(
            kind = DrawingInstructionKind.RefineEdges,
            title = "Correct contour placement",
            detail = "Contour match $edgeMatch%. Cyan marks missing lines, magenta marks extra lines, and green marks aligned contours.",
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

private fun similarityColor(similarity: Float, threshold: Float, palette: HeatmapPalette): Int {
    if (palette == HeatmapPalette.Mono) {
        return if (similarity >= threshold) argb(190, 255, 255, 255) else argb(210, 0, 0, 0)
    }

    if (similarity >= threshold) return argb(185, 0, 230, 100)

    val t = (similarity / threshold).coerceIn(0f, 1f)
    val hue = 55f * t
    val value = (0.45f + 0.55f * t).coerceIn(0f, 1f)
    return hsvToArgb(215, hue, 1f, value)
}

private fun differenceColor(similarity: Float, threshold: Float, palette: HeatmapPalette): Int {
    val error = (1f - similarity).coerceIn(0f, 1f)
    val thresholdError = 1f - threshold
    if (error <= thresholdError) {
        return if (palette == HeatmapPalette.Mono) argb(65, 255, 255, 255) else argb(60, 0, 220, 100)
    }
    val intensity = ((error - thresholdError) / (1f - thresholdError)).coerceIn(0f, 1f)
    return if (palette == HeatmapPalette.Mono) {
        val value = (80f + intensity * 175f).toInt()
        argb(220, value, value, value)
    } else {
        val hue = 48f * (1f - intensity)
        hsvToArgb(225, hue, 1f, 0.65f + intensity * 0.35f)
    }
}

private fun edgeColor(backgroundEdge: Boolean, overlayEdge: Boolean, palette: HeatmapPalette): Int {
    if (palette == HeatmapPalette.Mono) {
        return when {
            backgroundEdge && overlayEdge -> argb(230, 255, 255, 255)
            else -> argb(230, 60, 60, 60)
        }
    }
    return when {
        backgroundEdge && overlayEdge -> argb(230, 20, 235, 120)
        backgroundEdge -> argb(230, 0, 210, 255)
        else -> argb(230, 255, 40, 210)
    }
}

private fun gradientAt(luma: FloatArray, width: Int, x: Int, y: Int): Float {
    val index = y * width + x
    val left = if (x > 0) luma[index - 1] else luma[index]
    val top = if (y > 0) luma[index - width] else luma[index]
    return kotlin.math.abs(luma[index] - left) + kotlin.math.abs(luma[index] - top)
}

private fun isVisibleBoundary(alpha: IntArray, width: Int, height: Int, x: Int, y: Int): Boolean {
    if (alpha[y * width + x] < 8) return false
    return (x > 0 && alpha[y * width + x - 1] < 8) ||
        (x + 1 < width && alpha[y * width + x + 1] < 8) ||
        (y > 0 && alpha[(y - 1) * width + x] < 8) ||
        (y + 1 < height && alpha[(y + 1) * width + x] < 8)
}

private fun pixelLuma(pixel: Int): Float =
    0.299f * red(pixel) + 0.587f * green(pixel) + 0.114f * blue(pixel)

private fun hsvToArgb(alpha: Int, hue: Float, saturation: Float, value: Float): Int {
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
    return argb(
        alpha,
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
    argb(255, r, g, b)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a.coerceIn(0, 255) shl 24) or
        (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or
        b.coerceIn(0, 255)
