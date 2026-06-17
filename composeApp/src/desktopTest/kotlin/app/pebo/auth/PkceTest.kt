package app.pebo.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun challengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            pkceChallengeS256(verifier),
        )
    }

    @Test
    fun verifierUsesAllowedLengthAndCharacters() {
        val verifier = newPkceVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun googleAuthorizationUrlUsesDriveFileAndPkce() {
        val request = AuthorizationRequest(
            config = OAuthClientConfig(
                provider = OAuthProviderConfig.GoogleDrive,
                clientId = "client-id",
                redirectUri = "http://127.0.0.1:4567/cb",
            ),
            state = "state value",
            pkce = PkcePair("verifier", "challenge"),
        )

        val url = request.url()
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file"))
        assertTrue(url.contains("code_challenge=challenge"))
        assertTrue(url.contains("state=state%20value"))
        assertTrue(url.contains("access_type=offline"))
    }
}
