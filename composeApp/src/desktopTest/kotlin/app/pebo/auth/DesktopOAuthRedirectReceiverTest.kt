package app.pebo.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopOAuthRedirectReceiverTest {
    @Test
    fun capturesCodeAndStateFromLoopbackRedirect() = runBlocking {
        DesktopOAuthRedirectReceiver().use { receiver ->
            val awaited = async { receiver.await(timeoutMillis = 5_000) }
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("${receiver.redirectUri}?code=abc123&state=s1")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            assertTrue(response.body().contains("complete"))
            val result = assertIs<OAuthRedirectResult.Code>(awaited.await())
            assertEquals("abc123", result.code)
            assertEquals("s1", result.state)
        }
    }

    @Test
    fun capturesProviderErrorFromLoopbackRedirect() = runBlocking {
        DesktopOAuthRedirectReceiver().use { receiver ->
            val awaited = async { receiver.await(timeoutMillis = 5_000) }
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                    URI.create("${receiver.redirectUri}?error=access_denied&error_description=Denied&state=s2"),
                ).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            val result = assertIs<OAuthRedirectResult.Error>(awaited.await())
            assertEquals("access_denied", result.error)
            assertEquals("Denied", result.description)
            assertEquals("s2", result.state)
        }
    }
}
