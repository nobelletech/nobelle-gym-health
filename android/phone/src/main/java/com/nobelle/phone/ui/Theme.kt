package com.nobelle.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Visual direction: premium, calm; charcoal/navy, white, teal, restrained blue (§3).
private val Navy = Color(0xFF12263A)
private val Charcoal = Color(0xFF1E252B)
private val Teal = Color(0xFF0E7C7B)
private val Blue = Color(0xFF3D6FA8)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Blue,
    background = Color(0xFFF7F9FA),
    surface = Color.White,
    onSurface = Charcoal,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BC0BE),
    onPrimary = Charcoal,
    secondary = Color(0xFF8FB8DE),
    background = Charcoal,
    surface = Navy,
    onSurface = Color(0xFFEDF2F4),
)

@Composable
fun NobelleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
