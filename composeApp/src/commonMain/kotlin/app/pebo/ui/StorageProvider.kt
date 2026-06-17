package app.pebo.ui

/** Storage backends offered in Settings. Only [Local] is wired up in v0.1; the rest are Phase 2. */
enum class StorageProvider(
    val displayName: String,
    val description: String,
    val available: Boolean,
) {
    Local("On this device", "Notes saved as .md files in a local folder", true),
    OneDrive("OneDrive (Microsoft)", "Sync through your own OneDrive — coming soon", false),
    GoogleDrive("Google Drive", "Sync through your own Google Drive — coming soon", false),
    ICloud("iCloud Drive", "Apple devices only — coming soon", false),
}
