package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamingEffectsTest {
    @Test
    fun presetCodecRoundTripsUserVisibleEffectsAndKeepsOutputStopped() {
        val original = StreamingEffectsState(
            chromaKey = ChromaKeySettings(
                enabled = true,
                target = ChromaTarget.Both,
                keyColor = 0xFF123456.toInt(),
                tolerance = 0.31f,
                softness = 0.09f,
            ),
            pictureInPicture = PictureInPictureSettings(
                enabled = true,
                imageUri = "content://example/pip?name=a=b",
                centerX = 0.22f,
                centerY = 0.71f,
                widthFraction = 0.42f,
                opacity = 0.77f,
            ),
            logo = LogoOverlaySettings(
                enabled = true,
                imageUri = "content://example/logo",
                centerX = 0.82f,
                centerY = 0.11f,
                widthFraction = 0.16f,
                opacity = 0.66f,
            ),
            text = TextOverlaySettings(
                enabled = true,
                text = "Live = 100%\nSecond line",
                x = 0.13f,
                y = 0.21f,
                sizeSp = 48f,
                opacity = 0.91f,
                font = TextOverlayFont.Custom,
                customFontUri = "content://example/font",
                animation = TextOverlayAnimation.Pulse,
            ),
            output = StreamingOutputSettings(
                enabled = true,
                port = 8099,
                maxFps = 30,
                jpegQuality = 86,
                maxEdge = 1440,
            ),
        )

        val decoded = assertNotNull(EffectPresetCodec.decode(EffectPresetCodec.encode(original)))

        assertEquals(original.chromaKey, decoded.chromaKey)
        assertEquals(original.pictureInPicture, decoded.pictureInPicture)
        assertEquals(original.logo, decoded.logo)
        assertEquals(original.text, decoded.text)
        assertEquals(original.output.copy(enabled = false), decoded.output)
        assertFalse(decoded.output.enabled)
    }

    @Test
    fun normalizationClampsRealtimeSettingsToSupportedRanges() {
        val normalized = StreamingEffectsState(
            chromaKey = ChromaKeySettings(tolerance = 2f, softness = 0f),
            pictureInPicture = PictureInPictureSettings(
                centerX = -2f,
                centerY = 3f,
                widthFraction = 4f,
                opacity = -1f,
            ),
            logo = LogoOverlaySettings(
                centerX = 4f,
                centerY = -1f,
                widthFraction = 0f,
                opacity = 4f,
            ),
            text = TextOverlaySettings(
                x = -1f,
                y = 2f,
                sizeSp = 500f,
                opacity = 3f,
            ),
            output = StreamingOutputSettings(
                port = 1,
                maxFps = 500,
                jpegQuality = 2,
                maxEdge = 99,
            ),
        ).normalized()

        assertTrue(normalized.chromaKey.tolerance in 0f..0.95f)
        assertTrue(normalized.chromaKey.softness in 0.01f..0.5f)
        assertEquals(0f, normalized.pictureInPicture.centerX)
        assertEquals(1f, normalized.pictureInPicture.centerY)
        assertEquals(0.75f, normalized.pictureInPicture.widthFraction)
        assertEquals(0f, normalized.pictureInPicture.opacity)
        assertEquals(1f, normalized.logo.centerX)
        assertEquals(0f, normalized.logo.centerY)
        assertEquals(0.05f, normalized.logo.widthFraction)
        assertEquals(1f, normalized.logo.opacity)
        assertEquals(120f, normalized.text.sizeSp)
        assertEquals(1024, normalized.output.port)
        assertEquals(60, normalized.output.maxFps)
        assertEquals(40, normalized.output.jpegQuality)
        assertEquals(320, normalized.output.maxEdge)
    }
}
