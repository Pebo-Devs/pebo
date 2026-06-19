package app.pebo.platform

/**
 * Opens the platform's native folder chooser and returns the absolute path of the selected
 * directory, or `null` if the user cancelled. Implemented per-platform via `actual`:
 *  - Desktop (JVM): native shell dialog on Windows, `FileDialog` (directory mode) on macOS.
 *  - iOS: not supported — notes live in the app's sandboxed Documents directory.
 *
 * Implementations may block until the modal dialog is dismissed, so callers on a UI thread should
 * expect the call to return only after the user chooses or cancels.
 */
expect fun pickFolder(title: String, initialPath: String?): String?

/**
 * Whether [pickFolder] can present a chooser on this platform. Used to hide the "change folder"
 * affordance where it is unsupported (e.g. the iOS sandbox) instead of showing a dead button.
 */
expect fun folderPickerSupported(): Boolean
