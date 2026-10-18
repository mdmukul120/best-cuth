package com.bestcut.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00E5C3)

private val BestCutColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201A),
    secondary = Color(0xFFFF4F7B),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF15151C),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF22222C),
    onSurfaceVariant = Color(0xFFB5B5C3)
)

@Composable
fun BestCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BestCutColors, content = content)
}
