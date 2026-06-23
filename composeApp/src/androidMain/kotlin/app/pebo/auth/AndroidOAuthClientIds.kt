package app.pebo.auth

import android.content.Context
import android.content.pm.PackageManager

/**
 * Android equivalent of `DesktopOAuthClientIds`. Public native clients embed only a client id (no
 * secret). A `<meta-data>` entry in the manifest can override it (e.g. injected from a Gradle property
 * at build time); otherwise it falls back to the public client ids baked into `PeboBuildConfig`, so the
 * cloud providers work out of the box just like on desktop.
 */
object AndroidOAuthClientIds {
    fun clientIdFor(context: Context, provider: OAuthProviderId): String? {
        val key = when (provider) {
            OAuthProviderId.GoogleDrive -> "app.pebo.google.clientId"
            OAuthProviderId.OneDrive -> "app.pebo.onedrive.clientId"
        }
        val fromManifest = runCatching {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            info.metaData?.getString(key)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        // Fall back to the public client ids baked into the build so the cloud providers work out of
        // the box; a manifest <meta-data> override still wins when present.
        return fromManifest ?: builtInClientId(provider)
    }

    private fun builtInClientId(provider: OAuthProviderId): String? =
        when (provider) {
            OAuthProviderId.GoogleDrive -> app.pebo.PeboBuildConfig.GOOGLE_CLIENT_ID
            OAuthProviderId.OneDrive -> app.pebo.PeboBuildConfig.ONEDRIVE_CLIENT_ID
        }.takeIf { it.isNotBlank() }
}
