package app.pebo.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure text transforms that turn toolbar actions into real Markdown edits on the raw note text.
 *
 * Every function takes and returns a [TextFieldValue] (text + selection) so the editor's source of
 * truth is always plain Markdown — nothing is ever re-encoded through a lossy rich model. This means
 * fenced code blocks, tables, task lists, images and any other Markdown survive verbatim.
 *
 * All offsets are kept consistent so the caret/selection lands somewhere sensible after each edit,
 * which keeps the typing experience smooth.
 */
object MarkdownEditing {

    private fun TextFieldValue.normalizedSelection(): IntRange {
        val s = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val e = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        return s..e
    }

    /** Wrap the selection (or insert empty markers at the caret) with [prefix]/[suffix]. */
    fun wrap(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
        val range = value.normalizedSelection()
        val start = range.first
        val end = range.last
        val text = value.text
        val selected = text.substring(start, end)

        // Toggle off when the selection is already wrapped.
        if (selected.isNotEmpty() && selected.startsWith(prefix) && selected.endsWith(suffix) &&
            selected.length >= prefix.length + suffix.length
        ) {
            val inner = selected.substring(prefix.length, selected.length - suffix.length)
            val newText = text.substring(0, start) + inner + text.substring(end)
            return value.copy(
                text = newText,
                selection = TextRange(start, start + inner.length),
            )
        }

        val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
        val selStart = start + prefix.length
        val selEnd = selStart + selected.length
        return value.copy(
            text = newText,
            selection = if (selected.isEmpty()) TextRange(selStart) else TextRange(selStart, selEnd),
        )
    }

    /** Line range [start, end) for the line containing [index]. */
    private fun lineRange(text: String, index: Int): IntRange {
        val safe = index.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let { if (it < 0 || safe == 0) 0 else it + 1 }
        var end = text.indexOf('\n', safe)
        if (end < 0) end = text.length
        return start..end
    }

    private val headingPrefix = Regex("^(#{1,6})\\s+")

    /**
     * Toggle an ATX heading of [level] (1–6) on the caret's line. Re-applying the same level clears
     * it; a different level replaces it.
     */
    fun toggleHeading(value: TextFieldValue, level: Int): TextFieldValue {
        val text = value.text
        val range = lineRange(text, value.selection.start)
        val line = text.substring(range.first, range.last)
        val existing = headingPrefix.find(line)
        val stripped = if (existing != null) line.substring(existing.range.last + 1) else line
        val hashes = "#".repeat(level.coerceIn(1, 6))
        val sameLevel = existing != null && existing.groupValues[1].length == level
        val newLine = if (sameLevel) stripped else "$hashes $stripped"
        val newText = text.substring(0, range.first) + newLine + text.substring(range.last)
        val caret = range.first + newLine.length
        return value.copy(text = newText, selection = TextRange(caret))
    }

    /**
     * Toggle a per-line [prefix] (e.g. `> `, `- `, `- [ ] `) across every line the selection spans.
     * If all touched lines already start with [prefix] it is removed, otherwise it is added.
     */
    fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val sel = value.normalizedSelection()
        val blockStart = lineRange(text, sel.first).first
        val blockEnd = lineRange(text, sel.last).last
        val block = text.substring(blockStart, blockEnd)
        val lines = block.split('\n')
        val allHave = lines.all { it.isBlank() || it.startsWith(prefix) }
        val rebuilt = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                allHave -> line.removePrefix(prefix)
                else -> prefix + line
            }
        }
        val newText = text.substring(0, blockStart) + rebuilt + text.substring(blockEnd)
        return value.copy(text = newText, selection = TextRange(blockStart, blockStart + rebuilt.length))
    }

    /** Toggle an ordered (`1. `) prefix on the caret's line. */
    fun toggleOrdered(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val range = lineRange(text, value.selection.start)
        val line = text.substring(range.first, range.last)
        val ordered = Regex("^\\d+\\.\\s+")
        val match = ordered.find(line)
        val newLine = if (match != null) line.substring(match.range.last + 1) else "1. $line"
        val newText = text.substring(0, range.first) + newLine + text.substring(range.last)
        val caret = range.first + newLine.length
        return value.copy(text = newText, selection = TextRange(caret))
    }

    /**
     * Insert [block] on its own blank-line-padded lines at the caret, placing the caret at
     * [caretOffsetInBlock] within the inserted text (defaults to its end).
     */
    fun insertBlock(value: TextFieldValue, block: String, caretOffsetInBlock: Int = block.length): TextFieldValue {
        val text = value.text
        val at = value.selection.start.coerceIn(0, text.length)
        val before = text.substring(0, at)
        val after = text.substring(at)
        val leading = when {
            before.isEmpty() -> ""
            before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trailing = when {
            after.isEmpty() -> "\n"
            after.startsWith("\n") -> ""
            else -> "\n"
        }
        val insertion = leading + block + trailing
        val newText = before + insertion + after
        val caret = at + leading.length + caretOffsetInBlock.coerceIn(0, block.length)
        return value.copy(text = newText, selection = TextRange(caret))
    }

    /** Wrap the selection as a Markdown link `[text](url)`, or insert `[](url)` at the caret. */
    fun insertLink(value: TextFieldValue, url: String): TextFieldValue {
        val range = value.normalizedSelection()
        val text = value.text
        val label = text.substring(range.first, range.last)
        val snippet = "[$label]($url)"
        val newText = text.substring(0, range.first) + snippet + text.substring(range.last)
        val caret = if (label.isEmpty()) {
            range.first + 1 // between the empty [] brackets
        } else {
            range.first + snippet.length
        }
        return value.copy(text = newText, selection = TextRange(caret))
    }

    /** Insert an image reference `![alt](url)` at the caret. */
    fun insertImage(value: TextFieldValue, url: String, alt: String = "image"): TextFieldValue {
        val text = value.text
        val at = value.selection.start.coerceIn(0, text.length)
        val snippet = "![$alt]($url)"
        val newText = text.substring(0, at) + snippet + text.substring(at)
        return value.copy(text = newText, selection = TextRange(at + snippet.length))
    }

    const val CODE_BLOCK = "```\n\n```"
    const val TABLE_SKELETON = "| Column | Column |\n| --- | --- |\n| Cell | Cell |"
    const val HORIZONTAL_RULE = "---"
}
