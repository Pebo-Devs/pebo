package app.pebo.sync

import app.pebo.auth.DesktopOAuthClientIds
import app.pebo.auth.DesktopOAuthSignIn
import app.pebo.auth.DesktopSecureTokenStore
import app.pebo.auth.OAuthClientConfig
import app.pebo.auth.OAuthProviderConfig
import app.pebo.auth.OAuthProviderId
import app.pebo.auth.OAuthSessionManager
import app.pebo.auth.OAuthTokenClient
import app.pebo.auth.SecureTokenStore
import app.pebo.auth.configurePeboHttpClient
import app.pebo.data.NoteStore
import app.pebo.sync.googledrive.GoogleDriveFolders
import app.pebo.sync.googledrive.GoogleDriveNoteRemote
import app.pebo.sync.onedrive.OneDriveNoteRemote
import app.pebo.ui.StorageProvider
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Desktop implementation of [CloudSyncController]. Composes the already-tested pieces — a CIO
 * [HttpClient], the OS-secured [DesktopSecureTokenStore] (Windows DPAPI / macOS Keychain), the
 * Authorization-Code + PKCE [DesktopOAuthSignIn], the provider remote, and the [SyncEngine] — into
 * the select → sign-in → sync flow the Settings screen drives.
 *
 * OneDrive (Graph app-folder, no bootstrap) and Google Drive (a find-or-create notes folder via
 * [GoogleDriveFolders]) are both wired end-to-end through provider-neutral [oauthId]/[providerConfig]/
 * [remoteFor] mappers, so a new provider is a localized addition.
 */
class DesktopCloudSyncController(
    private val fs: FileSystem,
    private val appDir: Path,
    private val deviceName: String = defaultDeviceName(),
    private val http: HttpClient = configurePeboHttpClient(),
    private val tokenStore: SecureTokenStore = DesktopSecureTokenStore(fs, appDir / "oauth.dat"),
) : CloudSyncController {
    private val tokenClient = OAuthTokenClient(http)
    private val signIn = DesktopOAuthSignIn(tokenClient, tokenStore)
    private val session = OAuthSessionManager(tokenClient, tokenStore) { System.currentTimeMillis() }

    private var googleFolderId: String? = null

    override fun configuredProviders(): Set<StorageProvider> =
        StorageProvider.entries
            .filter { provider -> oauthId(provider)?.let { DesktopOAuthClientIds.isConfigured(it) } == true }
            .toSet()

    override suspend fun connectedProvider(): StorageProvider? =
        StorageProvider.entries.firstOrNull { provider ->
            oauthId(provider)?.let { tokenStore.load(it) } != null
        }

    override suspend fun connect(provider: StorageProvider, local: NoteStore, notesDir: String): CloudSyncState {
        val providerId = oauthId(provider) ?: return unsupported(provider)
        val clientId = DesktopOAuthClientIds.clientIdFor(providerId)
            ?: return needsClientId(provider)
        return try {
            if (tokenStore.load(providerId) == null) {
                // Opens the system browser and suspends until the loopback redirect returns the code.
                signIn.signIn(providerConfig(provider), clientId, DesktopOAuthClientIds.clientSecretFor(providerId))
            }
            sync(provider, local, notesDir)
        } catch (t: Throwable) {
            CloudSyncState(CloudStatus.Error, provider, t.message ?: "Couldn't connect to ${provider.displayName}.")
        }
    }

    override suspend fun sync(provider: StorageProvider, local: NoteStore, notesDir: String): CloudSyncState {
        val providerId = oauthId(provider) ?: return unsupported(provider)
        val clientId = DesktopOAuthClientIds.clientIdFor(providerId)
            ?: return needsClientId(provider)
        val clientConfig = OAuthClientConfig(
            providerConfig(provider),
            clientId,
            redirectUri = "",
            clientSecret = DesktopOAuthClientIds.clientSecretFor(providerId),
        )
        val remote = remoteFor(provider) { accessToken(clientConfig) }
        val metadata = FileSyncMetadataStore(fs, metadataPath(notesDir, provider))
        val engine = SyncEngine(local, remote, metadata, deviceName)
        return runCatching { engine.sync() }.fold(
            onSuccess = { report -> CloudSyncState(CloudStatus.Connected, provider, summarize(provider, report)) },
            onFailure = { t -> CloudSyncState(CloudStatus.Error, provider, t.message ?: "Sync failed.") },
        )
    }

    override suspend fun disconnect(provider: StorageProvider) {
        oauthId(provider)?.let { session.signOut(it) }
    }

    /** Returns a valid access token, refreshing it via the stored refresh token when near expiry. */
    private suspend fun accessToken(config: OAuthClientConfig): String {
        val stored = tokenStore.load(config.provider.id)
            ?: error("Not connected to ${config.provider.displayName}.")
        val expiresAt = stored.expiresAtEpochMillis
        val current = if (expiresAt != null && System.currentTimeMillis() >= expiresAt - EXPIRY_SKEW_MS) {
            session.refresh(config)
        } else {
            stored
        }
        return current.accessToken
    }

    private suspend fun remoteFor(provider: StorageProvider, accessToken: suspend () -> String): CloudNoteRemote =
        when (provider) {
            StorageProvider.OneDrive -> OneDriveNoteRemote(http, accessToken)
            StorageProvider.GoogleDrive ->
                GoogleDriveNoteRemote(http, accessToken, googleNotesFolderId(accessToken))
            else -> error("No cloud remote for $provider")
        }

    /** Find-or-create the Drive notes folder once per controller, then reuse its id for later syncs. */
    private suspend fun googleNotesFolderId(accessToken: suspend () -> String): String =
        googleFolderId ?: GoogleDriveFolders.findOrCreateNotesFolder(http, accessToken)
            .also { googleFolderId = it }

    private fun providerConfig(provider: StorageProvider): OAuthProviderConfig =
        when (provider) {
            StorageProvider.OneDrive -> OAuthProviderConfig.OneDrive
            StorageProvider.GoogleDrive -> OAuthProviderConfig.GoogleDrive
            else -> error("No OAuth config for $provider")
        }

    private fun oauthId(provider: StorageProvider): OAuthProviderId? =
        when (provider) {
            StorageProvider.OneDrive -> OAuthProviderId.OneDrive
            StorageProvider.GoogleDrive -> OAuthProviderId.GoogleDrive
            else -> null
        }

    private fun metadataPath(notesDir: String, provider: StorageProvider): Path {
        val base = notesDir.takeIf { it.isNotBlank() }?.toPath() ?: appDir
        return base / ".pebo" / "${provider.name.lowercase()}-sync.json"
    }

    private fun summarize(provider: StorageProvider, report: SyncReport): String {
        val pushed = report.events.count { it is SyncEvent.Pushed }
        val pulled = report.events.count { it is SyncEvent.Pulled }
        val conflicts = report.conflicts
        return buildString {
            append("Synced with ${provider.displayName} · $pushed up, $pulled down")
            if (conflicts > 0) append(", $conflicts conflict${if (conflicts == 1) "" else "s"} kept")
            append(".")
        }
    }

    private fun needsClientId(provider: StorageProvider) = CloudSyncState(
        CloudStatus.Error,
        provider,
        "Set ${envVar(provider)} and restart Pebo to connect ${provider.displayName}.",
    )

    private fun unsupported(provider: StorageProvider) =
        CloudSyncState(CloudStatus.Error, provider, "${provider.displayName} sync isn't available yet.")

    private fun envVar(provider: StorageProvider): String =
        when (provider) {
            StorageProvider.OneDrive -> "PEBO_ONEDRIVE_CLIENT_ID"
            StorageProvider.GoogleDrive -> "PEBO_GOOGLE_CLIENT_ID"
            else -> "PEBO_CLIENT_ID"
        }

    private companion object {
        const val EXPIRY_SKEW_MS = 60_000L

        fun defaultDeviceName(): String =
            (System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "desktop") + " (desktop)"
    }
}
