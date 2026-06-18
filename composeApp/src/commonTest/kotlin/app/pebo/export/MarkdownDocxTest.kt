package app.pebo.export

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownDocxTest {

    @Test
    fun document_is_well_formed_wordprocessing_xml() {
        val xml = markdownToWordDocumentXml("# Hi\n\nbody")
        assertTrue(xml.startsWith("<?xml"), "should declare XML")
        assertContains(xml, "<w:document")
        assertContains(xml, "<w:body>")
        assertContains(xml, "</w:document>")
        assertContains(xml, "<w:sectPr>")
    }

    @Test
    fun heading_run_is_bold_and_sized() {
        val xml = markdownToWordDocumentXml("# Heading")
        assertContains(xml, "<w:b/>")
        assertContains(xml, "<w:sz w:val=\"64\"/>") // h1 = 32pt = 64 half-points
        assertContains(xml, "Heading")
    }

    @Test
    fun inline_formatting_maps_to_run_properties() {
        val xml = markdownToWordDocumentXml("**b** *i* ~~s~~ `c`")
        assertContains(xml, "<w:b/>")
        assertContains(xml, "<w:i/>")
        assertContains(xml, "<w:strike/>")
        assertContains(xml, "w:ascii=\"Consolas\"")
    }

    @Test
    fun highlight_maps_to_word_highlight() {
        val xml = markdownToWordDocumentXml("==note==")
        assertContains(xml, "<w:highlight w:val=\"yellow\"/>")
        assertContains(xml, "note")
    }

    @Test
    fun inline_image_renders_alt_placeholder() {
        val xml = markdownToWordDocumentXml("![a cat](u.png)")
        assertContains(xml, "[Image: a cat]")
    }

    @Test
    fun xml_special_chars_are_escaped() {
        val xml = markdownToWordDocumentXml("a < b & c")
        assertContains(xml, "a &lt; b &amp; c")
        assertFalse(xml.contains("a < b"), "raw '<' must be escaped in body text")
    }

    @Test
    fun table_uses_real_table_markup() {
        val xml = markdownToWordDocumentXml("| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertContains(xml, "<w:tbl>")
        assertContains(xml, "<w:tr>")
        assertContains(xml, "<w:tc>")
        assertContains(xml, "<w:tblBorders>")
    }

    @Test
    fun task_items_use_checkbox_glyphs() {
        val xml = markdownToWordDocumentXml("- [x] done\n- [ ] todo")
        assertContains(xml, "\u2611") // checked box
        assertContains(xml, "\u2610") // empty box
    }

    @Test
    fun package_parts_are_complete() {
        val parts = docxPackageParts("<doc/>").toMap()
        assertEquals(5, parts.size)
        assertTrue("[Content_Types].xml" in parts)
        assertTrue("_rels/.rels" in parts)
        assertTrue("word/_rels/document.xml.rels" in parts)
        assertTrue("word/styles.xml" in parts)
        assertEquals("<doc/>", parts["word/document.xml"])
    }
}
