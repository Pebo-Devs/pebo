package app.pebo.auth

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
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopOAuthSignInTest {
    @Test
    fun capturesRedirectExchangesCodeAndStoresTokens() = runBlocking {
        val store = MemorySecureTokenStore()
        val tokenClient = OAuthTokenClient(
            HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                engine {
                    addHandler {
                        respond(
                            content = """{"access_token":"access","refresh_token":"refresh","expires_in":60,"token_type":"Bearer"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            },
        )

        val signIn = DesktopOAuthSignIn(
            tokenClient = tokenClient,
            tokenStore = store,
            openBrowser = { authUrl ->
                val redirectUri = queryParam(authUrl, "redirect_uri")
                val state = queryParam(authUrl, "state")
                thread {
                    JdkHttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("$redirectUri?code=code-123&state=$state")).GET().build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
                }
            },
            stateFactory = { "state-123" },
            nowEpochMillis = { 1_000L },
        )

        val stored = signIn.signIn(OAuthProviderConfig.GoogleDrive, clientId = "client-id", timeoutMillis = 5_000)

        assertEquals("access", stored.accessToken)
        assertEquals("refresh", stored.refreshToken)
        assertEquals(61_000L, stored.expiresAtEpochMillis)
        assertEquals("access", store.load(OAuthProviderId.GoogleDrive)?.accessToken)
    }

    @Test
    fun clientIdsReadSystemProperties() {
        val old = System.getProperty("pebo.google.clientId")
        try {
            System.setProperty("pebo.google.clientId", "google-client")
            assertEquals("google-client", DesktopOAuthClientIds.clientIdFor(OAuthProviderId.GoogleDrive))
        } finally {
            if (old == null) System.clearProperty("pebo.google.clientId") else System.setProperty("pebo.google.clientId", old)
        }
    }

    private fun queryParam(url: String, name: String): String {
        val raw = url.substringAfter('?').split('&').first { it.startsWith("$name=") }.substringAfter('=')
        return URLDecoder.decode(raw, Charsets.UTF_8.name())
    }
}

private class MemorySecureTokenStore : SecureTokenStore {
    private val tokens = LinkedHashMap<OAuthProviderId, StoredOAuthTokens>()

    override suspend fun load(provider: OAuthProviderId): StoredOAuthTokens? = tokens[provider]

    override suspend fun save(tokens: StoredOAuthTokens) {
        this.tokens[tokens.provider] = tokens
    }

    override suspend fun clear(provider: OAuthProviderId) {
        tokens.remove(provider)
    }
}
