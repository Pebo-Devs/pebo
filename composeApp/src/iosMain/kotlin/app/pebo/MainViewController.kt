package app.pebo

import androidx.compose.ui.window.ComposeUIViewController
import app.pebo.data.FileAppPreferences
import app.pebo.data.LocalNoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.data.seedWelcomeNoteIfNeeded
import app.pebo.ui.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/**
 * iOS entry point. Bridges the shared Compose [App] into a `UIViewController` that the SwiftUI host
 * (`iosApp/`) embeds. Mirrors the desktop `main.kt` wiring: preferences + note store live in the app's
 * sandboxed Documents directory, the welcome note tree is seeded on first run, and a [NotesViewModel]
 * drives the same UI as every other platform.
 */
fun MainViewController(): UIViewController {
    val fs = FileSystem.SYSTEM
    val documents = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
    val appDir = "$documents/Pebo".toPath()
    val prefs = FileAppPreferences(fs, appDir / "settings.conf")
    val notesBaseDir = prefs.getString(PREF_NOTES_DIR)
        ?.takeIf { it.isNotBlank() }
        ?.toPath()
        ?: appDir
    val store = LocalNoteStore(fs, notesBaseDir)
    runBlocking { seedWelcomeNoteIfNeeded(store, fs, notesBaseDir) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = NotesViewModel(
        store = store,
        scope = scope,
        prefs = prefs,
        initialNotesDir = notesBaseDir.toString(),
        storeFactory = { path -> LocalNoteStore(fs, path.toPath()) },
    )
    return ComposeUIViewController { App(vm, notesBaseDir.toString()) }
}
