package app.pebo

import androidx.compose.runtime.Composable

/** Desktop maps Esc to the same actions via key events in [App], so back handling is a no-op here. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on desktop.
}
