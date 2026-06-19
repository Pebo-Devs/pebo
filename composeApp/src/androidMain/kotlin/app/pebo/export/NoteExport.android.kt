package app.pebo.export

import app.pebo.platform.CurrentActivityHolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android actuals for note export.
 *
 * HTML and DOCX are produced by the pure `commonMain` generators (text only); JPG/PNG/PDF are
 * rasterized through the android.graphics renderer in `MarkdownImageRenderer.android.kt`. For the
 * foundation, [pickSaveFile] writes into the app's external "exports" directory and returns a real
 * path; the `android-export-saf-share` parity unit replaces this with a Storage Access Framework
 * create-document / share flow.
 */
actual fun exportNote(
    format: ExportFormat,
    title: String,
    markdown: String,
    destPath: String,
    attachmentsDir: String?,
): Boolean = try {
    val dest = File(destPath)
    dest.parentFile?.mkdirs()
    when (format) {
        ExportFormat.Html -> {
            dest.writeText(markdownToHtml(title, markdown), Charsets.UTF_8)
            true
        }
        ExportFormat.Docx -> {
            writeDocx(dest, markdownToWordDocumentXml(markdown))
            true
        }
        ExportFormat.Jpg -> writeBytesOrFalse(dest, renderNoteToImageBytes(markdown, attachmentsDir, jpeg = true))
        ExportFormat.Png -> writeBytesOrFalse(dest, renderNoteToImageBytes(markdown, attachmentsDir, jpeg = false))
        ExportFormat.Pdf -> writeBytesOrFalse(dest, renderNoteToPdfBytes(markdown, attachmentsDir))
    }
} catch (t: Throwable) {
    System.err.println("Export failed (${format.ext}): ${t.message}")
    false
}

private fun writeBytesOrFalse(dest: File, bytes: ByteArray?): Boolean {
    if (bytes == null) return false
    dest.writeBytes(bytes)
    return true
}

/** Writes a `.docx` (Office Open XML) by zipping the package parts (DEFLATED). */
private fun writeDocx(dest: File, documentXml: String) {
    ZipOutputStream(dest.outputStream().buffered()).use { zip ->
        for ((path, content) in docxPackageParts(documentXml)) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}

actual fun pickSaveFile(title: String, defaultName: String, extension: String): String? {
    val ctx = CurrentActivityHolder.get() ?: return null
    val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "exports").apply { mkdirs() }
    var name = defaultName
    if (!name.endsWith(".$extension", ignoreCase = true)) name += ".$extension"
    return File(dir, name).absolutePath
}
