package app.pebo.export

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory

/**
 * iOS note export.
 *
 * HTML is produced by the shared [markdownToHtml] generator and written with Okio. DOCX (OOXML zip)
 * and the raster/PDF formats still need an iOS implementation — DOCX needs a zip writer and the raster
 * formats need a Skia/Core Graphics renderer — so they currently report failure rather than writing a
 * corrupt file. See `MarkdownImageRenderer.desktop.kt` for the renderer contract to port.
 */
actual fun exportNote(
    format: ExportFormat,
    title: String,
    markdown: String,
    destPath: String,
    attachmentsDir: String?,
): Boolean = try {
    when (format) {
        ExportFormat.Html -> {
            FileSystem.SYSTEM.write(destPath.toPath()) { writeUtf8(markdownToHtml(title, markdown)) }
            true
        }
        ExportFormat.Docx,
        ExportFormat.Jpg,
        ExportFormat.Png,
        ExportFormat.Pdf,
        -> false
    }
} catch (t: Throwable) {
    println("Export failed (${format.ext}): ${t.message}")
    false
}

/**
 * iOS has no modal "Save As" panel; exports are written to a temp file and then surfaced through a
 * share sheet by the caller. Returns a temporary path seeded with [defaultName].[extension].
 */
actual fun pickSaveFile(title: String, defaultName: String, extension: String): String? {
    val name = if (defaultName.endsWith(".$extension", ignoreCase = true)) defaultName else "$defaultName.$extension"
    return NSTemporaryDirectory() + name
}
