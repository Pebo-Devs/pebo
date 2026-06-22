package app.pebo.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Android OAuth 2.0 Authorization Code + PKCE sign-in, mirroring `DesktopOAuthSignIn`. Instead of a
 * loopback HTTP server + system browser, it opens the authorization URL in a Chrome Custom Tab and
 * receives the redirect on the app's custom scheme via [OAuthRedirectActivity] / [OAuthRedirectBus].
 * State is validated and the code exchange completes through the shared [OAuthSessionManager].
 *
 * Built for parity with the desktop flow; like desktop it is not wired into the UI until a public
 * client id is supplied (see [AndroidOAuthClientIds]).
 */
class AndroidOAuthSignIn(
    private val context: Context,
    private val tokenClient: OAuthTokenClient,
    private val tokenStore: SecureTokenStore,
    redirectScheme: String = "app.pebo",
    redirectHost: String = "oauth2redirect",
    private val launchUrl: (Context, String) -> Unit = ::launchCustomTab,
    private val stateFactory: () -> String = { newPkceVerifier() },
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    val redirectUri: String = "$redirectScheme://$redirectHost"

    suspend fun signIn(
        provider: OAuthProviderConfig,
        clientId: String,
        timeoutMillis: Long = 120_000,
    ): StoredOAuthTokens {
        require(clientId.isNotBlank()) { "OAuth client id is required for ${provider.displayName}" }
        val pkce = newPkcePair()
        val state = stateFactory()
        val config = OAuthClientConfig(
            provider = provider,
            clientId = clientId,
            redirectUri = redirectUri,
        )
        val redirect = OAuthRedirectBus.await(timeoutMillis) {
            launchUrl(context, AuthorizationRequest(config, state, pkce).url())
        }
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

private fun launchCustomTab(context: Context, url: String) {
    val customTabs = CustomTabsIntent.Builder().build()
    customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    customTabs.launchUrl(context, Uri.parse(url))
}
