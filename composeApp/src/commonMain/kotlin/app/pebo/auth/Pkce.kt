package app.pebo.auth

/**
 * Platform crypto used by OAuth 2.0 Authorization Code + PKCE. Public native clients embed only
 * client ids; no client secrets are used.
 */
expect fun newPkceVerifier(): String

expect fun pkceChallengeS256(verifier: String): String

data class PkcePair(
    val verifier: String,
    val challenge: String,
)

fun newPkcePair(): PkcePair {
    val verifier = newPkceVerifier()
    return PkcePair(verifier = verifier, challenge = pkceChallengeS256(verifier))
}
