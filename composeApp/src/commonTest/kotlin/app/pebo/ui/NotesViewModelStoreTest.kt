package app.pebo.ui

import app.pebo.core.Note
import app.pebo.core.NoteFilter
import app.pebo.data.AppPreferences
import app.pebo.data.NoteStore
import app.pebo.data.PREF_NOTES_DIR
import app.pebo.data.StoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards [NotesViewModel.changeNotesDir] — the folder-picker store swap. Verifies it rebuilds the
 * store via the factory, reloads notes from the new location, persists the choice, and resets
 * workspace-scoped UI state. Uses [Dispatchers.Unconfined] so the eager `refresh()` runs inline.
 */
class NotesViewModelStoreTest {

    private class MapPrefs : AppPreferences {
        val map = HashMap<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private fun storeOf(vararg bodies: String): NoteStore = object : NoteStore {
        private val notes = bodies.map { Note.new(it) }
        override suspend fun load() = StoreSnapshot(active = notes, trashed = emptyList())
        override suspend fun save(note: Note) {}
        override suspend fun moveToTrash(id: String) {}
        override suspend fun restore(id: String) {}
        override suspend fun purge(id: String) {}
        override suspend fun emptyTrash() {}
    }

    @Test
    fun changeNotesDirSwapsStorePersistsAndReloads() {
        val home = storeOf("# Home note")
        val work = storeOf("# Work note A", "# Work note B")
        val prefs = MapPrefs()
        val vm = NotesViewModel(
            store = home,
            scope = CoroutineScope(Dispatchers.Unconfined),
            prefs = prefs,
            initialNotesDir = "/home/pebo",
            storeFactory = { path -> if (path == "/work/pebo") work else home },
        )

        assertEquals("/home/pebo", vm.notesDir)
        assertEquals(1, vm.active.size)
        vm.select(vm.active.first().id)
        vm.selectFilter(NoteFilter.Untagged)

        vm.changeNotesDir("/work/pebo")

        assertEquals("/work/pebo", vm.notesDir)
        assertEquals(2, vm.active.size)
        assertEquals("/work/pebo", prefs.getString(PREF_NOTES_DIR))
        assertEquals(NoteFilter.All, vm.filter)
        assertNull(vm.selectedId)
    }

    @Test
    fun changeNotesDirIgnoresBlankSamePathOrMissingFactory() {
        val home = storeOf("# Home note")

        // No factory supplied → swap is a no-op even for a valid new path.
        val noFactoryPrefs = MapPrefs()
        val noFactory = NotesViewModel(
            store = home,
            scope = CoroutineScope(Dispatchers.Unconfined),
            prefs = noFactoryPrefs,
            initialNotesDir = "/home/pebo",
        )
        noFactory.changeNotesDir("/elsewhere")
        assertEquals("/home/pebo", noFactory.notesDir)
        assertNull(noFactoryPrefs.getString(PREF_NOTES_DIR))

        // With a factory, blank and same-path inputs are ignored.
        val prefs = MapPrefs()
        val vm = NotesViewModel(
            store = home,
            scope = CoroutineScope(Dispatchers.Unconfined),
            prefs = prefs,
            initialNotesDir = "/home/pebo",
            storeFactory = { home },
        )
        vm.changeNotesDir("   ")
        vm.changeNotesDir("/home/pebo")
        assertEquals("/home/pebo", vm.notesDir)
        assertNull(prefs.getString(PREF_NOTES_DIR))
    }
}
