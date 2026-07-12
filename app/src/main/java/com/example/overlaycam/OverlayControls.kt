package com.example.overlaycam

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun OverlayControlSheet(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    stageLabel: String,
    title: String,
    guidance: String,
    expandActionLabel: String,
    collapsedContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50),
                    )
            )

            var dragTotal = 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(expanded) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                            onDragEnd = {
                                if (dragTotal < -48f) onExpandedChange(true)
                                if (dragTotal > 48f) onExpandedChange(false)
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onExpandedChange(!expanded) },
                    label = { Text(stageLabel) },
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(if (expanded) "Close" else expandActionLabel)
                }
            }

            if (expanded) {
                Text(
                    text = guidance,
                    modifier = Modifier.widthIn(max = 520.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                content()
            } else {
                collapsedContent()
            }
        }
    }
}

@Composable
internal fun WorkflowActionPanel(
    state: OverlayUiState,
    expanded: Boolean,
    onRecommendedAction: () -> Unit,
    onFreezeCamera: () -> Unit,
    onPickBackground: () -> Unit,
    onPickOverlay: () -> Unit,
    onResetOverlay: () -> Unit,
    onReturnToLiveCamera: () -> Unit,
    onAdjustAgain: () -> Unit,
) {
    Button(
        onClick = onRecommendedAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(state.recommendedActionLabel)
    }

    Text(
        text = state.recommendedActionHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.showGestureHint) {
        Text(
            text = "Drag to move  •  Pinch to resize  •  Twist to rotate",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    when (state.mode) {
        OverlayUiMode.Setup -> ActionRow {
            OutlinedButton(onClick = onPickBackground) { Text("Use background photo") }
        }

        OverlayUiMode.BackgroundReady -> ActionRow(fill = true, alignCenter = true) {
            OutlinedButton(onClick = onFreezeCamera) { Text("Refreeze") }
            OutlinedButton(onClick = onPickBackground) { Text("Change background") }
            if (expanded) {
                OutlinedButton(onClick = onReturnToLiveCamera) { Text("Live camera") }
            }
        }

        OverlayUiMode.EditOverlay -> {
            if (state.showManagementActions(expanded)) {
                ActionRow(fill = true, alignCenter = true) {
                    OutlinedButton(onClick = onResetOverlay) { Text("Reset overlay") }
                    OutlinedButton(onClick = onPickOverlay) { Text("Change overlay") }
                    OutlinedButton(onClick = onPickBackground) { Text("Change background") }
                }
            }
        }

        OverlayUiMode.PointFix -> Unit

        OverlayUiMode.FixedOverlay -> ActionRow {
            OutlinedButton(onClick = onAdjustAgain) { Text("Adjust again") }
        }
    }
}

@Composable
internal fun QuickOpacityControl(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    AdjustmentSlider(
        label = "Overlay opacity",
        valueLabel = String.format("%.0f%%", opacity * 100f),
        value = opacity,
        valueRange = 0f..1f,
        onValueChange = { onOpacityChange(it.coerceIn(0f, 1f)) },
    )
}

@Composable
internal fun EditOverlayPanel(
    scale: Float,
    minScale: Float,
    maxScale: Float,
    rotationDeg: Float,
    opacity: Float,
    canAutoAlign: Boolean,
    autoAlignInProgress: Boolean,
    autoAlignStatus: String?,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: () -> Unit,
    onAutoAlign: () -> Unit,
    onStartPointFix: () -> Unit,
) {
    Text("Fine adjustments", style = MaterialTheme.typography.titleSmall)

    AdjustmentSlider(
        label = "Opacity",
        valueLabel = String.format("%.0f%%", opacity * 100f),
        value = opacity,
        valueRange = 0f..1f,
        onValueChange = { onOpacityChange(it.coerceIn(0f, 1f)) },
    )
    AdjustmentSlider(
        label = "Size",
        valueLabel = String.format("%.2f×", scale),
        value = scale,
        valueRange = minScale..maxScale,
        onValueChange = { onScaleChange(it.coerceIn(minScale, maxScale)) },
    )
    AdjustmentSlider(
        label = "Rotation",
        valueLabel = String.format("%.0f°", rotationDeg),
        value = rotationDeg.coerceIn(-180f, 180f),
        valueRange = -180f..180f,
        onValueChange = { onRotationChange(it.coerceIn(-180f, 180f)) },
    )

    ActionRow {
        OutlinedButton(onClick = onReset) { Text("Reset") }
        OutlinedButton(
            enabled = canAutoAlign && !autoAlignInProgress,
            onClick = onAutoAlign,
        ) { Text(if (autoAlignInProgress) "Matching…" else "Auto align") }
        OutlinedButton(onClick = onStartPointFix) { Text("Match points") }
    }

    autoAlignStatus?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
    )
}

@Composable
internal fun PointFixPanel(
    pointCount: Int,
    onClearPoints: () -> Unit,
    onResetZoom: () -> Unit,
) {
    WorkflowCallout(
        label = "Matching landmarks",
        text = "Tap one or two recognizable points. One point fixes position; two also correct size and rotation.",
    )
    Text("Points placed: $pointCount of 2", style = MaterialTheme.typography.bodySmall)
    ActionRow {
        OutlinedButton(onClick = onClearPoints) { Text("Clear points") }
        OutlinedButton(onClick = onResetZoom) { Text("Reset zoom") }
    }
}

@Composable
internal fun FixedOverlayActionsPanel(
    hideAllOverlays: Boolean,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    variant: EffectVariant,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette,
    drawingInstruction: DrawingInstruction?,
    onToggleHide: () -> Unit,
    onAddOverlay: () -> Unit,
    onToggleFlip: () -> Unit,
    onNextEffect: () -> Unit,
    onFrozenOverlayOpacityChange: (Float) -> Unit,
    onHeatmapSimilarityThresholdChange: (Float) -> Unit,
    onHeatmapPaletteChange: (HeatmapPalette) -> Unit,
) {
    Text("More options", style = MaterialTheme.typography.titleSmall)
    ActionRow(fill = true, alignCenter = true) {
        OutlinedButton(onClick = onToggleHide) {
            Text(if (hideAllOverlays) "Show overlay" else "Hide overlay")
        }
        OutlinedButton(onClick = onAddOverlay) { Text("Add another overlay") }
        OutlinedButton(onClick = onToggleFlip) {
            Text(if (flipOverlayAndBackground) "Background on top" else "Put background on top")
        }
        OutlinedButton(onClick = onNextEffect) { Text("Next effect") }
    }

    if (flipOverlayAndBackground) {
        AdjustmentSlider(
            label = "Background opacity",
            valueLabel = String.format("%.0f%%", frozenOverlayOpacity * 100f),
            value = frozenOverlayOpacity,
            valueRange = 0f..1f,
            onValueChange = { onFrozenOverlayOpacityChange(it.coerceIn(0f, 1f)) },
        )
    }

    if (variant == EffectVariant.Heatmap) {
        drawingInstruction?.let { instruction ->
            WorkflowCallout(
                label = instruction.title,
                text = instruction.detail,
            )
        }
        AdjustmentSlider(
            label = "Similarity threshold",
            valueLabel = String.format("%.2f", heatmapSimilarityThreshold),
            value = heatmapSimilarityThreshold,
            valueRange = 0.5f..0.99f,
            onValueChange = { onHeatmapSimilarityThresholdChange(it.coerceIn(0.5f, 0.99f)) },
        )
        ActionRow {
            OutlinedButton(onClick = { onHeatmapPaletteChange(HeatmapPalette.Color) }) {
                Text(if (heatmapPalette == HeatmapPalette.Color) "Color ✓" else "Color")
            }
            OutlinedButton(onClick = { onHeatmapPaletteChange(HeatmapPalette.Mono) }) {
                Text(if (heatmapPalette == HeatmapPalette.Mono) "Mono ✓" else "Mono")
            }
        }
    }
}

@Composable
private fun WorkflowCallout(
    label: String,
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = text,
                modifier = Modifier.widthIn(max = 520.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionRow(
    fill: Boolean = false,
    alignCenter: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = if (alignCenter) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
