package app.pebo.platform

/**
 * iOS keeps "On this device" notes in the app's sandboxed Documents directory, so there is no
 * synchronous native folder chooser the way the desktop has one. Returning null leaves the current
 * folder unchanged.
 *
 * A future enhancement can present `UIDocumentPickerViewController` in folder mode and resolve a
 * security-scoped bookmark — but that is inherently asynchronous, so it would also require making the
 * shared `pickFolder` contract suspendable.
 */
actual fun pickFolder(title: String, initialPath: String?): String? = null

/** iOS keeps notes in the app's sandboxed Documents directory, so there is no folder chooser. */
actual fun folderPickerSupported(): Boolean = false
