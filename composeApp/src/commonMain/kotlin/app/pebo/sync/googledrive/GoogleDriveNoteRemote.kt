package app.pebo.sync.googledrive

import app.pebo.auth.urlEncode
import app.pebo.core.NoteFile
import app.pebo.sync.CloudNoteFile
import app.pebo.sync.CloudNoteRemote
import app.pebo.sync.CloudProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class GoogleDriveNoteRemote(
    private val http: HttpClient,
    private val accessToken: suspend () -> String,
    private val notesFolderId: String,
) : CloudNoteRemote {
    override val provider: CloudProvider = CloudProvider.GoogleDrive

    override suspend fun listFiles(): List<CloudNoteFile> {
        val token = accessToken()
        val query = "'$notesFolderId' in parents and trashed = false and name contains '.md'"
        val files = http.get(
            "$DriveRoot/files?q=${urlEncode(query)}&fields=files(id,name,headRevisionId,md5Checksum)",
        ) {
            bearerAuth(token)
        }.body<GoogleFilesResponse>()

        return files.files.map { file ->
            val content = http.get("$DriveRoot/files/${file.id}?alt=media") {
                bearerAuth(token)
            }.bodyAsText()
            val note = NoteFile.parse(content, fallbackId = file.id, trashed = false)
            CloudNoteFile(
                remoteId = file.id,
                noteId = note.id,
                fileName = file.name,
                content = content,
                revision = file.headRevisionId ?: file.md5Checksum ?: file.id,
            )
        }
    }

    override suspend fun upload(
        noteId: String,
        fileName: String,
        content: String,
        previousRemoteId: String?,
        previousRevision: String?,
    ): CloudNoteFile {
        val token = accessToken()
        val file = if (previousRemoteId == null) {
            http.post("$DriveRoot/files?fields=id,name,headRevisionId,md5Checksum") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(GoogleFileMetadata(name = fileName, parents = listOf(notesFolderId)))
            }.body<GoogleFile>()
        } else {
            GoogleFile(id = previousRemoteId, name = fileName)
        }

        val updated = http.patch(
            "https://www.googleapis.com/upload/drive/v3/files/${file.id}?uploadType=media&fields=id,name,headRevisionId,md5Checksum",
        ) {
            bearerAuth(token)
            contentType(ContentType.Text.Plain)
            setBody(content.encodeToByteArray())
        }.body<GoogleFile>()

        return CloudNoteFile(
            remoteId = updated.id,
            noteId = noteId,
            fileName = updated.name.ifBlank { fileName },
            content = content,
            revision = updated.headRevisionId ?: updated.md5Checksum ?: updated.id,
        )
    }

    private companion object {
        const val DriveRoot = "https://www.googleapis.com/drive/v3"
    }
}

@Serializable
private data class GoogleFilesResponse(
    val files: List<GoogleFile> = emptyList(),
)

@Serializable
private data class GoogleFile(
    val id: String,
    val name: String = "",
    val headRevisionId: String? = null,
    val md5Checksum: String? = null,
)

@Serializable
private data class GoogleFileMetadata(
    val name: String,
    val parents: List<String>,
    val mimeType: String = "text/markdown",
)
