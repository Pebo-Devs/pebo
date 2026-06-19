package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Document outline ("Outline with focus"). Pure heading extraction + the section-fold math, plus the
 * Compose panel that renders a clickable table of contents. Lives in commonMain so every KMP target
 * gets the same behaviour. The pure parts ([parseOutline], [sectionEnd], [visibleBlockIndices]) are
 * unit-tested; the renderers are thin shells over them.
 */

private val outlineHeadingRegex = Regex("""^(#{1,6})\s+(.*)$""")

/**
 * A single heading in a note's outline. [ordinal] is the heading's position among all headings and
 * matches the order of [MdBlock.Heading] emitted by [parseMarkdownBlocks] (both skip fenced code),
 * so it can be used to scroll the rendered preview. [charOffset] is the index of the heading's `#`
 * in the raw source, used to move the caret in the source editor.
 */
internal data class OutlineItem(
    val level: Int,
    val title: String,
    val charOffset: Int,
    val ordinal: Int,
)

/** Pure: extract the heading outline from raw Markdown, ignoring headings inside fenced code blocks. */
internal fun parseOutline(source: String): List<OutlineItem> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val items = ArrayList<OutlineItem>()
    var offset = 0
    var inFence = false
    var ordinal = 0
    for (line in lines) {
        if (line.trim().startsWith("```")) {
            inFence = !inFence
            offset += line.length + 1
            continue
        }
        if (!inFence) {
            val m = outlineHeadingRegex.matchEntire(line)
            if (m != null) {
                items += OutlineItem(
                    level = m.groupValues[1].length,
                    title = m.groupValues[2].trim(),
                    charOffset = offset,
                    ordinal = ordinal++,
                )
            }
        }
        offset += line.length + 1
    }
    return items
}

/**
 * The exclusive end index of the section owned by the heading at [headingIndex]: every block after it
 * up to (but not including) the next heading whose level is the same or higher. Returns
 * `headingIndex + 1` when the block is not a heading or owns nothing.
 */
internal fun sectionEnd(blocks: List<MdBlock>, headingIndex: Int): Int {
    val h = blocks.getOrNull(headingIndex) as? MdBlock.Heading ?: return headingIndex + 1
    var j = headingIndex + 1
    while (j < blocks.size) {
        val b = blocks[j]
        if (b is MdBlock.Heading && b.level <= h.level) break
        j++
    }
    return j
}

/** Pure: original block indices that stay visible given a set of collapsed heading indices. */
internal fun visibleBlockIndices(blocks: List<MdBlock>, collapsed: Set<Int>): List<Int> {
    if (collapsed.isEmpty()) return blocks.indices.toList()
    val hidden = BooleanArray(blocks.size)
    for (idx in collapsed) {
        if (idx in blocks.indices && blocks[idx] is MdBlock.Heading) {
            val end = sectionEnd(blocks, idx)
            for (k in (idx + 1) until end) hidden[k] = true
        }
    }
    return blocks.indices.filter { !hidden[it] }
}

/** Left rail listing the note's headings; clicking one navigates the editor to that section. */
@Composable
internal fun OutlinePanel(
    items: List<OutlineItem>,
    activeOrdinal: Int?,
    onPick: (OutlineItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxHeight()
            .width(244.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)),
    ) {
        Text(
            "OUTLINE",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        )
        if (items.isEmpty()) {
            Text(
                "No headings yet. Start a line with # to build your outline.",
                style = MaterialTheme.typography.bodySmall,
                color = muted.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(items, key = { it.ordinal }) { item ->
                OutlineRow(
                    item = item,
                    active = item.ordinal == activeOrdinal,
                    primary = primary,
                    muted = muted,
                    onPick = onPick,
                )
            }
        }
    }
}

@Composable
private fun OutlineRow(
    item: OutlineItem,
    active: Boolean,
    primary: Color,
    muted: Color,
    onPick: (OutlineItem) -> Unit,
) {
    val indent = (12 + (item.level - 1).coerceIn(0, 5) * 14).dp
    Box(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(item) }
            .background(if (active) primary.copy(alpha = 0.14f) else Color.Transparent)
            .padding(start = indent, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            item.title.ifBlank { "Untitled" },
            style = if (item.level <= 1) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = when {
                active -> primary
                item.level <= 1 -> MaterialTheme.colorScheme.onSurface
                else -> muted
            },
            fontWeight = if (item.level <= 1) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A foldable heading row used in the preview: a chevron that toggles its section plus heading text. */
@Composable
internal fun FoldableHeadingRow(
    text: AnnotatedString,
    fontSize: TextUnit,
    body: Color,
    collapsed: Boolean,
    foldable: Boolean,
    showControls: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showControls && foldable) {
            Icon(
                if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                contentDescription = if (collapsed) "Expand section" else "Collapse section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable { onToggle() },
            )
            Spacer(Modifier.width(6.dp))
        } else if (showControls) {
            Spacer(Modifier.width(28.dp))
        }
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = body,
            modifier = Modifier.weight(1f),
        )
        if (collapsed) {
            Text(
                "…",
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
