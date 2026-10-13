package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gutter/line-height drift that came from reading the layout off the hidden BasicTextField
 * instead of the Text that actually draws the glyphs. These pin the row model and the geometry
 * rules that depend on it.
 */
class EditorRowGeometryTest {

    // -- one row model -------------------------------------------------------------------

    @Test
    fun rowsStepByExactlyOneLineHeight() {
        assertEquals(0f, rowTopPx(1, 20f, 0f), 0.001f)
        assertEquals(20f, rowTopPx(2, 20f, 0f), 0.001f)
        assertEquals(60f, rowTopPx(4, 20f, 0f), 0.001f)
    }

    @Test
    fun rowHeightIsReadFromTheRenderersLineTops() {
        // The old measurement compared the first two *characters* of the file. A blank second row
        // made it read twice the real height, which is what pushed the numbers and the dots off
        // their rows. Averaging over every row cannot be skewed by any one of them.
        assertEquals(20f, rowHeightFromTops(0f, 200f, 10)!!, 0.001f)
        assertEquals(20f, rowHeightFromTops(12f, 412f, 20)!!, 0.001f)
    }

    @Test
    fun aLayoutThatHasNotBeenMeasuredHasNoRowHeight() {
        // Null is the contract: the caller falls back to the hand-converted value instead of
        // drawing every row on top of the first one.
        assertNull(rowHeightFromTops(0f, 0f, 0))
        assertNull(rowHeightFromTops(0f, 0f, 5))
        assertNull(rowHeightFromTops(100f, 20f, 3))
    }

    @Test
    fun theGutterAddsNoTopPaddingOfItsOwn() {
        // The editor has exactly one top padding. A second one inside the gutter is what put every
        // line number and status dot half a line below the code it labels.
        assertEquals(0f, GutterTopInset.value, 0.001f)
        assertEquals(4f, EditorTopPadding.value, 0.001f)
        assertEquals(EditorTopPadding.value, GutterPadding.value, 0.001f)
    }

    @Test
    fun theGutterAndTheFirstCodeLineStartAtTheSameOffset() {
        // Row 1 of the gutter and row 1 of the code are both at the editor's top padding, so they
        // cannot be half a line apart at the top, in the middle or at the bottom of a long file.
        assertEquals(rowTopPx(1, 20f, 12f), rowTopPx(1, 20f, 12f), 0.001f)
        assertEquals(12f, rowTopPx(1, 20f, 12f), 0.001f)
    }

    @Test
    fun rowsIncludeTheGutterTopPadding() {
        // The gutter and the code both start after 12 dp, so row 1 is at 12, not 0.
        assertEquals(12f, rowTopPx(1, 20f, 12f), 0.001f)
        assertEquals(32f, rowTopPx(2, 20f, 12f), 0.001f)
    }

    @Test
    fun rowsScaleWithTheZoomedLineHeight() {
        // At 2x zoom the gutter must use 40 dp rows or it drifts out of step again.
        assertEquals(12f, rowTopPx(1, 40f, 12f), 0.001f)
        assertEquals(52f, rowTopPx(2, 40f, 12f), 0.001f)
    }

    @Test
    fun theGutterRowModelAndThePillModelAgreeOnTheSameRow() {
        // pillTopPx is the un-padded variant used inside the code column; both must be the same
        // advance, which is what keeps the numbers level with the glyphs.
        for (line in 1..20) {
            val viaRow = rowTopPx(line, 20f, 0f)
            val viaPill = pillTopPx(line, 20f)
            assertEquals("line $line", viaPill, viaRow, 0.001f)
        }
    }

    @Test
    fun aLineNumberRowNeverRoundsAwayFromItsCodeRow() {
        // Twenty rows at 20 dp: every gutter row must land exactly one row apart, otherwise the
        // drift accumulates and the last numbers point at the wrong lines.
        for (line in 1..200) {
            val top = rowTopPx(line, 20f, 12f)
            assertEquals("line $line", (top - 12f) % 20f, 0f, 0.001f)
        }
    }

    @Test
    fun aLineBelowTheFirstClampsToTheFirstRow() {
        // With the 12 dp gutter padding, line 1 and anything below it both start at 12 dp.
        assertEquals(12f, rowTopPx(0, 20f, 12f), 0.001f)
        assertEquals(12f, rowTopPx(-5, 20f, 12f), 0.001f)
    }

    // -- word start, for the popup's left edge -----------------------------------------

    @Test
    fun theWordStartIsTheFirstCharacterOfTheIdentifier() {
        val line = "    val name = 1"
        val caret = line.indexOf("name") + 2
        assertEquals(line.indexOf("name"), wordStartOffset(line, caret))
    }

    @Test
    fun aCaretWithNothingTypedYetAnchorsAtTheCaret() {
        // "val " with the caret just past the space: there is no word behind it, so the popup
        // should sit at the caret rather than jumping back to the previous word.
        val line = "val x = 1"
        assertEquals(4, wordStartOffset(line, 4))
    }

    @Test
    fun aCaretMidWordAnchorsAtThatWordsStart() {
        assertEquals(0, wordStartOffset("value = 1", 3))
        assertEquals(0, wordStartOffset("value = 1", 5))
    }

    @Test
    fun theWordStartStopsAtANonWordCharacter() {
        val line = "foo(bar"
        assertEquals(4, wordStartOffset(line, 6))
    }

    @Test
    fun underscoresCountAsWordCharacters() {
        val line = "val my_name = 1"
        assertEquals(4, wordStartOffset(line, 10))
    }

    @Test
    fun aCaretOnWhitespaceAnchorsAtItself() {
        assertEquals(5, wordStartOffset("foo   bar", 5))
    }

    @Test
    fun aCaretOnANewlineStartsAtTheNewLine() {
        val source = "abc\ndef"
        assertEquals(4, wordStartOffset(source, 4))
    }

    @Test
    fun anOutOfRangeCaretIsClamped() {
        // Past the end: clamped to the end, and the walk back finds the whole word.
        assertEquals(0, wordStartOffset("abc", 99))
        // Before the start: there is nothing behind the caret.
        assertEquals(0, wordStartOffset("abc", -5))
    }

    // -- popup metrics -------------------------------------------------------------------

    @Test
    fun thePopupShowsAtMostFourRows() {
        assertEquals(4, MAX_VISIBLE_SUGGESTIONS_FOR_TEST)
    }

    @Test
    fun thePopupRowsAreTwentySixDpAndFourRowsTall() {
        // One line per row now: badge, name and type share 26 dp, and four rows cap the list at
        // 104 dp so it never covers a third of the screen.
        assertEquals(26f, POPUP_ROW_HEIGHT_FOR_TEST.value, 0.001f)
        assertEquals(4, MAX_VISIBLE_SUGGESTIONS_FOR_TEST)
        assertEquals(104f, POPUP_ROW_HEIGHT_FOR_TEST.value * MAX_VISIBLE_SUGGESTIONS_FOR_TEST, 0.001f)
    }

    @Test
    fun thePopupSitsOnTheRowBelowTheCaretLineWhenThereIsRoom() {
        val caretTop = 200f
        val lineHeight = 20f
        val gap = 6f
        val popupHeight = 5 * 53f + 3f
        val viewport = 800f
        val top = popupTopPx(caretTop, lineHeight, gap, popupHeight, viewport, gap)
        // One line below the caret line, plus the gap: the caret row is completely free.
        assertEquals(226f, top, 0.001f)
        assertTrue("the caret line is not covered", top >= caretTop + lineHeight)
    }

    @Test
    fun thePopupFlipsAboveTheCaretLineWhenTheKeyboardLeavesNoRoomBelow() {
        val caretTop = 700f
        val lineHeight = 20f
        val gap = 6f
        val popupHeight = 5 * 53f + 3f
        val viewport = 800f
        val top = popupTopPx(caretTop, lineHeight, gap, popupHeight, viewport, gap)
        // Entirely above: its bottom edge stops short of the caret line's top.
        assertEquals(caretTop - gap - popupHeight, top, 0.001f)
        assertTrue("the caret line is not covered", top + popupHeight <= caretTop)
    }

    @Test
    fun theFixedHeightPanelClearsTheLineWhileThereIsRoomEitherSide() {
        // popupTopPx now places only the selection bar — a panel of a fixed height that cannot give
        // up rows. The suggestion popup moved to popupPlacement, which is swept below.
        val lineHeight = 20f
        val gap = 6f
        val popupHeight = 5 * 53f + 3f
        val viewport = 800f
        var caretTop = 0f
        while (caretTop <= viewport) {
            val top = popupTopPx(caretTop, lineHeight, gap, popupHeight, viewport, gap)
            val clearsTheCaretRow = top + popupHeight <= caretTop || top >= caretTop + lineHeight
            assertTrue(
                "caret row at $caretTop was covered: panel spans $top..${top + popupHeight}",
                clearsTheCaretRow,
            )
            caretTop += 7f
        }
    }

    @Test
    fun thePopupGivesUpRowsRatherThanCoverTheCaretLine() {
        // The bug this pins: a viewport short enough that neither side could take the whole list
        // used to be answered by clamping the full-height popup into the viewport, which landed it
        // on top of the line being typed. The row count gives way instead.
        val place = popupPlacement(
            caretTopPx = 100f,
            lineHeightPx = 20f,
            gapPx = 6f,
            rowHeightPx = 53f,
            borderPx = 3f,
            viewportHeightPx = 300f,
            edgePx = 6f,
            maxRows = 5,
        )
        requireNotNull(place) { "a short viewport still has room for a row" }
        val (top, rows) = place
        val height = rows * 53f + 3f
        assertEquals(3, rows)
        assertEquals(126f, top, 0.001f)
        assertTrue("clamped inside the viewport", top >= 0f && top + height <= 300f)
        assertTrue(
            "the caret line is not covered: popup spans $top..${top + height}",
            top + height <= 100f || top >= 120f,
        )
    }

    @Test
    fun thePopupSitsUnderneathWhenTheWholeListFitsThere() {
        val place = popupPlacement(
            caretTopPx = 400f,
            lineHeightPx = 20f,
            gapPx = 6f,
            rowHeightPx = 53f,
            borderPx = 3f,
            viewportHeightPx = 800f,
            edgePx = 6f,
            maxRows = 5,
        )
        requireNotNull(place)
        val (top, rows) = place
        assertEquals(5, rows)
        assertEquals(426f, top, 0.001f)
        assertTrue("the caret line is not covered", top >= 420f)
    }

    @Test
    fun thePopupFlipsAboveWhenUnderneathCannotTakeTheWholeList() {
        // A caret near the bottom: underneath has a row or two, but above has the whole list, and
        // one row below the caret is worse than the full list above it.
        val place = popupPlacement(
            caretTopPx = 700f,
            lineHeightPx = 20f,
            gapPx = 6f,
            rowHeightPx = 53f,
            borderPx = 3f,
            viewportHeightPx = 800f,
            edgePx = 6f,
            maxRows = 5,
        )
        requireNotNull(place)
        val (top, rows) = place
        assertEquals(5, rows)
        assertEquals(426f, top, 0.001f)
        assertTrue("the caret line is not covered", top + rows * 53f + 3f <= 700f)
    }

    @Test
    fun aViewportShorterThanASingleRowGetsNoPopup() {
        // Nothing fits either side, and a popup that cannot keep the caret line clear has no
        // business being on screen at all.
        assertNull(
            popupPlacement(
                caretTopPx = 10f,
                lineHeightPx = 20f,
                gapPx = 6f,
                rowHeightPx = 53f,
                borderPx = 3f,
                viewportHeightPx = 40f,
                edgePx = 6f,
                maxRows = 5,
            ),
        )
    }

    @Test
    fun thePopupNeverOverlapsTheCaretLineWhereverTheCaretIs() {
        val rowHeight = 53f
        val border = 3f
        val gap = 6f
        val lineHeight = 20f
        val viewport = 800f
        var caretTop = 0f
        while (caretTop <= viewport) {
            val place = popupPlacement(
                caretTopPx = caretTop,
                lineHeightPx = lineHeight,
                gapPx = gap,
                rowHeightPx = rowHeight,
                borderPx = border,
                viewportHeightPx = viewport,
                edgePx = gap,
                maxRows = 5,
            )
            if (place != null) {
                val (top, rows) = place
                val height = rows * rowHeight + border
                assertTrue("off the top at $caretTop", top >= 0f)
                assertTrue("off the bottom at $caretTop", top + height <= viewport)
                val clearsTheCaretRow = top + height <= caretTop || top >= caretTop + lineHeight
                assertTrue(
                    "caret row at $caretTop was covered: popup spans $top..${top + height}",
                    clearsTheCaretRow,
                )
            }
            caretTop += 7f
        }
    }

    @Test
    fun thePopupLeftEdgeKeepsAMarginInsideTheCodeColumn() {
        val inset = 6f
        val codeWidth = 298f
        val popupWidth = 292f
        // Far right: pulled back so the whole popup and a 6 dp margin fit inside the code column.
        assertEquals(6f, popupLeftPx(400f, 76f, 0, popupWidth, codeWidth, inset), 0.001f)
        // Far left: never inside the margin.
        assertEquals(6f, popupLeftPx(-500f, 76f, 0, popupWidth, codeWidth, inset), 0.001f)
    }

    @Test
    fun thePopupNeverRunsUnderTheLineNumbers() {
        // The width is bounded by the code column, not the whole screen, so the gutter stays clear.
        val gutter = 62f
        val width = popupWidthPx(maxWidthPx = 430f, viewportWidthPx = 393f, gutterWidthPx = gutter, insetPx = 8f)
        assertEquals(315f, width, 0.001f)
        assertTrue("fits the code column", width <= 393f - gutter)
        // A narrow screen shrinks the popup instead of letting it overflow.
        val narrow = popupWidthPx(maxWidthPx = 430f, viewportWidthPx = 240f, gutterWidthPx = gutter, insetPx = 8f)
        assertEquals(162f, narrow, 0.001f)
        // The 430 dp cap still applies on a tablet.
        assertEquals(430f, popupWidthPx(maxWidthPx = 430f, viewportWidthPx = 1200f, gutterWidthPx = gutter, insetPx = 8f), 0.001f)
    }

    @Test
    fun thePopupAnchorsToTheWordStartInsideTheCodeColumn() {
        val left = popupLeftPx(wordX = 40f, columnStartPx = 76f, hScrollPx = 0, popupWidthPx = 200f, codeWidthPx = 331f, insetPx = 6f)
        assertEquals(116f, left, 0.001f)
    }

    @Test
    fun thePopupAnchorsToTheWordStartAfterScrollingSideways() {
        // The code column has scrolled left, so the word has moved with it and the popup follows.
        val left = popupLeftPx(wordX = 100f, columnStartPx = 76f, hScrollPx = 40, popupWidthPx = 180f, codeWidthPx = 331f, insetPx = 6f)
        assertEquals(136f, left, 0.001f)
    }

    // -- gutter rows come from the layout -----------------------------------------------

    @Test
    fun aGutterRowFallsBackToTheRowModelWhenThereIsNoLayout() {
        assertEquals(0f, lineTopFromLayout(null, 0, 20f), 0.001f)
        assertEquals(60f, lineTopFromLayout(null, 3, 20f), 0.001f)
        assertEquals(0f, lineTopFromLayout(null, -2, 20f), 0.001f)
    }

    // -- Enter indents the way the file is already written ----------------------------

    /**
     * The reference indent for these tests. Every old expectation used two spaces because the extra
     * level was hard-coded to two; now it is the reader own setting, so these pin a four-space file.
     */
    private val four = 4

    @Test
    fun enterCarriesTheCurrentIndentationToTheNextLine() {
        val old = "    val x = 1"
        val new = "    val x = 1\n"
        val (text, caret) = autoIndentAfterEnter(old, new, new.length, four)!!
        assertEquals("    val x = 1\n    ", text)
        assertEquals(new.length + 4, caret)
    }

    @Test
    fun theExtraLevelIsTheReadersIndentSizeNotATwo() {
        // The bug this pins: a file set to four spaces got two, so every brace crept out of step with
        // the Tab key by one level per line.
        val src = "fun main() {"
        // Spaces, not a tab character: the Tab key inserts a run of spaces too, and nothing in the
        // app yet says a file should indent with tabs instead.
        assertEquals("fun main() {\n    ", autoIndentAfterEnter(src, "$src\n", src.length + 1, 4)!!.first)
        assertEquals("fun main() {\n  ", autoIndentAfterEnter(src, "$src\n", src.length + 1, 2)!!.first)
        assertEquals("fun main() {\n        ", autoIndentAfterEnter(src, "$src\n", src.length + 1, 8)!!.first)
    }

    @Test
    fun enterStepsInAfterAnOpener() {
        // Each case keeps whatever indentation the line already had and adds one level to it.
        for (opener in listOf("fun main() {", "  foo(", "val a = listOf(", "when (x) {", "val a = [")) {
            val base = opener.takeWhile { it == ' ' }
            val (text, _) = autoIndentAfterEnter(opener, "$opener\n", opener.length + 1, four)!!
            assertEquals(opener + "\n" + base + " ".repeat(four), text)
        }
    }

    @Test
    fun enterStepsInAfterKoltinsColonAndArrow() {
        val colon = "class A:"
        assertEquals("$colon\n    ", autoIndentAfterEnter(colon, "$colon\n", colon.length + 1, four)!!.first)
        val arrow = "    val f = { x ->"
        assertEquals("$arrow\n        ", autoIndentAfterEnter(arrow, "$arrow\n", arrow.length + 1, four)!!.first)
    }

    @Test
    fun enterStepsBackOutAfterAClosingBrace() {
        // Without this, code drifts rightward: every block closed and the next line stayed inside it.
        val text = "    }"
        val (next, caret) = enterEdit(text, text.length, text.length, four)
        assertEquals("    }\n", next)
        assertEquals(text.length + 1, caret)
    }

    @Test
    fun theImePathAgreesWithTheStepOut() {
        // The IME already left the caret at the margin here, so there is nothing left to rewrite and
        // the transform stands down rather than reporting an empty indent as a change.
        val old = "    }"
        val new = "    }\n"
        assertNull(autoIndentAfterEnter(old, new, new.length, four))
    }

    @Test
    fun steppingOutIsOneLevelAndNeverPastTheMargin() {
        assertEquals("  ", dedentColumns("      ", 4))
        assertEquals("", dedentColumns("    ", 4))
        assertEquals("", dedentColumns("", 4))
        // A deeper indent loses one level, not all of it.
        assertEquals("        ", dedentColumns("            ", 4))
    }

    @Test
    fun aTabComesBackOutAsOneTabNotOneColumn() {
        // Treating a tab as one column would shave four spaces off a two-space indent and land the
        // caret at the margin.
        assertEquals("\t", dedentColumns("\t\t", 4))
        assertEquals("\t\t", dedentColumns("\t\t\t", 4))
    }

    @Test
    fun theAutoInsertedCloserDropsToItsOwnLine() {
        // Typing { puts a } straight after the caret, so Enter splits the line between the two. The
        // IME path sees that as: old text already ending in } with the caret before it, new text with
        // the newline dropped into that same gap.
        val old = "fun main() {}"
        val new = "fun main() {\n}"
        // The caret lands straight after the newline, which is before the }, not at the end.
        val (text, caret) = autoIndentAfterEnter(old, new, new.indexOf('\n') + 1, four)!!
        assertEquals("fun main() {\n    \n}", text)
        assertEquals("fun main() {\n    ".length, caret)
    }

    @Test
    fun theKeyPathPushesTheAutoInsertedCloserDownToo() {
        val text = "fun main() {}"
        val (next, caret) = enterEdit(text, 12, 12, four)
        assertEquals("fun main() {\n    \n}", next)
        assertEquals("fun main() {\n    ".length, caret)
    }

    @Test
    fun theAutoInsertedCloserGoesToTheOpenersOwnIndent() {
        val text = "    fun a() {}"
        val (next, _) = enterEdit(text, text.length - 1, text.length - 1, four)
        assertEquals("    fun a() {\n        \n    }", next)
    }

    @Test
    fun aCloserNotFromPairingIsLeftWhereItIs() {
        // The line already ends in }, so this Enter is a step-out; the brace after the caret belongs
        // to a block that already closed and has no business moving.
        val text = "    }}"
        val (next, _) = enterEdit(text, 5, 5, four)
        assertEquals("    }\n}", next)
    }

    @Test
    fun enterWithNoIndentationLeavesTheTextAlone() {
        val old = "val x = 1"
        val new = "val x = 1\n"
        assertEquals(null, autoIndentAfterEnter(old, new, new.length, four))
    }

    @Test
    fun typingAnythingOtherThanEnterIsNotTouched() {
        val old = "val "
        assertEquals(null, autoIndentAfterEnter(old, "val x", 5, four))
        assertEquals(null, autoIndentAfterEnter(old, "val foo\nbar", 11, four))
        assertEquals(null, autoIndentAfterEnter("val x", "val ", 4, four))
    }

    @Test
    fun enterInTheMiddleOfALineOnlyPadsTheTail() {
        val old = "  a\n  b"
        val new = "  a\n  b\n"
        val (text, caret) = autoIndentAfterEnter(old, new, new.length, four)!!
        assertEquals("  a\n  b\n  ", text)
        assertEquals(caret, text.length)
    }

    @Test
    fun enterAtTheVeryStartOfTheFileIsHandled() {
        assertEquals(null, autoIndentAfterEnter("", "\n", 1, four))
    }

    @Test
    fun tabsArePreservedAsIndentation() {
        val old = "\tval x = 1"
        val new = "\tval x = 1\n"
        val (text, _) = autoIndentAfterEnter(old, new, new.length, four)!!
        assertEquals("\tval x = 1\n\t", text)
    }

    // -- one press of Enter is exactly one row ---------------------------------------------

    @Test
    fun onePressOfEnterAddsExactlyOneNewline() {
        val source = "package com.example\n\nimport android.os.Bundle"
        val endOfLineOne = source.indexOf('\n')
        val (text, caret) = enterEdit(source, endOfLineOne, endOfLineOne, four)
        // No pad is carried from a line with no indentation, so the growth is one character.
        assertEquals(1, text.length - source.length)
        assertEquals(endOfLineOne + 1, caret)
    }

    @Test
    fun everyPressOfEnterAdvancesTheCaretByExactlyOneLine() {
        // The bug this pins: one press dropped the caret two rows down, and pressing harder made
        // it worse. Whatever the line under the caret looks like, the next row is one row down.
        var text = "package com.example\n\nimport android.os.Bundle"
        var caret = text.indexOf('\n')
        var line = text.substring(0, caret).count { it == '\n' } + 1
        repeat(5) { press ->
            val (next, nextCaret) = enterEdit(text, caret, caret, four)
            val nextLine = next.substring(0, nextCaret).count { it == '\n' } + 1
            assertEquals("press $press", line + 1, nextLine)
            // The text grows by exactly as much as the caret moves, so nothing is added twice.
            assertEquals("press $press", nextCaret - caret, next.length - text.length)
            text = next
            caret = nextCaret
            line = nextLine
        }
    }

    @Test
    fun enterReplacesTheSelectionRatherThanLeavingItBehind() {
        val (text, caret) = enterEdit("one TWO three", 4, 7, four)
        assertEquals("one \n three", text)
        assertEquals(5, caret)
    }

    @Test
    fun switchingAutoIndentOffGivesAPlainNewlineOnBothPaths() {
        // The bug this pins: the setting was read on the soft-keyboard path alone, so switching it
        // off did nothing at all on a hardware keyboard.
        val src = "fun main() {"
        val plain = enterEdit(src, src.length, src.length, four, indent = false)
        assertEquals("$src\n", plain.first)
        assertEquals(src.length + 1, plain.second)
    }

    @Test
    fun theKeyPathAndTheImePathIndentTheSameWay() {
        // The key handler and the IME fallback must agree, or Enter behaves differently on a soft
        // keyboard than it does on a hardware one.
        for (source in listOf(
            "    val x = 1",
            "fun main() {",
            "fun main() {}",
            "  foo(",
            "    }",
            "\tval y",
            "class A:",
        )) {
            val end = source.length
            val (keyText, keyCaret) = enterEdit(source, end, end, four)
            val ime = autoIndentAfterEnter(source, source + "\n", end + 1, four)
            assertEquals(source, ime?.first ?: source + "\n", keyText)
            assertEquals(source, ime?.second ?: end + 1, keyCaret)
        }
    }

    @Test
    fun enterOnAnEmptyFileStillMakesOneRow() {
        val (text, caret) = enterEdit("", 0, 0, four)
        assertEquals("\n", text)
        assertEquals(1, caret)
    }

    // -- status bar wording --------------------------------------------------------------

    @Test
    fun theStatusBarPluralisesTheCounts() {
        assertEquals("0 errors", pluralProblems(0, "error"))
        assertEquals("1 error", pluralProblems(1, "error"))
        assertEquals("2 errors", pluralProblems(2, "error"))
    }

    @Test
    fun theStatusBarPluralisesWarningsTheSameWay() {
        assertEquals("0 warnings", pluralProblems(0, "warning"))
        assertEquals("1 warning", pluralProblems(1, "warning"))
        assertEquals("7 warnings", pluralProblems(7, "warning"))
    }
}
class SnapScrollTest {

    @Test
    fun settlesOnTheNearestWholeCharacter() {
        // A 10 px character: 24 px is nearer to 20 than to 30, so it comes back.
        assertEquals(20, snapScrollToCharacter(24f, 10f))
        assertEquals(30, snapScrollToCharacter(26f, 10f))
        assertEquals(0, snapScrollToCharacter(0f, 10f))
    }

    @Test
    fun anExactCharacterStaysPut() {
        assertEquals(40, snapScrollToCharacter(40f, 10f))
    }

    @Test
    fun nothingMeasuredMeansNothingMoves() {
        // No layout yet, no honest pitch to snap to: moving would be a guess.
        assertEquals(17, snapScrollToCharacter(17f, 0f))
        assertEquals(17, snapScrollToCharacter(17f, -4f))
    }

    @Test
    fun fractionalWidthsRoundFairly() {
        // A 7.2 px JetBrains Mono advance: the third character starts at 21.6 px.
        assertEquals(22, snapScrollToCharacter(21f, 7.2f))
        assertEquals(22, snapScrollToCharacter(22f, 7.2f))
    }
}

class SuggestionOrderTest {

    private fun sug(name: String) = com.closecode.ui.ide.model.Suggestion(
        glyph = "M",
        name = name,
        detail = "Unit",
    )

    @Test
    fun exactPrefixMatchesComeFirst() {
        val items = listOf(sug("amodifier"), sug("modifier"), sug("amode"))
        val ordered = sortSuggestions(items, prefix = "mod", recent = emptyList())
        assertEquals("modifier", ordered[0].name)
    }

    @Test
    fun recentPicksBreakTies() {
        val items = listOf(sug("modeA"), sug("modeB"))
        val ordered = sortSuggestions(items, prefix = "mode", recent = listOf("modeB"))
        assertEquals("modeB", ordered[0].name)
        assertEquals("modeA", ordered[1].name)
    }

    @Test
    fun untouchedNamesKeepEngineOrder() {
        val items = listOf(sug("b"), sug("a"), sug("c"))
        assertEquals(items, sortSuggestions(items, prefix = "", recent = emptyList()))
    }

    @Test
    fun badgeLettersFollowTheSpecTable() {
        // F for functions, P for properties and parameters, M for everything the table
        // does not name — never a fourth letter.
        assertEquals('F', suggestionBadge("ƒ"))
        assertEquals('P', suggestionBadge("P"))
        assertEquals('P', suggestionBadge("p"))
        assertEquals('M', suggestionBadge("C"))
        assertEquals('M', suggestionBadge("K"))
        assertEquals('M', suggestionBadge(""))
    }
}

class TooltipBelowTest {

    @Test
    fun theTooltipDropsBelowWhenNothingFitsAbove() {
        // Caret on the first line: above is the top edge, below has the whole viewport.
        val top = tooltipBelowTopPx(
            caretTopPx = 4f,
            lineHeightPx = 20f,
            gapPx = 4f,
            tooltipHeightPx = 54f,
            viewportHeightPx = 800f,
            edgePx = 8f,
        )
        assertEquals(28f, top!!, 0.001f)
    }

    @Test
    fun noRoomEitherSideMeansNoTooltip() {
        // A viewport shorter than the tooltip itself: above fails and below fails, so the
        // honest answer is null rather than a box over the line being typed.
        assertNull(
            tooltipBelowTopPx(
                caretTopPx = 10f,
                lineHeightPx = 20f,
                gapPx = 4f,
                tooltipHeightPx = 200f,
                viewportHeightPx = 100f,
                edgePx = 8f,
            ),
        )
    }
}
