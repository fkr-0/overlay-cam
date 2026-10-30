package com.example.overlaycam

enum class OverlayUiMode {
    Setup,
    BackgroundReady,
    EditOverlay,
    PointFix,
    FixedOverlay,
}

enum class OverlayRecommendedAction {
    FreezeFrame,
    PickBackground,
    PickOverlay,
    FinishAdjusting,
    FinishPointFix,
    SaveImage,
}

data class OverlayUiState(
    val hasStillBackground: Boolean,
    val cameraAvailable: Boolean = true,
    val cameraSupported: Boolean = true,
    val hasOverlay: Boolean = false,
    val isOverlayFixed: Boolean = false,
    val isPointFixMode: Boolean = false,
    val isRepositionEnabled: Boolean = true,
) {
    val mode: OverlayUiMode = when {
        !hasStillBackground -> OverlayUiMode.Setup
        isOverlayFixed && hasOverlay -> OverlayUiMode.FixedOverlay
        isPointFixMode && hasOverlay -> OverlayUiMode.PointFix
        hasOverlay -> OverlayUiMode.EditOverlay
        else -> OverlayUiMode.BackgroundReady
    }

    val stageLabel: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) "LIVE" else "PHOTOS"
        OverlayUiMode.BackgroundReady -> "BACKGROUND"
        OverlayUiMode.EditOverlay -> "ALIGN"
        OverlayUiMode.PointFix -> "POINTS"
        OverlayUiMode.FixedOverlay -> "REVIEW"
    }

    val title: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) "Live camera" else "Photo mode"
        OverlayUiMode.BackgroundReady -> "Background ready"
        OverlayUiMode.EditOverlay -> if (isRepositionEnabled) "Position overlay" else "Inspect alignment"
        OverlayUiMode.PointFix -> "Match points"
        OverlayUiMode.FixedOverlay -> "Review comparison"
    }

    val guidance: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            "Hold the camera still, then freeze the frame. You can also use a photo as the background."
        } else if (!cameraSupported) {
            "This device has no usable camera. Choose a background photo to start."
        } else {
            "Choose a background photo to start. Camera access remains optional and can be enabled from Options."
        }
        OverlayUiMode.BackgroundReady -> "Add the image you want to compare or trace over the frozen background."
        OverlayUiMode.EditOverlay -> if (isRepositionEnabled) {
            "Position the reference with one finger to move and two fingers to resize or rotate. Freeze it when alignment is ready."
        } else {
            "Position is frozen. Use one finger to pan and two fingers to zoom while checking values, similarity, and contours."
        }
        OverlayUiMode.PointFix -> "For each pair, tap a landmark on the overlay first, then tap where it belongs on the background."
        OverlayUiMode.FixedOverlay -> "Position is frozen. Pan, zoom, and switch guides without disturbing alignment; reposition at any time."
    }

    val recommendedAction: OverlayRecommendedAction = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            OverlayRecommendedAction.FreezeFrame
        } else {
            OverlayRecommendedAction.PickBackground
        }
        OverlayUiMode.BackgroundReady -> OverlayRecommendedAction.PickOverlay
        OverlayUiMode.EditOverlay -> OverlayRecommendedAction.FinishAdjusting
        OverlayUiMode.PointFix -> OverlayRecommendedAction.FinishPointFix
        OverlayUiMode.FixedOverlay -> OverlayRecommendedAction.SaveImage
    }

    val recommendedActionLabel: String = when (recommendedAction) {
        OverlayRecommendedAction.FreezeFrame -> "Freeze frame"
        OverlayRecommendedAction.PickBackground -> "Choose background photo"
        OverlayRecommendedAction.PickOverlay -> "Add overlay image"
        OverlayRecommendedAction.FinishAdjusting -> "Use this position"
        OverlayRecommendedAction.FinishPointFix -> "Finish matching"
        OverlayRecommendedAction.SaveImage -> "Save image"
    }

    val compactActionLabel: String = when (recommendedAction) {
        OverlayRecommendedAction.FreezeFrame -> "Freeze"
        OverlayRecommendedAction.PickBackground -> "Photo"
        OverlayRecommendedAction.PickOverlay -> "Add image"
        OverlayRecommendedAction.FinishAdjusting -> "Set"
        OverlayRecommendedAction.FinishPointFix -> "Done"
        OverlayRecommendedAction.SaveImage -> "Save"
    }

    val recommendedActionHint: String = when (mode) {
        OverlayUiMode.Setup -> if (cameraAvailable) {
            "Hold still, then freeze the camera frame."
        } else {
            "Choose a photo to use as the background."
        }
        OverlayUiMode.BackgroundReady -> "Pan with one finger or zoom the frozen scene, then add an overlay image."
        OverlayUiMode.EditOverlay -> if (isRepositionEnabled) {
            "Move with one finger; resize and rotate with two."
        } else {
            "Frozen: pan with one finger and zoom with two."
        }
        OverlayUiMode.PointFix -> "Finish when the matching points are placed correctly."
        OverlayUiMode.FixedOverlay -> "Inspect the guides, then save or reposition."
    }

    val expandActionLabel: String = when (mode) {
        OverlayUiMode.Setup,
        OverlayUiMode.BackgroundReady -> "Options"
        OverlayUiMode.EditOverlay -> "Tools"
        OverlayUiMode.PointFix -> "Point tools"
        OverlayUiMode.FixedOverlay -> "More actions"
    }

    val canInspectFrozenScene: Boolean = hasStillBackground && mode != OverlayUiMode.PointFix
    val showComparisonControls: Boolean = mode == OverlayUiMode.EditOverlay || mode == OverlayUiMode.FixedOverlay
    val showGestureHint: Boolean = mode == OverlayUiMode.EditOverlay
    val showPointFixPanel: Boolean = mode == OverlayUiMode.PointFix
    val showEditPanel: Boolean = mode == OverlayUiMode.EditOverlay
    val showFixedOverlayActions: Boolean = mode == OverlayUiMode.FixedOverlay
}

/**
 * A recent entry is a pair of reopenable URIs, not a persisted Android Bitmap.
 * Camera freezes are saved as app-private files and represented by file URIs.
 */

internal enum class EditorBackAction {
    None,
    ConfirmNewCamera,
    RemoveWorkingOverlay,
    ExitPointMatch,
    UnlockOverlay,
}

internal fun editorBackActionFor(mode: OverlayUiMode): EditorBackAction = when (mode) {
    OverlayUiMode.Setup -> EditorBackAction.None
    OverlayUiMode.BackgroundReady -> EditorBackAction.ConfirmNewCamera
    OverlayUiMode.EditOverlay -> EditorBackAction.RemoveWorkingOverlay
    OverlayUiMode.PointFix -> EditorBackAction.ExitPointMatch
    OverlayUiMode.FixedOverlay -> EditorBackAction.UnlockOverlay
}

internal fun needsRestartConfirmation(state: OverlayUiState): Boolean =
    state.hasStillBackground || state.hasOverlay

/** User-committed placement, not a viewport-only inspection zoom. */
internal data class RecentAlignment(
    val scale: Float,
    val rotation: Float,
    val offsetX: Float,
    val offsetY: Float,
    val blend: Float,
) {
    fun normalized(): RecentAlignment = RecentAlignment(
        scale = scale.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: 1f,
        rotation = rotation.takeIf(Float::isFinite)?.coerceIn(-3600f, 3600f) ?: 0f,
        offsetX = offsetX.takeIf(Float::isFinite)?.coerceIn(-32768f, 32768f) ?: 0f,
        offsetY = offsetY.takeIf(Float::isFinite)?.coerceIn(-32768f, 32768f) ?: 0f,
        blend = blend.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.7f,
    )
}

internal data class RecentScene(
    val backgroundUri: String,
    val overlayUri: String? = null,
    val label: String = "Frozen camera",
    val lastUsedAt: Long = 0L,
    val alignment: RecentAlignment? = null,
)

internal fun recentScenesWith(
    existing: List<RecentScene>,
    incoming: RecentScene,
    limit: Int = 12,
): List<RecentScene> =
    (listOf(incoming) + existing)
        .filter { it.backgroundUri.isNotBlank() }
        .distinctBy { it.backgroundUri to it.overlayUri }
        .take(limit.coerceAtLeast(0))

/** Local URI-only history. Reopening a revoked external document may fail safely. */
internal class RecentSceneStore(context: android.content.Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "overlay_cam_recent_scenes", android.content.Context.MODE_PRIVATE,
    )

    fun list(): List<RecentScene> = try {
        val entries = org.json.JSONArray(prefs.getString("entries", "[]") ?: "[]")
        buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val bg = item.optString("background", "")
                if (bg.isBlank()) continue
                add(RecentScene(
                    backgroundUri = bg,
                    overlayUri = item.optString("overlay", "").takeIf(String::isNotBlank),
                    label = item.optString("label", "Recent comparison"),
                    lastUsedAt = item.optLong("time", 0L),
                    alignment = item.optJSONObject("alignment")?.let { state ->
                        RecentAlignment(
                            scale = state.optDouble("scale", 1.0).toFloat(),
                            rotation = state.optDouble("rotation", 0.0).toFloat(),
                            offsetX = state.optDouble("x", 0.0).toFloat(),
                            offsetY = state.optDouble("y", 0.0).toFloat(),
                            blend = state.optDouble("blend", 0.7).toFloat(),
                        ).normalized()
                    },
                ))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun record(
        backgroundUri: String,
        overlayUri: String?,
        label: String,
        alignment: RecentAlignment? = null,
    ): List<RecentScene> {
        val next = recentScenesWith(list(), RecentScene(
            backgroundUri = backgroundUri,
            overlayUri = overlayUri,
            label = label,
            lastUsedAt = System.currentTimeMillis(),
            alignment = alignment?.normalized(),
        ))
        val array = org.json.JSONArray()
        next.forEach { entry ->
            array.put(org.json.JSONObject().apply {
                put("background", entry.backgroundUri)
                put("overlay", entry.overlayUri ?: "")
                put("label", entry.label)
                put("time", entry.lastUsedAt)
                entry.alignment?.let { alignment ->
                    put("alignment", org.json.JSONObject().apply {
                        put("scale", alignment.scale.toDouble())
                        put("rotation", alignment.rotation.toDouble())
                        put("x", alignment.offsetX.toDouble())
                        put("y", alignment.offsetY.toDouble())
                        put("blend", alignment.blend.toDouble())
                    })
                }
            })
        }
        prefs.edit().putString("entries", array.toString()).apply()
        return next
    }
}
