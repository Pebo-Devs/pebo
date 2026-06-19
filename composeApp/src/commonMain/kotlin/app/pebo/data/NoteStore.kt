package app.pebo.data

import app.pebo.core.Note

/** A point-in-time read of everything on disk. */
data class StoreSnapshot(
    val active: List<Note>,
    val trashed: List<Note>,
)

/** Persistence boundary for notes. v0.1 ships [LocalNoteStore]; cloud providers implement this later. */
interface NoteStore {
    suspend fun load(): StoreSnapshot
    suspend fun save(note: Note)
    suspend fun moveToTrash(id: String)
    suspend fun restore(id: String)
    suspend fun purge(id: String)
    suspend fun emptyTrash()
}
