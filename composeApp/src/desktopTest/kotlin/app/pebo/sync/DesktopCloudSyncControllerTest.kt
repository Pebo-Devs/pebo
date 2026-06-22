package app.pebo.sync

import app.pebo.auth.OAuthProviderId
import app.pebo.auth.SecureTokenStore
import app.pebo.auth.StoredOAuthTokens
import app.pebo.core.Note
import app.pebo.data.NoteStore
import app.pebo.data.StoreSnapshot
import app.pebo.ui.StorageProvider
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
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards [DesktopCloudSyncController] — the desktop composition layer that turns "select OneDrive"
 * into sign-in + [SyncEngine] sync. Drives the deterministic paths (provider configuration, stored
 * connection, error-on-no-token, a clean empty sync, disconnect) with a fake token store and a
 * MockEngine HTTP client; the interactive browser sign-in is exercised only in real use.
 */
class DesktopCloudSyncControllerTest {
    private val clientIdProperty = "pebo.onedrive.clientId"

    @AfterTest
    fun clearClientId() {
        System.clearProperty(clientIdProperty)
    }

    @Test
    fun configuredProvidersReflectsClientIdProperty() {
        val controller = controller()

        System.clearProperty(clientIdProperty)
        assertTrue(StorageProvider.OneDrive !in controller.configuredProviders())

        System.setProperty(clientIdProperty, "test-client")
        assertTrue(StorageProvider.OneDrive in controller.configuredProviders())
    }

    @Test
    fun connectedProviderReflectsStoredTokens() = runBlocking {
        val store = FakeSecureTokenStore()
        val controller = controller(tokenStore = store)

        assertNull(controller.connectedProvider())

        store.save(oneDriveToken())
        assertEquals(StorageProvider.OneDrive, controller.connectedProvider())
    }

    @Test
    fun syncWithoutStoredTokenReturnsErrorStateNotCrash() = runBlocking {
        System.setProperty(clientIdProperty, "test-client")
        val controller = controller(tokenStore = FakeSecureTokenStore())

        val state = controller.sync(StorageProvider.OneDrive, emptyStore(), notesDir = "/pebo")

        assertEquals(CloudStatus.Error, state.status)
        assertEquals(StorageProvider.OneDrive, state.provider)
        assertTrue(state.message.contains("Not connected", ignoreCase = true))
    }

    @Test
    fun syncWithoutClientIdAsksUserToSetEnvVar() = runBlocking {
        System.clearProperty(clientIdProperty)
        val controller = controller(tokenStore = FakeSecureTokenStore())

        val state = controller.sync(StorageProvider.OneDrive, emptyStore(), notesDir = "/pebo")

        assertEquals(CloudStatus.Error, state.status)
        assertTrue(state.message.contains("PEBO_ONEDRIVE_CLIENT_ID"))
    }

    @Test
    fun cleanSyncWithValidTokenReportsConnected() = runBlocking {
        System.setProperty(clientIdProperty, "test-client")
        val store = FakeSecureTokenStore().apply { save(oneDriveToken()) }
        val http = mockClient { url ->
            if (url.endsWith("/children")) """{"value":[]}""" else error("Unexpected URL $url")
        }
        val controller = controller(tokenStore = store, http = http)

        val state = controller.sync(StorageProvider.OneDrive, emptyStore(), notesDir = "/pebo")

        assertEquals(CloudStatus.Connected, state.status)
        assertEquals(StorageProvider.OneDrive, state.provider)
        assertTrue(state.message.contains("0 up, 0 down"), "was: ${state.message}")
    }

    @Test
    fun disconnectClearsStoredTokens() = runBlocking {
        val store = FakeSecureTokenStore().apply { save(oneDriveToken()) }
        val controller = controller(tokenStore = store)

        controller.disconnect(StorageProvider.OneDrive)

        assertNull(controller.connectedProvider())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun controller(
        tokenStore: SecureTokenStore = FakeSecureTokenStore(),
        http: HttpClient = mockClient { """{"value":[]}""" },
    ): DesktopCloudSyncController =
        DesktopCloudSyncController(
            fs = FileSystem.SYSTEM,
            appDir = (Files.createTempDirectory("pebo-cloud-test").toString()).toPath(),
            deviceName = "test (desktop)",
            http = http,
            tokenStore = tokenStore,
        )

    private fun oneDriveToken(): StoredOAuthTokens = StoredOAuthTokens(
        provider = OAuthProviderId.OneDrive,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtEpochMillis = System.currentTimeMillis() + 3_600_000,
        tokenType = "Bearer",
        scope = "Files.ReadWrite.AppFolder offline_access",
    )

    private fun emptyStore(): NoteStore = object : NoteStore {
        override suspend fun load() = StoreSnapshot(active = emptyList(), trashed = emptyList())
        override suspend fun save(note: Note) {}
        override suspend fun moveToTrash(id: String) {}
        override suspend fun restore(id: String) {}
        override suspend fun purge(id: String) {}
        override suspend fun emptyTrash() {}
    }

    private fun mockClient(handler: (String) -> String): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    respond(
                        content = handler(request.url.toString()),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

    private class FakeSecureTokenStore : SecureTokenStore {
        private val tokens = HashMap<OAuthProviderId, StoredOAuthTokens>()
        override suspend fun load(provider: OAuthProviderId): StoredOAuthTokens? = tokens[provider]
        override suspend fun save(tokens: StoredOAuthTokens) { this.tokens[tokens.provider] = tokens }
        override suspend fun clear(provider: OAuthProviderId) { tokens.remove(provider) }
    }
}
