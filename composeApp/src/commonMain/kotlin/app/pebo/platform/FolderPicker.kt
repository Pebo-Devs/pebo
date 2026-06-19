package app.pebo.platform

/**
 * Opens the platform's native folder chooser and returns the absolute path of the selected
 * directory, or `null` if the user cancelled. Implemented per-platform via `actual`:
 *  - Desktop (JVM): Swing `JFileChooser` in directories-only mode.
 *  - Android / iOS: provide their own document-picker actuals when those targets are wired up.
 *
 * Implementations may block until the modal dialog is dismissed, so callers on a UI thread should
 * expect the call to return only after the user chooses or cancels.
 */
expect fun pickFolder(title: String, initialPath: String?): String?
