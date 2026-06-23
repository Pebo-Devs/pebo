package app.pebo.data

import app.pebo.core.Note
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun discoversMarkdownInNestedSubfoldersAtAnyDepth() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        fs.createDirectories(dir / "a" / "b" / "c")
        fs.write(dir / "top.md") { writeUtf8("# Top") }
        fs.write(dir / "a" / "mid.md") { writeUtf8("# Mid") }
        fs.write(dir / "a" / "b" / "c" / "deep.md") { writeUtf8("# Deep\n#research") }

        val snap = LocalNoteStore(fs, dir).load()

        assertEquals(setOf("Top", "Mid", "Deep"), snap.active.map { it.title }.toSet())
        assertEquals(3, snap.totalActive)
        assertFalse(snap.truncated)
        assertTrue(snap.active.single { it.title == "Deep" }.tags.contains("research"))
    }

    @Test
    fun skipsHiddenAndVendorFolders() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        fs.createDirectories(dir / ".git")
        fs.createDirectories(dir / ".obsidian")
        fs.createDirectories(dir / "node_modules" / "pkg")
        fs.createDirectories(dir / "real")
        fs.write(dir / ".git" / "config.md") { writeUtf8("# Should be skipped") }
        fs.write(dir / ".obsidian" / "workspace.md") { writeUtf8("# Should be skipped") }
        fs.write(dir / "node_modules" / "pkg" / "readme.md") { writeUtf8("# Should be skipped") }
        fs.write(dir / "real" / "keep.md") { writeUtf8("# Keep") }

        val snap = LocalNoteStore(fs, dir).load()

        assertEquals(listOf("Keep"), snap.active.map { it.title })
        assertEquals(1, snap.totalActive)
    }

    @Test
    fun editsDeeplyNestedAdoptedFileInPlace() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        fs.createDirectories(dir / "research" / "2024")
        fs.write(dir / "research" / "2024" / "paper.md") { writeUtf8("# Paper\nDraft.") }

        val store = LocalNoteStore(fs, dir)
        val note = store.load().active.single()
        store.save(note.copy(body = "# Paper\nFinal version."))

        // The nested file is updated where it lives; nothing is copied into notes/.
        assertTrue(fs.exists(dir / "research" / "2024" / "paper.md"))
        assertTrue(!fs.exists(dir / "notes" / "paper.md"))
        assertTrue(store.load().active.single().body.contains("Final version"))
    }

    @Test
    fun capsToMostRecentWindowAndReportsTotals() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        for (i in 1..6) fs.write(dir / "n$i.md") { writeUtf8("# Note $i") }
        // Make recency deterministic so we can assert which window survives: n6 newest … n1 oldest.
        for (i in 1..6) {
            val nio = java.nio.file.Paths.get((dir / "n$i.md").toString())
            java.nio.file.Files.setLastModifiedTime(
                nio,
                java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L + i * 60_000L),
            )
        }

        val snap = LocalNoteStore(fs, dir, loadLimit = 3).load()

        assertEquals(3, snap.active.size)
        assertEquals(6, snap.totalActive)
        assertTrue(snap.truncated)
        // Only the three most-recently-modified notes are loaded.
        assertEquals(setOf("Note 6", "Note 5", "Note 4"), snap.active.map { it.title }.toSet())
    }

    @Test
    fun reportsTotalsWithoutTruncationWhenUnderLimit() = runBlocking {
        val fs = FileSystem.SYSTEM
        val dir = Files.createTempDirectory("pebo-test").toString().toPath()
        for (i in 1..3) fs.write(dir / "n$i.md") { writeUtf8("# Note $i") }

        val snap = LocalNoteStore(fs, dir, loadLimit = 10).load()

        assertEquals(3, snap.active.size)
        assertEquals(3, snap.totalActive)
        assertFalse(snap.truncated)
    }
}
