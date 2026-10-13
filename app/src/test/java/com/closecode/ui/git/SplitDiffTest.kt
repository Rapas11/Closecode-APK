package com.closecode.ui.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the side-by-side alignment: equal lines pair up, changed blocks pair first, leftovers
 * go one-sided, and counts can never escape the line lists.
 */
class SplitDiffTest {

    @Test
    fun identicalTextsPairEveryLine() {
        val rows = splitDiff("a\nb\n", "a\nb\n")
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.oldLine == it.newLine && it.oldLine != null })
    }

    @Test
    fun aOneWordEditReadsAsOneRow() {
        val rows = splitDiff("line1\nline2\nline3\n", "line1\nLINE2\nline3\n")
        val changed = rows.filter { it.oldLine != it.newLine }
        assertEquals(listOf(DiffRow("line2", "LINE2")), changed)
    }

    @Test
    fun addedLinesAreNewSideOnly() {
        val rows = splitDiff("a\nc\n", "a\nb\nc\n")
        assertTrue(rows.contains(DiffRow(null, "b")))
        assertTrue(rows.none { it.oldLine == "b" })
    }

    @Test
    fun deletedLinesAreOldSideOnly() {
        val rows = splitDiff("a\nb\nc\n", "a\nc\n")
        assertTrue(rows.contains(DiffRow("b", null)))
        assertTrue(rows.none { it.newLine == "b" })
    }

    @Test
    fun unevenReplacementsPairFirst() {
        val rows = splitDiff("a\nx\ny\nc\n", "a\nz\nc\n")
        // x pairs with z; y has no partner and goes old-side only.
        assertTrue(rows.contains(DiffRow("x", "z")))
        assertTrue(rows.contains(DiffRow("y", null)))
    }

    @Test
    fun emptyOldSideIsAllAdded() {
        val rows = splitDiff("", "a\nb\n")
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.oldLine == null && it.newLine != null })
    }

    @Test
    fun unifyNumbersBothSides() {
        val rows = com.closecode.ui.ide.unify(splitDiff("a\nb\n", "a\nB\n"))
        assertEquals(2, rows.size)
        assertEquals(1 to 1, (rows[0].oldNo to rows[0].newNo))
        assertEquals(' ', rows[0].sign)
        assertEquals(2 to 2, (rows[1].oldNo to rows[1].newNo))
        assertEquals('+', rows[1].sign)
        assertTrue(rows[1].added && rows[1].removed)
    }

    @Test
    fun newFilesCollapseToSixPlusABar() {
        val added = (1..20).joinToString("\n") { "line$it" }
        val rows = com.closecode.ui.ide.unify(splitDiff("", added))
        val blocks = com.closecode.ui.ide.collapseUnified(rows, isNew = true, emptyMap())
        assertEquals(2, blocks.size)
        val gap = blocks[1] as com.closecode.ui.ide.DiffBlock.Gap
        assertEquals(14, gap.hidden)
    }

    @Test
    fun changedFilesKeepThreeLinesOfContext() {
        val old = (1..20).joinToString("\n") { "line$it" }
        val new = (1..20).joinToString("\n") { if (it == 10) "CHANGED" else "line$it" }
        val rows = com.closecode.ui.ide.unify(splitDiff(old, new))
        val blocks = com.closecode.ui.ide.collapseUnified(rows, isNew = false, emptyMap())
        val gaps = blocks.filterIsInstance<com.closecode.ui.ide.DiffBlock.Gap>()
        assertEquals(2, gaps.size)
        // Leading run keeps its last 3, trailing run its first 3.
        assertEquals(6, gaps[0].hidden)
        assertEquals(7, gaps[1].hidden)
    }

    @Test
    fun everythingIsCapped() {
        val big = (1..5000).joinToString("\n") { "line$it" }
        assertTrue(splitDiff(big, big + "x").size <= 2000)
    }
}
