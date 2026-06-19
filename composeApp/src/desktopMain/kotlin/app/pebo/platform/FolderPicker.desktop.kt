package app.pebo.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop folder chooser backed by the **native Win32 shell folder dialog** (`SHBrowseForFolder`)
 * via JNA, rather than Swing's [javax.swing.JFileChooser].
 *
 * A `JFileChooser` shown from inside a Compose Desktop process renders with the correct OS title
 * bar but a blank white client area — its lightweight Swing components never paint inside the
 * Skiko/Compose-hosted JVM (reproduced with and without a worker thread, and with and without the
 * native Look & Feel). The shell dialog sidesteps that entirely: it is a real top-level window that
 * the operating system itself draws, so it always paints correctly and matches Explorer.
 *
 * The dialog is shown on a dedicated worker thread initialized as a COM single-threaded apartment
 * (required by `BIF_NEWDIALOGSTYLE`). We [Thread.join] the worker so the call blocks until the user
 * picks a folder or cancels; the `join()` publishes [result] back to the caller safely
 * (happens-before), so no `@Volatile` is needed. Returns the chosen absolute path, or null on cancel.
 *
 * The blocking work is moved to [Dispatchers.IO] so the `suspend` contract is honoured and the
 * Compose UI dispatcher is never blocked while the modal dialog is open.
 */
actual suspend fun pickFolder(title: String, initialPath: String?): String? = withContext(Dispatchers.IO) {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
        return@withContext null
    }

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
            // Fresh dedicated thread → CoInitializeEx(APARTMENTTHREADED) always succeeds with S_OK,
            // so the matching CoUninitialize is always correct here.
            Ole32.INSTANCE.CoUninitialize()
        }
    }, "pebo-folder-picker").apply { isDaemon = true }

    worker.start()
    worker.join()
    result
}

private const val MAX_PATH = 260

// SHBrowseForFolder ulFlags
private const val BIF_RETURNONLYFSDIRS = 0x00000001
private const val BIF_EDITBOX = 0x00000010
private const val BIF_NEWDIALOGSTYLE = 0x00000040

// CoInitializeEx apartment model
private const val COINIT_APARTMENTTHREADED = 0x2

/**
 * Minimal JNA mapping of the two shell32 entry points we need. We declare them ourselves (rather
 * than relying on jna-platform's [com.sun.jna.platform.win32.Shell32]) so the exact Unicode `...W`
 * signatures are unambiguous. [W32APIOptions.DEFAULT_OPTIONS] selects the wide-character variants
 * and maps [WString]/`char[]` to `LPCWSTR`/`wchar_t[]`.
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
