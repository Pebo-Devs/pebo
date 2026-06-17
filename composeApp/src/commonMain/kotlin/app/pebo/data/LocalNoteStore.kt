package app.pebo.data

import app.pebo.core.Note
import app.pebo.core.NoteFile
import app.pebo.core.Slug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * Stores notes as individual `.md` files under [baseDir]`/notes`, with trashed notes moved to
 * [baseDir]`/.trash`. Writes are atomic (temp file + rename). Identity is the frontmatter `id`;
 * filenames are title slugs and are never auto-renamed once created.
 */
class LocalNoteStore(
    private val fs: FileSystem,
    private val baseDir: Path,
) : NoteStore {

    private val notesDir: Path = baseDir / "notes"
    private val trashDir: Path = baseDir / ".trash"

    private val activePaths = HashMap<String, Path>()
    private val trashPaths = HashMap<String, Path>()

    private fun ensureDirs() {
        fs.createDirectories(notesDir)
        fs.createDirectories(trashDir)
    }

    override suspend fun load(): StoreSnapshot = withContext(Dispatchers.Default) {
        ensureDirs()
        activePaths.clear()
        trashPaths.clear()
        val active = readDir(notesDir, trashed = false, into = activePaths)
        val trashed = readDir(trashDir, trashed = true, into = trashPaths)
        StoreSnapshot(
            active = active.sortedWith(noteOrder),
            trashed = trashed.sortedByDescending { it.modified },
        )
    }

    private fun readDir(dir: Path, trashed: Boolean, into: HashMap<String, Path>): List<Note> {
        if (!fs.exists(dir)) return emptyList()
        val notes = ArrayList<Note>()
        for (path in fs.list(dir)) {
            if (!path.name.endsWith(".md")) continue
            if (fs.metadataOrNull(path)?.isRegularFile != true) continue
            val text = fs.read(path) { readUtf8() }
            val note = NoteFile.parse(text, fallbackId = path.name.removeSuffix(".md"), trashed = trashed)
            into[note.id] = path
            notes.add(note)
        }
        return notes
    }

    override suspend fun save(note: Note): Unit = withContext(Dispatchers.Default) {
        ensureDirs()
        val path = activePaths[note.id]
            ?: uniquePath(notesDir, Slug.of(note.title)).also { activePaths[note.id] = it }
        writeAtomic(path, NoteFile.serialize(note))
    }

    override suspend fun moveToTrash(id: String): Unit = withContext(Dispatchers.Default) {
        val src = activePaths[id] ?: return@withContext
        val text = fs.read(src) { readUtf8() }
        val dest = uniquePath(trashDir, src.name.removeSuffix(".md"))
        writeAtomic(dest, text)
        fs.delete(src)
        activePaths.remove(id)
        trashPaths[id] = dest
    }

    override suspend fun restore(id: String): Unit = withContext(Dispatchers.Default) {
        val src = trashPaths[id] ?: return@withContext
        val text = fs.read(src) { readUtf8() }
        val dest = uniquePath(notesDir, src.name.removeSuffix(".md"))
        writeAtomic(dest, text)
        fs.delete(src)
        trashPaths.remove(id)
        activePaths[id] = dest
    }

    override suspend fun purge(id: String): Unit = withContext(Dispatchers.Default) {
        val p = trashPaths[id] ?: return@withContext
        if (fs.exists(p)) fs.delete(p)
        trashPaths.remove(id)
    }

    override suspend fun emptyTrash(): Unit = withContext(Dispatchers.Default) {
        for ((_, p) in trashPaths) if (fs.exists(p)) fs.delete(p)
        trashPaths.clear()
    }

    private fun uniquePath(dir: Path, baseName: String): Path {
        val safe = baseName.ifBlank { "untitled" }
        var candidate = dir / "$safe.md"
        var i = 2
        while (fs.exists(candidate)) {
            candidate = dir / "$safe-$i.md"
            i++
        }
        return candidate
    }

    private fun writeAtomic(target: Path, content: String) {
        val tmp = target.parent!! / (target.name + ".tmp")
        fs.write(tmp) { writeUtf8(content) }
        fs.atomicMove(tmp, target)
    }

    private val noteOrder: Comparator<Note> =
        compareByDescending<Note> { it.pinned }.thenByDescending { it.modified }
}
