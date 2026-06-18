package app.pebo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/** A known tag offered as an autocomplete suggestion, with its usage count for ranking. */
internal data class TagCandidate(val name: String, val count: Int)

/**
 * The span of a `#tag` token currently being typed under the caret. [start] points at the `#`,
 * [end] at the character after the tag run, and [query] is the text typed so far (after the `#`,
 * up to the caret) used to filter suggestions.
 */
internal data class TagQuery(val start: Int, val end: Int, val query: String)

private fun isTagChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '/' || c == '-' || c == '_'

/**
 * Decides whether the caret sits inside a `#tag` the user is typing, and if so returns the token
 * span + query. Pure and platform-free so it can be unit-tested.
 *
 * Rules (Bear-compatible): the `#` must start a tag — preceded by start-of-text, whitespace, or an
 * opening `(`/`[`/`>` — never another `#` (that is a heading) and never mid-word. The caret must be
 * outside fenced code blocks. An empty query (caret right after `#`) is valid and lists all tags.
 */
internal fun tagCompletionContext(text: String, caret: Int): TagQuery? {
    if (caret < 0 || caret > text.length) return null

    var left = caret
    while (left > 0 && isTagChar(text[left - 1])) left--
    val hashIdx = left - 1
    if (hashIdx < 0 || text[hashIdx] != '#') return null

    if (hashIdx > 0) {
        val prev = text[hashIdx - 1]
        if (!prev.isWhitespace() && prev != '(' && prev != '[' && prev != '>') return null
    }

    // Reject if the caret is inside a fenced code block (odd count of ``` fences before the '#').
    var fences = 0
    var scan = 0
    while (true) {
        val f = text.indexOf("```", scan)
        if (f < 0 || f >= hashIdx) break
        fences++
        scan = f + 3
    }
    if (fences % 2 == 1) return null

    var right = caret
    while (right < text.length && isTagChar(text[right])) right++

    val query = text.substring(left, caret)
    // A lone '#' immediately followed by a space is a heading marker, not a tag.
    if (query.isEmpty() && caret < text.length && text[caret].isWhitespace()) return null
    return TagQuery(hashIdx, right, query)
}

/**
 * Ranks [tags] for a [query]: prefix matches (on the full path or the leaf segment) come before
 * substring matches, each ordered by usage count then name. An exact full match is dropped (there is
 * nothing left to complete). Pure and unit-tested.
 */
internal fun rankTagSuggestions(query: String, tags: List<TagCandidate>, limit: Int = 6): List<TagCandidate> {
    val q = query.lowercase()
    val prefix = ArrayList<TagCandidate>()
    val contains = ArrayList<TagCandidate>()
    for (t in tags) {
        val name = t.name.lowercase()
        if (q.isNotEmpty() && name == q) continue
        val leaf = name.substringAfterLast('/')
        when {
            q.isEmpty() -> prefix.add(t)
            name.startsWith(q) || leaf.startsWith(q) -> prefix.add(t)
            name.contains(q) -> contains.add(t)
        }
    }
    val cmp = compareByDescending<TagCandidate> { it.count }.thenBy { it.name.lowercase() }
    prefix.sortWith(cmp)
    contains.sortWith(cmp)
    return (prefix + contains).take(limit)
}

/**
 * A caret-anchored autocomplete dropdown for tags. It renders in a non-focusable [Popup] so the
 * editor keeps keyboard focus; navigation/acceptance is driven by the editor's key handler, and a
 * click also accepts. Positions itself just below the caret, flipping above when there is no room.
 */
@Composable
internal fun TagCompletionPopup(
    anchorLeft: Float,
    anchorTop: Float,
    anchorBottom: Float,
    suggestions: List<TagCandidate>,
    selectedIndex: Int,
    onPick: (TagCandidate) -> Unit,
) {
    val provider = remember(anchorLeft, anchorTop, anchorBottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                var x = anchorLeft.roundToInt()
                if (x + popupContentSize.width > windowSize.width) {
                    x = windowSize.width - popupContentSize.width - 8
                }
                if (x < 8) x = 8
                var y = anchorBottom.roundToInt() + 6
                if (y + popupContentSize.height > windowSize.height) {
                    y = anchorTop.roundToInt() - popupContentSize.height - 6
                }
                if (y < 0) y = 0
                return IntOffset(x, y)
            }
        }
    }

    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(min = 210.dp, max = 340.dp),
        ) {
            Column(Modifier.padding(vertical = 5.dp)) {
                suggestions.forEachIndexed { i, cand ->
                    val selected = i == selectedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(cand) }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "#",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            cand.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 2.dp),
                        )
                        if (cand.count > 0) {
                            Text(
                                cand.count.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
