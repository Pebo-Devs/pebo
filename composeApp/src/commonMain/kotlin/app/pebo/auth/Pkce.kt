package app.pebo.auth

import okio.ByteString.Companion.encodeUtf8

/**
 * Platform crypto used by OAuth 2.0 Authorization Code + PKCE. Public native clients embed only
 * client ids; no client secrets are used.
 */
expect fun newPkceVerifier(): String

/**
 * RFC 7636 `S256` code challenge: base64url(SHA-256(verifier)) without padding. Implemented in shared
 * code via Okio so every platform (desktop, iOS, …) produces byte-identical challenges.
 */
fun pkceChallengeS256(verifier: String): String =
    verifier.encodeUtf8().sha256().base64Url().trimEnd('=')

data class PkcePair(
    val verifier: String,
    val challenge: String,
)

fun newPkcePair(): PkcePair {
    val verifier = newPkceVerifier()
    return PkcePair(verifier = verifier, challenge = pkceChallengeS256(verifier))
}
