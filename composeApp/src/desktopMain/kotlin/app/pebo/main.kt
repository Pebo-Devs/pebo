package app.pebo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.pebo.core.Note
import app.pebo.data.LocalNoteStore
import app.pebo.ui.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
        App(vm, baseDir.toString())
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
