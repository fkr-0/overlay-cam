package com.example.overlaycam

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    CameraPermissionGate {
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

        var backgroundUri by remember { mutableStateOf<Uri?>(null) }
        var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var backgroundImage by remember { mutableStateOf<ImageBitmap?>(null) }

        var overlayUri by remember { mutableStateOf<Uri?>(null) }
        var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var backgroundLoadInProgress by remember { mutableStateOf(false) }
        var overlayLoadInProgress by remember { mutableStateOf(false) }
        var saveInProgress by remember { mutableStateOf(false) }

        val minScale = 0.5f
        val maxScale = 2.0f
        var opacity by remember { mutableFloatStateOf(0.7f) }
        var scaleF by remember { mutableFloatStateOf(1f) }
        var rotationDeg by remember { mutableFloatStateOf(0f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        var pointFixMode by remember { mutableStateOf(false) }
        val pointLocks = remember { mutableStateListOf<FixedPointLock>() }
        var selectedPointLockIndex by remember { mutableIntStateOf(-1) }
        var pointFixZoom by remember { mutableFloatStateOf(1f) }

        var overlayFixedMode by remember { mutableStateOf(false) }
        var lockedLayerA by remember { mutableStateOf<OverlayLayer?>(null) }
        var lockedLayerB by remember { mutableStateOf<OverlayLayer?>(null) }
        var hideAllOverlays by remember { mutableStateOf(false) }
        var hideLayerAUntilSecondLock by remember { mutableStateOf(false) }
        var addOverlayPending by remember { mutableStateOf(false) }
        var flipOverlayAndBackground by remember { mutableStateOf(false) }
        var frozenOverlayOpacity by remember { mutableFloatStateOf(0.5f) }

        var variantIndex by remember { mutableIntStateOf(0) }
        val variants = EffectVariant.entries
        val variant = variants[variantIndex]

        var heatmapBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var heatmapImage by remember { mutableStateOf<ImageBitmap?>(null) }
        var heatmapInstruction by remember { mutableStateOf<DrawingInstruction?>(null) }
        var heatmapSimilarityThreshold by remember { mutableFloatStateOf(0.85f) }
        var heatmapPalette by remember { mutableStateOf(HeatmapPalette.Color) }

        var autoAlignInProgress by remember { mutableStateOf(false) }
        var autoAlignStatus by remember { mutableStateOf<String?>(null) }

        var pickerTarget by remember { mutableStateOf(PickerTarget.Overlay) }
        fun resetWorkingOverlayTransform(clearPoints: Boolean = true) {
            scaleF = 1f
            rotationDeg = 0f
            offsetX = 0f
            offsetY = 0f
            opacity = 0.7f
            if (clearPoints) {
                pointLocks.clear()
                selectedPointLockIndex = -1
            }
            pointFixZoom = 1f
        }

        fun currentWorkingLayerOrNull(): OverlayLayer? {
            val bmp = overlayBitmap ?: return null
            return OverlayLayer(
                bitmap = bmp,
                opacity = opacity,
                scale = scaleF,
                rotation = rotationDeg,
                offsetX = offsetX,
                offsetY = offsetY,
                fixedPoints = pointLocks.toList()
            )
        }

        fun createPointLockAt(tap: Offset): FixedPointLock? {
            val bmp = overlayBitmap ?: return null
            if (previewSize.width <= 1 || previewSize.height <= 1) return null
            val matrix = buildOverlayMatrix(
                previewSize = previewSize,
                overlay = bmp,
                scale = scaleF,
                rotation = rotationDeg,
                offsetX = offsetX,
                offsetY = offsetY,
                fixedPoints = pointLocks.toList()
            )
            val inverse = Matrix()
            if (!matrix.invert(inverse)) return null

            val mapped = floatArrayOf(tap.x, tap.y)
            inverse.mapPoints(mapped)
            val srcX = mapped[0].coerceIn(0f, bmp.width.toFloat())
            val srcY = mapped[1].coerceIn(0f, bmp.height.toFloat())
            return FixedPointLock(
                source = Offset(srcX, srcY),
                target = tap
            )
        }

        suspend fun loadBitmap(uri: Uri?): Bitmap? = withContext(Dispatchers.IO) {
            uri?.let { u ->
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
            }
        }

        val photoPickerLauncher =
            if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let {
                        if (pickerTarget == PickerTarget.Background) backgroundUri = it else overlayUri = it
                    }
                }
            } else null

        val openDocLauncher =
            if (Build.VERSION.SDK_INT < 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        if (pickerTarget == PickerTarget.Background) backgroundUri = it else overlayUri = it
                    }
                }
            } else null

        fun launchImagePicker(target: PickerTarget) {
            pickerTarget = target
            if (Build.VERSION.SDK_INT >= 33) {
                photoPickerLauncher?.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                openDocLauncher?.launch(arrayOf("image/*"))
            }
        }

        LaunchedEffect(backgroundUri) {
            val uri = backgroundUri ?: return@LaunchedEffect
            backgroundLoadInProgress = true
            try {
                val bmp = loadBitmap(uri)
                if (bmp == null) {
                    snackbarHostState.showSnackbar("Could not load that background image")
                    return@LaunchedEffect
                }
                backgroundBitmap?.recycle()
                backgroundBitmap = bmp
                backgroundImage = bmp.asImageBitmap()
                overlayFixedMode = false
                hideAllOverlays = false
                lockedLayerA = null
                lockedLayerB = null
                addOverlayPending = false
                hideLayerAUntilSecondLock = false
                resetWorkingOverlayTransform(clearPoints = true)
            } finally {
                backgroundLoadInProgress = false
            }
        }

        LaunchedEffect(overlayUri) {
            val uri = overlayUri ?: return@LaunchedEffect
            overlayLoadInProgress = true
            try {
                val bmp = loadBitmap(uri)
                if (bmp == null) {
                    snackbarHostState.showSnackbar("Could not load that overlay image")
                    return@LaunchedEffect
                }
                overlayBitmap = bmp
            } finally {
                overlayLoadInProgress = false
            }
        }

        val hasStillBackground = backgroundBitmap != null

        val visibleLayersForEffects = listOfNotNull(
            if (hideAllOverlays || hideLayerAUntilSecondLock) null else lockedLayerA,
            if (hideAllOverlays) null else lockedLayerB,
            currentWorkingLayerOrNull()?.takeIf { !overlayFixedMode || addOverlayPending }
        )

        LaunchedEffect(
            variant,
            hasStillBackground,
            backgroundBitmap,
            previewSize,
            visibleLayersForEffects,
            heatmapSimilarityThreshold,
            heatmapPalette
        ) {
            if (variant == EffectVariant.Heatmap && hasStillBackground && previewSize.width > 1 && previewSize.height > 1 && visibleLayersForEffects.isNotEmpty()) {
                val generated = withContext(Dispatchers.Default) {
                    buildHeatmapRender(
                        background = backgroundBitmap!!,
                        outputWidth = previewSize.width,
                        outputHeight = previewSize.height,
                        layers = visibleLayersForEffects,
                        similarityThreshold = heatmapSimilarityThreshold,
                        palette = heatmapPalette,
                        matrixForLayer = { layer ->
                            buildOverlayMatrix(
                                previewSize = previewSize,
                                overlay = layer.bitmap,
                                scale = layer.scale,
                                rotation = layer.rotation,
                                offsetX = layer.offsetX,
                                offsetY = layer.offsetY,
                                fixedPoints = layer.fixedPoints
                            )
                        }
                    )
                }
                heatmapBitmap?.recycle()
                heatmapBitmap = generated?.bitmap
                heatmapImage = generated?.bitmap?.asImageBitmap()
                heatmapInstruction = generated?.instruction
            } else {
                heatmapBitmap?.recycle()
                heatmapBitmap = null
                heatmapImage = null
                heatmapInstruction = null
            }
        }

        val transformState: TransformableState = rememberTransformableState { zoom, pan, rot ->
            if (overlayFixedMode) return@rememberTransformableState
            if (pointFixMode) {
                pointFixZoom = (pointFixZoom * zoom).coerceIn(1f, 6f)
            } else {
                scaleF = (scaleF * zoom).coerceIn(minScale, maxScale)
                rotationDeg += rot
                offsetX += pan.x
                offsetY += pan.y
            }
        }

        val uiState = OverlayUiState(
            hasStillBackground = hasStillBackground,
            hasOverlay = currentWorkingLayerOrNull() != null || lockedLayerA != null || lockedLayerB != null,
            isOverlayFixed = overlayFixedMode,
            isPointFixMode = pointFixMode,
        )

        var showTools by remember { mutableStateOf(false) }
        val busyLabel = when {
            saveInProgress -> "Saving image…"
            backgroundLoadInProgress -> "Loading background…"
            overlayLoadInProgress -> "Loading overlay…"
            autoAlignInProgress -> "Matching image…"
            else -> null
        }

        LaunchedEffect(uiState.mode) {
            showTools = false
        }

        BackHandler(enabled = showTools || pointFixMode) {
            when {
                showTools -> showTools = false
                pointFixMode -> {
                    pointFixMode = false
                    pointFixZoom = 1f
                }
            }
        }

        fun freezeCameraBackground(successMessage: String = "Background frozen") {
            val bmp = handle?.captureFrame?.invoke()
            if (bmp != null) {
                backgroundBitmap?.recycle()
                backgroundBitmap = bmp
                backgroundImage = bmp.asImageBitmap()
                showTools = false
                coroutineScope.launch { snackbarHostState.showSnackbar(successMessage) }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Camera is still starting") }
            }
        }

        fun returnToLiveCamera() {
            backgroundBitmap?.recycle()
            backgroundBitmap = null
            backgroundImage = null
            backgroundUri = null
            overlayFixedMode = false
            lockedLayerA = null
            lockedLayerB = null
            hideAllOverlays = false
            addOverlayPending = false
            hideLayerAUntilSecondLock = false
            resetWorkingOverlayTransform(clearPoints = true)
            showTools = false
        }

        fun lockWorkingOverlay() {
            val working = currentWorkingLayerOrNull() ?: return
            if (lockedLayerA == null) lockedLayerA = working else lockedLayerB = working
            overlayBitmap = null
            overlayUri = null
            addOverlayPending = false
            hideLayerAUntilSecondLock = false
            overlayFixedMode = true
            pointFixMode = false
            showTools = false
        }

        fun reopenLatestOverlayForEditing() {
            val layer = lockedLayerB ?: lockedLayerA ?: return
            if (lockedLayerB != null) lockedLayerB = null else lockedLayerA = null
            overlayBitmap = layer.bitmap
            opacity = layer.opacity
            scaleF = layer.scale
            rotationDeg = layer.rotation
            offsetX = layer.offsetX
            offsetY = layer.offsetY
            pointLocks.clear()
            pointLocks.addAll(layer.fixedPoints)
            selectedPointLockIndex = if (pointLocks.isEmpty()) -1 else 0
            overlayFixedMode = false
            pointFixMode = false
            addOverlayPending = false
            hideLayerAUntilSecondLock = false
            showTools = true
        }

        fun saveCurrentComposite() {
            if (saveInProgress) return
            val layers = listOfNotNull(
                if (hideAllOverlays || hideLayerAUntilSecondLock) null else lockedLayerA,
                if (hideAllOverlays) null else lockedLayerB,
                currentWorkingLayerOrNull()?.takeIf { addOverlayPending }
            )
            val frozen = backgroundBitmap
            when {
                layers.isEmpty() -> coroutineScope.launch {
                    snackbarHostState.showSnackbar("There is no visible overlay to save")
                }
                frozen == null -> coroutineScope.launch {
                    snackbarHostState.showSnackbar("Freeze or load a background first")
                }
                else -> {
                    saveInProgress = true
                    showTools = false
                    coroutineScope.launch {
                        val result = saveCompositeLayers(
                            context = context,
                            previewSize = previewSize,
                            layers = layers,
                            frozen = frozen,
                            variant = variant,
                            flipOverlayAndBackground = flipOverlayAndBackground,
                            frozenOverlayOpacity = frozenOverlayOpacity,
                            heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                            heatmapPalette = heatmapPalette,
                        )
                        saveInProgress = false
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }

        fun runRecommendedAction() {
            when (uiState.recommendedAction) {
                OverlayRecommendedAction.FreezeFrame -> freezeCameraBackground()
                OverlayRecommendedAction.PickOverlay -> {
                    showTools = false
                    launchImagePicker(PickerTarget.Overlay)
                }
                OverlayRecommendedAction.FinishAdjusting -> lockWorkingOverlay()
                OverlayRecommendedAction.FinishPointFix -> {
                    pointFixMode = false
                    pointFixZoom = 1f
                    showTools = false
                }
                OverlayRecommendedAction.SaveImage -> saveCurrentComposite()
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { previewSize = it.size }
        ) {
            val sceneZoom = if (pointFixMode) pointFixZoom else 1f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sceneZoom
                        scaleY = sceneZoom
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                Crossfade(targetState = variant, label = "variant-bg") { currentVariant ->
                    if (!hasStillBackground) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            isActive = true,
                            bindHandle = { handle = it }
                        )
                    } else {
                        val bg = backgroundImage
                        if (bg != null) {
                            Image(
                                bitmap = bg,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                colorFilter = if (currentVariant == EffectVariant.BackgroundInverted) invertComposeColorFilter() else null
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                }

                val canvasModifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = when (uiState.mode) {
                            OverlayUiMode.EditOverlay -> "Overlay editor canvas"
                            OverlayUiMode.PointFix -> "Point matching canvas"
                            OverlayUiMode.FixedOverlay -> "Finished overlay preview"
                            else -> "Camera and background preview"
                        }
                        stateDescription = when (uiState.mode) {
                            OverlayUiMode.EditOverlay -> "Drag, pinch, or rotate the overlay"
                            OverlayUiMode.PointFix -> "${pointLocks.size} of 2 matching points placed"
                            OverlayUiMode.FixedOverlay -> variant.label
                            else -> uiState.title
                        }
                    }
                    .then(if (!overlayFixedMode) Modifier.transformable(transformState) else Modifier)
                    .pointerInput(pointFixMode, overlayBitmap, hasStillBackground, selectedPointLockIndex) {
                        detectTapGestures { tap ->
                            if (!overlayFixedMode && pointFixMode && hasStillBackground && overlayBitmap != null) {
                                val lock = createPointLockAt(tap) ?: return@detectTapGestures
                                when {
                                    selectedPointLockIndex in pointLocks.indices -> {
                                        pointLocks[selectedPointLockIndex] = lock
                                    }
                                    pointLocks.size < 2 -> {
                                        pointLocks.add(lock)
                                        selectedPointLockIndex = pointLocks.lastIndex
                                    }
                                    else -> {
                                        val nearest = pointLocks.indices.minByOrNull { i ->
                                            val d = pointLocks[i].target - tap
                                            d.x * d.x + d.y * d.y
                                        }
                                        if (nearest != null) {
                                            pointLocks[nearest] = lock
                                            selectedPointLockIndex = nearest
                                        }
                                    }
                                }
                            }
                        }
                    }

                Canvas(modifier = canvasModifier) {
                val clip = Rect(0f, 0f, size.width, size.height)

                clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                    if (variant == EffectVariant.Heatmap) {
                        heatmapImage?.let { drawImage(it) }
                    } else if (!hideAllOverlays) {
                        val invertOverlays = variant == EffectVariant.OverlayInverted
                        val drawSize = IntSize(size.width.toInt(), size.height.toInt())

                        val layerAVisible = lockedLayerA != null && !hideLayerAUntilSecondLock
                        if (layerAVisible) {
                            drawOverlayLayer(lockedLayerA!!, previewForDraw = drawSize, invert = invertOverlays)
                        }
                        if (lockedLayerB != null) {
                            drawOverlayLayer(lockedLayerB!!, previewForDraw = drawSize, invert = invertOverlays)
                        }

                        val currentLayer = currentWorkingLayerOrNull()
                        if (currentLayer != null && (!overlayFixedMode || addOverlayPending)) {
                            drawOverlayLayer(currentLayer, previewForDraw = drawSize, invert = invertOverlays)
                        }
                    }

                    if (flipOverlayAndBackground && hasStillBackground && backgroundImage != null) {
                        drawImage(
                            image = backgroundImage!!,
                            alpha = frozenOverlayOpacity.coerceIn(0f, 1f),
                            colorFilter = if (variant == EffectVariant.BackgroundInverted) invertComposeColorFilter() else null
                        )
                    }
                }

                if (pointFixMode) {
                    pointLocks.forEachIndexed { idx, point ->
                        drawCircle(
                            color = if (idx == selectedPointLockIndex) Color.Red else if (idx == 0) Color.Cyan else Color.Yellow,
                            radius = 13f,
                            center = point.target
                        )
                    }
                }
                }

            }

            OverlayControlSheet(
                modifier = Modifier.align(Alignment.BottomCenter),
                expanded = controlsExpanded,
                onExpandedChange = { controlsExpanded = it },
                stageLabel = uiState.stageLabel,
                title = if (uiState.mode == OverlayUiMode.FixedOverlay) {
                    "${uiState.title} · ${variant.label}"
                } else {
                    uiState.title
                },
                guidance = uiState.guidance,
                expandActionLabel = uiState.expandActionLabel,
                collapsedContent = {
                    WorkflowActionPanel(
                        state = uiState,
                        expanded = false,
                        onRecommendedAction = { runRecommendedAction() },
                        onFreezeCamera = { freezeCameraBackground("Background refreshed") },
                        onPickBackground = {
                            controlsExpanded = false
                            launchImagePicker(PickerTarget.Background)
                        },
                        onPickOverlay = {
                            controlsExpanded = false
                            launchImagePicker(PickerTarget.Overlay)
                        },
                        onResetOverlay = {
                            resetWorkingOverlayTransform(clearPoints = true)
                            hideAllOverlays = false
                        },
                        onReturnToLiveCamera = { returnToLiveCamera() },
                        onAdjustAgain = { reopenLatestOverlayForEditing() },
                    )
                    if (uiState.showQuickOpacityControl) {
                        QuickOpacityControl(
                            opacity = opacity,
                            onOpacityChange = { opacity = it },
                        )
                    }
                },
            ) {
                WorkflowActionPanel(
                    state = uiState,
                    expanded = true,
                    onRecommendedAction = { runRecommendedAction() },
                    onFreezeCamera = { freezeCameraBackground("Background refreshed") },
                    onPickBackground = {
                        controlsExpanded = false
                        launchImagePicker(PickerTarget.Background)
                    },
                    onPickOverlay = {
                        controlsExpanded = false
                        launchImagePicker(PickerTarget.Overlay)
                    },
                    onResetOverlay = {
                        resetWorkingOverlayTransform(clearPoints = true)
                        hideAllOverlays = false
                    },
                    onReturnToLiveCamera = { returnToLiveCamera() },
                    onAdjustAgain = { reopenLatestOverlayForEditing() },
                )

                if (uiState.showEditPanel) {
                    EditOverlayPanel(
                        scale = scaleF,
                        minScale = minScale,
                        maxScale = maxScale,
                        rotationDeg = rotationDeg,
                        opacity = opacity,
                        canAutoAlign = hasStillBackground && overlayBitmap != null,
                        autoAlignInProgress = autoAlignInProgress,
                        autoAlignStatus = autoAlignStatus,
                        onScaleChange = { scaleF = it },
                        onRotationChange = { rotationDeg = it },
                        onOpacityChange = { opacity = it },
                        onReset = {
                            resetWorkingOverlayTransform(clearPoints = true)
                            hideAllOverlays = false
                        },
                        onAutoAlign = {
                            val bg = backgroundBitmap
                            val ov = overlayBitmap
                            if (bg != null && ov != null) {
                                autoAlignInProgress = true
                                autoAlignStatus = "Matching image patterns…"
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        estimateOverlayAutoAlignment(
                                            background = bg,
                                            overlay = ov,
                                            previewSize = previewSize,
                                            scale = scaleF,
                                            rotation = rotationDeg,
                                            offsetX = offsetX,
                                            offsetY = offsetY,
                                            fixedPoints = pointLocks.toList(),
                                        )
                                    }
                                    if (result != null && result.score >= 0.18f) {
                                        offsetX += result.dx
                                        offsetY += result.dy
                                        autoAlignStatus = "Auto-aligned Δx ${String.format("%.0f", result.dx)}, Δy ${String.format("%.0f", result.dy)} · score ${String.format("%.2f", result.score)}"
                                    } else if (result != null) {
                                        autoAlignStatus = "No confident match · best score ${String.format("%.2f", result.score)}"
                                    } else {
                                        autoAlignStatus = "No usable textured overlay area found"
                                    }
                                    autoAlignInProgress = false
                                }
                            }
                        },
                        onStartPointFix = {
                            pointFixMode = true
                            pointFixZoom = 1f
                            selectedPointLockIndex = if (pointLocks.isEmpty()) -1 else 0
                            controlsExpanded = false
                        },
                    )
                }

                if (uiState.showPointFixPanel) {
                    PointFixPanel(
                        pointCount = pointLocks.size,
                        onClearPoints = {
                            pointLocks.clear()
                            selectedPointLockIndex = -1
                        },
                        onResetZoom = { pointFixZoom = 1f },
                    )
                }

                if (uiState.showFixedOverlayActions) {
                    FixedOverlayActionsPanel(
                        hideAllOverlays = hideAllOverlays,
                        flipOverlayAndBackground = flipOverlayAndBackground,
                        frozenOverlayOpacity = frozenOverlayOpacity,
                        variant = variant,
                        heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                        heatmapPalette = heatmapPalette,
                        drawingInstruction = heatmapInstruction.takeIf { variant == EffectVariant.Heatmap },
                        onToggleHide = { hideAllOverlays = !hideAllOverlays },
                        onAddOverlay = {
                            addOverlayPending = true
                            overlayFixedMode = false
                            hideLayerAUntilSecondLock = true
                            resetWorkingOverlayTransform(clearPoints = true)
                            overlayBitmap = null
                            overlayUri = null
                            controlsExpanded = false
                            launchImagePicker(PickerTarget.Overlay)
                        },
                        onToggleFlip = { flipOverlayAndBackground = !flipOverlayAndBackground },
                        onNextEffect = { variantIndex = (variantIndex + 1) % variants.size },
                        onFrozenOverlayOpacityChange = { frozenOverlayOpacity = it },
                        onHeatmapSimilarityThresholdChange = { heatmapSimilarityThreshold = it },
                        onHeatmapPaletteChange = { heatmapPalette = it },
                    )
                }
            }

        }
    }
}

private fun DrawScope.drawOverlayLayer(layer: OverlayLayer, previewForDraw: IntSize, invert: Boolean) {
    val matrix = buildOverlayMatrix(
        previewSize = previewForDraw,
        overlay = layer.bitmap,
        scale = layer.scale,
        rotation = layer.rotation,
        offsetX = layer.offsetX,
        offsetY = layer.offsetY,
        fixedPoints = layer.fixedPoints
    )

    drawIntoCanvas { canvas ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            isFilterBitmap = true
            if (invert) colorFilter = invertAndroidColorFilter()
        }
        canvas.nativeCanvas.drawBitmap(layer.bitmap, matrix, p)
    }
}

@Composable
private fun CornerHandle(x: Float, y: Float, halfPx: Float, onDrag: (dx: Float, dy: Float) -> Unit) {
    Box(
        Modifier
            .offset { IntOffset((x - halfPx).toInt(), (y - halfPx).toInt()) }
            .size(20.dp)
            .drawBehind { drawRect(Color.Yellow) }
            .pointerInput(Unit) {
                detectDragGestures { change: PointerInputChange, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
    )
}

private fun saveCompositeLayers(
    context: android.content.Context,
    imageCapture: ImageCapture?,
    previewSize: IntSize,
    layers: List<OverlayLayer>,
    cropRect: android.graphics.RectF?,
    frozen: Bitmap?,
    variant: EffectVariant,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette
) {
    if (layers.isEmpty()) {
        Toast.makeText(context, "No visible overlay to save", Toast.LENGTH_SHORT).show()
        return
    }

    if (frozen != null) {
        composeAndSaveLayers(
            context = context,
            camBitmap = frozen,
            previewSize = previewSize,
            layers = layers,
            cropRect = cropRect,
            variant = variant,
            flipOverlayAndBackground = flipOverlayAndBackground,
            frozenOverlayOpacity = frozenOverlayOpacity,
            heatmapSimilarityThreshold = heatmapSimilarityThreshold,
            heatmapPalette = heatmapPalette
        )
        return
    }

    if (imageCapture == null) {
        Toast.makeText(context, "Freeze or load a background first", Toast.LENGTH_SHORT).show()
        return
    }

    val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val temp = File(context.cacheDir, "$name.tmp.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(temp).build()
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(options, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val camBitmap = BitmapFactory.decodeFile(temp.absolutePath) ?: run {
                        temp.delete()
                        Toast.makeText(context, "Failed to decode captured image", Toast.LENGTH_LONG).show()
                        return
                    }
                    composeAndSaveLayers(
                        context = context,
                        camBitmap = camBitmap,
                        previewSize = previewSize,
                        layers = layers,
                        cropRect = cropRect,
                        variant = variant,
                        flipOverlayAndBackground = flipOverlayAndBackground,
                        frozenOverlayOpacity = frozenOverlayOpacity,
                        heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                        heatmapPalette = heatmapPalette
                    )
                    temp.delete()
                } catch (t: Throwable) {
                    Toast.makeText(context, "Save failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )
}


private fun estimateOverlayAutoAlignment(
    background: Bitmap,
    overlay: Bitmap,
    previewSize: IntSize,
    scale: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    fixedPoints: List<FixedPointLock>,
): AutoAlignResult? {
    if (previewSize.width <= 1 || previewSize.height <= 1) return null
    if (overlay.width <= 1 || overlay.height <= 1) return null

    val maxMatchEdge = 192f
    val previewMaxEdge = kotlin.math.max(previewSize.width, previewSize.height).toFloat()
    val downscale = (maxMatchEdge / previewMaxEdge).coerceAtMost(1f)
    val matchWidth = kotlin.math.max(24, (previewSize.width * downscale).toInt())
    val matchHeight = kotlin.math.max(24, (previewSize.height * downscale).toInt())

    val backgroundMatch = Bitmap.createScaledBitmap(background, matchWidth, matchHeight, true)
    val overlayMatch = Bitmap.createBitmap(matchWidth, matchHeight, Bitmap.Config.ARGB_8888)
    val overlayCanvas = Canvas(overlayMatch)
    val scaledLocks = fixedPoints.map { lock ->
        FixedPointLock(
            source = lock.source,
            target = Offset(lock.target.x * downscale, lock.target.y * downscale),
        )
    }
    val matrix = buildOverlayMatrix(
        previewSize = IntSize(matchWidth, matchHeight),
        overlay = overlay,
        scale = scale,
        rotation = rotation,
        offsetX = offsetX * downscale,
        offsetY = offsetY * downscale,
        fixedPoints = scaledLocks,
    )
    overlayCanvas.drawBitmap(
        overlay,
        matrix,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 255
            isFilterBitmap = true
        },
    )

    val backgroundPixels = IntArray(matchWidth * matchHeight)
    val overlayPixels = IntArray(matchWidth * matchHeight)
    backgroundMatch.getPixels(backgroundPixels, 0, matchWidth, 0, 0, matchWidth, matchHeight)
    overlayMatch.getPixels(overlayPixels, 0, matchWidth, 0, 0, matchWidth, matchHeight)

    val backgroundLuma = FloatArray(backgroundPixels.size)
    val overlayLuma = FloatArray(overlayPixels.size)
    val validMask = BooleanArray(overlayPixels.size)
    var validCount = 0
    for (i in overlayPixels.indices) {
        backgroundLuma[i] = lumaOf(backgroundPixels[i])
        val alpha = overlayPixels[i] ushr 24 and 0xFF
        if (alpha > 16) {
            overlayLuma[i] = lumaOf(overlayPixels[i])
            validMask[i] = true
            validCount += 1
        }
    }

    if (validCount < 80) return null

    val minSamples = kotlin.math.max(48, validCount / 12)
    val coarse = findBestLumaTranslation(
        backgroundLuma = backgroundLuma,
        overlayLuma = overlayLuma,
        validMask = validMask,
        width = matchWidth,
        height = matchHeight,
        radiusX = matchWidth / 2,
        radiusY = matchHeight / 2,
        stepPixels = 8,
        sampleStride = 4,
        minComparedSamples = minSamples,
    ) ?: return null

    val fine = findBestLumaTranslation(
        backgroundLuma = backgroundLuma,
        overlayLuma = overlayLuma,
        validMask = validMask,
        width = matchWidth,
        height = matchHeight,
        centerDx = coarse.dx,
        centerDy = coarse.dy,
        radiusX = 10,
        radiusY = 10,
        stepPixels = 2,
        sampleStride = 2,
        minComparedSamples = minSamples,
    ) ?: coarse

    if (backgroundMatch !== background) backgroundMatch.recycle()
    overlayMatch.recycle()

    return AutoAlignResult(
        dx = fine.dx / downscale,
        dy = fine.dy / downscale,
        score = fine.score,
        comparedSamples = fine.comparedSamples,
    )
}

private fun lumaOf(pixel: Int): Float {
    val r = pixel shr 16 and 0xFF
    val g = pixel shr 8 and 0xFF
    val b = pixel and 0xFF
    return 0.299f * r + 0.587f * g + 0.114f * b
}

private fun composeAndSaveLayers(
    context: android.content.Context,
    camBitmap: Bitmap,
    previewSize: IntSize,
    layers: List<OverlayLayer>,
    cropRect: android.graphics.RectF?,
    variant: EffectVariant,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette
) {
    val sx = camBitmap.width.toFloat() / previewSize.width
    val sy = camBitmap.height.toFloat() / previewSize.height

    val outBitmap = Bitmap.createBitmap(camBitmap.width, camBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        if (variant == EffectVariant.BackgroundInverted) colorFilter = invertAndroidColorFilter()
    }

    if (!flipOverlayAndBackground && variant != EffectVariant.Heatmap) {
        canvas.drawBitmap(camBitmap, 0f, 0f, basePaint)
    }

    if (variant == EffectVariant.Heatmap) {
        val heatmap = buildHeatmapRender(
            background = camBitmap,
            outputWidth = camBitmap.width,
            outputHeight = camBitmap.height,
            layers = layers,
            similarityThreshold = heatmapSimilarityThreshold,
            palette = heatmapPalette,
            matrixForLayer = { layer ->
                buildOverlayMatrix(
                    previewSize = previewSize,
                    overlay = layer.bitmap,
                    scale = layer.scale,
                    rotation = layer.rotation,
                    offsetX = layer.offsetX,
                    offsetY = layer.offsetY,
                    fixedPoints = layer.fixedPoints,
                    sx = sx,
                    sy = sy
                )
            }
        )
        if (heatmap != null) {
            canvas.drawBitmap(heatmap.bitmap, 0f, 0f, null)
            heatmap.bitmap.recycle()
        }
    }

    val invertOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = if (variant == EffectVariant.OverlayInverted) invertAndroidColorFilter() else null
        isFilterBitmap = true
    }

    if (variant != EffectVariant.Heatmap && cropRect != null) {
        val camCrop = android.graphics.RectF(
            cropRect.left * sx, cropRect.top * sy,
            cropRect.right * sx, cropRect.bottom * sy
        )
        canvas.save()
        canvas.clipRect(camCrop)
        layers.forEach { layer ->
            val m = buildOverlayMatrix(
                previewSize = previewSize,
                overlay = layer.bitmap,
                scale = layer.scale,
                rotation = layer.rotation,
                offsetX = layer.offsetX,
                offsetY = layer.offsetY,
                fixedPoints = layer.fixedPoints,
                sx = sx,
                sy = sy
            )
            invertOverlayPaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layer.bitmap, m, invertOverlayPaint)
        }
        canvas.restore()
    } else if (variant != EffectVariant.Heatmap) {
        layers.forEach { layer ->
            val m = buildOverlayMatrix(
                previewSize = previewSize,
                overlay = layer.bitmap,
                scale = layer.scale,
                rotation = layer.rotation,
                offsetX = layer.offsetX,
                offsetY = layer.offsetY,
                fixedPoints = layer.fixedPoints,
                sx = sx,
                sy = sy
            )
            invertOverlayPaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layer.bitmap, m, invertOverlayPaint)
        }
    }

    if (flipOverlayAndBackground) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (frozenOverlayOpacity * 255).toInt().coerceIn(0, 255)
            isFilterBitmap = true
            if (variant == EffectVariant.BackgroundInverted) colorFilter = invertAndroidColorFilter()
        }
        canvas.drawBitmap(camBitmap, 0f, 0f, p)
    }

    val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OverlayCam")
        }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let { out ->
        context.contentResolver.openOutputStream(out)?.use { os ->
            outBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
        }
        Toast.makeText(context, "Saved to Pictures/OverlayCam", Toast.LENGTH_SHORT).show()
    } ?: Toast.makeText(context, "Failed to create media entry", Toast.LENGTH_LONG).show()
}

private fun buildOverlayMatrix(
    previewSize: IntSize,
    overlay: Bitmap,
    scale: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    fixedPoints: List<FixedPointLock>,
    sx: Float = 1f,
    sy: Float = 1f
): Matrix {
    val cx = previewSize.width / 2f
    val cy = previewSize.height / 2f
    val iw = overlay.width.toFloat()
    val ih = overlay.height.toFloat()

    val base = Matrix()
    base.postTranslate(cx - iw / 2f, cy - ih / 2f)
    base.postTranslate(offsetX, offsetY)
    base.postTranslate(-cx, -cy)
    base.postRotate(rotation, 0f, 0f)
    base.postScale(scale, scale)
    base.postTranslate(cx, cy)

    val corrected = applyPointLock(base, fixedPoints)
    corrected.postScale(sx, sy)
    return corrected
}

private fun applyPointLock(baseMatrix: Matrix, fixedPoints: List<FixedPointLock>): Matrix {
    if (fixedPoints.isEmpty()) return Matrix(baseMatrix)

    val count = fixedPoints.size.coerceIn(1, 2)
    val src = FloatArray(count * 2)
    val dst = FloatArray(count * 2)
    repeat(count) { i ->
        src[i * 2] = fixedPoints[i].source.x
        src[i * 2 + 1] = fixedPoints[i].source.y
        dst[i * 2] = fixedPoints[i].target.x
        dst[i * 2 + 1] = fixedPoints[i].target.y
    }

    val mapped = src.copyOf()
    baseMatrix.mapPoints(mapped)

    val correction = Matrix().apply {
        setPolyToPoly(mapped, 0, dst, 0, count)
    }

    val out = Matrix()
    out.setConcat(correction, baseMatrix)
    return out
}

private data class HeatmapRender(
    val bitmap: Bitmap,
    val instruction: DrawingInstruction,
)

private fun buildHeatmapRender(
    background: Bitmap,
    outputWidth: Int,
    outputHeight: Int,
    layers: List<OverlayLayer>,
    similarityThreshold: Float,
    palette: HeatmapPalette,
    matrixForLayer: (OverlayLayer) -> Matrix
): HeatmapRender? {
    if (layers.isEmpty()) return null
    if (outputWidth <= 0 || outputHeight <= 0) return null

    val bg = if (background.width == outputWidth && background.height == outputHeight) {
        background
    } else {
        Bitmap.createScaledBitmap(background, outputWidth, outputHeight, true)
    }

    val overlayComposite = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val overlayCanvas = Canvas(overlayComposite)
    layers.forEach { layer ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            isFilterBitmap = true
        }
        overlayCanvas.drawBitmap(layer.bitmap, matrixForLayer(layer), p)
    }

    val bgPixels = IntArray(outputWidth * outputHeight)
    val ovPixels = IntArray(outputWidth * outputHeight)
    bg.getPixels(bgPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
    overlayComposite.getPixels(ovPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)

    val analysis = analyzeOverlayDiff(
        backgroundPixels = bgPixels,
        overlayPixels = ovPixels,
        width = outputWidth,
        height = outputHeight,
        similarityThreshold = similarityThreshold,
        palette = palette,
    )

    val out = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    out.setPixels(analysis.heatmapPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
    overlayComposite.recycle()
    if (bg !== background) bg.recycle()
    return HeatmapRender(bitmap = out, instruction = analysis.instruction)
}

private fun invertAndroidColorFilter(): ColorMatrixColorFilter {
    val matrix = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(matrix)
}

private fun invertComposeColorFilter(): ColorFilter {
    return ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 1f,
                0f, -1f, 0f, 0f, 1f,
                0f, 0f, -1f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}
