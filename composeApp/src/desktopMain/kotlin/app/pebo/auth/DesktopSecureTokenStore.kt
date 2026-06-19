package app.pebo.auth

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class DesktopSecureTokenStore(
    private val fs: FileSystem,
    private val path: Path,
) : SecureTokenStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private var cache: LinkedHashMap<OAuthProviderId, StoredOAuthTokens>? = null

    init {
        require(isWindows()) { "DesktopSecureTokenStore currently uses Windows DPAPI; add Keychain support for macOS." }
    }

    override suspend fun load(provider: OAuthProviderId): StoredOAuthTokens? = withContext(Dispatchers.Default) {
        loadAll()[provider]
    }

    override suspend fun save(tokens: StoredOAuthTokens): Unit = withContext(Dispatchers.Default) {
        val all = loadAll()
        all[tokens.provider] = tokens
        write(all)
    }

    override suspend fun clear(provider: OAuthProviderId): Unit = withContext(Dispatchers.Default) {
        val all = loadAll()
        all.remove(provider)
        write(all)
    }

    private fun loadAll(): LinkedHashMap<OAuthProviderId, StoredOAuthTokens> {
        cache?.let { return it }
        val records = if (fs.exists(path)) {
            val encrypted = fs.read(path) { readByteArray() }
            val plain = Crypt32Util.cryptUnprotectData(encrypted)
            json.decodeFromString<TokenVault>(plain.decodeToString()).tokens
        } else {
            emptyList()
        }
        return LinkedHashMap<OAuthProviderId, StoredOAuthTokens>().also { map ->
            for (record in records) map[record.provider] = record
            cache = map
        }
    }

    private fun write(records: LinkedHashMap<OAuthProviderId, StoredOAuthTokens>) {
        path.parent?.let { fs.createDirectories(it) }
        val plain = json.encodeToString(TokenVault(records.values.toList())).encodeToByteArray()
        val encrypted = Crypt32Util.cryptProtectData(plain)
        val tmp = path.parent!! / (path.name + ".tmp")
        fs.write(tmp) { write(encrypted) }
        fs.atomicMove(tmp, path)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)
}

@Serializable
private data class TokenVault(
    val tokens: List<StoredOAuthTokens> = emptyList(),
)
