package app.pebo.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncRecord(
    val noteId: String,
    val remoteId: String,
    val lastRevision: String,
    val lastBodyHash: String,
    val tombstone: Boolean = false,
)

interface SyncMetadataStore {
    suspend fun get(noteId: String): SyncRecord?
    suspend fun put(record: SyncRecord)
    suspend fun all(): List<SyncRecord>
}

class InMemorySyncMetadataStore : SyncMetadataStore {
    private val records = LinkedHashMap<String, SyncRecord>()

    override suspend fun get(noteId: String): SyncRecord? = records[noteId]

    override suspend fun put(record: SyncRecord) {
        records[record.noteId] = record
    }

    override suspend fun all(): List<SyncRecord> = records.values.toList()
}
