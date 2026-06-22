package app.pebo.auth

import android.content.Context
import android.content.pm.PackageManager

/**
 * Android equivalent of `DesktopOAuthClientIds`. Public native clients embed only a client id (no
 * secret); the desktop reads it from a system property / env var, while Android reads it from a
 * `<meta-data>` entry in the manifest (typically injected from a Gradle property at build time).
 * Returns null when unset, so — exactly like desktop — the cloud providers stay opt-in until a
 * client id is supplied.
 */
object AndroidOAuthClientIds {
    fun clientIdFor(context: Context, provider: OAuthProviderId): String? {
        val key = when (provider) {
            OAuthProviderId.GoogleDrive -> "app.pebo.google.clientId"
            OAuthProviderId.OneDrive -> "app.pebo.onedrive.clientId"
        }
        return runCatching {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            info.metaData?.getString(key)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
