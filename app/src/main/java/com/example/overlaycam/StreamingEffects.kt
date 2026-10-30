package com.example.overlaycam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ChromaTarget {
    PictureInPicture,
    Logo,
    Both,
}

internal enum class TextOverlayAnimation {
    None,
    Pulse,
    Slide,
}

internal enum class TextOverlayFont {
    Sans,
    Serif,
    Monospace,
    Cursive,
    Custom,
}

internal data class ChromaKeySettings(
    val enabled: Boolean = false,
    val target: ChromaTarget = ChromaTarget.PictureInPicture,
    val keyColor: Int = Color.GREEN,
    val tolerance: Float = 0.26f,
    val softness: Float = 0.08f,
) {
    fun normalized(): ChromaKeySettings = copy(
        tolerance = tolerance.coerceIn(0f, 0.95f),
        softness = softness.coerceIn(0.01f, 0.5f),
    )
}

internal data class PictureInPictureSettings(
    val enabled: Boolean = false,
    val imageUri: String? = null,
    val centerX: Float = 0.78f,
    val centerY: Float = 0.76f,
    val widthFraction: Float = 0.30f,
    val opacity: Float = 1f,
    val cornerRadiusFraction: Float = 0.04f,
) {
    fun normalized(): PictureInPictureSettings = copy(
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        widthFraction = widthFraction.coerceIn(0.10f, 0.75f),
        opacity = opacity.coerceIn(0f, 1f),
        cornerRadiusFraction = cornerRadiusFraction.coerceIn(0f, 0.2f),
    )
}

internal data class LogoOverlaySettings(
    val enabled: Boolean = false,
    val imageUri: String? = null,
    val centerX: Float = 0.86f,
    val centerY: Float = 0.12f,
    val widthFraction: Float = 0.18f,
    val opacity: Float = 0.9f,
) {
    fun normalized(): LogoOverlaySettings = copy(
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        widthFraction = widthFraction.coerceIn(0.05f, 0.6f),
        opacity = opacity.coerceIn(0f, 1f),
    )
}

internal data class TextOverlaySettings(
    val enabled: Boolean = false,
    val text: String = "",
    val x: Float = 0.06f,
    val y: Float = 0.10f,
    val sizeSp: Float = 34f,
    val opacity: Float = 1f,
    val color: Int = Color.WHITE,
    val font: TextOverlayFont = TextOverlayFont.Sans,
    val customFontUri: String? = null,
    val animation: TextOverlayAnimation = TextOverlayAnimation.None,
) {
    fun normalized(): TextOverlaySettings = copy(
        x = x.coerceIn(0f, 0.95f),
        y = y.coerceIn(0f, 0.95f),
        sizeSp = sizeSp.coerceIn(12f, 120f),
        opacity = opacity.coerceIn(0f, 1f),
    )
}

internal data class StreamingOutputSettings(
    val enabled: Boolean = false,
    val port: Int = 8088,
    val maxFps: Int = 30,
    val jpegQuality: Int = 82,
    val maxEdge: Int = 1280,
) {
    fun normalized(): StreamingOutputSettings = copy(
        port = port.coerceIn(1024, 65535),
        maxFps = maxFps.coerceIn(1, 60),
        jpegQuality = jpegQuality.coerceIn(40, 95),
        maxEdge = maxEdge.coerceIn(320, 1920),
    )
}

internal data class StreamingEffectsState(
    val chromaKey: ChromaKeySettings = ChromaKeySettings(),
    val pictureInPicture: PictureInPictureSettings = PictureInPictureSettings(),
    val logo: LogoOverlaySettings = LogoOverlaySettings(),
    val text: TextOverlaySettings = TextOverlaySettings(),
    val output: StreamingOutputSettings = StreamingOutputSettings(),
) {
    fun normalized(): StreamingEffectsState = copy(
        chromaKey = chromaKey.normalized(),
        pictureInPicture = pictureInPicture.normalized(),
        logo = logo.normalized(),
        text = text.normalized(),
        output = output.normalized(),
    )
}

internal object EffectPresetCodec {
    private const val VERSION = "1"

    fun encode(state: StreamingEffectsState): String {
        val normalized = state.normalized()
        val values = linkedMapOf(
            "version" to VERSION,
            "chroma.enabled" to normalized.chromaKey.enabled.toString(),
            "chroma.target" to normalized.chromaKey.target.name,
            "chroma.color" to normalized.chromaKey.keyColor.toString(),
            "chroma.tolerance" to normalized.chromaKey.tolerance.toString(),
            "chroma.softness" to normalized.chromaKey.softness.toString(),
            "pip.enabled" to normalized.pictureInPicture.enabled.toString(),
            "pip.uri" to normalized.pictureInPicture.imageUri.orEmpty(),
            "pip.x" to normalized.pictureInPicture.centerX.toString(),
            "pip.y" to normalized.pictureInPicture.centerY.toString(),
            "pip.width" to normalized.pictureInPicture.widthFraction.toString(),
            "pip.opacity" to normalized.pictureInPicture.opacity.toString(),
            "pip.radius" to normalized.pictureInPicture.cornerRadiusFraction.toString(),
            "logo.enabled" to normalized.logo.enabled.toString(),
            "logo.uri" to normalized.logo.imageUri.orEmpty(),
            "logo.x" to normalized.logo.centerX.toString(),
            "logo.y" to normalized.logo.centerY.toString(),
            "logo.width" to normalized.logo.widthFraction.toString(),
            "logo.opacity" to normalized.logo.opacity.toString(),
            "text.enabled" to normalized.text.enabled.toString(),
            "text.value" to normalized.text.text,
            "text.x" to normalized.text.x.toString(),
            "text.y" to normalized.text.y.toString(),
            "text.size" to normalized.text.sizeSp.toString(),
            "text.opacity" to normalized.text.opacity.toString(),
            "text.color" to normalized.text.color.toString(),
            "text.font" to normalized.text.font.name,
            "text.fontUri" to normalized.text.customFontUri.orEmpty(),
            "text.animation" to normalized.text.animation.name,
            "output.port" to normalized.output.port.toString(),
            "output.fps" to normalized.output.maxFps.toString(),
            "output.quality" to normalized.output.jpegQuality.toString(),
            "output.maxEdge" to normalized.output.maxEdge.toString(),
        )
        return values.entries.joinToString("\n") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
    }

    fun decode(encoded: String): StreamingEffectsState? {
        val values = encoded.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else {
                    val key = line.substring(0, separator)
                    val value = runCatching {
                        URLDecoder.decode(line.substring(separator + 1), Charsets.UTF_8.name())
                    }.getOrNull() ?: return@mapNotNull null
                    key to value
                }
            }
            .toMap()

        if (values["version"] != VERSION) return null

        fun bool(key: String, fallback: Boolean) = values[key]?.toBooleanStrictOrNull() ?: fallback
        fun float(key: String, fallback: Float) = values[key]?.toFloatOrNull() ?: fallback
        fun int(key: String, fallback: Int) = values[key]?.toIntOrNull() ?: fallback
        fun stringOrNull(key: String) = values[key]?.takeIf(String::isNotBlank)

        val defaults = StreamingEffectsState()
        return StreamingEffectsState(
            chromaKey = ChromaKeySettings(
                enabled = bool("chroma.enabled", defaults.chromaKey.enabled),
                target = values["chroma.target"]?.let { runCatching { ChromaTarget.valueOf(it) }.getOrNull() }
                    ?: defaults.chromaKey.target,
                keyColor = int("chroma.color", defaults.chromaKey.keyColor),
                tolerance = float("chroma.tolerance", defaults.chromaKey.tolerance),
                softness = float("chroma.softness", defaults.chromaKey.softness),
            ),
            pictureInPicture = PictureInPictureSettings(
                enabled = bool("pip.enabled", defaults.pictureInPicture.enabled),
                imageUri = stringOrNull("pip.uri"),
                centerX = float("pip.x", defaults.pictureInPicture.centerX),
                centerY = float("pip.y", defaults.pictureInPicture.centerY),
                widthFraction = float("pip.width", defaults.pictureInPicture.widthFraction),
                opacity = float("pip.opacity", defaults.pictureInPicture.opacity),
                cornerRadiusFraction = float("pip.radius", defaults.pictureInPicture.cornerRadiusFraction),
            ),
            logo = LogoOverlaySettings(
                enabled = bool("logo.enabled", defaults.logo.enabled),
                imageUri = stringOrNull("logo.uri"),
                centerX = float("logo.x", defaults.logo.centerX),
                centerY = float("logo.y", defaults.logo.centerY),
                widthFraction = float("logo.width", defaults.logo.widthFraction),
                opacity = float("logo.opacity", defaults.logo.opacity),
            ),
            text = TextOverlaySettings(
                enabled = bool("text.enabled", defaults.text.enabled),
                text = values["text.value"].orEmpty(),
                x = float("text.x", defaults.text.x),
                y = float("text.y", defaults.text.y),
                sizeSp = float("text.size", defaults.text.sizeSp),
                opacity = float("text.opacity", defaults.text.opacity),
                color = int("text.color", defaults.text.color),
                font = values["text.font"]?.let { runCatching { TextOverlayFont.valueOf(it) }.getOrNull() }
                    ?: defaults.text.font,
                customFontUri = stringOrNull("text.fontUri"),
                animation = values["text.animation"]?.let {
                    runCatching { TextOverlayAnimation.valueOf(it) }.getOrNull()
                } ?: defaults.text.animation,
            ),
            output = StreamingOutputSettings(
                enabled = false,
                port = int("output.port", defaults.output.port),
                maxFps = int("output.fps", defaults.output.maxFps),
                jpegQuality = int("output.quality", defaults.output.jpegQuality),
                maxEdge = int("output.maxEdge", defaults.output.maxEdge),
            ),
        ).normalized()
    }
}

internal class EffectPresetStore(context: Context) {
    private val preferences = context.getSharedPreferences("overlay_cam_effect_presets", Context.MODE_PRIVATE)

    fun save(name: String, state: StreamingEffectsState) {
        val normalizedName = name.trim().take(64).ifBlank { "default" }
        val names = names().toMutableSet().apply { add(normalizedName) }
        preferences.edit()
            .putString("preset:$normalizedName", EffectPresetCodec.encode(state))
            .putStringSet("preset_names", names)
            .putString("last_preset", normalizedName)
            .apply()
    }

    fun load(name: String): StreamingEffectsState? =
        preferences.getString("preset:${name.trim()}", null)?.let(EffectPresetCodec::decode)

    fun names(): List<String> =
        preferences.getStringSet("preset_names", emptySet()).orEmpty().sorted()

    fun lastPresetName(): String? = preferences.getString("last_preset", null)
}

internal fun chromaKeyCopy(source: Bitmap, settings: ChromaKeySettings): Bitmap {
    val normalized = settings.normalized()
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val keyR = Color.red(normalized.keyColor)
    val keyG = Color.green(normalized.keyColor)
    val keyB = Color.blue(normalized.keyColor)
    val maxDistance = 441.67295f

    for (index in pixels.indices) {
        val pixel = pixels[index]
        val alpha = Color.alpha(pixel)
        if (alpha == 0) continue

        val dr = Color.red(pixel) - keyR
        val dg = Color.green(pixel) - keyG
        val db = Color.blue(pixel) - keyB
        val distance = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat()) / maxDistance
        val alphaFactor = ((distance - normalized.tolerance) / normalized.softness).coerceIn(0f, 1f)
        val nextAlpha = (alpha * alphaFactor).roundToInt().coerceIn(0, 255)
        pixels[index] = Color.argb(nextAlpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

internal fun DrawScope.drawStreamingEffects(
    pictureInPicture: Bitmap?,
    logo: Bitmap?,
    state: StreamingEffectsState,
    customTypeface: Typeface?,
    animationPhase: Float,
) {
    drawIntoCanvas { composeCanvas ->
        drawStreamingEffectsOnCanvas(
            canvas = composeCanvas.nativeCanvas,
            width = size.width,
            height = size.height,
            pictureInPicture = pictureInPicture,
            logo = logo,
            state = state,
            customTypeface = customTypeface,
            animationPhase = animationPhase,
            density = density,
        )
    }
}

internal fun renderStreamingEffects(
    base: Bitmap,
    pictureInPicture: Bitmap?,
    logo: Bitmap?,
    state: StreamingEffectsState,
    customTypeface: Typeface?,
    animationPhase: Float,
): Bitmap {
    val output = base.copy(Bitmap.Config.ARGB_8888, true)
    drawStreamingEffectsOnCanvas(
        canvas = Canvas(output),
        width = output.width.toFloat(),
        height = output.height.toFloat(),
        pictureInPicture = pictureInPicture,
        logo = logo,
        state = state,
        customTypeface = customTypeface,
        animationPhase = animationPhase,
        density = 1f,
    )
    return output
}

private fun drawStreamingEffectsOnCanvas(
    canvas: Canvas,
    width: Float,
    height: Float,
    pictureInPicture: Bitmap?,
    logo: Bitmap?,
    state: StreamingEffectsState,
    customTypeface: Typeface?,
    animationPhase: Float,
    density: Float,
) {
    val normalized = state.normalized()

    if (normalized.pictureInPicture.enabled && pictureInPicture != null && !pictureInPicture.isRecycled) {
        drawPositionedBitmap(
            canvas = canvas,
            bitmap = pictureInPicture,
            width = width,
            height = height,
            centerX = normalized.pictureInPicture.centerX,
            centerY = normalized.pictureInPicture.centerY,
            widthFraction = normalized.pictureInPicture.widthFraction,
            opacity = normalized.pictureInPicture.opacity,
            cornerRadiusFraction = normalized.pictureInPicture.cornerRadiusFraction,
        )
    }

    if (normalized.logo.enabled && logo != null && !logo.isRecycled) {
        drawPositionedBitmap(
            canvas = canvas,
            bitmap = logo,
            width = width,
            height = height,
            centerX = normalized.logo.centerX,
            centerY = normalized.logo.centerY,
            widthFraction = normalized.logo.widthFraction,
            opacity = normalized.logo.opacity,
            cornerRadiusFraction = 0f,
        )
    }

    if (normalized.text.enabled && normalized.text.text.isNotBlank()) {
        val phase = animationPhase.coerceIn(0f, 1f)
        val pulse = if (normalized.text.animation == TextOverlayAnimation.Pulse) {
            0.72f + 0.28f * ((sin(phase * 2f * PI).toFloat() + 1f) / 2f)
        } else {
            1f
        }
        val slideX = if (normalized.text.animation == TextOverlayAnimation.Slide) {
            (phase * 2f - 1f) * width * 0.08f
        } else {
            0f
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = normalized.text.color
            alpha = (normalized.text.opacity * pulse * 255f).roundToInt().coerceIn(0, 255)
            textSize = normalized.text.sizeSp * density
            typeface = resolveTypeface(normalized.text.font, customTypeface)
            setShadowLayer(max(1f, textSize * 0.08f), 0f, textSize * 0.04f, Color.argb(150, 0, 0, 0))
        }
        val x = normalized.text.x * width + slideX
        val y = normalized.text.y * height + paint.textSize
        canvas.drawText(normalized.text.text, x, y, paint)
    }
}

private fun drawPositionedBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    width: Float,
    height: Float,
    centerX: Float,
    centerY: Float,
    widthFraction: Float,
    opacity: Float,
    cornerRadiusFraction: Float,
) {
    val targetWidth = width * widthFraction
    val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
    val targetHeight = targetWidth * aspect
    val left = centerX * width - targetWidth / 2f
    val top = centerY * height - targetHeight / 2f
    val destination = RectF(left, top, left + targetWidth, top + targetHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        isFilterBitmap = true
    }

    val save = canvas.save()
    if (cornerRadiusFraction > 0f) {
        val radius = minOf(destination.width(), destination.height()) * cornerRadiusFraction
        val path = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
        canvas.clipPath(path)
    }
    canvas.drawBitmap(bitmap, null, destination, paint)
    canvas.restoreToCount(save)
}

private fun resolveTypeface(font: TextOverlayFont, customTypeface: Typeface?): Typeface = when (font) {
    TextOverlayFont.Sans -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    TextOverlayFont.Serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
    TextOverlayFont.Monospace -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    TextOverlayFont.Cursive -> Typeface.create("cursive", Typeface.BOLD)
    TextOverlayFont.Custom -> customTypeface ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}

internal suspend fun loadTypefaceFromUri(context: Context, uri: Uri): Typeface? = withContext(Dispatchers.IO) {
    runCatching {
        val temp = File.createTempFile("overlaycam-font-", ".font", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Typeface.createFromFile(temp)
        } finally {
            temp.delete()
        }
    }.getOrNull()
}

internal fun scaleBitmapToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
    val longest = max(source.width, source.height)
    if (longest <= maxEdge) return source
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(
        source,
        max(1, (source.width * scale).roundToInt()),
        max(1, (source.height * scale).roundToInt()),
        true,
    )
}
