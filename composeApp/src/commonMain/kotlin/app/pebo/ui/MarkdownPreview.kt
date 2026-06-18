package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pebo.export.InlineSpan
import app.pebo.export.parseInline

/**
 * A from-scratch Compose renderer for the raw Markdown editing buffer. The editor stays the source of
 * truth (plain `.md`); this only *displays* a rendered view. Parsing is split into a pure
 * [parseMarkdownBlocks] (unit-tested) and the Composable renderer below.
 */

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<Pair<Int, String>>) : MdBlock
    data class Tasks(val items: List<TaskLine>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class Image(val alt: String, val url: String) : MdBlock
    data object Rule : MdBlock
}

internal data class TaskLine(val checked: Boolean, val text: String)

private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRegex = Regex("""^\s*[-*+]\s+(.*)$""")
private val orderedRegex = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val taskRegex = Regex("""^\s*[-*+]\s+\[([ xX])]\s+(.*)$""")
private val ruleRegex = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")
private val imageOnlyRegex = Regex("""^\s*!\[([^\]]*)]\(([^)]+)\)\s*$""")
private val tableSepRegex = Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)+\|?\s*$""")

/** Pure Markdown → block list. Groups fenced code, contiguous list/quote/table runs. */
internal fun parseMarkdownBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val blocks = ArrayList<MdBlock>()
    var i = 0

    fun splitRow(line: String): List<String> =
        line.trim().trim('|').split("|").map { it.trim() }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Blank line → block separator.
        if (trimmed.isEmpty()) { i++; continue }

        // Fenced code block.
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().ifBlank { null }
            val body = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing fence
            blocks += MdBlock.Code(lang, body.toString())
            continue
        }

        // Horizontal rule.
        if (ruleRegex.matches(line)) { blocks += MdBlock.Rule; i++; continue }

        // Heading.
        val heading = headingRegex.matchEntire(line)
        if (heading != null) {
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        // Standalone image.
        val image = imageOnlyRegex.matchEntire(line)
        if (image != null) {
            blocks += MdBlock.Image(image.groupValues[1], image.groupValues[2].trim())
            i++
            continue
        }

        // GFM table: a header row immediately followed by a separator row.
        if (trimmed.contains('|') && i + 1 < lines.size && tableSepRegex.matches(lines[i + 1])) {
            val headers = splitRow(line)
            val rows = ArrayList<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().contains('|')) {
                rows += splitRow(lines[i])
                i++
            }
            blocks += MdBlock.Table(headers, rows)
            continue
        }

        // Blockquote run.
        if (trimmed.startsWith(">")) {
            val sb = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks += MdBlock.Quote(sb.toString())
            continue
        }

        // Task list run (checked before plain bullets, since it is a stricter match).
        if (taskRegex.matches(line)) {
            val items = ArrayList<TaskLine>()
            while (i < lines.size) {
                val m = taskRegex.matchEntire(lines[i]) ?: break
                items += TaskLine(m.groupValues[1].lowercase() == "x", m.groupValues[2].trim())
                i++
            }
            blocks += MdBlock.Tasks(items)
            continue
        }

        // Ordered list run.
        if (orderedRegex.matches(line)) {
            val items = ArrayList<Pair<Int, String>>()
            while (i < lines.size) {
                val m = orderedRegex.matchEntire(lines[i]) ?: break
                if (taskRegex.matches(lines[i])) break
                items += (m.groupValues[1].toIntOrNull() ?: (items.size + 1)) to m.groupValues[2].trim()
                i++
            }
            blocks += MdBlock.Ordered(items)
            continue
        }

        // Bullet list run.
        if (bulletRegex.matches(line)) {
            val items = ArrayList<String>()
            while (i < lines.size) {
                if (taskRegex.matches(lines[i])) break
                val m = bulletRegex.matchEntire(lines[i]) ?: break
                items += m.groupValues[1].trim()
                i++
            }
            blocks += MdBlock.Bullet(items)
            continue
        }

        // Paragraph: accumulate consecutive plain lines.
        val sb = StringBuilder()
        while (i < lines.size) {
            val l = lines[i]
            val t = l.trim()
            if (t.isEmpty() || t.startsWith("```") || t.startsWith(">") ||
                headingRegex.matches(l) || ruleRegex.matches(l) ||
                bulletRegex.matches(l) || orderedRegex.matches(l) ||
                imageOnlyRegex.matches(l)
            ) break
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(t)
            i++
        }
        if (sb.isNotEmpty()) blocks += MdBlock.Paragraph(sb.toString())
    }
    return blocks
}

private data class InlineColors(
    val code: Color,
    val codeBg: Color,
    val link: Color,
    val tag: Color,
    val highlightBg: Color,
    val muted: Color,
)

/** A warm amber highlight readable on both light and dark surfaces (`==text==`). */
private fun highlightColor(surface: Color): Color =
    if (surface.luminance() < 0.5f) Color(0xFF5C5316).copy(alpha = 0.85f) else Color(0xFFFFF3A3)

/** Renders inline spans (bold/italic/strike/highlight/code/link/image/tag) via the shared parser. */
private fun inlineAnnotated(raw: String, c: InlineColors): AnnotatedString = buildAnnotatedString {
    appendSpans(parseInline(raw), c)
}

private fun AnnotatedString.Builder.appendSpans(spans: List<InlineSpan>, c: InlineColors) {
    for (s in spans) when (s) {
        is InlineSpan.Text -> append(s.text)
        is InlineSpan.Bold -> {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); appendSpans(s.children, c); pop()
        }
        is InlineSpan.Italic -> {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); appendSpans(s.children, c); pop()
        }
        is InlineSpan.Strike -> {
            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); appendSpans(s.children, c); pop()
        }
        is InlineSpan.Highlight -> {
            pushStyle(SpanStyle(background = c.highlightBg)); appendSpans(s.children, c); pop()
        }
        is InlineSpan.Code -> {
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.code, background = c.codeBg))
            append(s.text); pop()
        }
        is InlineSpan.Link -> {
            pushStyle(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline))
            append(s.label); pop()
        }
        is InlineSpan.Image -> {
            pushStyle(SpanStyle(color = c.muted, fontStyle = FontStyle.Italic))
            append(if (s.alt.isNotBlank()) "\uD83D\uDDBC ${s.alt}" else "\uD83D\uDDBC image"); pop()
        }
        is InlineSpan.Tag -> {
            pushStyle(SpanStyle(color = c.tag, fontWeight = FontWeight.Medium)); append(s.text); pop()
        }
    }
}

private val headingSizes = listOf(30.sp, 24.sp, 20.sp, 17.sp, 15.sp, 14.sp)

@Composable
fun MarkdownPreview(
    text: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    collapsed: Set<Int> = emptySet(),
    onToggleFold: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToOrdinal: Int? = null,
    onScrollConsumed: () -> Unit = {},
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val visible = remember(blocks, collapsed) { visibleBlockIndices(blocks, collapsed) }
    val headingBlockIndices = remember(blocks) { blocks.indices.filter { blocks[it] is MdBlock.Heading } }
    val colors = InlineColors(
        code = MaterialTheme.colorScheme.onSurface,
        codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        link = MaterialTheme.colorScheme.primary,
        tag = MaterialTheme.colorScheme.primary,
        highlightBg = highlightColor(MaterialTheme.colorScheme.surface),
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val body = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Scroll the preview to a heading requested from the outline panel.
    LaunchedEffect(scrollToOrdinal, visible) {
        val ord = scrollToOrdinal ?: return@LaunchedEffect
        val blockIndex = headingBlockIndices.getOrNull(ord)
        val row = blockIndex?.let { visible.indexOf(it) } ?: -1
        if (row >= 0) listState.animateScrollToItem(row)
        onScrollConsumed()
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (visible.isEmpty()) {
            item {
                Text(
                    "Nothing to preview yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = muted.copy(alpha = 0.7f),
                )
            }
        }
        items(visible, key = { it }) { bi ->
            when (val block = blocks[bi]) {
                is MdBlock.Heading -> FoldableHeadingRow(
                    text = inlineAnnotated(block.text, colors),
                    fontSize = headingSizes[(block.level - 1).coerceIn(0, 5)],
                    body = body,
                    collapsed = bi in collapsed,
                    foldable = sectionEnd(blocks, bi) > bi + 1,
                    showControls = onToggleFold != null,
                    onToggle = { onToggleFold?.invoke(bi) },
                )
                is MdBlock.Paragraph -> Text(
                    inlineAnnotated(block.text, colors),
                    style = MaterialTheme.typography.bodyLarge,
                    color = body,
                )
                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (item in block.items) BulletRow("•", inlineAnnotated(item, colors), body)
                }
                is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((num, item) in block.items) BulletRow("$num.", inlineAnnotated(item, colors), body)
                }
                is MdBlock.Tasks -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (task in block.items) TaskRow(task, colors, body, muted)
                }
                is MdBlock.Quote -> QuoteBlock(inlineAnnotated(block.text, colors))
                is MdBlock.Code ->
                    if (block.language?.equals("mermaid", ignoreCase = true) == true) {
                        MermaidView(block.code)
                    } else {
                        CodeBlockView(block)
                    }
                is MdBlock.Table -> TableView(block, colors, body)
                is MdBlock.Image -> ImagePlaceholder(block, muted)
                MdBlock.Rule -> Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun BulletRow(marker: String, content: AnnotatedString, body: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Text(content, style = MaterialTheme.typography.bodyLarge, color = body)
    }
}

@Composable
private fun TaskRow(task: TaskLine, colors: InlineColors, body: Color, muted: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (task.checked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (task.checked) "Done" else "To do",
            tint = if (task.checked) MaterialTheme.colorScheme.primary else muted,
            modifier = Modifier.size(20.dp).padding(end = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            inlineAnnotated(task.text, colors),
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.checked) muted else body,
            textDecoration = if (task.checked) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun QuoteBlock(content: AnnotatedString) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.Code) {
    val plain = MaterialTheme.colorScheme.onSurface
    val darkBg = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = remember(darkBg, plain) { codeColors(darkBg, plain) }
    val highlighted = remember(block.code, block.language, colors) {
        buildHighlightedCode(block.code, block.language, colors)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            ),
    ) {
        if (!block.language.isNullOrBlank()) {
            Text(
                block.language.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                highlighted,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun TableView(table: MdBlock.Table, colors: InlineColors, body: Color) {
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val cols = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)

    @Composable
    fun cells(values: List<String>, header: Boolean) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until cols) {
                val v = values.getOrElse(col) { "" }
                Box(
                    Modifier
                        .weight(1f)
                        .border(0.5.dp, border)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        inlineAnnotated(v, colors),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                        color = body,
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp)),
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            cells(table.headers, header = true)
        }
        for (row in table.rows) cells(row, header = false)
    }
}

@Composable
private fun ImagePlaceholder(image: MdBlock.Image, muted: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Image, contentDescription = null, tint = muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                image.alt.ifBlank { "Image" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(image.url, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
