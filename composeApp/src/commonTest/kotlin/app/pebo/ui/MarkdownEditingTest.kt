package app.pebo.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editor edits raw Markdown directly, so these pure transforms are what every toolbar button
 * does. Guarding them guards that fenced code, tables, task lists, links and images are inserted as
 * real, loss-free Markdown.
 */
class MarkdownEditingTest {

    private fun tfv(text: String, start: Int = text.length, end: Int = start) =
        TextFieldValue(text, TextRange(start, end))

    @Test
    fun wrapSelectionAddsMarkers() {
        val out = MarkdownEditing.wrap(tfv("hello", 0, 5), "**")
        assertEquals("**hello**", out.text)
        assertEquals(TextRange(2, 7), out.selection)
    }

    @Test
    fun wrapCollapsedInsertsEmptyMarkersWithCaretInside() {
        val out = MarkdownEditing.wrap(tfv("", 0, 0), "`")
        assertEquals("``", out.text)
        assertEquals(TextRange(1), out.selection)
    }

    @Test
    fun wrapTogglesOffWhenAlreadyWrapped() {
        val out = MarkdownEditing.wrap(tfv("**hello**", 0, 9), "**")
        assertEquals("hello", out.text)
    }

    @Test
    fun toggleHeadingAddsHashes() {
        val out = MarkdownEditing.toggleHeading(tfv("hello", 0), 2)
        assertEquals("## hello", out.text)
    }

    @Test
    fun toggleHeadingSameLevelRemoves() {
        val out = MarkdownEditing.toggleHeading(tfv("## hello", 0), 2)
        assertEquals("hello", out.text)
    }

    @Test
    fun toggleHeadingReplacesLevel() {
        val out = MarkdownEditing.toggleHeading(tfv("# hello", 0), 3)
        assertEquals("### hello", out.text)
    }

    @Test
    fun toggleLinePrefixAddsToEverySelectedLine() {
        val out = MarkdownEditing.toggleLinePrefix(tfv("a\nb", 0, 3), "> ")
        assertEquals("> a\n> b", out.text)
    }

    @Test
    fun toggleLinePrefixRemovesWhenAllPresent() {
        val out = MarkdownEditing.toggleLinePrefix(tfv("> a\n> b", 0, 7), "> ")
        assertEquals("a\nb", out.text)
    }

    @Test
    fun toggleTaskPrefixWorks() {
        val out = MarkdownEditing.toggleLinePrefix(tfv("buy milk", 0), "- [ ] ")
        assertEquals("- [ ] buy milk", out.text)
    }

    @Test
    fun toggleOrderedAddsNumber() {
        val out = MarkdownEditing.toggleOrdered(tfv("first", 0))
        assertEquals("1. first", out.text)
    }

    @Test
    fun insertCodeBlockIsPaddedAndPreserved() {
        val out = MarkdownEditing.insertBlock(tfv("notes", 5), MarkdownEditing.CODE_BLOCK, caretOffsetInBlock = 4)
        assertTrue(out.text.contains("```\n\n```"), "Expected fenced code block, got: ${out.text}")
        assertTrue(out.text.startsWith("notes\n\n"), "Expected blank-line padding, got: ${out.text}")
    }

    @Test
    fun insertTablePreservesPipes() {
        val out = MarkdownEditing.insertBlock(tfv("", 0), MarkdownEditing.TABLE_SKELETON)
        assertTrue(out.text.contains("| --- | --- |"), "Expected GFM table, got: ${out.text}")
    }

    @Test
    fun insertLinkWrapsSelection() {
        val out = MarkdownEditing.insertLink(tfv("Pebo", 0, 4), "https://pebo.app")
        assertEquals("[Pebo](https://pebo.app)", out.text)
    }

    @Test
    fun insertImageEmitsBangSyntax() {
        val out = MarkdownEditing.insertImage(tfv("", 0), "pic.png", "shot")
        assertEquals("![shot](pic.png)", out.text)
    }
}
