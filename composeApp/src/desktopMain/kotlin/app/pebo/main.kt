package app.pebo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.pebo.core.Note
import app.pebo.data.LocalNoteStore
import app.pebo.ui.NotesViewModel
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.awt.Window as AwtWindow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

fun main() = application {
    val baseDir = (System.getProperty("user.home") + "/Pebo").toPath()
    val fs = FileSystem.SYSTEM
    val store = LocalNoteStore(fs, baseDir)
    seedWelcomeNoteIfNeeded(store, fs, baseDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = NotesViewModel(store, scope)

    val windowState = rememberWindowState(size = DpSize(1220.dp, 760.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pebo",
        state = windowState,
    ) {
        LaunchedEffect(Unit) {
            applyWindowsTitleBar(window)
        }
        App(vm, baseDir.toString())
    }
}

private fun applyWindowsTitleBar(window: AwtWindow) {
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
    try {
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        val enabled = IntByReference(1)
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled.pointer, Int.SIZE_BYTES)
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, enabled.pointer, Int.SIZE_BYTES)

        val captionColor = IntByReference(0x00120D0B)
        val textColor = IntByReference(0x00F7EDE8)
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, captionColor.pointer, Int.SIZE_BYTES)
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, textColor.pointer, Int.SIZE_BYTES)
    } catch (error: UnsatisfiedLinkError) {
        System.err.println("Unable to apply dark Windows title bar: ${error.message}")
    }
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_TEXT_COLOR = 36

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, valueSize: Int): WinNT.HRESULT

    companion object {
        val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java)
    }
}

private fun seedWelcomeNoteIfNeeded(store: LocalNoteStore, fs: FileSystem, baseDir: Path) = runBlocking {
    val marker = baseDir / ".welcome-note-created"
    if (fs.exists(marker)) return@runBlocking

    val snapshot = store.load()
    if (snapshot.active.isNotEmpty() || snapshot.trashed.isNotEmpty()) {
        fs.createDirectories(baseDir)
        fs.write(marker) { writeUtf8("existing-notes\n") }
        return@runBlocking
    }

    store.save(
        Note.new(
            body = """
                # Welcome to Pebo

                Pebo is a focused Markdown workspace for notes that belong to you. #getting-started

                - Write in plain Markdown with autosave.
                - Use #tags anywhere to organize naturally.
                - Keep your notes as portable `.md` files.
                - Cloud storage foundations are ready for OneDrive and Google Drive setup.

                Start by editing this note or create a new one from the sidebar.
            """.trimIndent(),
        ).copy(pinned = true),
    )
    fs.write(marker) { writeUtf8("created\n") }
}
