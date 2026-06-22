package app.pebo.sync.googledrive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleDriveFoldersTest {
    @Test
    fun reusesExistingFolderWithoutCreating() = runBlocking {
        val requests = ArrayList<Pair<HttpMethod, String>>()
        val client = mockClient { method, url ->
            requests += method to url
            if (method == HttpMethod.Get) {
                assertTrue(url.contains("vnd.google-apps.folder"), "folder query: $url")
                """{"files":[{"id":"existing-folder","name":"Pebo Notes"}]}"""
            } else {
                error("Should not create a folder when one already exists: $method $url")
            }
        }

        val id = GoogleDriveFolders.findOrCreateNotesFolder(client, { "access-token" })

        assertEquals("existing-folder", id)
        assertEquals(1, requests.size)
    }

    @Test
    fun createsFolderWhenNoneExists() = runBlocking {
        val requests = ArrayList<Pair<HttpMethod, String>>()
        val client = mockClient { method, url ->
            requests += method to url
            when (method) {
                HttpMethod.Get -> """{"files":[]}"""
                HttpMethod.Post -> """{"id":"new-folder"}"""
                else -> error("Unexpected $method $url")
            }
        }

        val id = GoogleDriveFolders.findOrCreateNotesFolder(client, { "access-token" }, name = "Pebo Notes")

        assertEquals("new-folder", id)
        assertEquals(2, requests.size)
        assertEquals(HttpMethod.Get, requests[0].first)
        assertEquals(HttpMethod.Post, requests[1].first)
        assertTrue(requests[1].second.contains("/drive/v3/files"))
    }

    private fun mockClient(handler: (HttpMethod, String) -> String): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = handler(request.method, request.url.toString()),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
}
