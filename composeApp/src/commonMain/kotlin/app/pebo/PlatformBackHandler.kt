package app.pebo

import androidx.compose.runtime.Composable

/**
 * Routes the platform's "back" affordance to in-app navigation. On Android this binds to the system
 * Back button / predictive-back gesture so Back returns to the note list, exits Focus mode, or
 * closes the palette/settings/drawer instead of killing the Activity. On desktop it is a no-op —
 * the desktop window already maps Esc to the same actions via key events in [App].
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
