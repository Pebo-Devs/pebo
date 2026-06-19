package app.pebo.ui

import app.pebo.core.Note
import app.pebo.data.NoteStore
import app.pebo.data.StoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards [NotesViewModel.tagRows] — the flattened tag hierarchy the sidebar draws. Exercises the
 * tricky parts: synthesising ancestor nodes that no note tags directly, counting descendants into a
 * parent, sibling ordering, and the connector [TagRow.guides] flags.
 */
class TagTreeTest {

    private fun vmWith(vararg bodies: String): NotesViewModel {
        val notes = bodies.map { Note.new(it) }
        val store = object : NoteStore {
            override suspend fun load() = StoreSnapshot(active = notes, trashed = emptyList())
            override suspend fun save(note: Note) {}
            override suspend fun moveToTrash(id: String) {}
            override suspend fun restore(id: String) {}
            override suspend fun purge(id: String) {}
            override suspend fun emptyTrash() {}
        }
        // Unconfined runs the init refresh() eagerly; the fake load() never suspends, so `active`
        // is populated synchronously by the time the constructor returns.
        return NotesViewModel(store, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun synthesisesAncestorsCountsDescendantsAndOrdersSiblings() {
        val vm = vmWith(
            "# A\n#project/pebo",
            "# B\n#project/pebo/ui",
            "# C\n#zeta",
            "# D\n#project/docs",
        )

        val rows = vm.tagRows
        // DFS order with siblings sorted by leaf: project, (docs, pebo, (ui)), zeta.
        assertEquals(
            listOf("project", "project/docs", "project/pebo", "project/pebo/ui", "zeta"),
            rows.map { it.name },
        )

        val byName = rows.associateBy { it.name }

        // `project` is synthesised even though no note carries the bare tag, and its count rolls up
        // every descendant (A, B, D — not C).
        val project = byName.getValue("project")
        assertEquals(0, project.depth)
        assertEquals(3, project.count)
        assertTrue(project.hasChildren)
        assertTrue(project.guides.isEmpty())

        // First child of `project` with a following sibling → continuing rail.
        assertEquals(listOf(true), byName.getValue("project/docs").guides)
        assertEquals(1, byName.getValue("project/docs").count)

        // Last child of `project` → elbow, no continuing rail at its column.
        val pebo = byName.getValue("project/pebo")
        assertEquals(listOf(false), pebo.guides)
        assertEquals(2, pebo.count)
        assertTrue(pebo.hasChildren)
        assertEquals("pebo", pebo.leaf)

        // Deepest leaf inherits an empty ancestor rail (parent was last child) plus its own elbow.
        val ui = byName.getValue("project/pebo/ui")
        assertEquals(2, ui.depth)
        assertEquals(listOf(false, false), ui.guides)
        assertEquals("ui", ui.leaf)

        val zeta = byName.getValue("zeta")
        assertEquals(0, zeta.depth)
        assertEquals(1, zeta.count)
    }

    @Test
    fun emptyWhenNoTags() {
        val vm = vmWith("# Just a title", "# Another\nplain body")
        assertTrue(vm.tagRows.isEmpty())
    }
}
