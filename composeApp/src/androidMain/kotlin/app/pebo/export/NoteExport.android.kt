package app.pebo.export

import android.net.Uri
import app.pebo.platform.CurrentActivityHolder
import app.pebo.platform.SafDocumentSaver
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android actuals for note export.
 *
 * HTML and DOCX are produced by the pure `commonMain` generators (text only); JPG/PNG/PDF are
 * rasterized through the android.graphics renderer in `MarkdownImageRenderer.android.kt`.
 *
 * [pickSaveFile] shows the system **Save As** dialog (Storage Access Framework
 * `ACTION_CREATE_DOCUMENT`) and returns the chosen `content://` document URI — the parity match for
 * the desktop native save dialog. [exportNote] then writes the bytes to that URI via the
 * [android.content.ContentResolver]; a plain filesystem path is still honoured as a fallback.
 */
actual fun exportNote(
    format: ExportFormat,
    title: String,
    markdown: String,
    destPath: String,
    attachmentsDir: String?,
): Boolean = try {
    val bytes: ByteArray? = when (format) {
        ExportFormat.Html -> markdownToHtml(title, markdown).toByteArray(Charsets.UTF_8)
        ExportFormat.Docx -> docxBytes(markdownToWordDocumentXml(markdown))
        ExportFormat.Jpg -> renderNoteToImageBytes(markdown, attachmentsDir, jpeg = true)
        ExportFormat.Png -> renderNoteToImageBytes(markdown, attachmentsDir, jpeg = false)
        ExportFormat.Pdf -> renderNoteToPdfBytes(markdown, attachmentsDir)
    }
    if (bytes == null) false else writeTo(destPath, bytes)
} catch (t: Throwable) {
    System.err.println("Export failed (${format.ext}): ${t.message}")
    false
}

/** Writes [bytes] to a SAF `content://` document URI (via ContentResolver) or a real file path. */
private fun writeTo(destPath: String, bytes: ByteArray): Boolean {
    if (destPath.startsWith("content://")) {
        val ctx = CurrentActivityHolder.get() ?: return false
        return ctx.contentResolver.openOutputStream(Uri.parse(destPath))?.use { out ->
            out.write(bytes)
            true
        } ?: false
    }
    val dest = File(destPath)
    dest.parentFile?.mkdirs()
    dest.writeBytes(bytes)
    return true
}

/** Builds a `.docx` (Office Open XML) in memory by zipping the package parts (DEFLATED). */
private fun docxBytes(documentXml: String): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zip ->
        for ((path, content) in docxPackageParts(documentXml)) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return bos.toByteArray()
}

actual fun pickSaveFile(title: String, defaultName: String, extension: String): String? {
    var name = defaultName
    if (!name.endsWith(".$extension", ignoreCase = true)) name += ".$extension"
    // Called off the UI thread (Dispatchers.Default); the SAF dialog runs on the main thread and
    // this background thread blocks for the result, so the UI thread is never blocked (no ANR).
    return runBlocking { SafDocumentSaver.createDocument(mimeFor(extension), name)?.toString() }
}

private fun mimeFor(extension: String): String = when (extension.lowercase()) {
    "html" -> "text/html"
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    else -> "*/*"
}
