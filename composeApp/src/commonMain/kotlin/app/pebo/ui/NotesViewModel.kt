package app.pebo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pebo.core.Note
import app.pebo.core.NoteFilter
import app.pebo.core.nowIso
import app.pebo.data.NoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TagEntry(val name: String, val count: Int)

/**
 * In-memory, Compose-observable note state backed by a [NoteStore]. Loads everything once, mutates
 * locally for instant UI, and writes through to disk (edits are debounced).
 */
class NotesViewModel(
    private val store: NoteStore,
    private val scope: CoroutineScope,
) {
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
    var storageProvider by mutableStateOf(StorageProvider.Local)
        private set

    init {
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

    fun createNote() {
        val note = Note.new(body = "")
        query = ""
        filter = NoteFilter.All
        active = listOf(note) + active
        selectedId = note.id
        scope.launch { store.save(note) }
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

    fun selectStorage(provider: StorageProvider) {
        if (provider.available) storageProvider = provider
    }
}
