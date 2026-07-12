package com.example.overlaycam

internal data class PixelCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal fun centerCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): PixelCropRect {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }

    val targetAspect = targetWidth.toDouble() / targetHeight
    val sourceAspect = sourceWidth.toDouble() / sourceHeight

    val cropWidth: Int
    val cropHeight: Int
    if (sourceAspect > targetAspect) {
        cropHeight = sourceHeight
        cropWidth = (cropHeight * targetAspect).toInt().coerceIn(1, sourceWidth)
    } else {
        cropWidth = sourceWidth
        cropHeight = (cropWidth / targetAspect).toInt().coerceIn(1, sourceHeight)
    }

    return PixelCropRect(
        left = (sourceWidth - cropWidth) / 2,
        top = (sourceHeight - cropHeight) / 2,
        width = cropWidth,
        height = cropHeight,
    )
}

internal fun bitmapSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maxEdge: Int,
): Int {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    require(maxEdge > 0) { "Maximum edge must be positive" }

    var sampleSize = 1
    while (maxOf(sourceWidth / sampleSize, sourceHeight / sampleSize) > maxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
