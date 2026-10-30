package com.example.overlaycam

import kotlin.math.max
import kotlin.math.ln
import kotlin.math.exp

/** Overlay coordinates are preview pixels, NOT pixels in the original bitmap.
 * Z is the overlay's uniform scale (not physical depth and not frozen View zoom).
 */
internal data class ManualOverlayTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 1f,
) {
    fun value(axis: ManualAxis): Float = when (axis) {
        ManualAxis.X -> x
        ManualAxis.Y -> y
        ManualAxis.Z -> z
    }

    fun withValue(axis: ManualAxis, proposed: Float, bounds: ManualFaderBounds): ManualOverlayTransform {
        val value = axis.clamp(proposed, bounds)
        return when (axis) {
            ManualAxis.X -> copy(x = value)
            ManualAxis.Y -> copy(y = value)
            ManualAxis.Z -> copy(z = value)
        }
    }

    fun increment(axis: ManualAxis, delta: Float, bounds: ManualFaderBounds): ManualOverlayTransform {
        if (!delta.isFinite()) return this
        return withValue(axis, value(axis) + delta, bounds)
    }
}

/** Axis offsets are limited to two full preview extents; preview rotation never swaps X/Y. */
internal data class ManualFaderBounds(val width: Float, val height: Float) {
    val xLimit: Float get() = max(256f, width.takeIf { it.isFinite() && it > 0f } ?: 256f)
        .times(2f).coerceAtMost(32768f)
    val yLimit: Float get() = max(256f, height.takeIf { it.isFinite() && it > 0f } ?: 256f)
        .times(2f).coerceAtMost(32768f)
}

internal enum class ManualAxis(val label: String, val unit: String) {
    X("X • horizontal overlay", "px"),
    Y("Y • vertical overlay", "px"),
    Z("Z • overlay size", "×");

    fun range(bounds: ManualFaderBounds): ClosedFloatingPointRange<Float> = when (this) {
        X -> -bounds.xLimit..bounds.xLimit
        Y -> -bounds.yLimit..bounds.yLimit
        Z -> 0.5f..2f
    }

    fun clamp(raw: Float, bounds: ManualFaderBounds): Float {
        val fallback = if (this == Z) 1f else 0f
        if (!raw.isFinite()) return fallback
        val range = range(bounds)
        return raw.coerceIn(range.start, range.endInclusive)
    }

    /** One full spring-wheel deflection, applied once at finger release. */
    val maxRelativeStep: Float get() = if (this == Z) 0.25f else 80f
    val baseline: Float get() = if (this == Z) 1f else 0f
}

internal enum class ManualFaderMode { Value, Relative }

/**
 * Small immutable gesture state machine. An inactive release is always a no-op.
 *
 * Value mode: the UI may render/apply previewValue during the drag; cancellation restores
 * startValue. Relative mode: previewValue is informational only, and release is the ONLY
 * point where its signed delta is added to the latest committed value.
 */
internal data class ManualFaderState(
    val axis: ManualAxis,
    val mode: ManualFaderMode = ManualFaderMode.Value,
    val active: Boolean = false,
    val startValue: Float = axis.baseline,
    val position: Float = 0.5f,
) {
    fun begin(committed: Float, bounds: ManualFaderBounds): ManualFaderState {
        val start = axis.clamp(committed, bounds)
        return copy(
            active = true,
            startValue = start,
            position = if (mode == ManualFaderMode.Value) axis.toPosition(start, bounds) else 0.5f,
        )
    }

    fun dragTo(rawPosition: Float): ManualFaderState {
        if (!active || !rawPosition.isFinite()) return this
        return copy(position = rawPosition.coerceIn(0f, 1f))
    }

    val relativeDelta: Float
        get() = if (mode == ManualFaderMode.Relative && active) {
            ((position - 0.5f) * 2f) * axis.maxRelativeStep
        } else 0f

    fun previewValue(committed: Float, bounds: ManualFaderBounds): Float {
        if (!active) return axis.clamp(committed, bounds)
        return when (mode) {
            ManualFaderMode.Value -> axis.fromPosition(position, bounds)
            ManualFaderMode.Relative -> axis.clamp(committed + relativeDelta, bounds)
        }
    }

    /** A commit token is consumed by returning an inactive state. */
    fun release(committed: Float, bounds: ManualFaderBounds): ManualFaderResult =
        if (!active) ManualFaderResult(axis.clamp(committed, bounds), this, false)
        else ManualFaderResult(previewValue(committed, bounds), reset(), true)

    fun cancel(committed: Float, bounds: ManualFaderBounds): ManualFaderResult =
        ManualFaderResult(
            when {
                !active -> axis.clamp(committed, bounds)
                mode == ManualFaderMode.Value -> startValue
                else -> axis.clamp(committed, bounds)
            },
            reset(),
            false,
        )

    fun switchTo(newMode: ManualFaderMode): ManualFaderState =
        copy(mode = newMode, active = false, position = 0.5f)

    private fun reset() = copy(active = false, position = 0.5f)
}

internal data class ManualFaderResult(
    val value: Float,
    val next: ManualFaderState,
    val committed: Boolean,
)

internal fun ManualAxis.toPosition(value: Float, bounds: ManualFaderBounds): Float {
    val range = range(bounds)
    return ((clamp(value, bounds) - range.start) / (range.endInclusive - range.start))
        .coerceIn(0f, 1f)
}

internal fun ManualAxis.fromPosition(position: Float, bounds: ManualFaderBounds): Float {
    val range = range(bounds)
    if (!position.isFinite()) return baseline
    return clamp(range.start + position.coerceIn(0f, 1f) *
        (range.endInclusive - range.start), bounds)
}

internal const val MIN_FROZEN_VIEW_SCALE = 0.5f
internal const val MAX_FROZEN_VIEW_SCALE = 20f

internal fun boundedFrozenViewScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_FROZEN_VIEW_SCALE, MAX_FROZEN_VIEW_SCALE) else 1f

/** Log-scale zoom slider: preserves fine control around the frequently used 1×–6× range. */
internal fun frozenZoomPosition(scale: Float): Float =
    (ln(boundedFrozenViewScale(scale) / MIN_FROZEN_VIEW_SCALE) /
        ln(MAX_FROZEN_VIEW_SCALE / MIN_FROZEN_VIEW_SCALE)).coerceIn(0f, 1f)

internal fun frozenZoomScaleAt(position: Float): Float =
    if (!position.isFinite()) 1f else boundedFrozenViewScale(
        MIN_FROZEN_VIEW_SCALE * exp(
            ln(MAX_FROZEN_VIEW_SCALE / MIN_FROZEN_VIEW_SCALE) * position.coerceIn(0f, 1f)
        )
    )
