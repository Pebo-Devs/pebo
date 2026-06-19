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

    @Test
    fun persistsTokensInKeychainOnMac() = runBlocking {
        if (!isMac()) return@runBlocking
        val dir = Files.createTempDirectory("pebo-token-store").toString()
        val account = "pebo-test-" + java.util.UUID.randomUUID()
        val path = ("$dir/$account").toPath()
        val store = DesktopSecureTokenStore(FileSystem.SYSTEM, path)
        try {
            try {
                store.save(StoredOAuthTokens(OAuthProviderId.GoogleDrive, "g-access", "g-refresh", 123L, "Bearer", "drive.file"))
            } catch (e: Exception) {
                return@runBlocking // login keychain unavailable/locked (e.g. headless CI) -> skip
            }
            store.save(StoredOAuthTokens(OAuthProviderId.OneDrive, "o-access", "o-refresh", null, null, null))

            val reloaded = DesktopSecureTokenStore(FileSystem.SYSTEM, path)
            assertEquals("g-access", reloaded.load(OAuthProviderId.GoogleDrive)?.accessToken)
            assertEquals("o-refresh", reloaded.load(OAuthProviderId.OneDrive)?.refreshToken)

            reloaded.clear(OAuthProviderId.GoogleDrive)
            val afterClear = DesktopSecureTokenStore(FileSystem.SYSTEM, path)
            assertEquals(null, afterClear.load(OAuthProviderId.GoogleDrive))
            assertEquals("o-access", afterClear.load(OAuthProviderId.OneDrive)?.accessToken)
        } finally {
            ProcessBuilder("security", "delete-generic-password", "-s", "app.pebo.oauth", "-a", account)
                .redirectErrorStream(true).start().waitFor()
        }
    }

    private fun isMac(): Boolean =
        System.getProperty("os.name").contains("Mac", ignoreCase = true)

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)
}
