package app.pebo.platform

/**
 * Android folder picker.
 *
 * The shared call site (SettingsScreen) invokes this synchronously from the Compose UI thread, so a
 * blocking system document-tree dialog cannot be shown here without an ANR. The default workspace
 * already lives on a real filesystem path under the app's external files dir, so notes work without
 * choosing a folder. Returning null here is a safe no-op for the foundation; the full Storage Access
 * Framework flow (OPEN_DOCUMENT_TREE + persisted permissions + an okio adapter, plus the coroutine
 * refactor of the call site) is implemented by the `android-folder-picker-saf` parity unit.
 */
actual fun pickFolder(title: String, initialPath: String?): String? = null
