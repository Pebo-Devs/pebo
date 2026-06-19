package app.pebo.sync

import app.pebo.core.Note
import app.pebo.core.NoteFile
import app.pebo.core.Slug
import app.pebo.core.newId
import app.pebo.core.nowIso
import app.pebo.data.NoteStore

class SyncEngine(
    private val local: NoteStore,
    private val remote: CloudNoteRemote,
    private val metadata: SyncMetadataStore,
    private val deviceName: String,
) {
    suspend fun sync(): SyncReport {
        val localNotes = local.load().active.associateBy { it.id }
        val remoteNotes = remote.listFiles()
            .filterNot { it.trashed }
            .associateBy { it.noteId }

        val noteIds = (localNotes.keys + remoteNotes.keys).sorted()
        val events = ArrayList<SyncEvent>()

        for (noteId in noteIds) {
            val localNote = localNotes[noteId]
            val remoteFile = remoteNotes[noteId]
            val record = metadata.get(noteId)

            when {
                localNote != null && remoteFile == null -> {
                    pushLocal(localNote, record, events)
                }
                localNote == null && remoteFile != null -> {
                    pullRemote(remoteFile, events)
                }
                localNote != null && remoteFile != null && record == null -> {
                    firstSeenBoth(localNote, remoteFile, events)
                }
                localNote != null && remoteFile != null && record != null -> {
                    reconcile(localNote, remoteFile, record, events)
                }
            }
        }

        return SyncReport(remote.provider, events)
    }

    private suspend fun pushLocal(
        note: Note,
        record: SyncRecord?,
        events: MutableList<SyncEvent>,
    ) {
        val uploaded = remote.upload(
            noteId = note.id,
            fileName = fileName(note),
            content = NoteFile.serialize(note),
            previousRemoteId = record?.remoteId,
            previousRevision = record?.lastRevision,
        )
        metadata.put(recordFor(note, uploaded))
        events += SyncEvent.Pushed(note.id)
    }

    private suspend fun pullRemote(remoteFile: CloudNoteFile, events: MutableList<SyncEvent>) {
        val note = parseRemote(remoteFile)
        local.save(note)
        metadata.put(recordFor(note, remoteFile))
        events += SyncEvent.Pulled(note.id)
    }

    private suspend fun firstSeenBoth(
        localNote: Note,
        remoteFile: CloudNoteFile,
        events: MutableList<SyncEvent>,
    ) {
        val remoteNote = parseRemote(remoteFile)
        if (NoteHash.of(localNote) == NoteHash.of(remoteNote)) {
            metadata.put(recordFor(localNote, remoteFile))
            events += SyncEvent.Unchanged(localNote.id)
        } else {
            keepBoth(localNote, remoteFile, events)
        }
    }

    private suspend fun reconcile(
        localNote: Note,
        remoteFile: CloudNoteFile,
        record: SyncRecord,
        events: MutableList<SyncEvent>,
    ) {
        val localDirty = NoteHash.of(localNote) != record.lastBodyHash
        val remoteChanged = remoteFile.revision != record.lastRevision

        when {
            !localDirty && !remoteChanged -> events += SyncEvent.Unchanged(localNote.id)
            localDirty && !remoteChanged -> pushLocal(localNote, record, events)
            !localDirty && remoteChanged -> pullRemote(remoteFile, events)
            else -> {
                val remoteNote = parseRemote(remoteFile)
                if (NoteHash.of(localNote) == NoteHash.of(remoteNote)) {
                    metadata.put(recordFor(localNote, remoteFile))
                    events += SyncEvent.Unchanged(localNote.id)
                } else {
                    keepBoth(localNote, remoteFile, events)
                }
            }
        }
    }

    private suspend fun keepBoth(
        localNote: Note,
        remoteFile: CloudNoteFile,
        events: MutableList<SyncEvent>,
    ) {
        val remoteNote = parseRemote(remoteFile)
        local.save(remoteNote)
        metadata.put(recordFor(remoteNote, remoteFile))

        val conflict = conflictCopyOf(localNote)
        local.save(conflict)
        val uploadedConflict = remote.upload(
            noteId = conflict.id,
            fileName = fileName(conflict),
            content = NoteFile.serialize(conflict),
        )
        metadata.put(recordFor(conflict, uploadedConflict))
        events += SyncEvent.ConflictKeptBoth(remoteNote.id, conflict.id)
    }

    private fun parseRemote(file: CloudNoteFile): Note =
        NoteFile.parse(file.content, fallbackId = file.noteId, trashed = file.trashed)

    private fun recordFor(note: Note, file: CloudNoteFile): SyncRecord =
        SyncRecord(
            noteId = note.id,
            remoteId = file.remoteId,
            lastRevision = file.revision,
            lastBodyHash = NoteHash.of(note),
        )

    private fun conflictCopyOf(note: Note): Note {
        val now = nowIso()
        return note.copy(
            id = newId(),
            body = "> Conflict copy from $deviceName at $now. Review and merge.\n\n${note.body}",
            created = now,
            modified = now,
            pinned = note.pinned,
            trashed = false,
        )
    }

    private fun fileName(note: Note): String = "${Slug.of(note.title)}.md"
}

data class SyncReport(
    val provider: CloudProvider,
    val events: List<SyncEvent>,
) {
    val conflicts: Int get() = events.count { it is SyncEvent.ConflictKeptBoth }
}

sealed interface SyncEvent {
    val noteId: String

    data class Pushed(override val noteId: String) : SyncEvent
    data class Pulled(override val noteId: String) : SyncEvent
    data class Unchanged(override val noteId: String) : SyncEvent
    data class ConflictKeptBoth(
        override val noteId: String,
        val localConflictNoteId: String,
    ) : SyncEvent
}
