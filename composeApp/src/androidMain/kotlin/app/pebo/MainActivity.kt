package app.pebo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.pebo.data.AppPreferences
import app.pebo.data.FileAppPreferences
import app.pebo.data.LocalNoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.data.seedWelcomeNoteIfNeeded
import app.pebo.platform.CurrentActivityHolder
import app.pebo.ui.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Android entry point. Builds the same dependency graph as the desktop `main.kt`
 * (filesystem-backed [LocalNoteStore] + [FileAppPreferences] + [NotesViewModel]) and hosts the
 * shared [App] composable, so the phone renders the identical Compose UI (App.kt switches to its
 * CompactLayout for narrow screens automatically).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CurrentActivityHolder.set(this)

        val fs = FileSystem.SYSTEM
        // User-visible, app-owned location (matches the desktop "~/Pebo" feel); falls back to
        // internal storage if external is unavailable.
        val baseRoot = (getExternalFilesDir(null) ?: filesDir).absolutePath
        val appDir = "$baseRoot/Pebo".toPath()

        val prefs: AppPreferences = FileAppPreferences(fs, appDir / "settings.conf")
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

        setContent {
            App(vm, notesBaseDir.toString())
        }
    }

    override fun onDestroy() {
        CurrentActivityHolder.clear(this)
        super.onDestroy()
    }
}
