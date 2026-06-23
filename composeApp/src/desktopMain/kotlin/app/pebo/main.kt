package app.pebo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.pebo.data.AppPreferences
import app.pebo.data.FileAppPreferences
import app.pebo.data.LocalNoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.data.seedWelcomeNoteIfNeeded
import app.pebo.sync.DesktopCloudSyncController
import app.pebo.ui.NotesViewModel
import app.pebo.ui.peboLogo
import app.pebo.update.DesktopUpdateService
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
    seedBuiltInOAuthClientIds()
    val fs = FileSystem.SYSTEM
    val appDir = (System.getProperty("user.home") + "/Pebo").toPath()
    // Prefs live in the fixed app dir so the chosen notes folder persists independently of it.
    val prefs: AppPreferences = FileAppPreferences(fs, appDir / "settings.conf")
    val notesBaseDir: Path = prefs.getString(PREF_NOTES_DIR)
        ?.takeIf { it.isNotBlank() }
        ?.toPath()
        ?: appDir
    val store = LocalNoteStore(fs, notesBaseDir)
    runBlocking { seedWelcomeNoteIfNeeded(store, fs, notesBaseDir) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cloudSync = DesktopCloudSyncController(fs = fs, appDir = appDir)
    val updater = DesktopUpdateService(exit = { exitApplication() })
    val vm = NotesViewModel(
        store = store,
        scope = scope,
        prefs = prefs,
        initialNotesDir = notesBaseDir.toString(),
        storeFactory = { path -> LocalNoteStore(fs, path.toPath()) },
        cloudSync = cloudSync,
        updateService = updater,
    )

    val windowState = rememberWindowState(
        placement = WindowPlacement.Maximized,
        size = DpSize(1280.dp, 820.dp),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pebo",
        state = windowState,
        icon = rememberVectorPainter(peboLogo()),
    ) {
        LaunchedEffect(Unit) {
            applyWindowsTitleBar(window)
        }
        App(vm, notesBaseDir.toString())
    }
}

private fun seedBuiltInOAuthClientIds() {
    // Pebo ships with public OAuth client ids baked in (see PeboBuildConfig) so OneDrive/Google work
    // out of the box. We surface them through the same system properties DesktopOAuthClientIds reads,
    // and only when the user/CI hasn't already supplied an override via -D/env — so explicit overrides
    // still win and unit tests (which set the properties directly) are unaffected.
    fun seed(property: String, env: String, default: String) {
        if (default.isBlank()) return
        val alreadySet = System.getProperty(property)?.isNotBlank() == true ||
            System.getenv(env)?.isNotBlank() == true
        if (!alreadySet) System.setProperty(property, default)
    }
    seed("pebo.onedrive.clientId", "PEBO_ONEDRIVE_CLIENT_ID", PeboBuildConfig.ONEDRIVE_CLIENT_ID)
    seed("pebo.google.clientId", "PEBO_GOOGLE_CLIENT_ID", PeboBuildConfig.GOOGLE_CLIENT_ID)
    seed("pebo.google.clientSecret", "PEBO_GOOGLE_CLIENT_SECRET", PeboBuildConfig.GOOGLE_CLIENT_SECRET)
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
