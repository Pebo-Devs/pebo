package app.pebo.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop folder chooser. Each platform uses a *native* dialog rather than Swing's
 * [javax.swing.JFileChooser], which renders with a blank client area inside a Compose Desktop
 * (Skiko-hosted) process.
 *
 *  - **Windows**: the shell folder dialog (`SHBrowseForFolder`) via JNA.
 *  - **macOS**: AWT [FileDialog] switched into directory mode with the `apple.awt.fileDialogForDirectories`
 *    system property — the same native Cocoa panel the export "Save As" dialog uses.
 *
 * Returns the chosen absolute path, or null on cancel / unsupported platform.
 */
actual fun pickFolder(title: String, initialPath: String?): String? {
    val os = System.getProperty("os.name", "")
    return when {
        os.startsWith("Windows") -> pickFolderWindows(title)
        os.contains("Mac", ignoreCase = true) -> pickFolderMacOs(title, initialPath)
        else -> null
    }
}

actual fun folderPickerSupported(): Boolean {
    val os = System.getProperty("os.name", "")
    return os.startsWith("Windows") || os.contains("Mac", ignoreCase = true)
}

/**
 * macOS: AWT [FileDialog] in LOAD mode. Setting `apple.awt.fileDialogForDirectories=true` makes the
 * native open panel select directories; the chosen folder's name comes back in [FileDialog.getFile]
 * and its parent in [FileDialog.getDirectory]. Shown on the AWT event thread (Compose Desktop's UI
 * thread), which pumps a nested modal loop so the call blocks until the user picks or cancels.
 */
private fun pickFolderMacOs(title: String, initialPath: String?): String? {
    var result: String? = null
    val task = Runnable {
        val key = "apple.awt.fileDialogForDirectories"
        val previous = System.getProperty(key)
        System.setProperty(key, "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            if (!initialPath.isNullOrBlank()) dialog.directory = initialPath
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file
            if (dir != null && name != null) {
                result = File(dir, name).absolutePath
            }
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }
    if (EventQueue.isDispatchThread()) task.run() else EventQueue.invokeAndWait(task)
    return result
}

/**
 * Windows: the native shell folder dialog (`SHBrowseForFolder`) via JNA. A `JFileChooser` shown from
 * inside a Compose Desktop process renders with the correct OS title bar but a blank white client
 * area, so we use the real top-level shell window instead.
 *
 * The dialog is shown on a dedicated worker thread initialized as a COM single-threaded apartment
 * (required by `BIF_NEWDIALOGSTYLE`). We [Thread.join] the worker so the call blocks until the user
 * picks a folder or cancels; the `join()` publishes [result] back to the caller safely.
 */
private fun pickFolderWindows(title: String): String? {
    var result: String? = null

    val worker = Thread({
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, COINIT_APARTMENTTHREADED)
        try {
            val displayBuffer = Memory(MAX_PATH.toLong() * Native.WCHAR_SIZE)
            val info = BrowseInfo().apply {
                hwndOwner = null
                pidlRoot = null
                pszDisplayName = displayBuffer
                lpszTitle = WString(title)
                ulFlags = BIF_RETURNONLYFSDIRS or BIF_NEWDIALOGSTYLE or BIF_EDITBOX
                lpfn = null
                lParam = null
                iImage = 0
            }

            val pidl = Shell32Ext.INSTANCE.SHBrowseForFolder(info)
            if (pidl != null) {
                try {
                    val pathBuffer = CharArray(MAX_PATH)
                    if (Shell32Ext.INSTANCE.SHGetPathFromIDList(pidl, pathBuffer)) {
                        val end = pathBuffer.indexOf('\u0000').let { if (it < 0) pathBuffer.size else it }
                        val path = String(pathBuffer, 0, end)
                        if (path.isNotBlank()) result = path
                    }
                } finally {
                    Ole32.INSTANCE.CoTaskMemFree(pidl)
                }
            }
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }, "pebo-folder-picker").apply { isDaemon = true }

    worker.start()
    worker.join()
    return result
}

private const val MAX_PATH = 260

// SHBrowseForFolder ulFlags
private const val BIF_RETURNONLYFSDIRS = 0x00000001
private const val BIF_EDITBOX = 0x00000010
private const val BIF_NEWDIALOGSTYLE = 0x00000040

// CoInitializeEx apartment model
private const val COINIT_APARTMENTTHREADED = 0x2

/**
 * Minimal JNA mapping of the two shell32 entry points we need, declared so the exact Unicode `...W`
 * signatures are unambiguous. [W32APIOptions.DEFAULT_OPTIONS] selects the wide-character variants.
 */
private interface Shell32Ext : StdCallLibrary {
    fun SHBrowseForFolder(lpbi: BrowseInfo): Pointer?
    fun SHGetPathFromIDList(pidl: Pointer, pszPath: CharArray): Boolean

    companion object {
        val INSTANCE: Shell32Ext =
            Native.load("shell32", Shell32Ext::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Native `BROWSEINFOW` layout passed by reference to `SHBrowseForFolderW`. */
@Structure.FieldOrder(
    "hwndOwner",
    "pidlRoot",
    "pszDisplayName",
    "lpszTitle",
    "ulFlags",
    "lpfn",
    "lParam",
    "iImage",
)
internal open class BrowseInfo : Structure() {
    @JvmField var hwndOwner: Pointer? = null
    @JvmField var pidlRoot: Pointer? = null
    @JvmField var pszDisplayName: Pointer? = null
    @JvmField var lpszTitle: WString? = null
    @JvmField var ulFlags: Int = 0
    @JvmField var lpfn: Pointer? = null
    @JvmField var lParam: Pointer? = null
    @JvmField var iImage: Int = 0
}
