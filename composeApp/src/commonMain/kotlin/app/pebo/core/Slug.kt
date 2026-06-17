package app.pebo.core

/** Turns a note title into a filesystem-friendly slug used for the `.md` filename. */
object Slug {
    fun of(title: String, maxLen: Int = 60): String {
        val sb = StringBuilder()
        var lastDash = false
        for (c in title.lowercase()) {
            if (c in 'a'..'z' || c in '0'..'9') {
                sb.append(c)
                lastDash = false
            } else if (!lastDash) {
                sb.append('-')
                lastDash = true
            }
        }
        var s = sb.toString().trim('-')
        if (s.length > maxLen) s = s.substring(0, maxLen).trim('-')
        return if (s.isEmpty()) "untitled" else s
    }
}
