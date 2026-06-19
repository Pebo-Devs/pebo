package app.pebo.auth

object DesktopOAuthClientIds {
    fun clientIdFor(provider: OAuthProviderId): String? =
        when (provider) {
            OAuthProviderId.GoogleDrive -> propertyOrEnv("pebo.google.clientId", "PEBO_GOOGLE_CLIENT_ID")
            OAuthProviderId.OneDrive -> propertyOrEnv("pebo.onedrive.clientId", "PEBO_ONEDRIVE_CLIENT_ID")
        }?.takeIf { it.isNotBlank() }

    private fun propertyOrEnv(property: String, env: String): String? =
        System.getProperty(property)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }
}
