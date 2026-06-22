package app.pebo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Live, Obsidian-style decoration of raw Markdown *source*. The text characters are never changed
 * (offsets map 1:1 via [OffsetMapping.Identity]), so the editor keeps editing plain `.md` — this is
 * only colour/weight/size painted on top. That makes it impossible to lose content: every element,
 * including fenced code, tables, task lists, images and mermaid blocks, stays exactly as typed.
 */
class MarkdownVisualTransformation(
    private val tag: Color,
    private val marker: Color,
    private val codeText: Color,
    private val codeBg: Color,
    private val quote: Color,
    private val link: Color,
    private val codeFont: FontFamily = FontFamily.Monospace,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = runCatching { decorate(text.text) }.getOrElse { AnnotatedString(text.text) }
        return TransformedText(annotated, OffsetMapping.Identity)
    }

    private fun headingSize(level: Int): TextUnit = when (level) {
        1 -> 1.9.em
        2 -> 1.55.em
        3 -> 1.3.em
        4 -> 1.15.em
        5 -> 1.05.em
        else -> 1.0.em
    }

    private fun decorate(raw: String): AnnotatedString = buildAnnotatedString {
        val codeRanges = fencedCodeRanges(raw)
        fun inCode(index: Int): Boolean = codeRanges.any { index in it }

        // Render bullet-list markers (-, *, +) as a tinted "•" glyph. The swap is 1:1 in
        // length so OffsetMapping.Identity stays exact and the on-disk `.md` keeps its plain markers.
        val bulletPositions = ArrayList<Int>()
        val display = bulletDisplay(raw, ::inCode, bulletPositions)
        append(display)
        if (raw.isEmpty()) return@buildAnnotatedString

        // Fenced code blocks: monospace + tinted background across the whole region.
        for (r in codeRanges) {
            safeStyle(r.first, r.last + 1, SpanStyle(fontFamily = codeFont, color = codeText, background = codeBg))
        }

        // Line-anchored constructs: headings and blockquotes.
        var lineStart = 0
        for (line in raw.split('\n')) {
            val lineEnd = lineStart + line.length
            if (!inCode(lineStart)) {
                val heading = headingRegex.find(line)
                if (heading != null) {
                    val level = heading.groupValues[1].length
                    safeStyle(lineStart, lineEnd, SpanStyle(fontSize = headingSize(level), fontWeight = FontWeight.Bold))
                    safeStyle(lineStart, lineStart + heading.groupValues[1].length, SpanStyle(color = marker))
                } else if (blockquoteRegex.containsMatchIn(line)) {
                    safeStyle(lineStart, lineEnd, SpanStyle(color = quote, fontStyle = FontStyle.Italic))
                }
            }
            lineStart = lineEnd + 1
        }

        // Inline spans (skipped inside fenced code so e.g. `#include` isn't mistaken for a tag).
        applyInline(raw, boldRegex, ::inCode) { m ->
            safeStyle(m.range.first, m.range.last + 1, SpanStyle(fontWeight = FontWeight.Bold))
            dimMarker(m.range.first, m.range.first + 2)
            dimMarker(m.range.last - 1, m.range.last + 1)
        }
        applyInline(raw, strikeRegex, ::inCode) { m ->
            safeStyle(m.range.first, m.range.last + 1, SpanStyle(textDecoration = TextDecoration.LineThrough))
            dimMarker(m.range.first, m.range.first + 2)
            dimMarker(m.range.last - 1, m.range.last + 1)
        }
        applyInline(raw, italicRegex, ::inCode) { m ->
            safeStyle(m.range.first, m.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic))
            dimMarker(m.range.first, m.range.first + 1)
            dimMarker(m.range.last, m.range.last + 1)
        }
        applyInline(raw, inlineCodeRegex, ::inCode) { m ->
            safeStyle(m.range.first, m.range.last + 1, SpanStyle(fontFamily = codeFont, color = codeText, background = codeBg))
        }
        applyInline(raw, linkRegex, ::inCode) { m ->
            val labelRange = m.groups[1]?.range
            if (labelRange != null) {
                safeStyle(labelRange.first, labelRange.last + 1, SpanStyle(color = link, textDecoration = TextDecoration.Underline))
                dimMarker(labelRange.last + 1, m.range.last + 1) // ](url)
                dimMarker(m.range.first, labelRange.first)        // [
            }
        }
        applyInline(raw, hashtagRegex, ::inCode) { m ->
            safeStyle(m.range.first, m.range.last + 1, SpanStyle(color = tag, fontWeight = FontWeight.Medium))
        }

        // Accent the swapped bullet glyphs (applied last so nothing overrides their tint).
        for (p in bulletPositions) {
            safeStyle(p, p + 1, SpanStyle(color = tag, fontWeight = FontWeight.Bold))
        }
    }

    /**
     * Returns a render string identical in length to [raw] with each plain bullet-list marker
     * (`-`/`*`/`+` followed by a space, but not task-list `- [ ]` items or `---` rules) replaced by a
     * `•`. Replaced positions are reported in [out] so the caller can tint them. Length is preserved,
     * so [OffsetMapping.Identity] stays valid and the source text is never mutated.
     */
    private fun bulletDisplay(raw: String, inCode: (Int) -> Boolean, out: MutableList<Int>): String {
        if (raw.isEmpty()) return raw
        val chars = raw.toCharArray()
        var lineStart = 0
        for (line in raw.split('\n')) {
            if (!inCode(lineStart)) {
                val m = listMarkerRegex.find(line)
                if (m != null) {
                    val markerIdx = lineStart + m.groupValues[1].length
                    if (markerIdx in chars.indices) {
                        chars[markerIdx] = '\u2022'
                        out.add(markerIdx)
                    }
                }
            }
            lineStart += line.length + 1
        }
        return chars.concatToString()
    }

    private fun fencedCodeRanges(raw: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var offset = 0
        var openStart = -1
        for (line in raw.split('\n')) {
            val isFence = line.trimStart().startsWith("```")
            if (isFence) {
                if (openStart < 0) {
                    openStart = offset
                } else {
                    ranges.add(openStart..(offset + line.length - 1).coerceAtLeast(openStart))
                    openStart = -1
                }
            }
            offset += line.length + 1
        }
        if (openStart >= 0) ranges.add(openStart..(raw.length - 1).coerceAtLeast(openStart))
        return ranges
    }

    private inline fun applyInline(
        raw: String,
        regex: Regex,
        inCode: (Int) -> Boolean,
        block: (MatchResult) -> Unit,
    ) {
        for (m in regex.findAll(raw)) {
            if (!inCode(m.range.first)) block(m)
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.dimMarker(start: Int, end: Int) =
        safeStyle(start, end, SpanStyle(color = marker))

    private fun androidx.compose.ui.text.AnnotatedString.Builder.safeStyle(start: Int, end: Int, style: SpanStyle) {
        if (start in 0..end && end >= start) addStyle(style, start, end)
    }

    private companion object {
        val headingRegex = Regex("^(#{1,6})\\s+")
        val blockquoteRegex = Regex("^\\s*>")
        val listMarkerRegex = Regex("^(\\s*)[-*+]\\s+(?!\\[[ xX]\\])")
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        val strikeRegex = Regex("~~(.+?)~~")
        val italicRegex = Regex("(?<![*\\w])[*_](?![*_\\s])([^*_\\n]+?)(?<![*_\\s])[*_](?![*\\w])")
        val inlineCodeRegex = Regex("`([^`\\n]+?)`")
        val linkRegex = Regex("\\[([^\\]\\n]+)\\]\\(([^)\\n]+)\\)")
    }
}

/** Shared with the editor: matches `#tag` and nested `#a/b` tags, not `# ` headings. */
internal val hashtagRegex = Regex("(?<=^|\\s)#[A-Za-z0-9_][A-Za-z0-9_/-]*")
