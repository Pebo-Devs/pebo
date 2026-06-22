package app.pebo

import androidx.compose.runtime.Composable

/**
 * iOS has no global hardware/system Back button to bind: back navigation is provided by the in-app
 * affordances (the note-list back arrow, Esc on a hardware keyboard, the drawer scrim). So, like
 * desktop, this is a no-op.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS.
}
