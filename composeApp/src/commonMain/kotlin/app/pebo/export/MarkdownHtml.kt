package app.pebo.export

import app.pebo.ui.MdBlock
import app.pebo.ui.parseMarkdownBlocks

/**
 * Pure Markdown → a self-contained, styled HTML document. Reuses the same block parser
 * ([parseMarkdownBlocks]) and inline grammar ([parseInline]) the live Preview uses, so the export
 * matches what the user sees. No external dependencies — the CSS is embedded so the file opens
 * anywhere offline.
 */
fun markdownToHtml(title: String, markdown: String): String {
    val blocks = parseMarkdownBlocks(markdown)
    val body = StringBuilder()
    for (b in blocks) {
        when (b) {
            is MdBlock.Heading -> {
                val lvl = b.level.coerceIn(1, 6)
                body.append("<h").append(lvl).append('>')
                    .append(inlineToHtml(b.text)).append("</h").append(lvl).append(">\n")
            }
            is MdBlock.Paragraph ->
                body.append("<p>").append(inlineToHtmlMultiline(b.text)).append("</p>\n")
            is MdBlock.Bullet -> {
                body.append("<ul>\n")
                b.items.forEach { body.append("  <li>").append(inlineToHtml(it)).append("</li>\n") }
                body.append("</ul>\n")
            }
            is MdBlock.Ordered -> {
                val start = b.items.firstOrNull()?.first ?: 1
                body.append("<ol start=\"").append(start).append("\">\n")
                b.items.forEach { body.append("  <li>").append(inlineToHtml(it.second)).append("</li>\n") }
                body.append("</ol>\n")
            }
            is MdBlock.Tasks -> {
                body.append("<ul class=\"tasks\">\n")
                b.items.forEach {
                    body.append("  <li><input type=\"checkbox\" disabled")
                        .append(if (it.checked) " checked" else "")
                        .append("> ").append(inlineToHtml(it.text)).append("</li>\n")
                }
                body.append("</ul>\n")
            }
            is MdBlock.Quote ->
                body.append("<blockquote>").append(inlineToHtmlMultiline(b.text)).append("</blockquote>\n")
            is MdBlock.Code -> {
                val cls = b.language?.takeIf { it.isNotBlank() }
                    ?.let { " class=\"language-${escapeAttr(it)}\"" } ?: ""
                body.append("<pre><code").append(cls).append('>')
                    .append(escapeHtml(b.code)).append("</code></pre>\n")
            }
            is MdBlock.Table -> appendTable(body, b)
            is MdBlock.Image ->
                body.append("<p class=\"img\"><img src=\"").append(escapeAttr(b.url))
                    .append("\" alt=\"").append(escapeAttr(b.alt)).append("\"></p>\n")
            MdBlock.Rule -> body.append("<hr>\n")
        }
    }
    return documentShell(title, body.toString())
}

private fun appendTable(sb: StringBuilder, t: MdBlock.Table) {
    sb.append("<table>\n<thead>\n<tr>")
    t.headers.forEach { sb.append("<th>").append(inlineToHtml(it)).append("</th>") }
    sb.append("</tr>\n</thead>\n<tbody>\n")
    for (row in t.rows) {
        sb.append("<tr>")
        for (c in 0 until t.headers.size.coerceAtLeast(row.size)) {
            sb.append("<td>").append(inlineToHtml(row.getOrElse(c) { "" })).append("</td>")
        }
        sb.append("</tr>\n")
    }
    sb.append("</tbody>\n</table>\n")
}

/** Converts a single line of inline Markdown to safe HTML. */
internal fun inlineToHtml(raw: String): String = spansToHtml(parseInline(raw))

/** Like [inlineToHtml] but turns hard line breaks inside a block into `<br>`. */
private fun inlineToHtmlMultiline(raw: String): String =
    raw.split("\n").joinToString("<br>\n") { inlineToHtml(it) }

private fun spansToHtml(spans: List<InlineSpan>): String {
    val sb = StringBuilder()
    for (s in spans) {
        when (s) {
            is InlineSpan.Text -> sb.append(escapeHtml(s.text))
            is InlineSpan.Bold -> sb.append("<strong>").append(spansToHtml(s.children)).append("</strong>")
            is InlineSpan.Italic -> sb.append("<em>").append(spansToHtml(s.children)).append("</em>")
            is InlineSpan.Strike -> sb.append("<del>").append(spansToHtml(s.children)).append("</del>")
            is InlineSpan.Code -> sb.append("<code>").append(escapeHtml(s.text)).append("</code>")
            is InlineSpan.Link ->
                sb.append("<a href=\"").append(escapeAttr(s.url)).append("\">")
                    .append(escapeHtml(s.label)).append("</a>")
            is InlineSpan.Tag ->
                sb.append("<span class=\"tag\">").append(escapeHtml(s.text)).append("</span>")
        }
    }
    return sb.toString()
}

internal fun escapeHtml(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        else -> sb.append(c)
    }
    return sb.toString()
}

internal fun escapeAttr(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;")
        else -> sb.append(c)
    }
    return sb.toString()
}

private fun documentShell(title: String, body: String): String = buildString {
    append("<!DOCTYPE html>\n")
    append("<html lang=\"en\">\n<head>\n")
    append("<meta charset=\"UTF-8\">\n")
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
    append("<title>").append(escapeHtml(title.ifBlank { "Note" })).append("</title>\n")
    append("<style>\n").append(CSS).append("\n</style>\n")
    append("</head>\n<body>\n<main class=\"note\">\n")
    append(body)
    append("</main>\n</body>\n</html>\n")
}

private val CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body {
  margin: 0; padding: 48px 16px; background: #f6f7f9; color: #1f2328;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  line-height: 1.65; font-size: 16px;
}
main.note {
  max-width: 760px; margin: 0 auto; background: #ffffff; padding: 56px 64px;
  border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.08), 0 8px 24px rgba(0,0,0,.06);
}
h1, h2, h3, h4, h5, h6 { line-height: 1.25; font-weight: 700; margin: 1.6em 0 .6em; }
h1 { font-size: 2em; margin-top: 0; } h2 { font-size: 1.5em; } h3 { font-size: 1.25em; }
h4 { font-size: 1.05em; } h5 { font-size: .95em; } h6 { font-size: .85em; color: #57606a; }
p { margin: .7em 0; }
a { color: #2563eb; text-decoration: none; } a:hover { text-decoration: underline; }
code {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace; font-size: .9em;
  background: #eef1f4; padding: .15em .4em; border-radius: 5px;
}
pre {
  background: #1e2127; color: #e6e6e6; padding: 16px 18px; border-radius: 10px; overflow-x: auto;
  line-height: 1.5;
}
pre code { background: transparent; padding: 0; color: inherit; font-size: .88em; }
blockquote {
  margin: 1em 0; padding: .4em 1.1em; border-left: 4px solid #d0d7de; color: #57606a;
  background: #f6f8fa; border-radius: 0 8px 8px 0;
}
ul, ol { padding-left: 1.5em; } li { margin: .25em 0; }
ul.tasks { list-style: none; padding-left: .2em; }
ul.tasks li input { margin-right: .55em; }
hr { border: none; border-top: 1px solid #d0d7de; margin: 2em 0; }
table { border-collapse: collapse; width: 100%; margin: 1.1em 0; font-size: .95em; }
th, td { border: 1px solid #d0d7de; padding: 8px 12px; text-align: left; }
thead th { background: #f3f4f6; font-weight: 700; }
tbody tr:nth-child(even) { background: #fafbfc; }
.tag { color: #0d9488; font-weight: 600; }
.img img { max-width: 100%; height: auto; border-radius: 10px; }
@media (prefers-color-scheme: dark) {
  body { background: #0d1117; color: #e6edf3; }
  main.note { background: #161b22; box-shadow: none; }
  h6 { color: #8b949e; } a { color: #58a6ff; } code { background: #21262d; }
  blockquote { background: #161b22; border-left-color: #30363d; color: #8b949e; }
  th, td { border-color: #30363d; } thead th { background: #21262d; }
  tbody tr:nth-child(even) { background: #1c2230; } hr { border-top-color: #30363d; }
}
""".trim()
