package com.nobelle.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Nobelle identity (docs/brand/nobelle-mark.svg): warm burgundy health mark,
// bright cobalt, electric-sky accent on ink or light canvas. Fixed palette —
// no dynamic colors, so records stay visually consistent across devices.
private val Burgundy = Color(0xFF941B45)
private val Cobalt = Color(0xFF155EEF)
private val Ink = Color(0xFF17213C)

private val LightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3D0016),
    secondary = Cobalt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5FF),
    onSecondaryContainer = Color(0xFF001A43),
    tertiary = Color(0xFF007FA5),
    onTertiary = Color.White,
    background = Color(0xFFF6F8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EBF5),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4C556F),
    outline = Color(0xFF747D98),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FB0),
    onPrimary = Color(0xFF580027),
    primaryContainer = Color(0xFF7A1238),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFAFC6FF),
    onSecondary = Color(0xFF002E69),
    secondaryContainer = Color(0xFF124791),
    onSecondaryContainer = Color(0xFFDCE5FF),
    tertiary = Color(0xFF65D4FA),
    background = Color(0xFF10162A),
    surface = Color(0xFF171E35),
    surfaceVariant = Color(0xFF252D48),
    onSurface = Color(0xFFF4F5FF),
    onSurfaceVariant = Color(0xFFC5C9DA),
    outline = Color(0xFF8E96B3),
)

@Composable
fun NobelleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
