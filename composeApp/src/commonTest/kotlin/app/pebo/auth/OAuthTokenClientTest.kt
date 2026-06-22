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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        assertFalse(requestBody.contains("client_secret"))
    }

    @Test
    fun sendsClientSecretWhenConfigured() = runBlocking {
        var requestBody = ""
        val client = OAuthTokenClient(mockJsonClient { body ->
            requestBody = body
            """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"token_type":"Bearer"}"""
        })

        client.exchangeCode(googleConfig(secret = "google-secret"), code = "code-123", verifier = "verifier-456")

        assertTrue(requestBody.contains("client_secret=google-secret"))
    }

    @Test
    fun refreshSendsClientSecretWhenConfigured() = runBlocking {
        var requestBody = ""
        val client = OAuthTokenClient(mockJsonClient { body ->
            requestBody = body
            """{"access_token":"new-access","expires_in":1800,"token_type":"Bearer"}"""
        })

        client.refresh(googleConfig(secret = "google-secret"), refreshToken = "existing-refresh")

        assertTrue(requestBody.contains("grant_type=refresh_token"))
        assertTrue(requestBody.contains("client_secret=google-secret"))
    }

    @Test
    fun surfacesProviderErrorBodyOnFailure() = runBlocking {
        val client = OAuthTokenClient(
            mockJsonClient(status = HttpStatusCode.BadRequest) {
                """{"error":"invalid_request","error_description":"client_secret is missing."}"""
            },
        )

        val failure = assertFailsWith<IllegalStateException> {
            client.exchangeCode(googleConfig(), code = "code-123", verifier = "verifier-456")
        }
        assertTrue(failure.message!!.contains("Google Drive token request failed (400)"))
        assertTrue(failure.message!!.contains("client_secret is missing."))
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

    private fun googleConfig(secret: String? = null): OAuthClientConfig =
        OAuthClientConfig(
            provider = OAuthProviderConfig.GoogleDrive,
            clientId = "client-id",
            redirectUri = "http://127.0.0.1:4567/cb",
            clientSecret = secret,
        )

    private fun mockJsonClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        handler: (String) -> String,
    ): HttpClient =
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
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
}
