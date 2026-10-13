package com.closecode.ui.ide

/**
 * Auto close brackets.
 *
 * Type an opening bracket or quote and its closer appears with the caret left between them. The
 * whole thing is a pure function of the buffer before and after the keystroke, so it can be tested
 * without a text field — and so nothing here can reach the editor's state.
 */

private val ClosingFor = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
)

private fun lineStartAt(text: String, index: Int): Int {
    var i = index.coerceIn(0, text.length) - 1
    while (i >= 0) {
        if (text[i] == '\n') return i + 1
        i--
    }
    return 0
}

private fun lineEndAt(text: String, index: Int): Int {
    var i = index.coerceIn(0, text.length)
    while (i < text.length) {
        if (text[i] == '\n') return i
        i++
    }
    return text.length
}

/**
 * True when [index] in [line] sits inside a string or char literal, or past a `//` comment.
 *
 * Line-local on purpose: the decision only ever concerns the line being typed on, and a whole-file
 * scan per keystroke would put this straight back on the typing path.
 */
internal fun insideLiteralOrComment(line: String, index: Int): Boolean {
    var inString = false
    var inChar = false
    var escaped = false
    val limit = index.coerceIn(0, line.length)
    for (i in 0 until limit) {
        val ch = line[i]
        when {
            escaped -> escaped = false
            ch == '\\' && (inString || inChar) -> escaped = true
            inString && ch == '"' -> inString = false
            inChar && ch == '\'' -> inChar = false
            !inString && !inChar && ch == '/' && i + 1 < line.length && line[i + 1] == '/' -> return true
            !inString && !inChar && ch == '"' -> inString = true
            !inString && !inChar && ch == '\'' -> inChar = true
        }
    }
    return inString || inChar
}

/**
 * The closing half of a typed opener, or null when this keystroke must not pair.
 *
 * [before] is the buffer as it was, [after] the buffer with the keystroke already applied, and
 * [caret] sits just past what was typed. Returning null means "leave the buffer exactly as it is",
 * so the caller has one thing to do rather than a set of conditions to re-check.
 *
 * Deliberately conservative:
 *  - only a single inserted character counts, so a paste or a selection replacement never pairs;
 *  - a bracket typed inside a string or a line comment is refused, where it would change what the
 *    rest of the line means;
 *  - when the closer already follows the caret nothing is added, so closing an open pair does not
 *    produce `())`.
 */
internal fun autoClosePair(before: String, after: String, caret: Int): Pair<String, Int>? {
    if (after.length != before.length + 1) return null
    if (caret <= 0 || caret > after.length) return null

    val typed = after[caret - 1]
    val closer = ClosingFor[typed] ?: return null
    if (caret < after.length && after[caret] == closer) return null

    val start = lineStartAt(after, caret)
    val line = after.substring(start, lineEndAt(after, caret))
    // The literal test covers what preceded the keystroke, never the character itself: a quote
    // typed on a plain line opens a literal, and testing after the fact would refuse every quote
    // the app ever pairs. "Already inside a string or a comment" is a fact about the text before
    // the key, so that is exactly what is looked at.
    if (insideLiteralOrComment(line, caret - 1 - start)) return null

    return after.substring(0, caret) + closer + after.substring(caret) to caret
}
