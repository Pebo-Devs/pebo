package app.pebo.platform

/**
 * Opens the platform's native folder chooser and returns a handle to the selected directory, or
 * `null` if the user cancelled. Implemented per-platform via `actual`:
 *  - Desktop (JVM): native Win32 shell folder dialog; returns an absolute filesystem path.
 *  - Android: Storage Access Framework `OPEN_DOCUMENT_TREE`; returns the persisted tree `content://`
 *    URI string (read/write access is kept across launches via `takePersistableUriPermission`).
 *
 * `suspend` so the asynchronous Android document-tree picker can be awaited without blocking the UI
 * thread (no ANR); the call returns only after the user chooses or cancels. [initialPath] is the
 * handle returned by a previous call (path or tree URI) used to hint the picker's start location.
 */
expect suspend fun pickFolder(title: String, initialPath: String?): String?
