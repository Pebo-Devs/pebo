package app.pebo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineTest {

    @Test
    fun parsesHeadingsWithLevelsTitlesAndOrdinals() {
        val md = """
            # Alpha
            intro text
            ## Beta
            ### Gamma
            # Delta
        """.trimIndent()
        val outline = parseOutline(md)
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Delta"), outline.map { it.title })
        assertEquals(listOf(1, 2, 3, 1), outline.map { it.level })
        assertEquals(listOf(0, 1, 2, 3), outline.map { it.ordinal })
    }

    @Test
    fun ignoresHeadingsInsideFencedCode() {
        val md = """
            # Real
            ```
            # not a heading
            ## also not
            ```
            ## Also real
        """.trimIndent()
        val outline = parseOutline(md)
        assertEquals(listOf("Real", "Also real"), outline.map { it.title })
    }

    @Test
    fun charOffsetPointsAtHashMarker() {
        val md = "intro\n\n# Title\nbody"
        val item = parseOutline(md).single()
        assertEquals(7, item.charOffset)
        assertTrue(md.substring(item.charOffset).startsWith("# Title"))
    }

    @Test
    fun ordinalsAndTitlesMatchMarkdownBlockHeadings() {
        val md = """
            # A
            text
            ## B
            more
            ```kotlin
            # fenced not heading
            ```
            # C
        """.trimIndent()
        val outline = parseOutline(md)
        val blockHeadings = parseMarkdownBlocks(md).filterIsInstance<MdBlock.Heading>()
        assertEquals(blockHeadings.map { it.text }, outline.map { it.title })
        assertEquals(blockHeadings.map { it.level }, outline.map { it.level })
    }

    @Test
    fun emptyAndHeadingLessSourcesYieldNoOutline() {
        assertEquals(emptyList(), parseOutline(""))
        assertEquals(emptyList(), parseOutline("just a paragraph\nand another line"))
    }

    @Test
    fun headingWithTrailingSpacesIsTrimmed() {
        val outline = parseOutline("#   Spaced Title   ")
        assertEquals("Spaced Title", outline.single().title)
        assertEquals(1, outline.single().level)
    }

    private val sample = listOf(
        MdBlock.Heading(1, "A"),   // 0
        MdBlock.Paragraph("a"),    // 1
        MdBlock.Heading(2, "B"),   // 2
        MdBlock.Paragraph("b"),    // 3
        MdBlock.Heading(1, "C"),   // 4
        MdBlock.Paragraph("c"),    // 5
    )

    @Test
    fun sectionEndStopsAtSameOrHigherLevelHeading() {
        assertEquals(4, sectionEnd(sample, 0)) // level-1 A owns until level-1 C
        assertEquals(4, sectionEnd(sample, 2)) // level-2 B owns until level-1 C
        assertEquals(6, sectionEnd(sample, 4)) // level-1 C owns to end
    }

    @Test
    fun sectionEndOnNonHeadingReturnsNext() {
        assertEquals(2, sectionEnd(sample, 1))
    }

    @Test
    fun noCollapseShowsEveryBlock() {
        assertEquals((0..5).toList(), visibleBlockIndices(sample, emptySet()))
    }

    @Test
    fun collapsingTopHeadingHidesItsWholeSection() {
        assertEquals(listOf(0, 4, 5), visibleBlockIndices(sample, setOf(0)))
    }

    @Test
    fun collapsingSubHeadingHidesOnlyItsContent() {
        assertEquals(listOf(0, 1, 2, 4, 5), visibleBlockIndices(sample, setOf(2)))
    }

    @Test
    fun collapsingTwoHeadingsHidesBothSections() {
        assertEquals(listOf(0, 4), visibleBlockIndices(sample, setOf(0, 4)))
    }

    @Test
    fun collapsingParentSubsumesNestedHeading() {
        // Collapsing A (level 1) also hides nested heading B (level 2) and its content.
        val visible = visibleBlockIndices(sample, setOf(0, 2))
        assertEquals(listOf(0, 4, 5), visible)
    }
}
