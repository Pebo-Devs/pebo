package app.pebo.platform

import android.net.Uri

/**
 * Android folder picker. Opens the system document-tree picker (`ACTION_OPEN_DOCUMENT_TREE`) through
 * [SafFolderPicker] and returns the chosen tree's persisted `content://` URI as a string, or `null`
 * if the user cancelled. The returned URI is what the app stores as the notes-folder handle;
 * [SafFileSystem] then lets the okio-based note store read and write inside it, so any folder the
 * user picks behaves exactly like the desktop "Change…" flow. `suspend` keeps the asynchronous
 * picker off the UI thread (no ANR).
 *
 * [initialPath] may be a previously chosen tree URI; it is forwarded to the picker as a
 * start-location hint when it is a `content://` URI and ignored otherwise.
 */
actual suspend fun pickFolder(title: String, initialPath: String?): String? {
    val initialUri = initialPath
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?.takeIf { it.scheme == "content" }
    return SafFolderPicker.pickTree(initialUri)?.toString()
}

/** Android always has the system document-tree picker available. */
actual fun folderPickerSupported(): Boolean = true
