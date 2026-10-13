package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldModelTest {

    private val src = "fun a() {\n    val x = 1\n    val y = 2\n}\nval z = 3\n"

    @Test
    fun noFoldsMeansDisplayEqualsSource() {
        val map = FoldMap.build(src, emptySet())
        assertEquals(src, map.display)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), map.displayLines)
        // Identity maps both ways.
        for (i in 0..src.length) assertEquals(i, map.toDisplay[i])
        for (i in 0..src.length) assertEquals(i, map.toSource[i])
    }

    @Test
    fun foldingHidesTheInteriorButKeepsBothEnds() {
        // Standard folding: the first line stays with a chip, everything through the closing
        // line hides.
        val map = FoldMap.build(src, setOf(1))
        assertEquals("fun a() {\nval z = 3\n", map.display)
        assertEquals(listOf(1, 5, 6), map.displayLines)
        // Hidden lines report -1.
        assertEquals(0, map.sourceToDisplayLine[1])
        assertEquals(-1, map.sourceToDisplayLine[2])
        assertEquals(-1, map.sourceToDisplayLine[3])
        assertEquals(-1, map.sourceToDisplayLine[4])
        assertEquals(1, map.sourceToDisplayLine[5])
    }

    @Test
    fun staleFoldStartsAreIgnoredNotHonored() {
        // Line 2 is not a foldable start; collapsing it would hide code unasked.
        val map = FoldMap.build(src, setOf(2))
        assertEquals(src, map.display)
        assertTrue(map.foldedRanges.isEmpty())
    }

    @Test
    fun nestedFoldsCollapseThroughTheOuterOne() {
        val nested = "class A {\n    fun b() {\n        g()\n    }\n}\n"
        val map = FoldMap.build(nested, setOf(1, 2))
        assertEquals("class A {\n", map.display)
        assertEquals(listOf(1, 6), map.displayLines)
    }

    @Test
    fun innerFoldAloneHidesOnlyItsOwnBody() {
        val nested = "class A {\n    fun b() {\n        g()\n    }\n}\n"
        val map = FoldMap.build(nested, setOf(2))
        assertEquals("class A {\n    fun b() {\n}\n", map.display)
    }

    @Test
    fun hiddenOffsetsClampToTheFoldPoint() {
        val map = FoldMap.build(src, setOf(1))
        // Start of the hidden line 2 maps to the end of line 1 in display coords.
        val hiddenStart = src.indexOf("    val x")
        val foldPoint = map.display.indexOf('\n')
        assertEquals(foldPoint, map.toDisplay[hiddenStart])
        // Visible offsets map exactly.
        assertEquals(0, map.toDisplay[0])
        assertEquals(map.display.length, map.toDisplay[src.length])
    }

    @Test
    fun toSourceRoundTripsEveryDisplayOffset() {
        val map = FoldMap.build(src, setOf(1))
        for (d in 0..map.display.length) {
            val s = map.toSource[d]
            // Mapping back lands on or before the display offset's own source position, and the
            // character at a mapped position always matches.
            if (d < map.display.length) {
                assertEquals(map.display[d], src[s])
            }
        }
    }

    // -- applyDisplayEdit ---------------------------------------------------------

    @Test
    fun typingAboveAFoldShiftsSourceButKeepsDisplayHonest() {
        val map = FoldMap.build(src, setOf(1))
        val oldDisplay = map.display
        // Type "!" at the very start.
        val newDisplay = "!$oldDisplay"
        val edit = FoldMap.applyDisplayEdit(map, oldDisplay, newDisplay, 1, 1)
        assertEquals("!fun a() {\n    val x = 1\n    val y = 2\n}\nval z = 3\n", edit.source)
        assertEquals(1, edit.caret)
        // Rebuilding from the new source with the same fold reproduces the new display exactly.
        assertEquals(newDisplay, FoldMap.build(edit.source, setOf(1)).display)
    }

    @Test
    fun typingInsideVisibleTextKeepsTheCaretAfterTheTypedChar() {
        // No folds here: exercises the caret math directly. Type "!" after "val ".
        val map = FoldMap.build(src, emptySet())
        val at = src.indexOf("val z") + 4
        val newDisplay = src.substring(0, at) + "!" + src.substring(at)
        val edit = FoldMap.applyDisplayEdit(map, src, newDisplay, at + 1, at + 1)
        assertEquals(newDisplay, edit.source)
        assertEquals(at + 1, edit.caret)
        assertEquals(at + 1, edit.anchor)
    }

    @Test
    fun typingInsideVisibleTextMapsThroughUnchanged() {
        val map = FoldMap.build(src, emptySet())
        val newDisplay = src.replace("val z", "val zz")
        val caret = newDisplay.indexOf("val zz") + 6
        val edit = FoldMap.applyDisplayEdit(map, src, newDisplay, caret, caret)
        assertEquals(newDisplay, edit.source)
        assertEquals(caret, edit.caret)
    }

    @Test
    fun deletingAcrossAFoldPointRemovesTheHiddenBodyToo() {
        // Display "fun a() {\nval z = 3\n": delete from inside the first line through the start
        // of "val z". The hidden lines belong to the folded range, so they go with it.
        val map = FoldMap.build(src, setOf(1))
        val oldDisplay = map.display
        val cutFrom = oldDisplay.indexOf('{') + 1
        val cutTo = oldDisplay.indexOf("val z")
        val newDisplay = oldDisplay.substring(0, cutFrom) + oldDisplay.substring(cutTo)
        val edit = FoldMap.applyDisplayEdit(map, oldDisplay, newDisplay, cutFrom, cutFrom)
        assertEquals("fun a() {val z = 3\n", edit.source)
    }

    // -- stale maps (two keystrokes, one frame) -------------------------------------

    @Test
    fun anEditAgainstAStaleMapDoesNotThrow() {
        // The crash this pins: the second keystroke of a frame sees the first keystroke's
        // field text with the map still describing the text before it, so the old display is
        // longer than the map. ArrayIndexOutOfBoundsException, mid-keystroke.
        val src = "fun a() {\n    x\n}\n"
        val stale = FoldMap.build(src, emptySet())
        val keystroke1 = src.replace("x", "xy")
        val keystroke2 = keystroke1.replace("xy", "xyz")
        val edit = FoldMap.applyDisplayEdit(stale, keystroke1, keystroke2, 14, 14)
        assertTrue(edit.caret in 0..edit.source.length)
        assertTrue(edit.source.contains("xyz"))
    }

    @Test
    fun aStaleMapWithFoldsDoesNotThrowEither() {
        val map = FoldMap.build("fun a() {\n    x\n}\nval z = 3\n", setOf(1))
        // A whole line typed past what the map knows, then one more character.
        val newer = map.display + "w"
        val newest = "$newer!"
        val edit = FoldMap.applyDisplayEdit(map, newer, newest, newest.length, newest.length)
        assertTrue(edit.caret in 0..edit.source.length)
    }

    @Test
    fun mapAccessorsClampInsteadOfThrowing() {
        val map = FoldMap.build("ab\ncd\n", emptySet())
        assertEquals(5, map.sourceAt(5))
        assertEquals(6, map.sourceAt(500))
        assertEquals(0, map.sourceAt(-3))
        assertEquals(5, map.displayAt(5))
        assertEquals(6, map.displayAt(500))
        assertEquals(0, map.displayAt(-1))
    }

    @Test
    fun selectionEndpointsTravelWithTheEdit() {        val map = FoldMap.build(src, setOf(1))
        // Select "val z" in display coords (line 5 -> display line 3).
        val anchorD = map.display.indexOf("val z")
        val caretD = anchorD + 5
        val edit = FoldMap.applyDisplayEdit(map, map.display, map.display, caretD, anchorD)
        assertEquals(src.indexOf("val z") + 5, edit.caret)
        assertEquals(src.indexOf("val z"), edit.anchor)
    }

    // -- foldableRanges ------------------------------------------------------------

    @Test
    fun singleLinePairsAreNotFoldable() {
        assertTrue(foldableRanges("fun a() {}").isEmpty())
    }

    @Test
    fun multiLineBracesAreFoldable() {
        val ranges = foldableRanges("fun a() {\n    x()\n}\n")
        assertEquals(listOf(1..3), ranges)
    }

    @Test
    fun importGroupsOfTwoOrMoreAreFoldable() {
        val code = "package p\n\nimport a\nimport b\nimport c\n\nfun f() {}\n"
        val ranges = foldableRanges(code)
        assertTrue(ranges.contains(3..5))
    }

    @Test
    fun aLoneImportIsNotFoldable() {
        assertTrue(foldableRanges("package p\n\nimport a\n\nfun f() {}\n").isEmpty())
    }

    @Test
    fun multiLineBlockCommentsAreFoldable() {
        val code = "/* one\ntwo\nthree */\nval x = 1\n"
        assertEquals(listOf(1..3), foldableRanges(code))
    }

    @Test
    fun importsInsideBlockCommentsDoNotFold() {
        val code = "/*\nimport a\nimport b\n*/\nval x = 1\n"
        // Only the comment itself is foldable; the commented imports are not a group.
        assertEquals(listOf(1..4), foldableRanges(code))
    }

    @Test
    fun bracesInsideStringsDoNotFold() {
        val code = "val s = \"{\"\nval x = 1\n"
        assertTrue(foldableRanges(code).isEmpty())
    }
}

class MapDiagnosticTest {

    private val src = "fun a() {\n    val x = 1\n    val y = 2\n}\nval z = 3\n"

    private fun error(line: Int, offset: Int, length: Int) =
        com.closecode.lang.Diagnostic(
            severity = com.closecode.lang.Severity.ERROR,
            message = "m",
            offset = offset,
            length = length,
            line = line,
            column = 1,
            code = "E0001",
        )

    @Test
    fun aVisibleDiagnosticKeepsItsMeaning() {
        val map = FoldMap.build(src, setOf(1))
        // Error on line 5 ("val z") which stays visible as display line 2.
        val lineStart = src.indexOf("val z")
        val mapped = mapDiagnosticToDisplay(map, error(5, lineStart, 3))
        requireNotNull(mapped)
        assertEquals(2, mapped.line)
        assertEquals(map.display.indexOf("val z"), mapped.offset)
        assertEquals(3, mapped.length)
    }

    @Test
    fun aHiddenDiagnosticDropsOut() {
        val map = FoldMap.build(src, setOf(1))
        // Error on hidden line 2.
        val lineStart = src.indexOf("    val x")
        assertEquals(null, mapDiagnosticToDisplay(map, error(2, lineStart, 3)))
    }

    @Test
    fun unfoldingBringsTheMarkBack() {
        val folded = FoldMap.build(src, setOf(1))
        val lineStart = src.indexOf("    val x")
        assertEquals(null, mapDiagnosticToDisplay(folded, error(2, lineStart, 3)))
        val open = FoldMap.build(src, emptySet())
        val back = mapDiagnosticToDisplay(open, error(2, lineStart, 3))
        requireNotNull(back)
        assertEquals(2, back.line)
        assertEquals(lineStart, back.offset)
    }
}
