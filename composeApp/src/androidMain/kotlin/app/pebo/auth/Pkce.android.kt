package app.pebo.auth

import java.security.SecureRandom

private val verifierAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private val random = SecureRandom()

actual fun newPkceVerifier(): String = buildString {
    repeat(64) {
        append(verifierAlphabet[random.nextInt(verifierAlphabet.length)])
    }
}
