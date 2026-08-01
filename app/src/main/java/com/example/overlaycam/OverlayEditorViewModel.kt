package com.example.overlaycam

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

internal class OverlayEditorViewModel : ViewModel() {
    val backgroundUri = mutableStateOf<Uri?>(null)
    val backgroundBitmap = mutableStateOf<Bitmap?>(null)
    val overlayUri = mutableStateOf<Uri?>(null)
    val overlayBitmap = mutableStateOf<Bitmap?>(null)

    val opacity = mutableFloatStateOf(0.7f)
    val scale = mutableFloatStateOf(1f)
    val rotationDegrees = mutableFloatStateOf(0f)
    val offsetX = mutableFloatStateOf(0f)
    val offsetY = mutableFloatStateOf(0f)

    val pointFixMode = mutableStateOf(false)
    val pointLocks = mutableStateListOf<FixedPointLock>()
    val pendingPointSource = mutableStateOf<Offset?>(null)
    val pendingPointScreenPosition = mutableStateOf<Offset?>(null)
    val pointFixZoom = mutableFloatStateOf(1f)

    val repositionEnabled = mutableStateOf(true)
    val viewportScale = mutableFloatStateOf(1f)
    val viewportOffsetX = mutableFloatStateOf(0f)
    val viewportOffsetY = mutableFloatStateOf(0f)

    val overlayFixedMode = mutableStateOf(false)
    val lockedLayerA = mutableStateOf<OverlayLayer?>(null)
    val lockedLayerB = mutableStateOf<OverlayLayer?>(null)
    val hideAllOverlays = mutableStateOf(false)
    val addOverlayPending = mutableStateOf(false)
    val flipOverlayAndBackground = mutableStateOf(false)
    val frozenOverlayOpacity = mutableFloatStateOf(0.5f)

    val variantIndex = mutableIntStateOf(0)
    val heatmapSimilarityThreshold = mutableFloatStateOf(0.85f)
    val heatmapPalette = mutableStateOf(HeatmapPalette.Color)

    override fun onCleared() {
        listOfNotNull(
            backgroundBitmap.value,
            overlayBitmap.value,
            lockedLayerA.value?.bitmap,
            lockedLayerB.value?.bitmap,
        ).distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
