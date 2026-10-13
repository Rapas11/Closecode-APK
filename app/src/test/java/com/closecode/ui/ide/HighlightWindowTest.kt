package com.closecode.ui.ide

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.PageColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor colourizes only the slice of the file that can be on screen, so colourizing that slice
 * has to give the same colours those rows would have had from the whole buffer.
 *
 * [highlightCode] works one line at a time with nothing carried between them — which is exactly
 * what makes windowing possible — and this pins that, so a future change that reaches across a
 * newline (a block comment, a multi-line string) is caught here rather than as a mysterious colour
 * change halfway down the screen.
 */
class HighlightWindowTest {

    private val colors: PageColors = DarkColors

    private val source = """
        package com.closecode

        import android.os.Bundle

        /**
         * A block comment that
         * spans several rows.
         */
        @androidx.compose.Composable
        fun main() {
            val name = "world"
            // A line comment.
            val open = 'a'
            val count = 42
            println("Hello, " + name + count)
        }
    """.trimIndent()

    /** The style a single character ends up drawn with, or null for the plain text colour. */
    private fun styleAt(text: AnnotatedString, index: Int): SpanStyle? =
        text.spanStyles.lastOrNull { it.start <= index && index < it.end }?.item

    @Test
    fun aSliceCarriesExactlyTheColoursTheWholeFileGaveThoseRows() {
        val full = highlightCode(source, colors)
        assertEquals(source, full.text)
        val lines = source.lines()
        for (from in lines.indices) {
            val to = (from + 4).coerceAtMost(lines.lastIndex)
            val slice = sliceOfLines(lines, from..to)
            val highlighted = highlightCode(slice, colors)
            assertEquals(slice, highlighted.text)

            val startOffset = offsetOfLineColumn(source, from + 1, 1)
            for (i in highlighted.text.indices) {
                assertEquals(
                    "row ${from + 1}, column $i",
                    styleAt(full, startOffset + i),
                    styleAt(highlighted, i),
                )
            }
        }
    }

    @Test
    fun oneRowOnItsOwnIsColouredTheSameAsInsideTheWholeFile() {
        // The window is often a handful of rows in the middle of a file, so the single-row case is
        // the one that runs most often while typing.
        val full = highlightCode(source, colors)
        val lines = source.lines()
        for (row in lines.indices) {
            val highlighted = highlightCode(sliceOfLines(lines, row..row), colors)
            val startOffset = offsetOfLineColumn(source, row + 1, 1)
            for (i in highlighted.text.indices) {
                assertEquals(
                    "row ${row + 1}, column $i",
                    styleAt(full, startOffset + i),
                    styleAt(highlighted, i),
                )
            }
        }
    }

    @Test
    fun aSliceFromTheWholeBufferIsTheWholeBuffer() {
        val lines = source.lines()
        val whole = sliceOfLines(lines, 0..lines.lastIndex)
        assertEquals(source, whole)
        val full = highlightCode(source, colors)
        val highlighted = highlightCode(whole, colors)
        assertEquals(full.text, highlighted.text)
        assertEquals(full.spanStyles, highlighted.spanStyles)
    }

    @Test
    fun anEmptySliceColoursToNothing() {
        val highlighted = highlightCode("", colors)
        assertEquals("", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }
}
