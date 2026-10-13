package com.closecode.ui.ide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.theme.AnsiColors
import com.closecode.ui.theme.TerminalPalettes
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono

internal const val ESC = ''

/** A tab stop every [TabStop] columns, as a terminal does it. */
internal const val TabStop = 8

/**
 * A terminal color: one of the sixteen palette entries, or an exact RGB triple.
 *
 * Extended colors (256-palette and truecolor) keep their exact value instead of collapsing onto
 * the sixteen: collapsing drew every program color as black or white, which is how `git diff`
 * lost all of its reds and greens.
 */
internal sealed interface AnsiColor {
    /** One of the sixteen, 0–7 ordinary and 8–15 bright. */
    data class Palette(val index: Int) : AnsiColor

    /** An exact color, packed 0xRRGGBB. */
    data class Rgb(val rgb: Int) : AnsiColor
}

/**
 * How one run of characters is drawn.
 *
 * A value, not a mutation: the parser carries one of these as it walks a line and emits a new one
 * whenever an escape sequence changes it. Nothing else has to be undone, because a "return to
 * default" is just the empty style again rather than an inverse of whatever came before.
 */
internal data class AnsiStyle(
    val fg: AnsiColor? = null,
    val bg: AnsiColor? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    /** [AnsiStyle] with every attribute off — what "default" means to a program. */
    companion object {
        val Plain = AnsiStyle()
    }
}

/** A stretch of one line that shares a style. */
internal data class AnsiRun(val text: String, val style: AnsiStyle)

/** What a line of terminal output is: its visible text, and how to draw each stretch of it. */
internal data class AnsiLine(val runs: List<AnsiRun>) {
    /** The line with every escape removed — the text, without any of the drawing. */
    val text: String get() = runs.joinToString("") { it.text }
}

/**
 * One line of terminal output, parsed into the runs it is actually drawn as.
 *
 * Programs colour their output with escape sequences: `ESC [ 31 m` before the text, `ESC [ 0 m`
 * after it. Shown raw, that is a line of unreadable noise, so the sequences are consumed here and
 * the colour is drawn instead.
 *
 * Three kinds of sequence are handled, and only three:
 *
 * - **SGR** (`ESC [ … m`) sets the drawing style. This is the only one that affects what the
 *   reader sees, and the only one most programs emit.
 * - **Everything else in the CSI family** — cursor moves, erases, scroll regions — is dropped. It
 *   describes motion on a screen that has no meaning here, because each line is laid out where its
 *   predecessor finished.
 * - **OSC** (`ESC ] … BEL`) is dropped too. It is titles and hyperlinks, neither of which a list of
 *   lines can show.
 *
 * Unknown sequences are removed rather than printed. A program that emits something unknown should
 * lose the noise, not litter the panel with it.
 */
internal fun parseAnsi(line: String): AnsiLine {
    // A carriage return means "go back and overwrite", which is how progress output works: the
    // same line printed over and over with a rising percentage. Only the last pass is worth
    // keeping, and everything before the final carriage return is exactly that superseded pass.
    // The colour of that final pass comes with it, since the escapes sit after the return.
    val start = line.lastIndexOf('\r') + 1
    val out = StringBuilder()
    val runs = mutableListOf<AnsiRun>()
    var style = AnsiStyle.Plain
    var runStart = -1
    var column = 0

    fun closeRun(at: Int) {
        if (at > runStart) runs += AnsiRun(out.substring(runStart, at), style)
    }

    fun beginRun() {
        runStart = out.length
    }

    beginRun()
    var i = start
    while (i < line.length) {
        val ch = line[i]
        if (ch == ESC) {
            val sequence = readEscape(line, i)
            if (sequence == null) {
                // An escape with nothing after it to close it: a program killed mid-write, most
                // likely. Everything from the escape onwards belongs to the unfinished sequence, so
                // the rest of the line is dropped with it. Skipping only the escape would leave its
                // introducer — a bare "[" — sitting in the output as though the program had meant it.
                break
            }
            if (sequence.sgr) {
                closeRun(out.length)
                style = applySgr(style, sequence.params)
                beginRun()
            }
            i = sequence.next
            continue
        }
        when (ch) {
            // Tabs are expanded rather than emitted: the drawn text is a Compose string with no
            // tab stop of its own, and a literal tab would push the rest of the line out of the
            // column it started in.
            '\t' -> {
                val width = TabStop - (column % TabStop)
                repeat(width) { out.append(' ') }
                column += width
            }
            // Everything else a program can emit that has no meaning here: bells, form feeds, and
            // the seven-bit C0 controls. Dropped so they cannot disturb the layout.
            else -> {
                if (ch.code >= 0x20 || ch == ' ') {
                    out.append(ch)
                    column++
                }
            }
        }
        i++
    }
    closeRun(out.length)
    // Drop a trailing empty run so plain text does not carry a zero-length style at its end.
    val trimmed = runs.lastOrNull()?.takeIf { it.text.isEmpty() }?.let { runs.dropLast(1) } ?: runs
    return AnsiLine(trimmed)
}

/** One escape sequence found in the line, and where the line continues after it. */
private class Escape(val sgr: Boolean, val params: IntArray, val next: Int)

/**
 * Reads the escape sequence starting at [i], or null when there is not a complete one there.
 *
 * The shape is fixed by the language: `ESC`, then an introducer, then parameters and
 * intermediates, then a final byte in the 0x40–0x7E range. Only the final byte decides whether the
 * sequence was about colour, which is why the whole run is scanned before anything is interpreted.
 */
private fun readEscape(line: String, i: Int): Escape? {
    val next = line.getOrNull(i + 1) ?: return null
    when (next) {
        // CSI: ESC [ — parameters, then a final byte.
        '[' -> {
            var j = i + 2
            while (j < line.length && !(line[j].code in 0x40..0x7E)) j++
            if (j >= line.length) return null
            val body = line.substring(i + 2, j)
            val final = line[j]
            return Escape(
                sgr = final == 'm',
                params = if (final == 'm') parseParams(body) else IntArray(0),
                next = j + 1,
            )
        }
        // OSC: ESC ] — runs to BEL, or to ESC \.
        ']' -> {
            var j = i + 2
            while (j < line.length) {
                if (line[j] == '') break
                if (line[j] == ESC && line.getOrNull(j + 1) == '\\') return Escape(false, IntArray(0), j + 2)
                j++
            }
            if (j >= line.length) return null
            return Escape(false, IntArray(0), j + 1)
        }
        // A two-character escape such as ESC c, the terminal reset.
        else -> return Escape(false, IntArray(0), i + 2)
    }
}

/**
 * The numbers in an SGR sequence.
 *
 * An empty parameter list means "reset everything" — `ESC [ m` is written by some programs in place
 * of `ESC [ 0 m` — so it answers as a single zero rather than as nothing at all, which would leave
 * the previous style in force.
 */
private fun parseParams(body: String): IntArray {
    if (body.isEmpty()) return intArrayOf(0)
    val parts = body.split(';', ':')
    val out = IntArray(parts.size) { index ->
        parts[index].trim().toIntOrNull() ?: 0
    }
    return out
}

/** The style after [params] are applied to [style]. */
internal fun applySgr(style: AnsiStyle, params: IntArray): AnsiStyle {
    var next = style
    var i = 0
    while (i < params.size) {
        when (val code = params[i]) {
            0 -> next = AnsiStyle.Plain
            1 -> next = next.copy(bold = true)
            2 -> next = next.copy(dim = true)
            3 -> next = next.copy(italic = true)
            4 -> next = next.copy(underline = true)
            // 5 and 6 are blink. A terminal that honours them is unreadable, so they are treated as
            // a request for the brighter half instead — which is what a reader expects anyway.
            5, 6 -> next = next.copy()
            7 -> next = next.copy(inverse = true)
            // 21 is "double underline" on some terminals and "bold off" on others. The widely
            // supported reading is the one that matches 22, so bold goes off.
            21, 22 -> next = next.copy(bold = false, dim = false)
            23 -> next = next.copy(italic = false)
            24 -> next = next.copy(underline = false)
            27 -> next = next.copy(inverse = false)
            39 -> next = next.copy(fg = null)
            49 -> next = next.copy(bg = null)
            // 38 and 48 introduce an extended colour: 5;n is the 256-colour palette, 2;r;g;b is
            // truecolor. Both are resolved to the nearest of the sixteen the theme actually has,
            // rather than dropped — dropping would lose the text's colour entirely.
            38, 48 -> {
                val (resolved, consumed) = readExtendedColor(params, i)
                next = if (code == 38) next.copy(fg = resolved) else next.copy(bg = resolved)
                i += consumed
            }
            else -> if (code in 30..37 || code in 40..47 || code in 90..97 || code in 100..107) {
                next = if (code in 40..47 || code in 100..107) {
                    next.copy(bg = normalizeColorCode(code))
                } else {
                    next.copy(fg = normalizeColorCode(code))
                }
            } else {
                next
            }
        }
        i++
    }
    return next
}

/** An SGR colour code as a palette entry: 0-7 ordinary, 8-15 bright. */
private fun normalizeColorCode(code: Int): AnsiColor.Palette = when (code) {
    in 30..37 -> AnsiColor.Palette(code - 30)
    in 90..97 -> AnsiColor.Palette(code - 90 + 8)
    in 40..47 -> AnsiColor.Palette(code - 40)
    in 100..107 -> AnsiColor.Palette(code - 100 + 8)
    else -> AnsiColor.Palette(0)
}

/**
 * Resolves an extended colour to its exact value, and says how many parameters it used.
 *
 * The 256 palette's cube and grays map to their real RGB (steps of 0, 95, 135, 175, 215, 255;
 * grays at 8 + 10 per step), and truecolor passes through untouched. Both used to collapse
 * onto black or white, which is how every 256-color program drew wrong.
 */
private fun readExtendedColor(params: IntArray, at: Int): Pair<AnsiColor, Int> {
    val mode = params.getOrNull(at + 1) ?: return AnsiColor.Palette(0) to 0
    return when (mode) {
        5 -> {
            val index = params.getOrNull(at + 2) ?: 0
            (ansi256(index) to 2)
        }
        2 -> {
            val r = params.getOrNull(at + 2)?.coerceIn(0, 255) ?: 0
            val g = params.getOrNull(at + 3)?.coerceIn(0, 255) ?: 0
            val b = params.getOrNull(at + 4)?.coerceIn(0, 255) ?: 0
            AnsiColor.Rgb((r shl 16) or (g shl 8) or b) to 4
        }
        else -> AnsiColor.Palette(0) to 0
    }
}

/** The 256-color palette as an exact color: 0–15 the sixteen, 16–231 the cube, 232–255 gray. */
internal fun ansi256(index: Int): AnsiColor = when (index) {
    in 0..15 -> AnsiColor.Palette(index)
    in 16..231 -> {
        val cube = index - 16
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        val r = steps[cube / 36]
        val g = steps[(cube % 36) / 6]
        val b = steps[cube % 6]
        AnsiColor.Rgb((r shl 16) or (g shl 8) or b)
    }
    else -> {
        val level = (8 + 10 * (index.coerceIn(232, 255) - 232)).coerceIn(0, 255)
        AnsiColor.Rgb((level shl 16) or (level shl 8) or level)
    }
}

/**
 * The colours and attributes of one run, resolved against the active theme.
 *
 * Inverse is resolved here rather than in the parser: it is the one attribute whose meaning depends
 * on what it is drawn on, so `fg` and `bg` have to have been chosen before the swap can happen.
 */
private fun spanStyleFor(style: AnsiStyle, ansi: AnsiColors, defaultFg: Color, defaultBg: Color): SpanStyle {
    // Palette entries read the reader's chosen sixteen; exact colors draw as themselves, so a
    // program's 256-color red is red rather than the nearest of sixteen.
    fun resolve(which: AnsiColor?, fallback: Color): Color = when (which) {
        is AnsiColor.Palette -> ansi.colorOf(which.index)
        is AnsiColor.Rgb -> Color(which.rgb or 0xFF000000.toInt())
        null -> fallback
    }
    val fg = resolve(style.fg, defaultFg)
    val bg = resolve(style.bg, defaultBg)
    val swapped = if (style.inverse) bg else fg
    // Dim is a partly transparent foreground rather than a grey, so it dims the colour it was asked
    // to dim instead of flattening it to one. `SpanStyle` has no alpha of its own, so it has to be
    // folded into the colour — which is also why it is applied after the inverse swap: a dim
    // inverted run dims the colour that ended up in front, not the one that started behind.
    val foreground = if (style.dim) swapped.copy(alpha = DimAlpha) else swapped
    // Unspecified unless the program asked for a background (or inversion put one in front):
    // painting the editor's own background explicitly would be the same color but a different
    // span, and spans are what the tests count.
    val background = if (style.inverse) fg else if (style.bg != null) bg else Color.Unspecified
    return SpanStyle(
        color = foreground,
        background = background,
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (style.underline) TextDecoration.Underline else null,
    )
}

/** How far a dim run is turned down. Enough to read as quieter, not enough to read as disabled. */
internal const val DimAlpha = 0.62f

/** The line's drawing, ready for a [Text]. */
@Composable
internal fun ansiAnnotated(line: String): AnnotatedString {
    val c = LocalPageColors.current
    // The palette the reader chose, not the app's own: a program asking for "green" means the
    // green of the scheme they are used to. Falls back to the app's own when the setting names a
    // scheme this build does not carry, rather than drawing nothing at all.
    val settings = LocalSettings.current
    val ansi = TerminalPalettes[settings.terminalTheme] ?: c.ansi
    val parsed = remember(line) { parseAnsi(line) }
    return remember(parsed, ansi, c) {
        buildAnnotatedString {
            parsed.runs.forEach { run ->
                if (run.style == AnsiStyle.Plain) {
                    append(run.text)
                } else {
                    pushStyle(spanStyleFor(run.style, ansi, c.tx, c.bg))
                    append(run.text)
                    pop()
                }
            }
        }
    }
}

/**
 * One line of terminal output, drawn with whatever colour the program asked for.
 *
 * Plain text takes the short path: no parse result is remembered per line for output that has no
 * escape in it, which is most of what a shell prints.
 */
@Composable
internal fun TerminalLine(
    line: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit? = null,
    fontFamily: FontFamily = Mono,
    color: Color? = null,
) {
    val c = LocalPageColors.current
    val hasEscapes = remember(line) { line.indexOf(ESC) >= 0 }
    // Fixed size: 11.5sp on 19sp rows, so output, echoes and the prompt line up as one transcript.
    val size = fontSize ?: 11.5.sp
    Text(
        text = if (hasEscapes) ansiAnnotated(line) else AnnotatedString(line),
        color = color ?: c.tx,
        fontSize = size,
        fontFamily = fontFamily,
        lineHeight = 19.sp,
        // Long lines wrap rather than clip: a clipped path or URL is information lost, while a
        // wrapped one is only taller.
        softWrap = true,
        modifier = modifier,
    )
}
/**
 * The terminal's text size in points.
 *
 * Clamped rather than taken as given: a preference written by an older build, or edited by hand,
 * must never be able to set the terminal to a size nothing can be read at. It is clamped to the
 * ends of the range the settings screen offers, so the answer is always a size somebody chose.
 */
internal fun terminalFontSize(settings: com.closecode.ui.settings.Settings): Int =
    settings.terminalFontSize.coerceIn(
        com.closecode.ui.settings.TerminalFontSizeChoices.first().toInt(),
        com.closecode.ui.settings.TerminalFontSizeChoices.last().toInt(),
    )
