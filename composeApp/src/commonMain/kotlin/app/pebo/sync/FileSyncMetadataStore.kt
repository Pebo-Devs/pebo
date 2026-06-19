package app.pebo.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class FileSyncMetadataStore(
    private val fs: FileSystem,
    private val path: Path,
) : SyncMetadataStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private var cache: LinkedHashMap<String, SyncRecord>? = null

    override suspend fun get(noteId: String): SyncRecord? = withContext(Dispatchers.Default) {
        load()[noteId]
    }

    override suspend fun put(record: SyncRecord): Unit = withContext(Dispatchers.Default) {
        val records = load()
        records[record.noteId] = record
        write(records)
    }

    override suspend fun all(): List<SyncRecord> = withContext(Dispatchers.Default) {
        load().values.toList()
    }

    private fun load(): LinkedHashMap<String, SyncRecord> {
        cache?.let { return it }
        val records = if (fs.exists(path)) {
            val text = fs.read(path) { readUtf8() }
            json.decodeFromString<SyncRecordFile>(text).records
        } else {
            emptyList()
        }
        return LinkedHashMap<String, SyncRecord>().also { map ->
            for (record in records) map[record.noteId] = record
            cache = map
        }
    }

    private fun write(records: LinkedHashMap<String, SyncRecord>) {
        path.parent?.let { fs.createDirectories(it) }
        val tmp = path.parent!! / (path.name + ".tmp")
        fs.write(tmp) {
            writeUtf8(json.encodeToString(SyncRecordFile(records.values.toList())))
        }
        fs.atomicMove(tmp, path)
    }
}

@Serializable
private data class SyncRecordFile(
    val records: List<SyncRecord> = emptyList(),
)
