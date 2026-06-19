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
        "Any folder you choose — notes saved as portable .md files you fully own",
        "Active",
        true,
    ),
    OneDrive(
        "OneDrive (Microsoft)",
        "OAuth PKCE + secure token storage are ready; set PEBO_ONEDRIVE_CLIENT_ID.",
        "Needs setup",
        false,
    ),
    GoogleDrive(
        "Google Drive",
        "OAuth PKCE + secure token storage are ready; set PEBO_GOOGLE_CLIENT_ID.",
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
