package app.pebo.auth

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.util.Base64

/**
 * Desktop secure storage for OAuth tokens.
 *
 *  - **Windows** encrypts the vault at rest with DPAPI ([Crypt32Util]) and stores it in [path].
 *  - **macOS** stores the vault in the login Keychain (a `kSecClassGenericPassword` item) via the
 *    `security` tool; [path]'s file name is used as the Keychain account so independent stores stay
 *    isolated. The on-disk [path] is not used on macOS.
 *
 * Linux has no secure backend wired yet, so construction fails fast there.
 */
class DesktopSecureTokenStore(
    private val fs: FileSystem,
    private val path: Path,
) : SecureTokenStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private var cache: LinkedHashMap<OAuthProviderId, StoredOAuthTokens>? = null
    private val platform = DesktopPlatform.current()

    init {
        require(platform != DesktopPlatform.OTHER) {
            "DesktopSecureTokenStore supports Windows (DPAPI) and macOS (Keychain) only."
        }
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
        val plain: String? = when (platform) {
            DesktopPlatform.WINDOWS -> readWindows()
            DesktopPlatform.MACOS -> MacKeychain.read(KEYCHAIN_SERVICE, path.name)
            DesktopPlatform.OTHER -> null
        }
        val records = plain?.let { json.decodeFromString<TokenVault>(it).tokens } ?: emptyList()
        return LinkedHashMap<OAuthProviderId, StoredOAuthTokens>().also { map ->
            for (record in records) map[record.provider] = record
            cache = map
        }
    }

    private fun write(records: LinkedHashMap<OAuthProviderId, StoredOAuthTokens>) {
        val plain = json.encodeToString(TokenVault(records.values.toList()))
        when (platform) {
            DesktopPlatform.WINDOWS -> writeWindows(plain)
            DesktopPlatform.MACOS -> MacKeychain.write(KEYCHAIN_SERVICE, path.name, plain)
            DesktopPlatform.OTHER -> error("No secure token backend for this platform")
        }
        cache = records
    }

    private fun readWindows(): String? {
        if (!fs.exists(path)) return null
        val encrypted = fs.read(path) { readByteArray() }
        return Crypt32Util.cryptUnprotectData(encrypted).decodeToString()
    }

    private fun writeWindows(plain: String) {
        path.parent?.let { fs.createDirectories(it) }
        val encrypted = Crypt32Util.cryptProtectData(plain.encodeToByteArray())
        val tmp = path.parent!! / (path.name + ".tmp")
        fs.write(tmp) { write(encrypted) }
        fs.atomicMove(tmp, path)
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "app.pebo.oauth"
    }
}

private enum class DesktopPlatform {
    WINDOWS,
    MACOS,
    OTHER,
    ;

    companion object {
        fun current(): DesktopPlatform {
            val os = System.getProperty("os.name").orEmpty()
            return when {
                os.contains("Windows", ignoreCase = true) -> WINDOWS
                os.contains("Mac", ignoreCase = true) -> MACOS
                else -> OTHER
            }
        }
    }
}

/**
 * Minimal macOS Keychain access via the `security` command-line tool. The vault JSON is base64
 * encoded so it survives the tool's text handling.
 *
 * Note: `security add-generic-password -w <value>` passes the (encoded) value as a process argument,
 * a known limitation of the CLI. A future hardening can call `SecItem*` from Security.framework
 * directly (e.g. via JNA) to keep the secret off the argument list.
 */
private object MacKeychain {
    fun read(service: String, account: String): String? {
        val proc = ProcessBuilder("security", "find-generic-password", "-s", service, "-a", account, "-w")
            .redirectErrorStream(false)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
        proc.errorStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()
        if (code != 0 || out.isEmpty()) return null
        return runCatching { Base64.getDecoder().decode(out).decodeToString() }.getOrNull()
    }

    fun write(service: String, account: String, plain: String) {
        val encoded = Base64.getEncoder().encodeToString(plain.encodeToByteArray())
        val proc = ProcessBuilder(
            "security", "add-generic-password", "-U", "-s", service, "-a", account, "-w", encoded,
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()
        require(code == 0) { "Keychain write failed (exit $code): ${out.trim()}" }
    }
}

@Serializable
private data class TokenVault(
    val tokens: List<StoredOAuthTokens> = emptyList(),
)
