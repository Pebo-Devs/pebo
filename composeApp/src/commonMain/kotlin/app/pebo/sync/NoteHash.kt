package app.pebo.sync

import app.pebo.core.Note

/**
 * Stable content hash for sync dirtiness. Intentionally excludes `modified`, because saving a note
 * bumps that timestamp even when the markdown body did not semantically change.
 */
object NoteHash {
    fun of(note: Note): String = fnv1a64(buildString {
        append(note.body)
        append("\nparent=").append(note.parentId.orEmpty())
        append("\npinned=").append(note.pinned)
    })

    private fun fnv1a64(value: String): String {
        var hash = -3750763034362895579L // 64-bit FNV offset basis as signed Long.
        for (ch in value) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        return hash.toULong().toString(16)
    }
}
