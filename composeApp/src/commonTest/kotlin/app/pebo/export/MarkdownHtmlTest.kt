package app.pebo.export

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownHtmlTest {

    private fun html(md: String) = markdownToHtml("Title", md)

    @Test
    fun wraps_in_a_self_contained_document() {
        val out = html("# Hello")
        assertTrue(out.startsWith("<!DOCTYPE html>"), "should be a full HTML document")
        assertContains(out, "<style>")
        assertContains(out, "<title>Title</title>")
        assertContains(out, "<h1>Hello</h1>")
    }

    @Test
    fun escapes_html_special_chars_in_text() {
        val out = html("A < B & C > D")
        assertContains(out, "A &lt; B &amp; C &gt; D")
        assertFalse(out.contains("<p>A < B"), "raw angle brackets must be escaped")
    }

    @Test
    fun inline_styles_become_tags() {
        val out = html("This is **bold**, *italic*, ~~gone~~ and `code`.")
        assertContains(out, "<strong>bold</strong>")
        assertContains(out, "<em>italic</em>")
        assertContains(out, "<del>gone</del>")
        assertContains(out, "<code>code</code>")
    }

    @Test
    fun links_and_tags_render() {
        val out = html("See [site](https://x.test) about #travel")
        assertContains(out, "<a href=\"https://x.test\">site</a>")
        assertContains(out, "<span class=\"tag\">#travel</span>")
    }

    @Test
    fun code_block_is_escaped_and_keeps_language() {
        val out = html("```kotlin\nval x = a < b\n```")
        assertContains(out, "<pre><code class=\"language-kotlin\">")
        assertContains(out, "val x = a &lt; b")
    }

    @Test
    fun task_list_renders_checkboxes() {
        val out = html("- [x] done\n- [ ] todo")
        assertContains(out, "<input type=\"checkbox\" disabled checked> done")
        assertContains(out, "<input type=\"checkbox\" disabled> todo")
    }

    @Test
    fun table_renders_thead_and_tbody() {
        val out = html("| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertContains(out, "<table>")
        assertContains(out, "<th>A</th>")
        assertContains(out, "<td>1</td>")
    }

    @Test
    fun blockquote_and_rule_render() {
        val out = html("> quoted\n\n---")
        assertContains(out, "<blockquote>quoted</blockquote>")
        assertContains(out, "<hr>")
    }

    @Test
    fun highlight_becomes_mark() {
        val out = html("This is ==important== text")
        assertContains(out, "<mark>important</mark>")
    }

    @Test
    fun inline_image_becomes_img_tag() {
        val out = html("Look ![a cat](https://x.test/c.png) here")
        assertContains(out, "<img class=\"inline-img\" src=\"https://x.test/c.png\" alt=\"a cat\">")
    }

    @Test
    fun double_underscore_and_autolinks_render() {
        val out = html("__strong__ and https://pebo.app and <https://x.test>")
        assertContains(out, "<strong>strong</strong>")
        assertContains(out, "<a href=\"https://pebo.app\">https://pebo.app</a>")
        assertContains(out, "<a href=\"https://x.test\">https://x.test</a>")
    }

    @Test
    fun mark_styles_are_present_in_css() {
        assertContains(html("x"), "mark {")
    }
}
