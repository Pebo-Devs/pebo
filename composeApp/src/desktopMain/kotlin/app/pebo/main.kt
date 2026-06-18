package app.pebo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.pebo.core.Note
import app.pebo.data.AppPreferences
import app.pebo.data.FileAppPreferences
import app.pebo.data.LocalNoteStore
import app.pebo.data.PREF_NOTES_DIR
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
    val fs = FileSystem.SYSTEM
    val appDir = (System.getProperty("user.home") + "/Pebo").toPath()
    // Prefs live in the fixed app dir so the chosen notes folder persists independently of it.
    val prefs: AppPreferences = FileAppPreferences(fs, appDir / "settings.conf")
    val notesBaseDir: Path = prefs.getString(PREF_NOTES_DIR)
        ?.takeIf { it.isNotBlank() }
        ?.toPath()
        ?: appDir
    val store = LocalNoteStore(fs, notesBaseDir)
    seedWelcomeNoteIfNeeded(store, fs, notesBaseDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = NotesViewModel(
        store = store,
        scope = scope,
        prefs = prefs,
        initialNotesDir = notesBaseDir.toString(),
        storeFactory = { path -> LocalNoteStore(fs, path.toPath()) },
    )

    val windowState = rememberWindowState(
        placement = WindowPlacement.Maximized,
        size = DpSize(1280.dp, 820.dp),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pebo",
        state = windowState,
    ) {
        LaunchedEffect(Unit) {
            applyWindowsTitleBar(window)
        }
        App(vm, notesBaseDir.toString())
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

    val welcome = Note.new(
        body = """
            # Welcome to Pebo

            Pebo is a focused Markdown workspace for notes that belong to you. #getting-started

            - Write in plain Markdown with live formatting and autosave.
            - Type #tags anywhere — try nested ones like #project/pebo.
            - Build a tree: open a note and choose "Add child note".
            - Everything stays as portable `.md` files you fully own.

            The notes below are a small tree so you can see how nesting looks.
        """.trimIndent(),
    ).copy(pinned = true)
    store.save(welcome)

    store.save(
        Note.new(
            parentId = welcome.id,
            body = """
                # Getting started

                A child note — the connector lines on the left tie it back to "Welcome to Pebo". #project/pebo

                1. Edit this text; it saves automatically.
                2. Use the toolbar for headings, bold, lists, and links.
                3. Add your own #tags to grow the sidebar tree.
            """.trimIndent(),
        ),
    )

    store.save(
        Note.new(
            parentId = welcome.id,
            body = """
                # Tips & shortcuts

                Another child of Welcome, one level deeper in the tag tree. #project/pebo/ui

                - Click a tag in the sidebar to filter notes.
                - Pin important notes to keep them on top.
                - Trash is non-destructive — restore anytime.
            """.trimIndent(),
        ),
    )

    store.save(
        Note.new(
            body = """
                # Ideas

                A separate top-level note with its own tag branch. #project/docs

                Capture anything here, then organize it with tags or child notes later.
            """.trimIndent(),
        ),
    )

    fs.write(marker) { writeUtf8("created\n") }
}
