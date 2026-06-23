package app.pebo.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    private val sampleRelease = """
        {
          "tag_name": "v1.1.0",
          "name": "Pebo 1.1.0",
          "body": "Shiny new things.",
          "html_url": "https://github.com/Pebo-Devs/pebo/releases/tag/v1.1.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            { "name": "Pebo-1.1.0.msi", "browser_download_url": "https://dl/Pebo-1.1.0.msi", "size": 1000 },
            { "name": "Pebo-1.1.0.exe", "browser_download_url": "https://dl/Pebo-1.1.0.exe", "size": 1001 },
            { "name": "Pebo-1.1.0.dmg", "browser_download_url": "https://dl/Pebo-1.1.0.dmg", "size": 1002 },
            { "name": "pebo_1.1.0_amd64.deb", "browser_download_url": "https://dl/pebo_1.1.0_amd64.deb", "size": 1003 }
          ]
        }
    """.trimIndent()

    @Test
    fun reportsAvailableWithWindowsInstallerWhenNewer() = runBlocking {
        val checker = UpdateChecker(mockClient(sampleRelease))

        val result = checker.check(currentVersion = "1.0.0", platform = UpdatePlatform.Windows)

        val available = assertIs<UpdateCheck.Available>(result)
        assertEquals("1.1.0", available.release.version)
        assertEquals("v1.1.0", available.release.tag)
        assertEquals("Pebo 1.1.0", available.release.name)
        assertEquals("Pebo-1.1.0.msi", available.asset?.name)
        assertEquals("https://dl/Pebo-1.1.0.msi", available.asset?.downloadUrl)
    }

    @Test
    fun picksMacAssetForMacPlatform() = runBlocking {
        val checker = UpdateChecker(mockClient(sampleRelease))

        val result = checker.check(currentVersion = "1.0.0", platform = UpdatePlatform.MacOs)

        val available = assertIs<UpdateCheck.Available>(result)
        assertEquals("Pebo-1.1.0.dmg", available.asset?.name)
    }

    @Test
    fun availableWithNullAssetWhenNoInstallerForPlatform() = runBlocking {
        val checker = UpdateChecker(mockClient(sampleRelease))

        val result = checker.check(currentVersion = "1.0.0", platform = UpdatePlatform.Android)

        val available = assertIs<UpdateCheck.Available>(result)
        assertNull(available.asset)
    }

    @Test
    fun reportsUpToDateWhenVersionsMatch() = runBlocking {
        val checker = UpdateChecker(mockClient(sampleRelease))

        val result = checker.check(currentVersion = "1.1.0", platform = UpdatePlatform.Windows)

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun reportsUpToDateWhenRunningNewer() = runBlocking {
        val checker = UpdateChecker(mockClient(sampleRelease))

        val result = checker.check(currentVersion = "2.0.0", platform = UpdatePlatform.Windows)

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun sendsRequiredGitHubHeaders() = runBlocking {
        var sawUserAgent = false
        var sawAccept = false
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    sawUserAgent = request.headers[HttpHeaders.UserAgent]?.isNotBlank() == true
                    sawAccept = request.headers["Accept"]?.contains("github") == true
                    respond(
                        content = sampleRelease,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

        UpdateChecker(client).check(currentVersion = "1.0.0", platform = UpdatePlatform.Windows)

        assertTrue(sawUserAgent, "GitHub requires a User-Agent header")
        assertTrue(sawAccept, "should send the GitHub Accept header")
    }

    @Test
    fun throwsOnNonSuccessStatus() = runBlocking {
        val checker = UpdateChecker(
            mockClient("""{"message":"Not Found"}""", status = HttpStatusCode.NotFound),
        )

        val failure = assertFailsWith<IllegalStateException> {
            checker.check(currentVersion = "1.0.0", platform = UpdatePlatform.Windows)
        }
        assertTrue(failure.message!!.contains("404"))
    }

    private fun mockClient(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
}
