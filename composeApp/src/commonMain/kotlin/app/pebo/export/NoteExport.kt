package app.pebo.export

/** The formats a note can be exported to. [ext] is the file extension (no dot). */
enum class ExportFormat(val ext: String, val label: String) {
    Html("html", "HTML page"),
    Pdf("pdf", "PDF document"),
    Docx("docx", "Word document"),
    Jpg("jpg", "JPG image"),
    Png("png", "PNG image"),
}

/**
 * Renders [markdown] (the note body) as [format] and writes the result to [destPath].
 *
 * [title] seeds document metadata (HTML `<title>`). [attachmentsDir], when non-null, is the directory
 * used to resolve **relative** image paths for the raster formats (usually the notes folder). This is
 * blocking/CPU-heavy (image formats rasterize the whole note) — call it off the UI thread. Returns
 * true on success; false on any failure (already logged by the platform actual).
 */
expect fun exportNote(
    format: ExportFormat,
    title: String,
    markdown: String,
    destPath: String,
    attachmentsDir: String?,
): Boolean

/**
 * Shows the platform "Save As" dialog seeded with [defaultName] and filtered to [extension].
 * Returns the chosen absolute path (with the extension ensured) or null if the user cancelled.
 * Must be called off the UI thread (it blocks until the dialog closes).
 */
expect fun pickSaveFile(title: String, defaultName: String, extension: String): String?
