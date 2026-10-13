package com.closecode.ui.ide

import com.closecode.lang.AndroidApiIndex
import com.closecode.lang.ApiMember

/** One parameter of a call, as the tooltip shows it: `state: Bundle`. */
internal data class SignatureParam(val name: String, val type: String)

/**
 * What the tooltip draws for the call the caret is inside.
 *
 * [activeIndex] is the argument the caret sits in, not the one it is typing — the caret is past the
 * comma that ends the previous argument the moment the second one starts, so counting commas is the
 * only reading that stays right while the argument is still empty.
 */
internal data class SignatureInfo(
    val name: String,
    val params: List<SignatureParam>,
    val activeIndex: Int,
    val returnType: String? = null,
)

/** The call surrounding the caret: which function, and which of its arguments. */
internal data class CallSite(
    val name: String,
    /** The dotted part before the name, e.g. `androidx.compose` in `TextPainter.apply`. */
    val qualifier: String?,
    val openParen: Int,
    val argIndex: Int,
)

/**
 * The innermost call whose parentheses are still open at [cursor].
 *
 * One forward pass over the text up to the caret, keeping a stack of open parentheses and a comma
 * count for each. A stack rather than a walk backwards because backwards cannot tell a `(` inside a
 * string from one that opens a call without knowing the string's own extent, and a `(` inside
 * `"text ("` would otherwise swallow everything after it.
 *
 * Returns null when the caret is not inside any call, and when it is inside a call whose name is
 * nothing the identifier rules can read — `if (`, `while (`, `for (` are all ruled out below by
 * their keyword, so they never reach the resolver.
 */
internal fun callSiteAt(source: String, cursor: Int): CallSite? {
    val at = cursor.coerceIn(0, source.length)
    val opens = ArrayList<Int>()
    val commas = ArrayList<Int>()
    var i = 0
    while (i < at) {
        val ch = source[i]
        // A raw string first: its contents are code as far as the highlighter is concerned, but its
        // closing delimiter is three quotes, so checking for `"` first would end it at the first
        // quote inside.
        if (ch == '"' && source.startsWith("\"\"\"", i)) {
            val close = source.indexOf("\"\"\"", i + 3)
            i = if (close < 0 || close >= at) at else close + 3
            continue
        }
        if (ch == '"' || ch == '\'') {
            i = skipLiteral(source, i, at)
            continue
        }
        if (ch == '/' && i + 1 < at && source[i + 1] == '/') {
            val nl = source.indexOf('\n', i)
            i = if (nl < 0 || nl >= at) at else nl + 1
            continue
        }
        if (ch == '/' && i + 1 < at && source[i + 1] == '*') {
            val close = source.indexOf("*/", i + 2)
            i = if (close < 0 || close + 2 > at) at else close + 2
            continue
        }
        when (ch) {
            '(' -> {
                opens.add(i)
                commas.add(0)
            }
            ',' -> if (commas.isNotEmpty()) commas[commas.lastIndex] = commas.last() + 1
            ')', '[', '{' -> if (opens.isNotEmpty()) {
                opens.removeAt(opens.lastIndex)
                commas.removeAt(commas.lastIndex)
            }
            else -> Unit
        }
        i++
    }
    if (opens.isEmpty()) return null

    val open = opens.last()
    val name = identifierBefore(source, open) ?: return null
    if (name.text in CallKeywords) return null
    // A call is qualified when a dot sits immediately before the name — `super.onCreate(` has one,
    // `Text(` does not. Reading the dot from the name's own start is what makes that the test; a dot
    // two characters back happens to be a dot only by coincidence.
    var qualifier: String? = null
    val dot = name.start - 1
    if (dot >= 0 && source[dot] == '.') {
        var q = dot
        while (q > 0 && isIdentChar(source[q - 1])) q--
        if (q < dot) qualifier = source.substring(q, dot)
    }
    return CallSite(name = name.text, qualifier = qualifier, openParen = open, argIndex = commas.last())
}

/** Index just past the `(` at [at]. */
private fun skipLiteral(source: String, at: Int, limit: Int): Int {
    val quote = source[at]
    var i = at + 1
    while (i < limit) {
        val ch = source[i]
        if (ch == '\\') {
            i += 2
            continue
        }
        if (ch == quote) return i + 1
        // A newline ends an unterminated literal; without this a stray apostrophe in a comment
        // would swallow the rest of the file and hide every call after it.
        if (ch == '\n') return i + 1
        i++
    }
    return limit
}

private data class Ident(val text: String, val start: Int)

private fun identifierBefore(source: String, openParen: Int): Ident? {
    var i = openParen - 1
    while (i >= 0 && source[i].isWhitespace()) i--
    if (i < 0) return null
    val end = i + 1
    while (i >= 0 && isIdentChar(source[i])) i--
    if (i == end - 1) return null
    return Ident(source.substring(i + 1, end), i + 1)
}

private fun isIdentChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

private val CallKeywords = setOf(
    "if", "while", "for", "when", "catch", "return", "do", "else", "try",
)

private val FunDeclaration = Regex(
    "\\bfun\\s+(?:<[^>]*>\\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\\.)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
)

/**
 * The parameters a `fun` in this buffer declares under [name].
 *
 * Read from the buffer rather than from the symbol table because the table records a function's
 * name and return type but not what it takes — and the call being written is almost always to a
 * function the reader is looking at.
 *
 * The nearest declaration to the caret wins, so a later `private fun load()` shadows an earlier one
 * the same way the compiler reads it. A `fun` whose parameter list does not parse is skipped
 * rather than reported as empty: an empty list means "takes nothing", which is a different claim.
 */
internal fun declaredParams(source: String, name: String): List<SignatureParam>? =
    declaredSignatures(source, name).lastOrNull()?.params

/** Every `fun` in this buffer declared under [name], nearest the caret last. */
internal data class DeclaredSignature(
    val params: List<SignatureParam>,
    val returnType: String?,
    val start: Int,
)

internal fun declaredSignatures(source: String, name: String): List<DeclaredSignature> {
    val out = ArrayList<DeclaredSignature>()
    for (match in FunDeclaration.findAll(source)) {
        if (match.groupValues[1] != name) continue
        val close = matchingParen(source, match.range.last) ?: continue
        val parsed = splitParams(source.substring(match.range.last + 1, close)) ?: continue
        val rest = source.indexOf('\n', close).let { if (it < 0) source.length else it }
        val tail = source.substring(close + 1, rest).trim()
        val returnType = if (!tail.startsWith(":")) {
            null
        } else {
            tail.substring(1).substringBefore("{").substringBefore("=").trim().takeIf { it.isNotEmpty() }
        }
        out.add(DeclaredSignature(parsed, returnType, match.range.first))
    }
    return out.sortedBy { it.start }
}

/** The declared return type of a `fun` in this buffer, for the tooltip's trailing colon. */
internal fun declaredReturnType(source: String, name: String): String? =
    declaredSignatures(source, name).lastOrNull { it.returnType != null }?.returnType

/** The `)` matching the `(` at [open], or null when it is not closed. */
internal fun matchingParen(source: String, openParen: Int): Int? {
    var depth = 0
    var i = openParen
    while (i < source.length) {
        when (source[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return i
            }
            else -> Unit
        }
        i++
    }
    return null
}

/**
 * Splits a parameter list on its top-level commas, then each part on the `:` that ends its name.
 *
 * Null when any part has no `:` — an untyped parameter is not a parse failure the reader needs to be
 * told about, but reporting it as an untakes-anything signature would be a lie.
 */
internal fun splitParams(list: String): List<SignatureParam>? {
    val trimmed = list.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = ArrayList<String>()
    var depth = 0
    var start = 0
    var i = 0
    while (i < trimmed.length) {
        when (trimmed[i]) {
            '(', '[', '<' -> depth++
            ')', ']', '>' -> depth--
            '"' -> {
                val end = trimmed.indexOf('"', i + 1)
                i = if (end < 0) trimmed.length else end
            }
            ',' -> if (depth == 0) {
                parts.add(trimmed.substring(start, i))
                start = i + 1
            }
            else -> Unit
        }
        i++
    }
    parts.add(trimmed.substring(start))
    val out = ArrayList<SignatureParam>(parts.size)
    for (part in parts) {
        val one = part.trim().removeSuffix(",")
        if (one.isEmpty()) continue
        val colon = one.indexOf(':')
        if (colon <= 0) return null
        val name = one.substring(0, colon).trim()
        val type = one.substring(colon + 1)
            .substringBefore('=')
            .trim()
            .removeSuffix(",")
        if (name.isEmpty() || type.isEmpty()) return null
        out.add(SignatureParam(name, type))
    }
    return out
}

/** An API member's parameters, which arrive as one semicolon-separated blob of raw text. */
internal fun apiParams(member: ApiMember): List<SignatureParam> =
    member.params.mapNotNull { raw ->
        val one = raw.trim()
        if (one.isEmpty()) return@mapNotNull null
        val colon = one.indexOf(':')
        if (colon < 0) {
            SignatureParam(name = "", type = one.substringBefore('=').trim())
        } else {
            SignatureParam(
                name = one.substring(0, colon).trim(),
                type = one.substring(colon + 1).substringBefore('=').trim(),
            )
        }
    }

/**
 * The signature for the call the caret is in, or null when there is no call, or when the name is
 * nothing this project can describe.
 *
 * A function written in the buffer beats an Android one of the same name, because the reader is
 * looking at the definition when they write the call.
 */
internal fun signatureHelp(source: String, cursor: Int, index: AndroidApiIndex): SignatureInfo? =
    signatureHelpAll(source, cursor, index).firstOrNull()

/**
 * Every signature the call the caret is in could mean: each matching buffer declaration, then each
 * matching platform member. The tooltip shows one and counts the rest, so an overloaded call reads
 * `1/3` instead of silently picking.
 */
internal fun signatureHelpAll(source: String, cursor: Int, index: AndroidApiIndex): List<SignatureInfo> {
    val call = callSiteAt(source, cursor) ?: return emptyList()
    val out = ArrayList<SignatureInfo>()
    // Nearest declaration first, so the single-result answer stays what it always was: the
    // definition the reader is most likely looking at.
    val declared = declaredSignatures(source, call.name)
    val orderedDeclared = declared.lastOrNull()?.let { nearest ->
        listOf(nearest) + declared.filterNot { it === nearest }
    }.orEmpty()
    for (found in orderedDeclared) {
        out.add(
            SignatureInfo(
                name = call.name,
                params = found.params,
                activeIndex = namedOrPositionalIndex(
                    source = source,
                    cursor = cursor,
                    openParen = call.openParen,
                    positional = call.argIndex,
                    params = found.params,
                ),
                returnType = found.returnType,
            ),
        )
    }
    val qualified = call.qualifier?.takeIf { it.firstOrNull()?.isUpperCase() == true }
    val candidates = when {
        qualified != null -> index.membersOf(qualified)
        else -> index.membersNamed(call.name)
    }.filter { it.name == call.name }
    // The arity match first, as before: with three arguments typed, the three-argument version is
    // the one being written.
    val arity = call.argIndex + 1
    val ordered = candidates.filter { it.params.size == arity } +
        candidates.filter { it.params.size != arity && it.params.isNotEmpty() } +
        candidates.filter { it.params.isEmpty() }
    for (member in ordered) {
        val params = apiParams(member)
        out.add(
            SignatureInfo(
                name = call.name,
                params = params,
                activeIndex = namedOrPositionalIndex(
                    source = source,
                    cursor = cursor,
                    openParen = call.openParen,
                    positional = call.argIndex,
                    params = params,
                ),
                returnType = member.type.takeIf { it.isNotBlank() && it != "void" },
            ),
        )
    }
    return out
}

/**
 * Which argument the caret is on: the named one when the text being typed says `name =`, otherwise
 * the positional count of commas.
 *
 * A named argument highlights its matching parameter wherever it sits, so `color = …` lights up
 * `color` even when it is not the argument the commas would point at.
 */
internal fun namedOrPositionalIndex(
    source: String,
    cursor: Int,
    openParen: Int,
    positional: Int,
    params: List<SignatureParam>,
): Int {
    val named = namedArgumentAt(source, cursor, openParen)?.let { name ->
        params.indexOfFirst { it.name == name }.takeIf { it >= 0 }
    }
    return (named ?: positional).coerceIn(0, params.size.coerceAtLeast(0))
}

/**
 * The parameter name being assigned at the caret, or null when this argument is positional.
 *
 * Reads the current argument's text — everything after the last top-level comma or the opening
 * paren — and matches `name =` with nothing after the `=` yet. Strings and nested calls are
 * stepped over, so a comma inside either cannot split the argument early.
 */
internal fun namedArgumentAt(source: String, cursor: Int, openParen: Int): String? {
    val at = cursor.coerceIn(0, source.length)
    if (openParen < 0 || openParen >= at) return null
    var depth = 0
    var argStart = openParen + 1
    var i = openParen + 1
    while (i < at) {
        when (source[i]) {
            '"', '\'' -> i = skipLiteral(source, i, at)
            '(','[','{' -> depth++
            ')',']','}' -> depth--
            ',' -> if (depth == 0) argStart = i + 1
            else -> Unit
        }
        i++
    }
    val arg = source.substring(argStart.coerceIn(0, at), at)
    val equals = arg.indexOf('=')
    if (equals < 0) return null
    // `==` compares; only a bare `=` assigns.
    if (arg.getOrNull(equals + 1) == '=') return null
    val name = arg.substring(0, equals).trim()
    if (name.isEmpty() || !name.all { it.isLetterOrDigit() || it == '_' }) return null
    return name
}

/** The signature as plain text, for tests and for anything that cannot draw boxes. */
internal fun signaturePlainText(info: SignatureInfo): String = buildString {
    append(info.name)
    append('(')
    info.params.forEachIndexed { index, param ->
        if (index > 0) append(", ")
        append(param.name)
        append(": ")
        append(param.type)
    }
    append(')')
    info.returnType?.takeIf { it.isNotBlank() }?.let {
        append(": ")
        append(it)
    }
}

/**
 * The active parameter's text, or null when the index points past the end.
 *
 * The tooltip draws this inside the highlighted box; the test below pins the same answer without
 * needing a screen.
 */
internal fun activeParamText(info: SignatureInfo): String? =
    info.params.getOrNull(info.activeIndex)?.let { "${it.name}: ${it.type}" }

/**
 * A one-line description of a parameter, for the tooltip's second line.
 *
 * Null for a name not in here. The tooltip keeps the line and leaves it empty rather than changing
 * height, so a signature never jumps under the caret because one of its parameters is unfamiliar.
 */
internal fun describeParam(name: String): String? = ParamDescriptions[name]

/**
 * The forty parameters worth a description.
 *
 * Kept short on purpose. A sentence invented for a parameter nobody asked about is a sentence that
 * can be wrong, and a wrong hint is worse than a blank line — so this covers the names that appear
 * in almost every Android and Compose call and leaves the rest to the type itself.
 */
private val ParamDescriptions: Map<String, String> = mapOf(
    "text" to "The text to display.",
    "modifier" to "To apply to this layout.",
    "content" to "The content of the layout.",
    "contentColor" to "Colour applied to the content.",
    "color" to "The colour to apply.",
    "background" to "The background to draw.",
    "onClick" to "Called when this is tapped.",
    "onValueChange" to "Called when the value changes.",
    "value" to "The current value.",
    "enabled" to "Whether this can be interacted with.",
    "visible" to "Whether this is shown.",
    "name" to "The name of this thing.",
    "id" to "The identifier of this thing.",
    "title" to "The title to show.",
    "subtitle" to "The line under the title.",
    "label" to "The text shown beside or above this.",
    "placeholder" to "Shown when this is empty.",
    "style" to "The text style to apply.",
    "fontSize" to "The size of the text, in scale-independent pixels.",
    "fontWeight" to "How bold the text is.",
    "fontFamily" to "The typeface to use.",
    "colorScheme" to "The colours used by the app.",
    "shape" to "The corners of this shape.",
    "elevation" to "How high this sits above its neighbours.",
    "size" to "How big this is.",
    "width" to "How wide this is.",
    "height" to "How tall this is.",
    "padding" to "Space kept inside this.",
    "items" to "The things to show, one for each row.",
    "count" to "How many of these there are.",
    "key" to "What identifies this item.",
    "index" to "Which one this is, counting from zero.",
    "data" to "The value carried.",
    "result" to "What the call returned.",
    "context" to "The context this runs in.",
    "intent" to "What this screen was opened with.",
    "savedInstanceState" to "The state to restore the screen with.",
    "duration" to "How long, in milliseconds.",
    "message" to "The text to show.",
    "callback" to "Called when this finishes.",
)
/** How many parameters carry a written description. Exposed for the tests that keep it honest. */
internal fun describeParamCount(): Int = ParamDescriptions.size
