package com.closecode.ui.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.closecode.ui.theme.PageColors

private val KeywordTokens = setOf(
    "package", "import", "class", "object", "interface", "fun", "val", "var",
    "return", "if", "else", "when", "for", "while", "do", "override", "private",
    "public", "internal", "const", "suspend", "inline", "data", "companion",
    "const", "val", "lateinit", "constructor", "this", "super", "null", "true",
    "false", "function", "let", "const", "new", "async", "await", "export",
    "default", "extends", "implements", "def", "from", "import", "try", "catch",
    "finally", "throw", "with", "in", "is", "as", "typeof", "yield",
)

/**
 * Colourizes [text] for the editor.
 *
 * This runs on the main thread, once per keystroke, so its allocations are the ones that matter:
 * the seven styles below used to be built *per token*, and every token used to be copied out of its
 * line before being appended. A two thousand line file was making tens of thousands of throwaway
 * objects on the typing path. The styles are now built once per call, and tokens are appended
 * straight from the line by range, so what is left is the one copy the string actually needs.
 *
 * It stays synchronous on purpose. Producing it off the main thread would mean painting the
 * uncoloured buffer for a frame on every keystroke — a colour flash while typing is worse than the
 * cost of not flashing — and the result must always match the text on screen, because the
 * [AnnotatedString] carries its own text.
 */
fun highlightCode(text: String, c: PageColors): AnnotatedString =
    highlightCode(text, c, codeLanguageOf(text))

/**
 * Colours a buffer.
 *
 * [bracketCycle] is the rainbow-bracket palette and [initialDepth] is how many blocks were already
 * open above this text. Both are arguments rather than global state, and both are load-bearing:
 *
 * A global written from an effect cannot invalidate the `remember` that reads it, so switching the
 * setting on would recolour nothing at all — the setting was written after the frame was drawn and
 * the colour pass never ran again.
 *
 * And the renderer only ever sees the visible slice of the file, so a depth counter starting at
 * zero gave every window the shallowest colours no matter how deep in the file the reader had
 * scrolled. That is the one thing the feature exists to prevent.
 */
internal fun highlightCode(
    text: String,
    c: PageColors,
    language: CodeLanguage,
    bracketCycle: List<Color> = emptyList(),
    initialDepth: Int = 0,
): AnnotatedString =
    when (language) {
        CodeLanguage.PROGRAMMING -> highlightProgramCode(text, c, bracketCycle, initialDepth)
        CodeLanguage.MARKUP -> highlightMarkup(text, c)
        CodeLanguage.STYLESHEET -> highlightStylesheet(text, c)
    }

/** Which rules a buffer is coloured by, decided from the text itself. */
internal enum class CodeLanguage {
    /** Braces, semicolons, keywords: the general programming rules. */
    PROGRAMMING,

    /** Tags, attributes and `<!-- -->`. */
    MARKUP,

    /** Selectors, properties, at-rules and `/* */`. */
    STYLESHEET,
}

/**
 * The language of a buffer, worked out from its own first line rather than its file name.
 *
 * The renderer is handed a slice of a file with no name attached to it, so the only thing that says
 * what the rules should be is what is in the text. An `<html`, `<!DOCTYPE` or `<!--` on the
 * first line is a markup file; a `body {` or an `@media` is a stylesheet.
 */
internal fun codeLanguageOf(text: String): CodeLanguage {
    val head = text.lineSequence().take(8).joinToString("\n").take(600)
    val trimmed = head.trimStart()
    return when {
        trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.startsWith("<!--") -> CodeLanguage.MARKUP
        Regex("^</?[a-zA-Z][a-zA-Z0-9]*[\\s/>]", RegexOption.IGNORE_CASE).containsMatchIn(head) ->
            CodeLanguage.MARKUP
        Regex("^[.#a-zA-Z][\\w.#-]*\\s*\\{", RegexOption.MULTILINE).containsMatchIn(head) ||
            head.contains("@media") || head.contains("@import") -> CodeLanguage.STYLESHEET
        else -> CodeLanguage.PROGRAMMING
    }
}

/**
 * How deep the bracket nesting is at the end of [prefix], so colouring a *slice* can start from
 * where the slice is.
 *
 * The renderer only ever sees the visible window, so a depth counter starting at zero gave every
 * window the shallowest colours no matter how deep in the file the reader had scrolled — which is the
 * one thing the feature exists to prevent.
 *
 * All three bracket kinds count, exactly as the colour pass counts them: a slice that starts inside
 * `foo(bar,` is one level deep even though no brace is open. Strings and comments are skipped: a
 * bracket inside either is a character, and counting it would walk every following line one level
 * out of step with the code.
 */
internal fun openBlockDepth(prefix: String): Int {
    var depth = 0
    var inString = false
    var quote = ' '
    var inLineComment = false
    var blockComment = 0
    var i = 0
    while (i < prefix.length) {
        val ch = prefix[i]
        when {
            inLineComment -> if (ch == '\n') inLineComment = false
            blockComment > 0 -> {
                if (ch == '/' && i + 1 < prefix.length && prefix[i + 1] == '*') blockComment++
                else if (ch == '*' && i + 1 < prefix.length && prefix[i + 1] == '/') blockComment--
            }
            inString -> {
                if (ch == '\\') i++
                else if (ch == quote) inString = false
            }
            ch == '/' && i + 1 < prefix.length && prefix[i + 1] == '/' -> inLineComment = true
            ch == '/' && i + 1 < prefix.length && prefix[i + 1] == '*' -> blockComment++
            ch == '"' || ch == '\'' -> {
                inString = true
                quote = ch
            }
            ch == '{' || ch == '(' || ch == '[' -> depth++
            ch == '}' || ch == ')' || ch == ']' -> depth = (depth - 1).coerceAtLeast(0)
        }
        i++
    }
    return depth
}

/** Which colour a bracket at this nesting depth gets, or null when the feature is off. */
internal fun rainbowBracketColor(depth: Int, cycle: List<Color>): Color? {
    if (cycle.isEmpty()) return null
    // Floored, not truncated: a stray closer at depth -1 wraps to the last colour instead of
    // crashing or clamping to the first, so every depth has exactly one colour.
    return cycle[depth.mod(cycle.size)]
}

private fun highlightProgramCode(
    text: String,
    c: PageColors,
    bracketCycle: List<Color>,
    initialDepth: Int,
): AnnotatedString {
    val commentStyle = SpanStyle(color = c.text, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = c.codeString)
    val annotationStyle = SpanStyle(color = c.text)
    val keywordStyle = SpanStyle(color = c.codeKeyword, fontWeight = FontWeight.Medium)
    val functionStyle = SpanStyle(color = c.codeFunction)
    val typeStyle = SpanStyle(color = c.codeType)
    val numberStyle = SpanStyle(color = c.text)

    // Depth carries across lines, because a block does. A counter reset per line gave every line's
    // first bracket the same colour no matter how deep in the file it sat, which is the one thing the
    // feature is for.
    // Depth carries across lines *and* across windows: a block spans both.
    var bracketDepth = initialDepth
    return buildAnnotatedString {
        text.lines().forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            var i = 0
            val n = raw.length
            while (i < n) {
                val ch = raw[i]
                when {
                    ch == '/' && i + 1 < n && (raw[i + 1] == '/' || raw[i + 1] == '*') -> {
                        val end = if (raw[i + 1] == '/') n else {
                            val close = raw.indexOf("*/", i + 2)
                            if (close < 0) n else close + 2
                        }
                        withStyle(commentStyle) {
                            append(raw, i, end)
                        }
                        i = end
                    }

                    ch == '"' || ch == '\'' -> {
                        var j = i + 1
                        while (j < n && raw[j] != ch) {
                            if (raw[j] == '\\') j++
                            j++
                        }
                        val end = if (j < n) j + 1 else n
                        withStyle(stringStyle) {
                            append(raw, i, end)
                        }
                        i = end
                    }

                    ch == '<' && raw.startsWith("<!--", i) -> {
                        val end = if (raw.contains("-->")) {
                            raw.indexOf("-->", i) + 3
                        } else {
                            n
                        }
                        withStyle(commentStyle) {
                            append(raw, i, end)
                        }
                        i = end
                    }

                    ch == '@' -> {
                        var j = i + 1
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '_' || raw[j] == '.')) j++
                        withStyle(annotationStyle) {
                            append(raw, i, j)
                        }
                        i = j
                    }

                    ch.isLetter() || ch == '_' -> {
                        var j = i
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        // The one token still copied: it is the key of the keyword lookup and the
                        // thing the capitalisation test looks at, so it is needed either way.
                        val word = raw.substring(i, j)
                        val after = raw.getOrNull(j)
                        when {
                            word in KeywordTokens -> withStyle(keywordStyle) { append(raw, i, j) }
                            after == '(' -> withStyle(functionStyle) { append(raw, i, j) }
                            word.firstOrNull()?.isUpperCase() == true -> withStyle(typeStyle) {
                                append(raw, i, j)
                            }
                            else -> append(raw, i, j)
                        }
                        i = j
                    }

                    ch.isDigit() -> {
                        var j = i
                        while (j < n && (raw[j].isDigit() || raw[j] == '.')) j++
                        withStyle(numberStyle) {
                            append(raw, i, j)
                        }
                        i = j
                    }

                    ch == '{' || ch == '(' || ch == '[' || ch == '}' || ch == ')' || ch == ']' -> {
                        val opening = ch == '{' || ch == '(' || ch == '['
                        val depth = if (opening) bracketDepth else bracketDepth - 1
                        val style = rainbowBracketColor(depth, bracketCycle)?.let { SpanStyle(color = it) }
                        if (style == null) append(ch) else withStyle(style) { append(ch) }
                        // A pair shares its colour: the opener climbs one level, the closer draws at
                        // the level it closes and then climbs back down. Without the way back down,
                        // depth only ever rose, and a closer ended up deeper than its own opener.
                        bracketDepth = if (opening) {
                            bracketDepth + 1
                        } else {
                            (bracketDepth - 1).coerceAtLeast(0)
                        }
                        i++
                    }

                    else -> {
                        append(ch)
                        i++
                    }
                }
            }
        }
    }
}

/**
 * The `{ … }` pairs in a whole source file, as absolute offset ranges, sorted by where each pair
 * opens.
 *
 * A scan rather than a parse. Braces inside a line comment, a block comment, a string or a
 * character literal are not braces, so those are stepped over instead of counted — the guide for a
 * block must not be thrown off by a `"{"` in a log message. A brace left over at the end of the
 * file — a half-typed block, or a file that does not compile — contributes no pair at all, because
 * a rule drawn to nowhere is worse than no rule.
 */
internal fun bracePairs(source: String): List<IntRange> {
    val opens = mutableListOf<Int>()
    val pairs = mutableListOf<IntRange>()
    var i = 0
    while (i < source.length) {
        when (source[i]) {
            '/' -> when {
                source.getOrNull(i + 1) == '/' -> {
                    val end = source.indexOf('\n', i)
                    i = if (end < 0) source.length else end
                }
                source.getOrNull(i + 1) == '*' -> i = endOfBlockComment(source, i)
                else -> i++
            }
            '"' -> i = endOfString(source, i)
            '\'' -> i = endOfCharLiteral(source, i)
            '{' -> {
                opens += i
                i++
            }
            '}' -> {
                val start = opens.removeLastOrNull()
                if (start != null) pairs += start..i
                i++
            }
            else -> i++
        }
    }
    return pairs.sortedBy { it.first }
}

/**
 * Every matched bracket pair in a whole source file — `{ }`, `( )` and `[ ]` — as absolute offset
 * ranges, sorted by where each pair opens.
 *
 * The same scan as [bracePairs] with the same skipping, extended to all three kinds with one stack
 * of (bracket, offset). A closer only pairs when the innermost open bracket is its own kind, so
 * `([)]` pairs nothing rather than crossing: a crossed pair would underline two brackets that do
 * not belong together. This feeds the brace underline only; the indent guides keep reading
 * [bracePairs], because a guide belongs to a block's indent level, not to a parenthesis.
 */
internal fun allBracketPairs(source: String): List<IntRange> {
    val opens = mutableListOf<Pair<Char, Int>>()
    val pairs = mutableListOf<IntRange>()
    var i = 0
    while (i < source.length) {
        when (source[i]) {
            '/' -> when {
                source.getOrNull(i + 1) == '/' -> {
                    val end = source.indexOf('\n', i)
                    i = if (end < 0) source.length else end
                }
                source.getOrNull(i + 1) == '*' -> i = endOfBlockComment(source, i)
                else -> i++
            }
            '"' -> i = endOfString(source, i)
            '\'' -> i = endOfCharLiteral(source, i)
            '{', '(', '[' -> {
                opens += source[i] to i
                i++
            }
            '}', ')', ']' -> {
                val want = when (source[i]) {
                    '}' -> '{'
                    ')' -> '('
                    else -> '['
                }
                if (opens.lastOrNull()?.first == want) {
                    val start = opens.removeAt(opens.size - 1).second
                    pairs += start..i
                }
                i++
            }
            else -> i++
        }
    }
    return pairs.sortedBy { it.first }
}

/** Index just past the block comment opening at [start], or the end of the file if it never closes. */
private fun endOfBlockComment(source: String, start: Int): Int {
    var i = start + 2
    var depth = 1
    while (i < source.length && depth > 0) {
        when {
            source.startsWith("/*", i) -> {
                depth++
                i += 2
            }
            source.startsWith("*/", i) -> {
                depth--
                i += 2
            }
            else -> i++
        }
    }
    return i
}

/** Index just past the string starting at [start]. A raw `"""` string is one token, not three. */
private fun endOfString(source: String, start: Int): Int {
    if (source.startsWith("\"\"\"", start)) {
        val end = source.indexOf("\"\"\"", start + 3)
        return if (end < 0) source.length else end + 3
    }
    var i = start + 1
    while (i < source.length && source[i] != '"') {
        if (source[i] == '\\') i++
        i++
    }
    return if (i < source.length) i + 1 else source.length
}

/** Index just past the character literal starting at [start], honouring a backslash escape. */
private fun endOfCharLiteral(source: String, start: Int): Int {
    var i = start + 1
    while (i < source.length && source[i] != '\'') {
        if (source[i] == '\\') i++
        i++
    }
    return if (i < source.length) i + 1 else source.length
}

/**
 * Markup: `<!-- -->` is a comment, a tag's name and its attributes are coloured as parts of a tag,
 * and an attribute's quoted value is a string.
 *
 * The rule that matters is the one this file previously got wrong for every language at once: a
 * slash does not start a comment here. In a page, `href="http://example.com"` had everything after
 * the `//` swallowed as a comment, so the closing quote and the rest of the line drew in the
 * comment colour and the tag never closed on screen.
 */
internal fun highlightMarkup(text: String, c: PageColors): AnnotatedString {
    val commentStyle = SpanStyle(color = c.text, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = c.codeString)
    val tagStyle = SpanStyle(color = c.codeKeyword, fontWeight = FontWeight.Medium)
    val attrStyle = SpanStyle(color = c.codeType)
    val punctuationStyle = SpanStyle(color = c.mu)

    return buildAnnotatedString {
        text.lines().forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            var i = 0
            val n = raw.length
            while (i < n) {
                if (raw.startsWith("<!--", i)) {
                    val close = raw.indexOf("-->", i)
                    val end = if (close < 0) n else close + 3
                    withStyle(commentStyle) { append(raw, i, end) }
                    i = end
                    continue
                }
                if (raw[i] == '<') {
                    val close = raw.indexOf('>', i)
                    val end = if (close < 0) n else close + 1
                    appendMarkupTag(raw.substring(i, end), tagStyle, attrStyle, punctuationStyle, stringStyle)
                    i = end
                    continue
                }
                append(raw[i])
                i++
            }
        }
    }
}

/** Colours one `<...>` run: the tag name, each attribute name, and each quoted value. */
private fun AnnotatedString.Builder.appendMarkupTag(
    tag: String,
    tagStyle: SpanStyle,
    attrStyle: SpanStyle,
    punctuationStyle: SpanStyle,
    stringStyle: SpanStyle,
) {
    var i = 0
    val n = tag.length
    while (i < n) {
        val ch = tag[i]
        when {
            ch == '<' || ch == '>' || ch == '/' -> {
                withStyle(punctuationStyle) { append(ch) }
                i++
            }
            ch == '"' || ch == '\'' -> {
                val end = tag.indexOf(ch, i + 1).let { if (it < 0) n else it + 1 }
                withStyle(stringStyle) { append(tag, i, end) }
                i = end
            }
            ch == '=' -> {
                withStyle(punctuationStyle) { append(ch) }
                i++
            }
            ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == ':' -> {
                // A name before any whitespace in the run is the tag; anything after it is an
                // attribute. That is the only difference between the two, and it is positional.
                val style = if (tag.take(i).none { it.isWhitespace() }) tagStyle else attrStyle
                var j = i
                while (j < n && (tag[j].isLetterOrDigit() || tag[j] == '-' || tag[j] == '_' || tag[j] == ':')) j++
                withStyle(style) { append(tag, i, j) }
                i = j
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

/**
 * Stylesheets: block comments that carry across lines, at-rules as keywords, and a declaration's
 * name set against its value.
 *
 * The comment has to carry, which the programming rules never needed because every comment in code
 * closed on its own line. A stylesheet comment left open swallows the rules after it, which is the
 * same class of bug the `//` case above caused.
 */
internal fun highlightStylesheet(text: String, c: PageColors): AnnotatedString {
    val commentStyle = SpanStyle(color = c.text, fontStyle = FontStyle.Italic)
    val keywordStyle = SpanStyle(color = c.codeKeyword, fontWeight = FontWeight.Medium)
    val propStyle = SpanStyle(color = c.codeType)
    val valueStyle = SpanStyle(color = c.codeString)
    val numberStyle = SpanStyle(color = c.text)

    return buildAnnotatedString {
        var inComment = false
        text.lines().forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            var i = 0
            val n = raw.length
            while (i < n) {
                if (inComment) {
                    val close = raw.indexOf("*/", i)
                    val end = if (close < 0) n else close + 2
                    withStyle(commentStyle) { append(raw, i, end) }
                    inComment = raw.substring(i, end).let { !it.contains("*/") }
                    i = end
                    continue
                }
                if (raw.startsWith("/*", i)) {
                    inComment = true
                    continue
                }
                val ch = raw[i]
                when {
                    ch == '@' -> {
                        var j = i + 1
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '-')) j++
                        withStyle(keywordStyle) { append(raw, i, j) }
                        i = j
                    }
                    ch == '"' || ch == '\'' -> {
                        var j = i + 1
                        while (j < n && raw[j] != ch) j++
                        val end = if (j < n) j + 1 else n
                        withStyle(valueStyle) { append(raw, i, end) }
                        i = end
                    }
                    ch == ':' -> {
                        withStyle(propStyle) { append(ch) }
                        i++
                    }
                    ch == '#' || (ch == '.' && i + 1 < n && raw[i + 1].isLetterOrDigit()) -> {
                        var j = i + 1
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '-' || raw[j] == '#')) j++
                        withStyle(valueStyle) { append(raw, i, j) }
                        i = j
                    }
                    ch.isDigit() -> {
                        var j = i
                        while (j < n && (raw[j].isDigit() || raw[j] == '.' || raw[j] == '%')) j++
                        withStyle(numberStyle) { append(raw, i, j) }
                        i = j
                    }
                    else -> {
                        append(ch)
                        i++
                    }
                }
            }
        }
    }
}

/**
 * The colouring rules a file's own language name asks for.
 *
 * Read from the name the project store already keeps, so there is one answer per file rather than a
 * guess per rendered slice. Anything unrecognised stays on the programming rules, which is the right
 * default for the languages this app is mostly used to write.
 */
internal fun codeLanguageFor(language: String): CodeLanguage = when {
    language.contains("HTML", true) || language.contains("XML", true) -> CodeLanguage.MARKUP
    language.contains("CSS", true) -> CodeLanguage.STYLESHEET
    else -> CodeLanguage.PROGRAMMING
}
