package app.pebo.core

/**
 * A single note. The markdown [body] is the source of truth on disk; [title], [snippet] and
 * [tags] are derived from it. [trashed] reflects which folder the file lives in, not frontmatter.
 */
data class Note(
    val id: String,
    val body: String,
    val created: String,
    val modified: String,
    val parentId: String? = null,
    val pinned: Boolean = false,
    val trashed: Boolean = false,
) {
    val title: String get() = deriveTitle(body)
    val snippet: String get() = deriveSnippet(body)
    val tags: List<String> get() = TagParser.extract(body)

    companion object {
        fun new(body: String = "", parentId: String? = null): Note {
            val now = nowIso()
            return Note(id = newId(), body = body, created = now, modified = now, parentId = parentId)
        }
    }
}

internal fun deriveTitle(body: String): String {
    val firstLine = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return "Untitled"
    val stripped = firstLine.trimStart('#').trim()
    return if (stripped.isEmpty()) "Untitled" else stripped
}

internal fun deriveSnippet(body: String): String {
    val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.isEmpty()) return ""
    val sb = StringBuilder()
    var skippedTitle = false
    for (raw in lines) {
        if (!skippedTitle) {
            skippedTitle = true
            continue // skip the title line
        }
        if (isTagOnlyLine(raw)) continue // tags render as chips, keep them out of the preview text
        val l = raw.trimStart('#', '>', '-', '*', ' ').trim()
        if (l.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append("  ")
        sb.append(l)
        if (sb.length >= 120) break
    }
    return sb.toString().take(120)
}

private fun isTagOnlyLine(line: String): Boolean {
    val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return tokens.isNotEmpty() && tokens.all { it.length > 1 && it.startsWith("#") }
}
