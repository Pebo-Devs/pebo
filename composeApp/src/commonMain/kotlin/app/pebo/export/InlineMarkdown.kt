package app.pebo.export

/**
 * A pure, platform-free inline-Markdown parser shared by every exporter (HTML, DOCX, image/PDF).
 *
 * It mirrors the editor's render-time [app.pebo.ui] inline styling **exactly** (the same bold `**`,
 * italic `*`/`_`, strike `~~`, inline code `` ` ``, link `[label](url)`, and `#tag` rules) but emits a
 * structured [InlineSpan] tree instead of a Compose `AnnotatedString`, so non-UI exporters can consume
 * it. Keeping a single source of truth means an export looks like what the user sees in Preview.
 */
sealed interface InlineSpan {
    data class Text(val text: String) : InlineSpan
    data class Bold(val children: List<InlineSpan>) : InlineSpan
    data class Italic(val children: List<InlineSpan>) : InlineSpan
    data class Strike(val children: List<InlineSpan>) : InlineSpan
    data class Code(val text: String) : InlineSpan
    data class Link(val label: String, val url: String) : InlineSpan
    /** Includes the leading `#`, e.g. `#project/pebo`. */
    data class Tag(val text: String) : InlineSpan
}

/** Parses [raw] into inline spans, dropping the Markdown markers (matching the Preview renderer). */
fun parseInline(raw: String): List<InlineSpan> {
    val out = ArrayList<InlineSpan>()
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            out += InlineSpan.Text(buf.toString())
            buf.setLength(0)
        }
    }

    var i = 0
    val n = raw.length
    while (i < n) {
        val ch = raw[i]
        when {
            ch == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    out += InlineSpan.Code(raw.substring(i + 1, end))
                    i = end + 1
                } else { buf.append(ch); i++ }
            }
            ch == '*' && i + 1 < n && raw[i + 1] == '*' -> {
                val end = raw.indexOf("**", i + 2)
                if (end > i + 1) {
                    flush()
                    out += InlineSpan.Bold(parseInline(raw.substring(i + 2, end)))
                    i = end + 2
                } else { buf.append(ch); i++ }
            }
            ch == '~' && i + 1 < n && raw[i + 1] == '~' -> {
                val end = raw.indexOf("~~", i + 2)
                if (end > i + 1) {
                    flush()
                    out += InlineSpan.Strike(parseInline(raw.substring(i + 2, end)))
                    i = end + 2
                } else { buf.append(ch); i++ }
            }
            ch == '*' || ch == '_' -> {
                val end = raw.indexOf(ch, i + 1)
                if (end > i + 1) {
                    flush()
                    out += InlineSpan.Italic(parseInline(raw.substring(i + 1, end)))
                    i = end + 1
                } else { buf.append(ch); i++ }
            }
            ch == '[' -> {
                val close = raw.indexOf(']', i + 1)
                if (close > i && close + 1 < n && raw[close + 1] == '(') {
                    val urlEnd = raw.indexOf(')', close + 2)
                    if (urlEnd > close) {
                        flush()
                        out += InlineSpan.Link(raw.substring(i + 1, close), raw.substring(close + 2, urlEnd))
                        i = urlEnd + 1
                    } else { buf.append(ch); i++ }
                } else { buf.append(ch); i++ }
            }
            ch == '#' && (i == 0 || raw[i - 1].isWhitespace()) -> {
                var j = i + 1
                while (j < n && !raw[j].isWhitespace()) j++
                if (j > i + 1) {
                    flush()
                    out += InlineSpan.Tag(raw.substring(i, j))
                    i = j
                } else { buf.append(ch); i++ }
            }
            else -> { buf.append(ch); i++ }
        }
    }
    flush()
    return out
}

/** Flattens spans back to their visible text (markers already removed) — handy for tests/snippets. */
fun InlineSpan.plainText(): String = when (this) {
    is InlineSpan.Text -> text
    is InlineSpan.Bold -> children.joinToString("") { it.plainText() }
    is InlineSpan.Italic -> children.joinToString("") { it.plainText() }
    is InlineSpan.Strike -> children.joinToString("") { it.plainText() }
    is InlineSpan.Code -> text
    is InlineSpan.Link -> label
    is InlineSpan.Tag -> text
}
