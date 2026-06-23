package app.pebo.update

import app.pebo.PeboBuildConfig
import app.pebo.auth.configurePeboHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

/**
 * Desktop self-updater. Uses the shared [UpdateChecker] (Ktor) for the small GitHub Releases JSON
 * call, then streams the matched installer to disk with [java.net.HttpURLConnection] (so the large
 * binary download is independent of the Ktor engine and reports byte-accurate progress), launches the
 * platform installer, and quits so the OS can replace the running files.
 *
 * The collaborators ([platform], [downloadDir], [launchInstaller], [exit], [openUri]) are injectable
 * so the orchestration can be exercised without spawning processes or quitting the JVM in tests.
 */
class DesktopUpdateService(
    override val currentVersion: String = PeboBuildConfig.VERSION,
    private val http: HttpClient = configurePeboHttpClient(),
    private val platform: UpdatePlatform = detectUpdatePlatform(),
    private val downloadDir: File = defaultUpdateDir(),
    private val launchInstaller: (File) -> Unit = ::launchPlatformInstaller,
    private val exit: () -> Unit = { exitProcess(0) },
    private val openUri: (String) -> Unit = ::openInBrowser,
) : UpdateService {
    private val checker = UpdateChecker(http)

    override suspend fun check(): UpdateState =
        when (val result = checker.check(currentVersion, platform)) {
            is UpdateCheck.UpToDate -> UpdateState.UpToDate(currentVersion)
            is UpdateCheck.Available -> UpdateState.Available(result.release, canInstall = result.asset != null)
        }

    override suspend fun downloadAndInstall(release: ReleaseInfo, onProgress: (Float?) -> Unit): UpdateState {
        val asset = release.assetFor(platform)
            ?: return UpdateState.Error("No ${platform.label} installer in release ${release.tag}.")

        val target = downloadDir.resolve(asset.name)
        runCatching { withContext(Dispatchers.IO) { download(asset, target, onProgress) } }
            .onFailure { return UpdateState.Error(it.message ?: "Download failed.") }

        return runCatching {
            launchInstaller(target)
            // The installer is now a detached child process; quit so it can replace our files.
            exit()
            UpdateState.Installing(release)
        }.getOrElse { UpdateState.Error("Couldn't start the installer: ${it.message ?: "unknown error"}.") }
    }

    override fun openReleasePage(release: ReleaseInfo) {
        openUri(release.htmlUrl)
    }

    private fun download(asset: ReleaseAsset, target: File, onProgress: (Float?) -> Unit) {
        target.parentFile?.mkdirs()
        val connection = (URI.create(asset.downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Pebo-Updater")
        }
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 }
                ?: asset.sizeBytes.takeIf { it > 0 }
            onProgress(if (total != null) 0f else null)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total != null) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }
}

/** Maps a JVM `os.name` to the artifact family Pebo ships for it. Pure, so it is unit-tested. */
internal fun platformForOsName(osName: String): UpdatePlatform {
    val os = osName.lowercase()
    return when {
        // macOS first: "darwin" contains "win", so it must be matched before the Windows check.
        os.contains("mac") || os.contains("darwin") || os.contains("osx") -> UpdatePlatform.MacOs
        os.contains("win") -> UpdatePlatform.Windows
        os.contains("nux") || os.contains("nix") || os.contains("aix") -> UpdatePlatform.Linux
        else -> UpdatePlatform.Unsupported
    }
}

internal fun detectUpdatePlatform(): UpdatePlatform =
    platformForOsName(System.getProperty("os.name").orEmpty())

/**
 * The OS command that hands [file] to the platform installer. MSI uses `msiexec /i` (honours Pebo's
 * upgrade code for an in-place upgrade); everything else is opened with the desktop's default handler.
 */
internal fun installerCommand(osName: String, file: File): List<String> {
    val path = file.absolutePath
    return when (platformForOsName(osName)) {
        UpdatePlatform.Windows ->
            if (file.extension.equals("msi", ignoreCase = true)) listOf("msiexec", "/i", path)
            else listOf(path)
        UpdatePlatform.MacOs -> listOf("open", path)
        else -> listOf("xdg-open", path)
    }
}

private fun launchPlatformInstaller(file: File) {
    ProcessBuilder(installerCommand(System.getProperty("os.name").orEmpty(), file))
        .inheritIO()
        .start()
}

private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url))
        }
    }
}

/** Installers download into `~/Pebo/updates`, the app's existing home, so they are easy to find. */
private fun defaultUpdateDir(): File =
    File(System.getProperty("user.home"), "Pebo/updates")
