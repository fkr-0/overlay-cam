package com.example.overlaycam

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset

internal data class OverlayLayer(
    val bitmap: Bitmap,
    val opacity: Float,
    val scale: Float,
    val rotation: Float,
    val offsetX: Float,
    val offsetY: Float,
    val fixedPoints: List<FixedPointLock>,
)

internal data class FixedPointLock(
    val source: Offset,
    val target: Offset,
)

internal enum class GuidanceVisualization {
    Similarity,
    Difference,
    Edges,
}

internal enum class EffectVariant(
    val label: String,
    val shortLabel: String,
    val guidanceVisualization: GuidanceVisualization? = null,
    val showInComparisonBar: Boolean = true,
) {
    Normal("Overlay", "Overlay"),
    Similarity("Similarity", "Match", GuidanceVisualization.Similarity),
    Difference("Value difference", "Values", GuidanceVisualization.Difference),
    Edges("Contour alignment", "Edges", GuidanceVisualization.Edges),
    BackgroundInverted("Background inverted", "BG invert", showInComparisonBar = false),
    OverlayInverted("Overlay inverted", "Overlay invert", showInComparisonBar = false),
    ;

    val isGuidance: Boolean
        get() = guidanceVisualization != null
}

internal enum class HeatmapPalette(val label: String) {
    Color("Green / Red-Yellow"),
    Mono("Black / White"),
}

internal enum class PickerTarget {
    Overlay,
    Background,
}

internal data class AutoAlignResult(
    val dx: Float,
    val dy: Float,
    val score: Float,
    val comparedSamples: Int,
)
