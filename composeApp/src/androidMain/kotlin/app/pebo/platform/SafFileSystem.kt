package app.pebo.platform

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * An [okio.FileSystem] backed by the Android **Storage Access Framework**, so the shared
 * [app.pebo.data.LocalNoteStore] can read and write inside an arbitrary user-chosen folder (any
 * `content://` *tree* obtained via `ACTION_OPEN_DOCUMENT_TREE`) exactly as it does against a real
 * filesystem on desktop. This is what makes "Any folder you choose" real parity on Android 11+ where
 * scoped storage forbids direct `java.io.File` access to folders outside the app sandbox.
 *
 * okio [Path]s are treated as positions relative to the picked tree's root: `"/"` is the tree root,
 * `"/notes/foo.md"` is the document `foo.md` inside the child directory `notes`. Document ids are
 * provider-specific and cannot be derived by string concatenation, so every lookup resolves a path
 * by walking the tree from its root and matching child **display names** (which okio already keeps
 * unique within a directory). New documents are created with a generic MIME type and, defensively,
 * renamed back to the exact requested name if a provider reconciled the extension — guaranteeing the
 * on-storage name always round-trips for subsequent name-based lookups.
 *
 * Only the operations [LocalNoteStore]/preferences actually use are implemented; random-access
 * [okio.FileHandle], appending and symlinks are unsupported.
 */
class SafFileSystem(
    context: Context,
    private val treeUri: Uri,
) : FileSystem() {

    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    private data class Doc(
        val documentId: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
    ) {
        val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    }

    private val rootDoc = Doc(rootDocumentId, Document.MIME_TYPE_DIR, 0L, 0L)

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun childrenUri(parentDocumentId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    private fun Cursor.readDoc(): Doc {
        val id = getString(getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))
        val mime = getString(getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE))
            ?: Document.MIME_TYPE_DIR
        val sizeIdx = getColumnIndexOrThrow(Document.COLUMN_SIZE)
        val modIdx = getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
        val size = if (isNull(sizeIdx)) 0L else getLong(sizeIdx)
        val mod = if (isNull(modIdx)) 0L else getLong(modIdx)
        return Doc(id, mime, size, mod)
    }

    private fun Cursor.displayName(): String =
        getString(getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) ?: ""

    private fun findChild(parentDocumentId: String, name: String): Doc? {
        val cursor = resolver.query(childrenUri(parentDocumentId), projection, null, null, null)
            ?: return null
        cursor.use {
            while (it.moveToNext()) {
                if (it.displayName() == name) return it.readDoc()
            }
        }
        return null
    }

    private fun resolve(path: Path): Doc? {
        var current = rootDoc
        for (segment in path.normalized().segments) {
            if (!current.isDirectory) return null
            current = findChild(current.documentId, segment) ?: return null
        }
        return current
    }

    private fun readDocAndName(uri: Uri): Pair<Doc, String>? =
        resolver.query(uri, projection, null, null, null)?.use {
            if (it.moveToFirst()) it.readDoc() to it.displayName() else null
        }

    /**
     * Creates a child document and guarantees its on-storage display name is exactly [name].
     * Providers may reconcile names/extensions on `createDocument`; we rename back when needed and,
     * if an exact name still can't be achieved, delete the stray document and fail loudly rather than
     * leave one that later name-based [resolve] could never find.
     */
    private fun createExact(parentDocumentId: String, mimeType: String, name: String): Doc {
        val created = DocumentsContract.createDocument(
            resolver,
            documentUri(parentDocumentId),
            mimeType,
            name,
        ) ?: throw IOException("Could not create '$name' in the chosen folder")

        // Some providers reconcile the extension on create; rename back to force the exact name.
        val createdName = readDocAndName(created)?.second
        val finalUri = if (createdName != null && createdName != name) {
            runCatching { DocumentsContract.renameDocument(resolver, created, name) }.getOrNull() ?: created
        } else {
            created
        }

        val info = readDocAndName(finalUri)
        if (info == null || info.second != name) {
            runCatching { DocumentsContract.deleteDocument(resolver, finalUri) }
            throw IOException("Storage provider could not create '$name' with an exact name")
        }
        return info.first
    }

    private fun openOutput(uri: Uri): OutputStream =
        (runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w"))
            ?: throw IOException("Could not open '$uri' for writing")

    override fun metadataOrNull(path: Path): FileMetadata? {
        val doc = resolve(path) ?: return null
        return FileMetadata(
            isRegularFile = !doc.isDirectory,
            isDirectory = doc.isDirectory,
            size = doc.size,
            lastModifiedAtMillis = doc.lastModified.takeIf { it > 0 },
        )
    }

    override fun list(dir: Path): List<Path> =
        listOrNull(dir) ?: throw FileNotFoundException("No such directory: $dir")

    override fun listOrNull(dir: Path): List<Path>? {
        val doc = resolve(dir) ?: return null
        if (!doc.isDirectory) return null
        val cursor = resolver.query(childrenUri(doc.documentId), projection, null, null, null)
            ?: return emptyList()
        val out = ArrayList<Path>()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.displayName()
                if (name.isNotEmpty()) out.add(dir / name)
            }
        }
        return out
    }

    override fun source(file: Path): Source {
        val doc = resolve(file) ?: throw FileNotFoundException("No such file: $file")
        val stream = resolver.openInputStream(documentUri(doc.documentId))
            ?: throw IOException("Could not open '$file' for reading")
        return stream.source()
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        val parentPath = file.parent ?: throw IOException("Cannot write to a filesystem root: $file")
        val parent = resolve(parentPath) ?: throw FileNotFoundException("No such directory: $parentPath")
        if (!parent.isDirectory) throw IOException("Not a directory: $parentPath")

        val existing = findChild(parent.documentId, file.name)
        val uri = when {
            existing != null && mustCreate -> throw IOException("File already exists: $file")
            existing != null -> documentUri(existing.documentId)
            else -> documentUri(createExact(parent.documentId, MIME_OCTET_STREAM, file.name).documentId)
        }
        return openOutput(uri).sink()
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        val parentPath = dir.parent ?: return // the tree root always exists
        val parent = resolve(parentPath) ?: throw FileNotFoundException("No such directory: $parentPath")
        if (!parent.isDirectory) throw IOException("Not a directory: $parentPath")

        val existing = findChild(parent.documentId, dir.name)
        if (existing != null) {
            if (mustCreate) throw IOException("Directory already exists: $dir")
            if (!existing.isDirectory) throw IOException("Not a directory: $dir")
            return
        }
        createExact(parent.documentId, Document.MIME_TYPE_DIR, dir.name)
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val doc = resolve(path)
        if (doc == null) {
            if (mustExist) throw FileNotFoundException("No such file: $path")
            return
        }
        val ok = DocumentsContract.deleteDocument(resolver, documentUri(doc.documentId))
        if (!ok) throw IOException("Could not delete: $path")
    }

    override fun atomicMove(source: Path, target: Path) {
        val src = resolve(source) ?: throw FileNotFoundException("No such file: $source")

        if (source.parent == target.parent) {
            resolve(target)?.let { existing ->
                DocumentsContract.deleteDocument(resolver, documentUri(existing.documentId))
            }
            // Same-parent rename is the fast, in-place path; src stays valid after deleting target.
            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, documentUri(src.documentId), target.name)
            }.getOrNull()
            if (renamed != null) return
            // Fall through to copy+delete for providers that don't support rename.
        }

        // Cross-directory move, or rename-unsupported: copy then delete.
        this.source(source).buffer().use { input ->
            this.sink(target, mustCreate = false).buffer().use { output -> output.writeAll(input) }
        }
        delete(source, mustExist = true)
    }

    override fun canonicalize(path: Path): Path {
        val normalized = path.normalized()
        return if (normalized.isAbsolute) normalized else ("/".toPath() / normalized).normalized()
    }

    override fun openReadOnly(file: Path) =
        throw UnsupportedOperationException("Random access is not supported on SAF storage")

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean) =
        throw UnsupportedOperationException("Random access is not supported on SAF storage")

    override fun appendingSink(file: Path, mustExist: Boolean): Sink =
        throw UnsupportedOperationException("Appending is not supported on SAF storage")

    override fun createSymlink(source: Path, target: Path) =
        throw UnsupportedOperationException("Symlinks are not supported on SAF storage")

    private companion object {
        const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}
