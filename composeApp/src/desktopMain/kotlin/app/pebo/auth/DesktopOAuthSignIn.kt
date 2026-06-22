package app.pebo.auth

import java.awt.Desktop
import java.net.URI

class DesktopOAuthSignIn(
    private val tokenClient: OAuthTokenClient,
    private val tokenStore: SecureTokenStore,
    private val openBrowser: (String) -> Unit = { url -> Desktop.getDesktop().browse(URI.create(url)) },
    private val stateFactory: () -> String = { newPkceVerifier() },
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun signIn(
        provider: OAuthProviderConfig,
        clientId: String,
        clientSecret: String? = null,
        timeoutMillis: Long = 120_000,
    ): StoredOAuthTokens {
        require(clientId.isNotBlank()) { "OAuth client id is required for ${provider.displayName}" }
        DesktopOAuthRedirectReceiver().use { receiver ->
            val pkce = newPkcePair()
            val state = stateFactory()
            val config = OAuthClientConfig(
                provider = provider,
                clientId = clientId,
                redirectUri = receiver.redirectUri,
                clientSecret = clientSecret,
            )
            openBrowser(AuthorizationRequest(config, state, pkce).url())
            val redirect = receiver.await(timeoutMillis)
            return when (redirect) {
                is OAuthRedirectResult.Code -> {
                    require(redirect.state == state) { "OAuth state mismatch for ${provider.displayName}" }
                    OAuthSessionManager(tokenClient, tokenStore, nowEpochMillis)
                        .completeAuthorization(config, redirect.code, pkce.verifier)
                }
                is OAuthRedirectResult.Error -> error(
                    "OAuth sign-in failed for ${provider.displayName}: ${redirect.error}" +
                        (redirect.description?.let { " ($it)" } ?: ""),
                )
            }
        }
    }
}
