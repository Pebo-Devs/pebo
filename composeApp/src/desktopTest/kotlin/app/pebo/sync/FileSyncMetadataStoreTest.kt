package app.pebo.sync

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSyncMetadataStoreTest {
    @Test
    fun persistsRecordsAcrossStoreInstances() = runBlocking {
        val path = (Files.createTempDirectory("pebo-sync-state").toString() + "/sync-state.json").toPath()
        val store = FileSyncMetadataStore(FileSystem.SYSTEM, path)
        store.put(
            SyncRecord(
                noteId = "note-1",
                remoteId = "remote-1",
                lastRevision = "rev-1",
                lastBodyHash = "hash-1",
            ),
        )

        val reloaded = FileSyncMetadataStore(FileSystem.SYSTEM, path)
        assertEquals("remote-1", reloaded.get("note-1")?.remoteId)
        assertEquals(1, reloaded.all().size)
    }

    @Test
    fun replacesRecordByNoteId() = runBlocking {
        val path = (Files.createTempDirectory("pebo-sync-state").toString() + "/sync-state.json").toPath()
        val store = FileSyncMetadataStore(FileSystem.SYSTEM, path)
        store.put(SyncRecord("note-1", "remote-1", "rev-1", "hash-1"))
        store.put(SyncRecord("note-1", "remote-1", "rev-2", "hash-2"))

        val records = FileSyncMetadataStore(FileSystem.SYSTEM, path).all()
        assertEquals(1, records.size)
        assertEquals("rev-2", records.single().lastRevision)
    }
}
