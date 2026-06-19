package app.pebo.sync

/**
 * Provider-neutral cloud folder API. OneDrive, Google Drive, and iCloud adapters normalize their
 * provider-specific file ids/revisions into this shape so the sync engine stays storage-agnostic.
 */
interface CloudNoteRemote {
    val provider: CloudProvider

    suspend fun listFiles(): List<CloudNoteFile>

    suspend fun upload(
        noteId: String,
        fileName: String,
        content: String,
        previousRemoteId: String? = null,
        previousRevision: String? = null,
    ): CloudNoteFile
}

enum class CloudProvider {
    OneDrive,
    GoogleDrive,
    ICloud,
}

data class CloudNoteFile(
    val remoteId: String,
    val noteId: String,
    val fileName: String,
    val content: String,
    val revision: String,
    val trashed: Boolean = false,
)
