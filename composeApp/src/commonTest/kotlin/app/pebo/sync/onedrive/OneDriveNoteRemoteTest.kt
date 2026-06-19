package app.pebo.sync.onedrive

import app.pebo.core.Note
import app.pebo.core.NoteFile
import app.pebo.sync.CloudProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneDriveNoteRemoteTest {
    @Test
    fun listsMarkdownFilesFromAppFolderAndDownloadsContent() = runBlocking {
        val note = Note.new("# Cloud\nBody")
        val client = mockOneDriveClient { url, _ ->
            when {
                url.endsWith("/children") -> json(
                    """{"value":[{"id":"item-1","name":"cloud.md","eTag":"etag-1","file":{}},{"id":"folder","name":"nested","folder":{}}]}""",
                )
                url.endsWith("/items/item-1/content") -> text(NoteFile.serialize(note))
                else -> error("Unexpected URL $url")
            }
        }

        val remote = OneDriveNoteRemote(client) { "access-token" }
        val files = remote.listFiles()

        assertEquals(CloudProvider.OneDrive, remote.provider)
        assertEquals(1, files.size)
        assertEquals(note.id, files.single().noteId)
        assertEquals("etag-1", files.single().revision)
    }

    @Test
    fun uploadsNewNoteToAppFolderNotesPath() = runBlocking {
        var uploadUrl = ""
        var uploadBody = ""
        val note = Note.new("# Upload\nBody")
        val client = mockOneDriveClient { url, requestBody ->
            uploadUrl = url
            uploadBody = requestBody
            json("""{"id":"created-1","name":"upload.md","eTag":"etag-created","file":{}}""")
        }

        val uploaded = OneDriveNoteRemote(client) { "access-token" }.upload(
            noteId = note.id,
            fileName = "upload.md",
            content = NoteFile.serialize(note),
        )

        assertTrue(uploadUrl.endsWith("/me/drive/special/approot:/notes/upload.md:/content"))
        assertTrue(uploadBody.contains("# Upload"))
        assertEquals("created-1", uploaded.remoteId)
        assertEquals("etag-created", uploaded.revision)
    }

    private fun mockOneDriveClient(handler: (String, String) -> MockResponse): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    val body = when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                        else -> ""
                    }
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    val response = handler(request.url.toString(), body)
                    respond(
                        content = response.content,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, response.contentType),
                    )
                }
            }
        }

    private fun json(content: String): MockResponse = MockResponse(content, "application/json")
    private fun text(content: String): MockResponse = MockResponse(content, "text/plain")

    private data class MockResponse(val content: String, val contentType: String)
}
