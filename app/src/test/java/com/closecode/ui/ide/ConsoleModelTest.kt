package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the console's rules: capped at 500 oldest-first, blanks skipped, counts for the badge
 * and the filters, and the load stamp every page gets.
 */
class ConsoleModelTest {

    @Test
    fun blankLinesAreNotSignal() {
        assertTrue(appendConsole(emptyList(), ConsoleLine(ConsoleLevel.Log, "  ")).isEmpty())
    }

    @Test
    fun theListCapsAtFiveHundredOldestFirst() {
        var lines = emptyList<ConsoleLine>()
        repeat(CONSOLE_MAX_LINES + 10) { i ->
            lines = appendConsole(lines, ConsoleLine(ConsoleLevel.Log, "line $i"))
        }
        assertEquals(CONSOLE_MAX_LINES, lines.size)
        assertEquals("line 10", lines.first().text)
        assertEquals("line ${CONSOLE_MAX_LINES + 9}", lines.last().text)
    }

    @Test
    fun countsSplitErrorsWarningsAndAll() {
        val lines = listOf(
            ConsoleLine(ConsoleLevel.Error, "a"),
            ConsoleLine(ConsoleLevel.Error, "b"),
            ConsoleLine(ConsoleLevel.Warning, "c"),
            ConsoleLine(ConsoleLevel.Log, "d"),
        )
        assertEquals(Triple(2, 1, 4), consoleCounts(lines))
    }

    @Test
    fun anEmptyConsoleCountsZero() {
        assertEquals(Triple(0, 0, 0), consoleCounts(emptyList()))
    }

    @Test
    fun theLoadStampReadsMilliseconds() {
        assertEquals("Page loaded in 84 ms", loadStampLine(84).text)
        assertEquals(ConsoleLevel.Log, loadStampLine(84).level)
    }

    @Test
    fun lineOffsetsCountNewlines() {
        assertEquals(0, offsetOfLine("a\nb\nc", 1))
        assertEquals(2, offsetOfLine("a\nb\nc", 2))
        assertEquals(4, offsetOfLine("a\nb\nc", 3))
    }

    @Test
    fun pastTheEndClampsToTheEnd() {
        assertEquals(5, offsetOfLine("a\nb\nc", 99))
        assertEquals(0, offsetOfLine("", 5))
    }

}
