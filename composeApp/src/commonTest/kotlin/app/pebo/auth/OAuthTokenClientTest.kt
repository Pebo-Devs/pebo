package app.pebo.auth

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

class OAuthTokenClientTest {
    @Test
    fun exchangesAuthorizationCodeWithPkceVerifier() = runBlocking {
        var requestBody = ""
        val client = OAuthTokenClient(mockJsonClient { body ->
            requestBody = body
            """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"token_type":"Bearer"}"""
        })

        val tokens = client.exchangeCode(googleConfig(), code = "code-123", verifier = "verifier-456")

        assertEquals("access", tokens.accessToken)
        assertEquals("refresh", tokens.refreshToken)
        assertTrue(requestBody.contains("grant_type=authorization_code"))
        assertTrue(requestBody.contains("code=code-123"))
        assertTrue(requestBody.contains("code_verifier=verifier-456"))
        assertTrue(requestBody.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A4567%2Fcb"))
    }

    @Test
    fun refreshKeepsExistingRefreshTokenWhenProviderDoesNotRotate() = runBlocking {
        var requestBody = ""
        val client = OAuthTokenClient(mockJsonClient { body ->
            requestBody = body
            """{"access_token":"new-access","expires_in":1800,"token_type":"Bearer"}"""
        })

        val tokens = client.refresh(googleConfig(), refreshToken = "existing-refresh")

        assertEquals("new-access", tokens.accessToken)
        assertEquals("existing-refresh", tokens.refreshToken)
        assertTrue(requestBody.contains("grant_type=refresh_token"))
        assertTrue(requestBody.contains("refresh_token=existing-refresh"))
    }

    private fun googleConfig(): OAuthClientConfig =
        OAuthClientConfig(
            provider = OAuthProviderConfig.GoogleDrive,
            clientId = "client-id",
            redirectUri = "http://127.0.0.1:4567/cb",
        )

    private fun mockJsonClient(handler: (String) -> String): HttpClient =
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
                    respond(
                        content = handler(body),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
}
