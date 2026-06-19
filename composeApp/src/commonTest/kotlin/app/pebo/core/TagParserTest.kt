package app.pebo.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TagParserTest {

    @Test
    fun extracts_simple_and_nested_tags() {
        val tags = TagParser.extract("Buy milk #shopping and plan #work/projects/pebo today")
        assertEquals(listOf("shopping", "work/projects/pebo"), tags)
    }

    @Test
    fun ignores_headings_and_double_hash() {
        val tags = TagParser.extract("# Heading line\n## Sub heading\nreal #tag here")
        assertEquals(listOf("tag"), tags)
    }

    @Test
    fun ignores_fenced_code_blocks() {
        val body = "before #keep\n```\ncode with #ignored\n```\nafter #also"
        assertEquals(listOf("keep", "also"), TagParser.extract(body))
    }

    @Test
    fun ignores_inline_code_and_url_fragments() {
        val body = "see `#notatag` and https://x.com/page#section but #real counts"
        assertEquals(listOf("real"), TagParser.extract(body))
    }

    @Test
    fun deduplicates_preserving_order() {
        assertEquals(listOf("a", "b"), TagParser.extract("#a #b #a #b"))
    }
}
