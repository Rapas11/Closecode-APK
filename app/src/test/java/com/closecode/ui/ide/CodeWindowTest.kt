package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The code text no longer lays out the whole file: it renders only the rows that can be on screen,
 * and [CodeWindow] is what tells everything else where that slice sits.
 *
 * These are the pure halves of that contract — which row of the slice a line lands on, where the
 * slice was pushed to in the content, and which lines are in it at all. They are what lets the
 * gutter, the problem pills, the bulb and the caret stay on their own rows instead of each
 * guessing.
 */
class CodeWindowTest {

    private val lineHeight = 22f
    private val viewport = 880f

    @Test
    fun theFirstLineOfTheWindowMapsToRowZero() {
        assertEquals(0, sliceIndexForLine(1, 0))
        assertEquals(0, sliceIndexForLine(41, 40))
        assertEquals(8, sliceIndexForLine(50, 41))
    }

    @Test
    fun aLineAboveTheWindowMapsToANegativeRow() {
        // Negative is the answer every caller has to handle: the layout simply does not have it.
        assertEquals(-1, sliceIndexForLine(40, 40))
        assertEquals(-40, sliceIndexForLine(1, 40))
    }

    @Test
    fun characterOffsetsAreMappedOntoTheSliceAndClampedAtItsEdges() {
        assertEquals(0, mapOffsetToSlice(100, 100, 50))
        assertEquals(10, mapOffsetToSlice(110, 100, 50))
        assertEquals(50, mapOffsetToSlice(1_000, 100, 50))
        // Above the slice: clamped to its first character rather than answered negatively, so an
        // overlay asking about a line just off the top of the screen gets the edge of the text.
        assertEquals(0, mapOffsetToSlice(40, 100, 50))
    }

    @Test
    fun theWholeFileIsTheIdentityWindow() {
        assertEquals(CodeWindow(0, 0, 0f), CodeWindow.Full)
        assertEquals(6, sliceIndexForLine(7, CodeWindow.Full.firstLine))
        assertEquals(0f, windowTopPx(CodeWindow.Full.firstLine, lineHeight), 0.001f)
    }

    @Test
    fun theSliceSitsOnTheRowItStartsOn() {
        assertEquals(0f, windowTopPx(0, lineHeight), 0.001f)
        assertEquals(40 * lineHeight, windowTopPx(40, lineHeight), 0.001f)
        // A negative first line would push the text off the top of the column.
        assertEquals(0f, windowTopPx(-5, lineHeight), 0.001f)
    }

    @Test
    fun everyLineThatIsOnScreenIsAlwaysInsideTheCodeWindow() {
        val lineCount = 2000
        var scrollTop = 0f
        val end = lineCount * lineHeight
        while (scrollTop < end) {
            val firstOnScreen = (scrollTop / lineHeight).toInt()
            val lastOnScreen =
                ((scrollTop + viewport) / lineHeight).toInt().coerceAtMost(lineCount - 1)
            val window = codeWindowRange(lineCount, scrollTop, viewport, lineHeight)
            assertTrue("window $window misses line $firstOnScreen at $scrollTop", firstOnScreen in window)
            assertTrue("window $window misses line $lastOnScreen at $scrollTop", lastOnScreen in window)
            scrollTop += 37f
        }
    }

    @Test
    fun theGutterWindowIsAlwaysInsideTheCodeWindow() {
        // The gutter asks for rows the code's slice may not contain, and would fall back to the row
        // model for them. It never does, because the code keeps one more row of slack either side —
        // and a gutter row landing in the fallback would be a row whose number and glyphs came from
        // two different places.
        val lineCount = 2000
        var scrollTop = 0f
        val end = lineCount * lineHeight
        while (scrollTop < end) {
            val gutter = gutterWindow(lineCount, scrollTop, viewport, lineHeight)
            val code = codeWindowRange(lineCount, scrollTop, viewport, lineHeight)
            assertTrue("code $code does not cover gutter $gutter at $scrollTop", gutter.first >= code.first)
            assertTrue("code $code does not cover gutter $gutter at $scrollTop", gutter.last <= code.last)
            scrollTop += 37f
        }
    }

    @Test
    fun theCodeWindowDoesNotGrowWithTheFile() {
        val small = codeWindowRange(200, 0f, viewport, lineHeight)
        val huge = codeWindowRange(200_000, 0f, viewport, lineHeight)
        assertEquals(small.count(), huge.count())
        assertTrue("expected a bounded window, got ${huge.count()} rows", huge.count() <= 70)
    }

    @Test
    fun anEditorThatHasNotBeenMeasuredYetRendersTheWholeFile() {
        // The safe answer for the first frame only: guessing a window before there is a viewport
        // would leave the editor blank until the next scroll.
        assertEquals(0 until 2000, codeWindowRange(2000, 0f, 0f, lineHeight))
        assertEquals(0 until 2000, codeWindowRange(2000, 0f, viewport, 0f))
    }

    @Test
    fun anEmptyFileHasNothingToShow() {
        assertEquals(IntRange.EMPTY, codeWindowRange(0, 0f, viewport, lineHeight))
    }

    @Test
    fun theWindowStopsAtTheLastLineInsteadOfRunningPastIt() {
        assertEquals(0 until 10, codeWindowRange(10, 0f, viewport, lineHeight))
        assertEquals(1_999..1_999, codeWindowRange(2000, 1_000_000f, viewport, lineHeight))
    }

    @Test
    fun sliceOfLinesGivesBackExactlyTheRowsItWasGiven() {
        val lines = listOf("package com.closecode", "", "fun main() {", "    println(1)", "}")
        assertEquals("package com.closecode", sliceOfLines(lines, 0..0))
        assertEquals("fun main() {\n    println(1)", sliceOfLines(lines, 2..3))
        assertEquals(lines.joinToString("\n"), sliceOfLines(lines, 0..4))
        assertEquals("", sliceOfLines(lines, IntRange.EMPTY))
    }

    @Test
    fun sliceOfLinesRoundTripsThroughTheLineSplitter() {
        // What makes windowing safe at all: taking rows out and putting them back with the newline
        // that split them gives the same characters, so neither the highlighter nor the renderer can
        // tell a slice from those rows of the buffer.
        val source = "a\n\nb\nc\n"
        val lines = source.lines()
        for (from in 0..lines.lastIndex) {
            for (to in from..lines.lastIndex) {
                assertEquals(
                    lines.subList(from, to + 1).joinToString("\n"),
                    sliceOfLines(lines, from..to),
                )
            }
        }
        assertEquals(source, sliceOfLines(lines, 0..lines.lastIndex))
    }

    @Test
    fun aCrLfDocumentIsJoinedTheSameWayStringLinesSplitsIt() {
        // String.lines() treats CRLF as one terminator, so a round trip through it drops the CR.
        // That is only safe because the highlighter rebuilds the buffer from String.lines() too — a
        // slice that disagreed would draw the coloured characters somewhere other than the ones the
        // editable buffer owns.
        val source = "a\r\nb\r\n"
        val lines = source.lines()
        assertEquals(listOf("a", "b", ""), lines)
        assertEquals("a\nb\n", sliceOfLines(lines, 0..lines.lastIndex))
    }

    @Test
    fun aSliceStartsWhereItsFirstLineStartsInTheDocument() {
        val source = "package x\n\nimport y\nfun f() = 1\n"
        val lines = source.lines()
        for (from in lines.indices) {
            val slice = sliceOfLines(lines, from..from)
            val start = offsetOfLineColumn(source, from + 1, 1)
            assertEquals(slice, source.substring(start, start + slice.length))
        }
    }

    @Test
    fun sliceOfLinesIsSafeWhenTheRangeMatchesNothing() {
        val lines = listOf("only", "two")
        assertEquals("", sliceOfLines(lines, 5..9))
        assertEquals("", sliceOfLines(lines, -4..-1))
        assertEquals("", sliceOfLines(emptyList(), 0..3))
    }

    @Test
    fun aRowTheSliceDoesNotContainFallsBackToTheRowModel() {
        // This is the gutter's case: a row the code's layout has never heard of still needs a top
        // edge, and the row model gives it the same number it would have had without windowing.
        val window = CodeWindow(firstLine = 40, startOffset = 800, topPx = 40 * lineHeight)
        assertEquals(0f, lineTopFromLayout(null, 0, lineHeight, window), 0.001f)
        assertEquals(40 * lineHeight, lineTopFromLayout(null, 40, lineHeight, window), 0.001f)
        assertEquals(45 * lineHeight, lineTopFromLayout(null, 45, lineHeight, window), 0.001f)
        assertEquals(0f, lineTopFromLayout(null, -3, lineHeight, window), 0.001f)
        // And with no window at all — every caller that existed before this — nothing changed.
        assertEquals(45 * lineHeight, lineTopFromLayout(null, 45, lineHeight), 0.001f)
    }

    @Test
    fun aLineWithNoMeasuredTopIsAnsweredNullSoTheRowModelTakesOver() {
        // The guards a caller relies on when it writes `lineTopPx(...) ?: pillTopPx(...)`: past the
        // end of the buffer, or before the first line, there is no row to measure.
        val source = "one\ntwo\nthree"
        val window = CodeWindow(firstLine = 2, startOffset = 8, topPx = 2 * lineHeight)
        assertTrue(lineTopPx(null, source, 1, window) == null)
        assertTrue(lineTopPx(null, source, 0, window) == null)
        assertTrue(lineTopPx(null, source, 4, window) == null)
    }
}
