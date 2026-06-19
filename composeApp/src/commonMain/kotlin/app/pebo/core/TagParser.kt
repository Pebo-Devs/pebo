package app.pebo.core

/**
 * Extracts `#tags` from a markdown body. A tag starts at line-start or after whitespace, begins
 * with `#` immediately followed by an alphanumeric/underscore, and may nest with `/` (e.g. `#a/b`).
 * Headings (`# `), `##`, fenced code blocks and inline `code` are ignored, as are URL fragments.
 */
object TagParser {
    private val tagRegex = Regex("""(?:^|\s)#([A-Za-z0-9_][A-Za-z0-9_/-]*)""")

    fun extract(body: String): List<String> {
        val result = LinkedHashSet<String>()
        var inFence = false
        for (rawLine in body.lineSequence()) {
            val trimmed = rawLine.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            val cleaned = stripInlineCode(rawLine)
            for (m in tagRegex.findAll(cleaned)) {
                val tag = m.groupValues[1].trim('/', '-')
                if (tag.isNotEmpty()) result.add(tag)
            }
        }
        return result.toList()
    }

    private fun stripInlineCode(line: String): String {
        if (!line.contains('`')) return line
        val sb = StringBuilder(line.length)
        var inCode = false
        for (c in line) {
            if (c == '`') {
                inCode = !inCode
                sb.append(' ')
            } else {
                sb.append(if (inCode) ' ' else c)
            }
        }
        return sb.toString()
    }
}
