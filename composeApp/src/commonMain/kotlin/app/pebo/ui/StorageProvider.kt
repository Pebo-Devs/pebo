package app.pebo.ui

/** Storage backends offered in Settings. Cloud providers ship with built-in public OAuth client ids. */
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
        "Sign in with your Microsoft account to sync your notes to OneDrive.",
        "Needs setup",
        false,
    ),
    GoogleDrive(
        "Google Drive",
        "Sign in with your Google account to sync your notes to Google Drive.",
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
