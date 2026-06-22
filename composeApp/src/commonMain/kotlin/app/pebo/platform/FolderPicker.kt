package app.pebo.platform

/**
 * Opens the platform's native folder chooser and returns a handle to the selected directory — an
 * absolute filesystem path on desktop, or an Android tree `content://` URI — or `null` if the user
 * cancelled. Implemented per-platform via `actual`:
 *  - Desktop (JVM): native shell dialog on Windows, `FileDialog` (directory mode) on macOS; returns
 *    an absolute filesystem path.
 *  - Android: Storage Access Framework `OPEN_DOCUMENT_TREE`; returns the persisted tree `content://`
 *    URI string (read/write access is kept across launches via `takePersistableUriPermission`).
 *  - iOS: not supported — notes live in the app's sandboxed Documents directory.
 *
 * `suspend` so the asynchronous Android document-tree picker can be awaited without blocking the UI
 * thread (no ANR); the call returns only after the user chooses or cancels. [initialPath] is the
 * handle returned by a previous call (path or tree URI) used to hint the picker's start location.
 */
expect suspend fun pickFolder(title: String, initialPath: String?): String?

/**
 * Whether [pickFolder] can present a chooser on this platform. Used to hide the "change folder"
 * affordance where it is unsupported (e.g. the iOS sandbox) instead of showing a dead button.
 */
expect fun folderPickerSupported(): Boolean
