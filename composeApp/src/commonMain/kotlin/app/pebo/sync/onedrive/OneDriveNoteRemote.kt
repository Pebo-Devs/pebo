package app.pebo.sync.onedrive

import app.pebo.auth.urlEncode
import app.pebo.core.NoteFile
import app.pebo.sync.CloudNoteFile
import app.pebo.sync.CloudNoteRemote
import app.pebo.sync.CloudProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OneDriveNoteRemote(
    private val http: HttpClient,
    private val accessToken: suspend () -> String,
) : CloudNoteRemote {
    override val provider: CloudProvider = CloudProvider.OneDrive

    override suspend fun listFiles(): List<CloudNoteFile> {
        val token = accessToken()
        val children = http.get("$GraphRoot/me/drive/special/approot:/notes:/children") {
            bearerAuth(token)
        }.body<OneDriveChildrenResponse>()

        return children.value
            .filter { it.file != null && it.name.endsWith(".md", ignoreCase = true) }
            .map { item ->
                val content = http.get("$GraphRoot/me/drive/items/${item.id}/content") {
                    bearerAuth(token)
                }.bodyAsText()
                val note = NoteFile.parse(content, fallbackId = item.id, trashed = false)
                CloudNoteFile(
                    remoteId = item.id,
                    noteId = note.id,
                    fileName = item.name,
                    content = content,
                    revision = item.eTag ?: item.cTag ?: item.id,
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
        val url = if (previousRemoteId != null) {
            "$GraphRoot/me/drive/items/$previousRemoteId/content"
        } else {
            "$GraphRoot/me/drive/special/approot:/notes/${urlEncode(fileName)}:/content"
        }
        val item = http.put(url) {
            bearerAuth(token)
            contentType(ContentType.Text.Plain)
            setBody(content.encodeToByteArray())
        }.body<OneDriveItem>()

        return CloudNoteFile(
            remoteId = item.id,
            noteId = noteId,
            fileName = item.name,
            content = content,
            revision = item.eTag ?: item.cTag ?: item.id,
        )
    }

    private companion object {
        const val GraphRoot = "https://graph.microsoft.com/v1.0"
    }
}

@Serializable
private data class OneDriveChildrenResponse(
    val value: List<OneDriveItem> = emptyList(),
)

@Serializable
private data class OneDriveItem(
    val id: String,
    val name: String,
    @SerialName("eTag") val eTag: String? = null,
    @SerialName("cTag") val cTag: String? = null,
    val file: OneDriveFileFacet? = null,
)

@Serializable
private class OneDriveFileFacet
