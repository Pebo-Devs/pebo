package app.pebo.sync.googledrive

import app.pebo.auth.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * Resolves the Drive folder Pebo keeps notes in. [GoogleDriveNoteRemote] needs a concrete folder id
 * (unlike OneDrive's automatic app folder), so this performs a find-or-create keyed by folder name.
 *
 * Under the least-privilege `drive.file` scope the app only ever sees files it created, so a folder
 * Pebo made on a previous launch (or another device with the same account) is the only match the
 * query can return — making name-based reuse reliable without requesting broad Drive read access.
 */
object GoogleDriveFolders {
    const val DEFAULT_NOTES_FOLDER = "Pebo Notes"

    private const val DRIVE_ROOT = "https://www.googleapis.com/drive/v3"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    suspend fun findOrCreateNotesFolder(
        http: HttpClient,
        accessToken: suspend () -> String,
        name: String = DEFAULT_NOTES_FOLDER,
    ): String {
        val token = accessToken()
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val query = "mimeType = '$FOLDER_MIME' and name = '$escaped' and trashed = false"
        val existing = http.get(
            "$DRIVE_ROOT/files?q=${urlEncode(query)}&fields=files(id,name)&spaces=drive",
        ) {
            bearerAuth(token)
        }.body<DriveFolderList>().files.firstOrNull()
        if (existing != null) return existing.id

        return http.post("$DRIVE_ROOT/files?fields=id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DriveFolderMetadata(name = name, mimeType = FOLDER_MIME))
        }.body<DriveFolder>().id
    }
}

@Serializable
private data class DriveFolderList(val files: List<DriveFolder> = emptyList())

@Serializable
private data class DriveFolder(val id: String, val name: String = "")

@Serializable
private data class DriveFolderMetadata(val name: String, val mimeType: String)
