package com.closecode.ui.ide

import com.closecode.lang.Diagnostic
import com.closecode.lang.DiagnosticCodes
import com.closecode.lang.Severity

/**
 * The tags a reader reaches for, for the completion list.
 *
 * Deliberately the everyday set and nothing more. A list of every element in the specification would
 * be a scroll rather than a list, and the tags nobody types by hand — `template`, `slot`, `portal` —
 * are exactly the ones already in the autocomplete list above them once `html` is typed.
 */
internal val HtmlTagNames: List<String> = listOf(
    "div", "span", "p", "a", "img", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
    "header", "footer", "nav", "main", "section", "article", "aside", "table", "tr", "td", "th",
    "button", "input", "label", "form", "select", "option", "textarea", "strong", "em", "small",
    "code", "pre", "br", "hr", "iframe", "video", "audio", "canvas", "svg", "script", "style",
)

/** The attributes each common tag is read with. Shown once the caret is inside a tag after a space. */
internal val HtmlAttributesByTag: Map<String, List<String>> = mapOf(
    "a" to listOf("href", "target", "rel", "title", "download"),
    "img" to listOf("src", "alt", "width", "height", "loading"),
    "input" to listOf("type", "name", "value", "placeholder", "required", "min", "max", "step"),
    "button" to listOf("type", "disabled", "name"),
    "label" to listOf("for"),
    "form" to listOf("action", "method", "enctype"),
    "table" to listOf("border", "cellpadding"),
    "td" to listOf("colspan", "rowspan"),
    "th" to listOf("colspan", "rowspan", "scope"),
    "ol" to listOf("start", "type"),
    "video" to listOf("src", "controls", "autoplay", "loop", "muted", "poster"),
    "audio" to listOf("src", "controls", "autoplay", "loop", "muted"),
    "iframe" to listOf("src", "width", "height", "allowfullscreen", "loading"),
    "script" to listOf("src", "type", "defer", "async"),
    "meta" to listOf("charset", "name", "content", "viewport"),
    "link" to listOf("rel", "href", "type"),
)

/** Attributes every element may carry. Offered for a tag with no list of its own. */
internal val HtmlGlobalAttributes: List<String> = listOf("id", "class", "style", "title", "hidden")

/**
 * What the reader is part-way through typing, when it is a tag.
 *
 * Three shapes, and the difference matters because each one completes differently: just an angle
 * bracket, an angle bracket with a partial name, or a name inside a tag with nothing after it yet —
 * which is where an attribute list belongs.
 */
internal data class HtmlTagContext(
    val tagName: String,
    /** True when the caret is after the name rather than still on it. */
    val afterName: Boolean,
)

/**
 * The tag being typed at [cursor], or null when it is not a tag at all.
 *
 * Found by walking back to the `<` that opens this tag, not back over the word before the caret: a
 * caret sitting after a space inside a tag — the exact moment an attribute list is wanted — has no
 * word in front of it at all, and the word-shaped rule found nothing there.
 *
 * A tag that has already closed before the caret is not this one, which is what stops a second
 * `<div>` later on the same line from taking over the list.
 */
internal fun htmlTagContextAt(source: String, cursor: Int): HtmlTagContext? {
    val at = cursor.coerceIn(0, source.length)
    val lineStart = if (at == 0) 0 else (source.lastIndexOf('\n', at - 1) + 1)
    val open = source.lastIndexOf('<', (at - 1).coerceAtLeast(0)).let { if (at == 0) -1 else it }
    if (open < lineStart) return null
    // Already past this tag's own end: whatever comes next is not part of it.
    if (source.indexOf('>', open) in 0 until at) return null
    val afterBracket = open + 1
    // A `<` is only a tag's opening when a name, a slash, a doctype or a declaration follows it.
    // Without this, `a < b` in ordinary text offered a list of elements to a reader who had just
    // typed a less-than in a comparison.
    val next = source.getOrNull(afterBracket)
    if (next != null && next != '/' && next != '!' && next != '?' && !next.isLetter()) return null
    if (next == '/') return null
    var nameEnd = afterBracket
    while (nameEnd < source.length &&
        (source[nameEnd].isLetterOrDigit() || source[nameEnd] == '-' || source[nameEnd] == '_')
    ) nameEnd++
    val tag = source.substring(afterBracket, nameEnd)
    return HtmlTagContext(tagName = tag, afterName = at > nameEnd)
}

/** The tag names offered after a bare `<`, filtered by what has been typed. */
internal fun htmlTagSuggestions(partial: String, limit: Int = 8): List<String> {
    val lower = partial.lowercase()
    return HtmlTagNames.filter { it.startsWith(lower) }.take(limit)
}

/** The attributes offered inside a tag, filtered by what has been typed. */
internal fun htmlAttributeSuggestions(tagName: String, partial: String, limit: Int = 8): List<String> {
    val lower = partial.lowercase()
    val own = HtmlAttributesByTag[tagName.lowercase()].orEmpty()
    return (own + HtmlGlobalAttributes)
        .distinct()
        .filter { it.startsWith(lower) }
        .take(limit)
}

/** One open tag, with where its name ends and the line it was opened on. */
internal data class OpenTag(val name: String, val offset: Int, val line: Int)

/**
 * The tag left open when the reader types `</`.
 *
 * Scans the whole buffer rather than the line, because the closer is usually typed on a line of its
 * own below the block it closes. The innermost one still open is the one the reader means — anything
 * else would offer `</html>` when they are three blocks deep.
 */
internal fun openTagBefore(source: String, cursor: Int): OpenTag? {
    val stack = ArrayList<OpenTag>()
    var i = 0
    val limit = cursor.coerceIn(0, source.length)
    var line = 1
    var inComment = false
    while (i < limit) {
        val ch = source[i]
        when {
            ch == '\n' -> line++
            inComment -> {
                if (ch == '-' && source.startsWith("-->", i)) {
                    inComment = false
                    i += 2
                }
            }
            ch == '<' && source.startsWith("<!--", i) -> {
                inComment = true
                i += 3
            }
            ch == '<' -> {
                var j = i + 1
                val closing = j < limit && source[j] == '/'
                if (closing) j++
                val nameStart = j
                while (j < limit && (source[j].isLetterOrDigit() || source[j] == '-' || source[j] == '_')) j++
                val name = source.substring(nameStart, j)
                if (name.isEmpty() || isHtmlVoidTag(name)) {
                    i = maxOf(j, i + 1)
                    continue
                }
                if (closing) {
                    // A closing tag pops the innermost matching open one, and pops past anything left
                    // open inside it — which is the point: it is how the reader recovers.
                    val at = stack.indexOfLast { it.name.equals(name, ignoreCase = true) }
                    if (at >= 0) while (stack.size > at) stack.removeAt(stack.lastIndex)
                } else {
                    stack.add(OpenTag(name, j, line))
                }
                i = maxOf(j, i + 1)
                continue
            }
            else -> Unit
        }
        i++
    }
    return stack.lastOrNull()
}

/** Tags that never take a closing tag. Offering to close one of these closes nothing. */
internal val HtmlVoidTags: Set<String> = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
    "param", "source", "track", "wbr",
)

internal fun isHtmlVoidTag(name: String): Boolean = name.lowercase() in HtmlVoidTags

/**
 * Tags left open, and tags closed in the wrong order.
 *
 * The reader is told on the line the tag was opened, not on the end of the file, because that is
 * where the fix goes. Mismatched closers are reported the same way: a `</div>` that finds no `<div>`
 * open is worth a squiggle, and so is a closer that had to pop past a tag still open inside it.
 */
internal fun htmlTagDiagnostics(source: String): List<Diagnostic> {
    if (codeLanguageFor(languageNameFor(source)) != CodeLanguage.MARKUP) return emptyList()
    val out = ArrayList<Diagnostic>()
    val stack = ArrayList<OpenTag>()
    var i = 0
    var line = 1
    var inComment = false
    var inRaw = false
    var rawTag = ""
    while (i < source.length) {
        val ch = source[i]
        if (ch == '\n') {
            line++
            i++
            continue
        }
        if (inComment) {
            if (ch == '-' && source.startsWith("-->", i)) {
                inComment = false
                i += 2
            }
            i++
            continue
        }
        if (inRaw) {
            val close = "</$rawTag"
            if (ch == '<' && source.regionMatches(i, close, 0, close.length, ignoreCase = true)) {
                inRaw = false
                // It is an ordinary closing tag with content in the middle, so it closes the element
                // it belongs to. Leaving it on the stack reported every script and style as unclosed.
                val at2 = stack.indexOfLast { it.name.equals(rawTag, ignoreCase = true) }
                if (at2 >= 0) stack.removeAt(at2)
                i += close.length
            }
            i++
            continue
        }
        if (ch == '<' && source.startsWith("<!--", i)) {
            inComment = true
            i += 3
            continue
        }
        if (ch != '<') {
            i++
            continue
        }
        if (source.startsWith("</", i)) {
            var j = i + 2
            val nameStart = j
            while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '-')) j++
            val name = source.substring(nameStart, j)
            val at = stack.indexOfLast { it.name.equals(name, ignoreCase = true) }
            if (at >= 0) {
                // Anything still open above the match was never closed. Each is reported on its own
                // opening line, because that is where the missing `</` belongs.
                for (unclosed in stack.subList(at + 1, stack.size)) {
                    out += unclosedTagDiagnostic(unclosed)
                }
                while (stack.size > at) stack.removeAt(stack.lastIndex)
            }
            i = maxOf(j, i + 1)
            continue
        }
        var j = i + 1
        // A doctype or a processing instruction is not a tag.
        if (j < source.length && (source[j] == '!' || source[j] == '?')) {
            i = j
            while (i < source.length && source[i] != '>') i++
            i++
            continue
        }
        val nameStart = j
        while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '-' || source[j] == '_')) j++
        val name = source.substring(nameStart, j)
        // Skip the attribute run to find this tag's own end, so `>` inside a value does not close it.
        var k = j
        var inQuote = ' '
        while (k < source.length) {
            val d = source[k]
            if (inQuote != ' ') {
                if (d == inQuote) inQuote = ' '
            } else if (d == '"' || d == '\'') {
                inQuote = d
            } else if (d == '>') {
                break
            }
            k++
        }
        val selfClosing = k < source.length && source[k - 1] == '/'
        if (name.isNotEmpty() && !isHtmlVoidTag(name) && !selfClosing) {
            stack.add(OpenTag(name, j, line))
            if (name.equals("script", true) || name.equals("style", true)) {
                inRaw = true
                rawTag = name
                i = k + 1
                continue
            }
        }
        i = maxOf(k, i + 1)
    }
    // Whatever is still open at the end of the file is unclosed too.
    for (unclosed in stack) out += unclosedTagDiagnostic(unclosed)
    return out
}

private fun unclosedTagDiagnostic(tag: OpenTag): Diagnostic =
    Diagnostic(
        severity = Severity.ERROR,
        message = "Unclosed <${tag.name}>",
        offset = tag.offset,
        length = 1 + tag.name.length,
        line = tag.line,
        column = 1,
        code = DiagnosticCodes.HTML_UNCLOSED_TAG,
    )

/** The language a buffer looks like, for the checks that must not run on code. */
private fun languageNameFor(source: String): String =
    when (codeLanguageOf(source)) {
        CodeLanguage.MARKUP -> "HTML"
        CodeLanguage.STYLESHEET -> "CSS"
        CodeLanguage.PROGRAMMING -> "Kotlin"
    }
/**
 * Completes a closing tag when the reader types the second slash of `</`.
 *
 * The trigger is deliberately narrow: the edit has to be exactly one character long, that character
 * has to be the slash, and it has to land directly after a `<`. Anything else — a paste, a
 * selection, a slash in the middle of a line — is passed straight through, so a page can be edited
 * normally and only the deliberate gesture of typing a closer completes one.
 *
 * Nothing is offered when the innermost open tag is a void one. Offering to close an `<img>` would
 * insert a `</img>` that no markup allows.
 */
internal fun closeTagAt(oldText: String, newText: String, caret: Int): Pair<String, Int>? {
    if (newText.length != oldText.length + 1) return null
    if (caret < 2 || caret > newText.length) return null
    if (newText[caret - 1] != '/') return null
    if (newText[caret - 2] != '<') return null
    val tag = openTagBefore(newText, caret - 2) ?: return null
    val completion = tag.name + ">"
    val next = newText.substring(0, caret) + completion + newText.substring(caret)
    return next to (caret + completion.length)
}

/**
 * Where the partial word being typed starts, measured from the line the caret is on.
 *
 * The attribute list needs the text between the tag's name and the caret, and reading it from the
 * line alone is what keeps a page's inline tags out of the answer.
 */
internal fun htmlContextOffset(source: String, cursor: Int): Int {
    val at = cursor.coerceIn(0, source.length)
    var i = at
    while (i > 0 && (source[i - 1].isLetterOrDigit() || source[i - 1] == '-' || source[i - 1] == '_')) i--
    return i
}
