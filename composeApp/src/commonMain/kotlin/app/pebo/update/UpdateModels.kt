package app.pebo.update

/**
 * The distributable artifact families Pebo publishes for each GitHub release. The [assetSuffixes]
 * are matched (in order) against release asset file names so the updater can pick the installer that
 * fits the running platform — preferring the first suffix when several are present (e.g. `.msi` over
 * `.exe` on Windows, since the MSI carries the upgrade code for in-place upgrades).
 */
enum class UpdatePlatform(val assetSuffixes: List<String>, val label: String) {
    Windows(listOf(".msi", ".exe"), "Windows"),
    MacOs(listOf(".dmg"), "macOS"),
    Linux(listOf(".deb"), "Linux"),
    Android(listOf(".apk"), "Android"),

    /** A platform with no self-update artifact (e.g. iOS, or an unknown desktop OS). */
    Unsupported(emptyList(), "this platform"),
}

/** A single downloadable file attached to a release. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/** The metadata Pebo needs from a published GitHub release. */
data class ReleaseInfo(
    /** Version with any leading `v` stripped, e.g. `1.1.0`. */
    val version: String,
    /** The raw git tag, e.g. `v1.1.0`. */
    val tag: String,
    val name: String,
    /** Release notes (markdown body), possibly empty. */
    val notes: String,
    /** The release's web page, used as a manual-download fallback. */
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
) {
    /** The best-matching installer asset for [platform], or `null` when this release has none. */
    fun assetFor(platform: UpdatePlatform): ReleaseAsset? {
        for (suffix in platform.assetSuffixes) {
            assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }?.let { return it }
        }
        return null
    }
}

/** Outcome of comparing the latest published release against the running version. */
sealed interface UpdateCheck {
    /** The running version is the latest (or newer). */
    data object UpToDate : UpdateCheck

    /** A newer [release] exists; [asset] is the installer for the current platform (may be `null`). */
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset?) : UpdateCheck
}

/** UI-facing snapshot of the in-app updater, surfaced in Settings → About. */
sealed interface UpdateState {
    /** Nothing checked yet this session. */
    data object Idle : UpdateState

    /** A check is in flight. */
    data object Checking : UpdateState

    /** The running [version] is the latest. */
    data class UpToDate(val version: String) : UpdateState

    /** A newer [release] is available; [canInstall] is false when no installer fits this platform. */
    data class Available(val release: ReleaseInfo, val canInstall: Boolean) : UpdateState

    /** The installer is downloading; [progress] is 0..1 (or null when the size is unknown). */
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState

    /** The installer launched and the app is about to quit so the OS can take over. */
    data class Installing(val release: ReleaseInfo) : UpdateState

    /** The check or download failed; [message] is user-facing. */
    data class Error(val message: String) : UpdateState
}
