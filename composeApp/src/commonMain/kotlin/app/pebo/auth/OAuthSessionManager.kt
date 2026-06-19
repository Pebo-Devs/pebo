package app.pebo.auth

class OAuthSessionManager(
    private val tokenClient: OAuthTokenClient,
    private val tokenStore: SecureTokenStore,
    private val nowEpochMillis: () -> Long,
) {
    suspend fun completeAuthorization(
        config: OAuthClientConfig,
        code: String,
        verifier: String,
    ): StoredOAuthTokens {
        val tokens = tokenClient.exchangeCode(config, code, verifier)
            .toStored(config.provider.id, nowEpochMillis())
        tokenStore.save(tokens)
        return tokens
    }

    suspend fun refresh(config: OAuthClientConfig): StoredOAuthTokens {
        val existing = tokenStore.load(config.provider.id)
            ?: error("No stored OAuth tokens for ${config.provider.displayName}")
        val refreshToken = existing.refreshToken
            ?: error("No refresh token stored for ${config.provider.displayName}")
        val tokens = tokenClient.refresh(config, refreshToken)
            .toStored(config.provider.id, nowEpochMillis())
        tokenStore.save(tokens)
        return tokens
    }

    suspend fun signOut(provider: OAuthProviderId) {
        tokenStore.clear(provider)
    }
}
