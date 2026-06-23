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

    private var cachedInput: String? = null
    private var cachedOutput: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val input = text.text
        // The framework re-invokes filter on every layout pass (cursor blink, selection, scroll,
        // focus) — not just on text change. Decoration depends only on the raw string (colours are
        // fixed per instance), so memoising the last result keeps those passes free and confines the
        // regex work to genuine edits.
        cachedOutput?.let { if (cachedInput == input) return it }
        val annotated = runCatching { decorate(input) }.getOrElse { AnnotatedString(input) }
        val result = TransformedText(annotated, OffsetMapping.Identity)
        cachedInput = input
        cachedOutput = result
        return result
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

        // Render bullet-list markers (-, *, +) as a tinted "•" glyph, and turn completed task items
        // `- [x]` into a "✓". Both swaps are 1:1 in length so OffsetMapping.Identity stays exact and the
        // on-disk `.md` keeps its plain markers.
        val bulletPositions = ArrayList<Int>()
        val tasks = taskLines(raw, ::inCode)
        val display = buildDisplay(raw, ::inCode, bulletPositions, tasks)
        append(display)
        if (raw.isEmpty()) return@buildAnnotatedString

        // Fenced code blocks: monospace + tinted background across the whole region.
        for (r in codeRanges) {
            safeStyle(r.first, r.last + 1, SpanStyle(fontFamily = codeFont, color = codeText, background = codeBg))
        }

        // Line-anchored constructs: headings, blockquotes, horizontal rules and ordered markers.
        var lineStart = 0
        for (line in raw.split('\n')) {
            val lineEnd = lineStart + line.length
            if (!inCode(lineStart)) {
                val heading = headingRegex.find(line)
                val trimmed = line.trim()
                when {
                    heading != null -> {
                        val level = heading.groupValues[1].length
                        safeStyle(lineStart, lineEnd, SpanStyle(fontSize = headingSize(level), fontWeight = FontWeight.Bold))
                        safeStyle(lineStart, lineStart + heading.groupValues[1].length, SpanStyle(color = marker))
                    }
                    blockquoteRegex.containsMatchIn(line) -> {
                        safeStyle(lineStart, lineEnd, SpanStyle(color = quote, fontStyle = FontStyle.Italic))
                    }
                    trimmed.isNotEmpty() && horizontalRuleRegex.matches(trimmed) -> {
                        safeStyle(lineStart, lineEnd, SpanStyle(color = marker, fontWeight = FontWeight.Bold))
                    }
                    else -> {
                        val ord = orderedMarkerRegex.find(line)
                        if (ord != null) {
                            val s = lineStart + ord.groupValues[1].length
                            val e = s + ord.groupValues[2].length
                            safeStyle(s, e, SpanStyle(color = marker, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
            lineStart = lineEnd + 1
        }

        // Inline spans (skipped inside fenced code so e.g. `#include` isn't mistaken for a tag).
        // Each pass is a full-document regex scan, so above a generous size cap we skip them and keep
        // only the cheap line-based styling (headings, code, bullets). This keeps even multi-megabyte
        // notes typeable; such notes are rare and still render fully in Preview mode.
        if (raw.length <= INLINE_DECORATION_LIMIT) {
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
        }

        // Accent the swapped bullet glyphs (applied last so nothing overrides their tint).
        for (p in bulletPositions) {
            safeStyle(p, p + 1, SpanStyle(color = tag, fontWeight = FontWeight.Bold))
        }

        // Task-list items: tint the checkbox; completed `- [x]` items strike + dim their text.
        for (t in tasks) {
            safeStyle(t.markerStart, t.markerStart + 1, SpanStyle(color = marker))
            safeStyle(
                t.checkboxStart,
                (t.checkboxStart + 3).coerceAtMost(raw.length),
                SpanStyle(color = if (t.done) tag else marker, fontWeight = FontWeight.Bold),
            )
            if (t.done && t.contentStart < t.lineEnd) {
                safeStyle(t.contentStart, t.lineEnd, SpanStyle(color = marker, textDecoration = TextDecoration.LineThrough))
            }
        }
    }

    private data class TaskLine(
        val markerStart: Int,   // index of the -, *, + bullet
        val checkboxStart: Int, // index of the '[' in [ ] / [x]
        val done: Boolean,
        val contentStart: Int,  // first index of the task text after the checkbox
        val lineEnd: Int,
    )

    /** Locate task-list items (`- [ ]` / `- [x]`) outside fenced code, with their key offsets. */
    private fun taskLines(raw: String, inCode: (Int) -> Boolean): List<TaskLine> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<TaskLine>()
        var lineStart = 0
        for (line in raw.split('\n')) {
            if (!inCode(lineStart)) {
                val m = taskItemRegex.find(line)
                if (m != null) {
                    val markerStart = lineStart + m.groupValues[1].length
                    val box = m.groups[3]!!.range // the [x] / [ ] token
                    val checkboxStart = lineStart + box.first
                    val inner = line[box.first + 1]
                    val done = inner == 'x' || inner == 'X'
                    val contentStart = (lineStart + m.range.last + 1).coerceAtMost(lineStart + line.length)
                    out.add(TaskLine(markerStart, checkboxStart, done, contentStart, lineStart + line.length))
                }
            }
            lineStart += line.length + 1
        }
        return out
    }

    /**
     * Returns a render string identical in length to [raw] with each plain bullet-list marker
     * (`-`/`*`/`+` followed by a space, but not task-list `- [ ]` items or `---` rules) replaced by a
     * `•`, and each completed task checkbox's inner `x` replaced by a `✓`. Replaced bullet positions are
     * reported in [bulletOut] so the caller can tint them. Length is preserved, so
     * [OffsetMapping.Identity] stays valid and the source text is never mutated.
     */
    private fun buildDisplay(
        raw: String,
        inCode: (Int) -> Boolean,
        bulletOut: MutableList<Int>,
        tasks: List<TaskLine>,
    ): String {
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
                        bulletOut.add(markerIdx)
                    }
                }
            }
            lineStart += line.length + 1
        }
        for (t in tasks) {
            val inner = t.checkboxStart + 1
            if (t.done && inner in chars.indices) chars[inner] = '\u2713' // ✓
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
        // Above this raw length the per-keystroke inline regex passes are skipped (headings/code/
        // bullets still render). ~50 KB is far larger than virtually every real note.
        const val INLINE_DECORATION_LIMIT = 50_000
        val headingRegex = Regex("^(#{1,6})\\s+")
        val blockquoteRegex = Regex("^\\s*>")
        val listMarkerRegex = Regex("^(\\s*)[-*+]\\s+(?!\\[[ xX]\\])")
        val taskItemRegex = Regex("^(\\s*)([-*+])\\s+(\\[[ xX]\\])\\s?")
        val orderedMarkerRegex = Regex("^(\\s*)(\\d+[.)])\\s+")
        val horizontalRuleRegex = Regex("^([-*_])\\1{2,}$")
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        val strikeRegex = Regex("~~(.+?)~~")
        val italicRegex = Regex("(?<![*\\w])[*_](?![*_\\s])([^*_\\n]+?)(?<![*_\\s])[*_](?![*\\w])")
        val inlineCodeRegex = Regex("`([^`\\n]+?)`")
        val linkRegex = Regex("\\[([^\\]\\n]+)\\]\\(([^)\\n]+)\\)")
    }
}

/** Shared with the editor: matches `#tag` and nested `#a/b` tags, not `# ` headings. */
internal val hashtagRegex = Regex("(?<=^|\\s)#[A-Za-z0-9_][A-Za-z0-9_/-]*")
