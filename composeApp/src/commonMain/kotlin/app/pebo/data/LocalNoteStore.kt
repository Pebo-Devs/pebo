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
 *
 * To make the app friendly to point at an existing pile of Markdown, [load] scans **recursively**:
 * every `.md` file at any nesting depth under [baseDir] is discovered (not just the top level or
 * `notes/`), so an existing vault with deeply nested folders shows up in full. Discovered files are
 * shown as notes and edited in place — Pebo never moves them into `notes/` behind the user's back.
 * On an `id` collision a file under `notes/` wins, so Pebo's own notes always take precedence.
 *
 * **Scale.** A workspace can hold *millions* of `.md` files. To stay responsive, [load] never holds
 * every note in memory: it walks the tree reading only cheap metadata, keeps a bounded window of the
 * [loadLimit] most-recently-modified files (via a min-heap, so memory is O([loadLimit]) regardless of
 * folder size), and only reads/parses the content of that window. A [maxScan] safety cap bounds the
 * worst-case launch time on pathologically huge trees. The returned [StoreSnapshot] reports the total
 * number of files discovered and whether the lists were truncated, so the UI can be honest about it.
 * Hidden/system folders (`.trash`, `.archive`, any dot-folder, `node_modules`) and symlinks are
 * skipped to avoid scanning caches and following loops.
 */
class LocalNoteStore(
    private val fs: FileSystem,
    private val baseDir: Path,
    private val loadLimit: Int = DEFAULT_LOAD_LIMIT,
    private val maxScan: Int = DEFAULT_MAX_SCAN,
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

        // Active notes: a single recursive walk from baseDir covers Pebo's own notes/, any Markdown
        // the user kept directly in the folder, and every nested subfolder at any depth. .trash and
        // hidden folders are pruned (trash is read separately below).
        val activeScan = collectRecent(baseDir, loadLimit)
        val active = parse(activeScan.window, trashed = false, into = activePaths)

        // Trash lives under .trash; bound it the same way so a huge trash can't blow up memory.
        val trashScan = collectRecent(trashDir, loadLimit)
        val trashed = parse(trashScan.window, trashed = true, into = trashPaths)

        StoreSnapshot(
            active = active.sortedWith(noteOrder),
            trashed = trashed.sortedByDescending { it.modified },
            totalActive = activeScan.totalMarkdown,
            totalTrashed = trashScan.totalMarkdown,
            truncated = activeScan.truncated || trashScan.truncated,
        )
    }

    /** Result of a bounded recursive scan: the retained window plus how much was actually out there. */
    private class Scan(val window: List<Path>, val totalMarkdown: Int, val truncated: Boolean)

    /**
     * Walks [root] recursively (iterative DFS) collecting `.md` files, retaining only the
     * [capacity] most-recently-modified ones in a min-heap so memory stays bounded no matter how many
     * files exist. Prunes hidden/system folders and skips symlinks. Stops after [maxScan] entries as a
     * safety valve against pathological trees, marking the result truncated.
     */
    private fun collectRecent(root: Path, capacity: Int): Scan {
        if (capacity <= 0 || !isDirectory(root)) return Scan(emptyList(), 0, false)
        val recent = RecentWindow(capacity)
        val stack = ArrayDeque<Path>()
        stack.addLast(root)
        var scanned = 0
        var hitScanCap = false
        outer@ while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val entries = fs.listOrNull(dir) ?: continue
            for (path in entries) {
                if (scanned >= maxScan) {
                    hitScanCap = true
                    break@outer
                }
                scanned++
                val md = runCatching { fs.metadataOrNull(path) }.getOrNull() ?: continue
                if (md.symlinkTarget != null) continue // never follow symlinks (loop / duplicate guard)
                if (md.isDirectory) {
                    if (!isExcludedDir(path.name)) stack.addLast(path)
                } else if (md.isRegularFile && path.name.endsWith(".md")) {
                    recent.offer(md.lastModifiedAtMillis ?: md.createdAtMillis ?: 0L, path)
                }
            }
        }
        return Scan(recent.paths(), recent.total, hitScanCap || recent.dropped)
    }

    /** Reads + parses each path in the window into a [Note], deduping by id (notes/ wins). */
    private fun parse(window: List<Path>, trashed: Boolean, into: HashMap<String, Path>): List<Note> {
        // Parse files under notes/ first so they win an id collision over an adopted top-level copy.
        val ordered = window.sortedBy { if (isUnder(it, notesDir)) 0 else 1 }
        val notes = ArrayList<Note>(ordered.size)
        for (path in ordered) {
            val text = runCatching { fs.read(path) { readUtf8() } }.getOrNull() ?: continue
            val note = NoteFile.parse(text, fallbackId = path.name.removeSuffix(".md"), trashed = trashed)
            if (into.containsKey(note.id)) continue // first occurrence wins
            into[note.id] = path
            notes.add(note)
        }
        return notes
    }

    private fun isDirectory(path: Path): Boolean =
        runCatching { fs.metadataOrNull(path)?.isDirectory }.getOrNull() == true

    private fun isExcludedDir(name: String): Boolean =
        name.startsWith(".") || name == "node_modules"

    private fun isUnder(path: Path, ancestor: Path): Boolean {
        var p: Path? = path.parent
        while (p != null) {
            if (p == ancestor) return true
            p = p.parent
        }
        return false
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

    /**
     * A fixed-capacity retainer of the most-recently-modified files, backed by a binary **min-heap**
     * keyed by mtime (so the oldest sits at the root and is the cheapest to evict). Offering N files
     * costs O(N log capacity) time and only ever O(capacity) memory — the trick that lets [load]
     * survive a folder of millions without holding them all.
     */
    private class RecentWindow(private val capacity: Int) {
        private val mtimes = ArrayList<Long>(minOf(capacity, 1024))
        private val paths = ArrayList<Path>(minOf(capacity, 1024))

        /** Total `.md` files offered (i.e. discovered), regardless of how many were retained. */
        var total = 0
            private set

        /** True once at least one file was evicted because the window was full. */
        var dropped = false
            private set

        fun offer(mtime: Long, path: Path) {
            total++
            if (paths.size < capacity) {
                mtimes.add(mtime)
                paths.add(path)
                siftUp(paths.size - 1)
            } else if (mtime > mtimes[0]) {
                dropped = true
                mtimes[0] = mtime
                paths[0] = path
                siftDown(0)
            } else {
                dropped = true
            }
        }

        /** The retained paths, most-recently-modified first. */
        fun paths(): List<Path> =
            paths.indices.sortedByDescending { mtimes[it] }.map { paths[it] }

        private fun siftUp(start: Int) {
            var i = start
            while (i > 0) {
                val parent = (i - 1) / 2
                if (mtimes[i] >= mtimes[parent]) break
                swap(i, parent)
                i = parent
            }
        }

        private fun siftDown(start: Int) {
            var i = start
            val size = paths.size
            while (true) {
                val l = 2 * i + 1
                val r = 2 * i + 2
                var smallest = i
                if (l < size && mtimes[l] < mtimes[smallest]) smallest = l
                if (r < size && mtimes[r] < mtimes[smallest]) smallest = r
                if (smallest == i) break
                swap(i, smallest)
                i = smallest
            }
        }

        private fun swap(a: Int, b: Int) {
            val tm = mtimes[a]; mtimes[a] = mtimes[b]; mtimes[b] = tm
            val tp = paths[a]; paths[a] = paths[b]; paths[b] = tp
        }
    }

    companion object {
        /** Most-recent notes parsed into memory; keeps tag/search derivations snappy on huge vaults. */
        const val DEFAULT_LOAD_LIMIT = 5_000

        /** Directory entries visited before the recursive scan stops as a launch-time safety valve. */
        const val DEFAULT_MAX_SCAN = 200_000
    }
}
