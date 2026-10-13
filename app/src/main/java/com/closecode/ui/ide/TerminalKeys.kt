package com.closecode.ui.ide

/**
 * The control code a key produces when CTRL is held, or null when the key has none.
 *
 * This is the ASCII convention, unchanged since the 1960s and still what every terminal sends: CTRL
 * plus a letter produces the control code whose number is the letter's position in the alphabet, so
 * CTRL+C is 3 and CTRL+Z is 26. The six punctuation keys below the `Z` were given the codes that
 * were left over, and every terminal has sent them ever since.
 *
 * Uppercase is folded rather than rejected. CTRL+SHIFT+C is still ETX on a real terminal, and a
 * keyboard that reports the shift would otherwise hand back nothing at all.
 */
internal fun controlChar(ch: Char): Char? = when {
    ch in 'a'..'z' -> ((ch - 'a') + 1).toChar()
    ch in 'A'..'Z' -> ((ch - 'A') + 1).toChar()
    // NUL and the six codes below Z, all written out rather than typed as invisible bytes: a source
    // file full of control characters is unreadable and unmaintainable.
    ch == '@' || ch == ' ' -> '\u0000'
    ch == '[' -> '\u001B'
    ch == '\\' -> '\u001C'
    ch == ']' -> '\u001D'
    ch == '^' -> '\u001E'
    ch == '_' -> '\u001F'
    else -> null
}

/** Whether a key has a CTRL meaning at all. A key bar should grey out the rest rather than lie. */
internal fun hasControlChar(ch: Char): Boolean = controlChar(ch) != null

/**
 * What CTRL plus [ch] should do to a command line, before the shell is involved.
 *
 * Everything here is the shell's own line editor, which a pipe-driven shell does not have: there is
 * no readline behind it, so the app has to be it. That is not a workaround — with the process fed
 * through a pipe the shell never sees the keys at all, and the only way to give them meaning is to
 * act on them here.
 *
 * CTRL+C is deliberately absent: in a real terminal it raises an interrupt, which a pipe cannot
 * deliver. It is handled by killing the process instead, which is the same effect.
 */
internal enum class ControlAction {
    NONE, LINE_START, LINE_END, CLEAR_LINE, CLEAR_AFTER, DELETE_WORD, DELETE_FORWARD, CLEAR_SCREEN
}

/** What CTRL plus [ch] means on the command line. */
internal fun controlActionFor(ch: Char): ControlAction = when (controlChar(ch)) {
    // The readline bindings, which every terminal has used since emacs: A and E to the ends of the
    // line, K to kill forwards, U to kill the whole line, W to kill a word, L to clear the screen.
    '\u0001' -> ControlAction.LINE_START
    '\u0005' -> ControlAction.LINE_END
    '\u000B' -> ControlAction.CLEAR_AFTER
    '\u0015' -> ControlAction.CLEAR_LINE
    '\u0017' -> ControlAction.DELETE_WORD
    '\u000C' -> ControlAction.CLEAR_SCREEN
    '\u0004' -> ControlAction.DELETE_FORWARD
    '\u0003' -> ControlAction.NONE // interrupt, which the process kill stands in for
    else -> ControlAction.NONE
}

/** The text and caret left after deleting the character just after the caret. */
internal fun deleteForward(text: String, caret: Int): Pair<String, Int> {
    val at = caret.coerceIn(0, text.length)
    if (at >= text.length) return text to at
    return text.substring(0, at) + text.substring(at + 1) to at
}

/**
 * The text and caret left after deleting from [caret] to the end of the line.
 *
 * CTRL+K in every terminal that has ever had one. The name says what it keeps rather than what it
 * removes, because "what is left of the line" is what the caller actually wants.
 */
internal fun deleteToEnd(text: String, caret: Int): Pair<String, Int> {
    val at = caret.coerceIn(0, text.length)
    return text.substring(0, at) to at
}

/**
 * The text and caret left after deleting from the start of the line to [caret].
 *
 * CTRL+U, which readline calls `unix-line-discard`. It discards backwards and stops at the cursor
 * rather than emptying the line outright, so anything typed after the caret is left alone — on a
 * phone that is not a detail, because the caret is rarely at the end.
 */
internal fun deleteToCaret(text: String, caret: Int): Pair<String, Int> {
    val at = caret.coerceIn(0, text.length)
    return text.substring(at) to 0
}

/**
 * The text and caret left after deleting the word before [caret].
 *
 * A word ends at whitespace, and CTRL+W removes the run of non-whitespace the caret sits in — or,
 * when the caret is already in whitespace, the whitespace as well, so a second CTRL+W takes a
 * further word rather than appearing to do nothing.
 */
internal fun deleteWordBefore(text: String, caret: Int): Pair<String, Int> {
    val at = caret.coerceIn(0, text.length)
    var start = at
    if (start == 0) return text to at
    val isSpace = { c: Char -> c.isWhitespace() }
    if (isSpace(text[start - 1])) {
        while (start > 0 && isSpace(text[start - 1])) start--
    } else {
        while (start > 0 && !isSpace(text[start - 1])) start--
    }
    return text.substring(0, start) + text.substring(at) to start
}

/**
 * A remembered command and where the reader is standing in the list.
 *
 * History is newest first, and [HistoryStep.index] counts into it the same way — index 0 is the most
 * recent command, and an index one past the end means "back at the draft you were typing". Keeping
 * the draft as a real slot rather than as a special case is what lets the down arrow come back to it
 * instead of off the end into nothing.
 */
internal data class HistoryStep(val text: String, val index: Int)

/**
 * The entry one step further back, or null when there is nothing to go back to.
 *
 * History is newest first, so "further back" is a *larger* index. Walking it the other way is the
 * easy mistake to make here, and the symptom is an up arrow that appears to do nothing after the
 * first press.
 */
internal fun previousHistoryEntry(history: List<String>, index: Int, draft: String): HistoryStep? {
    if (history.isEmpty()) return null
    return when {
        // Standing at the draft: the first press back shows the most recent command.
        index >= history.size -> HistoryStep(history[0], 0)
        index + 1 < history.size -> HistoryStep(history[index + 1], index + 1)
        // Already at the oldest command. Staying put is the honest answer; wrapping round to the
        // newest would make the arrow look broken at exactly the place a reader stops at it.
        else -> HistoryStep(history[index], index)
    }
}

/** The entry one step further forward, or null when the reader is already at the draft. */
internal fun nextHistoryEntry(history: List<String>, index: Int, draft: String): HistoryStep? = when {
    history.isEmpty() -> null
    index >= history.size -> null
    // One past the newest is the draft again, which is where the reader started.
    index == 0 -> HistoryStep(draft, history.size)
    else -> HistoryStep(history[index - 1], index - 1)
}

/** How many commands are remembered. Old enough that a year-old command is still reachable. */
internal const val TERMINAL_HISTORY_LIMIT = 200

/**
 * The list with [command] added at the front, de-duplicated and capped.
 *
 * Most recent first, so a command already at the top does not fill the list with itself — which is
 * what happens otherwise when a command is run repeatedly, and it pushes everything else out.
 */
internal fun addToHistory(history: List<String>, command: String): List<String> {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) return history
    val withoutIt = history.filterNot { it == trimmed }
    return (listOf(trimmed) + withoutIt).take(TERMINAL_HISTORY_LIMIT)
}

/**
 * How a modifier key is behaving right now.
 *
 * Termux's three-state model, and the reason it has three: tapping CTRL once arms the next key only,
 * which is what a shortcut needs; tapping it a second time locks it so a whole word can be typed in
 * capitals; tapping a third time disarms it. A single on/off cannot do both, and a modifier that
 * silently applied to two keystrokes because the user was slow would be worse than none.
 */
internal enum class ModifierLatch { OFF, LATCHED, LOCKED }

/** What a tap on a modifier moves the latch to. */
internal fun nextLatch(current: ModifierLatch): ModifierLatch = when (current) {
    ModifierLatch.OFF -> ModifierLatch.LATCHED
    ModifierLatch.LATCHED -> ModifierLatch.LOCKED
    ModifierLatch.LOCKED -> ModifierLatch.OFF
}

/** Whether a key typed now should carry the modifier. */
internal fun modifierApplies(latch: ModifierLatch): Boolean = latch != ModifierLatch.OFF

/** Whether consuming the modifier is what a tap does, as opposed to leaving it latched for later. */
internal fun modifierConsumes(latch: ModifierLatch): Boolean = latch == ModifierLatch.LATCHED

/**
 * The character a key bar button inserts, with any active modifier applied.
 *
 * CTRL turns the key into its control code, and ALT prefixes the escape byte — the encoding a shell
 * expects for a meta key, and the only one it can be handed without a real terminal underneath.
 * An unmodified key is simply itself.
 */
internal fun keyBarCharacter(ch: Char, ctrl: Boolean, alt: Boolean): String {
    val core = if (ctrl) controlChar(ch)?.toString().orEmpty() else ch.toString()
    if (core.isEmpty()) return ""
    return if (alt) ESC + core else core
}