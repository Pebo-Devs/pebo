package app.pebo.update

/**
 * Bridge between Settings → About and the platform's self-update mechanism. Desktop supplies
 * [app.pebo.update.DesktopUpdateService] (GitHub release check + installer download/launch); platforms
 * without an in-app updater pass `null` into the view model, so the UI simply points users at the
 * website instead of offering an in-app install.
 */
interface UpdateService {
    /** The running app version, e.g. `1.0.0`. */
    val currentVersion: String

    /** Check GitHub for a newer release that has an installer for the current platform. */
    suspend fun check(): UpdateState

    /**
     * Download the matching installer for [release] (reporting 0..1 [onProgress], or null progress
     * when the size is unknown), launch it, and quit the app so the OS installer can replace files.
     * Returns a terminal [UpdateState] if it fails before the app exits.
     */
    suspend fun downloadAndInstall(release: ReleaseInfo, onProgress: (Float?) -> Unit): UpdateState

    /** Open [release]'s web page in the system browser (manual-download fallback). */
    fun openReleasePage(release: ReleaseInfo)
}
