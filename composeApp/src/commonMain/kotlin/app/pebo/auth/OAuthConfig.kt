package app.pebo.auth

enum class OAuthProviderId {
    GoogleDrive,
    OneDrive,
}

data class OAuthProviderConfig(
    val id: OAuthProviderId,
    val displayName: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
) {
    companion object {
        val GoogleDrive = OAuthProviderConfig(
            id = OAuthProviderId.GoogleDrive,
            displayName = "Google Drive",
            authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            scopes = listOf("https://www.googleapis.com/auth/drive.file"),
        )

        val OneDrive = OAuthProviderConfig(
            id = OAuthProviderId.OneDrive,
            displayName = "OneDrive",
            authorizationEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
            tokenEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            scopes = listOf("Files.ReadWrite.AppFolder", "offline_access"),
        )
    }
}

data class OAuthClientConfig(
    val provider: OAuthProviderConfig,
    val clientId: String,
    val redirectUri: String,
    // Google's installed-app token endpoint requires a client secret even with PKCE; for public
    // clients like OneDrive this stays null and no secret is ever sent.
    val clientSecret: String? = null,
) {
    val configured: Boolean get() = clientId.isNotBlank()
}

data class AuthorizationRequest(
    val config: OAuthClientConfig,
    val state: String,
    val pkce: PkcePair,
) {
    fun url(): String = buildString {
        append(config.provider.authorizationEndpoint)
        append("?response_type=code")
        append("&client_id=").append(urlEncode(config.clientId))
        append("&redirect_uri=").append(urlEncode(config.redirectUri))
        append("&scope=").append(urlEncode(config.provider.scopes.joinToString(" ")))
        append("&state=").append(urlEncode(state))
        append("&code_challenge=").append(urlEncode(pkce.challenge))
        append("&code_challenge_method=S256")
        if (config.provider.id == OAuthProviderId.GoogleDrive) {
            append("&access_type=offline")
            append("&prompt=consent")
        }
    }
}

internal fun urlEncode(value: String): String = buildString {
    for (ch in value.encodeToByteArray()) {
        val c = ch.toInt() and 0xff
        val allowed =
            c in 'A'.code..'Z'.code ||
                c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        if (allowed) {
            append(c.toChar())
        } else {
            append('%')
            append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
