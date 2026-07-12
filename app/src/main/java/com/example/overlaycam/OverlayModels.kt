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

internal enum class EffectVariant(val label: String) {
    Normal("Normal"),
    BackgroundInverted("BG Inverted"),
    OverlayInverted("Overlay Inverted"),
    Heatmap("Heatmap"),
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
