package app.pebo.sync

import app.pebo.core.Note
import app.pebo.core.NoteFile
import app.pebo.data.NoteStore
import app.pebo.data.StoreSnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncEngineTest {

    @Test
    fun pushesNewLocalNoteToRemote() = runBlocking {
        val local = MemoryNoteStore()
        val remote = MemoryCloudRemote()
        val metadata = InMemorySyncMetadataStore()
        val note = Note.new("# Local\nBody")
        local.save(note)

        val report = SyncEngine(local, remote, metadata, "test-device").sync()

        assertEquals(listOf(SyncEvent.Pushed(note.id)), report.events)
        assertEquals(note.id, remote.listFiles().single().noteId)
        assertEquals(note.id, metadata.all().single().noteId)
    }

    @Test
    fun pullsNewRemoteNoteToLocal() = runBlocking {
        val local = MemoryNoteStore()
        val remote = MemoryCloudRemote()
        val metadata = InMemorySyncMetadataStore()
        val remoteNote = Note.new("# Remote\nBody")
        remote.upload(remoteNote.id, "remote.md", NoteFile.serialize(remoteNote))

        val report = SyncEngine(local, remote, metadata, "test-device").sync()

        assertEquals(listOf(SyncEvent.Pulled(remoteNote.id)), report.events)
        assertEquals(remoteNote.id, local.load().active.single().id)
    }

    @Test
    fun pushesLocalDirtyWhenRemoteUnchanged() = runBlocking {
        val local = MemoryNoteStore()
        val remote = MemoryCloudRemote()
        val metadata = InMemorySyncMetadataStore()
        val note = Note.new("# Note\nOriginal")
        local.save(note)
        SyncEngine(local, remote, metadata, "test-device").sync()

        local.save(note.copy(body = "# Note\nLocal edit"))
        val report = SyncEngine(local, remote, metadata, "test-device").sync()

        assertEquals(listOf(SyncEvent.Pushed(note.id)), report.events)
        val remoteBody = NoteFile.parse(remote.listFiles().single().content, note.id, trashed = false).body
        assertEquals("# Note\nLocal edit", remoteBody)
    }

    @Test
    fun keepsBothWhenLocalAndRemoteChangedDifferently() = runBlocking {
        val local = MemoryNoteStore()
        val remote = MemoryCloudRemote()
        val metadata = InMemorySyncMetadataStore()
        val note = Note.new("# Shared\nOriginal")
        local.save(note)
        SyncEngine(local, remote, metadata, "test-device").sync()

        local.save(note.copy(body = "# Shared\nLocal edit"))
        val remoteFile = remote.listFiles().single()
        val remoteNote = note.copy(body = "# Shared\nRemote edit")
        remote.upload(note.id, remoteFile.fileName, NoteFile.serialize(remoteNote), remoteFile.remoteId, remoteFile.revision)

        val report = SyncEngine(local, remote, metadata, "test-device").sync()

        val event = assertIs<SyncEvent.ConflictKeptBoth>(report.events.single())
        assertEquals(note.id, event.noteId)
        assertEquals(1, report.conflicts)

        val localNotes = local.load().active
        assertEquals(2, localNotes.size)
        assertTrue(localNotes.any { it.id == note.id && it.body == "# Shared\nRemote edit" })
        assertTrue(localNotes.any { it.id == event.localConflictNoteId && it.body.contains("Local edit") })
        assertEquals(2, remote.listFiles().size)
    }
}

private class MemoryNoteStore : NoteStore {
    private val active = LinkedHashMap<String, Note>()
    private val trashed = LinkedHashMap<String, Note>()

    override suspend fun load(): StoreSnapshot = StoreSnapshot(active.values.toList(), trashed.values.toList())

    override suspend fun save(note: Note) {
        trashed.remove(note.id)
        active[note.id] = note.copy(trashed = false)
    }

    override suspend fun moveToTrash(id: String) {
        val note = active.remove(id) ?: return
        trashed[id] = note.copy(trashed = true)
    }

    override suspend fun restore(id: String) {
        val note = trashed.remove(id) ?: return
        active[id] = note.copy(trashed = false)
    }

    override suspend fun purge(id: String) {
        trashed.remove(id)
    }

    override suspend fun emptyTrash() {
        trashed.clear()
    }
}

private class MemoryCloudRemote : CloudNoteRemote {
    override val provider: CloudProvider = CloudProvider.GoogleDrive
    private val files = LinkedHashMap<String, CloudNoteFile>()
    private var nextRemoteId = 1
    private var nextRevision = 1

    override suspend fun listFiles(): List<CloudNoteFile> = files.values.toList()

    override suspend fun upload(
        noteId: String,
        fileName: String,
        content: String,
        previousRemoteId: String?,
        previousRevision: String?,
    ): CloudNoteFile {
        val remoteId = previousRemoteId ?: "remote-${nextRemoteId++}"
        val file = CloudNoteFile(
            remoteId = remoteId,
            noteId = noteId,
            fileName = fileName,
            content = content,
            revision = "rev-${nextRevision++}",
        )
        files[remoteId] = file
        return file
    }
}
