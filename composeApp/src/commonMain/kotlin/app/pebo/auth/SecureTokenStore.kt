package app.pebo.auth

import kotlinx.serialization.Serializable

interface SecureTokenStore {
    suspend fun load(provider: OAuthProviderId): StoredOAuthTokens?
    suspend fun save(tokens: StoredOAuthTokens)
    suspend fun clear(provider: OAuthProviderId)
}

@Serializable
data class StoredOAuthTokens(
    val provider: OAuthProviderId,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long?,
    val tokenType: String?,
    val scope: String?,
)

fun OAuthTokens.toStored(
    provider: OAuthProviderId,
    issuedAtEpochMillis: Long,
): StoredOAuthTokens =
    StoredOAuthTokens(
        provider = provider,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochMillis = expiresInSeconds?.let { issuedAtEpochMillis + it * 1_000 },
        tokenType = tokenType,
        scope = scope,
    )
