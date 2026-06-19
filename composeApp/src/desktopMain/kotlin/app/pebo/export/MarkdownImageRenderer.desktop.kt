package app.pebo.export

import app.pebo.ui.MdBlock
import app.pebo.ui.TokenKind
import app.pebo.ui.highlightCode
import app.pebo.ui.parseMarkdownBlocks
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWeight
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.DecorationLineStyle
import org.jetbrains.skia.paragraph.DecorationStyle
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Skiko-backed renderer that rasterizes a note's Markdown into a high-resolution image, plus a
 * dependency-free PDF assembler that paginates that image into pages. Skia ships no PDF backend in
 * skiko-awt, so the PDF embeds the rendered raster (faithful to Preview; text is not selectable — a
 * known v1 trade-off). All drawing reuses the shared block parser, inline parser, and the existing
 * syntax highlighter so the export matches the in-app Preview.
 */

private const val PAGE_W = 1240            // render width in px (~A4 @ 150 dpi)
private const val MARGIN = 64f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val PDF_DPI = 150f           // px → PDF points mapping

// Palette (ARGB).
private const val BG = 0xFFFFFFFF.toInt()
private const val TEXT = 0xFF1F2328.toInt()
private const val HEADING = 0xFF11161B.toInt()
private const val MUTED = 0xFF57606A.toInt()
private const val LINK = 0xFF2563EB.toInt()
private const val TAG = 0xFF0D9488.toInt()
private const val INLINE_CODE = 0xFF8B3FB0.toInt()
private const val CODE_BG = 0xFF1E2127.toInt()
private const val QUOTE_BAR = 0xFFD0D7DE.toInt()
private const val TABLE_BORDER = 0xFFD0D7DE.toInt()
private const val TABLE_HEADER_BG = 0xFFF3F4F6.toInt()
private const val RULE = 0xFFD0D7DE.toInt()
private const val HIGHLIGHT_BG = 0xFFFFF3A3.toInt()

// Font sizes (px).
private const val BODY = 26f
private val HEADING_SIZE = floatArrayOf(46f, 38f, 32f, 28f, 26f, 24f)
private const val CODE = 22f

private val fonts: FontCollection by lazy { FontCollection().setDefaultFontManager(FontMgr.default) }
private val SANS = arrayOf("Segoe UI", "Helvetica Neue", "Arial", "sans-serif")
private val MONO = arrayOf("Consolas", "Menlo", "DejaVu Sans Mono", "monospace")

private data class Fmt(
    val size: Float = BODY,
    val color: Int = TEXT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val mono: Boolean = false,
    val strike: Boolean = false,
    val highlight: Boolean = false,
)

private fun Fmt.textStyle(): TextStyle {
    val ts = TextStyle().setColor(color).setFontSize(size)
    val style = when {
        bold && italic -> FontStyle.BOLD_ITALIC
        bold -> FontStyle.BOLD
        italic -> FontStyle(FontWeight.NORMAL, FontWidth.NORMAL, FontSlant.ITALIC)
        else -> FontStyle.NORMAL
    }
    ts.setFontStyle(style)
    ts.setFontFamilies(if (mono) MONO else SANS)
    if (strike) ts.setDecorationStyle(DecorationStyle(false, false, true, false, color, DecorationLineStyle.SOLID, 1f))
    if (highlight) ts.setBackground(Paint().also { it.color = HIGHLIGHT_BG })
    return ts
}

private fun emitSpans(b: ParagraphBuilder, spans: List<InlineSpan>, f: Fmt) {
    for (s in spans) when (s) {
        is InlineSpan.Text -> { b.pushStyle(f.textStyle()); b.addText(s.text); b.popStyle() }
        is InlineSpan.Bold -> emitSpans(b, s.children, f.copy(bold = true))
        is InlineSpan.Italic -> emitSpans(b, s.children, f.copy(italic = true))
        is InlineSpan.Strike -> emitSpans(b, s.children, f.copy(strike = true))
        is InlineSpan.Highlight -> emitSpans(b, s.children, f.copy(highlight = true, color = TEXT))
        is InlineSpan.Code -> { b.pushStyle(f.copy(mono = true, color = INLINE_CODE).textStyle()); b.addText(s.text); b.popStyle() }
        is InlineSpan.Link -> { b.pushStyle(f.copy(color = LINK).textStyle()); b.addText(s.label); b.popStyle() }
        is InlineSpan.Image -> {
            val label = if (s.alt.isNotBlank()) "\uD83D\uDDBC ${s.alt}" else "\uD83D\uDDBC image"
            b.pushStyle(f.copy(color = MUTED, italic = true).textStyle()); b.addText(label); b.popStyle()
        }
        is InlineSpan.Tag -> { b.pushStyle(f.copy(color = TAG, bold = true).textStyle()); b.addText(s.text); b.popStyle() }
    }
}

private fun paragraph(spans: List<InlineSpan>, f: Fmt, width: Float): Paragraph {
    val builder = ParagraphBuilder(ParagraphStyle(), fonts)
    if (spans.isEmpty()) { builder.pushStyle(f.textStyle()); builder.addText(" "); builder.popStyle() }
    else emitSpans(builder, spans, f)
    return builder.build().also { it.layout(width) }
}

private fun textParagraph(text: String, f: Fmt, width: Float): Paragraph =
    paragraph(parseInline(text), f, width)

/** A measured, positioned drawable. */
private class Item(val height: Float, val draw: (Canvas, Float) -> Unit)

private fun fillPaint(color: Int) = Paint().also { it.color = color; it.mode = PaintMode.FILL }
private fun strokePaint(color: Int, w: Float = 1f) =
    Paint().also { it.color = color; it.mode = PaintMode.STROKE; it.strokeWidth = w }

private fun blockItems(block: MdBlock, attachmentsDir: String?): List<Item> = when (block) {
    is MdBlock.Heading -> {
        val size = HEADING_SIZE[(block.level.coerceIn(1, 6)) - 1]
        val p = textParagraph(block.text, Fmt(size = size, color = HEADING, bold = true), CONTENT_W)
        listOf(Item(18f + p.height + 10f) { c, y -> p.paint(c, MARGIN, y + 18f) })
    }
    is MdBlock.Paragraph -> {
        val p = textParagraph(block.text, Fmt(), CONTENT_W)
        listOf(Item(p.height + 12f) { c, y -> p.paint(c, MARGIN, y) })
    }
    is MdBlock.Bullet -> block.items.map { listItem("•   $it") }
    is MdBlock.Ordered -> block.items.map { (n, t) -> listItem("$n.   $t") }
    is MdBlock.Tasks -> block.items.map { listItem(if (it.checked) "\u2611   ${it.text}" else "\u2610   ${it.text}") }
    is MdBlock.Quote -> {
        val p = paragraph(parseInline(block.text), Fmt(color = MUTED, italic = true), CONTENT_W - 28f)
        val h = p.height + 16f
        listOf(Item(h + 8f) { c, y ->
            c.drawRect(Rect.makeXYWH(MARGIN, y, 5f, h), fillPaint(QUOTE_BAR))
            p.paint(c, MARGIN + 20f, y + 8f)
        })
    }
    is MdBlock.Code -> listOf(codeItem(block))
    is MdBlock.Table -> listOf(tableItem(block))
    is MdBlock.Image -> listOf(imageItem(block, attachmentsDir))
    MdBlock.Rule -> listOf(Item(28f) { c, y ->
        c.drawLine(MARGIN, y + 14f, PAGE_W - MARGIN, y + 14f, strokePaint(RULE))
    })
}

private fun listItem(text: String): Item {
    val p = textParagraph(text, Fmt(), CONTENT_W - 18f)
    return Item(p.height + 8f) { c, y -> p.paint(c, MARGIN + 18f, y) }
}

private fun codeItem(block: MdBlock.Code): Item {
    val pad = 16f
    val builder = ParagraphBuilder(ParagraphStyle(), fonts)
    for (tok in highlightCode(block.code, block.language)) {
        builder.pushStyle(Fmt(size = CODE, color = codeColor(tok.kind), mono = true, italic = tok.kind == TokenKind.Comment).textStyle())
        builder.addText(tok.text)
        builder.popStyle()
    }
    val p = builder.build().also { it.layout(CONTENT_W - 2 * pad) }
    val h = p.height + 2 * pad
    return Item(h + 12f) { c, y ->
        c.drawRRect(RRect.makeLTRB(MARGIN, y, PAGE_W - MARGIN, y + h, 10f), fillPaint(CODE_BG))
        p.paint(c, MARGIN + pad, y + pad)
    }
}

private fun codeColor(kind: TokenKind): Int = when (kind) {
    TokenKind.Keyword -> 0xFFC678DD.toInt()
    TokenKind.Str -> 0xFF98C379.toInt()
    TokenKind.Comment -> 0xFF7F848E.toInt()
    TokenKind.Number -> 0xFFD19A66.toInt()
    TokenKind.Function -> 0xFF61AFEF.toInt()
    TokenKind.Punctuation -> 0xFFABB2BF.toInt()
    TokenKind.Plain -> 0xFFE6E6E6.toInt()
}

private fun tableItem(block: MdBlock.Table): Item {
    val cols = block.headers.size.coerceAtLeast(block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val colW = CONTENT_W / cols
    val cellPad = 10f
    val rows = listOf(block.headers) + block.rows

    data class Cell(val p: Paragraph)
    val grid = rows.map { row -> (0 until cols).map { Cell(textParagraph(row.getOrElse(it) { "" }, Fmt(), colW - 2 * cellPad)) } }
    val rowHeights = grid.map { row -> (row.maxOf { it.p.height }) + 2 * cellPad }
    val total = rowHeights.sum()

    return Item(total + 14f) { c, y ->
        var ry = y
        for ((ri, row) in grid.withIndex()) {
            val rh = rowHeights[ri]
            if (ri == 0) c.drawRect(Rect.makeXYWH(MARGIN, ry, CONTENT_W, rh), fillPaint(TABLE_HEADER_BG))
            for ((ci, cell) in row.withIndex()) {
                val cx = MARGIN + ci * colW
                cell.p.paint(c, cx + cellPad, ry + cellPad)
                c.drawRect(Rect.makeXYWH(cx, ry, colW, rh), strokePaint(TABLE_BORDER))
            }
            ry += rh
        }
    }
}

private fun imageItem(block: MdBlock.Image, attachmentsDir: String?): Item {
    val img = runCatching {
        val f = resolveImage(block.url, attachmentsDir) ?: return@runCatching null
        Image.makeFromEncoded(f.readBytes())
    }.getOrNull()
    if (img == null) {
        val p = textParagraph(if (block.alt.isNotBlank()) "[Image: ${block.alt}]" else "[Image]", Fmt(color = MUTED, italic = true), CONTENT_W)
        return Item(p.height + 12f) { c, y -> p.paint(c, MARGIN, y) }
    }
    val drawW = minOf(CONTENT_W, img.width.toFloat())
    val scale = drawW / img.width
    val drawH = img.height * scale
    return Item(drawH + 16f) { c, y ->
        c.drawImageRect(img, Rect.makeXYWH(MARGIN, y + 8f, drawW, drawH))
    }
}

private fun resolveImage(url: String, attachmentsDir: String?): File? {
    if (url.startsWith("http://", true) || url.startsWith("https://", true)) return null
    val clean = url.removePrefix("file://")
    val direct = File(clean)
    if (direct.isAbsolute && direct.exists()) return direct
    if (attachmentsDir != null) {
        val rel = File(attachmentsDir, clean)
        if (rel.exists()) return rel
    }
    return direct.takeIf { it.exists() }
}

/** Lays the whole note out into one tall raster surface (caller must close it). */
private fun renderNoteSurface(markdown: String, attachmentsDir: String?): Surface {
    val blocks = parseMarkdownBlocks(markdown)
    val items = blocks.flatMap { blockItems(it, attachmentsDir) }
    val contentH = items.fold(0f) { acc, it -> acc + it.height }
    val totalH = (MARGIN * 2 + contentH).toInt().coerceAtLeast((MARGIN * 2).toInt() + 1)
    val surface = Surface.makeRasterN32Premul(PAGE_W, totalH)
    val canvas = surface.canvas
    canvas.clear(BG)
    var y = MARGIN
    for (it in items) { it.draw(canvas, y); y += it.height }
    return surface
}

/** Renders the note to a single tall image and encodes it. Used for JPG/PNG export. */
internal fun renderNoteToImageBytes(markdown: String, attachmentsDir: String?, format: EncodedImageFormat): ByteArray? {
    val surface = renderNoteSurface(markdown, attachmentsDir)
    return try {
        val image = surface.makeImageSnapshot()
        val quality = if (format == EncodedImageFormat.JPEG) 92 else 100
        image.encodeToData(format, quality)?.bytes
    } finally {
        surface.close()
    }
}

/** Renders the note, slices it into pages, and assembles a PDF embedding each page as a JPEG. */
internal fun renderNoteToPdfBytes(markdown: String, attachmentsDir: String?): ByteArray? {
    val surface = renderNoteSurface(markdown, attachmentsDir)
    return try {
        val fullH = surface.makeImageSnapshot().height
        val pageH = (PAGE_W * 1.4142f).toInt() // A4 portrait ratio
        val pages = ArrayList<JpegPage>()
        var top = 0
        while (top < fullH) {
            val bottom = minOf(top + pageH, fullH)
            val slice = surface.makeImageSnapshot(IRect.makeLTRB(0, top, PAGE_W, bottom))
            val jpeg = slice?.encodeToData(EncodedImageFormat.JPEG, 92)?.bytes ?: return null
            pages += JpegPage(jpeg, PAGE_W, bottom - top)
            top = bottom
        }
        if (pages.isEmpty()) return null
        buildImagePdf(pages)
    } finally {
        surface.close()
    }
}

private class JpegPage(val jpeg: ByteArray, val wpx: Int, val hpx: Int)

/** Assembles a minimal PDF 1.4 with one image (DCTDecode/JPEG) per page. No external dependency. */
private fun buildImagePdf(pages: List<JpegPage>): ByteArray {
    val out = ByteArrayOutputStream()
    val offsets = ArrayList<Int>() // offsets[objNum-1]
    fun ascii(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
    fun beginObj(): Int { offsets.add(out.size()); return offsets.size }

    ascii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

    // obj 1: Catalog
    beginObj(); ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

    // obj 2: Pages
    val kids = pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
    beginObj(); ascii("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

    for ((i, page) in pages.withIndex()) {
        val ptW = page.wpx * 72f / PDF_DPI
        val ptH = page.hpx * 72f / PDF_DPI
        val pageObj = 3 + i * 3
        val contentObj = pageObj + 1
        val imageObj = pageObj + 2

        // Page
        beginObj()
        ascii(
            "$pageObj 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(ptW)} ${fmt(ptH)}] " +
                "/Resources << /XObject << /Im0 $imageObj 0 R >> >> /Contents $contentObj 0 R >>\nendobj\n",
        )

        // Contents
        val content = "q\n${fmt(ptW)} 0 0 ${fmt(ptH)} 0 0 cm\n/Im0 Do\nQ\n"
        val contentBytes = content.toByteArray(Charsets.ISO_8859_1)
        beginObj()
        ascii("$contentObj 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
        out.write(contentBytes)
        ascii("endstream\nendobj\n")

        // Image XObject
        beginObj()
        ascii(
            "$imageObj 0 obj\n<< /Type /XObject /Subtype /Image /Width ${page.wpx} /Height ${page.hpx} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n",
        )
        out.write(page.jpeg)
        ascii("\nendstream\nendobj\n")
    }

    // xref
    val xrefPos = out.size()
    val count = offsets.size
    ascii("xref\n0 ${count + 1}\n")
    ascii("0000000000 65535 f \n")
    for (off in offsets) ascii(off.toString().padStart(10, '0') + " 00000 n \n")
    ascii("trailer\n<< /Size ${count + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
    return out.toByteArray()
}

private fun fmt(v: Float): String {
    val r = (v * 100f).toInt() / 100f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
}
