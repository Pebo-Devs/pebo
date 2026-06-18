package app.pebo.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMarkdownTest {

    @Test
    fun plain_text_is_a_single_text_span() {
        assertEquals(listOf(InlineSpan.Text("hello world")), parseInline("hello world"))
    }

    @Test
    fun bold_italic_strike_code_are_recognised() {
        assertEquals(listOf(InlineSpan.Bold(listOf(InlineSpan.Text("b")))), parseInline("**b**"))
        assertEquals(listOf(InlineSpan.Italic(listOf(InlineSpan.Text("i")))), parseInline("*i*"))
        assertEquals(listOf(InlineSpan.Italic(listOf(InlineSpan.Text("u")))), parseInline("_u_"))
        assertEquals(listOf(InlineSpan.Strike(listOf(InlineSpan.Text("s")))), parseInline("~~s~~"))
        assertEquals(listOf(InlineSpan.Code("c")), parseInline("`c`"))
    }

    @Test
    fun link_keeps_label_and_url() {
        assertEquals(
            listOf(InlineSpan.Link("Pebo", "https://pebo.app")),
            parseInline("[Pebo](https://pebo.app)"),
        )
    }

    @Test
    fun tag_includes_leading_hash_and_supports_nesting() {
        assertEquals(listOf(InlineSpan.Tag("#project/pebo")), parseInline("#project/pebo"))
    }

    @Test
    fun bold_can_nest_italic() {
        val spans = parseInline("**bold _and_ more**")
        val bold = spans.single() as InlineSpan.Bold
        assertTrue(bold.children.any { it is InlineSpan.Italic })
    }

    @Test
    fun unterminated_markers_are_literal() {
        assertEquals(listOf(InlineSpan.Text("a * b")), parseInline("a * b"))
        assertEquals(listOf(InlineSpan.Text("100% `done")), parseInline("100% `done"))
    }

    @Test
    fun mid_word_hash_is_not_a_tag() {
        // The '#' is not preceded by whitespace, so it is literal text.
        assertEquals(listOf(InlineSpan.Text("C#")), parseInline("C#"))
    }

    @Test
    fun plain_text_helper_drops_markers() {
        assertEquals("bold and code", parseInline("**bold** and `code`").joinToString("") { it.plainText() })
    }
}
