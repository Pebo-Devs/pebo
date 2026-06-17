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
    val pinned: Boolean = false,
    val trashed: Boolean = false,
) {
    val title: String get() = deriveTitle(body)
    val snippet: String get() = deriveSnippet(body)
    val tags: List<String> get() = TagParser.extract(body)

    companion object {
        fun new(body: String = ""): Note {
            val now = nowIso()
            return Note(id = newId(), body = body, created = now, modified = now)
        }
    }
}

internal fun deriveTitle(body: String): String {
    val firstLine = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return "Untitled"
    val stripped = firstLine.trimStart('#').trim()
    return if (stripped.isEmpty()) "Untitled" else stripped
}

internal fun deriveSnippet(body: String): String {
    val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.iterator()
    if (!lines.hasNext()) return ""
    lines.next() // skip the title line
    val sb = StringBuilder()
    while (lines.hasNext() && sb.length < 120) {
        val l = lines.next().trimStart('#', '>', '-', '*', ' ').trim()
        if (l.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(l)
        }
    }
    return sb.toString().take(120)
}
