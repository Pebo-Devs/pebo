package app.pebo.auth

/**
 * The outcome of an OAuth authorization redirect, shared by the desktop loopback receiver and the
 * Android custom-scheme receiver so both platforms complete the code exchange through the same
 * [OAuthSessionManager].
 */
sealed interface OAuthRedirectResult {
    val state: String?

    data class Code(
        val code: String,
        override val state: String,
    ) : OAuthRedirectResult

    data class Error(
        val error: String,
        val description: String?,
        override val state: String?,
    ) : OAuthRedirectResult
}
