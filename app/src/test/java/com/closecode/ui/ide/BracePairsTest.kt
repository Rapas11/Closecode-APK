package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan that finds `{ … }` pairs for the bracket guides.
 *
 * The interesting cases are the braces that are not braces. A guide drawn from a `{` inside a
 * comment, a string or a character literal would start at a brace the code never opened, and a
 * guide drawn from a block that was never closed would run off to nowhere — so both are pinned
 * here rather than left to be discovered on screen.
 */
class BracePairsTest {

    @Test
    fun ablockIsPairedWithTheBraceThatClosesIt() {
        val source = "fun main() {\n    one()\n}\n"
        val pairs = bracePairs(source)

        assertEquals(1, pairs.size)
        assertEquals("{\n    one()\n}", source.substring(pairs[0].first, pairs[0].last + 1))
    }

    @Test
    fun nestedBlocksComeBackOutermostFirst() {
        val source = "fun a() {\n    if (x) {\n        y()\n    }\n}\n"
        val pairs = bracePairs(source)

        assertEquals(2, pairs.size)
        assertTrue("outer opens first", pairs[0].first < pairs[1].first)
        assertTrue("outer closes last", pairs[0].last > pairs[1].last)
        assertTrue(source.substring(pairs[0].first, pairs[0].last + 1).contains("if (x)"))
    }

    @Test
    fun bracesInsideCommentsAndStringsAreNotBraces() {
        val source = """
            fun a() {
                // {
                val s = "}"
                /* } */
                val c = '}'
                one()
            }
        """.trimIndent()

        assertEquals(1, bracePairs(source).size)
    }

    @Test
    fun arawStringIsOneTokenRatherThanThreeQuotes() {
        val source = "val q = \"\"\"\n{ nope }\n\"\"\"\nfun a() {\n}\n"

        assertEquals(1, bracePairs(source).size)
    }

    @Test
    fun ablockLeftOpenDrawsNothing() {
        // A half-typed block must not send a rule off the bottom of the screen.
        assertEquals(emptyList<IntRange>(), bracePairs("fun a() {\n    one()\n"))
        assertEquals(emptyList<IntRange>(), bracePairs("}\n"))
        assertEquals(emptyList<IntRange>(), bracePairs(""))
    }

    @Test
    fun aOffsetIsCountedAsTheLineItFallsOn() {
        assertEquals(1, lineOfOffset("a\nb\nc", 0))
        assertEquals(1, lineOfOffset("a\nb\nc", 1))
        assertEquals(2, lineOfOffset("a\nb\nc", 2))
        assertEquals(3, lineOfOffset("a\nb\nc", 4))
        // Past the end clamps rather than inventing a line.
        assertEquals(3, lineOfOffset("a\nb\nc", 999))
    }
}
