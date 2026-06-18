package app.pebo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Empirical guards for two assumptions the editor relies on:
 *  1. A locally-replicated H1/H2 [SpanStyle] (matching the library's internal heading spans) makes
 *     [RichTextState.toMarkdown] emit `#`/`##`, so headings round-trip.
 *  2. A colour-only span over a `#tag` does NOT leak into the Markdown, so saved `.md` stays clean.
 */
class EditorMarkdownRoundTripTest {

    private val h1 = SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold)

    @Test
    fun headingMarkdownRoundTrips() {
        val state = RichTextState()
        state.setMarkdown("# Hello\n\nBody")
        val md = state.toMarkdown()
        assertTrue(md.contains("# Hello"), "Expected heading to survive round-trip, got: $md")
    }

    @Test
    fun togglingHeadingEmitsHash() {
        val state = RichTextState()
        state.setMarkdown("Hello")
        state.selection = TextRange(0, 5)
        state.toggleSpanStyle(h1)
        val md = state.toMarkdown()
        assertTrue(md.trimStart().startsWith("#"), "Expected '#' after H1 toggle, got: $md")
    }

    @Test
    fun hashtagColourDoesNotPolluteMarkdown() {
        val state = RichTextState()
        state.setMarkdown("Note with #demo tag")
        val color = SpanStyle(color = Color(0xFF7AA2FF))
        val text = state.annotatedString.text
        val idx = text.indexOf("#demo")
        state.addSpanStyle(color, TextRange(idx, idx + "#demo".length))
        val md = state.toMarkdown()
        assertEquals("Note with #demo tag", md.trim(), "Colour span must not alter Markdown, got: $md")
    }
}
