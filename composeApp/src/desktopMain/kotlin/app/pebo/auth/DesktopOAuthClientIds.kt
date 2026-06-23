package app.pebo.auth

object DesktopOAuthClientIds {
    fun clientIdFor(provider: OAuthProviderId): String? =
        when (provider) {
            OAuthProviderId.GoogleDrive -> propertyOrEnv("pebo.google.clientId", "PEBO_GOOGLE_CLIENT_ID")
            OAuthProviderId.OneDrive -> propertyOrEnv("pebo.onedrive.clientId", "PEBO_ONEDRIVE_CLIENT_ID")
        }?.takeIf { it.isNotBlank() }

    // Only Google's installed-app flow needs a client secret; OneDrive is a public client.
    fun clientSecretFor(provider: OAuthProviderId): String? =
        when (provider) {
            OAuthProviderId.GoogleDrive -> propertyOrEnv("pebo.google.clientSecret", "PEBO_GOOGLE_CLIENT_SECRET")
            OAuthProviderId.OneDrive -> null
        }?.takeIf { it.isNotBlank() }

    /**
     * A provider is usable only when every credential its token exchange requires is present: a client
     * id for both, plus a client secret for Google's desktop client. This keeps Google from advertising
     * itself as "Ready" in builds where the (build-time injected) secret is absent.
     */
    fun isConfigured(provider: OAuthProviderId): Boolean {
        if (clientIdFor(provider) == null) return false
        return when (provider) {
            OAuthProviderId.GoogleDrive -> clientSecretFor(provider) != null
            OAuthProviderId.OneDrive -> true
        }
    }

    private fun propertyOrEnv(property: String, env: String): String? =
        System.getProperty(property)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }
}
