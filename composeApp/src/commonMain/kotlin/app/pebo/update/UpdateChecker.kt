package app.pebo.update

import app.pebo.PeboBuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Queries the GitHub Releases API for the latest published release and decides whether it is newer
 * than the running version. Pure transport + comparison, so it is fully covered by commonTest with a
 * Ktor mock engine (no real network, no platform code).
 */
class UpdateChecker(
    private val http: HttpClient,
    private val owner: String = PeboBuildConfig.GITHUB_OWNER,
    private val repo: String = PeboBuildConfig.GITHUB_REPO,
) {
    suspend fun check(currentVersion: String, platform: UpdatePlatform): UpdateCheck {
        val response = http.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub rejects API requests without a User-Agent.
            header("User-Agent", "Pebo-Updater")
        }
        if (!response.status.isSuccess()) {
            error("GitHub release check failed (${response.status.value})${errorDetail(response)}")
        }
        val release = response.body<ReleaseDto>().toDomain()
        return if (isNewerVersion(currentVersion, release.version)) {
            UpdateCheck.Available(release, release.assetFor(platform))
        } else {
            UpdateCheck.UpToDate
        }
    }

    private suspend fun errorDetail(response: HttpResponse): String =
        runCatching { response.bodyAsText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { ": $it" }
            ?: "."
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
) {
    fun toDomain(): ReleaseInfo {
        val version = tagName.trim().removePrefix("v").removePrefix("V")
        return ReleaseInfo(
            version = version,
            tag = tagName,
            name = name?.takeIf { it.isNotBlank() } ?: tagName,
            notes = body.orEmpty(),
            htmlUrl = htmlUrl,
            assets = assets.map { it.toDomain() },
        )
    }
}

@Serializable
private data class AssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
) {
    fun toDomain() = ReleaseAsset(name = name, downloadUrl = downloadUrl, sizeBytes = size)
}
