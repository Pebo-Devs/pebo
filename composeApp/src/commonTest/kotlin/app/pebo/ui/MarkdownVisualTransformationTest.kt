package app.pebo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownVisualTransformationTest {

    private val transform = MarkdownVisualTransformation(
        tag = Color(0xFF4C8DFF),
        marker = Color(0x80808080),
        codeText = Color(0xFFCFD8DC),
        codeBg = Color(0xFF26282D),
        quote = Color(0xFF9AA0A6),
        link = Color(0xFF4C8DFF),
    )

    private fun render(src: String): String =
        transform.filter(AnnotatedString(src)).text.text

    @Test
    fun dashBulletBecomesGlyphAndLengthIsPreserved() {
        val src = "- milk\n- eggs"
        val out = render(src)
        assertEquals("\u2022 milk\n\u2022 eggs", out)
        assertEquals(src.length, out.length, "offset mapping must stay 1:1")
    }

    @Test
    fun starAndPlusMarkersAlsoBecomeGlyphs() {
        assertEquals("\u2022 a", render("* a"))
        assertEquals("\u2022 b", render("+ b"))
    }

    @Test
    fun indentedChildBulletsAreConverted() {
        val src = "- parent\n  - child"
        assertEquals("\u2022 parent\n  \u2022 child", render(src))
    }

    @Test
    fun uncheckedTaskKeepsBracketsCheckedTaskShowsGlyph() {
        val src = "- [ ] todo\n- [x] done"
        // Display only: unchecked stays "[ ]", a completed item's inner x renders as ✓. The swap is
        // 1:1 so the on-disk source is never mutated (OffsetMapping.Identity).
        val out = render(src)
        assertEquals("- [ ] todo\n- [\u2713] done", out)
        assertEquals(src.length, out.length, "offset mapping must stay 1:1")
    }

    @Test
    fun capitalCheckedTaskAlsoShowsGlyph() {
        assertEquals("- [\u2713] done", render("- [X] done"))
    }

    @Test
    fun orderedMarkerDoesNotChangeText() {
        val src = "1. first\n2. second"
        assertEquals(src, render(src))
        assertEquals(src.length, render(src).length)
    }

    @Test
    fun horizontalRuleIsNotTreatedAsBullet() {
        assertEquals("---", render("---"))
        assertEquals("***", render("***"))
        assertEquals("___", render("___"))
    }

    @Test
    fun boldTextIsNotMistakenForABullet() {
        val src = "**bold** text"
        assertEquals(src, render(src))
    }

    @Test
    fun bulletInsideFencedCodeIsLeftAlone() {
        val src = "```\n- not a bullet\n```"
        assertEquals(src, render(src))
    }

    @Test
    fun emptyAndPlainTextAreUnchanged() {
        assertEquals("", render(""))
        assertEquals("just a sentence", render("just a sentence"))
    }

    @Test
    fun lengthIsAlwaysPreservedAcrossMixedContent() {
        val src = buildString {
            append("# Heading\n")
            append("- one\n")
            append("  * two\n")
            append("- [ ] task\n")
            append("normal **bold** line\n")
            append("```\n- fenced\n```\n")
            append("> quote")
        }
        assertEquals(src.length, render(src).length)
    }
}
