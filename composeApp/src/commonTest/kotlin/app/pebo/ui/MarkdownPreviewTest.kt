package app.pebo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The preview renderer is split so this pure block parser can be guarded directly: it must group
 * fenced code, detect GFM tables, separate task lists from bullets, and read heading levels.
 */
class MarkdownPreviewTest {

    @Test
    fun headingLevelsAreParsed() {
        val blocks = parseMarkdownBlocks("# One\n\n### Three")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Heading(1, "One"), blocks[0])
        assertEquals(MdBlock.Heading(3, "Three"), blocks[1])
    }

    @Test
    fun fencedCodeIsGroupedWithLanguageAndPreservesLines() {
        val src = "before\n\n```kotlin\nfun a() {}\nval b = 1\n```\n\nafter"
        val blocks = parseMarkdownBlocks(src)
        val code = blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("fun a() {}\nval b = 1", code.code)
        // Code body is untouched even though it contains markdown-looking text.
        assertTrue(blocks.first() is MdBlock.Paragraph)
        assertTrue(blocks.last() is MdBlock.Paragraph)
    }

    @Test
    fun fenceWithoutLanguageHasNullLanguage() {
        val code = parseMarkdownBlocks("```\nplain\n```").filterIsInstance<MdBlock.Code>().single()
        assertEquals(null, code.language)
        assertEquals("plain", code.code)
    }

    @Test
    fun mermaidFenceKeepsItsLanguageLabel() {
        val code = parseMarkdownBlocks("```mermaid\ngraph TD\nA-->B\n```")
            .filterIsInstance<MdBlock.Code>().single()
        assertEquals("mermaid", code.language)
        assertEquals("graph TD\nA-->B", code.code)
    }

    @Test
    fun gfmTableIsParsedWithHeadersAndRows() {
        val src = "| Name | Qty |\n| --- | --- |\n| Apple | 3 |\n| Pear | 7 |"
        val table = parseMarkdownBlocks(src).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("Name", "Qty"), table.headers)
        assertEquals(listOf(listOf("Apple", "3"), listOf("Pear", "7")), table.rows)
    }

    @Test
    fun taskListIsSeparateFromBullets() {
        val src = "- [ ] todo\n- [x] done\n- plain bullet"
        val blocks = parseMarkdownBlocks(src)
        val tasks = blocks.filterIsInstance<MdBlock.Tasks>().single()
        assertEquals(2, tasks.items.size)
        assertEquals(TaskLine(false, "todo"), tasks.items[0])
        assertEquals(TaskLine(true, "done"), tasks.items[1])
        val bullets = blocks.filterIsInstance<MdBlock.Bullet>().single()
        assertEquals(listOf("plain bullet"), bullets.items)
    }

    @Test
    fun orderedListKeepsNumbers() {
        val ordered = parseMarkdownBlocks("1. first\n2. second\n3. third")
            .filterIsInstance<MdBlock.Ordered>().single()
        assertEquals(listOf(1 to "first", 2 to "second", 3 to "third"), ordered.items)
    }

    @Test
    fun blockquoteRunIsCollapsed() {
        val quote = parseMarkdownBlocks("> line one\n> line two")
            .filterIsInstance<MdBlock.Quote>().single()
        assertEquals("line one\nline two", quote.text)
    }

    @Test
    fun horizontalRuleIsDetected() {
        assertTrue(parseMarkdownBlocks("---").single() is MdBlock.Rule)
        assertTrue(parseMarkdownBlocks("***").single() is MdBlock.Rule)
    }

    @Test
    fun standaloneImageBecomesImageBlock() {
        val img = parseMarkdownBlocks("![logo](assets/logo.png)")
            .filterIsInstance<MdBlock.Image>().single()
        assertEquals("logo", img.alt)
        assertEquals("assets/logo.png", img.url)
    }

    @Test
    fun consecutiveParagraphLinesJoinIntoOneBlock() {
        val blocks = parseMarkdownBlocks("hello world\nsecond line\n\nnew para")
        val paras = blocks.filterIsInstance<MdBlock.Paragraph>()
        assertEquals(2, paras.size)
        assertEquals("hello world\nsecond line", paras[0].text)
        assertEquals("new para", paras[1].text)
    }

    @Test
    fun blankInputProducesNoBlocks() {
        assertTrue(parseMarkdownBlocks("").isEmpty())
        assertTrue(parseMarkdownBlocks("\n\n   \n").isEmpty())
    }
}
