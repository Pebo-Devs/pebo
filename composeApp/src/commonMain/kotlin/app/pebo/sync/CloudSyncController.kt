package app.pebo.sync

import app.pebo.data.NoteStore
import app.pebo.ui.StorageProvider

enum class CloudStatus { Idle, Connecting, Syncing, Connected, Error }

/** UI-facing snapshot of the current cloud connection, surfaced in Settings → Storage. */
data class CloudSyncState(
    val status: CloudStatus = CloudStatus.Idle,
    val provider: StorageProvider? = null,
    val message: String = "",
)

/**
 * Bridge between the Settings UI and the platform's cloud sync stack (OAuth sign-in + [SyncEngine]).
 *
 * Desktop supplies [app.pebo.sync.DesktopCloudSyncController]; platforms that haven't wired a cloud
 * flow yet simply pass `null`, so the cloud providers stay non-selectable there. The controller is
 * provider-agnostic by design — OneDrive ships first; Google Drive (and others) slot in behind the
 * same methods once their remote bootstrap is added.
 */
interface CloudSyncController {
    /** Providers that have a public OAuth client id configured, so the UI may offer them. */
    fun configuredProviders(): Set<StorageProvider>

    /** The provider whose tokens are already stored from a prior session, if any. */
    suspend fun connectedProvider(): StorageProvider?

    /** Sign in if needed, then run an initial sync of [local] (rooted at [notesDir]). */
    suspend fun connect(provider: StorageProvider, local: NoteStore, notesDir: String): CloudSyncState

    /** Run a sync for an already-connected [provider]. */
    suspend fun sync(provider: StorageProvider, local: NoteStore, notesDir: String): CloudSyncState

    /** Forget stored tokens for [provider]. */
    suspend fun disconnect(provider: StorageProvider)
}
