package app.pebo.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlightTest {

    private fun roundTrips(code: String, lang: String?) {
        val joined = highlightCode(code, lang).joinToString("") { it.text }
        assertEquals(code, joined, "tokens must reconstruct the source exactly ($lang)")
    }

    private fun kinds(code: String, lang: String?) =
        highlightCode(code, lang).associate { it.text to it.kind }

    @Test
    fun emptyCodeYieldsNoTokens() {
        assertTrue(highlightCode("", "kotlin").isEmpty())
    }

    @Test
    fun reconstructsSourceForManyLanguages() {
        roundTrips("fun main() { val x = 42 }", "kotlin")
        roundTrips("def f(x):\n    return x # done", "python")
        roundTrips("const y = `a${'$'}{b}c`; // note", "javascript")
        roundTrips("SELECT * FROM t WHERE a = 'x';", "sql")
        roundTrips("{\n  \"a\": true,\n  \"b\": 1.5\n}", "json")
        roundTrips("package main\nfunc main() {}\n", "go")
        roundTrips("/* block */ int n = 0x1F;", "c")
    }

    @Test
    fun classifiesKotlinKeywordsAndCalls() {
        val k = kinds("fun main() {", "kotlin")
        assertEquals(TokenKind.Keyword, k["fun"])
        assertEquals(TokenKind.Function, k["main"])
    }

    @Test
    fun numbersAreClassified() {
        assertEquals(TokenKind.Number, kinds("val n = 42", "kotlin")["42"])
        assertEquals(TokenKind.Number, kinds("x = 0xFF", "c")["0xFF"])
        assertEquals(TokenKind.Number, kinds("d = 1.5", "python")["1.5"])
    }

    @Test
    fun lineCommentRunsToEndOfLine() {
        val toks = highlightCode("a // tail\nb", "kotlin")
        val comment = toks.first { it.kind == TokenKind.Comment }
        assertEquals("// tail", comment.text)
        // The newline and following code are not swallowed.
        assertTrue(toks.any { it.text.contains("b") })
    }

    @Test
    fun hashIsCommentInPythonButNotInC() {
        assertEquals(TokenKind.Comment, highlightCode("# hi", "python").first().kind)
        // In C, '#' is punctuation, not a comment, so "hi" stays separate.
        val c = highlightCode("# hi", "c")
        assertEquals(TokenKind.Punctuation, c.first { it.text == "#" }.kind)
    }

    @Test
    fun stringWithEscapeIsOneToken() {
        val toks = highlightCode("s = \"a\\\"b\"", "kotlin")
        val str = toks.first { it.kind == TokenKind.Str }
        assertEquals("\"a\\\"b\"", str.text)
    }

    @Test
    fun blockCommentSpansMultipleLines() {
        val toks = highlightCode("/* one\ntwo */ x", "java")
        val comment = toks.first { it.kind == TokenKind.Comment }
        assertEquals("/* one\ntwo */", comment.text)
    }

    @Test
    fun jsonBooleanIsKeywordAndKeyIsString() {
        val k = kinds("{\"a\": true}", "json")
        assertEquals(TokenKind.Keyword, k["true"])
        assertEquals(TokenKind.Str, k["\"a\""])
    }

    @Test
    fun unknownLanguageStillReconstructsAndFindsKeywords() {
        roundTrips("if (x) return y;", null)
        assertEquals(TokenKind.Keyword, kinds("if (x) return y;", null)["if"])
    }

    @Test
    fun annotatedStringPreservesText() {
        val colors = CodeColors(
            keyword = Color.Red, str = Color.Green, comment = Color.Gray, number = Color.Blue,
            function = Color.Cyan, punctuation = Color.Magenta, plain = Color.Black,
        )
        val code = "fun main() { println(\"hi\") } // c"
        val annotated = buildHighlightedCode(code, "kotlin", colors)
        assertEquals(code, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun codeColorsDifferByBackground() {
        val dark = codeColors(darkBackground = true, plain = Color.White)
        val light = codeColors(darkBackground = false, plain = Color.Black)
        assertTrue(dark.keyword != light.keyword)
        assertEquals(Color.White, dark.plain)
        assertEquals(Color.Black, light.plain)
    }
}
