package app.pebo.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Bridges the shared, off-UI-thread `pickSaveFile` call to Android's asynchronous
 * `ACTION_CREATE_DOCUMENT` ("Save As") flow without blocking the main thread — the analogue of the
 * desktop native save dialog. The launcher is registered from `MainActivity.onCreate` (result
 * launchers must be registered before STARTED); [createDocument] then suspends until the user picks
 * a destination or cancels and returns the chosen `content://` document URI (or null on cancel).
 *
 * A raw `ACTION_CREATE_DOCUMENT` intent is used (rather than `ActivityResultContracts.CreateDocument`)
 * so the MIME type can vary per export format with a single registered launcher.
 */
object SafDocumentSaver {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var pending: CancellableContinuation<Uri?>? = null

    fun register(activity: ComponentActivity) {
        resolvePending(null)
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
            resolvePending(uri)
        }
    }

    fun unregister() {
        resolvePending(null)
        launcher?.unregister()
        launcher = null
    }

    suspend fun createDocument(mimeType: String, suggestedName: String): Uri? =
        withContext(Dispatchers.Main.immediate) {
            val activeLauncher = launcher ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                resolvePending(null)
                pending = cont
                cont.invokeOnCancellation { if (pending === cont) pending = null }
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, suggestedName)
                }
                try {
                    activeLauncher.launch(intent)
                } catch (t: Throwable) {
                    if (pending === cont) pending = null
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private fun resolvePending(uri: Uri?) {
        val cont = pending
        pending = null
        if (cont != null && cont.isActive) cont.resume(uri)
    }
}
