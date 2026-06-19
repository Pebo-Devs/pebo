package app.pebo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.pebo.data.AppPreferences
import app.pebo.data.FileAppPreferences
import app.pebo.data.LocalNoteStore
import app.pebo.data.NoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.data.seedWelcomeNoteIfNeeded
import app.pebo.platform.CurrentActivityHolder
import app.pebo.platform.SafFileSystem
import app.pebo.platform.SafFolderPicker
import app.pebo.ui.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Android entry point. Builds the same dependency graph as the desktop `main.kt`
 * (filesystem-backed [LocalNoteStore] + [FileAppPreferences] + [NotesViewModel]) and hosts the
 * shared [App] composable, so the phone renders the identical Compose UI (App.kt switches to its
 * CompactLayout for narrow screens automatically).
 *
 * The chosen notes folder may be a real path (the app-owned default) or, once the user picks one via
 * the Storage Access Framework, a `content://` tree URI; [openWorkspace] resolves either into a
 * [LocalNoteStore]. The launcher used by the shared folder picker is registered here in `onCreate`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CurrentActivityHolder.set(this)
        SafFolderPicker.register(this)

        val systemFs = FileSystem.SYSTEM
        // User-visible, app-owned default location (matches the desktop "~/Pebo" feel); falls back to
        // internal storage if external is unavailable. Prefs always live here so the chosen notes
        // folder persists independently of it, even when that folder is a SAF tree URI.
        val baseRoot = (getExternalFilesDir(null) ?: filesDir).absolutePath
        val defaultDir = "$baseRoot/Pebo"

        val prefs: AppPreferences = FileAppPreferences(systemFs, "$defaultDir/settings.conf".toPath())
        val stored = prefs.getString(PREF_NOTES_DIR)?.takeIf { it.isNotBlank() }
        // Fall back to the default folder if a previously chosen SAF tree is no longer accessible
        // (permission revoked / storage removed), so the app never opens onto a dead location.
        val preferred = stored?.takeIf { isUsableLocation(applicationContext, it) } ?: defaultDir

        // Opening/seeding a SAF tree can still fail at runtime (provider unavailable, query throws);
        // degrade gracefully to the app-owned default rather than crashing on launch.
        val preferredWorkspace = if (preferred != defaultDir) {
            runCatching { openAndSeed(applicationContext, preferred) }.getOrNull()
        } else {
            null
        }
        val location = if (preferredWorkspace != null) preferred else defaultDir
        val workspace = preferredWorkspace ?: openAndSeed(applicationContext, defaultDir)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val appContext = applicationContext
        val vm = NotesViewModel(
            store = workspace.store,
            scope = scope,
            prefs = prefs,
            initialNotesDir = location,
            storeFactory = { loc -> openWorkspace(appContext, loc).store },
        )

        setContent {
            App(vm, location)
        }
    }

    override fun onDestroy() {
        SafFolderPicker.unregister()
        CurrentActivityHolder.clear(this)
        super.onDestroy()
    }
}

/** A note store plus the [FileSystem]/[Path] root it lives on, so first-run seeding targets it. */
private class Workspace(
    val store: NoteStore,
    val fs: FileSystem,
    val baseDir: Path,
)

/**
 * Resolves a notes-folder [location] into a [Workspace]. A `content://` tree URI is served by a
 * [SafFileSystem] rooted at the tree (`"/"`); any other string is treated as a real filesystem path
 * served by [FileSystem.SYSTEM]. Both yield a [LocalNoteStore] with identical behaviour.
 */
private fun openWorkspace(context: Context, location: String): Workspace {
    if (location.startsWith("content://")) {
        val safFs = SafFileSystem(context, Uri.parse(location))
        val base = "/".toPath()
        return Workspace(LocalNoteStore(safFs, base), safFs, base)
    }
    val base = location.toPath()
    return Workspace(LocalNoteStore(FileSystem.SYSTEM, base), FileSystem.SYSTEM, base)
}

/** Opens [location] and runs first-run seeding against it; throws if the location can't be used. */
private fun openAndSeed(context: Context, location: String): Workspace {
    val workspace = openWorkspace(context, location)
    runBlocking { seedWelcomeNoteIfNeeded(workspace.store, workspace.fs, workspace.baseDir) }
    return workspace
}

/** A real path is always usable; a SAF tree URI is only usable while we still hold persisted access. */
private fun isUsableLocation(context: Context, location: String): Boolean {
    if (!location.startsWith("content://")) return true
    val uri = runCatching { Uri.parse(location) }.getOrNull() ?: return false
    return context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }
}
