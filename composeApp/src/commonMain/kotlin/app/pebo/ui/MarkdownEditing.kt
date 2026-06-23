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

    private val taskItem = Regex("^(\\s*)([-*+])\\s+\\[[ xX]\\]\\s+")
    private val bulletItem = Regex("^(\\s*)([-*+])\\s+")
    private val orderedItem = Regex("^(\\s*)(\\d+)([.)])\\s+")
    private val quoteItem = Regex("^(\\s*>+\\s?)")

    /**
     * Smart Enter: when the caret sits on a list, task-list, ordered-list or blockquote line, continue
     * the structure on the next line (next bullet / `- [ ] ` / incremented number / `> `). Pressing
     * Enter on an *empty* item instead clears the marker and exits the list. Returns `null` when there
     * is nothing to continue, so the caller lets the platform insert a normal newline.
     */
    fun smartEnter(value: TextFieldValue): TextFieldValue? {
        if (value.selection.start != value.selection.end) return null
        val text = value.text
        val caret = value.selection.start.coerceIn(0, text.length)
        val range = lineRange(text, caret)
        val lineStart = range.first
        val lineEnd = range.last
        val line = text.substring(lineStart, lineEnd)

        val task = taskItem.find(line)
        val bullet = if (task == null) bulletItem.find(line) else null
        val ordered = if (task == null && bullet == null) orderedItem.find(line) else null
        val quote = if (task == null && bullet == null && ordered == null) quoteItem.find(line) else null
        val match = task ?: bullet ?: ordered ?: quote ?: return null

        val markerEnd = lineStart + match.range.last + 1
        if (caret < markerEnd) return null // caret is inside the marker → plain newline

        val content = line.substring(match.range.last + 1)
        if (caret == lineEnd && content.isBlank()) {
            // Empty item: drop the marker and stay on the now-blank line (exit the list).
            val newText = text.substring(0, lineStart) + text.substring(lineEnd)
            return value.copy(text = newText, selection = TextRange(lineStart))
        }

        val continuation = when {
            task != null -> "${task.groupValues[1]}${task.groupValues[2]} [ ] "
            bullet != null -> "${bullet.groupValues[1]}${bullet.groupValues[2]} "
            ordered != null -> {
                val next = (ordered.groupValues[2].toLongOrNull() ?: 0L) + 1L
                "${ordered.groupValues[1]}$next${ordered.groupValues[3]} "
            }
            else -> quote!!.groupValues[1]
        }
        val insertion = "\n" + continuation
        val newText = text.substring(0, caret) + insertion + text.substring(caret)
        return value.copy(text = newText, selection = TextRange(caret + insertion.length))
    }

    private const val INDENT = "  "

    private fun leadingOutdent(line: String): Int {
        var i = 0
        while (i < line.length && i < INDENT.length && line[i] == ' ') i++
        return if (i == 0 && line.startsWith("\t")) 1 else i
    }

    /** Indent the caret's line (or every line the selection spans) by one level (two spaces). */
    fun indentSelection(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val sel = value.normalizedSelection()
        if (sel.first == sel.last) {
            val ls = lineRange(text, sel.first).first
            val newText = text.substring(0, ls) + INDENT + text.substring(ls)
            return value.copy(text = newText, selection = TextRange(value.selection.start + INDENT.length))
        }
        val blockStart = lineRange(text, sel.first).first
        val blockEnd = lineRange(text, sel.last).last
        val block = text.substring(blockStart, blockEnd)
        val rebuilt = block.split('\n').joinToString("\n") { if (it.isEmpty()) it else INDENT + it }
        val newText = text.substring(0, blockStart) + rebuilt + text.substring(blockEnd)
        return value.copy(text = newText, selection = TextRange(blockStart, blockStart + rebuilt.length))
    }

    /** Outdent the caret's line (or every line the selection spans) by one level. */
    fun outdentSelection(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val sel = value.normalizedSelection()
        if (sel.first == sel.last) {
            val lr = lineRange(text, sel.first)
            val line = text.substring(lr.first, lr.last)
            val remove = leadingOutdent(line)
            if (remove == 0) return value
            val newText = text.substring(0, lr.first) + text.substring(lr.first + remove)
            val caret = (value.selection.start - remove).coerceAtLeast(lr.first)
            return value.copy(text = newText, selection = TextRange(caret))
        }
        val blockStart = lineRange(text, sel.first).first
        val blockEnd = lineRange(text, sel.last).last
        val block = text.substring(blockStart, blockEnd)
        val rebuilt = block.split('\n').joinToString("\n") { line -> line.substring(leadingOutdent(line)) }
        val newText = text.substring(0, blockStart) + rebuilt + text.substring(blockEnd)
        return value.copy(text = newText, selection = TextRange(blockStart, blockStart + rebuilt.length))
    }

    const val CODE_BLOCK = "```\n\n```"
    const val TABLE_SKELETON = "| Column | Column |\n| --- | --- |\n| Cell | Cell |"
    const val HORIZONTAL_RULE = "---"
}
