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

    // --- Smart Enter / list continuation ---

    @Test
    fun smartEnterContinuesBullet() {
        val out = MarkdownEditing.smartEnter(tfv("- milk"))
        assertEquals("- milk\n- ", out?.text)
        assertEquals(TextRange(9), out?.selection)
    }

    @Test
    fun smartEnterContinuesIndentedBulletKeepsIndent() {
        val out = MarkdownEditing.smartEnter(tfv("  - milk"))
        assertEquals("  - milk\n  - ", out?.text)
    }

    @Test
    fun smartEnterIncrementsOrdered() {
        val out = MarkdownEditing.smartEnter(tfv("1. first"))
        assertEquals("1. first\n2. ", out?.text)
    }

    @Test
    fun smartEnterContinuesTaskAsUnchecked() {
        val out = MarkdownEditing.smartEnter(tfv("- [x] done"))
        assertEquals("- [x] done\n- [ ] ", out?.text)
    }

    @Test
    fun smartEnterContinuesBlockquote() {
        val out = MarkdownEditing.smartEnter(tfv("> quote"))
        assertEquals("> quote\n> ", out?.text)
    }

    @Test
    fun smartEnterEmptyBulletExitsList() {
        val out = MarkdownEditing.smartEnter(tfv("- "))
        assertEquals("", out?.text)
        assertEquals(TextRange(0), out?.selection)
    }

    @Test
    fun smartEnterEmptyOrderedExitsList() {
        val out = MarkdownEditing.smartEnter(tfv("a\n2. "))
        assertEquals("a\n", out?.text)
    }

    @Test
    fun smartEnterReturnsNullForPlainText() {
        assertEquals(null, MarkdownEditing.smartEnter(tfv("just a paragraph")))
    }

    @Test
    fun smartEnterReturnsNullWhenSelectionNotCollapsed() {
        assertEquals(null, MarkdownEditing.smartEnter(tfv("- milk", 0, 6)))
    }

    @Test
    fun smartEnterSplitsMidItem() {
        // Caret after "ab" in "- abcd" → "- ab\n- cd"
        val out = MarkdownEditing.smartEnter(tfv("- abcd", 4))
        assertEquals("- ab\n- cd", out?.text)
    }

    // --- Indent / outdent ---

    @Test
    fun indentAddsTwoSpacesAtLineStart() {
        val out = MarkdownEditing.indentSelection(tfv("- item", 6))
        assertEquals("  - item", out.text)
        assertEquals(TextRange(8), out.selection)
    }

    @Test
    fun outdentRemovesTwoSpaces() {
        val out = MarkdownEditing.outdentSelection(tfv("    - item", 10))
        assertEquals("  - item", out.text)
    }

    @Test
    fun outdentNoLeadingSpaceIsNoOp() {
        val v = tfv("- item", 6)
        val out = MarkdownEditing.outdentSelection(v)
        assertEquals("- item", out.text)
        assertEquals(v.selection, out.selection)
    }

    @Test
    fun indentSelectionAddsToEveryLine() {
        val out = MarkdownEditing.indentSelection(tfv("a\nb", 0, 3))
        assertEquals("  a\n  b", out.text)
    }
}
