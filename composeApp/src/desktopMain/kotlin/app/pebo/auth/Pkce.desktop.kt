package app.pebo.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val verifierAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private val random = SecureRandom()

actual fun newPkceVerifier(): String = buildString {
    repeat(64) {
        append(verifierAlphabet[random.nextInt(verifierAlphabet.length)])
    }
}

actual fun pkceChallengeS256(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
