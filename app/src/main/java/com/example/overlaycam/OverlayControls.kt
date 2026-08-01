package com.example.overlaycam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
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
    val compact = state.mode == OverlayUiMode.EditOverlay || state.mode == OverlayUiMode.FixedOverlay
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
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
                    if (!compact || isBusy) {
                        Text(
                            text = busyLabel ?: supportingText ?: state.recommendedActionHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (compact) {
                    Button(enabled = !isBusy, onClick = onRecommendedAction) {
                        Text(state.recommendedActionLabel)
                    }
                } else {
                    TextButton(
                        enabled = !isBusy,
                        onClick = onOpenTools,
                    ) {
                        Text(state.expandActionLabel)
                    }
                }
            }

            if (compact) {
                TextButton(
                    enabled = !isBusy,
                    onClick = onOpenTools,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(state.expandActionLabel)
                }
            } else Row(
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
internal fun ComparisonControlBar(
    modifier: Modifier = Modifier,
    state: OverlayUiState,
    busyLabel: String?,
    mix: Float,
    variant: EffectVariant,
    availableVariants: List<EffectVariant>,
    summary: OverlayDiffSummary?,
    instruction: DrawingInstruction?,
    analysisUpdating: Boolean,
    enabled: Boolean,
    onMixChange: (Float) -> Unit,
    onVariantChange: (EffectVariant) -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val narrowHeader = LocalConfiguration.current.screenWidthDp < 420
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StagePill(state.stageLabel)
                Text(
                    text = state.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!narrowHeader) {
                    TextButton(enabled = enabled, onClick = onOpenTools) {
                        Text(state.expandActionLabel)
                    }
                    Button(enabled = enabled, onClick = onPrimaryAction) {
                        if (busyLabel != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(state.recommendedActionLabel)
                        }
                    }
                }
            }
            if (narrowHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(enabled = enabled, onClick = onOpenTools) {
                        Text(state.expandActionLabel)
                    }
                    Button(enabled = enabled, onClick = onPrimaryAction) {
                        if (busyLabel != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(state.recommendedActionLabel)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Compare", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Drawing ↔ ${variant.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    String.format(Locale.getDefault(), "%.0f%%", mix * 100f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                modifier = Modifier.semantics {
                    contentDescription = "Shift continuously between drawing and ${variant.label}"
                },
                enabled = enabled,
                value = mix,
                onValueChange = { onMixChange(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Drawing", style = MaterialTheme.typography.labelSmall)
                Text(variant.shortLabel, style = MaterialTheme.typography.labelSmall)
            }
            ActionFlow {
                availableVariants.forEach { option ->
                    FilterChip(
                        selected = variant == option,
                        enabled = enabled,
                        onClick = { onVariantChange(option) },
                        label = { Text(option.shortLabel) },
                    )
                }
            }

            if (variant.isGuidance) {
                if (summary != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComparisonMetric(
                            modifier = Modifier.weight(1f),
                            label = "Accuracy",
                            value = String.format(Locale.getDefault(), "%.0f%%", summary.matchRatio * 100f),
                        )
                        ComparisonMetric(
                            modifier = Modifier.weight(1f),
                            label = "Similarity",
                            value = String.format(Locale.getDefault(), "%.0f%%", summary.averageSimilarity * 100f),
                        )
                        ComparisonMetric(
                            modifier = Modifier.weight(1f),
                            label = "Contours",
                            value = if (summary.edgeComparedPixels == 0) "—" else
                                String.format(Locale.getDefault(), "%.0f%%", summary.edgeMatchRatio * 100f),
                        )
                    }
                }
                Text(
                    text = when {
                        analysisUpdating -> "Updating guide…"
                        instruction != null -> instruction.title
                        else -> "Guide unavailable"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!analysisUpdating && instruction != null) {
                    Text(
                        text = instruction.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun RepositionModeToggle(
    modifier: Modifier = Modifier,
    repositionEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val title = if (repositionEnabled) "Position unlocked" else "Position frozen"
    val action = if (repositionEnabled) "Freeze" else "Reposition"
    Surface(
        modifier = modifier
            .semantics {
                role = Role.Button
                stateDescription = title
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (repositionEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (repositionEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(action, style = MaterialTheme.typography.labelSmall)
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
        OverlayGestureOperation.MoveOverlay,
        OverlayGestureOperation.PanView -> MaterialTheme.colorScheme.primaryContainer
        OverlayGestureOperation.TransformOverlay,
        OverlayGestureOperation.ZoomView -> MaterialTheme.colorScheme.tertiaryContainer
        OverlayGestureOperation.Idle -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (operation) {
        OverlayGestureOperation.MoveOverlay,
        OverlayGestureOperation.PanView -> MaterialTheme.colorScheme.onPrimaryContainer
        OverlayGestureOperation.TransformOverlay,
        OverlayGestureOperation.ZoomView -> MaterialTheme.colorScheme.onTertiaryContainer
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
    repositionEnabled: Boolean,
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
            label = if (repositionEnabled) "Reposition mode" else "Inspection mode",
            text = if (repositionEnabled) {
                "One finger moves the overlay; two fingers resize and rotate. Freeze position with the floating control when alignment is ready."
            } else {
                "The overlay cannot move. One finger pans the drawing and two fingers zoom around the touch centroid."
            },
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
    onResetView: () -> Unit,
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
        OutlinedButton(enabled = enabled, onClick = onResetView) { Text("Reset view") }
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
    onResetView: () -> Unit,
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
        OutlinedButton(onClick = onResetView) { Text("Reset view") }
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

    if (variant.isGuidance) {
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
        Text("Guide palette", style = MaterialTheme.typography.labelLarge)
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
        Text(
            "Guides compare rendered pixels and contours; they support observation but are not an objective score of drawing quality.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
