package app.pebo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/** The lexical role of a run of source code, used to pick a display color. */
internal enum class TokenKind { Keyword, Str, Comment, Number, Function, Punctuation, Plain }

/** A contiguous run of source text classified as one [kind]. Concatenating every [text] in order
 *  reproduces the original input exactly (a property the unit tests rely on). */
internal data class CodeToken(val text: String, val kind: TokenKind)

/** Per-language lexer configuration. Deliberately small: a generic C-like tokenizer plus a keyword
 *  set and comment/quote rules covers the vast majority of snippets people paste into notes. */
private data class LangSpec(
    val keywords: Set<String>,
    val lineComments: List<String> = listOf("//"),
    val blockComment: Pair<String, String>? = "/*" to "*/",
    val quotes: Set<Char> = setOf('"', '\'', '`'),
    val highlightCalls: Boolean = true,
)

private val C_LIKE = setOf(
    "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
    "return", "goto", "struct", "union", "enum", "typedef", "sizeof", "const", "static",
    "void", "int", "long", "short", "char", "float", "double", "unsigned", "signed", "bool",
    "true", "false", "null", "nullptr",
)

private val KOTLIN = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "annotation",
    "if", "else", "when", "for", "while", "do", "return", "break", "continue", "is", "as", "in",
    "import", "package", "private", "public", "protected", "internal", "open", "override", "final",
    "abstract", "companion", "init", "constructor", "this", "super", "null", "true", "false",
    "by", "lateinit", "lazy", "suspend", "inline", "reified", "operator", "infix", "vararg",
    "throw", "try", "catch", "finally", "typealias", "out", "Unit", "String", "Int", "Boolean",
)

private val JAVA = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
    "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
    "while", "var", "record", "sealed", "yield", "true", "false", "null",
)

private val JS = setOf(
    "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch",
    "case", "default", "break", "continue", "new", "delete", "typeof", "instanceof", "in", "of",
    "class", "extends", "super", "this", "import", "export", "from", "as", "async", "await",
    "yield", "try", "catch", "finally", "throw", "void", "null", "undefined", "true", "false",
    "interface", "type", "enum", "implements", "public", "private", "protected", "readonly",
    "namespace", "declare", "abstract", "static", "get", "set",
)

private val PYTHON = setOf(
    "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue", "pass",
    "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield",
    "global", "nonlocal", "del", "in", "is", "not", "and", "or", "None", "True", "False", "self",
    "async", "await", "assert", "match", "case",
)

private val GO = setOf(
    "func", "var", "const", "package", "import", "return", "if", "else", "for", "range", "switch",
    "case", "default", "break", "continue", "go", "defer", "select", "chan", "map", "struct",
    "interface", "type", "fallthrough", "goto", "nil", "true", "false", "string", "int", "int64",
    "byte", "rune", "bool", "error", "make", "new", "len", "cap", "append",
)

private val RUST = setOf(
    "fn", "let", "mut", "const", "static", "if", "else", "match", "for", "while", "loop", "break",
    "continue", "return", "struct", "enum", "trait", "impl", "use", "mod", "pub", "crate", "self",
    "Self", "super", "as", "where", "move", "ref", "dyn", "async", "await", "unsafe", "extern",
    "type", "true", "false", "Some", "None", "Ok", "Err", "Option", "Result", "Vec", "String",
)

private val SQL = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create",
    "table", "alter", "drop", "join", "inner", "left", "right", "outer", "on", "group", "by",
    "order", "having", "limit", "offset", "as", "and", "or", "not", "null", "is", "in", "like",
    "between", "distinct", "count", "sum", "avg", "min", "max", "primary", "key", "foreign",
    "references", "index", "view", "union", "all", "case", "when", "then", "else", "end",
)

private val GENERIC = (C_LIKE + KOTLIN + JAVA + JS + PYTHON + GO + RUST)

private fun specFor(language: String?): LangSpec = when (language?.trim()?.lowercase()) {
    "kotlin", "kt", "kts" -> LangSpec(KOTLIN)
    "java" -> LangSpec(JAVA)
    "javascript", "js", "jsx", "typescript", "ts", "tsx" -> LangSpec(JS)
    "python", "py" -> LangSpec(PYTHON, lineComments = listOf("#"), blockComment = null)
    "go", "golang" -> LangSpec(GO)
    "rust", "rs" -> LangSpec(RUST)
    "c", "cpp", "c++", "cc", "h", "hpp", "objc" -> LangSpec(C_LIKE)
    "csharp", "cs", "c#" -> LangSpec(JAVA + setOf("using", "namespace", "string", "bool", "var", "async", "await", "get", "set", "value"))
    "swift" -> LangSpec(KOTLIN + setOf("func", "let", "guard", "defer", "struct", "protocol"))
    "ruby", "rb" -> LangSpec(
        setOf("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until",
            "for", "do", "return", "yield", "begin", "rescue", "ensure", "raise", "require",
            "attr_accessor", "self", "nil", "true", "false", "and", "or", "not", "then", "puts"),
        lineComments = listOf("#"), blockComment = null,
    )
    "php" -> LangSpec(JS + setOf("echo", "function", "public", "private", "protected", "use", "namespace"),
        lineComments = listOf("//", "#"))
    "shell", "bash", "sh", "zsh" -> LangSpec(
        setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "in", "return", "export", "local", "echo", "cd", "exit", "true", "false"),
        lineComments = listOf("#"), blockComment = null, quotes = setOf('"', '\''),
    )
    "sql" -> LangSpec(SQL, lineComments = listOf("--"), blockComment = "/*" to "*/", quotes = setOf('\'', '"'))
    "yaml", "yml", "toml", "ini" -> LangSpec(
        setOf("true", "false", "null", "yes", "no", "on", "off"),
        lineComments = listOf("#"), blockComment = null, highlightCalls = false,
    )
    "json" -> LangSpec(setOf("true", "false", "null"), lineComments = emptyList(), blockComment = null,
        quotes = setOf('"'), highlightCalls = false)
    else -> LangSpec(GENERIC, lineComments = listOf("//", "#"))
}

private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

/**
 * Tokenizes [code] into colored runs using a generic C-like lexer tuned per [language]. Pure and
 * platform-free so it is unit-tested. The concatenation of all returned [CodeToken.text] always
 * equals [code] exactly — no character is dropped or duplicated — so callers can render it safely.
 */
internal fun highlightCode(code: String, language: String?): List<CodeToken> {
    if (code.isEmpty()) return emptyList()
    val spec = specFor(language)
    val out = ArrayList<CodeToken>()
    val n = code.length
    var i = 0
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            out.add(CodeToken(plain.toString(), TokenKind.Plain))
            plain.setLength(0)
        }
    }

    fun startsWith(s: String, at: Int): Boolean {
        if (s.isEmpty() || at + s.length > n) return false
        for (k in s.indices) if (code[at + k] != s[k]) return false
        return true
    }

    while (i < n) {
        val c = code[i]

        // Line comments.
        val lc = spec.lineComments.firstOrNull { startsWith(it, i) }
        if (lc != null) {
            flushPlain()
            var j = i
            while (j < n && code[j] != '\n') j++
            out.add(CodeToken(code.substring(i, j), TokenKind.Comment))
            i = j
            continue
        }

        // Block comments.
        val bc = spec.blockComment
        if (bc != null && startsWith(bc.first, i)) {
            flushPlain()
            var j = i + bc.first.length
            while (j < n && !startsWith(bc.second, j)) j++
            val end = if (j < n) j + bc.second.length else n
            out.add(CodeToken(code.substring(i, end), TokenKind.Comment))
            i = end
            continue
        }

        // Strings.
        if (c in spec.quotes) {
            flushPlain()
            var j = i + 1
            while (j < n) {
                if (code[j] == '\\' && j + 1 < n) { j += 2; continue }
                if (code[j] == c) { j++; break }
                if (code[j] == '\n' && c != '`') { break }
                j++
            }
            out.add(CodeToken(code.substring(i, j.coerceAtMost(n)), TokenKind.Str))
            i = j.coerceAtMost(n)
            continue
        }

        // Numbers (incl. hex, float, suffixes).
        if (c.isDigit() || (c == '.' && i + 1 < n && code[i + 1].isDigit())) {
            flushPlain()
            var j = i
            if (c == '0' && i + 1 < n && (code[i + 1] == 'x' || code[i + 1] == 'X' || code[i + 1] == 'b')) {
                j += 2
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            } else {
                while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == '_' ||
                        code[j] == 'e' || code[j] == 'E')) j++
                while (j < n && code[j].isLetter()) j++ // trailing suffix like f, L, ull
            }
            out.add(CodeToken(code.substring(i, j), TokenKind.Number))
            i = j
            continue
        }

        // Identifiers / keywords / calls.
        if (isIdentStart(c)) {
            flushPlain()
            var j = i + 1
            while (j < n && isIdentPart(code[j])) j++
            val word = code.substring(i, j)
            val kind = when {
                word in spec.keywords -> TokenKind.Keyword
                spec.highlightCalls && run {
                    var k = j; while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    k < n && code[k] == '('
                } -> TokenKind.Function
                else -> TokenKind.Plain
            }
            out.add(CodeToken(word, kind))
            i = j
            continue
        }

        // Punctuation vs whitespace/plain.
        if (!c.isWhitespace() && !c.isLetterOrDigit() && c != '_') {
            flushPlain()
            out.add(CodeToken(c.toString(), TokenKind.Punctuation))
            i++
            continue
        }

        plain.append(c)
        i++
    }
    flushPlain()
    return out
}

/** Display colors for each [TokenKind], chosen to read clearly on the code-block background. Two
 *  curated sets (for dark and light surfaces) keep snippets legible across all themes. */
internal data class CodeColors(
    val keyword: Color,
    val str: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val punctuation: Color,
    val plain: Color,
)

/** Picks a code palette suited to a [darkBackground] code box (One-Dark-ish) or a light one. */
internal fun codeColors(darkBackground: Boolean, plain: Color): CodeColors =
    if (darkBackground) {
        CodeColors(
            keyword = Color(0xFFC792EA),
            str = Color(0xFFC3E88D),
            comment = Color(0xFF7A8290),
            number = Color(0xFFF78C6C),
            function = Color(0xFF82AAFF),
            punctuation = Color(0xFF89DDFF),
            plain = plain,
        )
    } else {
        CodeColors(
            keyword = Color(0xFF8B1A9E),
            str = Color(0xFF1E7A33),
            comment = Color(0xFF8A8F98),
            number = Color(0xFFB3590A),
            function = Color(0xFF1456C7),
            punctuation = Color(0xFF3B6EA5),
            plain = plain,
        )
    }

private fun CodeColors.colorFor(kind: TokenKind): Color = when (kind) {
    TokenKind.Keyword -> keyword
    TokenKind.Str -> str
    TokenKind.Comment -> comment
    TokenKind.Number -> number
    TokenKind.Function -> function
    TokenKind.Punctuation -> punctuation
    TokenKind.Plain -> plain
}

/** Builds a colored [AnnotatedString] for [code] in [language] using [colors]. Comments render in
 *  italic. Pure (no Compose runtime), so it is unit-testable. */
internal fun buildHighlightedCode(code: String, language: String?, colors: CodeColors): AnnotatedString =
    buildAnnotatedString {
        for (tok in highlightCode(code, language)) {
            val style = SpanStyle(
                color = colors.colorFor(tok.kind),
                fontStyle = if (tok.kind == TokenKind.Comment) FontStyle.Italic else FontStyle.Normal,
            )
            withStyle(style) { append(tok.text) }
        }
    }
