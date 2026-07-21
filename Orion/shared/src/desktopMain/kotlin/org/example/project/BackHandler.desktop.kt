package org.example.project

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op for desktop (or could wire to a global keyboard shortcut if desired)
}
