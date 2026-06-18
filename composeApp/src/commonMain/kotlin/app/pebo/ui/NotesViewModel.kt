package app.pebo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pebo.core.Note
import app.pebo.core.NoteFilter
import app.pebo.core.nowIso
import app.pebo.data.AppPreferences
import app.pebo.data.NoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.ui.theme.Palettes
import app.pebo.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TagEntry(val name: String, val count: Int)

/** One row in the flattened tag tree; see [NotesViewModel.tagRows]. [guides] mirrors [NoteTreeRow.guides]. */
data class TagRow(
    val name: String,
    val leaf: String,
    val count: Int,
    val depth: Int,
    val hasChildren: Boolean,
    val guides: List<Boolean>,
)

/**
 * One row in the flattened note tree.
 *
 * [guides] has one entry per ancestor column (length == [depth]); `true` means that column should
 * draw a continuing vertical rail (the ancestor at that level has a following sibling). The last
 * entry doubles as this row's own connector: `true` → a `├` tee (more siblings below), `false` → a
 * `└` elbow (last child). Roots have an empty list and draw no rails.
 */
data class NoteTreeRow(
    val note: Note,
    val depth: Int,
    val hasChildren: Boolean,
    val childCount: Int,
    val guides: List<Boolean>,
)

/**
 * In-memory, Compose-observable note state backed by a [NoteStore]. Loads everything once, mutates
 * locally for instant UI, and writes through to disk (edits are debounced).
 */
class NotesViewModel(
    store: NoteStore,
    private val scope: CoroutineScope,
    private val prefs: AppPreferences = AppPreferences.NoOp,
    initialNotesDir: String = "",
    private val storeFactory: ((String) -> NoteStore)? = null,
) {
    private var store: NoteStore = store

    var active by mutableStateOf<List<Note>>(emptyList())
        private set
    var trashed by mutableStateOf<List<Note>>(emptyList())
        private set
    var filter by mutableStateOf<NoteFilter>(NoteFilter.All)
        private set
    var query by mutableStateOf("")
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
        private set

    /**
     * Distraction-free writing mode: the sidebar + note list collapse away and the editor becomes a
     * centered, wide-margin canvas (iA Writer / Bear-style "focus"). UI-only, never persisted.
     */
    var focusMode by mutableStateOf(false)
        private set
    var storageProvider by mutableStateOf(StorageProvider.Local)
        private set
    var themeMode by mutableStateOf(ThemeMode.System)
        private set
    var paletteId by mutableStateOf(Palettes.DEFAULT_ID)
        private set

    /** Absolute path of the folder holding the active workspace (its `notes/` + `.trash/`). */
    var notesDir by mutableStateOf(initialNotesDir)
        private set

    /**
     * Per-tag "pop" styling (icon, accent color, pinned), keyed by full tag name. Only non-default
     * entries are kept. Persisted one key per tag (`tagstyle.<name>`) so it survives relaunches.
     */
    var tagStyles by mutableStateOf<Map<String, TagStyle>>(emptyMap())
        private set

    init {
        prefs.getString(KEY_PALETTE)?.let { paletteId = it }
        prefs.getString(KEY_MODE)?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()?.let { themeMode = it }
        }
        loadTagStyles()
        refresh()
    }

    private val byModified = compareByDescending<Note> { it.pinned }.thenByDescending { it.modified }

    fun refresh() {
        scope.launch {
            val snap = store.load()
            active = snap.active
            trashed = snap.trashed
            if (selectedId != null && active.none { it.id == selectedId } && trashed.none { it.id == selectedId }) {
                selectedId = null
            }
        }
    }

    val tags: List<TagEntry>
        get() {
            val counts = LinkedHashMap<String, Int>()
            for (n in active) for (t in n.tags) counts[t] = (counts[t] ?: 0) + 1
            return counts.entries
                .sortedBy { it.key.lowercase() }
                .map { TagEntry(it.key, it.value) }
        }

    /**
     * The tag hierarchy as a flattened tree. Nested tags like `project/pebo` synthesise their
     * ancestor nodes (`project`) even when no note carries the bare parent tag, so the sidebar can
     * draw a complete tree. A node's count includes every note under it (self or any descendant),
     * matching how the [NoteFilter.Tag] filter resolves prefixes.
     */
    val tagRows: List<TagRow>
        get() {
            val names = HashSet<String>()
            for (n in active) {
                for (t in n.tags) {
                    val parts = t.split('/')
                    for (i in 1..parts.size) names.add(parts.subList(0, i).joinToString("/"))
                }
            }
            if (names.isEmpty()) return emptyList()

            val childrenOf = HashMap<String, MutableList<String>>()
            for (name in names) {
                val parent = if ('/' in name) name.substringBeforeLast('/') else ""
                childrenOf.getOrPut(parent) { mutableListOf() }.add(name)
            }
            for (list in childrenOf.values) list.sortBy { it.substringAfterLast('/').lowercase() }

            fun countFor(name: String): Int =
                active.count { n -> n.tags.any { it == name || it.startsWith("$name/") } }

            val rows = ArrayList<TagRow>(names.size)
            fun visit(name: String, guides: List<Boolean>) {
                val kids = childrenOf[name].orEmpty()
                rows.add(
                    TagRow(
                        name = name,
                        leaf = name.substringAfterLast('/'),
                        count = countFor(name),
                        depth = guides.size,
                        hasChildren = kids.isNotEmpty(),
                        guides = guides,
                    ),
                )
                kids.forEachIndexed { index, kid ->
                    visit(kid, guides + (index != kids.lastIndex))
                }
            }
            childrenOf[""].orEmpty().forEach { visit(it, emptyList()) }
            return rows
        }

    /**
     * Pinned tags lifted into their own flat section at the top of the sidebar. Each row shows the
     * full tag name (so `project/pebo` reads unambiguously out of the tree) and keeps its rolled-up
     * count; rendered without rails since there is no hierarchy here.
     */
    val pinnedTagRows: List<TagRow>
        get() = tagRows
            .filter { tagStyles[it.name]?.pinned == true }
            .sortedBy { it.name.lowercase() }
            .map { it.copy(leaf = it.name, depth = 0, hasChildren = false, guides = emptyList()) }

    /** The stored style for [name], or a default (unstyled) one. */
    fun tagStyle(name: String): TagStyle = tagStyles[name] ?: TagStyle()

    val untaggedCount: Int get() = active.count { it.tags.isEmpty() }

    val visibleNotes: List<Note>
        get() {
            val base = when (val f = filter) {
                NoteFilter.All -> active
                NoteFilter.Untagged -> active.filter { it.tags.isEmpty() }
                NoteFilter.Trash -> trashed
                is NoteFilter.Tag -> active.filter { n ->
                    n.tags.any { it == f.name || it.startsWith(f.name + "/") }
                }
            }
            val q = query.trim()
            if (q.isEmpty()) return base
            return base.filter { n ->
                n.title.contains(q, ignoreCase = true) ||
                    n.body.contains(q, ignoreCase = true) ||
                    n.tags.any { it.contains(q, ignoreCase = true) }
            }
        }

    val visibleNoteRows: List<NoteTreeRow>
        get() = buildTreeRows(visibleNotes)

    val selectedNote: Note? get() = active.firstOrNull { it.id == selectedId } ?: trashed.firstOrNull { it.id == selectedId }

    fun select(id: String?) {
        selectedId = id
    }

    fun selectFilter(f: NoteFilter) {
        filter = f
        if (visibleNotes.none { it.id == selectedId }) selectedId = null
    }

    fun updateQuery(q: String) {
        query = q
        if (visibleNotes.none { it.id == selectedId }) selectedId = null
    }

    fun createNote(parentId: String? = null) {
        val note = Note.new(body = "", parentId = parentId)
        query = ""
        filter = NoteFilter.All
        active = listOf(note) + active
        selectedId = note.id
        scope.launch { store.save(note) }
    }

    fun createChildNote(parentId: String) {
        if (active.none { it.id == parentId }) return
        createNote(parentId = parentId)
    }

    fun addTag(id: String, rawTag: String): String? {
        val tag = normalizeTag(rawTag) ?: return null
        val note = active.firstOrNull { it.id == id && !it.trashed } ?: return null
        if (note.tags.any { it.equals(tag, ignoreCase = true) }) return note.body
        val trimmed = note.body.trimEnd()
        val body = if (trimmed.isBlank()) {
            "# Untitled\n\n#$tag"
        } else {
            "$trimmed\n\n#$tag"
        }
        val updated = note.copy(body = body, modified = nowIso())
        active = active.map { if (it.id == id) updated else it }
        scope.launch { store.save(updated) }
        return body
    }

    private var saveJob: Job? = null

    fun updateBody(id: String, newBody: String) {
        active = active.map { if (it.id == id) it.copy(body = newBody, modified = nowIso()) else it }
        val note = active.firstOrNull { it.id == id } ?: return
        saveJob?.cancel()
        saving = true
        saveJob = scope.launch {
            delay(400)
            store.save(note)
            saving = false
        }
    }

    fun togglePin(id: String) {
        active = active
            .map { if (it.id == id) it.copy(pinned = !it.pinned, modified = nowIso()) else it }
            .sortedWith(byModified)
        val note = active.firstOrNull { it.id == id } ?: return
        scope.launch { store.save(note) }
    }

    fun trash(id: String) {
        val note = active.firstOrNull { it.id == id } ?: return
        active = active.filterNot { it.id == id }
        trashed = listOf(note.copy(trashed = true)) + trashed
        if (selectedId == id) selectedId = null
        scope.launch { store.moveToTrash(id) }
    }

    fun restore(id: String) {
        val note = trashed.firstOrNull { it.id == id } ?: return
        trashed = trashed.filterNot { it.id == id }
        active = (listOf(note.copy(trashed = false)) + active).sortedWith(byModified)
        if (selectedId == id) selectedId = null
        scope.launch { store.restore(id) }
    }

    fun purge(id: String) {
        trashed = trashed.filterNot { it.id == id }
        if (selectedId == id) selectedId = null
        scope.launch { store.purge(id) }
    }

    fun emptyTrash() {
        trashed = emptyList()
        if (filter == NoteFilter.Trash) selectedId = null
        scope.launch { store.emptyTrash() }
    }

    fun openSettings() {
        showSettings = true
    }

    fun closeSettings() {
        showSettings = false
    }

    fun toggleFocusMode() {
        focusMode = !focusMode
    }

    /** Returns true if focus mode was actually on (so callers can swallow the Esc key). */
    fun exitFocusMode(): Boolean {
        if (!focusMode) return false
        focusMode = false
        return true
    }

    fun selectStorage(provider: StorageProvider) {
        if (provider.available) storageProvider = provider
    }

    /**
     * Re-points the local workspace at [path] (where its `notes/` and `.trash/` folders live),
     * persists the choice, and reloads. No-op when the path is blank, unchanged, or when no
     * [storeFactory] was supplied (e.g. in tests).
     */
    fun changeNotesDir(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed == notesDir) return
        val factory = storeFactory ?: return
        store = factory(trimmed)
        notesDir = trimmed
        selectedId = null
        query = ""
        filter = NoteFilter.All
        prefs.putString(PREF_NOTES_DIR, trimmed)
        refresh()
    }

    fun selectPalette(id: String) {
        paletteId = id
        prefs.putString(KEY_PALETTE, id)
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.putString(KEY_MODE, mode.name)
    }

    private fun loadTagStyles() {
        val loaded = LinkedHashMap<String, TagStyle>()
        for (key in prefs.keys()) {
            if (!key.startsWith(TAG_STYLE_PREFIX)) continue
            val name = key.removePrefix(TAG_STYLE_PREFIX)
            val style = prefs.getString(key)?.let { decodeTagStyle(it) } ?: continue
            if (!style.isDefault) loaded[name] = style
        }
        tagStyles = loaded
    }

    private fun updateTagStyle(name: String, transform: (TagStyle) -> TagStyle) {
        val next = transform(tagStyles[name] ?: TagStyle())
        val map = LinkedHashMap(tagStyles)
        val key = TAG_STYLE_PREFIX + name
        if (next.isDefault) {
            map.remove(name)
            prefs.remove(key)
        } else {
            map[name] = next
            prefs.putString(key, encodeTagStyle(next))
        }
        tagStyles = map
    }

    fun setTagIconId(name: String, iconId: String?) = updateTagStyle(name) { it.copy(iconId = iconId) }

    fun setTagColor(name: String, colorArgb: Long?) = updateTagStyle(name) { it.copy(colorArgb = colorArgb) }

    fun toggleTagPinned(name: String) = updateTagStyle(name) { it.copy(pinned = !it.pinned) }

    fun resetTagStyle(name: String) = updateTagStyle(name) { TagStyle() }

    private fun buildTreeRows(notes: List<Note>): List<NoteTreeRow> {
        val ids = notes.mapTo(HashSet()) { it.id }
        val childrenByParent = notes
            .filter { it.parentId in ids }
            .groupBy { it.parentId!! }
        val roots = notes.filter { it.parentId !in ids }
        val rows = ArrayList<NoteTreeRow>(notes.size)
        val visited = HashSet<String>()

        fun visit(note: Note, guides: List<Boolean>) {
            if (!visited.add(note.id)) return
            val children = childrenByParent[note.id].orEmpty()
            rows.add(
                NoteTreeRow(
                    note = note,
                    depth = guides.size,
                    hasChildren = children.isNotEmpty(),
                    childCount = children.size,
                    guides = guides,
                ),
            )
            children.forEachIndexed { index, child ->
                visit(child, guides + (index != children.lastIndex))
            }
        }

        roots.forEach { visit(it, emptyList()) }
        notes.filter { it.id !in visited }.forEach { visit(it, emptyList()) }
        return rows
    }

    private fun normalizeTag(rawTag: String): String? {
        val tag = rawTag
            .trim()
            .trimStart('#')
            .replace('\\', '/')
            .split('/')
            .joinToString("/") { segment ->
                segment
                    .trim()
                    .lowercase()
                    .replace(Regex("\\s+"), "-")
                    .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            }
            .trim('/')
        return tag.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val KEY_PALETTE = "theme.palette"
        const val KEY_MODE = "theme.mode"
        const val TAG_STYLE_PREFIX = "tagstyle."
    }
}
