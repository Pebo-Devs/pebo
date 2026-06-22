package app.pebo.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import app.pebo.ui.MdBlock
import app.pebo.ui.TokenKind
import app.pebo.ui.highlightCode
import app.pebo.ui.parseMarkdownBlocks
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android (android.graphics) port of the desktop Skiko note renderer. It rasterizes a note's
 * Markdown into a tall bitmap for JPG/PNG export and paginates that bitmap into an
 * [android.graphics.pdf.PdfDocument] for PDF export. It reuses the shared block parser
 * ([parseMarkdownBlocks]), inline parser ([parseInline]) and syntax highlighter ([highlightCode])
 * so the output matches the in-app Preview, mirroring `MarkdownImageRenderer.desktop.kt`.
 */

private const val PAGE_W = 1240            // render width in px (~A4 @ 150 dpi)
private const val MARGIN = 64f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val PDF_DPI = 150f           // px -> PDF points mapping

// Palette (ARGB) — identical to the desktop renderer.
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

private val SANS: Typeface = Typeface.SANS_SERIF
private val SANS_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
private val MONO: Typeface = Typeface.MONOSPACE

private const val SPAN_FLAG = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

private data class Sty(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val mono: Boolean = false,
    val strike: Boolean = false,
    val highlight: Boolean = false,
    val color: Int = TEXT,
)

private fun appendStyled(sb: SpannableStringBuilder, text: String, s: Sty) {
    if (text.isEmpty()) return
    val start = sb.length
    sb.append(text)
    val end = sb.length
    val styleFlag = when {
        s.bold && s.italic -> Typeface.BOLD_ITALIC
        s.bold -> Typeface.BOLD
        s.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    if (styleFlag != Typeface.NORMAL) sb.setSpan(StyleSpan(styleFlag), start, end, SPAN_FLAG)
    if (s.mono) sb.setSpan(TypefaceSpan("monospace"), start, end, SPAN_FLAG)
    sb.setSpan(ForegroundColorSpan(s.color), start, end, SPAN_FLAG)
    if (s.strike) sb.setSpan(StrikethroughSpan(), start, end, SPAN_FLAG)
    if (s.highlight) sb.setSpan(BackgroundColorSpan(HIGHLIGHT_BG), start, end, SPAN_FLAG)
}

private fun emitSpans(sb: SpannableStringBuilder, spans: List<InlineSpan>, s: Sty) {
    for (sp in spans) when (sp) {
        is InlineSpan.Text -> appendStyled(sb, sp.text, s)
        is InlineSpan.Bold -> emitSpans(sb, sp.children, s.copy(bold = true))
        is InlineSpan.Italic -> emitSpans(sb, sp.children, s.copy(italic = true))
        is InlineSpan.Strike -> emitSpans(sb, sp.children, s.copy(strike = true))
        is InlineSpan.Highlight -> emitSpans(sb, sp.children, s.copy(highlight = true, color = TEXT))
        is InlineSpan.Code -> appendStyled(sb, sp.text, s.copy(mono = true, color = INLINE_CODE))
        is InlineSpan.Link -> appendStyled(sb, sp.label, s.copy(color = LINK))
        is InlineSpan.Image -> {
            val label = if (sp.alt.isNotBlank()) "\uD83D\uDDBC ${sp.alt}" else "\uD83D\uDDBC image"
            appendStyled(sb, label, s.copy(color = MUTED, italic = true))
        }
        is InlineSpan.Tag -> appendStyled(sb, sp.text, s.copy(color = TAG, bold = true))
    }
}

private fun basePaint(size: Float, color: Int, typeface: Typeface): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

private fun layoutOf(text: CharSequence, paint: TextPaint, width: Float): StaticLayout {
    val w = width.toInt().coerceAtLeast(1)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
}

private fun inlineLayout(text: String, base: Sty, paint: TextPaint, width: Float): StaticLayout {
    val sb = SpannableStringBuilder()
    val spans = parseInline(text)
    if (spans.isEmpty()) appendStyled(sb, " ", base) else emitSpans(sb, spans, base)
    return layoutOf(sb, paint, width)
}

/** A measured, positioned drawable. */
private class Item(val height: Float, val draw: (Canvas, Float) -> Unit)

private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

private fun strokePaint(color: Int, w: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = w
}

private fun blockItems(block: MdBlock, attachmentsDir: String?): List<Item> = when (block) {
    is MdBlock.Heading -> {
        val size = HEADING_SIZE[(block.level.coerceIn(1, 6)) - 1]
        val paint = basePaint(size, HEADING, SANS_BOLD)
        val l = inlineLayout(block.text, Sty(color = HEADING, bold = true), paint, CONTENT_W)
        listOf(Item(18f + l.height + 10f) { c, y -> drawLayout(c, l, MARGIN, y + 18f) })
    }
    is MdBlock.Paragraph -> {
        val paint = basePaint(BODY, TEXT, SANS)
        val l = inlineLayout(block.text, Sty(), paint, CONTENT_W)
        listOf(Item(l.height + 12f) { c, y -> drawLayout(c, l, MARGIN, y) })
    }
    is MdBlock.Bullet -> block.items.map { listItem("\u2022   $it") }
    is MdBlock.Ordered -> block.items.map { (n, t) -> listItem("$n.   $t") }
    is MdBlock.Tasks -> block.items.map { listItem(if (it.checked) "\u2611   ${it.text}" else "\u2610   ${it.text}") }
    is MdBlock.Quote -> {
        val paint = basePaint(BODY, MUTED, Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC))
        val l = inlineLayout(block.text, Sty(color = MUTED, italic = true), paint, CONTENT_W - 28f)
        val h = l.height + 16f
        listOf(Item(h + 8f) { c, y ->
            c.drawRect(MARGIN, y, MARGIN + 5f, y + h, fillPaint(QUOTE_BAR))
            drawLayout(c, l, MARGIN + 20f, y + 8f)
        })
    }
    is MdBlock.Code -> listOf(codeItem(block))
    is MdBlock.Table -> listOf(tableItem(block))
    is MdBlock.Image -> listOf(imageItem(block, attachmentsDir))
    MdBlock.Rule -> listOf(Item(28f) { c, y ->
        c.drawLine(MARGIN, y + 14f, PAGE_W - MARGIN, y + 14f, strokePaint(RULE))
    })
}

private fun drawLayout(c: Canvas, layout: StaticLayout, x: Float, y: Float) {
    c.save()
    c.translate(x, y)
    layout.draw(c)
    c.restore()
}

private fun listItem(text: String): Item {
    val paint = basePaint(BODY, TEXT, SANS)
    val l = inlineLayout(text, Sty(), paint, CONTENT_W - 18f)
    return Item(l.height + 8f) { c, y -> drawLayout(c, l, MARGIN + 18f, y) }
}

private fun codeItem(block: MdBlock.Code): Item {
    val pad = 16f
    val paint = basePaint(CODE, 0xFFE6E6E6.toInt(), MONO)
    val sb = SpannableStringBuilder()
    for (tok in highlightCode(block.code, block.language)) {
        val start = sb.length
        sb.append(tok.text)
        sb.setSpan(ForegroundColorSpan(codeColor(tok.kind)), start, sb.length, SPAN_FLAG)
        if (tok.kind == TokenKind.Comment) sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, SPAN_FLAG)
    }
    val l = layoutOf(sb, paint, CONTENT_W - 2 * pad)
    val h = l.height + 2 * pad
    return Item(h + 12f) { c, y ->
        val rect = RectF(MARGIN, y, PAGE_W - MARGIN, y + h)
        c.drawRoundRect(rect, 10f, 10f, fillPaint(CODE_BG))
        drawLayout(c, l, MARGIN + pad, y + pad)
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
    val paint = basePaint(BODY, TEXT, SANS)

    val grid = rows.map { row ->
        (0 until cols).map { inlineLayout(row.getOrElse(it) { "" }, Sty(), paint, colW - 2 * cellPad) }
    }
    val rowHeights = grid.map { row -> (row.maxOf { it.height }) + 2 * cellPad }
    val total = rowHeights.sum()

    return Item(total + 14f) { c, y ->
        var ry = y
        for ((ri, row) in grid.withIndex()) {
            val rh = rowHeights[ri]
            if (ri == 0) c.drawRect(MARGIN, ry, MARGIN + CONTENT_W, ry + rh, fillPaint(TABLE_HEADER_BG))
            for ((ci, cell) in row.withIndex()) {
                val cx = MARGIN + ci * colW
                drawLayout(c, cell, cx + cellPad, ry + cellPad)
                c.drawRect(cx, ry, cx + colW, ry + rh, strokePaint(TABLE_BORDER))
            }
            ry += rh
        }
    }
}

private fun imageItem(block: MdBlock.Image, attachmentsDir: String?): Item {
    val bmp = runCatching {
        val f = resolveImage(block.url, attachmentsDir) ?: return@runCatching null
        BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()
    if (bmp == null) {
        val paint = basePaint(BODY, MUTED, Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC))
        val label = if (block.alt.isNotBlank()) "[Image: ${block.alt}]" else "[Image]"
        val l = inlineLayout(label, Sty(color = MUTED, italic = true), paint, CONTENT_W)
        return Item(l.height + 12f) { c, y -> drawLayout(c, l, MARGIN, y) }
    }
    val drawW = minOf(CONTENT_W, bmp.width.toFloat())
    val scale = drawW / bmp.width
    val drawH = bmp.height * scale
    return Item(drawH + 16f) { c, y ->
        c.drawBitmap(bmp, null, RectF(MARGIN, y + 8f, MARGIN + drawW, y + 8f + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
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

/** Lays the whole note out into one tall bitmap. */
private fun renderNoteBitmap(markdown: String, attachmentsDir: String?): Bitmap {
    val blocks = parseMarkdownBlocks(markdown)
    val items = blocks.flatMap { blockItems(it, attachmentsDir) }
    val contentH = items.fold(0f) { acc, it -> acc + it.height }
    val totalH = (MARGIN * 2 + contentH).toInt().coerceAtLeast((MARGIN * 2).toInt() + 1)
    val bitmap = Bitmap.createBitmap(PAGE_W, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(BG)
    var y = MARGIN
    for (it in items) { it.draw(canvas, y); y += it.height }
    return bitmap
}

/** Renders the note to a single tall image and encodes it. Used for JPG/PNG export. */
internal fun renderNoteToImageBytes(markdown: String, attachmentsDir: String?, jpeg: Boolean): ByteArray? {
    val bitmap = renderNoteBitmap(markdown, attachmentsDir)
    return try {
        val out = ByteArrayOutputStream()
        val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val quality = if (jpeg) 92 else 100
        if (!bitmap.compress(format, quality, out)) null else out.toByteArray()
    } finally {
        bitmap.recycle()
    }
}

/** Renders the note, slices it into A4 pages, and assembles a PDF. */
internal fun renderNoteToPdfBytes(markdown: String, attachmentsDir: String?): ByteArray? {
    val bitmap = renderNoteBitmap(markdown, attachmentsDir)
    val doc = PdfDocument()
    return try {
        val fullH = bitmap.height
        val pageHpx = (PAGE_W * 1.4142f).toInt() // A4 portrait ratio
        val scale = 72f / PDF_DPI
        var top = 0
        var pageNum = 1
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        while (top < fullH) {
            val bottom = minOf(top + pageHpx, fullH)
            val sliceHpx = bottom - top
            val pageWpts = (PAGE_W * scale).toInt().coerceAtLeast(1)
            val pageHpts = (sliceHpx * scale).toInt().coerceAtLeast(1)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWpts, pageHpts, pageNum).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawColor(BG)
            page.canvas.drawBitmap(
                bitmap,
                Rect(0, top, PAGE_W, bottom),
                RectF(0f, 0f, pageWpts.toFloat(), pageHpts.toFloat()),
                bmpPaint,
            )
            doc.finishPage(page)
            top = bottom
            pageNum++
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        out.toByteArray()
    } catch (t: Throwable) {
        null
    } finally {
        doc.close()
        bitmap.recycle()
    }
}
