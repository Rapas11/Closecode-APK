package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scroll-frame caches: [LineIndex] answers offsets, lines and depths without walking the
 * file, and [DeclarationIndex] answers the sticky header the same way.
 *
 * Each test pins the cache against the function it replaces, so a faster answer that disagrees
 * with the old one fails here instead of drawing wrong code on the phone.
 */
class ScrollCacheTest {

    private val sample =
        "fun main() {\n" + // 1
            "    val s = \"{\" // }\n" + // 2: braces in a string and a comment name nothing
            "    if (true) {\n" + // 3: a control block, not a declaration
            "        println(s)\n" + // 4
            "    }\n" + // 5
            "}\n" + // 6
            "fun other() {\n" + // 7
            "}\n" // 8

    @Test
    fun lineStartsMatchTheWalkingOffset() {
        val index = LineIndex(sample)
        for (line in 1..8) {
            assertEquals(
                "line $line",
                offsetOfLineColumn(sample, line, 1),
                index.lineStart(line),
            )
        }
    }

    @Test
    fun offsetsMatchTheWalkingOffset() {
        val index = LineIndex(sample)
        assertEquals(offsetOfLineColumn(sample, 4, 9), index.offsetOf(4, 9))
        assertEquals(offsetOfLineColumn(sample, 1, 1), index.offsetOf(1, 1))
        assertEquals(sample.length, index.offsetOf(99, 99))
        assertEquals(0, index.offsetOf(0, 0))
    }

    @Test
    fun linesMatchTheWalkingLineOf() {
        val index = LineIndex(sample)
        var offset = 0
        for (line in 1..8) {
            assertEquals("offset $offset", line, index.lineOf(offset))
            assertEquals("offset $offset", lineOfOffset(sample, offset), index.lineOf(offset))
            offset = sample.indexOf('\n', offset) + 1
        }
        assertEquals(8, index.lineOf(sample.length))
    }

    @Test
    fun depthsMatchOpenBlockDepth() {
        val index = LineIndex(sample)
        for (line0 in 0 until 8) {
            val above = sample.substring(0, index.lineStart(line0 + 1))
            assertEquals("line ${line0 + 1}", openBlockDepth(above), index.depthAtLine0(line0))
        }
        // The string's brace and the comment's brace change nothing.
        assertEquals(1, index.depthAtLine0(1))
        assertEquals(1, index.depthAtLine0(2))
        assertEquals(2, index.depthAtLine0(3))
    }

    @Test
    fun declarationsMatchEnclosingDeclarationAt() {
        val index = DeclarationIndex(sample)
        for (line in 1..8) {
            assertEquals(
                "line $line",
                enclosingDeclarationAt(sample, line),
                index[line - 1],
            )
        }
    }

    @Test
    fun controlBlocksNameNothing() {
        val code = "fun main() {\n    if (true) {\n        println()\n    }\n}\n"
        val index = DeclarationIndex(code)
        // Line 3 sits in the `if`, which is inside `main`: the header names main, never the if.
        assertEquals("fun main()", index[2]?.second)
        assertEquals(1, index[2]?.first)
        assertNull(index[0])
    }

    @Test
    fun commentBracesBehaveLikeTheOriginalScan() {
        // enclosingDeclarationAt has no comment handling, so a `}` inside a `//` comment pops
        // the open block. The index reproduces that quirk exactly rather than diverging from
        // the signature-help path that still uses the original.
        val index = DeclarationIndex(sample)
        assertEquals(enclosingDeclarationAt(sample, 4), index[3])
    }

    @Test
    fun emptyTextHasOneEmptyLine() {
        val index = LineIndex("")
        assertEquals(1, index.lineCount)
        assertEquals(0, index.lineStart(1))
        assertEquals(1, index.lineOf(0))
        assertEquals(0, index.depthAtLine0(0))
        assertNull(DeclarationIndex("")[0])
    }

    @Test
    fun longNamesLoseTheMiddleNotTheExtension() {        assertEquals("short.kt", ellipsizeMiddle("short.kt"))
        assertEquals("VeryLongFileNameIsVery….kt", ellipsizeMiddle("VeryLongFileNameIsVeryLong.kt"))
        val cut = ellipsizeMiddle("VeryLongFileNameIsVeryLong.kt")
        assertTrue(cut.endsWith(".kt"))
        assertTrue(cut.length < "VeryLongFileNameIsVeryLong.kt".length)
        assertEquals("noextensionmiddle", ellipsizeMiddle("noextensionmiddle"))
    }

    @Test
    fun dragsLockToTheDominantAxisPastSlop() {        val slop = 8f
        // Undecided inside slop: a tap commits to nothing.
        assertEquals(0, panDirectionLocked(5f, 5f, slop))
        // Sideways and leading: the column pans.
        assertEquals(1, panDirectionLocked(20f, 6f, slop))
        // Vertical and leading: the column stands down for the vertical scroller.
        assertEquals(-1, panDirectionLocked(6f, 20f, slop))
        // Diagonal ties go horizontal only past slop on both: equal travel is not a lead.
        assertEquals(0, panDirectionLocked(8f, 8f, slop))
        assertEquals(0, panDirectionLocked(9f, 9f, slop))
    }

    @Test
    fun detailsNeedADeclarationNotAUsage() {
        assertTrue(declaresSymbol("    val username = \"\" ", "username"))
        assertTrue(declaresSymbol("fun signIn(name: String) {", "signIn"))
        assertTrue(declaresSymbol("class HomeScreen {", "HomeScreen"))
        assertFalse(declaresSymbol("    println(username)", "username"))
        assertFalse(declaresSymbol("    val usernames = x", "username"))
        assertFalse(declaresSymbol("", "username"))
        assertFalse(declaresSymbol("val x = 1", ""))
    }
}
