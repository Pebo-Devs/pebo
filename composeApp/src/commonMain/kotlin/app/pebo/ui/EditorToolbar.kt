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
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Heading styles replicated to match the rich-editor library's internal H1/H2 spans exactly
 * (`SpanStyle` is a data class, so equal field values compare equal and the Markdown encoder emits
 * `#`/`##`). The library's own constants are `internal`, so we mirror their values here.
 */
private val H1Style = SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold)
private val H2Style = SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold)

/**
 * Bear-style formatting bar. Buttons toggle live markdown styling on [state] and light up when the
 * caret sits inside matching formatting.
 */
@Composable
fun EditorToolbar(
    state: RichTextState,
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = state.currentSpanStyle
    val isBold = (span.fontWeight?.weight ?: 400) >= 600
    val isItalic = span.fontStyle == FontStyle.Italic
    val isStrike = span.textDecoration?.contains(TextDecoration.LineThrough) == true
    val isH1 = span.fontSize == H1Style.fontSize
    val isH2 = span.fontSize == H2Style.fontSize

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextToolButton("H1", active = isH1) { state.toggleSpanStyle(H1Style) }
        TextToolButton("H2", active = isH2) { state.toggleSpanStyle(H2Style) }
        ToolDivider()
        IconToolButton(Icons.Filled.FormatBold, "Bold", isBold) {
            state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        IconToolButton(Icons.Filled.FormatItalic, "Italic", isItalic) {
            state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        IconToolButton(Icons.Filled.FormatStrikethrough, "Strikethrough", isStrike) {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }
        IconToolButton(Icons.Filled.Code, "Code", state.isCodeSpan) { state.toggleCodeSpan() }
        ToolDivider()
        IconToolButton(Icons.Filled.FormatListBulleted, "Bulleted list", state.isUnorderedList) {
            state.toggleUnorderedList()
        }
        IconToolButton(Icons.Filled.FormatListNumbered, "Numbered list", state.isOrderedList) {
            state.toggleOrderedList()
        }
        ToolDivider()
        IconToolButton(Icons.Filled.Link, "Link", state.isLink, onClick = onLink)
    }
}

@Composable
private fun IconToolButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent
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
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent
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
