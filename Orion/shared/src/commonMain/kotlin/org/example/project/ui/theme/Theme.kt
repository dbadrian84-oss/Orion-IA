package org.example.project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OrionDarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = DeepBlack,
    primaryContainer = DeepBlue,
    onPrimaryContainer = TextWhite,
    secondary = NeonBlue,
    onSecondary = DeepBlack,
    background = DeepBlack,
    onBackground = TextWhite,
    surface = SurfaceBlack,
    onSurface = TextWhite,
    surfaceVariant = SurfaceBlack,
    onSurfaceVariant = TextGray,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun OrionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For this futuristic theme, we always force dark mode for now.
    val colorScheme = OrionDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
