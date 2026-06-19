package app.pebo.platform

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
 * Bridges the shared, UI-thread `pickFolder` call to Android's asynchronous
 * `ACTION_OPEN_DOCUMENT_TREE` flow without blocking the main thread (no ANR).
 *
 * `ActivityResultLauncher`s must be registered before the host Activity is STARTED, so [register] is
 * invoked from `MainActivity.onCreate`; [pickTree] then suspends until the user chooses a folder or
 * cancels. When a folder is chosen its read/write access is persisted with
 * [android.content.ContentResolver.takePersistableUriPermission] so the very same tree keeps working
 * across app launches — the durable half of "Any folder you choose" parity.
 */
object SafFolderPicker {
    private var launcher: ActivityResultLauncher<Uri?>? = null
    private var pending: CancellableContinuation<Uri?>? = null

    fun register(activity: ComponentActivity) {
        resolvePending(null)
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                runCatching {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            resolvePending(uri)
        }
    }

    fun unregister() {
        resolvePending(null)
        launcher?.unregister()
        launcher = null
    }

    suspend fun pickTree(initialUri: Uri?): Uri? = withContext(Dispatchers.Main.immediate) {
        val activeLauncher = launcher ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            // Only one picker can be open at a time; abandon any earlier request.
            resolvePending(null)
            pending = cont
            cont.invokeOnCancellation { if (pending === cont) pending = null }
            try {
                activeLauncher.launch(initialUri)
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
