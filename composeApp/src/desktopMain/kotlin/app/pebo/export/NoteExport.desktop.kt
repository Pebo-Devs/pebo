package app.pebo.export

import org.jetbrains.skia.EncodedImageFormat
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Desktop (JVM) actuals for note export.
 *
 * HTML and DOCX are produced by the pure `commonMain` generators (just text); JPG/PNG/PDF are
 * rasterized through the Skiko renderer in [MarkdownImageRenderer.desktop.kt]. The save dialog uses
 * the AWT [FileDialog] (a *native* peer), because Swing's `JFileChooser` renders blank inside the
 * Skiko/Compose-hosted JVM — the same reason the folder picker uses a native Win32 dialog.
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
        ExportFormat.Jpg -> writeBytesOrFalse(dest, renderNoteToImageBytes(markdown, attachmentsDir, EncodedImageFormat.JPEG))
        ExportFormat.Png -> writeBytesOrFalse(dest, renderNoteToImageBytes(markdown, attachmentsDir, EncodedImageFormat.PNG))
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
    var result: String? = null
    val task = Runnable {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) {
            var path = File(dir, name).absolutePath
            if (!path.endsWith(".$extension", ignoreCase = true)) path += ".$extension"
            result = path
        }
    }
    if (EventQueue.isDispatchThread()) task.run() else EventQueue.invokeAndWait(task)
    return result
}
