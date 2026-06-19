package app.pebo.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android [SecureTokenStore] backed by Jetpack Security's [EncryptedSharedPreferences], which wraps
 * the values with a Keystore-held master key (AES-256-GCM). Mirrors the desktop DPAPI-backed
 * `DesktopSecureTokenStore`: tokens are encrypted at rest and keyed per [OAuthProviderId]. Like the
 * desktop store it is built but not yet wired into the UI (cloud providers remain opt-in).
 */
class AndroidSecureTokenStore(
    context: Context,
    fileName: String = "pebo_oauth_tokens",
) : SecureTokenStore {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun load(provider: OAuthProviderId): StoredOAuthTokens? = withContext(Dispatchers.IO) {
        prefs.getString(provider.name, null)?.let { json.decodeFromString<StoredOAuthTokens>(it) }
    }

    override suspend fun save(tokens: StoredOAuthTokens): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(tokens.provider.name, json.encodeToString(tokens)).apply()
    }

    override suspend fun clear(provider: OAuthProviderId): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(provider.name).apply()
    }
}
