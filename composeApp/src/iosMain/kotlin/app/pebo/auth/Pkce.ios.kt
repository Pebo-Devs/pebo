package app.pebo.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

private const val VERIFIER_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/**
 * Cryptographically random RFC 7636 PKCE verifier (64 chars from the unreserved alphabet), using the
 * Security framework's CSPRNG. Bytes >= the largest multiple of the alphabet size are rejected to
 * avoid modulo bias. The S256 challenge is computed by the shared [pkceChallengeS256].
 */
@OptIn(ExperimentalForeignApi::class)
actual fun newPkceVerifier(): String {
    val alphabetSize = VERIFIER_ALPHABET.length
    val acceptLimit = 256 - (256 % alphabetSize)
    val out = StringBuilder(64)
    memScoped {
        val byte = allocArray<UByteVar>(1)
        while (out.length < 64) {
            val status = SecRandomCopyBytes(kSecRandomDefault, 1.convert(), byte)
            check(status == 0) { "SecRandomCopyBytes failed with status $status" }
            val value = byte[0].toInt()
            if (value < acceptLimit) {
                out.append(VERIFIER_ALPHABET[value % alphabetSize])
            }
        }
    }
    return out.toString()
}
