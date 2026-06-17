package app.pebo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.pebo.data.LocalNoteStore
import app.pebo.ui.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath

fun main() = application {
    val baseDir = (System.getProperty("user.home") + "/Pebo").toPath()
    val store = LocalNoteStore(FileSystem.SYSTEM, baseDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = NotesViewModel(store, scope)

    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pebo",
        state = windowState,
    ) {
        App(vm, baseDir.toString())
    }
}

