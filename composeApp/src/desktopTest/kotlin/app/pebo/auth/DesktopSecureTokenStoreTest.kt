package app.pebo.auth

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopSecureTokenStoreTest {
    @Test
    fun persistsTokensEncryptedWithDpapi() = runBlocking {
        if (!isWindows()) return@runBlocking
        val path = (Files.createTempDirectory("pebo-token-store").toString() + "/tokens.bin").toPath()
        val store = DesktopSecureTokenStore(FileSystem.SYSTEM, path)

        store.save(
            StoredOAuthTokens(
                provider = OAuthProviderId.GoogleDrive,
                accessToken = "access-secret",
                refreshToken = "refresh-secret",
                expiresAtEpochMillis = 123_456L,
                tokenType = "Bearer",
                scope = "drive.file",
            ),
        )

        val raw = FileSystem.SYSTEM.read(path) { readByteArray() }.decodeToString()
        assertFalse(raw.contains("access-secret"))
        assertFalse(raw.contains("refresh-secret"))

        val reloaded = DesktopSecureTokenStore(FileSystem.SYSTEM, path).load(OAuthProviderId.GoogleDrive)
        assertEquals("access-secret", reloaded?.accessToken)
        assertEquals("refresh-secret", reloaded?.refreshToken)
    }

    @Test
    fun clearRemovesOnlySelectedProvider() = runBlocking {
        if (!isWindows()) return@runBlocking
        val path = (Files.createTempDirectory("pebo-token-store").toString() + "/tokens.bin").toPath()
        val store = DesktopSecureTokenStore(FileSystem.SYSTEM, path)
        store.save(StoredOAuthTokens(OAuthProviderId.GoogleDrive, "g-access", "g-refresh", null, null, null))
        store.save(StoredOAuthTokens(OAuthProviderId.OneDrive, "o-access", "o-refresh", null, null, null))

        store.clear(OAuthProviderId.GoogleDrive)

        val reloaded = DesktopSecureTokenStore(FileSystem.SYSTEM, path)
        assertEquals(null, reloaded.load(OAuthProviderId.GoogleDrive))
        assertEquals("o-access", reloaded.load(OAuthProviderId.OneDrive)?.accessToken)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)
}
