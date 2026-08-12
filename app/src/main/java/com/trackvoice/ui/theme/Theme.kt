package com.trackvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF6FF),
    onSecondaryContainer = Color(0xFF1E3A5F),
    tertiary = Color(0xFF2563EB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF172126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172126),
    surfaceVariant = Color(0xFFEEF2F5),
    onSurfaceVariant = Color(0xFF52606A),
    outline = Color(0xFF94A3AB),
    outlineVariant = Color(0xFFDCE3E7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DD9CB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF9DF2E4),
    secondary = Color(0xFFB5CCC7),
    onSecondary = Color(0xFF203330),
    secondaryContainer = Color(0xFF394B47),
    onSecondaryContainer = Color(0xFFD4E9E4),
    tertiary = Color(0xFFB2C8E8),
    onTertiary = Color(0xFF1D314B),
    tertiaryContainer = Color(0xFF354863),
    onTertiaryContainer = Color(0xFFD9E4FF),
    background = Color(0xFF101513),
    onBackground = Color(0xFFE0E5E1),
    surface = Color(0xFF101513),
    onSurface = Color(0xFFE0E5E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBECAC5),
    outline = Color(0xFF89948F),
    outlineVariant = Color(0xFF3F4946),
)

@Composable
fun TrackVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
