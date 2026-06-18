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
    fun double_underscore_is_bold() {
        assertEquals(listOf(InlineSpan.Bold(listOf(InlineSpan.Text("b")))), parseInline("__b__"))
    }

    @Test
    fun highlight_is_recognised() {
        assertEquals(listOf(InlineSpan.Highlight(listOf(InlineSpan.Text("hot")))), parseInline("==hot=="))
    }

    @Test
    fun highlight_can_nest_inline_styles() {
        val h = parseInline("==a **b**==").single() as InlineSpan.Highlight
        assertTrue(h.children.any { it is InlineSpan.Bold })
    }

    @Test
    fun inline_image_keeps_alt_and_url() {
        assertEquals(
            listOf(InlineSpan.Image("cat", "https://x.test/c.png")),
            parseInline("![cat](https://x.test/c.png)"),
        )
    }

    @Test
    fun inline_image_distinguished_from_link() {
        val spans = parseInline("a ![alt](u.png) [lbl](u2)")
        assertTrue(spans.any { it is InlineSpan.Image })
        assertTrue(spans.any { it is InlineSpan.Link })
    }

    @Test
    fun angle_bracket_autolink() {
        assertEquals(
            listOf(InlineSpan.Link("https://pebo.app", "https://pebo.app")),
            parseInline("<https://pebo.app>"),
        )
    }

    @Test
    fun angle_brackets_without_url_stay_literal() {
        assertEquals(listOf(InlineSpan.Text("a <b> c")), parseInline("a <b> c"))
    }

    @Test
    fun bare_url_is_autolinked() {
        val spans = parseInline("see https://pebo.app now")
        val link = spans.first { it is InlineSpan.Link } as InlineSpan.Link
        assertEquals("https://pebo.app", link.url)
        assertEquals("https://pebo.app", link.label)
    }

    @Test
    fun bare_url_trailing_punctuation_is_excluded() {
        val link = parseInline("(see https://pebo.app).").first { it is InlineSpan.Link } as InlineSpan.Link
        assertEquals("https://pebo.app", link.url)
    }

    @Test
    fun markdown_link_url_is_not_double_autolinked() {
        // The bare-URL pass must not re-trigger inside a [label](url).
        assertEquals(
            listOf(InlineSpan.Link("site", "https://x.test")),
            parseInline("[site](https://x.test)"),
        )
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

    @Test
    fun plain_text_helper_handles_highlight_and_image() {
        assertEquals("hot pic", parseInline("==hot== ![pic](u)").joinToString("") { it.plainText() })
    }
}
