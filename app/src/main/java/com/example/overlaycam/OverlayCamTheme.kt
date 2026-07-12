package com.example.overlaycam

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OverlayCamDarkColors = darkColorScheme(
    primary = Color(0xFFB8C8FF),
    onPrimary = Color(0xFF102A5C),
    primaryContainer = Color(0xFF294477),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC2C7D7),
    secondaryContainer = Color(0xFF414754),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF282A30),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC5C6D0),
)

@Composable
internal fun OverlayCamTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        OverlayCamDarkColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
