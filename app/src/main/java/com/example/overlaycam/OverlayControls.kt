package com.example.overlaycam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun OverlayControlDock(
    modifier: Modifier = Modifier,
    state: OverlayUiState,
    busyLabel: String?,
    supportingText: String? = null,
    onRecommendedAction: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val isBusy = busyLabel != null
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StagePill(state.stageLabel)
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = busyLabel ?: supportingText ?: state.recommendedActionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    enabled = !isBusy,
                    onClick = onOpenTools,
                ) {
                    Text(state.expandActionLabel)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    enabled = !isBusy,
                    onClick = onRecommendedAction,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(busyLabel.orEmpty())
                    } else {
                        Text(state.recommendedActionLabel)
                    }
                }

            }
        }
    }
}

@Composable
internal fun PersistentOpacityFader(
    modifier: Modifier = Modifier,
    opacity: Float,
    enabled: Boolean,
    onOpacityChange: (Float) -> Unit,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Overlay opacity", style = MaterialTheme.typography.labelLarge)
                Text(
                    String.format(Locale.getDefault(), "%.0f%%", opacity * 100f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                modifier = Modifier.semantics {
                    contentDescription = "Overlay opacity, permanently available"
                },
                enabled = enabled,
                value = opacity,
                onValueChange = { onOpacityChange(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
internal fun GestureOperationFeedback(
    operation: OverlayGestureOperation,
    modifier: Modifier = Modifier,
) {
    if (operation == OverlayGestureOperation.Idle) return

    val containerColor = when (operation) {
        OverlayGestureOperation.Move -> MaterialTheme.colorScheme.primaryContainer
        OverlayGestureOperation.Transform -> MaterialTheme.colorScheme.tertiaryContainer
        OverlayGestureOperation.Idle -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (operation) {
        OverlayGestureOperation.Move -> MaterialTheme.colorScheme.onPrimaryContainer
        OverlayGestureOperation.Transform -> MaterialTheme.colorScheme.onTertiaryContainer
        OverlayGestureOperation.Idle -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(operation.symbol, style = MaterialTheme.typography.titleLarge)
            Column {
                Text(operation.label, style = MaterialTheme.typography.titleSmall)
                Text(operation.instruction, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayToolsSheet(
    state: OverlayUiState,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StagePill(state.stageLabel)
                Text(
                    text = state.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                TextButton(onClick = onDismissRequest) {
                    Text("Done")
                }
            }
            Text(
                text = state.guidance,
                modifier = Modifier.widthIn(max = 560.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StagePill(label: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun WorkflowActionPanel(
    state: OverlayUiState,
    enabled: Boolean,
    onRecommendedAction: () -> Unit,
    onEnableCamera: () -> Unit,
    onPickBackground: () -> Unit,
    onPickOverlay: () -> Unit,
    onResetOverlay: () -> Unit,
    onReturnToLiveCamera: () -> Unit,
    onAdjustAgain: () -> Unit,
) {
    Button(
        enabled = enabled,
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
        WorkflowCallout(
            label = "Direct manipulation",
            text = "One finger moves the overlay. Two fingers resize and rotate it. A border and status badge show which operation is active.",
        )
    }

    when (state.mode) {
        OverlayUiMode.Setup -> ActionFlow {
            if (state.cameraAvailable) {
                OutlinedButton(enabled = enabled, onClick = onPickBackground) {
                    Text("Use background photo")
                }
            } else if (state.cameraSupported) {
                OutlinedButton(enabled = enabled, onClick = onEnableCamera) {
                    Text("Enable live camera")
                }
            }
        }

        OverlayUiMode.BackgroundReady -> ActionFlow {
            if (!state.cameraAvailable && state.cameraSupported) {
                OutlinedButton(enabled = enabled, onClick = onEnableCamera) {
                    Text("Enable live camera")
                }
            }
            OutlinedButton(enabled = enabled, onClick = onPickBackground) { Text("Change background") }
            if (state.cameraAvailable) {
                OutlinedButton(enabled = enabled, onClick = onReturnToLiveCamera) { Text("Live camera") }
            }
        }

        OverlayUiMode.EditOverlay -> ActionFlow {
            OutlinedButton(enabled = enabled, onClick = onResetOverlay) { Text("Reset overlay") }
            OutlinedButton(enabled = enabled, onClick = onPickOverlay) { Text("Change overlay") }
            OutlinedButton(enabled = enabled, onClick = onPickBackground) { Text("Change background") }
        }

        OverlayUiMode.PointFix -> Unit

        OverlayUiMode.FixedOverlay -> ActionFlow {
            OutlinedButton(enabled = enabled, onClick = onAdjustAgain) { Text("Adjust again") }
        }
    }
}

@Composable
internal fun EditOverlayPanel(
    enabled: Boolean,
    scale: Float,
    minScale: Float,
    maxScale: Float,
    rotationDeg: Float,
    canAutoAlign: Boolean,
    autoAlignInProgress: Boolean,
    autoAlignStatus: String?,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onReset: () -> Unit,
    onAutoAlign: () -> Unit,
    onStartPointFix: () -> Unit,
) {
    Text("Fine adjustments", style = MaterialTheme.typography.titleSmall)

    AdjustmentSlider(
        label = "Size",
        valueLabel = String.format(Locale.getDefault(), "%.2f×", scale),
        value = scale,
        valueRange = minScale..maxScale,
        enabled = enabled,
        onValueChange = { onScaleChange(it.coerceIn(minScale, maxScale)) },
    )
    AdjustmentSlider(
        label = "Rotation",
        valueLabel = String.format(Locale.getDefault(), "%.0f°", rotationDeg),
        value = rotationDeg.coerceIn(-180f, 180f),
        valueRange = -180f..180f,
        enabled = enabled,
        onValueChange = { onRotationChange(it.coerceIn(-180f, 180f)) },
    )

    ActionFlow {
        OutlinedButton(enabled = enabled, onClick = onReset) { Text("Reset") }
        OutlinedButton(
            enabled = enabled && canAutoAlign && !autoAlignInProgress,
            onClick = onAutoAlign,
        ) { Text(if (autoAlignInProgress) "Matching…" else "Auto align") }
        OutlinedButton(enabled = enabled, onClick = onStartPointFix) { Text("Match points") }
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
    enabled: Boolean = true,
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
        modifier = Modifier.semantics { contentDescription = label },
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
    )
}

@Composable
internal fun PointFixPanel(
    state: PointMatchUiState,
    onClearPoints: () -> Unit,
    onResetZoom: () -> Unit,
) {
    WorkflowCallout(
        label = state.title,
        text = state.instruction,
    )
    Text(
        "Landmark pairs: ${state.completedPointCount} of 2",
        style = MaterialTheme.typography.bodySmall,
    )
    ActionFlow {
        OutlinedButton(onClick = onClearPoints) { Text("Clear points") }
        OutlinedButton(onClick = onResetZoom) { Text("Reset zoom") }
    }
}

@Composable
internal fun FixedOverlayActionsPanel(
    canAddOverlay: Boolean,
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
    onEffectChange: (EffectVariant) -> Unit,
    onFrozenOverlayOpacityChange: (Float) -> Unit,
    onHeatmapSimilarityThresholdChange: (Float) -> Unit,
    onHeatmapPaletteChange: (HeatmapPalette) -> Unit,
) {
    Text("Display and comparison", style = MaterialTheme.typography.titleSmall)

    Text("Display mode", style = MaterialTheme.typography.labelLarge)
    ActionFlow {
        EffectVariant.entries.forEach { option ->
            FilterChip(
                selected = variant == option,
                onClick = { onEffectChange(option) },
                label = { Text(option.label) },
            )
        }
    }

    ActionFlow {
        OutlinedButton(onClick = onToggleHide) {
            Text(if (hideAllOverlays) "Show overlay" else "Hide overlay")
        }
        OutlinedButton(enabled = canAddOverlay, onClick = onAddOverlay) {
            Text(if (canAddOverlay) "Add another overlay" else "Two overlays added")
        }
        OutlinedButton(onClick = onToggleFlip) {
            Text(if (flipOverlayAndBackground) "Background below" else "Background on top")
        }
    }

    if (flipOverlayAndBackground) {
        AdjustmentSlider(
            label = "Background opacity",
            valueLabel = String.format(Locale.getDefault(), "%.0f%%", frozenOverlayOpacity * 100f),
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
            valueLabel = String.format(Locale.getDefault(), "%.2f", heatmapSimilarityThreshold),
            value = heatmapSimilarityThreshold,
            valueRange = 0.5f..0.99f,
            onValueChange = { onHeatmapSimilarityThresholdChange(it.coerceIn(0.5f, 0.99f)) },
        )
        Text("Heatmap palette", style = MaterialTheme.typography.labelLarge)
        ActionFlow {
            FilterChip(
                selected = heatmapPalette == HeatmapPalette.Color,
                onClick = { onHeatmapPaletteChange(HeatmapPalette.Color) },
                label = { Text("Color") },
            )
            FilterChip(
                selected = heatmapPalette == HeatmapPalette.Mono,
                onClick = { onHeatmapPaletteChange(HeatmapPalette.Mono) },
                label = { Text("Mono") },
            )
        }
    }
}

@Composable
internal fun WorkflowCallout(
    label: String,
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = text,
                modifier = Modifier.widthIn(max = 560.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
