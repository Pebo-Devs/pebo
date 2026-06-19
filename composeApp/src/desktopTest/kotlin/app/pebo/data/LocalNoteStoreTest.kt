package app.pebo.data

import app.pebo.core.Note
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalNoteStoreTest {

    private fun newStore(): LocalNoteStore {
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        return LocalNoteStore(FileSystem.SYSTEM, dir)
    }

    @Test
    fun savesAndReloadsWithDerivedFields() = runBlocking {
        val store = newStore()
        val note = Note.new("# Hello world\nSome body with #travel and #travel/docs")
        store.save(note)

        val snap = store.load()
        assertEquals(1, snap.active.size)
        val loaded = snap.active.single()
        assertEquals(note.id, loaded.id)
        assertEquals("Hello world", loaded.title)
        assertTrue(loaded.tags.contains("travel"))
        assertTrue(loaded.tags.contains("travel/docs"))
    }

    @Test
    fun savesAndReloadsParentRelationship() = runBlocking {
        val store = newStore()
        val parent = Note.new("# Project")
        val child = Note.new("# Task", parentId = parent.id)
        store.save(parent)
        store.save(child)

        val snap = store.load()
        assertEquals(parent.id, snap.active.single { it.id == child.id }.parentId)
    }

    @Test
    fun trashRestoreAndPurge() = runBlocking {
        val store = newStore()
        val note = Note.new("Just a note")
        store.save(note)

        store.moveToTrash(note.id)
        store.load().let {
            assertEquals(0, it.active.size)
            assertEquals(1, it.trashed.size)
            assertTrue(it.trashed.single().trashed)
        }

        store.restore(note.id)
        store.load().let {
            assertEquals(1, it.active.size)
            assertEquals(0, it.trashed.size)
        }

        store.moveToTrash(note.id)
        store.purge(note.id)
        store.load().let {
            assertEquals(0, it.active.size)
            assertEquals(0, it.trashed.size)
        }
    }

    @Test
    fun pinnedNotesSortFirst() = runBlocking {
        val store = newStore()
        val a = Note.new("Alpha")
        val b = Note.new("Bravo")
        store.save(a)
        store.save(b)
        store.save(b.copy(pinned = true))

        val active = store.load().active
        assertEquals(b.id, active.first().id)
    }

    @Test
    fun adoptsPreexistingTopLevelMarkdown() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        // Markdown the user already had in the folder, with no Pebo frontmatter.
        fs.write(dir / "ideas.md") { writeUtf8("# Trip ideas\nVisit Kyoto in autumn. #travel") }

        val snap = LocalNoteStore(fs, dir).load()

        assertEquals(1, snap.active.size)
        val note = snap.active.single()
        assertEquals("ideas", note.id)
        assertEquals("Trip ideas", note.title)
        assertTrue(note.body.contains("Kyoto"))
        assertTrue(note.tags.contains("travel"))
    }

    @Test
    fun editingAdoptedTopLevelFileWritesBackInPlace() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        fs.write(dir / "ideas.md") { writeUtf8("# Trip ideas\nVisit Kyoto.") }

        val store = LocalNoteStore(fs, dir)
        val note = store.load().active.single()
        store.save(note.copy(body = "# Trip ideas\nVisit Kyoto and Nara."))

        // The original top-level file is updated in place; no duplicate appears under notes/.
        assertTrue(fs.exists(dir / "ideas.md"))
        assertTrue(!fs.exists(dir / "notes" / "ideas.md"))
        val reloaded = store.load().active
        assertEquals(1, reloaded.size)
        assertTrue(reloaded.single().body.contains("Nara"))
    }

    @Test
    fun notesSubfolderWinsOverTopLevelOnIdCollision() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        fs.createDirectories(dir / "notes")
        fs.write(dir / "notes" / "dup.md") { writeUtf8("---\nid: dup\n---\nFrom notes subfolder") }
        fs.write(dir / "dup.md") { writeUtf8("---\nid: dup\n---\nFrom top level") }

        val active = LocalNoteStore(fs, dir).load().active

        assertEquals(1, active.count { it.id == "dup" })
        assertTrue(active.single { it.id == "dup" }.body.contains("notes subfolder"))
    }
}
