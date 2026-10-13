package com.closecode.ui.ide

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rainbow brackets: off unless asked for, cycling by depth, and depth that survives both line breaks
 * and scrolling.
 */
class RainbowBracketTest {

    private val cycle = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)

    private val off = emptyList<Color>()

    private fun bracketColours(
        code: String,
        initialDepth: Int = 0,
        palette: List<Color> = cycle,
    ): List<Color?> {
        val out = ArrayList<Color?>()
        val marked = highlightCode(
            code,
            com.closecode.ui.theme.DarkColors,
            CodeLanguage.PROGRAMMING,
            palette,
            initialDepth,
        )
        for (span in marked.spanStyles) {
            var i = span.start
            while (i < span.end) {
                if (marked.text[i] in "([{}])") out.add(span.item.color)
                i++
            }
        }
        return out
    }

    @Test
    fun nothingIsColouredWhenTheSettingIsOff() {
        assertNull(rainbowBracketColor(0, off))
        // Brackets keep the base text colour, so nothing is spelled out for them at all.
        assertEquals(
            emptyList<Color?>(),
            bracketColours("fun a() { if (b) { c() } }", palette = off),
        )
    }

    @Test
    fun depthCyclesThroughTheCycleAndWraps() {
        assertEquals(Color.Red, rainbowBracketColor(0, cycle))
        assertEquals(Color.Green, rainbowBracketColor(1, cycle))
        assertEquals(Color.Magenta, rainbowBracketColor(4, cycle))
        assertEquals(Color.Yellow, rainbowBracketColor(3, cycle))
        // Past the end it wraps rather than running out of colours.
        assertEquals(Color.Red, rainbowBracketColor(5, cycle))
        // A closing bracket reports the depth it closes, one less than the opening.
        assertEquals(Color.Magenta, rainbowBracketColor(-1, cycle))
    }

    @Test
    fun nestedBracketsGetDifferentColours() {
        val colours = bracketColours("fun a() { if (b) { c() } }")
        assertTrue("expected brackets to be coloured", colours.isNotEmpty())
        assertTrue("nesting should produce more than one colour", colours.distinct().size > 1)
    }

    @Test
    fun depthCarriesAcrossLines() {
        // A block spans lines, so a counter reset per line gave every line's first bracket the same
        // colour. That, and the identical code on line, must colour the same way on two lines.
        val flat = bracketColours("fun a() { fun b() { c() } }")
        val acrossLines = bracketColours("fun a() {\n    fun b() {\n        c()\n    }\n}")
        assertEquals(flat.size, acrossLines.size)
        assertEquals(flat, acrossLines)
    }

    @Test
    fun depthCarriesAcrossWindows() {
        // The bug this pins: the renderer only ever sees the visible slice, so a counter starting at
        // zero coloured every window from the shallow end no matter how deep the reader had
        // scrolled. Starting from the depth above the slice is what makes the colours mean anything.
        val whole = bracketColours("fun a() { fun b() { c() } }")
        val midway = bracketColours("c()", initialDepth = 2)
        // The seventh bracket overall is the `(` of the inner call, at depth 2 — the same bracket
        // the sliced run starts from, so the two runs must agree on it.
        assertEquals(Color.Blue, midway.first())
        assertEquals(whole[6], midway.first())
        assertEquals(whole[7], midway[1])
    }

    @Test
    fun depthAboveASliceIsMeasuredFromTheText() {
        // A brace inside a string or a comment is a character, so it must not add a level.
        assertEquals(0, openBlockDepth(""))
        assertEquals(0, openBlockDepth("val a = 1"))
        assertEquals(1, openBlockDepth("fun a() {"))
        assertEquals(2, openBlockDepth("fun a() { fun b() {"))
        // The brace is inside a string, so it does not add a level; the outer one still counts.
        assertEquals(1, openBlockDepth("fun a() { val s = \"{\""))
        assertEquals(0, openBlockDepth("// fun a() {\nval b = 2"))
        assertEquals(0, openBlockDepth("// fun a() {"))
        // Closed again: the block is over, so nothing is open above.
        assertEquals(0, openBlockDepth("fun a() { }"))
        // Never negative, even when the prefix is unbalanced.
        assertEquals(0, openBlockDepth("}"))
        // Parentheses count too, exactly as the colour pass counts them: a slice starting inside
        // `foo(bar,` is one level deep even though no brace is open.
        assertEquals(1, openBlockDepth("foo(bar,"))
        assertEquals(0, openBlockDepth("foo(bar)"))
    }
}
