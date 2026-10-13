package com.closecode.ui.ide

/**
 * Code folding: which blocks can collapse, and the map between what the reader sees and what the
 * file holds.
 *
 * The editor's text field shows display text with folded bodies removed, while everything on disk,
 * in buffers and in analysis is source text. These two are different strings with different line
 * numbers and different offsets, and every crossing between them goes through [FoldMap] — there is
 * exactly one place where display coordinates become source coordinates and back, so a caret, a
 * diagnostic or a brace can never silently mean two things at once.
 */
internal data class FoldMap(
    /** The source text this map was built from. */
    val source: String,
    /** The visible text: source with folded interiors removed. Never edited in place. */
    val display: String,
    /** Display row index (0-based) to source 1-based line. */
    val displayLines: List<Int>,
    /** Source 1-based line to display row index, or -1 when hidden. Size sourceLineCount + 1. */
    val sourceToDisplayLine: IntArray,
    /** Display offset to source offset. Size display.length + 1. */
    val toSource: IntArray,
    /** Source offset to display offset. Size source.length + 1. */
    val toDisplay: IntArray,
    /** The folds actually applied, as source line ranges. */
    val foldedRanges: List<IntRange>,
    /** Every foldable source start line, for dropping stale fold state. */
    val foldableStarts: Set<Int>,
) {
    companion object {
        /**
         * Builds the map for [source] with [foldedStarts] collapsed.
         *
         * A folded start that is not foldable anymore — the code changed under it — is ignored
         * rather than honored: collapsing the wrong lines would hide code the reader did not ask
         * to hide, while quietly unfolding is merely noticeable.
         */
        fun build(source: String, foldedStarts: Set<Int>): FoldMap {
            val ranges = foldableRanges(source)
            val foldable = ranges.map { it.first }.toSet()
            val wanted = foldedStarts.intersect(foldable)
            val sourceLines = source.split('\n')
            val lineCount = sourceLines.size
            // display row (0-based) -> source line (1-based), and per-source-line visibility.
            val emitted = ArrayList<Int>()
            val visible = BooleanArray(lineCount + 1) { true }
            var skipUntil = 0
            for (line in 1..lineCount) {
                if (line <= skipUntil) {
                    visible[line] = false
                    continue
                }
                if (line in wanted) {
                    val range = ranges.first { it.first == line }
                    emitted.add(line)
                    skipUntil = range.last
                    continue
                }
                emitted.add(line)
            }
            val display = emitted.joinToString("\n") { sourceLines[it - 1] }
            val sourceToDisplayLine = IntArray(lineCount + 1) { -1 }
            emitted.forEachIndexed { row, line -> sourceToDisplayLine[line] = row }
            // Offset maps, walked once each way.
            val toSource = IntArray(display.length + 1)
            var di = 0
            var si = 0
            // Source line we have consumed up to (1-based). Advanced incrementally so the whole
            // walk stays linear no matter how many lines hide above the slice.
            var consumedLine = 1
            for ((row, line) in emitted.withIndex()) {
                val text = sourceLines[line - 1]
                // Advance the source cursor past hidden lines.
                while (consumedLine < line) {
                    si = source.indexOf('\n', si).let { if (it < 0) source.length else it + 1 }
                    consumedLine++
                }
                for (k in text.indices) {
                    toSource[di++] = si++
                }
                if (row < emitted.size - 1) {
                    // The newline joining this display line to the next.
                    toSource[di++] = si++
                }
                // The cursor now sits at the start of the next source line, whatever it is, so
                // the next row's advance loop sees a fresh invariant instead of a stale line
                // number pointing ten lines back.
                consumedLine = line + 1
            }
            // The caret position past the last character maps to the end of the source.
            toSource[display.length] = source.length
            // Source offsets inside hidden regions have no display position; they clamp to the
            // fold point — the end of the first line — which is where the caret is moved when a
            // fold closes over it.
            val toDisplay = IntArray(source.length + 1)
            var d = 0
            // Invert toSource, filling gaps (hidden source spans) with the current display offset.
            var prevDisplay = 0
            for (src in 0..source.length) {
                while (d <= display.length && toSource[d] < src) {
                    prevDisplay = d
                    d++
                }
                toDisplay[src] = if (d <= display.length && toSource[d] == src) d else prevDisplay
            }
            val foldedRanges = ranges.filter { it.first in wanted }
            return FoldMap(
                source = source,
                display = display,
                displayLines = emitted,
                sourceToDisplayLine = sourceToDisplayLine,
                toSource = toSource,
                toDisplay = toDisplay,
                foldedRanges = foldedRanges,
                foldableStarts = foldable,
            )
        }

        /**
         * Applies a display-text edit to the source: splits old and new display text into common
         * prefix, changed middle and common suffix, maps the two boundaries back to source
         * offsets, and splices the middle into the source.
         *
         * The caret and anchor travel as offsets in the NEW display text, mapped through the
         * diff structure itself: before the change they read through the old map, past it they
         * shift by the length delta, and inside the changed middle they land inside the spliced
         * source text. Mapping them through the old map instead would misplace every caret
         * after an insertion, because the old map knows nothing past the edit point.
         */
        fun applyDisplayEdit(
            map: FoldMap,
            oldDisplay: String,
            newDisplay: String,
            caretDisplay: Int,
            anchorDisplay: Int,
        ): DisplayEdit {
            var pre = 0
            val maxPre = minOf(oldDisplay.length, newDisplay.length)
            while (pre < maxPre && oldDisplay[pre] == newDisplay[pre]) pre++
            var suf = 0
            while (suf < maxPre - pre &&
                oldDisplay[oldDisplay.length - 1 - suf] == newDisplay[newDisplay.length - 1 - suf]
            ) suf++
            // Clamp the span to the selection: the raw prefix/suffix match can stretch across the
            // whole text (a single inserted character at the very start matches everything after
            // it), and splicing that span into the source would delete every hidden line it
            // covers. The selection always contains the real change, so narrowing to it can only
            // spare text, never drop typed characters.
            val loSel = minOf(caretDisplay, anchorDisplay).coerceIn(0, newDisplay.length)
            val hiSel = maxOf(caretDisplay, anchorDisplay).coerceIn(0, newDisplay.length)
            pre = minOf(pre, loSel)
            suf = minOf(suf, newDisplay.length - hiSel)
            // Every index below is clamped to the map, not to the texts: a caller can hand in a
            // display text the map was not built from (two keystrokes in one frame, before
            // recomposition rebuilds it), and an unclamped index then throws. Clamping turns
            // that into a slightly approximate splice instead of a crash.
            val lastMap = (map.toSource.size - 1).coerceAtLeast(0)
            val srcStart = map.toSource[pre.coerceIn(0, oldDisplay.length).coerceIn(0, lastMap)]
            val srcEnd = map.toSource[(oldDisplay.length - suf).coerceIn(0, oldDisplay.length)
                .coerceIn(0, lastMap)]
            val middle = newDisplay.substring(pre, newDisplay.length - suf)
            val newSource = map.source.substring(0, srcStart) + middle +
                map.source.substring(srcEnd)
            val delta = newDisplay.length - oldDisplay.length
            fun mapNew(p: Int): Int {
                val q = p.coerceIn(0, newDisplay.length)
                return when {
                    q <= pre -> map.toSource[q.coerceIn(0, lastMap)]
                    // The boundary itself belongs to the middle: a caret sitting right after
                    // inserted text is past the insertion, not before it.
                    q > newDisplay.length - suf ->
                        map.toSource[(q - delta).coerceIn(0, oldDisplay.length).coerceIn(0, lastMap)]
                    else -> srcStart + (q - pre)
                }
            }
            return DisplayEdit(newSource, mapNew(caretDisplay), mapNew(anchorDisplay))
        }
    }
}

/** A display edit expressed back in source coordinates. */
internal data class DisplayEdit(
    val source: String,
    val caret: Int,
    val anchor: Int,
)

/**
 * Maps a source-keyed diagnostic into display coordinates, or null when it hides with a fold.
 *
 * A diagnostic whose line is hidden has nowhere to squiggle and no row for a bar, so it drops out
 * of the drawn set entirely — the file still fails the same checks, and unfolding brings the mark
 * back. Offsets travel through the offset maps; a range reaching into hidden text clamps to the
 * fold point rather than stretching across rows that are not there.
 */
internal fun mapDiagnosticToDisplay(
    map: FoldMap,
    diagnostic: com.closecode.lang.Diagnostic,
): com.closecode.lang.Diagnostic? {
    val displayLine = if (diagnostic.line in 1..map.sourceToDisplayLine.size - 1) {
        map.sourceToDisplayLine[diagnostic.line]
    } else {
        -1
    }
    if (displayLine < 0) return null
    val start = diagnostic.offset.coerceIn(0, map.source.length)
    val end = (diagnostic.offset + diagnostic.length).coerceIn(0, map.source.length)
    val displayStart = map.toDisplay[start]
    val displayEnd = map.toDisplay[end]
    if (displayEnd <= displayStart && diagnostic.length > 0) return null
    val lineStart = map.display.lastIndexOf('\n', (displayStart - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    return diagnostic.copy(
        line = displayLine + 1,
        column = displayStart - lineStart + 1,
        offset = displayStart,
        length = displayEnd - displayStart,
    )
}

/**
 * Every foldable block in [source] as 1-based inclusive line ranges with more than one line:
 * brace blocks, import groups of two or more, and multi-line block comments.
 */
internal fun foldableRanges(source: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    for (pair in bracePairs(source)) {
        val first = lineOfOffset(source, pair.first)
        val last = lineOfOffset(source, pair.last)
        if (last > first) out.add(first..last)
    }
    out.addAll(importGroupRanges(source))
    out.addAll(blockCommentRanges(source))
    // Exact duplicates only: nested folds are all individually foldable, and the sweep keeps the
    // outermost when several close over the same rows.
    return out.distinct().sortedWith(compareBy({ it.first }, { it.last }))
}

/** Maximal runs of two or more consecutive import lines, as 1-based inclusive ranges. */
internal fun importGroupRanges(source: String): List<IntRange> {
    val lines = source.split('\n')
    val inComment = commentLineMask(source, lines.size)
    val out = ArrayList<IntRange>()
    var runStart = -1
    for ((index, line) in lines.withIndex()) {
        val number = index + 1
        if (!inComment[number] && line.trimStart().startsWith("import ")) {
            if (runStart < 0) runStart = number
        } else {
            if (runStart >= 0 && number - runStart >= 2) out.add(runStart..number - 1)
            runStart = -1
        }
    }
    if (runStart >= 0 && lines.size - runStart + 1 >= 2) out.add(runStart..lines.size)
    return out
}

/** Which 1-based lines sit inside a block comment. */
internal fun commentLineMask(source: String, lineCount: Int): BooleanArray {
    val mask = BooleanArray(lineCount + 1)
    var i = 0
    while (i < source.length) {
        if (source.startsWith("/*", i)) {
            val close = source.indexOf("*/", i + 2)
            val end = if (close < 0) source.length else close + 2
            val first = lineOfOffset(source, i)
            val last = lineOfOffset(source, end.coerceAtMost(source.length))
            for (line in first..last) {
                if (line in 1..lineCount) mask[line] = true
            }
            i = end
        } else {
            i++
        }
    }
    return mask
}

/** Block comments spanning more than one line, as 1-based inclusive ranges. */
internal fun blockCommentRanges(source: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var i = 0
    while (i < source.length) {
        if (source.startsWith("/*", i)) {
            val close = source.indexOf("*/", i + 2)
            val end = if (close < 0) source.length else close + 2
            val first = lineOfOffset(source, i)
            val last = lineOfOffset(source, end.coerceAtMost(source.length))
            if (last > first) out.add(first..last)
            i = end
        } else {
            i++
        }
    }
    return out
}

/**
 * Display offset to source offset, clamped to the map.
 *
 * Event handlers (keystrokes, taps, suggestion picks) can read a map the composition has not
 * rebuilt yet — two keystrokes in one frame, a tap right after typing — so the offset can point
 * past the map's end. Clamping turns that into a slightly approximate position instead of an
 * ArrayIndexOutOfBoundsException that kills the app mid-keystroke.
 */
internal fun FoldMap.sourceAt(displayOffset: Int): Int =
    toSource[displayOffset.coerceIn(0, toSource.size - 1)]

/** Source offset to display offset, clamped for the same reason. */
internal fun FoldMap.displayAt(sourceOffset: Int): Int =
    toDisplay[sourceOffset.coerceIn(0, toDisplay.size - 1)]
