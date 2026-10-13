package com.closecode.ui.ide
import com.closecode.lang.DiagnosticCodes

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.closecode.lang.AndroidApiIndex
import com.closecode.lang.KotlinAnalyzer
import com.closecode.lang.Severity
import com.closecode.lang.SourceEdits
import com.closecode.lang.Diagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillPlacementTest {

    private val lineHeight = 22f

    @Test
    fun theFirstLineSitsAtTheTop() {
        assertEquals(0f, pillTopPx(1, lineHeight), 0.01f)
    }

    @Test
    fun eachLineAdvancesByExactlyOneLineHeight() {
        // The bug this pins: stacking rows made every problem consume two code lines, so labels
        // drifted further away from their code the more problems a file had.
        assertEquals(22f, pillTopPx(2, lineHeight), 0.01f)
        assertEquals(44f, pillTopPx(3, lineHeight), 0.01f)
        assertEquals(198f, pillTopPx(10, lineHeight), 0.01f)
        assertEquals(308f, pillTopPx(15, lineHeight), 0.01f)
    }

    @Test
    fun spacingStaysProportionalForAnyLineHeight() {
        assertEquals(0f, pillTopPx(1, 30f), 0.01f)
        assertEquals(60f, pillTopPx(3, 30f), 0.01f)
    }

    @Test
    fun invalidLineNumbersDoNotProduceNegativeOffsets() {
        assertEquals(0f, pillTopPx(0, lineHeight), 0.01f)
        assertEquals(0f, pillTopPx(-5, lineHeight), 0.01f)
    }

    @Test
    fun aLabelAnchorsAfterTheLinesTextNeverOnItsNewline() {
        // The bug this pins: the newline draws at the start of the *next* row, so anchoring on it
        // gave x of zero and the label landed on top of the code it was describing.
        val source = "one\ntwo\nthree\n\nfive"
        for (line in intArrayOf(1, 2, 3, 5)) {
            val anchor = endOfLineAnchorOffset(source, line)
            assertTrue("line $line anchored on a newline", source[anchor] != '\n')
        }
        assertEquals(2, endOfLineAnchorOffset(source, 1))
        assertEquals(6, endOfLineAnchorOffset(source, 2))
        assertEquals(12, endOfLineAnchorOffset(source, 3))
        assertEquals(18, endOfLineAnchorOffset(source, 5))
    }

    @Test
    fun aBlankLineAnchorsAtItsOwnStart() {
        // There is no last character to sit after, so the label starts where its text would.
        assertEquals(2, endOfLineAnchorOffset("a\n\nb", 2))
        assertEquals(20, endOfLineAnchorOffset("val x = 1\nval y = 2\n", 3))
    }

    @Test
    fun aPillStartsJustPastTheEndOfItsLine() {
        assertEquals(100f, pillAnchorX(90f, 10f), 0.01f)
    }

    @Test
    fun aLongLineKeepsItsPillPastTheEndInsteadOfPullingItBack() {
        // Regression: the anchor used to be clamped to "the last x where the pill still fits on
        // screen", which meant a long line — which is exactly the line that has an error — had its
        // pill dragged backwards until it landed on top of the characters it was describing. Off
        // screen to the right is the honest answer: the layer is a child of the code column's
        // horizontal scroller, so the pill travels sideways with its own line.
        val x = pillAnchorX(400f, 0f)
        assertEquals(400f, x, 0.01f)
    }

    @Test
    fun aPillFarOffScreenIsLeftWhereItsLinePutsIt() {
        assertEquals(5000f, pillAnchorX(5000f, 0f), 0.01f)
    }

    @Test
    fun thePillDoesNotMoveWhenTheCodeIsScrolledSideways() {
        // The scroll used to be an input, both to clamp the pill and to size its message. Neither
        // survives: position comes from the line's own right edge, and the message is capped at a
        // fixed width. So the same line reports the same x however far the column is scrolled,
        // which is what stops a pill from re-landing on the text every time the reader moves.
        assertEquals(408f, pillAnchorX(400f, 8f), 0.01f)
        assertEquals(pillAnchorX(400f, 8f), pillAnchorX(400f, 8f), 0.01f)
    }

    @Test
    fun thePillMessageCapIsAFixedWidthNotAGapToTheScreenEdge() {
        // Sizing the message from "room left before the right edge" made the text grow and shrink
        // as the column was scrolled, so the same diagnostic read as "U..." on one screen and in
        // full on the next. A fixed cap breaks a long message at the same word every time.
        assertEquals(220.dp, PillMaxMessageWidth)
    }

    @Test
    fun aPillNeverStartsLeftOfTheGapItNeeds() {
        assertEquals(8f, pillAnchorX(0f, 8f), 0.01f)
        assertEquals(0f, pillAnchorX(-40f, 0f), 0.01f)
    }

    @Test
    fun aNegativeGapIsTreatedAsNoGapAtAll() {
        assertEquals(90f, pillAnchorX(90f, -12f), 0.01f)
    }

    @Test
    fun thePillOffsetCarriesTheXAndNotJustTheTop() {
        // Regression: the layer computed x from the line's real right edge and then handed the
        // offset an IntOffset(0, top), painting every pill over the first characters of its own
        // line. The offset is the only place that number reaches the screen.
        assertEquals(IntOffset(100, 19), pillOffsetOf(100f, 19f))
        assertEquals(IntOffset(0, -4), pillOffsetOf(0.4f, -4f))
        assertEquals(IntOffset(641, 0), pillOffsetOf(640.6f, 0.1f))
    }
}

class LineNumberRowCountTest {

    @Test
    fun trailingBlankLinesAreNotNumbered() {
        // Opening a file must not invent numbers past its content.
        assertEquals(2, gutterRowCount(listOf("a", "b", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "", "")))
    }

    @Test
    fun contentWithNoTrailingBlankIsUnchanged() {
        assertEquals(3, gutterRowCount(listOf("a", "b", "c")))
    }

    @Test
    fun blankRowsBelowTheCaretAreNotNumbered() {
        // Numbering runs up to the caret, never past it. Walking down through blank rows used to
        // produce numbered lines with nothing on them; the rows below where the user is must
        // stay unnumbered whether or not a caret line is passed in.
        assertEquals(1, gutterRowCount(listOf("a", "", "", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "", "", "")))
        assertEquals(1, gutterRowCount(listOf("a", "", "", ""), caretLine = 1))
        assertEquals(2, gutterRowCount(listOf("a", "", "", "", ""), caretLine = 2))
        assertEquals(3, gutterRowCount(listOf("a", "", "", "", ""), caretLine = 3))
    }

    @Test
    fun theCaretLineIsNumberedBeforeAnythingIsTypedOnIt() {
        // Pressing Enter lands the caret on a blank row. Until that row gets its number the
        // reader cannot tell it exists — which is exactly "the line number only shows up once
        // I type on the line".
        assertEquals(2, gutterRowCount(listOf("a", ""), caretLine = 2))
        assertEquals(3, gutterRowCount(listOf("a", "", ""), caretLine = 3))
        assertEquals(2, gutterRowCount(listOf("a", "b", ""), caretLine = 2))
        // A caret on a row that already has code changes nothing.
        assertEquals(3, gutterRowCount(listOf("a", "b", "c"), caretLine = 3))
    }

    @Test
    fun aCaretPastTheBufferIsClampedToIt() {
        assertEquals(3, gutterRowCount(listOf("a", "b", "c"), caretLine = 99))
        assertEquals(1, gutterRowCount(emptyList(), caretLine = 5))
        assertEquals(1, gutterRowCount(listOf(""), caretLine = 99))
    }

    @Test
    fun anEmptyFileStillHasOneRow() {
        assertEquals(1, gutterRowCount(emptyList()))
        assertEquals(1, gutterRowCount(listOf("")))
    }

    @Test
    fun linesOfInvisibleCharactersCountAsEmpty() {
        // Zero-width space, zero-width non-joiner/joiner, BOM and word joiner all draw nothing, so a
        // line made only of them must not be numbered or treated as the last line of code.
        val invisible = listOf('\u200B', '\u200C', '\u200D', '\uFEFF', '\u2060')
        for (ch in invisible) {
            val line = ch.toString().repeat(24)
            assertFalse("$ch draws nothing", hasVisibleContent(line))
            assertEquals("a line of $ch must not be numbered", 1, gutterRowCount(listOf("a", line)))
        }
    }

    @Test
    fun aNoBreakSpaceIsStillTreatedAsBlank() {
        // U+00A0 renders as a space, so it is blank like any other space.
        assertFalse(hasVisibleContent("\u00A0".repeat(24)))
        assertEquals(1, gutterRowCount(listOf("a", "\u00A0".repeat(24))))
    }

    @Test
    fun aLineWithOneRealCharacterAmongInvisibleOnesIsContent() {
        assertTrue(hasVisibleContent("".repeat(10) + "x"))
        assertEquals(2, gutterRowCount(listOf("a", "".repeat(10) + "x")))
    }

    @Test
    fun numberingStartsAtOneAndEndsOnTheLastLineWithCode() {
        // The scaffolded files all end with a newline, so the raw text has a 17th, empty, line.
        val source = "package a\n}\n"
        assertEquals(11, caretOffsetAtEndOfCode(source))
        assertEquals(2, gutterRowCount(source.lines()))
    }
}

class OpeningCaretTest {

    @Test
    fun theCaretOpensAtTheEndOfTheLastLineThatHasCode() {
        assertEquals(1, caretOffsetAtEndOfCode("}"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n"))
    }

    @Test
    fun trailingBlankLinesAreSkipped() {
        // Two scaffolded files' worth of trailing newlines: the caret belongs after "b".
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n\n"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n\n\n\n"))
    }

    @Test
    fun aBlankLineBetweenCodeLinesIsNotTreatedAsTheEnd() {
        // "a", blank, "bb": the blank line in the middle is not the end of the file.
        assertEquals(5, caretOffsetAtEndOfCode("a\n\nbb\n"))
    }

    @Test
    fun trailingSpacesOnTheLastCodeLineAreKeptAsColumnSpace() {
        assertEquals(4, caretOffsetAtEndOfCode("}   \n"))
    }

    @Test
    fun aFileWithNoCodeAtAllOpensAtTheStart() {
        assertEquals(0, caretOffsetAtEndOfCode(""))
        assertEquals(0, caretOffsetAtEndOfCode("\n"))
        assertEquals(0, caretOffsetAtEndOfCode("\n\n\n"))
        assertEquals(0, caretOffsetAtEndOfCode("   \n"))
    }

    @Test
    fun aFirstLineWithCodeOpensAtItsEnd() {
        assertEquals(3, caretOffsetAtEndOfCode("abc\n"))
    }

    @Test
    fun theTextItselfIsNeverRewritten() {
        // The trailing newline stays in the buffer so saving does not churn the file on disk.
        val source = "a\nb\n"
        caretOffsetAtEndOfCode(source)
        assertEquals("a\nb\n", source)
    }

    @Test
    fun theCaretSkipsLinesOfInvisibleCharacters() {
        // The reported symptom: lines 11 to 19 were numbered with nothing drawn on them, and the
        // caret opened on line 19 instead of the last line with code. Those lines held characters
        // that draw nothing, so they are skipped like blank lines.
        val source = "val x = 1\n" + "".repeat(3) + "\n"
        assertEquals("val x = 1".length, caretOffsetAtEndOfCode(source))
        assertEquals(1, gutterRowCount(source.lines()))
    }

    @Test
    fun theCaretStopsAtTheLastLineThatHasCode() {
        val source = "a\nb\n" + "".repeat(8) + "\n"
        val caret = caretOffsetAtEndOfCode(source)
        assertEquals("just after the b", "a\nb".length, caret)
    }
}

class LineColumnOffsetTest {

    private val src = "package com.example\n\nfun demo(spinner: Spinner) {}\n"

    @Test
    fun mapsLineAndColumnToACharacterOffset() {
        assertEquals(0, offsetOfLineColumn(src, 1, 1))
        assertEquals(8, offsetOfLineColumn(src, 1, 9))
        assertEquals(20, offsetOfLineColumn(src, 2, 1))
        assertEquals(21, offsetOfLineColumn(src, 3, 1))
    }

    @Test
    fun pointsAtTheNameInsideAParameterList() {
        val offset = offsetOfLineColumn(src, 3, 19)
        assertEquals("Spinner", src.substring(offset, offset + 7))
    }

    @Test
    fun clampsRatherThanThrowing() {
        assertEquals(0, offsetOfLineColumn(src, 0, 1))
        assertEquals(0, offsetOfLineColumn(src, 1, 0))
        assertTrue(offsetOfLineColumn(src, 999, 1) <= src.length)
        assertTrue(offsetOfLineColumn(src, 1, 999) <= src.length)
        assertEquals(src.length, offsetOfLineColumn(src, 999, 999).coerceAtMost(src.length))
    }

    @Test
    fun everyLineColumnPairStaysInsideTheSource() {
        var line = 1
        var column = 1
        while (line <= 5) {
            val offset = offsetOfLineColumn(src, line, column)
            assertTrue("offset $offset out of range", offset in 0..src.length)
            column += 7
            if (column > 60) {
                column = 1
                line++
            }
        }
    }
}

class ProblemsByLineTest {

    @Test
    fun groupsProblemsAndTracksSeverity() {
        val src = """
            package com.example

            fun demo() {
                val x = notDefinedAnywhere
                use(x)
            }
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src)
        val problems = ProblemsByLine.from(analysis.diagnostics)

        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertEquals(listOf(unresolved), problems.on(unresolved.line))
        assertTrue(unresolved.line in problems.linesWithErrors)
        assertEquals(unresolved, problems.worstOn(unresolved.line))
    }

    @Test
    fun emptyDiagnosticsProduceNoProblems() {
        val clean = ProblemsByLine.from(emptyList())
        assertTrue(clean.byLine.isEmpty())
        assertTrue(clean.all.isEmpty())
        assertEquals(null, clean.worstOn(1))
    }

    @Test
    fun errorsWinOverWarningsOnTheSameLine() {
        val src = "package com.example\n\nfun demo() { val x = nope }\n"
        val analysis = KotlinAnalyzer.analyze(src)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val worst = problems.worstOn(problems.all.first().line)
        assertEquals(Severity.ERROR, worst?.severity)
    }
}

class QuickFixActionTest {

    private val index = AndroidApiIndex.parse(
        """
        C|android.widget.Spinner|Spinner|android.widget.AbsSpinner
        C|android.widget.MotionLayout|MotionLayout|android.view.ViewGroup
        """.trimIndent(),
    )

    /** `Spinner` sits in a parameter-type position, so it is not a call and gets checked. */
    private val missingImportSource = """
        package com.example

        fun demo(spinner: Spinner) {}
    """.trimIndent()

    @Test
    fun offersImportForAnUnresolvedClassTheIndexKnows() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)

        val import = actions.single { it.kind == QuickFixKind.ADD_IMPORT }
        assertEquals("android.widget.Spinner", import.target)
        assertTrue(import.label.startsWith("Import"))
    }

    @Test
    fun offersTheLineActionsAlongsideTheFixes() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)

        // Spec 6.6 puts the token removal first so tapping the bulb deletes the bad token; the import
        // suggestion follows it, then the plain code actions.
        assertEquals(
            listOf(
                QuickFixKind.REMOVE_TOKEN,
                QuickFixKind.ADD_IMPORT,
                QuickFixKind.TOGGLE_COMMENT,
                QuickFixKind.MOVE_LINE_UP,
                QuickFixKind.MOVE_LINE_DOWN,
            ),
            actions.map { it.kind },
        )
    }

    @Test
    fun aProblemWithNoImportOfferStillGetsTheLineActions() {
        val src = """
            package com.example

            fun demo(thing: ZzUnknownThing) {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, src)

        // Nothing to import, but the token can still be deleted and the line commented or moved.
        assertTrue(actions.none { it.kind == QuickFixKind.ADD_IMPORT })
        assertEquals(
            listOf(
                QuickFixKind.REMOVE_TOKEN,
                QuickFixKind.TOGGLE_COMMENT,
                QuickFixKind.MOVE_LINE_UP,
                QuickFixKind.MOVE_LINE_DOWN,
            ),
            actions.map { it.kind },
        )
    }

    @Test
    fun lowerCaseNamesNeverOfferAnImport() {
        val src = """
            package com.example

            fun demo(thing: someUnknownValue) {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertTrue(
            buildQuickFixActions(unresolved.line, problems, analysis, src)
                .none { it.kind == QuickFixKind.ADD_IMPORT },
        )
    }

    @Test
    fun theCommentActionIsOfferedOnTheProblemLine() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)
        assertEquals("Comment Line", actions.single { it.kind == QuickFixKind.TOGGLE_COMMENT }.label)
    }

    @Test
    fun commentingOutTheProblemRemovesItAndTheActions() {
        val commented = SourceEdits.toggleLineComment(missingImportSource, 3)
        val analysis = KotlinAnalyzer.analyze(commented, index)
        assertTrue(analysis.diagnostics.none { it.code == DiagnosticCodes.E0101_UNRESOLVED })
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(3, ProblemsByLine.from(analysis.diagnostics), analysis, commented),
        )
    }

    @Test
    fun aCleanLineHasNoActionsAtAll() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(2, ProblemsByLine.from(analysis.diagnostics), analysis, missingImportSource),
        )
    }

    @Test
    fun theAddImportActionActuallyFixesTheFile() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val action = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)
            .single { it.kind == QuickFixKind.ADD_IMPORT }

        val fixed = SourceEdits.addImport(missingImportSource, action.target)
        val after = KotlinAnalyzer.analyze(fixed, index)
        assertEquals(
            emptyList<String>(),
            after.errors.map { "${it.line}: ${it.message}" },
        )
    }

    @Test
    fun kotlinBuiltinsAreNeverFlaggedAsMissingImports() {
        val src = """
            package com.example

            fun demo(text: String, count: Int) {
                val items: List<String> = listOf(text)
            }
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        assertEquals(
            emptyList<String>(),
            analysis.errors.map { "${it.line}: ${it.message}" },
        )
    }


    @Test
    fun offersRemovalForAnUnusedImport() {
        val src = """
            package com.example

            import android.widget.Spinner

            fun demo() {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val warning = analysis.diagnostics.first { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }
        val actions = buildQuickFixActions(warning.line, problems, analysis, src)

        val removal = actions.single { it.kind == QuickFixKind.REMOVE_IMPORT }
        assertEquals("android.widget.Spinner", removal.target)
    }

    @Test
    fun noActionsForALineWithNoProblems() {
        val analysis = KotlinAnalyzer.analyze("package com.example\n")
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(1, ProblemsByLine.from(analysis.diagnostics), analysis, "x"),
        )
        assertEquals(emptyList<QuickFixAction>(), buildQuickFixActions(null, ProblemsByLine(), null, ""))
    }

    @Test
    fun theRemoveImportActionActuallyFixesTheWarning() {
        val src = """
            package com.example

            import android.widget.Spinner

            fun demo() {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val warning = analysis.diagnostics.first { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }
        val action = buildQuickFixActions(warning.line, problems, analysis, src)
            .single { it.kind == QuickFixKind.REMOVE_IMPORT }

        val fixed = SourceEdits.removeImport(src, action.target)
        val after = KotlinAnalyzer.analyze(fixed, index)
        assertEquals(
            emptyList<String>(),
            after.diagnostics.filter { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }.map { it.message },
        )
    }
}


class CaretSideScrollTest {

    private val margin = 64f
    private val viewport = 400f

    @Test
    fun aCaretInsideTheBandDoesNotMoveTheView() {
        // Typing in the middle of a line that is already on screen costs nothing.
        assertEquals(
            100f,
            targetHScroll(caretXPx = 380f, currentPx = 100f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
        assertEquals(
            100f,
            targetHScroll(caretXPx = 165f, currentPx = 100f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
    }

    @Test
    fun aCaretThatHasWalkedIntoTheLeftMarginPullsTheViewBack() {
        assertEquals(
            66f,
            targetHScroll(caretXPx = 130f, currentPx = 100f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
    }

    @Test
    fun aCaretThatHasWalkedIntoTheRightMarginPullsTheViewBack() {
        assertEquals(
            114f,
            targetHScroll(caretXPx = 450f, currentPx = 100f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
    }

    @Test
    fun thereIsNothingLeftOfTheFirstCharacter() {
        assertEquals(
            0f,
            targetHScroll(caretXPx = 30f, currentPx = 0f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
        assertEquals(
            0f,
            targetHScroll(caretXPx = -10f, currentPx = 0f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
    }

    @Test
    fun aCaretPastTheContentIsLeftForTheScrollerToClamp() {
        // The content's real width belongs to the renderer, so this answers honestly and lets the
        // scroller coerce to its own maximum rather than guessing a number only it knows.
        assertEquals(
            4_664f,
            targetHScroll(caretXPx = 5_000f, currentPx = 0f, viewportWidthPx = viewport, marginPx = margin),
            0.01f,
        )
    }
}
class GutterWindowTest {

    private val lineHeight = 22f
    private val viewport = 880f

    @Test
    fun anEditorThatHasNotBeenMeasuredYetShowsEveryRow() {
        // The safe answer for the first frame only: guessing a window before there is a viewport
        // would leave the gutter blank until the next scroll.
        assertEquals(0 until 2000, gutterWindow(2000, scrollTopPx = 0f, viewportHeightPx = 0f, lineHeightPx = lineHeight))
    }

    @Test
    fun aTwoThousandLineFileComposesAFewDozenRowsNotTwoThousand() {
        // The whole point: the gutter used to build one composable per line in the file.
        // Ten rows of slack sit above and below the forty-odd visible ones.
        val window = gutterWindow(2000, scrollTopPx = 0f, viewportHeightPx = viewport, lineHeightPx = lineHeight)
        assertTrue("expected a bounded window, got ${window.count()} rows", window.count() <= 70)
    }

    @Test
    fun theWindowDoesNotGrowWithTheFile() {
        val small = gutterWindow(200, 0f, viewport, lineHeight)
        val huge = gutterWindow(200_000, 0f, viewport, lineHeight)
        assertEquals(small.count(), huge.count())
    }

    @Test
    fun scrollingDownMovesTheWindowWithIt() {
        val window = gutterWindow(2000, scrollTopPx = 2_200f, viewportHeightPx = viewport, lineHeightPx = lineHeight)
        assertEquals(90, window.first)
        assertEquals(151, window.last)
    }

    @Test
    fun theWindowStopsAtTheLastLineInsteadOfRunningPastIt() {
        assertEquals(0 until 10, gutterWindow(10, 0f, viewport, lineHeight))
        assertEquals(1_999..1_999, gutterWindow(2000, 1_000_000f, viewport, lineHeight))
    }

    @Test
    fun anEmptyFileHasNothingToShow() {
        assertEquals(IntRange.EMPTY, gutterWindow(0, 0f, viewport, lineHeight))
    }

    @Test
    fun rowsAreAlwaysKeptAboveAndBelowTheViewport() {
        // A fling lands on the next frame's window, and if that window began exactly at the first
        // visible row there would be one frame of gutter with a hole in it.
        val window = gutterWindow(2000, scrollTopPx = 2_200f, viewportHeightPx = viewport, lineHeightPx = lineHeight)
        assertEquals(90, window.first)
        assertTrue("no slack above the first visible row", window.first < 100)
        assertTrue("no slack below the last visible row", window.last > 140)
    }

    @Test
    fun everyLineThatIsOnScreenIsAlwaysInsideTheWindow() {
        val lineCount = 2000
        var scrollTop = 0f
        val end = lineCount * lineHeight
        while (scrollTop < end) {
            val firstOnScreen = (scrollTop / lineHeight).toInt()
            val lastOnScreen =
                ((scrollTop + viewport) / lineHeight).toInt().coerceAtMost(lineCount - 1)
            val window = gutterWindow(lineCount, scrollTop, viewport, lineHeight)
            assertTrue("window $window misses line $firstOnScreen at $scrollTop", firstOnScreen in window)
            assertTrue("window $window misses line $lastOnScreen at $scrollTop", lastOnScreen in window)
            scrollTop += 37f
        }
    }
}

/**
 * What the Run button says when it will not let the reader through, and what the sheet it opens
 * shows. The two disagreed: the blocker counted the whole project while the sheet listed one file,
 * so "Fix 1 error" and "No problems. Code is clean." were both true and contradicted each other.
 */
class RunBlockerTest {

    private fun errorIn(file: String, line: Int = 3) = ProjectDiagnostic(
        fileName = file,
        diagnostic = Diagnostic(
            severity = com.closecode.lang.Severity.ERROR,
            message = "Unresolved 'mod'",
            offset = 0,
            length = 1,
            line = line,
            column = 1,
            code = "E0001",
        ),
    )

    @Test
    fun oneErrorNamesItsFile() {
        val msg = blockedByErrors(listOf(errorIn("Theme.kt")), "running")
        assertTrue(msg, msg.contains("Theme.kt"))
        assertTrue(msg, msg.contains("1 error"))
        assertTrue("it must not say \"1 errors\"", !msg.contains("1 errors"))
    }

    @Test
    fun theVerbIsWhateverWasAttempted() {
        assertTrue(blockedByErrors(listOf(errorIn("A.kt")), "building").contains("before building"))
        assertTrue(blockedByErrors(listOf(errorIn("A.kt")), "running").contains("before running"))
    }

    @Test
    fun severalFilesAreCountedRatherThanListed() {
        // A toast has one line. Naming every path is unreadable, and the sheet is one tap away.
        val msg = blockedByErrors(
            listOf(errorIn("A.kt"), errorIn("B.kt"), errorIn("B.kt", line = 9)),
            "running",
        )
        assertTrue(msg, msg.contains("3 errors"))
        assertTrue(msg, msg.contains("2 files"))
        assertTrue("it must not list every path", !msg.contains("B.kt"))
    }

    @Test
    fun oneFileWithSeveralErrorsStillCountsTheFileOnce() {
        val msg = blockedByErrors(
            listOf(errorIn("A.kt"), errorIn("A.kt", line = 8), errorIn("A.kt", line = 12)),
            "running",
        )
        assertTrue(msg, msg.contains("3 errors"))
        assertTrue(msg, msg.contains("in A.kt"))
        assertTrue("not \"across 3 files\"", !msg.contains("across"))
    }

    @Test
    fun noErrorsStillProducesSomethingReadable() {
        // Defensive only: the caller checks the count first. A crash here would be worse than a
        // clumsy sentence.
        assertEquals("Fix 0 errors before running", blockedByErrors(emptyList(), "running"))
    }

    @Test
    fun theTwoScopesSayDifferentHeadings() {
        assertEquals("Problems", ProblemScope.FILE.heading())
        assertEquals("Problems in project", ProblemScope.PROJECT.heading())
    }
}

/** Where the scrollbar's error marks land, and why a line already on screen has none. */
class ErrorTickTest {

    @Test
    fun anErrorBelowTheWindowIsMarkedNearTheBottom() {
        // A hundred lines visible, a two-thousand-line file, an error on line 1500: it has to land
        // in the lower half or the mark is telling the reader the wrong direction.
        val ticks = errorTickFractions(
            errorLines = setOf(1500),
            lineCount = 2000,
            visibleTop = 1,
            visibleBottom = 100,
        )
        assertEquals(1, ticks.size)
        assertTrue(ticks[0] > 50f)
        assertTrue(ticks[0] <= 100f)
    }

    @Test
    fun anErrorAboveTheWindowIsMarkedNearTheTop() {
        val ticks = errorTickFractions(
            errorLines = setOf(40),
            lineCount = 2000,
            visibleTop = 900,
            visibleBottom = 1000,
        )
        assertTrue(ticks[0] < 50f)
    }

    @Test
    fun aLineAlreadyOnScreenGetsNoMark() {
        // It has a squiggle under it. A mark as well is two signals for one fact, and the scrollbar
        // one would sit on the row the reader is already looking at.
        assertEquals(
            emptyList<Float>(),
            errorTickFractions(setOf(50), lineCount = 2000, visibleTop = 1, visibleBottom = 100),
        )
    }

    @Test
    fun marksNeverFallOutsideTheBar() {
        val ticks = errorTickFractions(
            errorLines = setOf(1, 2000),
            lineCount = 2000,
            visibleTop = 1000,
            visibleBottom = 1100,
        )
        ticks.forEach { assertTrue("tick at $it", it >= 0f) }
    }

    @Test
    fun manyErrorsAreCappedRatherThanDrawingAWall() {
        // A file that fails to compile has hundreds of them; four hundred red marks is a bar, not a
        // map, and it hides the thumb underneath it.
        val many = (1..2000).toSet()
        val ticks = errorTickFractions(many, lineCount = 2000, visibleTop = 1, visibleBottom = 100)
        assertTrue(ticks.size <= 40)
    }

    @Test
    fun nothingToSayIsNothingToDraw() {
        assertEquals(
            emptyList<Float>(),
            errorTickFractions(emptySet(), lineCount = 100, visibleTop = 1, visibleBottom = 50),
        )
        // Nothing laid out, or a viewport of no height: no geometry, so no marks.
        assertEquals(
            emptyList<Float>(),
            errorTickFractions(setOf(5), lineCount = 0, visibleTop = 1, visibleBottom = 50),
        )
        assertEquals(
            emptyList<Float>(),
            errorTickFractions(setOf(5), lineCount = 100, visibleTop = 20, visibleBottom = 20),
        )
    }
}

/** The declaration the sticky header pins, and when it stays hidden. */
class ScopeHeaderTest {

    // Line numbers: 1 package, 2 blank, 3 class, 4 fun onCreate, 5 body, 6 closing fun, 7 blank,
    // 8 fun onResume, 9 body, 10 closing fun, 11 closing class.
    private val code = """
        package com.example

        class MainActivity {
            fun onCreate(state: Bundle?) {
                val a = 1
            }

            fun onResume() {
                val b = 2
            }
        }
    """.trimIndent()

    @Test
    fun theInnermostDeclarationIsFoundNotTheOutermostOne() {
        // A reader two blocks deep wants the function they are in, not the class they are inside.
        val found = enclosingDeclarationAt(code, 5)
        assertNotNull(found)
        assertEquals(4, found!!.first)
        assertTrue(found.second, found.second.contains("onCreate"))
    }

    @Test
    fun aSecondDeclarationIsFoundAfterTheFirstHasClosed() {
        val found = enclosingDeclarationAt(code, 9)
        assertNotNull(found)
        assertEquals(8, found!!.first)
        assertTrue(found.second, found.second.contains("onResume"))
    }

    @Test
    fun aBraceInAStringIsNotABlock() {
        val src = "fun a() {\n    val s = \"{\"\n    val b = 1\n}"
        val found = enclosingDeclarationAt(src, 3)
        assertNotNull(found)
        assertTrue(found!!.second, found.second.contains("fun a()"))
    }

    @Test
    fun aDeclarationLineItselfBelongsToItsEnclosingBlockNotItsOwn() {
        // Line 4 opens a brace, so the block it opens is not yet the one line 4 is *in*. The header
        // for line 4 is the class, which is what the reader is actually looking at.
        val found = enclosingDeclarationAt(code, 4)
        assertNotNull(found)
        assertEquals(3, found!!.first)
    }

    @Test
    fun theTopLevelShowsNoHeader() {
        // Outside every block there is no declaration to be inside of, and a header with nothing to
        // say is just a row of chrome.
        assertNull(enclosingDeclarationAt("val a = 1\nval b = 2", 2))
        assertNull(enclosingDeclarationAt("", 1))
    }

    @Test
    fun aClosedBlockDoesNotPinAnything() {
        val src = "fun a() {\n}\nval after = 1"
        assertNull(enclosingDeclarationAt(src, 3))
    }

    @Test
    fun aBraceOnlyLineOpensABlockRatherThanADeclaration() {
        // An if body has braces and names nothing, so it must not become the header.
        val src = "fun a() {\n    if (b) {\n        c()\n    }\n}"
        val found = enclosingDeclarationAt(src, 3)
        assertNotNull(found)
        assertTrue(found!!.second, found.second.contains("fun a()"))
    }

    @Test
    fun theHeaderNamesTheDeclarationRatherThanTheLine() {
        assertEquals(
            "fun onCreate(state: Bundle?)",
            declarationOf("    fun onCreate(state: Bundle?) {"),
        )
        assertEquals(
            "class MainActivity : Component()",
            declarationOf("class MainActivity : Component() {"),
        )
        // An unrecognised line is shown whole. Slightly wrong beats nothing.
        assertEquals("something odd", declarationOf("something odd"))
    }
}
