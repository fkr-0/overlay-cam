package com.example.overlaycam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun OverlayControlDock(
    modifier: Modifier = Modifier,
    state: OverlayUiState,
    busyLabel: String?,
    supportingText: String? = null,
    viewScale: Float,
    showViewFader: Boolean,
    onViewScaleChange: (Float) -> Unit,
    onResetView: () -> Unit,
    onRecommendedAction: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val isBusy = busyLabel != null
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StagePill(state.stageLabel)
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = busyLabel.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = supportingText ?: state.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TextButton(enabled = !isBusy, onClick = onOpenTools) {
                    Text(state.expandActionLabel)
                }
                Button(enabled = !isBusy, onClick = onRecommendedAction) {
                    Text(state.compactActionLabel)
                }
            }

            if (showViewFader && !isBusy) {
                CompactFaderRow(
                    label = "Frozen View",
                    valueLabel = String.format(Locale.getDefault(), "%.2f×", viewScale),
                    value = frozenZoomPosition(viewScale),
                    valueRange = 0f..1f,
                    contentDescription = "Frozen View zoom, inspection only, 0.5 to 20 times",
                    onValueChange = { onViewScaleChange(frozenZoomScaleAt(it)) },
                    resetLabel = "1×",
                    onReset = onResetView,
                )
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
    viewScale: Float,
    overlayScale: Float,
    variant: EffectVariant,
    availableVariants: List<EffectVariant>,
    enabled: Boolean,
    onMixChange: (Float) -> Unit,
    onViewScaleChange: (Float) -> Unit,
    onResetView: () -> Unit,
    onOverlayScaleChange: (Float) -> Unit,
    onResetOverlayScale: () -> Unit,
    onVariantChange: (EffectVariant) -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenTools: () -> Unit,
) {
    var showSizeFader by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRepositionEnabled, state.mode) {
        if (!state.isRepositionEnabled || state.mode != OverlayUiMode.EditOverlay) {
            showSizeFader = false
        }
    }
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    enabled = enabled && availableVariants.isNotEmpty(),
                    onClick = {
                        if (availableVariants.isNotEmpty()) {
                            val current = availableVariants.indexOf(variant).coerceAtLeast(0)
                            onVariantChange(availableVariants[(current + 1) % availableVariants.size])
                        }
                    },
                ) {
                    Text(variant.shortLabel)
                }
                Text(
                    text = if (state.isRepositionEnabled) "Move overlay" else "Inspect",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (state.mode == OverlayUiMode.EditOverlay && state.isRepositionEnabled) {
                    TextButton(
                        enabled = enabled,
                        onClick = { showSizeFader = !showSizeFader },
                    ) { Text(if (showSizeFader) "Size ✓" else "Size") }
                }
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
                        Text(state.compactActionLabel)
                    }
                }
            }

            CompactFaderRow(
                label = "Blend",
                valueLabel = String.format(Locale.getDefault(), "%.0f%%", mix * 100f),
                value = mix,
                valueRange = 0f..1f,
                enabled = enabled,
                contentDescription = "Overlay blend",
                onValueChange = { onMixChange(it.coerceIn(0f, 1f)) },
            )
            CompactFaderRow(
                label = "Frozen View",
                valueLabel = String.format(Locale.getDefault(), "%.2f×", viewScale),
                value = frozenZoomPosition(viewScale),
                valueRange = 0f..1f,
                enabled = enabled,
                contentDescription = "Frozen View zoom, inspection only, 0.5 to 20 times",
                onValueChange = { onViewScaleChange(frozenZoomScaleAt(it)) },
                resetLabel = "1×",
                onReset = onResetView,
            )
            // The optional overlay size fader is a direct action, not a
            // nearly full-screen inspector. View and overlay zoom stay separate.
            if (showSizeFader && state.mode == OverlayUiMode.EditOverlay && state.isRepositionEnabled) {
                CompactFaderRow(
                    label = "Overlay",
                    valueLabel = String.format(Locale.getDefault(), "%.2f×", overlayScale),
                    value = overlayScale.coerceIn(0.5f, 2f),
                    valueRange = 0.5f..2f,
                    enabled = enabled,
                    contentDescription = "Overlay size, changes the aligned image, 0.5 to 2 times",
                    onValueChange = { onOverlayScaleChange(it.coerceIn(0.5f, 2f)) },
                    resetLabel = "1×",
                    onReset = onResetOverlayScale,
                )
            }
        }
    }
}

@Composable
private fun CompactFaderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    contentDescription: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 38.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            modifier = Modifier
                .weight(1f)
                .semantics { this.contentDescription = contentDescription },
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
        Text(
            text = valueLabel,
            modifier = Modifier.widthIn(min = 40.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (resetLabel != null && onReset != null) {
            TextButton(enabled = enabled, onClick = onReset) {
                Text(resetLabel)
            }
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
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .semantics {
                role = Role.Button
                stateDescription = title
            }
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
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
    // Keep the camera alignment area visible: advanced tools are secondary.
    // Partial expansion and scrolling preserve access on small phones.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp

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
        Text(state.compactActionLabel)
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
    manualTransform: ManualOverlayTransform? = null,
    manualBounds: ManualFaderBounds = ManualFaderBounds(256f, 256f),
    onManualValue: (ManualAxis, Float) -> Unit = { _, _ -> },
) {
    var showManual by remember { mutableStateOf(false) }
    Text("Fine adjustments", style = MaterialTheme.typography.titleSmall)

    AdjustmentSlider(
        label = "Overlay size",
        valueLabel = String.format(Locale.getDefault(), "%.2f×", scale),
        value = scale,
        valueRange = minScale..maxScale,
        enabled = enabled,
        onValueChange = { onScaleChange(it.coerceIn(minScale, maxScale)) },
    )
    AdjustmentSlider(
        label = "Overlay rotation",
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
        if (manualTransform != null) {
            OutlinedButton(enabled = enabled, onClick = { showManual = !showManual }) {
                Text(if (showManual) "Hide manual adjust" else "Manual adjust XYZ")
            }
        }
    }

    if (showManual && manualTransform != null) {
        ManualAdjustPanel(enabled, manualTransform, manualBounds, onManualValue)
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


/**
 * Edit-overlay Manual adjust, separate from the frozen View zoom.
 *
 * Slider thumb interactions have explicit end/cancel callbacks. Material3 Slider's
 * onValueChangeFinished cannot distinguish release from pointer cancellation, so
 * this custom track is used for relative wheels to guarantee commit-once.
 */
@Composable
internal fun ManualAdjustPanel(
    enabled: Boolean,
    transform: ManualOverlayTransform,
    bounds: ManualFaderBounds,
    onAxisValue: (ManualAxis, Float) -> Unit,
) {
    Text("Manual adjust • image overlay", style = MaterialTheme.typography.titleSmall)
    Text(
        "X/Y are preview pixels (screen axes); Z is overlay size. " +
            "Frozen View zoom is separate and does not change the saved image.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ManualAxis.entries.forEach { axis ->
        ManualAxisControl(
            axis = axis,
            committed = transform.value(axis),
            bounds = bounds,
            enabled = enabled,
            onValue = { onAxisValue(axis, it) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualAxisControl(
    axis: ManualAxis,
    committed: Float,
    bounds: ManualFaderBounds,
    enabled: Boolean,
    onValue: (Float) -> Unit,
) {
    var mode by remember(axis) { mutableStateOf(ManualFaderMode.Value) }
    var fader by remember(axis) { mutableStateOf(ManualFaderState(axis)) }
    val latestCommitted by rememberUpdatedState(committed)
    val latestOnValue by rememberUpdatedState(onValue)
    val label = if (axis == ManualAxis.Z) {
        String.format(Locale.getDefault(), "%.2f×", committed)
    } else {
        String.format(Locale.getDefault(), "%+.0f px", committed)
    }
    val deltaLabel = if (axis == ManualAxis.Z) {
        String.format(Locale.getDefault(), "%+.3f×", fader.relativeDelta)
    } else {
        String.format(Locale.getDefault(), "%+.1f px", fader.relativeDelta)
    }

    fun cancel() {
        if (!fader.active) return
        val result = fader.cancel(latestCommitted, bounds)
        fader = result.next
        if (mode == ManualFaderMode.Value) latestOnValue(result.value)
    }
    fun start() { fader = fader.copy(mode = mode).begin(latestCommitted, bounds) }
    fun move(position: Float) {
        if (!fader.active) return
        fader = fader.dragTo(position)
        if (mode == ManualFaderMode.Value) latestOnValue(fader.previewValue(latestCommitted, bounds))
    }
    fun finish() {
        if (!fader.active) return
        val result = fader.release(latestCommitted, bounds)
        fader = result.next // consume the release token before calling back
        if (result.committed) latestOnValue(result.value)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = axis.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = if (mode == ManualFaderMode.Relative) label + "   Δ " + deltaLabel else label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            FilterChip(
                selected = mode == ManualFaderMode.Value,
                enabled = enabled,
                onClick = {
                    cancel()
                    mode = ManualFaderMode.Value
                    fader = fader.switchTo(mode)
                },
                label = { Text("Value") },
            )
            FilterChip(
                selected = mode == ManualFaderMode.Relative,
                enabled = enabled,
                onClick = {
                    cancel()
                    mode = ManualFaderMode.Relative
                    fader = fader.switchTo(mode)
                },
                label = { Text("Step") },
            )
            TextButton(enabled = enabled, onClick = {
                cancel()
                latestOnValue(axis.baseline)
            }) {
                Text(if (axis == ManualAxis.Z) "1×" else "0")
            }
        }
        Text(
            text = if (mode == ManualFaderMode.Relative) "Wheel springs to zero; release applies a single step"
            else "Wheel holds the absolute value on release",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val position = if (fader.active) fader.position
            else if (mode == ManualFaderMode.Value) axis.toPosition(committed, bounds)
            else 0.5f
        val primary = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.onSurfaceVariant
        val description = axis.label + ", " + mode.name + ", " + label +
            (if (mode == ManualFaderMode.Relative) ", pending " + deltaLabel else "")
        val startCallback by rememberUpdatedState(::start)
        val moveCallback by rememberUpdatedState(::move)
        val finishCallback by rememberUpdatedState(::finish)
        val cancelCallback by rememberUpdatedState(::cancel)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics {
                    contentDescription = description
                    stateDescription = if (mode == ManualFaderMode.Relative) {
                        "Relative signed increment, committed on release"
                    } else "Absolute value, retained on release"
                    progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                    setProgress { target ->
                        if (!enabled) false
                        else {
                            startCallback()
                            moveCallback(target)
                            finishCallback()
                            true
                        }
                    }
                }
                .pointerInput(enabled, axis, mode, bounds) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        var finished = false
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            startCallback()
                            down.consume() // keep horizontal adjustment independent of sheet scrolling
                            val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                            moveCallback(down.position.x / trackWidth)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1) break // multi-touch cancels, not commits
                                if (change.isConsumed) break // parent scroll / gesture cancellation
                                moveCallback(change.position.x / trackWidth)
                                if (!change.pressed) {
                                    finished = true
                                    finishCallback()
                                    break
                                }
                                change.consume()
                            }
                        } finally {
                            if (!finished) cancelCallback()
                        }
                    }
                },
        ) {
            val centerY = size.height / 2f
            val margin = 11.dp.toPx()
            val from = Offset(margin, centerY)
            val to = Offset(size.width - margin, centerY)
            drawLine(track, from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            if (mode == ManualFaderMode.Relative) {
                val centerX = size.width / 2f
                drawLine(primary.copy(alpha = 0.6f),
                    Offset(centerX, centerY - 10.dp.toPx()),
                    Offset(centerX, centerY + 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx())
            }
            val x = margin + (size.width - margin * 2f) * position.coerceIn(0f, 1f)
            drawCircle(primary, radius = 10.dp.toPx(), center = Offset(x, centerY))
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
