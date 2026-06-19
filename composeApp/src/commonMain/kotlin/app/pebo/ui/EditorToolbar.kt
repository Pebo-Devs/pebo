package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bear-style formatting bar that edits the raw Markdown source directly via [MarkdownEditing], so
 * every element — including fenced code, tables, task lists and images — is inserted as real, fully
 * round-trippable Markdown. Heading/list/quote buttons light up when the caret is already inside one.
 */
@Composable
fun EditorToolbar(
    value: TextFieldValue,
    onApply: (TextFieldValue) -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = value.text
    val caret = value.selection.start.coerceIn(0, text.length)
    val lineStart = if (caret == 0) 0 else text.lastIndexOf('\n', caret - 1) + 1
    val lineEnd = text.indexOf('\n', caret).let { if (it < 0) text.length else it }
    val line = if (lineStart <= lineEnd) text.substring(lineStart, lineEnd) else ""

    val isH1 = line.startsWith("# ")
    val isH2 = line.startsWith("## ")
    val isH3 = line.startsWith("### ")
    val isQuote = line.trimStart().startsWith(">")
    val isTask = line.startsWith("- [")
    val isBullet = line.startsWith("- ") && !isTask
    val isNumbered = Regex("^\\d+\\.\\s").containsMatchIn(line)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextToolButton("H1", active = isH1) { onApply(MarkdownEditing.toggleHeading(value, 1)) }
        TextToolButton("H2", active = isH2) { onApply(MarkdownEditing.toggleHeading(value, 2)) }
        TextToolButton("H3", active = isH3) { onApply(MarkdownEditing.toggleHeading(value, 3)) }
        ToolDivider()
        IconToolButton(Icons.Filled.FormatBold, "Bold") { onApply(MarkdownEditing.wrap(value, "**")) }
        IconToolButton(Icons.Filled.FormatItalic, "Italic") { onApply(MarkdownEditing.wrap(value, "*")) }
        IconToolButton(Icons.Filled.FormatStrikethrough, "Strikethrough") { onApply(MarkdownEditing.wrap(value, "~~")) }
        IconToolButton(Icons.Filled.Code, "Inline code") { onApply(MarkdownEditing.wrap(value, "`")) }
        ToolDivider()
        IconToolButton(Icons.Filled.FormatListBulleted, "Bulleted list", isBullet) {
            onApply(MarkdownEditing.toggleLinePrefix(value, "- "))
        }
        IconToolButton(Icons.Filled.FormatListNumbered, "Numbered list", isNumbered) {
            onApply(MarkdownEditing.toggleOrdered(value))
        }
        IconToolButton(Icons.Outlined.CheckBox, "Task list", isTask) {
            onApply(MarkdownEditing.toggleLinePrefix(value, "- [ ] "))
        }
        IconToolButton(Icons.Filled.FormatQuote, "Quote", isQuote) {
            onApply(MarkdownEditing.toggleLinePrefix(value, "> "))
        }
        ToolDivider()
        IconToolButton(Icons.Filled.DataObject, "Code block") {
            onApply(MarkdownEditing.insertBlock(value, MarkdownEditing.CODE_BLOCK, caretOffsetInBlock = 4))
        }
        IconToolButton(Icons.Filled.TableChart, "Table") {
            onApply(MarkdownEditing.insertBlock(value, MarkdownEditing.TABLE_SKELETON))
        }
        IconToolButton(Icons.Filled.HorizontalRule, "Divider") {
            onApply(MarkdownEditing.insertBlock(value, MarkdownEditing.HORIZONTAL_RULE))
        }
        ToolDivider()
        IconToolButton(Icons.Filled.Link, "Link", onClick = onLink)
        IconToolButton(Icons.Filled.Image, "Image", onClick = onImage)
    }
}

@Composable
private fun IconToolButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TextToolButton(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = tint,
        )
    }
}

@Composable
private fun ToolDivider() {
    Spacer(Modifier.width(3.dp))
    Box(
        Modifier
            .height(18.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
    Spacer(Modifier.width(3.dp))
}
