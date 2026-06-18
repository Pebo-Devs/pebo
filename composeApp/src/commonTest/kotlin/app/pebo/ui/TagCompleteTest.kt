package app.pebo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagCompleteTest {

    private fun ctx(text: String, caret: Int = text.length) = tagCompletionContext(text, caret)

    @Test
    fun detectsTagBeingTyped() {
        val q = ctx("see #pro")
        assertEquals(TagQuery(start = 4, end = 8, query = "pro"), q)
    }

    @Test
    fun emptyQueryRightAfterHashIsValidAtLineEnd() {
        val q = ctx("note #")
        assertEquals(TagQuery(start = 5, end = 6, query = ""), q)
    }

    @Test
    fun nestedTagKeepsSlashInQuery() {
        val q = ctx("plan #project/pe")
        assertEquals("project/pe", q?.query)
        assertEquals(5, q?.start)
    }

    @Test
    fun midWordHashIsNotATag() {
        assertNull(ctx("email a#b"))
    }

    @Test
    fun headingHashIsNotATag() {
        // Caret inside the heading text.
        assertNull(ctx("# Heading", caret = 9))
        // Caret right after '#', but a space follows -> heading, not a tag.
        assertNull(ctx("# ", caret = 1))
    }

    @Test
    fun doubleHashIsNotATag() {
        assertNull(ctx("## sub", caret = 6))
    }

    @Test
    fun caretInsideCodeFenceIsRejected() {
        val text = "```\n#nope\n```"
        // caret just after '#nope'
        val caret = text.indexOf("nope") + "nope".length
        assertNull(ctx(text, caret))
    }

    @Test
    fun tagAfterClosedFenceIsAllowed() {
        val text = "```\ncode\n```\nthen #ok"
        val q = ctx(text)
        assertEquals("ok", q?.query)
    }

    @Test
    fun spanCoversTagCharsAfterCaret() {
        // Caret between 'pro' and 'ject'; the replaceable span should cover the whole run.
        val text = "#project"
        val q = tagCompletionContext(text, caret = 4)
        assertEquals(0, q?.start)
        assertEquals(8, q?.end)
        assertEquals("pro", q?.query)
    }

    @Test
    fun prefixMatchesRankAboveSubstringMatches() {
        val tags = listOf(
            TagCandidate("approach", 9), // contains "pro" but not as a prefix of name or leaf
            TagCandidate("project", 1),  // prefix match
        )
        val out = rankTagSuggestions("pro", tags)
        // The prefix match wins despite its lower count.
        assertEquals("project", out.first().name)
        assertEquals(listOf("project", "approach"), out.map { it.name })
    }

    @Test
    fun leafPrefixCounts() {
        val tags = listOf(TagCandidate("project/pebo/ui", 2), TagCandidate("design", 5))
        val out = rankTagSuggestions("ui", tags)
        assertEquals(listOf("project/pebo/ui"), out.map { it.name })
    }

    @Test
    fun exactMatchIsDropped() {
        val tags = listOf(TagCandidate("travel", 3))
        assertTrue(rankTagSuggestions("travel", tags).isEmpty())
    }

    @Test
    fun emptyQueryListsByCountDescending() {
        val tags = listOf(
            TagCandidate("a", 1),
            TagCandidate("b", 7),
            TagCandidate("c", 3),
        )
        val out = rankTagSuggestions("", tags)
        assertEquals(listOf("b", "c", "a"), out.map { it.name })
    }

    @Test
    fun honoursLimit() {
        val tags = (1..20).map { TagCandidate("tag$it", it) }
        assertEquals(6, rankTagSuggestions("tag", tags).size)
        assertEquals(3, rankTagSuggestions("tag", tags, limit = 3).size)
    }

    @Test
    fun sameCountSortsByNameAscending() {
        val tags = listOf(TagCandidate("zebra", 2), TagCandidate("apple", 2))
        val out = rankTagSuggestions("", tags)
        assertEquals(listOf("apple", "zebra"), out.map { it.name })
    }
}
