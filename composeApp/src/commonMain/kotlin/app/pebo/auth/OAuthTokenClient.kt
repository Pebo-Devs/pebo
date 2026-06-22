package app.pebo.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OAuthTokenClient(
    private val http: HttpClient,
) {
    suspend fun exchangeCode(
        config: OAuthClientConfig,
        code: String,
        verifier: String,
    ): OAuthTokens {
        require(config.configured) { "OAuth client id is required for ${config.provider.displayName}" }
        val response = http.submitForm(
            url = config.provider.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", config.clientId)
                config.clientSecret?.takeIf { it.isNotBlank() }?.let { append("client_secret", it) }
                append("code", code)
                append("redirect_uri", config.redirectUri)
                append("code_verifier", verifier)
            },
        )
        return parse(config, response).toDomain()
    }

    suspend fun refresh(
        config: OAuthClientConfig,
        refreshToken: String,
    ): OAuthTokens {
        require(config.configured) { "OAuth client id is required for ${config.provider.displayName}" }
        val response = http.submitForm(
            url = config.provider.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", config.clientId)
                config.clientSecret?.takeIf { it.isNotBlank() }?.let { append("client_secret", it) }
                append("refresh_token", refreshToken)
            },
        )
        return parse(config, response).toDomain(existingRefreshToken = refreshToken)
    }

    // Surfaces the provider's actual error body instead of letting a non-token response fail later
    // as a cryptic "access_token is required" deserialization error.
    private suspend fun parse(config: OAuthClientConfig, response: HttpResponse): TokenResponse {
        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() }
            error(
                "${config.provider.displayName} token request failed (${response.status.value})" +
                    (detail?.let { ": $it" } ?: "."),
            )
        }
        return response.body()
    }
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
    val tokenType: String?,
    val scope: String?,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
) {
    fun toDomain(existingRefreshToken: String? = null): OAuthTokens =
        OAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken ?: existingRefreshToken,
            expiresInSeconds = expiresInSeconds,
            tokenType = tokenType,
            scope = scope,
        )
}
