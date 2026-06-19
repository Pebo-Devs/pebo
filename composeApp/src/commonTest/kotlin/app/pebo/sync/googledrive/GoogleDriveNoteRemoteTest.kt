package app.pebo.sync.googledrive

import app.pebo.core.Note
import app.pebo.core.NoteFile
import app.pebo.sync.CloudProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleDriveNoteRemoteTest {
    @Test
    fun listsMarkdownFilesFromConfiguredFolderAndDownloadsContent() = runBlocking {
        val note = Note.new("# GDrive\nBody")
        val client = mockGoogleClient { method, url, _ ->
            when {
                method == HttpMethod.Get && url.contains("/drive/v3/files?") -> json(
                    """{"files":[{"id":"g-1","name":"gdrive.md","headRevisionId":"rev-1"}]}""",
                )
                method == HttpMethod.Get && url.endsWith("/drive/v3/files/g-1?alt=media") ->
                    text(NoteFile.serialize(note))
                else -> error("Unexpected request $method $url")
            }
        }

        val remote = GoogleDriveNoteRemote(client, { "access-token" }, notesFolderId = "folder-1")
        val files = remote.listFiles()

        assertEquals(CloudProvider.GoogleDrive, remote.provider)
        assertEquals(1, files.size)
        assertEquals(note.id, files.single().noteId)
        assertEquals("rev-1", files.single().revision)
    }

    @Test
    fun createsMetadataThenUploadsMediaForNewNote() = runBlocking {
        val requests = ArrayList<String>()
        var mediaBody = ""
        val note = Note.new("# Upload\nBody")
        val client = mockGoogleClient { method, url, body ->
            requests += "$method $url"
            when {
                method == HttpMethod.Post && url.contains("/drive/v3/files?") -> {
                    assertTrue(body.contains("upload.md"))
                    assertTrue(body.contains("folder-1"))
                    json("""{"id":"g-created","name":"upload.md"}""")
                }
                method == HttpMethod.Patch && url.contains("/upload/drive/v3/files/g-created") -> {
                    mediaBody = body
                    json("""{"id":"g-created","name":"upload.md","headRevisionId":"rev-created"}""")
                }
                else -> error("Unexpected request $method $url")
            }
        }

        val uploaded = GoogleDriveNoteRemote(client, { "access-token" }, notesFolderId = "folder-1").upload(
            noteId = note.id,
            fileName = "upload.md",
            content = NoteFile.serialize(note),
        )

        assertEquals(2, requests.size)
        assertTrue(mediaBody.contains("# Upload"))
        assertEquals("g-created", uploaded.remoteId)
        assertEquals("rev-created", uploaded.revision)
    }

    private fun mockGoogleClient(handler: (HttpMethod, String, String) -> MockResponse): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    val body = when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                        else -> content.toString()
                    }
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    val response = handler(request.method, request.url.toString(), body)
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
