package app.pebo.core

/** Reads/writes a note's on-disk representation: minimal YAML frontmatter followed by the body. */
object NoteFile {

    fun serialize(note: Note): String = buildString {
        append("---\n")
        append("id: ").append(note.id).append('\n')
        append("created: ").append(note.created).append('\n')
        append("modified: ").append(note.modified).append('\n')
        if (!note.parentId.isNullOrBlank()) append("parent: ").append(note.parentId).append('\n')
        if (note.pinned) append("pinned: true\n")
        append("---\n")
        append(note.body)
    }

    fun parse(text: String, fallbackId: String, trashed: Boolean): Note {
        val normalized = text.replace("\r\n", "\n")
        if (normalized.startsWith("---\n")) {
            val close = normalized.indexOf("\n---", startIndex = 3)
            if (close != -1) {
                val fmBlock = normalized.substring(4, close)
                val afterDashes = close + 4 // position just past "\n---"
                val nl = normalized.indexOf('\n', afterDashes)
                val body = if (nl != -1) normalized.substring(nl + 1) else ""
                val fm = parseFrontMatter(fmBlock)
                val id = fm["id"]?.takeIf { it.isNotBlank() } ?: fallbackId
                return Note(
                    id = id,
                    body = body,
                    created = fm["created"]?.takeIf { it.isNotBlank() } ?: nowIso(),
                    modified = fm["modified"]?.takeIf { it.isNotBlank() } ?: nowIso(),
                    parentId = fm["parent"]?.takeIf { it.isNotBlank() },
                    pinned = fm["pinned"]?.equals("true", ignoreCase = true) == true,
                    trashed = trashed,
                )
            }
        }
        val now = nowIso()
        return Note(id = fallbackId, body = normalized, created = now, modified = now, trashed = trashed)
    }

    private fun parseFrontMatter(block: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (line in block.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            if (value.length >= 2 &&
                ((value.first() == '"' && value.last() == '"') || (value.first() == '\'' && value.last() == '\''))
            ) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        return map
    }
}
