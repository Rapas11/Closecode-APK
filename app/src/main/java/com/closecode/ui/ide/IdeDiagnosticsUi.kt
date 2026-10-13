package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.closecode.lang.Diagnostic
import com.closecode.lang.DiagnosticCodes
import com.closecode.lang.Severity
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * Editor metrics shared by the line numbers and the inline labels so their rows line up.
 *
 * Spec section 3: the code line height is exactly 20 dp and that number is load bearing. The gutter,
 * underlines, pills and lightbulb are all positioned against it, so it must not be "measured" into
 * a different value. Pinch zoom multiplies it rather than replacing it.
 */
val CodeLineHeight = 20.sp
val CodeFontSize = 12.sp

/**
 * Gutter width, exactly 56 dp with no border.
 *
 * Each item owns a fixed slot measured from the left edge — git marker 0–3, error bar 4–6,
 * lightbulb 6–24, line numbers ending at 38, fold arrow 41–53 — so numbers never shift when an
 * icon appears. There is no divider line and no gap after it: the code starts at x = 56 dp.
 */
val GutterWidth = 56.dp

/**
 * The editor's single top padding, 4 dp. The gutter and the code share it, because they
 * are siblings inside one scroll container. Adding a second pad to the gutter is what used to drop
 * every line number and status dot half a line below its own code.
 */
val EditorTopPadding = 4.dp

/** Alias kept for callers that read the gutter's padding by name; it is the editor's, not its own. */
val GutterPadding = EditorTopPadding

/**
 * Extra inset the gutter applies on top of [EditorTopPadding]. It must stay zero: any value here
 * shifts the numbers and dots away from the rows they label.
 */
val GutterTopInset = 0.dp

/** Gutter numbers are right aligned to end at 38 dp from the gutter's left edge. */
val GutterNumberPadding = 18.dp

/** No inset between gutter and code: the code area starts at exactly x = 56 dp. */
val CodeLeftInset = 0.dp

/** Left edge of the code column, measured from the left of the editor viewport. */
val CodeColumnStart = GutterWidth + CodeLeftInset

/**
 * What a version-control comparison says about one line. The comparison itself arrives in a later
 * phase; this type is the shape the gutter already draws, so that phase feeds data rather than
 * redesigning the row.
 */
enum class GitMark {
    Added,
    Modified,
    Deleted,
}

/**
 * The marks stacked at the gutter's left edge, in the order they are drawn.
 *
 * [GutterBarWidth] is the diagnostic bar: a 2 dp red rule on lines with an error, next to the git
 * marker and never over it. It is a bar and not a dot because a dot in a column of line numbers
 * reads as another digit at a glance, and a bar cannot be mistaken for anything.
 */
val GutterBarWidth = 2.dp
val GutterBarLeft = 4.dp

/**
 * The quick-fix bulb's slot: 6 to 24 dp, an 18 dp tile with a 14 dp glyph on it.
 *
 * The tile is smaller than a finger, so the tap area is 36 by 20 dp centred on the slot — the same
 * trick the scrollbar uses, where what the eye sees stays small and what a thumb may hit does not.
 */
val GutterBulbSize = 18.dp
val GutterBulbLeft = 6.dp
val GutterBulbIcon = 14.dp
val GutterBulbTapWidth = 36.dp
val GutterBulbTapHeight = 20.dp

/**
 * Where the bulb's tap area starts. The slot sits 6 dp from the edge, so a centred 36 dp area
 * would start at -3 dp — and negative padding throws the moment the bulb is drawn. Clamped to
 * the edge instead; the tile keeps its slot inside the area either way.
 */
internal val GutterBulbTapLeft: androidx.compose.ui.unit.Dp =
    (GutterBulbLeft - (GutterBulbTapWidth - GutterBulbSize) / 2)
        .coerceAtLeast(0.dp)

/** The git marker's slot: 0 to 3 dp, with a 1 dp inset top and bottom. */
val GutterGitWidth = 3.dp
val GutterGitInset = 1.dp

/** The fold arrow's slot: 41 to 53 dp. The tap area is 32 dp wide and the full row tall. */
val GutterFoldLeft = 41.dp
val GutterFoldSize = 12.dp
val GutterFoldTapWidth = 32.dp

/**
 * Where the fold chevron's tap area starts, same centring idea as the bulb's. Clamped for the
 * same reason: if these numbers ever move, the geometry test says so before a phone does.
 */
internal val GutterFoldTapLeft: androidx.compose.ui.unit.Dp =
    (GutterFoldLeft - (GutterFoldTapWidth - GutterFoldSize) / 2)
        .coerceAtLeast(0.dp)

/** Width reserved for the status dot and the line number. */
val NumberColumnWidth = GutterWidth

/**
 * Shortens a long file name through the middle, keeping the start (which names the file) and
 * the extension (which says what it is). End-cutting keeps the start and loses exactly the
 * part that tells one `...ViewModel.kt` from another, so the middle goes instead.
 */
internal fun ellipsizeMiddle(name: String, maxChars: Int = 26): String {
    if (name.length <= maxChars || maxChars < 8) return name
    val dot = name.lastIndexOf('.')
    val tail = if (dot > 0) name.substring(dot) else ""
    val headLen = (maxChars - tail.length - 1).coerceAtLeast(8)
    if (headLen + tail.length + 1 >= name.length) return name
    return name.take(headLen) + "…" + tail
}

/**
 * Character offset of a 1-based [line] and [column], for text-layout lookups.
 */
fun offsetOfLineColumn(source: String, line: Int, column: Int): Int {
    if (line < 1 || column < 1) return 0
    var lineStart = 0
    var current = 1
    var i = 0
    while (i < source.length && current < line) {
        if (source[i] == '\n') {
            current++
            lineStart = i + 1
        }
        i++
    }
    return (lineStart + (column - 1)).coerceIn(0, source.length)
}

/**
 * One pass over the buffer that answers every question scrolling asks, so a row-crossed frame
 * never walks the file again.
 *
 * Built once per text change and read per scroll frame: line starts (binary-searched), the brace
 * depth at each line start, and through those the offset of any line/column and the line of any
 * offset. The depth scan mirrors [openBlockDepth][com.closecode.ui.ide.openBlockDepth] character
 * for character — strings, escapes and both comment kinds included — so a depth read here
 * colours exactly what the old substring-and-scan coloured.
 */
class LineIndex(val text: String) {    /** 0-based offset of each 1-based line's first character. Always non-empty. */
    val starts: IntArray
    /** Brace depth at each line start, in the same slots as [starts]. */
    val depths: IntArray
    val lineCount: Int get() = starts.size

    init {
        val s = ArrayList<Int>(1024)
        val d = ArrayList<Int>(1024)
        s.add(0)
        d.add(0)
        var depth = 0
        var inString = false
        var quote = ' '
        var inLineComment = false
        var blockComment = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inLineComment -> if (ch == '\n') inLineComment = false
                blockComment > 0 -> {
                    if (ch == '/' && i + 1 < text.length && text[i + 1] == '*') blockComment++
                    else if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') blockComment--
                }
                inString -> {
                    if (ch == '\\') i++
                    else if (ch == quote) inString = false
                }
                ch == '/' && i + 1 < text.length && text[i + 1] == '/' -> inLineComment = true
                ch == '/' && i + 1 < text.length && text[i + 1] == '*' -> blockComment++
                ch == '"' || ch == '\'' -> {
                    inString = true
                    quote = ch
                }
                ch == '{' || ch == '(' || ch == '[' -> depth++
                ch == '}' || ch == ')' || ch == ']' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if (ch == '\n') {
                s.add(i + 1)
                d.add(depth)
            }
            i++
        }
        // A trailing newline opens a line past the end; it takes no depth and no offset.
        if (s.size > 1 && s.last() >= text.length) {
            s.removeAt(s.lastIndex)
            d.removeAt(d.lastIndex)
        }
        starts = s.toIntArray()
        depths = d.toIntArray()
    }

    /** 0-based offset of a 1-based line's first character. */
    fun lineStart(line: Int): Int = starts[(line - 1).coerceIn(0, starts.lastIndex)]

    /** Offset of a 1-based [line] and [column], clamped into the text. */
    fun offsetOf(line: Int, column: Int): Int {
        if (line < 1 || column < 1) return 0
        return (lineStart(line) + (column - 1)).coerceIn(0, text.length)
    }

    /** 1-based line holding [offset]. */
    fun lineOf(offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        var lo = 0
        var hi = starts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= safe) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    /** Brace depth at the start of a 0-based line — the base the slice highlight builds on. */
    fun depthAtLine0(line0: Int): Int = depths[line0.coerceIn(0, depths.lastIndex)]
}

/**
 * Whether this character actually draws something in the editor.
 *
 * Two kinds of character are invisible but are not whitespace, so asking "is this line blank" the
 * obvious way gets the wrong answer for them: U+200B zero-width space, U+200C and U+200D, U+FEFF and
 * U+2060. All of them are Unicode format characters, and all of them draw nothing. A line made only
 * of them looks empty, so it must count as empty, or the editor numbers it, treats it as the last
 * line of code and parks the caret there.
 */
fun Char.drawsGlyph(): Boolean = !isWhitespace() && category != CharCategory.FORMAT

/** True when [line] holds at least one character that would actually be visible on screen. */
fun hasVisibleContent(line: String): Boolean = line.any { it.drawsGlyph() }

/**
 * Spec 6.3: where the caret belongs when a file is opened, just past the last character of the last
 * line that has code in it.
 *
 * Every scaffolded file ends with a newline, so "the end of the file" is an empty line that has no
 * code on it. Opening there invented a numbered row past the last real line and put everything the
 * user typed, and every error about it, one row lower than the line they were looking at. The text
 * itself is never rewritten, so saving still keeps the file's trailing newline.
 */
fun caretOffsetAtEndOfCode(source: String): Int {
    var offset = source.length
    while (offset > 0) {
        val newline = source.lastIndexOf('\n', offset - 1)
        val lineStart = newline + 1
        val line = source.substring(lineStart, offset)
        // A blank first line is still the caret's line: there is nothing before it to fall back to.
        if (hasVisibleContent(line)) return offset
        if (lineStart == 0) return lineStart
        offset = newline
    }
    return 0
}

/**
 * How many numbered rows to draw: from 1 to the last line that shows something, or to the caret's
 * own line, whichever is further down.
 *
 * [caretLine] exists for one reason: pressing Enter leaves the caret sitting on a blank row, and a
 * row with no number is a row the reader does not know is there. Rows *below* the caret stay
 * unnumbered, so walking past the code still does not fill the gutter with numbers for blank tails.
 */
fun gutterRowCount(lines: List<String>, caretLine: Int = 1): Int {
    var last = lines.size
    while (last > 0 && !hasVisibleContent(lines[last - 1])) last--
    val toCaret = caretLine.coerceIn(1, lines.size.coerceAtLeast(1))
    return maxOf(last, toCaret).coerceAtLeast(1)
}

/**
 * Problems grouped by the 1-based line they sit on, in source order.
 */
@Immutable
data class ProblemsByLine(
    val byLine: Map<Int, List<Diagnostic>> = emptyMap(),
    val linesWithErrors: Set<Int> = emptySet(),
    val linesWithWarnings: Set<Int> = emptySet(),
    val all: List<Diagnostic> = emptyList(),
) {
    fun on(line: Int): List<Diagnostic> = byLine[line].orEmpty()

    /** Worst problem on a line; errors win over warnings. */
    fun worstOn(line: Int): Diagnostic? =
        on(line).minByOrNull { if (it.severity == Severity.ERROR) 0 else 1 }

    companion object {
        fun from(diagnostics: List<Diagnostic>): ProblemsByLine {
            if (diagnostics.isEmpty()) return ProblemsByLine()
            val byLine = LinkedHashMap<Int, MutableList<Diagnostic>>()
            for (d in diagnostics) byLine.getOrPut(d.line) { ArrayList() }.add(d)
            return ProblemsByLine(
                byLine = byLine,
                linesWithErrors = byLine.filterValues { list -> list.any { it.severity == Severity.ERROR } }
                    .keys,
                linesWithWarnings = byLine.keys.filterTo(HashSet()) { line ->
                    byLine[line]!!.none { it.severity == Severity.ERROR }
                },
                all = diagnostics,
            )
        }
    }
}
/** What one Enter should insert at the caret, and where in it the caret lands. */
internal data class EnterIndent(val text: String, val caret: Int)

/**
 * The indentation one press of Enter should give the new line.
 *
 * Three rules, in this order, all measured off the line being split rather than off any setting:
 *
 *  - a line that **ends with a closing brace** steps back out one level, because the block it was
 *    inside has just ended and code typed after it belongs outside that block
 *  - a line that **ends with an opener** — `{`, `(`, `[`, or Kotlin's `:` and `->` — steps in one
 *  - anything else keeps the indentation it already has
 *
 * The step is the reader's own indent size, so the one number that governs the Tab key governs this
 * too. It used to be two spaces hard-coded, which meant a file set to four spaces got two.
 *
 * When [nextChar] is the closer the app paired with the opener being split, that closer is pushed
 * down onto a line of its own at the opener's indentation. Leaving it where it was put the `}` after
 * the new line's padding, so every closing brace crept rightward.
 */
internal fun enterIndent(lineSoFar: String, nextChar: Char?, tabSize: Int): EnterIndent {
    val step = tabSize.coerceAtLeast(1)
    val base = lineSoFar.takeWhile { it == ' ' || it == '\t' }
    val trimmed = lineSoFar.trimEnd()

    val closes = trimmed.endsWith("}") || trimmed.endsWith(")") || trimmed.endsWith("]")
    val opens = trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") ||
        trimmed.endsWith(":") || trimmed.endsWith("->")

    val pad = when {
        closes -> dedentColumns(base, step)
        opens -> base + " ".repeat(step)
        else -> base
    }

    val pairedCloser = opens && nextChar == '}'
    val inserted = if (pairedCloser) "\n$pad\n$base" else "\n$pad"
    return EnterIndent(inserted, 1 + pad.length)
}

/**
 * Removes up to [step] columns from the end of an indent.
 *
 * A tab counts as a whole step rather than as one character: a tab-indented file must come back out
 * one tab, and treating it as one column would shave four spaces off a two-space indent and land the
 * caret at the margin.
 */
internal fun dedentColumns(base: String, step: Int): String {
    if (base.isEmpty()) return base
    var keep = base.length
    var removed = 0
    while (keep > 0 && removed < step) {
        removed += if (base[keep - 1] == '\t') step else 1
        keep--
    }
    return base.substring(0, keep)
}

/**
 * A typed Enter, read as a transform on the text the IME handed back rather than by intercepting the
 * key — so Enter still inserts a newline when the autocomplete popup is open, and only Tab or a tap
 * accepts a suggestion.
 *
 * Returns the new text and caret, or null when the edit was not a plain Enter.
 */
fun autoIndentAfterEnter(oldText: String, newText: String, caret: Int, tabSize: Int): Pair<String, Int>? {
    // A typed Enter is the only edit that grows the text by exactly one character and leaves the
    // caret straight after a newline. Anything else passes through untouched.
    if (newText.length != oldText.length + 1) return null
    if (caret <= 0 || caret > newText.length) return null
    if (newText[caret - 1] != '\n') return null
    val lineStart = newText.lastIndexOf('\n', caret - 2) + 1
    val ins = enterIndent(
        lineSoFar = newText.substring(lineStart, caret - 1),
        nextChar = newText.getOrNull(caret),
        tabSize = tabSize,
    )
    if (ins.text == "\n") return null
    val next = newText.substring(0, caret - 1) + ins.text + newText.substring(caret)
    return next to (caret - 1 + ins.caret)
}

/**
 * Exactly one newline per press of Enter, written here rather than left to the IME.
 *
 * Soft keyboards cannot be trusted to hand back one row per press — some insert the key's newline
 * and the composing editor's on top of it — and an editor that drops the caret two rows down for one
 * press cannot be undone by pressing harder. The selection, if there is one, is replaced the way
 * every editor replaces it.
 *
 * [indent] is false when the reader has switched auto-indent off, which is the only difference
 * between this and a plain newline. The setting used to be read on the soft-keyboard path alone, so
 * turning it off did nothing at all on a hardware keyboard.
 *
 * Returns the new text and the caret's new offset.
 */
fun enterEdit(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    tabSize: Int,
    indent: Boolean = true,
): Pair<String, Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val previousNewline = if (start <= 0) -1 else text.lastIndexOf('\n', start - 1)
    val lineStart = previousNewline + 1
    val ins = if (indent) {
        enterIndent(text.substring(lineStart, start), text.getOrNull(start), tabSize)
    } else {
        EnterIndent("\n", 1)
    }
    val next = text.substring(0, start) + ins.text + text.substring(end)
    return next to (start + ins.caret)
}

/**
 * Top edge of zero-based [index] as the renderer actually placed it, falling back to the row model
 * when the layout is not ready, does not contain that row, or the row is outside [window].
 *
 * The fallback stays in absolute document pixels, which is what makes this work for both windows at
 * once: the gutter asks for rows the code's slice may not contain, and gets the row model for them,
 * while a row the slice *does* contain comes back from the renderer and is shifted down by
 * [CodeWindow.topPx] into the same coordinate space.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun lineTopFromLayout(
    layout: TextLayoutResult?,
    index: Int,
    lineHeightPx: Float,
    window: CodeWindow = CodeWindow.Full,
): Float {
    val sliceIndex = index - window.firstLine
    if (layout != null && sliceIndex in 0 until layout.lineCount) {
        runCatching { layout.getLineTop(sliceIndex) }.getOrNull()?.let { return window.topPx + it }
    }
    return index.coerceAtLeast(0) * lineHeightPx
}

/** Rows of slack above and below the viewport, so a fast fling never lands on an unmeasured gap. */
private const val GutterWindowOverhang = 10

/**
 * The same slack for the code text: ten rows above and below what is visible, so composing and
 * drawing stay inside the window on every scroll frame.
 */
private const val CodeWindowOverhang = 10

/**
 * The slice of zero-based line indices the gutter should actually compose for a scroll position.
 *
 * The gutter lives inside the same scroll container as the code, so it used to build one row per
 * line in the file — two thousand rows for a two thousand line file, on every recomposition of the
 * editor, of which about forty were ever on screen. This returns only the rows that can be visible,
 * plus [GutterWindowOverhang] above and below.
 *
 * A viewport that has not been measured yet returns every row: the safe answer, and only ever the
 * first frame. Same for a non-positive line height, which would otherwise divide into nonsense.
 */
internal fun gutterWindow(
    lineCount: Int,
    scrollTopPx: Float,
    viewportHeightPx: Float,
    lineHeightPx: Float,
): IntRange = windowRange(
    lineCount = lineCount,
    scrollTopPx = scrollTopPx,
    viewportHeightPx = viewportHeightPx,
    lineHeightPx = lineHeightPx,
    overhang = GutterWindowOverhang,
)

/**
 * Where the slice of the file that is actually drawn sits inside the whole file.
 *
 * The code no longer lays out every line: it renders only the rows that can be on screen, so the
 * renderer's own [TextLayoutResult] describes *that* slice and not the document. Every helper that
 * reads the layout therefore has to know which line the layout's row 0 came from, which character
 * its character 0 came from, and where in the scrolled content that row's top falls.
 *
 * [Full] is the identity — row 0 is line 1, character 0 is offset 0, the top is zero — and it is
 * the default on every helper below, so a caller (or a test) that holds a layout of the whole file
 * keeps exactly the behaviour it had before the windowing existed.
 */
data class CodeWindow(
    /** Zero-based index of the document line that became layout row 0. */
    val firstLine: Int = 0,
    /** Absolute character offset of the document that became layout character 0. */
    val startOffset: Int = 0,
    /** Content-space y of layout row 0's top edge, i.e. how far the slice was pushed down. */
    val topPx: Float = 0f,
) {
    companion object {
        val Full = CodeWindow()
    }
}

/**
 * Which layout row draws 1-based [line], or a negative number when [line] is above the window.
 *
 * The caller decides what a negative answer means: [lineTopPx] returns null so its caller falls
 * back to the row model, which is right for an overlay that is about to draw nothing anyway.
 */
internal fun sliceIndexForLine(line: Int, firstLine: Int): Int = (line - 1) - firstLine

/** Character index inside the rendered slice that corresponds to absolute [offset]. */
internal fun mapOffsetToSlice(offset: Int, startOffset: Int, sliceLength: Int): Int =
    (offset - startOffset).coerceIn(0, sliceLength)

/** Content-space top of the slice, in the same pixels the overlays measure everything else in. */
internal fun windowTopPx(firstLine: Int, lineHeightPx: Float): Float =
    firstLine.coerceAtLeast(0) * lineHeightPx

/**
 * The slice of zero-based line indices the *code text* should lay out for a scroll position.
 *
 * Same shape as [gutterWindow], with a row more of slack either side: the gutter can afford a
 * momentary gap because it only draws numbers, but the text layer is the thing the user reads, so a
 * fling that lands on an unmeasured gap would show blank code.
 *
 * An unmeasured viewport returns every row — the safe answer, and only ever the first frame, which
 * is exactly when nothing has been measured to window against yet.
 */
internal fun codeWindowRange(
    lineCount: Int,
    scrollTopPx: Float,
    viewportHeightPx: Float,
    lineHeightPx: Float,
): IntRange = windowRange(
    lineCount = lineCount,
    scrollTopPx = scrollTopPx,
    viewportHeightPx = viewportHeightPx,
    lineHeightPx = lineHeightPx,
    overhang = CodeWindowOverhang,
)

/** One composable's worth of slack above and below the viewport, shared by both window callers. */
internal fun windowRange(
    lineCount: Int,
    scrollTopPx: Float,
    viewportHeightPx: Float,
    lineHeightPx: Float,
    overhang: Int,
): IntRange {
    if (lineCount <= 0) return IntRange.EMPTY
    if (viewportHeightPx <= 0f || lineHeightPx <= 0f) return 0 until lineCount
    val visibleRows = (viewportHeightPx / lineHeightPx).toInt() + 1
    val first = (scrollTopPx / lineHeightPx).toInt() - overhang
    val from = first.coerceIn(0, lineCount - 1)
    val to = (from + visibleRows + overhang * 2).coerceAtMost(lineCount - 1)
    return from..to
}

/**
 * The document text [range] covers, so colourizing the slice is the same work as colourizing those
 * rows of the whole file.
 *
 * The rows are joined with the newline `String.lines()` took out of them, so a slice and the same
 * rows of the buffer are the same characters — which is what lets a windowed editor be coloured
 * with the one highlighter it already has. Out of range answers an empty string rather than
 * throwing: an empty slice draws nothing, which is the honest answer for a window that matches
 * nothing.
 */
internal fun sliceOfLines(lines: List<String>, range: IntRange): String {
    if (lines.isEmpty() || range.isEmpty()) return ""
    val from = range.first
    if (from < 0 || from > lines.lastIndex) return ""
    val to = range.last.coerceAtMost(lines.lastIndex)
    if (to < from) return ""
    return buildString {
        for (i in from..to) {
            if (i > from) append('\n')
            append(lines[i])
        }
    }
}

/**
 * Status dot and line number for every row, drawn in the same box as the code so the two share one
 * coordinate space and one vertical scroll. The row height comes from [lineHeightPx], which is
 * measured off the rendered text rather than assumed, and the number carries that same height as
 * its line height so its baseline lands on the code's baseline.
 *
 * Red is used only for real errors and amber only for warnings. The background is opaque so code
 * scrolled sideways passes underneath instead of colliding with the numbers.
 *
 * The root is a Box on purpose. A Column places each child at the accumulated y of its slot and the
 * child's own `offset` then *adds* to that, so every number landed at about twice its true line and
 * the gutter drifted down past the end of the code. In a Box every child starts at one shared origin,
 * so `offset` alone decides the position and the row top is absolute — the same value the pills and
 * the bulb use.
 *
 * Only the rows that can be on screen are composed; see [gutterWindow]. The container still spans
 * the whole file, because an opaque gutter that stopped halfway down would let the code show
 * through below it.
 */
@Composable
fun LineNumberLayer(
    lineCount: Int,
    lineHeightPx: Float,
    problems: ProblemsByLine,
    caretLine: Int,
    // The layout the glyphs were drawn with. Row positions come from its line tops, so a number and
    // the code it labels cannot drift apart even if the row model ever disagrees with the renderer.
    layout: TextLayoutResult? = null,
    // Spec 6.3: the first import line carries a small "▸" affordance at the right of its row.
    foldLine: Int = 0,
    // Every line that shows a fold chevron, and the subset currently folded. The behaviour arrives
    // in a later phase; the slot, the glyph and the tap area are drawn now.
    foldableLines: Set<Int> = emptySet(),
    foldedLines: Set<Int> = emptySet(),
    onFoldTap: (Int) -> Unit = {},
    // The one row whose quick-fix bulb is drawn, and what tapping it does. Null draws none. The bulb
    // used to float over the code a row above the caret; it belongs in the margin, with the other
    // marks that describe the line rather than on top of the line's text.
    bulbLine: Int? = null,
    onBulbTap: (() -> Unit)? = null,
    // The version-control marks for this file, by 1-based line. Empty until the comparison
    // that computes them arrives; the gutter draws whatever it is given, so that phase feeds data
    // rather than redesigning the row.
    gitMarks: Map<Int, GitMark> = emptyMap(),
    // Display row to source line, for folded files: the gutter counts display rows but
    // numbers source lines, so line 5 still reads 5 with lines 2-4 collapsed above it. Null
    // means identity, which is every unfolded file.
    sourceLines: List<Int>? = null,
    // Read here rather than at the call site on purpose: a scroll then recomposes only this layer,
    // not the whole editor around it. Null means the caller does not scroll, so compose every row.
    scrollState: ScrollState? = null,
    // The other half of the window calculation: how many rows fit under the gutter at once.
    viewportHeightPx: Float = 0f,
    // The code only renders a slice of the document now, so a row the slice does not contain has no
    // measured top to read. The gutter is told the same window and falls back to the row model for
    // those, which is why it never drifts away from the code it labels.
    window: CodeWindow = CodeWindow.Full,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeight = with(density) { lineHeightPx.toDp() }
    // The Box has no children whose sizes add up any more, so the layer carries the height the old
    // Column produced by stacking them: one row per numbered line.
    val gutterHeight = rowHeight * lineCount.toFloat()
    // The number is drawn with the row's own height as its line height, centred the same way the code
    // is, so its baseline sits exactly on the code's baseline.
    val numberLineHeight = with(density) { lineHeightPx.toSp() }

    Box(
        modifier
            .width(GutterWidth)
            .height(gutterHeight)
            .background(c.editor)
            .padding(top = GutterTopInset),
    ) {
        val rows = if (scrollState == null) {
            0 until lineCount
        } else {
            gutterWindow(lineCount, scrollState.value.toFloat(), viewportHeightPx, lineHeightPx)
        }
        for (index in rows) {
            val line = index + 1
            // The number on the row is the source line, so folding never renumbers the file:
            // collapsing lines 2-4 leaves 5 reading 5, not 2.
            val numbered = sourceLines?.getOrNull(index) ?: line
            val errored = line in problems.linesWithErrors
            val warned = !errored && line in problems.linesWithWarnings
            val onCaret = line == caretLine
            // The renderer's own line top when it has one, and the row model otherwise. Both are
            // content-space, so the number lines up with its glyphs at any scroll position.
            val rowTop = lineTopFromLayout(layout, index, lineHeightPx, window)

            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, rowTop.roundToInt()) }
                    .fillMaxWidth()
                    .height(rowHeight)
                    // The cursor row's half of the full-width active line. The code side is a
                    // sibling box behind the scrolling column; this paints the gutter's 56 dp of
                    // the same row, so the highlight reads as one band from x = 0.
                    .background(if (onCaret) c.activeLineBg else Color.Transparent),
            ) {
                // Every mark below is placed with `align(TopStart)` and its own padding rather than
                // `Modifier.offset` on a centred child, because an offset on a centred child is
                // measured from where centring put it — which is why the old dot drifted onto the
                // line number from line 100 up instead of sitting at the edge where it was asked
                // to sit. Each owns a fixed slot, so numbers never shift when an icon appears.
                // Looked up by source line, not display row: with lines 2-4 folded away, the
                // mark for source line 5 is drawn on the row numbered 5, wherever that row sits.
                when (gitMarks[numbered]) {
                    GitMark.Added -> Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 0.dp, top = GutterGitInset, bottom = GutterGitInset)
                            .width(GutterGitWidth)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topEnd = 2.dp,
                                    bottomEnd = 2.dp,
                                ),
                            )
                            .background(c.gitAdded),
                    )
                    GitMark.Modified -> Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 0.dp, top = GutterGitInset, bottom = GutterGitInset)
                            .width(GutterGitWidth)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topEnd = 2.dp,
                                    bottomEnd = 2.dp,
                                ),
                            )
                            .background(c.gitModified),
                    )
                    // A deletion has no line of its own, so the mark goes on the line that follows
                    // the deleted ones: a small triangle pointing at the gap, on the left edge.
                    GitMark.Deleted -> Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .size(GutterFoldTapWidth.coerceAtMost(6.dp), 8.dp)
                            .drawBehind {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, size.height / 2f)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(path, c.gitDeleted)
                            },
                    )
                    null -> Unit
                }
                // The error bar. Only error lines get one: the bar already shows it, so the number
                // stays quiet. Warnings keep their squiggle and nothing else.
                if (errored) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = GutterBarLeft)
                            .width(GutterBarWidth)
                            .fillMaxHeight()
                            .background(c.error),
                    )
                }
                if (bulbLine == line && onBulbTap != null) {
                    // The tile is 18 dp; the tap area is 36 by 20. It is centred on the slot
                    // when there is room, otherwise it starts at the gutter edge — raw centring
                    // math goes negative here, and negative padding crashes the app the moment
                    // the bulb appears. The tile is placed at the slot inside the area either
                    // way, so it lands exactly where it always did.
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = GutterBulbTapLeft)
                            .width(GutterBulbTapWidth)
                            .height(GutterBulbTapHeight)
                            .clickable(onClick = onBulbTap),
                    ) {
                        Box(
                            Modifier
                                .padding(
                                    start = GutterBulbLeft - GutterBulbTapLeft,
                                    top = (GutterBulbTapHeight - GutterBulbSize) / 2,
                                )
                                .size(GutterBulbSize)
                                .clip(RoundedCornerShape(5.dp))
                                .background(c.bulbTileBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            CcIcon(CcIcons.Bulb, c.bulbGlyph, GutterBulbIcon)
                        }
                    }
                }
                Text(
                    text = "$numbered",
                    color = if (onCaret) c.lineNumberActive else c.lineNumber,
                    fontSize = 11.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        // The row's own height as the number's line height, centred the way the code
                        // is, so the two baselines coincide.
                        lineHeight = numberLineHeight,
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                        ),
                    ),
                    fontFamily = Mono,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = GutterNumberPadding),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                // The fold chevron lives in its own slot at 41 to 53 dp, vertically centred. Down
                // means the block is open, right means it is folded; the tap area is 32 dp wide
                // and the full row tall, so it never needs precision.
                if (line == foldLine || line in foldableLines) {
                    val folded = line in foldedLines
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = GutterFoldTapLeft)
                            .width(GutterFoldTapWidth)
                            .fillMaxHeight()
                            .clickable { onFoldTap(line) },
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon(
                            CcIcons.Chevron,
                            c.foldArrow,
                            GutterFoldSize,
                            Modifier.graphicsLayer {
                                rotationZ = if (folded) 0f else 90f
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top edge, in pixels, of the row that renders 1-based [line] of code, inside the shared editor
 * coordinate space.
 *
 * This is the single row model. The gutter numbers, the auto-scroll and the fallback positions for
 * overlays all call it, so the numbers and the code cannot drift apart the way two separately
 * maintained row heights did.
 */
fun rowTopPx(line: Int, lineHeightPx: Float, topPaddingPx: Float): Float =
    topPaddingPx + (line - 1).coerceAtLeast(0) * lineHeightPx

/**
 * Top edge, in pixels, of the row that renders 1-based [line] of code.
 *
 * Pills are placed with this rather than by stacking rows, because stacking made every problem
 * consume two code lines and the labels drifted away from the code they describe.
 */
fun pillTopPx(line: Int, lineHeightPx: Float): Float =
    (line - 1).coerceAtLeast(0) * lineHeightPx

/**
 * How wide a problem pill's message is allowed to get before it starts ellipsising.
 *
 * A cap rather than a measurement, because the old measurement was taken from how much room was
 * left between the pill and the right edge of the *visible* window — so the message grew and shrank
 * as the reader scrolled sideways, and the same problem read `U…` on one screen and in full on the
 * next. A fixed cap means a long diagnostic always breaks at the same word, wherever the code is.
 *
 * It also stops the pill ballooning across the screen: the icon, the code label and the badge sit
 * outside it, so 220 dp of message leaves the whole pill comfortably inside a phone's width.
 */
val PillMaxMessageWidth = 220.dp

/**
 * Left edge of a problem pill: a little past the end of its line, in the scrolled content's own
 * coordinates, so the pill travels sideways with the text it belongs to.
 *
 * [lineEndX] is the right edge of the line's own text and [gapPx] is the air between them, so the
 * pill always begins *after* the code instead of on top of it.
 *
 * There is deliberately no clamp to the viewport here. Clamping to "the last position where it
 * still fits" is what put pills on top of the code: a long line — which is exactly the line that
 * has an error — pushed the pill past the screen edge, and pulling it back to fit dropped it over
 * the characters it was describing. Off screen to the right is the honest answer, and the reader
 * scrolls to it, because this layer is a child of the code column's horizontal scroller. Typing on
 * that line brings it into view on its own, since the caret already pulls the column sideways.
 */
fun pillAnchorX(lineEndX: Float, gapPx: Float): Float =
    (lineEndX + gapPx.coerceAtLeast(0f)).coerceAtLeast(0f)

/**
 * The pill's on-screen offset, in the layer's own pixels.
 *
 * Both halves are load bearing. Dropping [x] — which is what shipped — drew every pill on top of
 * the first characters of its own line, because the position computed from the line's real right
 * edge was thrown away one line before it reached the offset.
 */
internal fun pillOffsetOf(x: Float, top: Float): IntOffset =
    IntOffset(x.roundToInt(), top.roundToInt())

/**
 * Inline problem pills drawn on the code surface, anchored past the end of the line they describe.
 *
 * [anchorX] supplies the right edge of a line's text, measured from the text field that actually
 * rendered the code, so the pill lands past the real glyphs rather than an estimate. [gapPx] is the
 * air left between the text and the pill.
 *
 * Nothing here reads the viewport or the sideways scroll, and that is the point. This layer is a
 * child of the code column's horizontal scroller, so a pill drawn in content coordinates travels
 * with its own line automatically. The moment a position was derived from the visible edge instead,
 * two things went wrong at once: the pill had to be clamped to fit, which put it back over the code
 * on long lines, and its message was sized from the gap to that edge, which made the text change
 * width every time the reader scrolled. Removing both inputs removes both faults, and takes the
 * recomposition this layer used to pay on every frame of a sideways scroll with it.
 */
@Composable
fun InlineProblemLayer(
    problems: ProblemsByLine,
    lineHeightPx: Float,
    gapPx: Float = 0f,
    anchorX: (line: Int) -> Float?,
    yOfLine: (line: Int) -> Float?,
    onTap: (Diagnostic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rows = problems.byLine.entries.sortedBy { it.key }
    val messageWidth = with(density) { PillMaxMessageWidth }

    // No clip here on purpose. The pills sit on their own rows via Modifier.offset, which does not
    // contribute to measurement, so the box only ever measured itself as tall as the widest pill —
    // and clipped every pill below the first one away. The scrollers around this layer already
    // clip it to the viewport.
    Box(modifier) {
        rows.forEach { (line, list) ->
            // Spec 6.5: when a line has several problems, show the most severe one and a count.
            val primary = list.minByOrNull { if (it.severity == Severity.ERROR) 0 else 1 } ?: return@forEach
            val isWarning = primary.severity == Severity.WARNING
            val tone = if (isWarning) c.warnPillTx else c.pillText
            val fill = if (isWarning) c.warnPillBg else c.pillBg

            // The layout has not rendered this line yet, so there is no honest place to put the
            // pill. Anchoring at 0 instead would draw it over the first characters of the line.
            val lineEnd = anchorX(line) ?: return@forEach

            // Spec 6.5: past the end of the line's text, in the scrolled content's own
            // coordinates, so the pill travels sideways with its line and never covers it.
            val x = pillAnchorX(lineEnd, gapPx)
            // Spec 6.5: top = line top minus 1 dp.
            val top = (yOfLine(line) ?: pillTopPx(line, lineHeightPx)) -
                with(density) { 1.dp.toPx() }

            Row(
                Modifier
                    // The x is load bearing. Dropping it here drew every pill on top of the first
                    // characters of its own line, with no room of its own.
                    .offset { pillOffsetOf(x, top) }
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(fill)
                    .border(1.dp, c.pillBorder, RoundedCornerShape(9.dp))
                    // Spec 7: tapping a pill opens the Problems sheet at that problem. The pill sits
                    // past the end of the line's text, so it only ever takes taps aimed at itself.
                    .clickable { onTap(primary) }
                    .padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CcIcon(CcIcons.AlertCircle, tone, 12.dp)
                Text(
                    // Short form, cut after 22 characters: the pill names the problem, and the
                    // sheet it opens carries the full message.
                    text = primary.message.take(22),
                    color = tone,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Spec 6.5 severity icon: a circle with a cross for an error and a triangle with a bang for a
 * warning. Drawn rather than typed, so it matches the spec shape instead of depending on which
 * glyphs the bundled fonts happen to carry.
 */
@Composable
private fun SeverityGlyph(severity: Severity) {
    val c = LocalPageColors.current
    val tone = if (severity == Severity.WARNING) c.warnPillTx else c.errorPillTx
    // The prototype's own 24 x 24 glyph, so the pill's icon matches the reference shape.
    CcIcon(if (severity == Severity.WARNING) CcIcons.Warning else CcIcons.ErrorCircle, tone, 12.dp)
}

/** Spec 6.5 "stack" glyph for the multi-problem badge, in the pill's own tone. */
@Composable
private fun StackGlyph(tone: Color) {
    CcIcon(CcIcons.Stack, tone, 11.dp)
}

/**
 * Horizontal x of [offset] within the rendered text, in the same coordinate space as the code.
 *
 * [offset] is an absolute document offset, so when the renderer only holds [window]'s slice it is
 * mapped onto the slice first. Anything outside the slice clamps to the slice's own edge: the
 * overlays decide whether to draw at all from the line's top, which does answer null, so an x for a
 * character just off screen is never the thing that puts a label in the wrong place.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun xForOffset(
    layout: TextLayoutResult?,
    offset: Int,
    window: CodeWindow = CodeWindow.Full,
): Float? {
    if (layout == null) return null
    val safe = mapOffsetToSlice(offset, window.startOffset, layout.layoutInput.text.length)
    return runCatching { layout.getBoundingBox(safe).left }.getOrNull()
}

/**
 * Geometry read straight out of the rendered text rather than assumed from a line number.
 *
 * Every overlay in the editor positions itself through these, so nothing can drift from the code
 * if the font, the zoom level or the line height changes. All of them read the renderer's own line
 * metrics, the same source the gutter numbers are drawn from, so a label and the row it describes
 * cannot disagree.
 *
 * Measuring a *character* instead of the row was the old bug: a blank line's only character is its
 * newline, and a newline draws at the start of the next row, so the numbers, the dots and the pills
 * all landed somewhere else than the code they belonged to.
 */

/**
 * Top edge of the 1-based [line], as the renderer drew it, in absolute content pixels.
 *
 * Null when the line is outside the rendered slice — the caller falls back to the row model, which
 * is right for the overlays because the only lines they ever draw are the ones on screen, and those
 * are always inside the window.
 */
fun lineTopPx(
    layout: TextLayoutResult?,
    source: String,
    line: Int,
    window: CodeWindow = CodeWindow.Full,
): Float? {
    if (layout == null || line < 1) return null
    // Past the end of the text there is no row to measure; the caller falls back to the row model.
    if (line > source.count { it == '\n' } + 1) return null
    val index = sliceIndexForLine(line, window.firstLine)
    if (index < 0 || index >= layout.lineCount) return null
    return runCatching { window.topPx + layout.getLineTop(index) }.getOrNull()
}

/**
 * The 1-based line an absolute document offset falls on.
 *
 * Counted from the start of the file rather than read from the layout, because a bracket pair is
 * found across the whole buffer while only a window of it is ever rendered.
 */
internal fun lineOfOffset(source: String, offset: Int): Int {
    val safe = offset.coerceIn(0, source.length)
    var line = 1
    for (i in 0 until safe) {
        if (source[i] == '\n') line++
    }
    return line
}

/**
 * Offset of the last character a label on [line] should sit after.
 *
 * The line's own newline is never the answer when the line has text, because the renderer draws it
 * at the start of the *next* row; anchoring there put every label at x of zero, on top of the code
 * it was describing. A blank line has no such character, so it returns the line's start.
 */
fun endOfLineAnchorOffset(source: String, line: Int): Int {
    val start = offsetOfLineColumn(source, line, 1)
    if (start >= source.length) return source.length
    val newline = source.indexOf('\n', start)
    val end = if (newline < 0) source.length else newline
    return if (end > start) end - 1 else start
}

/**
 * Right edge of [line]'s text, i.e. where its label should begin.
 *
 * Read from the renderer's line metrics: the newline has no width of its own, so asking the
 * character at the end of the line for its box answered with the wrong edge and the label started
 * over the code instead of after it.
 */
/**
 * The last rendered row belonging to the one that starts at [firstRow].
 *
 * Word wrap is the only thing that makes a document line occupy more than one row, and it changes
 * exactly one thing for the overlays: the *end* of the line. The first row of a document line is
 * always at its own slice index, so tops needed no change — but a pill anchored to the right edge
 * of the first row would sit halfway along a wrapped line, and a guide stopping at the bottom of the
 * first row would end above the code it belongs to.
 *
 * A row continues the one before it when no newline separates their text, which is read off the
 * renderer rather than guessed from a width, so it stays right at any font size, zoom or gutter.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun lastRowForLine(layout: TextLayoutResult, firstRow: Int): Int {
    val text = layout.layoutInput.text
    var last = firstRow
    var i = firstRow
    while (i + 1 < layout.lineCount) {
        val start = runCatching { layout.getLineStart(i) }.getOrNull() ?: break
        val next = runCatching { layout.getLineStart(i + 1) }.getOrNull() ?: break
        if (next <= start) break
        if (text.substring(start, next).indexOf('\n') >= 0) break
        last = i + 1
        i++
    }
    return last
}

/** How many rendered rows a document line occupies, at least one. */
fun rowsForLine(layout: TextLayoutResult?, line: Int, window: CodeWindow = CodeWindow.Full): Int {
    val index = sliceIndexForLine(line, window.firstLine)
    if (layout == null || index < 0 || index >= layout.lineCount) return 1
    return lastRowForLine(layout, index) - index + 1
}

/**
 * The bottom edge of a document line, which with word wrap is below its last row rather than below
 * its first.
 */
fun lineBottomPx(
    layout: TextLayoutResult?,
    source: String,
    line: Int,
    lineHeightPx: Float,
    window: CodeWindow = CodeWindow.Full,
): Float {
    val top = lineTopPx(layout, source, line, window) ?: return line * lineHeightPx
    return top + rowsForLine(layout, line, window).coerceAtLeast(1) * lineHeightPx
}

/**
 * Right edge of [line]'s text, i.e. where its label should begin.
 *
 * Read from the renderer's line metrics: the newline has no width of its own, so asking the
 * character at the end of the line for its box answered with the wrong edge and the label started
 * over the code instead of after it. With word wrap on it is the *last* row of the line that ends
 * the text, not the first.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun endOfLineX(
    layout: TextLayoutResult?,
    source: String,
    line: Int,
    window: CodeWindow = CodeWindow.Full,
): Float? {
    if (layout == null || line < 1) return null
    val index = sliceIndexForLine(line, window.firstLine)
    if (index < 0 || index >= layout.lineCount) return null
    // The last row of the line, not its first: a wrapped line's text ends on the row below.
    runCatching { layout.getLineRight(lastRowForLine(layout, index)) }.getOrNull()?.let { return it }
    val anchor = mapOffsetToSlice(
        endOfLineAnchorOffset(source, line),
        window.startOffset,
        layout.layoutInput.text.length,
    )
    return runCatching { layout.getBoundingBox(anchor).left }.getOrNull()
}

/** Offset of the first character of the identifier the caret sits in or after. */
fun wordStartOffset(source: String, caret: Int): Int {
    val safe = caret.coerceIn(0, source.length)
    var start = safe
    fun isWord(ch: Char) = ch.isLetterOrDigit() || ch == '_'
    while (start > 0 && isWord(source[start - 1])) start--
    return start
}

/**
 * Width of one advance in the rendered text, used to convert "two characters" into pixels.
 *
 * The probe walks the string the renderer actually laid out rather than the document, because the
 * renderer may only hold [window]'s slice: scanning the document would look for two glyphs near
 * offset zero and then ask a layout that starts somewhere else entirely for their boxes.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun charWidthPx(layout: TextLayoutResult?): Float? {
    if (layout == null) return null
    val drawn = layout.layoutInput.text.text
    val textLength = drawn.length
    // Two consecutive characters that both draw something. Anything else measures the wrong thing:
    // a newline sits on the next row, and the character after the last one has no box at all.
    var probe = -1
    for (i in 0 until textLength - 1) {
        if (!drawn[i].drawsGlyph()) continue
        if (!drawn[i + 1].drawsGlyph()) continue
        probe = i
        break
    }
    if (probe < 0) return null
    val a = runCatching { layout.getBoundingBox(probe) }.getOrNull() ?: return null
    val b = runCatching { layout.getBoundingBox(probe + 1) }.getOrNull() ?: return null
    val width = b.left - a.left
    return if (width > 0.1f) width else null
}

/**
 * The quick-fix bulb, styled as a small dark rounded square like the reference editor. Sits at the
 * start of the code line under the caret — just left of the first typed character on that line,
 * so it never covers code. Tapping it opens the action list for that line.
 */
/**
 * Wavy underlines beneath every problem token, the way a desktop IDE marks a bad import. Red for
 * errors, amber for warnings, matching the dot and the label so one problem is always the same
 * colour in all three places.
 *
 * Drawn on a canvas rather than as a font decoration so it can follow the token's real bounding box
 * and stay pinned to the text when the editor is zoomed. It has no pointer input, so it cannot
 * steal taps meant for the text field.
 */
@Composable
fun SquiggleLayer(
    problems: List<Diagnostic>,
    layout: androidx.compose.ui.text.TextLayoutResult?,
    window: CodeWindow = CodeWindow.Full,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Spec 6.4: a 4 dp tall wave with a 6 dp period, sitting 16 dp below the top of the line.
    val strokePx = with(density) { 1.2.dp.toPx() }
    val amplitudePx = with(density) { 2.dp.toPx() }
    val periodPx = with(density) { 6.dp.toPx() }
    val belowLineTopPx = with(density) { 16.dp.toPx() }
    val minWidthPx = with(density) { 4.dp.toPx() }

    androidx.compose.foundation.Canvas(modifier) {
        // The caller hands over only the diagnostics inside the window: drawing iterates what
        // is given, so a 200-error file costs the visible handful per frame, not the file.
        for (diagnostic in problems) {
            val tone = if (diagnostic.severity == Severity.WARNING) c.warnText else c.error
            val range = squiggleRange(layout, diagnostic, minWidthPx, window) ?: continue
            val left = range.first
            val right = range.second
            if (right <= left) continue
            val baseY = range.third + belowLineTopPx
            val path = androidx.compose.ui.graphics.Path()
            var x = left
            var up = true
            path.moveTo(x, baseY)
            while (x < right) {
                val end = (x + periodPx / 2f).coerceAtMost(right)
                val peak = if (up) baseY - amplitudePx else baseY + amplitudePx
                path.quadraticBezierTo((x + end) / 2f, peak, end, baseY)
                x = end
                up = !up
            }
            drawPath(
                path = path,
                color = tone,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

/**
 * Left edge, right edge and top of the line for a diagnostic, taken from the text layout so the wave sits
 * under the exact characters the problem points at. Null when the layout is not ready yet.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun squiggleRange(
    layout: androidx.compose.ui.text.TextLayoutResult?,
    diagnostic: Diagnostic,
    minWidthPx: Float,
    window: CodeWindow = CodeWindow.Full,
): Triple<Float, Float, Float>? {
    if (layout == null) return null
    val textLength = layout.layoutInput.text.length
    // The renderer only holds the slice, so a problem outside it has no box to draw under — which
    // is the right answer, because an underline only means anything on a line the user can see.
    if (diagnostic.offset < window.startOffset ||
        diagnostic.offset > window.startOffset + textLength
    ) {
        return null
    }
    val from = mapOffsetToSlice(diagnostic.offset, window.startOffset, textLength)
    val to = mapOffsetToSlice(diagnostic.offset + diagnostic.length, window.startOffset, textLength)
        .coerceAtLeast(from)
    val startBox = runCatching { layout.getBoundingBox(from) }.getOrNull() ?: return null
    val endBox = runCatching { layout.getBoundingBox(to) }.getOrNull() ?: return null
    val left = startBox.left
    val right = maxOf(endBox.right, left + minWidthPx)
    return Triple(left, right, window.topPx + startBox.top)
}

/**
 * The innermost block that contains [offset], or null when the caret sits at the top level.
 *
 * "Innermost" is the largest opening offset among the blocks containing the caret, so nesting
 * resolves to the tightest one and two sibling branches at the same indentation never fight over it.
 * Every pair is walked rather than scanning backwards for a match, because that would make the
 * answer depend on [pairs] arriving sorted — true today, and exactly the kind of invisible coupling
 * that breaks the first time a caller builds the list some other way.
 *
 * The caret sitting on a closing brace counts as inside that block; sitting on the opening brace does
 * not, because a caret immediately after `{` is the one that is genuinely inside it.
 */
internal fun enclosingPair(pairs: List<IntRange>, offset: Int): IntRange? {
    var best: IntRange? = null
    for (pair in pairs) {
        if (pair.first < offset && offset <= pair.last) {
            if (best == null || pair.first > best.first) best = pair
        }
    }
    return best
}

/** The pair [braceOffset] is an end of, or null when it is not a brace of any complete block. */
private fun pairOfBrace(pairs: List<IntRange>, braceOffset: Int): IntRange? =
    pairs.firstOrNull { it.first == braceOffset || it.last == braceOffset }

/** Every bracket character the underline can mark, of any of the three kinds. */
private val BRACKET_CHARS = setOf('{', '}', '(', ')', '[', ']')

/**
 * The two braces to outline for a caret at [offset], as their offsets in the buffer, or null when
 * there is nothing honest to outline.
 *
 * A caret sitting right beside a bracket wins over the block that contains it, because that is what a
 * reader means: they have just typed the `{`, or tapped it, and they are looking at the pair that
 * belongs to it. Any other caret gets the innermost block around it.
 *
 * A bracket with no partner answers null rather than falling through to the enclosing block. The
 * reader is next to a `{` that is not closed; there is no second box to draw, and highlighting some
 * outer block instead would put the emphasis somewhere they are not looking. [unmatchedBracketAt]
 * finds that lone bracket so it can be underlined in red instead.
 */
internal fun activeBraces(pairs: List<IntRange>, text: String, offset: Int): Pair<Int, Int>? {
    if (offset > 0 && offset <= text.length) {
        val before = text[offset - 1]
        if (before in BRACKET_CHARS) {
            return pairOfBrace(pairs, offset - 1)?.let { it.first to it.last }
        }
    }
    if (offset >= 0 && offset < text.length) {
        val after = text[offset]
        if (after in BRACKET_CHARS) {
            return pairOfBrace(pairs, offset)?.let { it.first to it.last }
        }
    }
    return enclosingPair(pairs, offset)?.let { it.first to it.last }
}

/**
 * The offset of the lone bracket the caret sits beside, or null when there is none.
 *
 * "Lone" means a bracket character with no pair in [pairs]: a closer whose opener never came, or an
 * opener nothing has closed yet. The reader is looking at exactly this character — they just typed
 * it or tapped it — so it gets underlined in red rather than leaving them to guess why its partner
 * never lit up. The before-caret position wins, mirroring [activeBraces].
 */
internal fun unmatchedBracketAt(text: String, offset: Int, pairs: List<IntRange>): Int? {
    val before = (offset - 1).takeIf { it >= 0 && it < text.length }?.takeIf { text[it] in BRACKET_CHARS }
    if (before != null && pairs.none { it.first == before || it.last == before }) return before
    val after = offset.takeIf { it >= 0 && it < text.length }?.takeIf { text[it] in BRACKET_CHARS }
    if (after != null && pairs.none { it.first == after || it.last == after }) return after
    return null
}

/**
 * Every line's indent, in character columns.
 *
 * A tab advances to the next tab stop rather than counting as one column, so a file indented with
 * tabs and one indented with the same number of spaces report the same numbers. Without that, a
 * tab-indented file would put every guide further right the deeper the block went, which is the
 * same drifting these rules exist to prevent.
 *
 * Indent is only counted before a line's first real character: whitespace after code is not indent,
 * and a line of nothing but spaces is a blank row, not a deeply indented one. A blank line between
 * two blocks therefore reads as column 0 and no guide is drawn from it.
 *
 * The list always has one entry per line, including a final line that has no newline of its own.
 */
internal fun indentColumnsOf(source: String, tabSize: Int): IntArray {
    val step = tabSize.coerceAtLeast(1)
    val indents = ArrayList<Int>()
    var column = 0
    var hasContent = false
    for (ch in source) {
        when (ch) {
            ' ' -> if (!hasContent) column++
            '\t' -> if (!hasContent) column += step - (column % step)
            '\n' -> {
                // A line with nothing on it reports no indent however much whitespace it carries.
                indents.add(if (hasContent) column else 0)
                column = 0
                hasContent = false
            }
            else -> hasContent = true
        }
    }
    indents.add(if (hasContent) column else 0)
    return indents.toIntArray()
}

/**
 * The column a block's guide is drawn at: one tab past the indent of the line that opened it.
 *
 * This is the indent of the block's own contents, which is what makes the rule read as structure
 * rather than as a stray vertical bar. The old anchor was the `{` glyph's own bounding box, and on
 * a line like `setContent {` that sits at the far right end of the line — nowhere near the code
 * underneath it, so the rule appeared to hang through the middle of everything nested inside.
 *
 * Multiplying by the measured character width is what turns this column back into a position: the
 * column is computed here as arithmetic and the width is read off the rendered text, so exactly one
 * number in the whole calculation comes from the renderer and a guide cannot drift away from the
 * code when the font, the zoom level or the row height changes.
 */
internal fun guideColumn(openIndent: Int, tabSize: Int): Int =
    openIndent.coerceAtLeast(0) + tabSize.coerceAtLeast(1)

/**
 * The lines a guide runs between: the line *after* the opening brace down to and including the
 * closing brace's own line, or null when there is nothing to draw.
 *
 * Starting on the following line is what keeps the rule off the opener's text — drawn from the
 * opener's row it would run across the `{` and everything after it on that line. A pair whose ends
 * are on the same row, or a brace left open at the end of the file, has no span and draws nothing.
 */
internal fun guideSpan(openLine: Int, closeLine: Int): IntRange? =
    if (closeLine <= openLine) null else (openLine + 1)..closeLine

/**
 * One vertical rule per `{ … }` that spans more than a single line, drawn down the column its own
 * contents are indented to.
 *
 * Every rule uses the same colour. A brighter rule on the caret's block would be a second cursor, and
 * the cursor is already drawn; the brace underline below is what says where the reader is.
 *
 * The two ends come from the renderer's line metrics, so a pair whose lines fall outside the
 * rendered slice answers null from [lineTopPx] and is not drawn at all — which is the whole clipping
 * rule for free, and the reason a guide never appears at a position clamped to the edge of the
 * window it is not in.
 */
@Composable
fun BracketGuideLayer(
    pairs: List<IntRange>,
    source: String,
    indents: IntArray,
    tabSize: Int,
    charWidthPx: Float,
    layout: TextLayoutResult?,
    lineHeightPx: Float,
    window: CodeWindow = CodeWindow.Full,
    modifier: Modifier = Modifier,
    // The buffer's index when the caller has one (the editor remembers it per text): every pair
    // then costs two binary searches instead of two full walks, which is what makes guides free
    // while scrolling. Null falls back to the walks, for callers without an index.
    lineIndex: LineIndex? = null,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Half a stroke, so the rule is *centred* on the column boundary rather than hanging off its
    // left edge — the same sub-pixel placement an indent guide gets in a desktop editor.
    val strokePx = with(density) { 1.dp.toPx() }
    val halfStroke = strokePx / 2f

    androidx.compose.foundation.Canvas(modifier) {
        // Without a measured character there is no honest way to turn a column into a position, and
        // a rule at the wrong x is worse than no rule at all.
        if (charWidthPx <= 0f) return@Canvas
        for (pair in pairs) {
            val openLine = lineIndex?.lineOf(pair.first) ?: lineOfOffset(source, pair.first)
            val closeLine = lineIndex?.lineOf(pair.last) ?: lineOfOffset(source, pair.last)
            val span = guideSpan(openLine, closeLine) ?: continue
            val openIndent = indents.getOrElse(openLine - 1) { 0 }
            val x = guideColumn(openIndent, tabSize) * charWidthPx + halfStroke
            val top = lineTopPx(layout, source, span.first, window) ?: continue
            val bottom = lineBottomPx(layout, source, span.last, lineHeightPx, window) - lineHeightPx
            drawLine(
                color = c.indentGuide,
                start = androidx.compose.ui.geometry.Offset(x, top),
                end = androidx.compose.ui.geometry.Offset(x, bottom + lineHeightPx),
                strokeWidth = strokePx,
            )
        }
    }
}
/**
 * The block the caret is in, marked by a short line under each of its two braces.
 *
 * A box around each brace said "these two are related" by enclosing them, which put a frame over
 * two characters of the reader's code and competed with the squiggle underneath an error on the same
 * row. A line under the brace says the same thing without touching the glyph: the brace's own bottom
 * is where the mark belongs, so it sits on the row below the code rather than over it.
 *
 * Both braces are marked, not just the one the caret is beside, because the pair is the fact worth
 * showing — the two ends of the block, on screen at once when both are visible.
 */
@Composable
internal fun MatchingBraceLayer(
    active: Pair<Int, Int>?,
    layout: TextLayoutResult?,
    window: CodeWindow = CodeWindow.Full,
    modifier: Modifier = Modifier,
    // A lone bracket with no partner, underlined in red. Null means every bracket the caret sits
    // beside has its match, so there is nothing to warn about.
    unmatched: Int? = null,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val strokePx = with(density) { BRACE_UNDERLINE_STROKE.toPx() }
    val gapPx = with(density) { BRACE_UNDERLINE_GAP.toPx() }
    val growPx = with(density) { BRACE_UNDERLINE_GROW.toPx() }

    androidx.compose.foundation.Canvas(modifier) {
        val textLength = layout?.layoutInput?.text?.length ?: return@Canvas
        val brackets = active
        if (brackets != null) {
            for (offset in intArrayOf(brackets.first, brackets.second)) {
                val box = braceBox(layout, offset, textLength, window) ?: continue
                val y = box.bottom + gapPx
                drawLine(
                    color = c.accent,
                    start = androidx.compose.ui.geometry.Offset(box.left - growPx, y),
                    end = androidx.compose.ui.geometry.Offset(box.right + growPx, y),
                    strokeWidth = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        if (unmatched != null) {
            val box = braceBox(layout, unmatched, textLength, window)
            if (box != null) {
                val y = box.bottom + gapPx
                drawLine(
                    color = c.error,
                    start = androidx.compose.ui.geometry.Offset(box.left - growPx, y),
                    end = androidx.compose.ui.geometry.Offset(box.right + growPx, y),
                    strokeWidth = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * The mark under a matching brace: stroke, gap and overhang.
 *
 * A 2 dp rule is the thickness of the tab's own underline and the scrollbar's own thumb, so the three
 * marks along the edge of the editor read as one family. The 2 dp gap is the distance from the glyph's
 * own bottom, which keeps the rule off the character itself at any font size.
 */
val BRACE_UNDERLINE_STROKE = 2.dp
val BRACE_UNDERLINE_GAP = 2.dp
val BRACE_UNDERLINE_GROW = 2.dp

/**
 * A brace's own glyph box in the layer's coordinates, or null when it is not in the rendered slice.
 */
private fun braceBox(
    layout: TextLayoutResult?,
    offset: Int,
    textLength: Int,
    window: CodeWindow,
): androidx.compose.ui.geometry.Rect? {
    if (layout == null) return null
    if (offset < window.startOffset || offset > window.startOffset + textLength) return null
    val sliceIndex = mapOffsetToSlice(offset, window.startOffset, textLength)
    val box = runCatching { layout.getBoundingBox(sliceIndex) }.getOrNull() ?: return null
    return box.translate(0f, window.topPx)
}


/**
 * How much of the code column has to stay clear on either side of the caret before the view moves
 * sideways to bring it back.
 *
 * Wide enough that typing in the middle of a line that is already on screen never nudges the view,
 * narrow enough that the caret cannot walk off the edge while the user is still typing there.
 */
val CaretHScrollMargin = 64.dp

/**
 * Where the code column should be scrolled sideways so the caret sits inside its margin.
 *
 * [caretXPx] is the caret's position in the scrollable content, [currentPx] is what the column is
 * scrolled to right now, and [viewportWidthPx] is how much of it is on screen. Inside the band the
 * answer is "stay where you are", so a caret moving within the visible middle of a line costs
 * nothing; only a caret that has walked into one of the two margins moves the view, and it moves
 * just far enough to put the margin back between the caret and the edge.
 *
 * The low end clamps to zero because there is nothing left of the first character. The high end is
 * deliberately left unclamped: the scroller owns the content's real width and coerces to its own
 * maximum, and guessing that width here would mean handing this function a number only the
 * renderer knows.
 */
fun targetHScroll(
    caretXPx: Float,
    currentPx: Float,
    viewportWidthPx: Float,
    marginPx: Float,
): Float {
    val left = currentPx + marginPx
    val right = currentPx + viewportWidthPx - marginPx
    return when {
        caretXPx < left -> (caretXPx - marginPx).coerceAtLeast(0f)
        caretXPx > right -> caretXPx - viewportWidthPx + marginPx
        else -> currentPx
    }
}

/**
 * Horizontal position of [caret] in the rendered text, or null while there is no layout yet.
 *
 * Read from the same [TextLayoutResult] the glyphs come from, so the bulb lands on the character
 * the caret is actually sitting in rather than an estimate from a monospace advance.
 */
fun caretX(
    layout: TextLayoutResult?,
    caret: Int,
    window: CodeWindow = CodeWindow.Full,
): Float? {
    if (layout == null) return null
    val offset = mapOffsetToSlice(caret, window.startOffset, layout.layoutInput.text.length)
    return runCatching { layout.getHorizontalPosition(offset, true) }.getOrNull()
}

/**
 * Spec 6.7 autocomplete popup placement, in viewport pixels.
 *
 * The popup never overlaps the line being typed. It sits on the row below the caret line, which
 * leaves the caret line free to read while typing; when the keyboard leaves no room underneath, it
 * flips to sit entirely above the caret line instead. Either way the popup's own band and the caret
 * line's band never intersect.
 *
 * All inputs are already converted out of the scrolled content's coordinates by the caller.
 */
fun popupTopPx(
    caretTopPx: Float,
    lineHeightPx: Float,
    gapPx: Float,
    popupHeightPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
): Float {
    val below = caretTopPx + lineHeightPx + gapPx
    val above = caretTopPx - gapPx - popupHeightPx
    val fitsBelow = below + popupHeightPx <= viewportHeightPx - edgePx
    return when {
        fitsBelow -> below
        above >= 0f -> above
        // Neither side fits: keep the popup on screen and let the caller scroll the caret into view.
        else -> below.coerceIn(0f, (viewportHeightPx - popupHeightPx).coerceAtLeast(0f))
    }
}

/**
 * Spec 6.7: where the suggestion popup goes and how much of it the space allows, as
 * `top to rows`.
 *
 * [popupTopPx] answers this for a panel of a *fixed* height and, when neither side has room, keeps
 * it on screen by clamping — which lands it on top of the line being typed. The suggestion popup
 * is not fixed: it is a list of rows, so instead of moving it onto the caret line it gives up
 * rows. The caret line is never covered, whatever the viewport, because the row count is chosen to
 * clear it rather than the other way round.
 *
 * The side with the room wins: as many rows as fit under the caret line if the whole popup fits
 * there — which is the case the design describes as sitting "on the row below" — otherwise whichever
 * side can show more, so a caret near the bottom flips above with its full list rather than sitting
 * on one row below it. Nothing fits either side only when the viewport is shorter than a single
 * row, and then the honest answer is no popup at all: [Pair]'s null.
 *
 * [rowHeightPx] is one row, [borderPx] the frame drawn around them, [edgePx] the air the popup
 * keeps from the viewport's own edge and [maxRows] the most it would ever show.
 */
fun popupPlacement(
    caretTopPx: Float,
    lineHeightPx: Float,
    gapPx: Float,
    rowHeightPx: Float,
    borderPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
    maxRows: Int,
): Pair<Float, Int>? {
    if (maxRows < 1 || rowHeightPx <= 0f || viewportHeightPx <= 0f) return null

    // Whole rows that fit in [room] without the frame poking out of it.
    fun fit(room: Float): Int =
        (((room - borderPx) / rowHeightPx).toInt()).coerceIn(0, maxRows)

    val belowTop = caretTopPx + lineHeightPx + gapPx
    val aboveBottom = caretTopPx - gapPx
    val below = fit(viewportHeightPx - edgePx - belowTop)
    val above = fit(aboveBottom - edgePx)
    fun aboveTop(rows: Int) = aboveBottom - (rows * rowHeightPx + borderPx)

    return when {
        // The usual case: the whole list fits underneath and the caret line stays readable.
        below >= maxRows -> belowTop to below
        // Underneath cannot take the whole list, so whichever side can show more wins.
        above > below && above > 0 -> aboveTop(above) to above
        below > 0 -> belowTop to below
        above > 0 -> aboveTop(above) to above
        else -> null
    }
}

/**
 * Spec 6.7: the popup's width. It lives in the code column, so the gutter is subtracted before the
 * screen margin: a popup wider than the code area would run under the line numbers.
 */
fun popupWidthPx(maxWidthPx: Float, viewportWidthPx: Float, gutterWidthPx: Float, insetPx: Float): Float =
    minOf(maxWidthPx, viewportWidthPx - gutterWidthPx - insetPx * 2f).coerceAtLeast(0f)

/**
 * Spec 6.7: the popup's left edge is the start of the word being typed, clamped so at least
 * [insetPx] of code column is left on both sides, so it can never run off the right edge.
 */
fun popupLeftPx(
    wordX: Float,
    columnStartPx: Float,
    hScrollPx: Int,
    popupWidthPx: Float,
    codeWidthPx: Float,
    insetPx: Float,
): Float {
    val max = (codeWidthPx - popupWidthPx - insetPx).coerceAtLeast(insetPx)
    return (columnStartPx + wordX - hScrollPx).coerceIn(insetPx, max)
}

/**
 * Height of one rendered code row, measured from the text field rather than converted by hand.
 *
 * This is what keeps the line numbers and the error labels on the same row as the code. A hand
 * conversion is wrong whenever the device scales fonts, because `sp` is multiplied by the user's
 * font-size setting and `dp` is not.
 *
 * The measurement is taken from the renderer's own line tops and averaged over every row, which is
 * immune to the two things that used to break it: a blank row near the top, whose only character is
 * its newline, and any single row that measures differently because of the glyph it happens to
 * start with.
 *
 * Null when there is no layout or no second line to compare against.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun measuredLineHeightPx(
    layout: TextLayoutResult?,
    source: String,
): Float? {
    if (layout == null) return null
    if (!source.contains('\n')) return null
    val rows = layout.lineCount - 1
    if (rows < 1) return null
    val first = runCatching { layout.getLineTop(0) }.getOrNull() ?: return null
    val last = runCatching { layout.getLineTop(rows) }.getOrNull() ?: return null
    return rowHeightFromTops(first, last, rows)
}

/**
 * Average height of one rendered row, from the renderer's own top edges. Pure so the averaging can
 * be tested without a layout: [rows] rows span [lastTopPx] - [firstTopPx].
 *
 * Null when the rows collapse, which only happens for a layout that has not been measured yet.
 */
fun rowHeightFromTops(firstTopPx: Float, lastTopPx: Float, rows: Int): Float? {
    if (rows < 1) return null
    val height = (lastTopPx - firstTopPx) / rows
    return if (height > 0.5f) height else null
}

enum class QuickFixKind {
    ADD_IMPORT,
    REMOVE_IMPORT,
    REMOVE_TOKEN,
    ADD_SEMICOLON,
    TOGGLE_COMMENT,
    MOVE_LINE_UP,
    MOVE_LINE_DOWN,
    NO_FIX,
}

/** An action offered by the quick-fix list. */
@Immutable
data class QuickFixAction(
    val kind: QuickFixKind,
    val target: String,
    val label: String,
)

/**
 * Every action available on a line: fixes for the problems there first, then the plain code
 * actions that always apply.
 *
 * Spec 6.6 pins the order, because tapping the bulb applies the first entry: E0101 removes the
 * unknown token, E0102 appends ";", W0201 deletes the import line. A code with no fix still gets a
 * visible "No quick fix for CODE" row rather than an empty menu.
 */
fun buildQuickFixActions(
    line: Int?,
    problems: ProblemsByLine,
    analysis: com.closecode.lang.KotlinFileAnalysis?,
    source: String,
): List<QuickFixAction> {
    if (line == null || line < 1 || analysis == null) return emptyList()
    val out = ArrayList<QuickFixAction>()
    for (diagnostic in problems.on(line)) {
        when (diagnostic.code) {
            DiagnosticCodes.E0101_UNRESOLVED -> {
                // Spec 6.6 wants the token deleted on tap, so this is offered before the import.
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.REMOVE_TOKEN,
                        target = diagnostic.data ?: "",
                        label = "Remove '${diagnostic.data.orEmpty()}'",
                    ),
                )
                val name = diagnostic.data
                if (name != null && isCapitalised(name)) {
                    val candidates = analysis.api.candidateFqns(name)
                    if (candidates.isNotEmpty()) {
                        val fqn = candidates.first()
                        out.add(
                            QuickFixAction(
                                kind = QuickFixKind.ADD_IMPORT,
                                target = fqn,
                                label = "Import ${com.closecode.lang.SourceEdits.packageOf(fqn)}",
                            ),
                        )
                    }
                }
            }

            DiagnosticCodes.E0102_MISSING_SEMICOLON -> {
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.ADD_SEMICOLON,
                        target = "$line",
                        label = "Add ; at end of line",
                    ),
                )
            }

            DiagnosticCodes.W0201_UNUSED_IMPORT -> {
                val fqn = diagnostic.data ?: continue
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.REMOVE_IMPORT,
                        target = fqn,
                        label = "Remove import $fqn",
                    ),
                )
            }
        }
    }
    val unfixable = problems.on(line).filter { d ->
        when (d.code) {
            DiagnosticCodes.E0101_UNRESOLVED,
            DiagnosticCodes.E0102_MISSING_SEMICOLON,
            DiagnosticCodes.W0201_UNUSED_IMPORT,
            -> false

            else -> true
        }
    }
    for (d in unfixable) {
        out.add(QuickFixAction(QuickFixKind.NO_FIX, d.code, "No quick fix for ${d.code}"))
    }
    if (problems.on(line).isEmpty()) return out
    val commented = source.split('\n').getOrNull(line - 1)?.trimStart()?.startsWith("//") == true
    out.add(
        QuickFixAction(
            kind = QuickFixKind.TOGGLE_COMMENT,
            target = "$line",
            label = if (commented) "Uncomment Line" else "Comment Line",
        ),
    )
    out.add(
        QuickFixAction(QuickFixKind.MOVE_LINE_UP, "$line", "Move Line Up"),
    )
    out.add(
        QuickFixAction(QuickFixKind.MOVE_LINE_DOWN, "$line", "Move Line Down"),
    )
    return out.distinctBy { it.kind to it.target }
}

/**
 * Spec 7 problem cards, shown in the bottom sheet now that the Problems tab is gone.
 *
 * Each card is radius 10 with a 1 dp border, a 10 dp severity dot on the left, the message in
 * bold, the code as a chip and `file:line` underneath. Errors sort above warnings, and tapping a
 * card puts the caret on that problem.
 */
@Composable
/**
 * The problems sheet.
 *
 * It can show one file's problems or the whole project's, because the Run and Build blockers look at
 * the whole project and the sheet is where they send the reader. Showing only the open file there is
 * how "Fix 1 error" and "No problems. Code is clean." came to be true at the same moment.
 *
 * In project scope each row carries the file it came from, because a bare line number in a list of
 * files that are not identified is not something a reader can act on.
 */
internal fun ProblemSheet(
    problems: List<ProjectDiagnostic>,
    scope: ProblemScope = ProblemScope.FILE,
    onSelect: (ProjectDiagnostic) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fileLabel: String? = null,
) {
    val c = LocalPageColors.current

    // Spec 7: errors sort above warnings; within a severity keep source order.
    val ordered = remember(problems) {
        problems.sortedBy { if (it.diagnostic.severity == Severity.ERROR) 0 else 1 }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = scope.heading(),
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "${ordered.size}",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                text = "✕",
                color = c.mu,
                fontSize = 16.sp,
                fontFamily = Sans,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }

        if (ordered.isEmpty()) {
            // Spec 7 empty state. In project scope this means the project is clean, which is a
            // different and much better fact than the open file being clean.
            Text(
                text = if (scope == ProblemScope.PROJECT) {
                    "No errors anywhere in the project."
                } else {
                    "No problems. Code is clean."
                },
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp, horizontal = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            return@Column
        }

        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        ) {
            for (row in ordered) {
                val diagnostic = row.diagnostic
                val isWarning = diagnostic.severity == Severity.WARNING
                val tone = if (isWarning) c.warnPillTx else c.errorPillTx
                val fill = if (isWarning) c.warnPillBg else c.errorPillBg
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.panel2)
                        .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                        .clickable { onSelect(row) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Severity dot, 10 dp, offset 5 dp from the top.
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tone),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            text = diagnostic.message,
                            color = c.tx,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(fill)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = diagnostic.code,
                                    color = tone,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            // In project scope the row's own file is the one worth naming; in file
                            // scope there is only the one the sheet was opened on.
                            val rowFile = if (scope == ProblemScope.PROJECT) row.fileName else fileLabel
                            if (rowFile != null) {
                                Text(
                                    text = "$rowFile:${diagnostic.line}",
                                    color = c.mu,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}private fun isCapitalised(name: String): Boolean =
    name.firstOrNull()?.isUpperCase() == true

/**
 * Shared card used for both the quick-fix list and the completion popup, so the two always look
 * identical and only one is ever visible at a time.
 */
@Composable
fun IdeActionCard(
    rows: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(10.dp)),
    ) {
        rows.forEachIndexed { index, (title, subtitle) ->
            val isSel = index == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) c.ac.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = title,
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = c.mu,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln.copy(alpha = 0.5f)))
            }
        }
    }
}
/**
 * Where the signature-help box goes, or null when there is nowhere to put it.
 *
 * Always above the caret line, never below, and never over it. Below is where the suggestion list
 * goes — both follow the caret, so one above and one below is the only arrangement where a reader
 * typing an argument can see the signature and the names that match it at the same time.
 *
 * When there is no room above, the honest answer is no box: putting it below would cover either the
 * code being typed or the list being chosen from, and a tooltip that hides the argument is worse than
 * no tooltip.
 */
fun signatureTopPx(
    caretTopPx: Float,
    tooltipHeightPx: Float,
    gapPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
): Float? {
    if (tooltipHeightPx <= 0f || viewportHeightPx <= 0f) return null
    val top = caretTopPx - gapPx - tooltipHeightPx
    return if (top >= edgePx && top + tooltipHeightPx <= viewportHeightPx) top else null
}

/**
 * Where the signature tooltip goes when there is no room above the caret line: below it, if the
 * tooltip fits there inside the viewport margins. Null when it fits nowhere, and the tooltip stays
 * hidden rather than covering the line being typed.
 */
fun tooltipBelowTopPx(
    caretTopPx: Float,
    lineHeightPx: Float,
    gapPx: Float,
    tooltipHeightPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
): Float? {
    if (tooltipHeightPx <= 0f || viewportHeightPx <= 0f) return null
    val top = caretTopPx + lineHeightPx + gapPx
    return if (top >= edgePx && top + tooltipHeightPx <= viewportHeightPx - edgePx) {
        top
    } else {
        null
    }
}
/** The box's own height: one line of signature, then one of description, plus its padding. */
val SignatureRowHeight = 20.dp
val SignatureDescriptionHeight = 18.dp
val SignatureBoxVerticalPadding = 8.dp

/**
 * The floating parameter tooltip: the signature, the argument the caret is in picked out, and one
 * line saying what that argument is for.
 *
 * The description line is always drawn, even when nothing is known about the parameter, because the
 * box changing height as the caret crosses a comma would move the very line the reader is reading.
 * Each parameter is its own composable rather than one annotated string because the active one
 * carries a rounded background with padding, which an inline span cannot draw.
 */
@Composable
internal fun SignatureHelpTooltip(
    info: SignatureInfo,
    overloadLabel: String? = null,
    onOverloadTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.popupBg)
            .border(1.dp, c.popupBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
        ) {
            Text(
                text = info.name + "(",
                color = c.iconTint,
                fontSize = 11.sp,
                fontFamily = Mono,
                lineHeight = 11.sp * 1.5f,
            )
            info.params.forEachIndexed { index, param ->
                if (index > 0) {
                    Text(
                        text = ", ",
                        color = c.iconTint,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 11.sp * 1.5f,
                    )
                }
                val label = "${param.name}: ${param.type}"
                if (index == info.activeIndex) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.popupSelected)
                            .padding(horizontal = 3.dp),
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 11.sp * 1.5f,
                        )
                    }
                } else {
                    Text(
                        text = label,
                        color = c.iconTint,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 11.sp * 1.5f,
                    )
                }
            }
            Text(
                text = ")" + (info.returnType?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                color = c.iconTint,
                fontSize = 11.sp,
                fontFamily = Mono,
                lineHeight = 11.sp * 1.5f,
            )
            if (overloadLabel != null) {
                // The overload counter rides at the end of the signature: tapping it steps through
                // the versions. It is text, not a button chrome, because it must sit inline where
                // the signature ends rather than in a row of its own.
                Text(
                    text = overloadLabel,
                    color = c.popupMuted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = onOverloadTap),
                )
            }
        }
        Text(
            text = info.params.getOrNull(info.activeIndex)?.let { describeParam(it.name) } ?: "",
            color = c.hintText,
            fontSize = 11.sp,
            fontFamily = Sans,
            modifier = Modifier
                .padding(top = 2.dp)
                .height(SignatureDescriptionHeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Which problems the sheet is showing. */
internal enum class ProblemScope {
    /** Only the file that is open. */
    FILE,

    /** Every Kotlin file in the project, which is what the Run and Build blockers look at. */
    PROJECT,
}

/** One project-wide problem, carrying the file it came from. */
internal data class ProjectDiagnostic(val fileName: String, val diagnostic: Diagnostic)

/**
 * What to say when the Run button will not let the reader through.
 *
 * The old message was a bare count — "Fix 1 errors", plural included — and the sheet it opened showed
 * only the file that was open. So an error in another file blocked Run while the sheet it opened said
 * the code was clean, and the reader had two true sentences that did not agree about the same project.
 *
 * Naming the file is what closes that gap: the message points at the file the sheet will now show.
 * One file is named; several are counted, because listing every path in a toast would be unreadable
 * and the sheet is one tap away.
 */
internal fun blockedByErrors(errors: List<ProjectDiagnostic>, doing: String): String {
    val count = errors.size
    val files = errors.map { it.fileName }.distinct()
    val where = when (files.size) {
        0 -> ""
        1 -> " in ${files.first()}"
        else -> " across ${files.size} files"
    }
    return "Fix ${pluralProblems(count, "error")}$where before $doing"
}

/** The sheet's copy when it is showing the whole project rather than one file. */
internal fun ProblemScope.heading(): String = when (this) {
    ProblemScope.FILE -> "Problems"
    ProblemScope.PROJECT -> "Problems in project"
}

/**
 * Where an error line sits down the whole file, as a fraction, for the scrollbar's ticks.
 *
 * A tick is the only way to answer "is the thing I am looking for above me or below me" on a screen
 * that shows twenty lines of a two-thousand-line file. A minimap would answer it better and cost more
 * than the app can afford: it has to draw every line of code, tiny, on every keystroke.
 *
 * Lines past the visible window are the ones worth marking — an error on a row already on screen has
 * a squiggle under it.
 */
internal fun errorTickFractions(
    errorLines: Set<Int>,
    lineCount: Int,
    visibleTop: Int,
    visibleBottom: Int,
    limit: Int = 40,
): List<Float> {
    if (lineCount <= 0 || visibleBottom <= visibleTop) return emptyList()
    val height = (visibleBottom - visibleTop).coerceAtLeast(1)
    return errorLines
        .filter { it <= visibleTop || it > visibleBottom }
        .sorted()
        .let { if (it.size > limit) it.take(limit) else it }
        .map { line ->
            // Position by the line's own row within the file, then scaled into the thumb's travel, so
            // a tick sits where that line actually is rather than evenly spaced down the bar.
            val rowFraction = ((line - 1).toFloat() / lineCount).coerceIn(0f, 1f)
            val viewportFraction = ((visibleTop - 1).toFloat() / lineCount).coerceIn(0f, 1f)
            (rowFraction - viewportFraction).coerceIn(0f, 1f) * height
        }
}

/**
 * The declaration whose body [line] sits in, or null when it sits in none.
 *
 * The innermost one, not the outermost: a reader three blocks deep wants the function they are in,
 * not the class they are inside. The stack of open braces is walked from the top and the first entry
 * that names a declaration wins, which also skips a bare block — an `if` body has braces and names
 * nothing.
 *
 * Brace depth rather than a parser, because this has to stay right on a file that does not compile:
 * the editor is most useful when it is wrong about something, and the header must not be the part
 * that goes blank.
 */
internal fun enclosingDeclarationAt(code: String, line: Int): Pair<Int, String>? {
    if (line < 1) return null
    val lines = code.lines()
    val at = line.coerceAtMost(lines.size) - 1
    val openers = ArrayList<Int>()
    for (i in 0 until at) {
        val text = lines[i]
        var inString = false
        var quote = ' '
        var j = 0
        while (j < text.length) {
            val ch = text[j]
            when {
                inString -> {
                    if (ch == '\\') j++
                    else if (ch == quote) inString = false
                }
                ch == '"' || ch == '\'' -> {
                    inString = true
                    quote = ch
                }
                // A line whose brace belongs to a control structure opens a block, not a
                // declaration: `if (b) {` has braces and names nothing, and pinning "if (b)" as the
                // header would be both useless and the first thing a reader saw.
                ch == '{' -> openers.add(if (namesADeclaration(text.substring(0, j))) i else -1)
                ch == '}' -> if (openers.isNotEmpty()) openers.removeAt(openers.lastIndex)
            }
            j++
        }
    }
    for (i in openers.indices.reversed()) {
        val opener = openers[i]
        if (opener < 0) continue
        return (opener + 1) to declarationOf(lines[opener])
    }
    return null
}

/** Whether the text before a brace names something, rather than introducing a block. */
internal fun namesADeclaration(head: String): Boolean {
    val text = head.trim()
    if (text.isEmpty()) return false
    val word = text.substringBefore('(').trim().substringBefore(' ')
    return word.lowercase() !in BlockIntroducers
}

/**
 * The sticky header's answer for every line, computed in one pass per text change instead of
 * re-walking the file on every row the window crosses.
 *
 * `enclosing[line0]` is the (1-based line, name) [enclosingDeclarationAt] would return for the
 * line after it — same per-line string-skipping scan, same control-structure rule — so a scroll
 * frame reads one array slot instead of scanning up to the whole file.
 */
internal class DeclarationIndex(code: String) {
    private val enclosing: Array<Pair<Int, String>?>

    init {
        val lines = code.lines()
        enclosing = arrayOfNulls(lines.size)
        // Both stacks move together: openers holds every unclosed `{` (or -1 for a control
        // block) and topNamed mirrors it with the nearest named line at or below each slot,
        // so reading the header is one slot lookup and building stays one linear pass.
        val openers = ArrayList<Int>()
        val topNamed = ArrayList<Int>()
        for (i in lines.indices) {
            val top = topNamed.lastOrNull()?.takeIf { it >= 0 }
            enclosing[i] = if (top == null) null else (top + 1) to declarationOf(lines[top])
            val text = lines[i]
            var inString = false
            var quote = ' '
            var j = 0
            while (j < text.length) {
                val ch = text[j]
                when {
                    inString -> {
                        if (ch == '\\') j++
                        else if (ch == quote) inString = false
                    }
                    ch == '"' || ch == '\'' -> {
                        inString = true
                        quote = ch
                    }
                    ch == '{' -> {
                        val named = if (namesADeclaration(text.substring(0, j))) i else -1
                        openers.add(named)
                        topNamed.add(if (named >= 0) named else topNamed.lastOrNull() ?: -1)
                    }
                    ch == '}' -> if (openers.isNotEmpty()) {
                        openers.removeAt(openers.lastIndex)
                        topNamed.removeAt(topNamed.lastIndex)
                    }
                }
                j++
            }
        }
    }

    /** The declaration enclosing 0-based [line0], or null when it names none. */
    operator fun get(line0: Int): Pair<Int, String>? =
        enclosing.getOrNull(line0.coerceAtLeast(0))
}

/** The words that open a block rather than declare something. */
private val BlockIntroducers = setOf(
    "if", "else", "when", "while", "for", "do", "try", "catch", "finally", "init",
)

/**
 * Whether [line] declares [symbol] — a val, var, function, class, object, interface or
 * typealias carrying its name. Usages do not count: selecting `foo` in `println(foo)` is a
 * selection, not a name with a declaration, and earns no details strip.
 */
internal fun declaresSymbol(line: String, symbol: String): Boolean {
    if (symbol.isEmpty() || !symbol.all(::isWordChar)) return false
    return Regex(
        """\b(val|var|fun|class|object|interface|typealias)\s+""" + Regex.escape(symbol) + """\b""",
    ).containsMatchIn(line)
}

internal fun declarationOf(line: String): String {
    val brace = line.indexOf('{')
    val head = if (brace >= 0) line.substring(0, brace).trim() else line.trim()
    return head.ifEmpty { line.trim() }
}

/**
 * A code word's characters: letters, digits and the underscore. `$` is not one — it marks
 * string templates and generated names, and treating it as a word character would glue a
 * template like `"${name}x"` into one unselectable lump.
 */
internal fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

/**
 * The word a collapsed caret sits in, or null when the caret is between words. A caret parked
 * right after a word's last letter still belongs to that word — that is where a tap lands when
 * a finger aims at the word's end — but a caret between a word and a space belongs to neither.
 */
internal fun symbolAt(text: String, offset: Int): String? {
    if (text.isEmpty()) return null
    val at = offset.coerceIn(0, text.length)
    val anchor = when {
        at < text.length && isWordChar(text[at]) -> at
        at > 0 && isWordChar(text[at - 1]) -> at - 1
        else -> return null
    }
    var start = anchor
    while (start > 0 && isWordChar(text[start - 1])) start--
    var end = anchor
    while (end < text.length && isWordChar(text[end])) end++
    return text.substring(start, end).takeIf { it.isNotEmpty() }
}

/**
 * Every whole-word, case-sensitive occurrence of [word], as offset ranges. `foo` matches in
 * `foo(bar)` but not in `foobar` or `my_foo`: symbols are case-sensitive, and a prefix is a
 * different symbol. An empty or non-word query matches nothing rather than everything.
 */
internal fun symbolOccurrences(text: String, word: String): List<IntRange> {
    if (word.isEmpty() || word.any { !isWordChar(it) }) return emptyList()
    val out = ArrayList<IntRange>()
    var from = 0
    while (from <= text.length - word.length) {
        val found = text.indexOf(word, from)
        if (found < 0) return out
        val beforeOk = found == 0 || !isWordChar(text[found - 1])
        val after = found + word.length
        val afterOk = after == text.length || !isWordChar(text[after])
        if (beforeOk && afterOk) out.add(found until after)
        from = found + 1
    }
    return out
}

/** One folded block's chip: the display row it sits on and where its source block starts. */
internal data class FoldChip(val displayLine: Int, val sourceLine: Int)

/**
 * The ellipsis chips after folded blocks' first lines. Tapping one unfolds, the same as tapping
 * the chevron — two doors to the same room, because the chevron sits in the gutter and the chip
 * sits where the hidden code used to continue.
 *
 * Positioned like pills: past the end of the row's text in content coordinates, so chips travel
 * sideways with their lines.
 */
@Composable
internal fun FoldChipLayer(
    chips: List<FoldChip>,
    lineEndX: (Int) -> Float?,
    lineTop: (Int) -> Float?,
    gapPx: Float,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Box(modifier) {
        for (chip in chips) {
            val end = lineEndX(chip.displayLine) ?: continue
            val top = lineTop(chip.displayLine) ?: continue
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (end + gapPx).roundToInt(),
                            top.roundToInt(),
                        )
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.foldChipBg)
                    .clickable { onTap(chip.sourceLine) }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "…",
                    color = c.popupMuted,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
            }
        }
    }
}
