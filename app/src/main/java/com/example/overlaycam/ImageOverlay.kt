package com.example.overlaycam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    CameraPermissionGate { cameraAvailable, requestCameraAccess ->
        val editor: OverlayEditorViewModel = viewModel()
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

        var backgroundUri by editor.backgroundUri
        var backgroundBitmap by editor.backgroundBitmap
        val backgroundImage = remember(backgroundBitmap) { backgroundBitmap?.asImageBitmap() }

        var overlayUri by editor.overlayUri
        var overlayBitmap by editor.overlayBitmap
        var backgroundLoadInProgress by remember { mutableStateOf(false) }
        var overlayLoadInProgress by remember { mutableStateOf(false) }
        var saveInProgress by remember { mutableStateOf(false) }
        var retrySaveAfterPermission by remember { mutableStateOf(false) }

        val minScale = 0.5f
        val maxScale = 2.0f
        var opacity by editor.opacity
        var scaleF by editor.scale
        var rotationDeg by editor.rotationDegrees
        var offsetX by editor.offsetX
        var offsetY by editor.offsetY

        var pointFixMode by editor.pointFixMode
        val pointLocks = editor.pointLocks
        var pendingPointSource by editor.pendingPointSource
        var pendingPointScreenPosition by editor.pendingPointScreenPosition
        var pointFixZoom by editor.pointFixZoom

        var overlayFixedMode by editor.overlayFixedMode
        var lockedLayerA by editor.lockedLayerA
        var lockedLayerB by editor.lockedLayerB
        var hideAllOverlays by editor.hideAllOverlays
        var addOverlayPending by editor.addOverlayPending
        var flipOverlayAndBackground by editor.flipOverlayAndBackground
        var frozenOverlayOpacity by editor.frozenOverlayOpacity

        var variantIndex by editor.variantIndex
        val variants = EffectVariant.entries
        val variant = variants[variantIndex]

        var heatmapBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var heatmapImage by remember { mutableStateOf<ImageBitmap?>(null) }
        var heatmapInstruction by remember { mutableStateOf<DrawingInstruction?>(null) }
        var heatmapSimilarityThreshold by editor.heatmapSimilarityThreshold
        var heatmapPalette by editor.heatmapPalette

        var autoAlignInProgress by remember { mutableStateOf(false) }
        var autoAlignStatus by remember { mutableStateOf<String?>(null) }

        var pickerTarget by remember { mutableStateOf(PickerTarget.Overlay) }

        fun clearOverlayBitmaps() {
            listOfNotNull(
                overlayBitmap,
                lockedLayerA?.bitmap,
                lockedLayerB?.bitmap,
            ).distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            overlayBitmap = null
            overlayUri = null
            lockedLayerA = null
            lockedLayerB = null
            heatmapBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            heatmapBitmap = null
            heatmapImage = null
            heatmapInstruction = null
        }

        DisposableEffect(Unit) {
            onDispose {
                heatmapBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }

        LaunchedEffect(retrySaveAfterPermission) {
            if (retrySaveAfterPermission) {
                retrySaveAfterPermission = false
                saveCurrentComposite()
            }
        }

        fun resetWorkingOverlayTransform(clearPoints: Boolean = true) {
            scaleF = 1f
            rotationDeg = 0f
            offsetX = 0f
            offsetY = 0f
            opacity = 0.7f
            if (clearPoints) {
                pointLocks.clear()
                pendingPointSource = null
                pendingPointScreenPosition = null
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

        fun overlaySourceAt(tap: Offset): Offset? {
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
            return Offset(srcX, srcY)
        }

        suspend fun loadBitmap(uri: Uri?): Bitmap? = uri?.let {
            decodeBitmapForEditor(context, it)
        }

        val photoPickerLauncher =
            if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let {
                        if (pickerTarget == PickerTarget.Background) backgroundUri = it else overlayUri = it
                    }
                }
            } else null

        val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                retrySaveAfterPermission = true
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Storage access is needed to save on this Android version")
                }
            }
        }

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
                clearOverlayBitmaps()
                overlayFixedMode = false
                hideAllOverlays = false
                addOverlayPending = false
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
                overlayBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                overlayBitmap = bmp
            } finally {
                overlayLoadInProgress = false
            }
        }

        val hasStillBackground = backgroundBitmap != null

        val visibleLayersForEffects = listOfNotNull(
            if (hideAllOverlays) null else lockedLayerA,
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
            cameraAvailable = cameraAvailable,
            hasOverlay = currentWorkingLayerOrNull() != null || lockedLayerA != null || lockedLayerB != null,
            isOverlayFixed = overlayFixedMode,
            isPointFixMode = pointFixMode,
        )
        val pointMatchUiState = PointMatchUiState(
            completedPointCount = pointLocks.size,
            hasPendingOverlayLandmark = pendingPointSource != null,
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

        BackHandler(enabled = pointFixMode && !showTools) {
            pointFixMode = false
            pointFixZoom = 1f
            pendingPointSource = null
            pendingPointScreenPosition = null
        }

        fun freezeCameraBackground(successMessage: String = "Background frozen") {
            val bmp = handle?.captureFrame?.invoke()
            if (bmp != null) {
                backgroundBitmap?.recycle()
                backgroundBitmap = bmp
                showTools = false
                coroutineScope.launch { snackbarHostState.showSnackbar(successMessage) }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Camera is still starting") }
            }
        }

        fun returnToLiveCamera() {
            backgroundBitmap?.recycle()
            backgroundBitmap = null
            backgroundUri = null
            overlayFixedMode = false
            clearOverlayBitmaps()
            hideAllOverlays = false
            addOverlayPending = false
            resetWorkingOverlayTransform(clearPoints = true)
            showTools = false
        }

        fun lockWorkingOverlay() {
            val working = currentWorkingLayerOrNull() ?: return
            if (lockedLayerA == null) lockedLayerA = working else lockedLayerB = working
            overlayBitmap = null
            overlayUri = null
            addOverlayPending = false
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
            pendingPointSource = null
            pendingPointScreenPosition = null
            overlayFixedMode = false
            pointFixMode = false
            addOverlayPending = false
            showTools = true
        }

        fun saveCurrentComposite() {
            if (saveInProgress) return
            val needsLegacyWritePermission =
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
            if (needsLegacyWritePermission) {
                legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
            val layers = listOfNotNull(
                if (hideAllOverlays) null else lockedLayerA,
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
                OverlayRecommendedAction.PickBackground -> {
                    showTools = false
                    launchImagePicker(PickerTarget.Background)
                }
                OverlayRecommendedAction.PickOverlay -> {
                    showTools = false
                    launchImagePicker(PickerTarget.Overlay)
                }
                OverlayRecommendedAction.FinishAdjusting -> lockWorkingOverlay()
                OverlayRecommendedAction.FinishPointFix -> {
                    pointFixMode = false
                    pointFixZoom = 1f
                    pendingPointSource = null
                    pendingPointScreenPosition = null
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
                    if (!hasStillBackground && cameraAvailable) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            isActive = true,
                            bindHandle = { handle = it }
                        )
                    } else if (hasStillBackground) {
                        val bg = backgroundImage
                        if (bg != null) {
                            Image(
                                bitmap = bg,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                colorFilter = if (currentVariant == EffectVariant.BackgroundInverted) invertComposeColorFilter() else null
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Choose a background photo to begin",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            OverlayUiMode.PointFix -> pointMatchUiState.title
                            OverlayUiMode.FixedOverlay -> variant.label
                            else -> uiState.title
                        }
                    }
                    .then(if (!overlayFixedMode && busyLabel == null) Modifier.transformable(transformState) else Modifier)
                    .pointerInput(
                        pointFixMode,
                        overlayBitmap,
                        hasStillBackground,
                        pendingPointSource,
                        pointLocks.size,
                    ) {
                        detectTapGestures { tap ->
                            if (busyLabel == null && !overlayFixedMode && pointFixMode && hasStillBackground && overlayBitmap != null) {
                                val pendingSource = pendingPointSource
                                when {
                                    pendingSource != null -> {
                                        pointLocks.add(FixedPointLock(source = pendingSource, target = tap))
                                        pendingPointSource = null
                                        pendingPointScreenPosition = null
                                    }
                                    pointLocks.size >= 2 -> coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Two landmark pairs are already set")
                                    }
                                    else -> {
                                        overlaySourceAt(tap)?.let { source ->
                                            pendingPointSource = source
                                            pendingPointScreenPosition = tap
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

                        if (lockedLayerA != null) {
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

                    val foregroundBackground = backgroundImage
                    if (flipOverlayAndBackground && hasStillBackground && foregroundBackground != null) {
                        drawImage(
                            image = foregroundBackground,
                            alpha = frozenOverlayOpacity.coerceIn(0f, 1f),
                            colorFilter = if (variant == EffectVariant.BackgroundInverted) invertComposeColorFilter() else null
                        )
                    }
                }

                if (pointFixMode) {
                    pointLocks.forEachIndexed { idx, point ->
                        drawCircle(
                            color = if (idx == 0) Color.Cyan else Color.Yellow,
                            radius = 13f,
                            center = point.target
                        )
                    }
                    pendingPointScreenPosition?.let { pending ->
                        drawCircle(
                            color = Color.Magenta,
                            radius = 18f,
                            center = pending,
                            style = Stroke(width = 5f),
                        )
                    }
                }
                }

            }

            AnimatedVisibility(
                visible = !showTools,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                OverlayControlDock(
                    state = uiState,
                    busyLabel = busyLabel,
                    supportingText = pointMatchUiState.title.takeIf {
                        uiState.mode == OverlayUiMode.PointFix
                    },
                    opacity = opacity,
                    onOpacityChange = { opacity = it },
                    onRecommendedAction = { runRecommendedAction() },
                    onOpenTools = { showTools = true },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (showTools) {
                OverlayToolsSheet(
                    state = uiState,
                    onDismissRequest = { showTools = false },
                ) {
                    WorkflowActionPanel(
                        state = uiState,
                        enabled = busyLabel == null,
                        onRecommendedAction = { runRecommendedAction() },
                        onFreezeCamera = { freezeCameraBackground("Background refreshed") },
                        onEnableCamera = requestCameraAccess,
                        onPickBackground = {
                            showTools = false
                            launchImagePicker(PickerTarget.Background)
                        },
                        onPickOverlay = {
                            showTools = false
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
                            enabled = busyLabel == null,
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
                                            autoAlignStatus = "Auto-aligned by ${String.format("%.0f", result.dx)} × ${String.format("%.0f", result.dy)} px · confidence ${String.format("%.2f", result.score)}"
                                        } else if (result != null) {
                                            autoAlignStatus = "No confident match · best confidence ${String.format("%.2f", result.score)}"
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
                                pendingPointSource = null
                                pendingPointScreenPosition = null
                                showTools = false
                            },
                        )
                    }

                    if (uiState.showPointFixPanel) {
                        PointFixPanel(
                            state = pointMatchUiState,
                            onClearPoints = {
                                pointLocks.clear()
                                pendingPointSource = null
                                pendingPointScreenPosition = null
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
                            canAddOverlay = lockedLayerB == null,
                            onToggleHide = { hideAllOverlays = !hideAllOverlays },
                            onAddOverlay = {
                                addOverlayPending = true
                                overlayFixedMode = false
                                resetWorkingOverlayTransform(clearPoints = true)
                                overlayBitmap = null
                                overlayUri = null
                                showTools = false
                                launchImagePicker(PickerTarget.Overlay)
                            },
                            onToggleFlip = { flipOverlayAndBackground = !flipOverlayAndBackground },
                            onEffectChange = { selected -> variantIndex = variants.indexOf(selected) },
                            onFrozenOverlayOpacityChange = { frozenOverlayOpacity = it },
                            onHeatmapSimilarityThresholdChange = { heatmapSimilarityThreshold = it },
                            onHeatmapPaletteChange = { heatmapPalette = it },
                        )
                    }
                }
            }

        }
    }
}

private suspend fun decodeBitmapForEditor(
    context: android.content.Context,
    uri: Uri,
    maxEdge: Int = 4096,
): Bitmap? = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(
                bitmapSampleSize(info.size.width, info.size.height, maxEdge),
            )
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }
}

private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    if (targetWidth <= 0 || targetHeight <= 0) return source
    val crop = centerCropRect(source.width, source.height, targetWidth, targetHeight)
    if (crop.width == source.width && crop.height == source.height) return source
    return Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
}

private fun scaledCenterCropBitmap(source: Bitmap, width: Int, height: Int): Bitmap {
    val cropped = centerCropBitmap(source, width, height)
    if (cropped.width == width && cropped.height == height) return cropped
    val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
    if (cropped !== source) cropped.recycle()
    return scaled
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

private data class SaveCompositeResult(val message: String)

private suspend fun saveCompositeLayers(
    context: android.content.Context,
    previewSize: IntSize,
    layers: List<OverlayLayer>,
    frozen: Bitmap,
    variant: EffectVariant,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette,
): SaveCompositeResult {
    return try {
        val outBitmap = withContext(Dispatchers.Default) {
            composeLayers(
                camBitmap = frozen,
                previewSize = previewSize,
                layers = layers,
                variant = variant,
                flipOverlayAndBackground = flipOverlayAndBackground,
                frozenOverlayOpacity = frozenOverlayOpacity,
                heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                heatmapPalette = heatmapPalette,
            )
        }

        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = "OverlayCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OverlayCam")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            var insertedUri: Uri? = null
            try {
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext SaveCompositeResult("Could not create a gallery image")
                insertedUri = uri
                val written = resolver.openOutputStream(uri)?.use { stream ->
                    outBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                } == true
                if (!written) {
                    resolver.delete(uri, null, null)
                    return@withContext SaveCompositeResult("Could not write the image")
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                SaveCompositeResult("Saved to Pictures/OverlayCam")
            } catch (error: Throwable) {
                insertedUri?.let { resolver.delete(it, null, null) }
                SaveCompositeResult("Save failed: ${error.message ?: "unknown error"}")
            } finally {
                outBitmap.recycle()
            }
        }
    } catch (error: Throwable) {
        SaveCompositeResult("Save failed: ${error.message ?: "unknown error"}")
    }
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

    val backgroundMatch = scaledCenterCropBitmap(background, matchWidth, matchHeight)
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

private fun composeLayers(
    camBitmap: Bitmap,
    previewSize: IntSize,
    layers: List<OverlayLayer>,
    variant: EffectVariant,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette
): Bitmap {
    val renderedBackground = centerCropBitmap(camBitmap, previewSize.width, previewSize.height)
    val sx = renderedBackground.width.toFloat() / previewSize.width
    val sy = renderedBackground.height.toFloat() / previewSize.height

    val outBitmap = Bitmap.createBitmap(
        renderedBackground.width,
        renderedBackground.height,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(outBitmap)

    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        if (variant == EffectVariant.BackgroundInverted) colorFilter = invertAndroidColorFilter()
    }

    if (!flipOverlayAndBackground && variant != EffectVariant.Heatmap) {
        canvas.drawBitmap(renderedBackground, 0f, 0f, basePaint)
    }

    if (variant == EffectVariant.Heatmap) {
        val heatmap = buildHeatmapRender(
            background = renderedBackground,
            outputWidth = renderedBackground.width,
            outputHeight = renderedBackground.height,
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

    if (variant != EffectVariant.Heatmap) {
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
        canvas.drawBitmap(renderedBackground, 0f, 0f, p)
    }

    if (renderedBackground !== camBitmap) renderedBackground.recycle()
    return outBitmap
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

    val bg = scaledCenterCropBitmap(background, outputWidth, outputHeight)

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
