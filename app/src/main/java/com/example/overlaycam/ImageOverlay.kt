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
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    CameraPermissionGate { cameraAvailable, cameraSupported, requestCameraAccess ->
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

        var repositionEnabled by editor.repositionEnabled
        var viewportScale by editor.viewportScale
        var viewportOffsetX by editor.viewportOffsetX
        var viewportOffsetY by editor.viewportOffsetY

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

        var guidanceBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var guidanceImage by remember { mutableStateOf<ImageBitmap?>(null) }
        var guidanceInstruction by remember { mutableStateOf<DrawingInstruction?>(null) }
        var guidanceSummary by remember { mutableStateOf<OverlayDiffSummary?>(null) }
        var guidanceUpdating by remember { mutableStateOf(false) }
        var heatmapSimilarityThreshold by editor.heatmapSimilarityThreshold
        var heatmapPalette by editor.heatmapPalette

        var streamingEffects by editor.streamingEffects
        var pictureInPictureBitmap by editor.pictureInPictureBitmap
        var logoBitmap by editor.logoBitmap
        var keyedPictureInPictureBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var keyedLogoBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var customTypeface by remember { mutableStateOf<Typeface?>(null) }
        var effectAnimationPhase by remember { mutableFloatStateOf(0f) }

        val presetStore = remember(context) { EffectPresetStore(context) }
        var presetName by remember { mutableStateOf(presetStore.lastPresetName() ?: "default") }
        var presetNames by remember { mutableStateOf(presetStore.names()) }
        val mjpegServer = remember { MjpegStreamServer() }
        var outputPortText by remember { mutableStateOf(streamingEffects.output.port.toString()) }
        var streamStatus by remember { mutableStateOf(context.getString(R.string.fx_mjpeg_off)) }

        val obsClient = remember { ObsWebSocketClient() }
        var obsHost by remember { mutableStateOf("") }
        var obsPort by remember { mutableStateOf("4455") }
        var obsPassword by remember { mutableStateOf("") }
        var obsScene by remember { mutableStateOf("") }
        var obsSecure by remember { mutableStateOf(false) }
        var obsStatus by remember { mutableStateOf(context.getString(R.string.obs_status_disconnected)) }
        var obsState by remember { mutableStateOf(ObsConnectionState.Disconnected) }

        var autoAlignInProgress by remember { mutableStateOf(false) }
        var autoAlignStatus by remember { mutableStateOf<String?>(null) }
        var gestureOperation by remember { mutableStateOf(OverlayGestureOperation.Idle) }

        var pickerTarget by remember { mutableStateOf(PickerTarget.Overlay) }

        val recentStore = remember(context) { RecentSceneStore(context) }
        var recentScenes by remember { mutableStateOf(recentStore.list()) }
        var currentFrozenUri by remember { mutableStateOf<Uri?>(null) }
        var currentOverlaySourceUri by remember { mutableStateOf<Uri?>(null) }
        // Invalidates slow asynchronous recent restores when the user changes
        // scene, freezes again, or returns to the live camera.
        var reopenGeneration by remember { mutableIntStateOf(0) }
        var showRecent by remember { mutableStateOf(false) }
        var recentLoadInProgress by remember { mutableStateOf(false) }
        var showRestartConfirm by remember { mutableStateOf(false) }
        var showCameraGrid by remember { mutableStateOf(true) }
        var showChrome by remember { mutableStateOf(true) }

        fun rememberRecent(background: Uri?, overlay: Uri?, label: String) {
            if (background != null) {
                recentScenes = recentStore.record(background.toString(), overlay?.toString(), label)
            }
        }

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
            currentOverlaySourceUri = null
            lockedLayerA = null
            lockedLayerB = null
            guidanceBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            guidanceBitmap = null
            guidanceImage = null
            guidanceInstruction = null
            guidanceSummary = null
            variantIndex = EffectVariant.Normal.ordinal
        }

        DisposableEffect(Unit) {
            onDispose {
                guidanceBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                keyedPictureInPictureBitmap
                    ?.takeIf { it !== pictureInPictureBitmap }
                    ?.takeUnless(Bitmap::isRecycled)
                    ?.recycle()
                keyedLogoBitmap
                    ?.takeIf { it !== logoBitmap }
                    ?.takeUnless(Bitmap::isRecycled)
                    ?.recycle()
                mjpegServer.stop()
                obsClient.disconnect()
            }
        }

        fun resetViewport() {
            viewportScale = 1f
            viewportOffsetX = 0f
            viewportOffsetY = 0f
        }

        fun resetWorkingOverlayTransform(clearPoints: Boolean = true) {
            scaleF = 1f
            rotationDeg = 0f
            offsetX = 0f
            offsetY = 0f
            opacity = 0.7f
            repositionEnabled = true
            resetViewport()
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
                opacity = 1f,
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
            val srcX = mapped[0]
            val srcY = mapped[1]
            if (!isPixelInside(bmp.width, bmp.height, srcX, srcY)) return null
            return Offset(srcX, srcY)
        }

        suspend fun loadBitmap(uri: Uri?): Bitmap? = uri?.let {
            decodeBitmapForEditor(context, it)
        }

        fun persistReadPermission(uri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        fun applyPickedImageUri(uri: Uri) {
            persistReadPermission(uri)
            when (pickerTarget) {
                PickerTarget.Background -> {
                    reopenGeneration += 1
                    recentLoadInProgress = false
                    backgroundUri = uri
                }
                PickerTarget.Overlay -> {
                    reopenGeneration += 1
                    recentLoadInProgress = false
                    overlayUri = uri
                }
                PickerTarget.PictureInPicture -> {
                    persistReadPermission(uri)
                    streamingEffects = streamingEffects.copy(
                        pictureInPicture = streamingEffects.pictureInPicture.copy(
                            enabled = true,
                            imageUri = uri.toString(),
                        ),
                    )
                }
                PickerTarget.Logo -> {
                    persistReadPermission(uri)
                    streamingEffects = streamingEffects.copy(
                        logo = streamingEffects.logo.copy(
                            enabled = true,
                            imageUri = uri.toString(),
                        ),
                    )
                }
            }
        }

        val photoPickerLauncher =
            if (Build.VERSION.SDK_INT >= 33) {
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let(::applyPickedImageUri)
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
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        } catch (_: SecurityException) {
                            // Some document providers grant only temporary access. The current
                            // editor session can still use the returned URI safely.
                        }
                        applyPickedImageUri(it)
                    }
                }
            } else null

        val fontPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                persistReadPermission(it)
                streamingEffects = streamingEffects.copy(
                    text = streamingEffects.text.copy(
                        font = TextOverlayFont.Custom,
                        customFontUri = it.toString(),
                    ),
                )
            }
        }

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

        fun launchFontPicker() {
            fontPickerLauncher.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/vnd.ms-opentype",
                    "application/octet-stream",
                ),
            )
        }

        LaunchedEffect(backgroundUri) {
            val uri = backgroundUri ?: return@LaunchedEffect
            backgroundLoadInProgress = true
            try {
                val bmp = loadBitmap(uri)
                if (bmp == null) {
                    // Revert selection without mutating the previous frozen bitmap.
                    backgroundUri = null
                    snackbarHostState.showSnackbar("Cannot open background; it may have moved or access was revoked")
                    return@LaunchedEffect
                }
                backgroundBitmap?.recycle()
                backgroundBitmap = bmp
                clearOverlayBitmaps()
                overlayFixedMode = false
                hideAllOverlays = false
                addOverlayPending = false
                resetWorkingOverlayTransform(clearPoints = true)
                currentFrozenUri = uri
                rememberRecent(uri, null, "Background photo")
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
                    overlayUri = null
                    snackbarHostState.showSnackbar("Could not load overlay; previous image is unchanged")
                    return@LaunchedEffect
                }
                overlayBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                overlayBitmap = bmp
                currentOverlaySourceUri = uri
                rememberRecent(
                    currentFrozenUri ?: backgroundUri,
                    uri,
                    if (backgroundUri == null) "Camera + overlay" else "Photo + overlay",
                )
            } finally {
                overlayLoadInProgress = false
            }
        }

        LaunchedEffect(streamingEffects.pictureInPicture.imageUri) {
            val uri = streamingEffects.pictureInPicture.imageUri?.let(Uri::parse)
            val next = loadBitmap(uri)
            keyedPictureInPictureBitmap
                ?.takeIf { it !== pictureInPictureBitmap }
                ?.takeUnless(Bitmap::isRecycled)
                ?.recycle()
            keyedPictureInPictureBitmap = null
            pictureInPictureBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            pictureInPictureBitmap = next
        }

        LaunchedEffect(streamingEffects.logo.imageUri) {
            val uri = streamingEffects.logo.imageUri?.let(Uri::parse)
            val next = loadBitmap(uri)
            keyedLogoBitmap
                ?.takeIf { it !== logoBitmap }
                ?.takeUnless(Bitmap::isRecycled)
                ?.recycle()
            keyedLogoBitmap = null
            logoBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            logoBitmap = next
        }

        LaunchedEffect(
            pictureInPictureBitmap,
            streamingEffects.chromaKey,
        ) {
            val source = pictureInPictureBitmap
            val shouldKey = streamingEffects.chromaKey.enabled &&
                streamingEffects.chromaKey.target in setOf(
                    ChromaTarget.PictureInPicture,
                    ChromaTarget.Both,
                )
            val next = if (source != null && shouldKey) {
                withContext(Dispatchers.Default) {
                    chromaKeyCopy(source, streamingEffects.chromaKey)
                }
            } else {
                source
            }
            keyedPictureInPictureBitmap
                ?.takeIf { it !== source && it !== next }
                ?.takeUnless(Bitmap::isRecycled)
                ?.recycle()
            keyedPictureInPictureBitmap = next
        }

        LaunchedEffect(
            logoBitmap,
            streamingEffects.chromaKey,
        ) {
            val source = logoBitmap
            val shouldKey = streamingEffects.chromaKey.enabled &&
                streamingEffects.chromaKey.target in setOf(
                    ChromaTarget.Logo,
                    ChromaTarget.Both,
                )
            val next = if (source != null && shouldKey) {
                withContext(Dispatchers.Default) {
                    chromaKeyCopy(source, streamingEffects.chromaKey)
                }
            } else {
                source
            }
            keyedLogoBitmap
                ?.takeIf { it !== source && it !== next }
                ?.takeUnless(Bitmap::isRecycled)
                ?.recycle()
            keyedLogoBitmap = next
        }

        LaunchedEffect(streamingEffects.text.customFontUri) {
            customTypeface = streamingEffects.text.customFontUri
                ?.let(Uri::parse)
                ?.let { loadTypefaceFromUri(context, it) }
        }

        LaunchedEffect(
            streamingEffects.text.enabled,
            streamingEffects.text.animation,
        ) {
            if (
                !streamingEffects.text.enabled ||
                streamingEffects.text.animation == TextOverlayAnimation.None
            ) {
                effectAnimationPhase = 0f
                return@LaunchedEffect
            }
            while (true) {
                effectAnimationPhase = (effectAnimationPhase + 0.0165f) % 1f
                delay(33)
            }
        }

        val hasStillBackground = backgroundBitmap != null

        LaunchedEffect(hasStillBackground) {
            if (hasStillBackground) handle = null
        }

        val visibleLayersForEffects = listOfNotNull(
            if (hideAllOverlays) null else lockedLayerA,
            if (hideAllOverlays) null else lockedLayerB,
            currentWorkingLayerOrNull()?.takeIf { !overlayFixedMode || addOverlayPending }
        )

        val latestStreamingEffects by rememberUpdatedState(streamingEffects)
        val latestStreamingLayers by rememberUpdatedState(visibleLayersForEffects)
        val latestStreamingBackground by rememberUpdatedState(backgroundBitmap)
        val latestStreamingHandle by rememberUpdatedState(handle)
        val latestStreamingPreviewSize by rememberUpdatedState(previewSize)
        val latestStreamingOpacity by rememberUpdatedState(opacity)
        val latestStreamingFlip by rememberUpdatedState(flipOverlayAndBackground)
        val latestStreamingBackgroundOpacity by rememberUpdatedState(frozenOverlayOpacity)
        val latestStreamingPictureInPicture by rememberUpdatedState(keyedPictureInPictureBitmap)
        val latestStreamingLogo by rememberUpdatedState(keyedLogoBitmap)
        val latestStreamingTypeface by rememberUpdatedState(customTypeface)
        val latestStreamingAnimationPhase by rememberUpdatedState(effectAnimationPhase)

        LaunchedEffect(
            streamingEffects.output.enabled,
            streamingEffects.output.port,
        ) {
            if (!streamingEffects.output.enabled) {
                mjpegServer.stop()
                streamStatus = context.getString(R.string.fx_mjpeg_off)
                return@LaunchedEffect
            }

            try {
                mjpegServer.start(streamingEffects.output.port)
                streamStatus = context.getString(R.string.fx_mjpeg_serving, mjpegServer.urlHint())
                while (true) {
                    val effects = latestStreamingEffects
                    val source = latestStreamingBackground ?: latestStreamingHandle?.captureFrame?.invoke()
                    val size = latestStreamingPreviewSize
                    if (source != null && size.width > 1 && size.height > 1) {
                        val ownsSource = source !== latestStreamingBackground
                        withContext(Dispatchers.Default) {
                            val scaledSource = scaleBitmapToMaxEdge(source, effects.output.maxEdge)
                            val base = composeLayers(
                                camBitmap = scaledSource,
                                previewSize = size,
                                layers = latestStreamingLayers,
                                variant = EffectVariant.Normal,
                                comparisonMix = latestStreamingOpacity,
                                flipOverlayAndBackground = latestStreamingFlip,
                                frozenOverlayOpacity = latestStreamingBackgroundOpacity,
                                heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                                heatmapPalette = heatmapPalette,
                            )
                            if (scaledSource !== source && !scaledSource.isRecycled) {
                                scaledSource.recycle()
                            }
                            val decorated = renderStreamingEffects(
                                base = base,
                                pictureInPicture = latestStreamingPictureInPicture,
                                logo = latestStreamingLogo,
                                state = effects,
                                customTypeface = latestStreamingTypeface,
                                animationPhase = latestStreamingAnimationPhase,
                            )
                            base.recycle()
                            mjpegServer.updateFrame(decorated, effects.output.jpegQuality)
                            decorated.recycle()
                        }
                        if (ownsSource && !source.isRecycled) source.recycle()
                    }
                    val frameDelay = (1000L / latestStreamingEffects.output.maxFps.coerceAtLeast(1))
                        .coerceAtLeast(16L)
                    delay(frameDelay)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                streamStatus = context.getString(
                    R.string.fx_mjpeg_failed,
                    error.message ?: context.getString(R.string.fx_unknown_error),
                )
            } finally {
                mjpegServer.stop()
            }
        }

        LaunchedEffect(
            variant,
            hasStillBackground,
            backgroundBitmap,
            previewSize,
            visibleLayersForEffects,
            heatmapSimilarityThreshold,
            heatmapPalette
        ) {
            if (variant.isGuidance && hasStillBackground && previewSize.width > 1 && previewSize.height > 1 && visibleLayersForEffects.isNotEmpty()) {
                guidanceUpdating = true
                delay(120)
                var generated: GuidanceRender? = null
                try {
                    val renderSize = guidanceRenderSize(previewSize)
                    val sx = renderSize.width.toFloat() / previewSize.width
                    val sy = renderSize.height.toFloat() / previewSize.height
                    generated = withContext(Dispatchers.Default) {
                        buildGuidanceRender(
                            background = backgroundBitmap!!,
                            outputWidth = renderSize.width,
                            outputHeight = renderSize.height,
                            layers = visibleLayersForEffects,
                            similarityThreshold = heatmapSimilarityThreshold,
                            palette = heatmapPalette,
                            visualization = variant.guidanceVisualization!!,
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
                                    sy = sy,
                                )
                            },
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    guidanceBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    guidanceBitmap = generated?.bitmap
                    guidanceImage = generated?.bitmap?.asImageBitmap()
                    guidanceInstruction = generated?.instruction
                    guidanceSummary = generated?.summary
                    generated = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    guidanceBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    guidanceBitmap = null
                    guidanceImage = null
                    guidanceInstruction = null
                    guidanceSummary = null
                    snackbarHostState.showSnackbar("Could not generate the comparison guide")
                } finally {
                    generated?.bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    guidanceUpdating = false
                }
            } else {
                guidanceBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                guidanceBitmap = null
                guidanceImage = null
                guidanceInstruction = null
                guidanceSummary = null
                guidanceUpdating = false
            }
        }

        val pointFixTransformState: TransformableState = rememberTransformableState { zoom, _, _ ->
            if (pointFixMode) pointFixZoom = (pointFixZoom * zoom).coerceIn(1f, 6f)
        }

        val uiState = OverlayUiState(
            hasStillBackground = hasStillBackground,
            cameraAvailable = cameraAvailable,
            cameraSupported = cameraSupported,
            hasOverlay = currentWorkingLayerOrNull() != null || lockedLayerA != null || lockedLayerB != null,
            isOverlayFixed = overlayFixedMode,
            isPointFixMode = pointFixMode,
            isRepositionEnabled = repositionEnabled && !overlayFixedMode,
        )
        val pointMatchUiState = PointMatchUiState(
            completedPointCount = pointLocks.size,
            hasPendingOverlayLandmark = pendingPointSource != null,
        )

        var showTools by remember { mutableStateOf(false) }
        val busyLabel = when {
            recentLoadInProgress -> "Opening recent…"
            saveInProgress -> "Saving image…"
            backgroundLoadInProgress -> "Loading background…"
            overlayLoadInProgress -> "Loading overlay…"
            autoAlignInProgress -> "Matching image…"
            else -> null
        }

        LaunchedEffect(uiState.mode, showTools, busyLabel) {
            if (uiState.mode != OverlayUiMode.EditOverlay || showTools || busyLabel != null) {
                gestureOperation = OverlayGestureOperation.Idle
            }
        }

        LaunchedEffect(uiState.mode) {
            showTools = false
        }

        BackHandler(enabled = showTools) {
            showTools = false
        }

        BackHandler(enabled = pointFixMode && !showTools) {
            pointFixMode = false
            pointFixZoom = 1f
            pendingPointSource = null
            pendingPointScreenPosition = null
        }

        fun freezeCameraBackground(successMessage: String = "Background frozen") {
            reopenGeneration += 1
            recentLoadInProgress = false
            val bmp = handle?.captureFrame?.invoke()
            if (bmp != null) {
                backgroundBitmap?.recycle()
                backgroundBitmap = bmp
                backgroundUri = null
                currentFrozenUri = null
                resetViewport()
                showRecent = false
                showTools = false
                // A copied bitmap makes saving the recent frame safe even when the
                // editor switches scenes before the IO dispatcher finishes.
                val archiveCopy = runCatching {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                if (archiveCopy != null) {
                    coroutineScope.launch {
                        val archivedUri = withContext(Dispatchers.IO) {
                            try {
                                val folder = File(context.filesDir, "recent-freezes")
                                if (!folder.exists() && !folder.mkdirs()) return@withContext null
                                val file = File.createTempFile("frame_", ".jpg", folder)
                                FileOutputStream(file).use { stream ->
                                    if (!archiveCopy.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                                        file.delete()
                                        return@withContext null
                                    }
                                }
                                Uri.fromFile(file)
                            } catch (_: Exception) {
                                null
                            } finally {
                                archiveCopy.recycle()
                            }
                        }
                        archivedUri?.let { uri ->
                            if (backgroundBitmap === bmp) {
                                currentFrozenUri = uri
                                val overlay = currentOverlaySourceUri
                                recentScenes = recentStore.record(
                                    uri.toString(),
                                    overlay?.toString(),
                                    if (overlay != null) "Camera + overlay" else "Camera freeze",
                                )
                            } else {
                                recentScenes = recentStore.record(uri.toString(), null, "Camera freeze")
                            }
                        }
                    }
                }
                coroutineScope.launch { snackbarHostState.showSnackbar(successMessage) }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Camera is still starting") }
            }
        }

        fun returnToLiveCamera() {
            reopenGeneration += 1
            recentLoadInProgress = false
            backgroundBitmap?.recycle()
            backgroundBitmap = null
            backgroundUri = null
            currentFrozenUri = null
            overlayFixedMode = false
            clearOverlayBitmaps()
            hideAllOverlays = false
            addOverlayPending = false
            resetWorkingOverlayTransform(clearPoints = true)
            showRecent = false
            showRestartConfirm = false
            showChrome = true
            showTools = false
        }

        fun reopenRecent(scene: RecentScene) {
            val nextBackground = Uri.parse(scene.backgroundUri)
            val nextOverlay = scene.overlayUri?.let(Uri::parse)
            val sameScene = scene.backgroundUri ==
                (currentFrozenUri ?: backgroundUri)?.toString() &&
                scene.overlayUri == currentOverlaySourceUri?.toString()
            showRecent = false
            showTools = false
            showChrome = true
            if (sameScene && scene.alignment == null) return

            reopenGeneration += 1
            val request = reopenGeneration
            coroutineScope.launch {
                recentLoadInProgress = true
                try {
                    // Transactional reopen: decode both images before mutating the
                    // editor. A missing photo or revoked overlay URI cannot erase
                    // the active composition.
                    val nextBitmap = loadBitmap(nextBackground)
                    if (request != reopenGeneration) {
                        nextBitmap?.recycle()
                        return@launch
                    }
                    if (nextBitmap == null) {
                        snackbarHostState.showSnackbar("Recent background is unavailable or access was revoked")
                        return@launch
                    }
                    val nextOverlayBitmap = if (nextOverlay != null) loadBitmap(nextOverlay) else null
                    if (request != reopenGeneration) {
                        nextBitmap.recycle()
                        nextOverlayBitmap?.recycle()
                        return@launch
                    }
                    if (nextOverlay != null && nextOverlayBitmap == null) {
                        nextBitmap.recycle()
                        snackbarHostState.showSnackbar("Recent overlay is unavailable; current scene kept")
                        return@launch
                    }
                    clearOverlayBitmaps()
                    backgroundBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    backgroundBitmap = nextBitmap
                    backgroundUri = null
                    currentFrozenUri = nextBackground
                    overlayUri = null
                    currentOverlaySourceUri = nextOverlay
                    overlayFixedMode = false
                    hideAllOverlays = false
                    addOverlayPending = false
                    resetWorkingOverlayTransform(clearPoints = true)
                    val fixed = scene.alignment?.normalized()
                    if (fixed != null && nextOverlayBitmap != null) {
                        lockedLayerA = OverlayLayer(
                            bitmap = nextOverlayBitmap,
                            opacity = 1f,
                            scale = fixed.scale,
                            rotation = fixed.rotation,
                            offsetX = fixed.offsetX,
                            offsetY = fixed.offsetY,
                            fixedPoints = emptyList(),
                        )
                        overlayBitmap = null
                        overlayFixedMode = true
                        repositionEnabled = false
                        opacity = fixed.blend
                    } else {
                        overlayBitmap = nextOverlayBitmap
                    }
                    recentScenes = recentStore.record(
                        scene.backgroundUri,
                        scene.overlayUri,
                        scene.label,
                        alignment = fixed,
                    )
                } finally {
                    if (request == reopenGeneration) recentLoadInProgress = false
                }
            }
        }

        fun lockWorkingOverlay() {
            val working = currentWorkingLayerOrNull() ?: return
            val recentBackground = currentFrozenUri ?: backgroundUri
            val recentOverlay = currentOverlaySourceUri
            if (recentBackground != null && recentOverlay != null) {
                recentScenes = recentStore.record(
                    recentBackground.toString(),
                    recentOverlay.toString(),
                    "Frozen overlay alignment",
                    RecentAlignment(
                        scale = working.scale,
                        rotation = working.rotation,
                        offsetX = working.offsetX,
                        offsetY = working.offsetY,
                        blend = opacity,
                    ),
                )
            }
            if (lockedLayerA == null) lockedLayerA = working else lockedLayerB = working
            overlayBitmap = null
            overlayUri = null
            addOverlayPending = false
            overlayFixedMode = true
            repositionEnabled = false
            pointFixMode = false
            showTools = false
        }

        fun reopenLatestOverlayForEditing() {
            val layer = lockedLayerB ?: lockedLayerA ?: return
            if (lockedLayerB != null) lockedLayerB = null else lockedLayerA = null
            overlayBitmap = layer.bitmap
            scaleF = layer.scale
            rotationDeg = layer.rotation
            offsetX = layer.offsetX
            offsetY = layer.offsetY
            pointLocks.clear()
            pointLocks.addAll(layer.fixedPoints)
            pendingPointSource = null
            pendingPointScreenPosition = null
            overlayFixedMode = false
            repositionEnabled = true
            pointFixMode = false
            addOverlayPending = false
            showTools = false
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
                            comparisonMix = opacity,
                            flipOverlayAndBackground = flipOverlayAndBackground,
                            frozenOverlayOpacity = frozenOverlayOpacity,
                            heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                            heatmapPalette = heatmapPalette,
                            streamingEffects = streamingEffects,
                            pictureInPicture = keyedPictureInPictureBitmap,
                            logo = keyedLogoBitmap,
                            customTypeface = customTypeface,
                            animationPhase = effectAnimationPhase,
                        )
                        saveInProgress = false
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }

        LaunchedEffect(retrySaveAfterPermission) {
            if (retrySaveAfterPermission) {
                retrySaveAfterPermission = false
                saveCurrentComposite()
            }
        }

        fun goBackOneStage() {
            when (editorBackActionFor(uiState.mode)) {
                EditorBackAction.None -> Unit
                EditorBackAction.ConfirmNewCamera -> showRestartConfirm = true
                EditorBackAction.RemoveWorkingOverlay -> {
                    clearOverlayBitmaps()
                    overlayFixedMode = false
                    resetWorkingOverlayTransform()
                }
                EditorBackAction.ExitPointMatch -> {
                    pointFixMode = false
                    pointFixZoom = 1f
                }
                EditorBackAction.UnlockOverlay -> reopenLatestOverlayForEditing()
            }
            showRecent = false
            showTools = false
        }

        BackHandler(enabled = showRecent && !showTools) {
            showRecent = false
        }
        BackHandler(enabled = hasStillBackground && !showTools && !showRecent && !pointFixMode) {
            goBackOneStage()
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
            val sceneZoom = if (pointFixMode) pointFixZoom else viewportScale
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sceneZoom
                        scaleY = sceneZoom
                        translationX = if (pointFixMode) 0f else viewportOffsetX
                        translationY = if (pointFixMode) 0f else viewportOffsetY
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

                if (!hasStillBackground && showCameraGrid) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridColor = Color.White.copy(alpha = 0.42f)
                        val lineWidth = 1.dp.toPx()
                        for (fraction in listOf(1f / 3f, 2f / 3f)) {
                            drawLine(
                                gridColor,
                                Offset(size.width * fraction, 0f),
                                Offset(size.width * fraction, size.height),
                                strokeWidth = lineWidth,
                            )
                            drawLine(
                                gridColor,
                                Offset(0f, size.height * fraction),
                                Offset(size.width, size.height * fraction),
                                strokeWidth = lineWidth,
                            )
                        }
                    }
                }

                val gestureBorderColor = when (gestureOperation) {
                    OverlayGestureOperation.MoveOverlay,
                    OverlayGestureOperation.PanView -> MaterialTheme.colorScheme.primary
                    OverlayGestureOperation.TransformOverlay,
                    OverlayGestureOperation.ZoomView -> MaterialTheme.colorScheme.tertiary
                    OverlayGestureOperation.Idle -> Color.Transparent
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
                        stateDescription = when {
                            uiState.showComparisonControls &&
                                gestureOperation != OverlayGestureOperation.Idle -> gestureOperation.instruction
                            uiState.showComparisonControls -> if (repositionEnabled && !overlayFixedMode) {
                                "Reposition enabled. One finger moves; two fingers resize and rotate. View zoom remains available in the fader rail."
                            } else {
                                "Position frozen. One finger pans; two fingers zoom."
                            }
                            uiState.canInspectFrozenScene -> "Frozen background. One finger pans; two fingers zoom."
                            uiState.mode == OverlayUiMode.PointFix -> pointMatchUiState.title
                            uiState.mode == OverlayUiMode.FixedOverlay -> variant.label
                            else -> uiState.title
                        }
                    }
                    .drawBehind {
                        if (gestureOperation != OverlayGestureOperation.Idle) {
                            drawRect(
                                color = gestureBorderColor,
                                style = Stroke(width = 5.dp.toPx()),
                            )
                        }
                    }
                    .then(
                        if (pointFixMode && !overlayFixedMode && busyLabel == null) {
                            Modifier.transformable(pointFixTransformState)
                        } else {
                            Modifier
                        },
                    )
                    .overlayCanvasGestures(
                        enabled = uiState.canInspectFrozenScene &&
                            busyLabel == null &&
                            !showTools &&
                            !pointFixMode,
                        repositionEnabled = uiState.showComparisonControls &&
                            repositionEnabled && !overlayFixedMode,
                        onOperationChange = { gestureOperation = it },
                        onOneFingerPan = { pan ->
                            if (uiState.showComparisonControls && repositionEnabled && !overlayFixedMode) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                viewportOffsetX += pan.x
                                viewportOffsetY += pan.y
                            }
                        },
                        onTwoFingerTransform = { zoom, rotation, centroid ->
                            if (uiState.showComparisonControls && repositionEnabled && !overlayFixedMode) {
                                scaleF = (scaleF * zoom).coerceIn(minScale, maxScale)
                                rotationDeg += rotation
                            } else {
                                val next = zoomViewportAround(
                                    currentScale = viewportScale,
                                    currentOffset = Offset(viewportOffsetX, viewportOffsetY),
                                    zoom = zoom,
                                    centroid = centroid,
                                    viewportCenter = Offset(
                                        previewSize.width / 2f,
                                        previewSize.height / 2f,
                                    ),
                                )
                                viewportScale = next.scale
                                viewportOffsetX = next.offset.x
                                viewportOffsetY = next.offset.y
                            }
                        },
                    )
                    .then(
                        if (pointFixMode) {
                            Modifier.pointerInput(
                                overlayBitmap,
                                hasStillBackground,
                                pendingPointSource,
                                pointLocks.size,
                            ) {
                                detectTapGestures { tap ->
                                    if (busyLabel == null && !overlayFixedMode && hasStillBackground && overlayBitmap != null) {
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
                                                val source = overlaySourceAt(tap)
                                                if (source != null) {
                                                    pendingPointSource = source
                                                    pendingPointScreenPosition = tap
                                                } else {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Tap inside the visible overlay first")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    )

                Canvas(modifier = canvasModifier) {
                val clip = Rect(0f, 0f, size.width, size.height)

                clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                    if (variant.isGuidance) {
                        guidanceImage?.let {
                            drawImage(
                                image = it,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                alpha = opacity.coerceIn(0f, 1f),
                            )
                        }
                    } else if (!hideAllOverlays) {
                        val invertOverlays = variant == EffectVariant.OverlayInverted
                        val drawSize = IntSize(size.width.toInt(), size.height.toInt())

                        if (lockedLayerA != null) {
                            drawOverlayLayer(
                                lockedLayerA!!,
                                previewForDraw = drawSize,
                                invert = invertOverlays,
                                comparisonMix = opacity,
                            )
                        }
                        if (lockedLayerB != null) {
                            drawOverlayLayer(
                                lockedLayerB!!,
                                previewForDraw = drawSize,
                                invert = invertOverlays,
                                comparisonMix = opacity,
                            )
                        }

                        val currentLayer = currentWorkingLayerOrNull()
                        if (currentLayer != null && (!overlayFixedMode || addOverlayPending)) {
                            drawOverlayLayer(
                                currentLayer,
                                previewForDraw = drawSize,
                                invert = invertOverlays,
                                comparisonMix = opacity,
                            )
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

                    drawStreamingEffects(
                        pictureInPicture = keyedPictureInPictureBitmap,
                        logo = keyedLogoBitmap,
                        state = streamingEffects,
                        customTypeface = customTypeface,
                        animationPhase = effectAnimationPhase,
                    )
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

            // Camera-style small top rail; it never demands the full viewport.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 6.dp, top = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.83f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!showChrome) {
                        TextButton(onClick = { showChrome = true }) { Text(stringResource(R.string.rail_controls)) }
                    } else {
                        if (hasStillBackground) {
                            TextButton(onClick = { goBackOneStage() }) { Text(stringResource(R.string.rail_back)) }
                        }
                        TextButton(onClick = {
                            showRecent = !showRecent
                            showTools = false
                        }) { Text(stringResource(R.string.rail_recent)) }
                        if (!hasStillBackground) {
                            val gridState = stringResource(
                                if (showCameraGrid) R.string.rail_grid_state_on else R.string.rail_grid_state_off,
                            )
                            TextButton(
                                onClick = { showCameraGrid = !showCameraGrid },
                                modifier = Modifier.semantics { stateDescription = gridState },
                            ) {
                                Text(stringResource(if (showCameraGrid) R.string.rail_grid_on else R.string.rail_grid))
                            }
                        } else {
                            TextButton(onClick = {
                                if (needsRestartConfirmation(uiState)) showRestartConfirm = true
                                else returnToLiveCamera()
                            }) { Text(stringResource(R.string.rail_new)) }
                        }
                        TextButton(onClick = {
                            showChrome = false
                            showRecent = false
                        }) { Text(stringResource(R.string.rail_hide)) }
                    }
                }
            }

            if (showRecent && showChrome) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 60.dp, start = 12.dp, end = 12.dp)
                        .widthIn(min = 220.dp, max = 320.dp)
                        .heightIn(max = 252.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleSmall)
                        if (recentScenes.isEmpty()) {
                            Text(stringResource(R.string.recent_empty), style = MaterialTheme.typography.bodySmall)
                        }
                        val activeBackground = (currentFrozenUri ?: backgroundUri)?.toString()
                        recentScenes.take(12).forEach { scene ->
                            val isCurrent = activeBackground == scene.backgroundUri &&
                                (currentOverlaySourceUri?.toString() == scene.overlayUri)
                            val overlayName = scene.overlayUri?.let {
                                Uri.parse(it).lastPathSegment?.takeLast(26)
                            }
                            TextButton(
                                onClick = { reopenRecent(scene) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = busyLabel == null,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AsyncImage(
                                        model = Uri.parse(scene.backgroundUri),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop,
                                    )
                                    if (scene.overlayUri != null) {
                                        AsyncImage(
                                            model = Uri.parse(scene.overlayUri),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(MaterialTheme.shapes.small),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            (if (isCurrent) "● " else "") + scene.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                            .format(Date(scene.lastUsedAt))
                                        Text(
                                            (if (scene.alignment != null) "● aligned · " else "") +
                                                (overlayName?.let { "↳ $it · " } ?: "") + time,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { showRecent = false }) { Text(stringResource(R.string.recent_close)) }
                    }
                }
            }

            if (showRestartConfirm) {
                AlertDialog(
                    onDismissRequest = { showRestartConfirm = false },
                    title = { Text(stringResource(R.string.restart_title)) },
                    text = { Text(stringResource(R.string.restart_message)) },
                    confirmButton = {
                        TextButton(onClick = { returnToLiveCamera() }) { Text(stringResource(R.string.restart_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestartConfirm = false }) { Text(stringResource(R.string.restart_cancel)) }
                    },
                )
            }

            AnimatedVisibility(
                visible = gestureOperation != OverlayGestureOperation.Idle,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp),
            ) {
                GestureOperationFeedback(operation = gestureOperation)
            }

            AnimatedVisibility(
                visible = uiState.showComparisonControls && !showTools && showChrome,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            ) {
                RepositionModeToggle(
                    repositionEnabled = repositionEnabled && !overlayFixedMode,
                    enabled = busyLabel == null,
                    onToggle = {
                        gestureOperation = OverlayGestureOperation.Idle
                        if (overlayFixedMode) {
                            reopenLatestOverlayForEditing()
                            resetViewport()
                        } else {
                            repositionEnabled = !repositionEnabled
                            if (repositionEnabled) resetViewport()
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = !showTools && showChrome,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier.navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (uiState.showComparisonControls) {
                        ComparisonControlBar(
                            state = uiState,
                            busyLabel = busyLabel,
                            mix = opacity,
                            viewScale = viewportScale,
                            overlayScale = scaleF,
                            variant = variant,
                            availableVariants = variants.filter(EffectVariant::showInComparisonBar),
                            enabled = busyLabel == null,
                            onMixChange = { opacity = it },
                            onViewScaleChange = { viewportScale = boundedFrozenViewScale(it) },
                            onResetView = { resetViewport() },
                            onOverlayScaleChange = { scaleF = it.coerceIn(minScale, maxScale) },
                            onResetOverlayScale = { scaleF = 1f },
                            onVariantChange = { selected ->
                                variantIndex = variants.indexOf(selected)
                            },
                            onPrimaryAction = { runRecommendedAction() },
                            onOpenTools = { showTools = true },
                        )
                    } else {
                        OverlayControlDock(
                            state = uiState,
                            busyLabel = busyLabel,
                            supportingText = pointMatchUiState.title.takeIf {
                                uiState.mode == OverlayUiMode.PointFix
                            },
                            viewScale = viewportScale,
                            showViewFader = uiState.canInspectFrozenScene,
                            onViewScaleChange = { viewportScale = boundedFrozenViewScale(it) },
                            onResetView = { resetViewport() },
                            onRecommendedAction = { runRecommendedAction() },
                            onOpenTools = { showTools = true },
                        )
                    }
                }
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
                        repositionEnabled = repositionEnabled && !overlayFixedMode,
                        enabled = busyLabel == null,
                        onRecommendedAction = { runRecommendedAction() },
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
                            canAutoAlign = hasStillBackground && overlayBitmap != null,
                            autoAlignInProgress = autoAlignInProgress,
                            autoAlignStatus = autoAlignStatus,
                            onScaleChange = { scaleF = it },
                            onRotationChange = { rotationDeg = it },
                            manualTransform = ManualOverlayTransform(offsetX, offsetY, scaleF),
                            manualBounds = ManualFaderBounds(
                                previewSize.width.toFloat(),
                                previewSize.height.toFloat(),
                            ),
                            onManualValue = { axis, value ->
                                val next = ManualOverlayTransform(offsetX, offsetY, scaleF).withValue(
                                    axis = axis,
                                    proposed = value,
                                    bounds = ManualFaderBounds(
                                        previewSize.width.toFloat(),
                                        previewSize.height.toFloat(),
                                    ),
                                )
                                offsetX = next.x
                                offsetY = next.y
                                scaleF = next.z
                            },
                            onReset = {
                                resetWorkingOverlayTransform(clearPoints = true)
                                hideAllOverlays = false
                            },
                            onResetView = { resetViewport() },
                            onAutoAlign = {
                                val bg = backgroundBitmap
                                val ov = overlayBitmap
                                if (bg != null && ov != null) {
                                    autoAlignInProgress = true
                                    autoAlignStatus = "Matching image patterns…"
                                    coroutineScope.launch {
                                        try {
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
                                                autoAlignStatus = "Auto-aligned by ${String.format(Locale.getDefault(), "%.0f", result.dx)} × ${String.format(Locale.getDefault(), "%.0f", result.dy)} px · confidence ${String.format(Locale.getDefault(), "%.2f", result.score)}"
                                            } else if (result != null) {
                                                autoAlignStatus = "No confident match · best confidence ${String.format(Locale.getDefault(), "%.2f", result.score)}"
                                            } else {
                                                autoAlignStatus = "No usable textured overlay area found"
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            autoAlignStatus = "Auto alignment failed"
                                        } finally {
                                            autoAlignInProgress = false
                                        }
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
                            drawingInstruction = guidanceInstruction.takeIf { variant.isGuidance },
                            canAddOverlay = lockedLayerB == null,
                            onToggleHide = { hideAllOverlays = !hideAllOverlays },
                            onAddOverlay = {
                                addOverlayPending = true
                                overlayFixedMode = false
                                variantIndex = EffectVariant.Normal.ordinal
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
                            onResetView = { resetViewport() },
                        )
                    }

                    StreamingEffectsPanel(
                        state = streamingEffects,
                        outputPortText = outputPortText,
                        streamStatus = streamStatus,
                        presetName = presetName,
                        presetNames = presetNames,
                        obsHost = obsHost,
                        obsPort = obsPort,
                        obsPassword = obsPassword,
                        obsScene = obsScene,
                        obsSecure = obsSecure,
                        obsStatus = obsStatus,
                        obsState = obsState,
                        onStateChange = { streamingEffects = it.normalized() },
                        onOutputPortTextChange = { input ->
                            val digits = input.filter(Char::isDigit).take(5)
                            outputPortText = digits
                            digits.toIntOrNull()
                                ?.takeIf { it in 1024..65535 }
                                ?.let { port ->
                                    streamingEffects = streamingEffects.copy(
                                        output = streamingEffects.output.copy(port = port),
                                    )
                                }
                        },
                        onPickPictureInPicture = {
                            showTools = false
                            launchImagePicker(PickerTarget.PictureInPicture)
                        },
                        onPickLogo = {
                            showTools = false
                            launchImagePicker(PickerTarget.Logo)
                        },
                        onPickCustomFont = {
                            showTools = false
                            launchFontPicker()
                        },
                        onPresetNameChange = { presetName = it },
                        onSavePreset = {
                            val name = presetName.trim().ifBlank { "default" }
                            presetStore.save(name, streamingEffects)
                            presetName = name
                            presetNames = presetStore.names()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.fx_preset_saved, name))
                            }
                        },
                        onLoadPreset = { name ->
                            presetStore.load(name)?.let { loaded ->
                                streamingEffects = loaded
                                outputPortText = loaded.output.port.toString()
                                presetName = name
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.fx_preset_loaded, name))
                                }
                            }
                        },
                        onObsHostChange = { obsHost = it },
                        onObsPortChange = { obsPort = it.filter(Char::isDigit).take(5) },
                        onObsPasswordChange = { obsPassword = it },
                        onObsSceneChange = { obsScene = it },
                        onObsSecureChange = { obsSecure = it },
                        onObsConnect = {
                            obsClient.connect(
                                ObsEndpoint(
                                    host = obsHost,
                                    port = obsPort.toIntOrNull()?.coerceIn(1, 65535) ?: 4455,
                                    password = obsPassword,
                                    secure = obsSecure,
                                ),
                            ) { state, message ->
                                obsState = state
                                obsStatus = message
                            }
                        },
                        onObsDisconnect = {
                            obsClient.disconnect()
                            obsState = ObsConnectionState.Disconnected
                            obsStatus = context.getString(R.string.obs_status_disconnected)
                        },
                        onObsSetScene = {
                            obsClient.setCurrentScene(obsScene.trim()) { ok, message ->
                                obsStatus = if (ok) context.getString(R.string.obs_status_scene_changed) else message ?: context.getString(R.string.obs_status_scene_failed)
                            }
                        },
                        onObsStartStream = {
                            obsClient.startStream { ok, message ->
                                obsStatus = if (ok) context.getString(R.string.obs_status_stream_started) else message ?: context.getString(R.string.obs_status_stream_start_failed)
                            }
                        },
                        onObsStopStream = {
                            obsClient.stopStream { ok, message ->
                                obsStatus = if (ok) context.getString(R.string.obs_status_stream_stopped) else message ?: context.getString(R.string.obs_status_stream_stop_failed)
                            }
                        },
                        onObsStartVirtualCamera = {
                            obsClient.startVirtualCamera { ok, message ->
                                obsStatus = if (ok) context.getString(R.string.obs_status_vcam_started) else message ?: context.getString(R.string.obs_status_vcam_start_failed)
                            }
                        },
                        onObsStopVirtualCamera = {
                            obsClient.stopVirtualCamera { ok, message ->
                                obsStatus = if (ok) context.getString(R.string.obs_status_vcam_stopped) else message ?: context.getString(R.string.obs_status_vcam_stop_failed)
                            }
                        },
                    )
                }
            }

        }
    }
}

private suspend fun decodeBitmapForEditor(
    context: android.content.Context,
    uri: Uri,
    maxEdge: Int = 2048,
): Bitmap? = withContext(Dispatchers.IO) {
    try {
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
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            decoded?.let { bitmap ->
                val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
                applyExifOrientation(bitmap, orientation)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }

    val transformed = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true,
    )
    if (transformed !== bitmap) bitmap.recycle()
    return transformed
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

internal fun guidanceRenderSize(previewSize: IntSize, maxEdge: Int = 720): IntSize {
    if (previewSize.width <= 0 || previewSize.height <= 0 || maxEdge <= 0) return IntSize(1, 1)
    val longest = kotlin.math.max(previewSize.width, previewSize.height)
    if (longest <= maxEdge) return previewSize
    val scale = maxEdge.toFloat() / longest
    return IntSize(
        width = kotlin.math.max(1, (previewSize.width * scale).toInt()),
        height = kotlin.math.max(1, (previewSize.height * scale).toInt()),
    )
}

private fun DrawScope.drawOverlayLayer(
    layer: OverlayLayer,
    previewForDraw: IntSize,
    invert: Boolean,
    comparisonMix: Float,
) {
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
            alpha = (layer.opacity * comparisonMix.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
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
    comparisonMix: Float,
    flipOverlayAndBackground: Boolean,
    frozenOverlayOpacity: Float,
    heatmapSimilarityThreshold: Float,
    heatmapPalette: HeatmapPalette,
    streamingEffects: StreamingEffectsState,
    pictureInPicture: Bitmap?,
    logo: Bitmap?,
    customTypeface: Typeface?,
    animationPhase: Float,
): SaveCompositeResult {
    return try {
        val outBitmap = withContext(Dispatchers.Default) {
            val base = composeLayers(
                camBitmap = frozen,
                previewSize = previewSize,
                layers = layers,
                variant = variant,
                comparisonMix = comparisonMix,
                flipOverlayAndBackground = flipOverlayAndBackground,
                frozenOverlayOpacity = frozenOverlayOpacity,
                heatmapSimilarityThreshold = heatmapSimilarityThreshold,
                heatmapPalette = heatmapPalette,
            )
            val decorated = renderStreamingEffects(
                base = base,
                pictureInPicture = pictureInPicture,
                logo = logo,
                state = streamingEffects,
                customTypeface = customTypeface,
                animationPhase = animationPhase,
            )
            base.recycle()
            decorated
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
            } catch (cancelled: CancellationException) {
                insertedUri?.let { runCatching { resolver.delete(it, null, null) } }
                throw cancelled
            } catch (error: Exception) {
                insertedUri?.let { runCatching { resolver.delete(it, null, null) } }
                SaveCompositeResult("Save failed: ${error.message ?: "unknown error"}")
            } finally {
                outBitmap.recycle()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
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
    try {
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

        return AutoAlignResult(
            dx = fine.dx / downscale,
            dy = fine.dy / downscale,
            score = fine.score,
            comparedSamples = fine.comparedSamples,
        )
    } finally {
        if (backgroundMatch !== background && !backgroundMatch.isRecycled) backgroundMatch.recycle()
        if (!overlayMatch.isRecycled) overlayMatch.recycle()
    }
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
    comparisonMix: Float,
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

    if (!flipOverlayAndBackground) {
        canvas.drawBitmap(renderedBackground, 0f, 0f, basePaint)
    }

    if (variant.isGuidance) {
        val guideSize = guidanceRenderSize(
            IntSize(renderedBackground.width, renderedBackground.height),
            maxEdge = 1440,
        )
        val guideSx = guideSize.width.toFloat() / previewSize.width
        val guideSy = guideSize.height.toFloat() / previewSize.height
        val guidance = buildGuidanceRender(
            background = renderedBackground,
            outputWidth = guideSize.width,
            outputHeight = guideSize.height,
            layers = layers,
            similarityThreshold = heatmapSimilarityThreshold,
            palette = heatmapPalette,
            visualization = variant.guidanceVisualization!!,
            matrixForLayer = { layer ->
                buildOverlayMatrix(
                    previewSize = previewSize,
                    overlay = layer.bitmap,
                    scale = layer.scale,
                    rotation = layer.rotation,
                    offsetX = layer.offsetX,
                    offsetY = layer.offsetY,
                    fixedPoints = layer.fixedPoints,
                    sx = guideSx,
                    sy = guideSy
                )
            }
        )
        if (guidance != null) {
            canvas.drawBitmap(
                guidance.bitmap,
                null,
                android.graphics.Rect(
                    0,
                    0,
                    renderedBackground.width,
                    renderedBackground.height,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (comparisonMix.coerceIn(0f, 1f) * 255).toInt()
                    isFilterBitmap = true
                },
            )
            guidance.bitmap.recycle()
        }
    }

    val invertOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = if (variant == EffectVariant.OverlayInverted) invertAndroidColorFilter() else null
        isFilterBitmap = true
    }

    if (!variant.isGuidance) {
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
            invertOverlayPaint.alpha =
                (layer.opacity * comparisonMix.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
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

private data class GuidanceRender(
    val bitmap: Bitmap,
    val summary: OverlayDiffSummary,
    val instruction: DrawingInstruction,
)

private fun buildGuidanceRender(
    background: Bitmap,
    outputWidth: Int,
    outputHeight: Int,
    layers: List<OverlayLayer>,
    similarityThreshold: Float,
    palette: HeatmapPalette,
    visualization: GuidanceVisualization,
    matrixForLayer: (OverlayLayer) -> Matrix
): GuidanceRender? {
    if (layers.isEmpty()) return null
    if (outputWidth <= 0 || outputHeight <= 0) return null

    val bg = scaledCenterCropBitmap(background, outputWidth, outputHeight)
    val overlayComposite = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    try {
        val overlayCanvas = Canvas(overlayComposite)
        layers.forEach { layer ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = 255
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
            visualization = visualization,
        )

        val out = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        out.setPixels(analysis.guidancePixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return GuidanceRender(
            bitmap = out,
            summary = analysis.summary,
            instruction = analysis.instruction,
        )
    } finally {
        if (!overlayComposite.isRecycled) overlayComposite.recycle()
        if (bg !== background && !bg.isRecycled) bg.recycle()
    }
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
