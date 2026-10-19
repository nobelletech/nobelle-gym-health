package com.nobelle.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

// Nobelle identity on Wear (docs/brand/nobelle-mark.svg): the same burgundy/
// cobalt/sky mark tuned for a small dark round display — brightened primary
// and cobalt secondary over the dark canvas for legibility.
private val Burgundy = Color(0xFF941B45)
private val BurgundyBright = Color(0xFFFF8FB0)
private val CobaltBright = Color(0xFF78A9FF)
private val DarkCanvas = Color(0xFF10162A)
private val DarkSurface = Color(0xFF171E35)

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = BurgundyBright,
            onPrimary = Color(0xFF580027),
            primaryContainer = Burgundy,
            onPrimaryContainer = Color(0xFFFFD9E3),
            secondary = CobaltBright,
            onSecondary = Color(0xFF002E69),
            background = DarkCanvas,
            onBackground = Color(0xFFF4F5FF),
            surfaceContainer = DarkSurface,
            onSurface = Color(0xFFF4F5FF),
            onSurfaceVariant = Color(0xFFC5C9DA),
        ),
        content = content,
    )
}
