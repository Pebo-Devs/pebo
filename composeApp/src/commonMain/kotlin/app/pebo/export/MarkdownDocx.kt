package app.pebo.export

import app.pebo.ui.MdBlock
import app.pebo.ui.parseMarkdownBlocks

/**
 * Pure Markdown → Office Open XML (`.docx`) generation. This file produces every **text** part of the
 * package as plain strings (no zip/IO and no external library); the desktop actual simply stores these
 * entries into a ZIP. Word reads a `.docx` as a ZIP of XML parts, so a faithful, dependency-free
 * exporter just needs correct XML.
 *
 * Formatting is applied inline via run properties (`w:rPr`) rather than named styles, so the document
 * renders correctly with only a minimal `styles.xml`.
 */

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/** Half-point font sizes (`w:sz` is in half-points) for body text and headings 1..6. */
private const val BODY_SZ = 22 // 11pt
private val HEADING_SZ = intArrayOf(64, 52, 44, 36, 32, 28) // 32,26,22,18,16,14 pt

/** Generates the `word/document.xml` body for [markdown]. Pure and fully unit-testable. */
fun markdownToWordDocumentXml(markdown: String): String {
    val blocks = parseMarkdownBlocks(markdown)
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
    sb.append("<w:document xmlns:w=\"").append(W_NS).append("\">\n<w:body>\n")
    for (b in blocks) {
        when (b) {
            is MdBlock.Heading -> {
                val sz = HEADING_SZ[(b.level.coerceIn(1, 6)) - 1]
                paragraph(sb, runs = inlineRuns(b.text, RunFmt(bold = true, size = sz)),
                    spacingBefore = 240, spacingAfter = 80)
            }
            is MdBlock.Paragraph ->
                multilineParagraph(sb, b.text, RunFmt())
            is MdBlock.Bullet ->
                b.items.forEach { paragraph(sb, prefixRun("•  ") + inlineRuns(it, RunFmt()), indentLeft = 360) }
            is MdBlock.Ordered ->
                b.items.forEach { (num, txt) ->
                    paragraph(sb, prefixRun("$num.  ") + inlineRuns(txt, RunFmt()), indentLeft = 360)
                }
            is MdBlock.Tasks ->
                b.items.forEach {
                    val box = if (it.checked) "\u2611  " else "\u2610  "
                    paragraph(sb, prefixRun(box) + inlineRuns(it.text, RunFmt()), indentLeft = 360)
                }
            is MdBlock.Quote ->
                multilineParagraph(sb, b.text, RunFmt(italic = true, color = "57606A"), indentLeft = 360)
            is MdBlock.Code ->
                b.code.split("\n").forEach { line ->
                    paragraph(sb, listOf(run(line, RunFmt(mono = true))), shade = "F1F3F5", indentLeft = 120)
                }
            is MdBlock.Table -> table(sb, b)
            is MdBlock.Image ->
                paragraph(sb, inlineRuns(if (b.alt.isNotBlank()) "[Image: ${b.alt}]" else "[Image]", RunFmt(italic = true, color = "57606A")))
            MdBlock.Rule ->
                // A bottom-bordered empty paragraph reads as a horizontal rule.
                sb.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"D0D7DE\"/></w:pBdr></w:pPr></w:p>\n")
        }
    }
    // Section properties close the body (Letter-ish page with 1in margins).
    sb.append("<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>")
    sb.append("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>\n")
    sb.append("</w:body>\n</w:document>\n")
    return sb.toString()
}

private data class RunFmt(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val underline: Boolean = false,
    val color: String? = null,
    val size: Int = BODY_SZ,
)

private fun inlineRuns(raw: String, base: RunFmt): List<String> {
    val out = ArrayList<String>()
    emitSpanRuns(parseInline(raw), base, out)
    return out
}

private fun emitSpanRuns(spans: List<InlineSpan>, fmt: RunFmt, out: MutableList<String>) {
    for (s in spans) when (s) {
        is InlineSpan.Text -> out += run(s.text, fmt)
        is InlineSpan.Bold -> emitSpanRuns(s.children, fmt.copy(bold = true), out)
        is InlineSpan.Italic -> emitSpanRuns(s.children, fmt.copy(italic = true), out)
        is InlineSpan.Strike -> emitSpanRuns(s.children, fmt.copy(strike = true), out)
        is InlineSpan.Code -> out += run(s.text, fmt.copy(mono = true))
        is InlineSpan.Link -> out += run(s.label, fmt.copy(underline = true, color = "2563EB"))
        is InlineSpan.Tag -> out += run(s.text, fmt.copy(color = "0D9488", bold = true))
    }
}

private fun prefixRun(text: String): List<String> = listOf(run(text, RunFmt()))

private fun run(text: String, fmt: RunFmt): String {
    val rpr = StringBuilder("<w:rPr>")
    if (fmt.bold) rpr.append("<w:b/>")
    if (fmt.italic) rpr.append("<w:i/>")
    if (fmt.strike) rpr.append("<w:strike/>")
    if (fmt.underline) rpr.append("<w:u w:val=\"single\"/>")
    if (fmt.mono) rpr.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/>")
    if (fmt.color != null) rpr.append("<w:color w:val=\"").append(fmt.color).append("\"/>")
    rpr.append("<w:sz w:val=\"").append(fmt.size).append("\"/><w:szCs w:val=\"").append(fmt.size).append("\"/>")
    rpr.append("</w:rPr>")
    return "<w:r>$rpr<w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r>"
}

private fun paragraph(
    sb: StringBuilder,
    runs: List<String>,
    indentLeft: Int = 0,
    spacingBefore: Int = 0,
    spacingAfter: Int = 0,
    shade: String? = null,
) {
    sb.append("<w:p>")
    if (indentLeft != 0 || spacingBefore != 0 || spacingAfter != 0 || shade != null) {
        sb.append("<w:pPr>")
        if (shade != null) sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(shade).append("\"/>")
        if (spacingBefore != 0 || spacingAfter != 0)
            sb.append("<w:spacing w:before=\"").append(spacingBefore).append("\" w:after=\"").append(spacingAfter).append("\"/>")
        if (indentLeft != 0) sb.append("<w:ind w:left=\"").append(indentLeft).append("\"/>")
        sb.append("</w:pPr>")
    }
    runs.forEach { sb.append(it) }
    sb.append("</w:p>\n")
}

private fun multilineParagraph(sb: StringBuilder, text: String, fmt: RunFmt, indentLeft: Int = 0) {
    // Render embedded newlines as <w:br/> within a single paragraph.
    val lines = text.split("\n")
    val runs = ArrayList<String>()
    lines.forEachIndexed { idx, line ->
        if (idx > 0) runs += "<w:r><w:br/></w:r>"
        runs += inlineRuns(line, fmt)
    }
    paragraph(sb, runs, indentLeft = indentLeft)
}

private fun table(sb: StringBuilder, t: MdBlock.Table) {
    val cols = t.headers.size.coerceAtLeast(t.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    sb.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/>")
    sb.append("<w:tblBorders>")
    for (edge in listOf("top", "left", "bottom", "right", "insideH", "insideV"))
        sb.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D0D7DE\"/>")
    sb.append("</w:tblBorders></w:tblPr>\n")
    tableRow(sb, t.headers, cols, header = true)
    for (row in t.rows) tableRow(sb, row, cols, header = false)
    sb.append("</w:tbl>\n")
}

private fun tableRow(sb: StringBuilder, cells: List<String>, cols: Int, header: Boolean) {
    sb.append("<w:tr>")
    for (c in 0 until cols) {
        val txt = cells.getOrElse(c) { "" }
        sb.append("<w:tc><w:tcPr>")
        if (header) sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F3F4F6\"/>")
        sb.append("</w:tcPr><w:p>")
        inlineRuns(txt, RunFmt(bold = header)).forEach { sb.append(it) }
        sb.append("</w:p></w:tc>")
    }
    sb.append("</w:tr>\n")
}

internal fun escapeXml(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;")
        '\'' -> sb.append("&apos;")
        '\t' -> sb.append("&#9;")
        else -> if (c.code >= 0x20 || c == '\n') sb.append(c)
    }
    return sb.toString()
}

/**
 * Returns every part of the `.docx` package as `path -> content` (the desktop actual zips these).
 * Bundling the static parts here keeps the entire package definition pure and testable.
 */
fun docxPackageParts(documentXml: String): List<Pair<String, String>> = listOf(
    "[Content_Types].xml" to CONTENT_TYPES,
    "_rels/.rels" to ROOT_RELS,
    "word/_rels/document.xml.rels" to DOCUMENT_RELS,
    "word/styles.xml" to STYLES,
    "word/document.xml" to documentXml,
)

private val CONTENT_TYPES = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>
""".trim()

private val ROOT_RELS = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
""".trim()

private val DOCUMENT_RELS = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
""".trim()

private val STYLES = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault>
    <w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
  <w:style w:type="table" w:default="1" w:styleId="TableGrid"><w:name w:val="Table Grid"/></w:style>
</w:styles>
""".trim()
