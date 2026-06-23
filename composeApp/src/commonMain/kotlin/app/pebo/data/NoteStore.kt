package app.pebo.data

import app.pebo.core.Note

/**
 * A point-in-time read of everything on disk.
 *
 * [active]/[trashed] may be a bounded **window** of the most-recently-modified notes when the
 * workspace is very large (millions of `.md` files), so the app stays responsive instead of trying
 * to hold every note in memory. [totalActive]/[totalTrashed] report how many `.md` files were
 * actually discovered (which can exceed the returned list size), and [truncated] is `true` when the
 * lists were capped — letting the UI honestly show "showing N of M".
 */
data class StoreSnapshot(
    val active: List<Note>,
    val trashed: List<Note>,
    val totalActive: Int = active.size,
    val totalTrashed: Int = trashed.size,
    val truncated: Boolean = false,
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
