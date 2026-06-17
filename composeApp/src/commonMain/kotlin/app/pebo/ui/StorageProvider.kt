package app.pebo.ui

/** Storage backends offered in Settings. Cloud providers need public OAuth client ids before use. */
enum class StorageProvider(
    val displayName: String,
    val description: String,
    val statusLabel: String,
    val available: Boolean,
) {
    Local(
        "On this device",
        "Notes saved as .md files in a local folder",
        "Active",
        true,
    ),
    OneDrive(
        "OneDrive (Microsoft)",
        "OAuth PKCE + sync engine foundation is in place; needs an Entra public client id.",
        "Needs setup",
        false,
    ),
    GoogleDrive(
        "Google Drive",
        "OAuth PKCE + sync engine foundation is in place; needs a Google desktop client id.",
        "Needs setup",
        false,
    ),
    ICloud(
        "iCloud Drive",
        "Apple-platform provider; requires iOS/macOS entitlements and cannot be enabled on Windows.",
        "Apple only",
        false,
    ),
}
