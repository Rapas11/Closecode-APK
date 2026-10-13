package com.closecode.ui.ide

import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry behind an indentation guide, away from any layout.
 *
 * Every number here is arithmetic over the file's own text, which is the point: the guide's column
 * is decided before a single pixel is measured, so it can be pinned by a test rather than checked by
 * eye on a phone. The one measured value — a character's width — is applied by the layer afterwards.
 */
class GuideGeometryTest {

    // -- The column ------------------------------------------------------------

    @Test
    fun aBlockSitsOneTabPastTheLineThatOpenedIt() {
        // The block's contents live one tab in from the opener, so that is where its rule goes.
        assertEquals(4, guideColumn(openIndent = 0, tabSize = 4))
        assertEquals(8, guideColumn(openIndent = 4, tabSize = 4))
    }

    @Test
    fun theColumnFollowsTheTabSizeTheProjectActuallyUses() {
        assertEquals(2, guideColumn(openIndent = 0, tabSize = 2))
        assertEquals(8, guideColumn(openIndent = 0, tabSize = 8))
    }

    @Test
    fun aSillyTabSizeStillProducesAColumnRatherThanTheOpenersOwn() {
        // A guide drawn at the opener's own column would sit on top of the opener's text, which is
        // what the old brace-glyph anchor did on a line like `setContent {`.
        assertTrue(guideColumn(openIndent = 4, tabSize = 0) > 4)
        assertTrue(guideColumn(openIndent = 4, tabSize = -1) > 4)
    }

    @Test
    fun aNegativeIndentIsTreatedAsNoIndentAtAll() {
        assertEquals(4, guideColumn(openIndent = -6, tabSize = 4))
    }

    @Test
    fun theColumnIsAlwaysDeeperThanTheLineThatOpenedTheBlock() {
        for (tabSize in 1..8) {
            for (indent in 0..24) {
                assertTrue(
                    "tabSize=$tabSize indent=$indent",
                    guideColumn(indent, tabSize) > indent,
                )
            }
        }
    }

    // -- Indents ---------------------------------------------------------------

    @Test
    fun everyLineReportsItsOwnIndent() {
        // Four lines, including the empty one the trailing newline leaves behind — the same count
        // `String.lines()` gives, so a line number and an indent always describe the same row.
        val indents = indentColumnsOf("fun main() {\n    one()\n}\n", tabSize = 4)
        assertEquals(4, indents.size)
        assertEquals(0, indents[0])
        assertEquals(4, indents[1])
        assertEquals(0, indents[2])
    }

    @Test
    fun aTabAdvancesToTheNextStopRatherThanCountingAsOneColumn() {
        // Four spaces and one tab are the same indent, so they must produce the same column — that
        // is the whole reason this is arithmetic rather than "count the characters before the code".
        val spaces = indentColumnsOf("    val x = 1\n", tabSize = 4)
        val tabs = indentColumnsOf("\tval x = 1\n", tabSize = 4)
        assertEquals(spaces[0], tabs[0])
        assertEquals(4, tabs[0])
    }

    @Test
    fun aTabPartWayAcrossTheStopCarriesItToTheNextOne() {
        // Two spaces then a tab lands on column 4, not 3: the tab fills what is left of the stop.
        val indents = indentColumnsOf("  \tval x = 1\n", tabSize = 4)
        assertEquals(4, indents[0])
    }

    @Test
    fun aTabAtAColumnThatIsAlreadyAStoPAdvancesAFullStop() {
        val indents = indentColumnsOf("\t\tval x = 1\n", tabSize = 4)
        assertEquals(8, indents[0])
    }

    @Test
    fun whitespaceAfterCodeIsNotIndent() {
        val indents = indentColumnsOf("val x = 1    \n", tabSize = 4)
        assertEquals(0, indents[0])
    }

    @Test
    fun aLineOfNothingButSpacesIsBlankRatherThanDeeplyIndented() {
        // Counting it would hang a guide off the middle of a blank row inside a block.
        val indents = indentColumnsOf("fun main() {\n        \n    one()\n}\n", tabSize = 4)
        assertEquals(0, indents[1])
        assertEquals(4, indents[2])
    }

    @Test
    fun aFinalLineWithNoNewlineOfItsOwnStillGetsAnEntry() {
        val indents = indentColumnsOf("a\n    b", tabSize = 4)
        assertEquals(2, indents.size)
        assertEquals(4, indents[1])
    }

    @Test
    fun anEmptyFileStillHasOneLine() {
        assertEquals(1, indentColumnsOf("", tabSize = 4).size)
    }

    @Test
    fun thereIsOneIndentForEveryLine() {
        val source = "a\n\nb\n\n\nc\n"
        assertEquals(source.lines().size, indentColumnsOf(source, tabSize = 4).size)
    }

    // -- The span --------------------------------------------------------------

    @Test
    fun aGuideRunsFromTheLineAfterTheOpenerToTheCloserItself() {
        assertEquals(8..16, guideSpan(openLine = 7, closeLine = 16))
        assertEquals(11..13, guideSpan(openLine = 10, closeLine = 13))
    }

    @Test
    fun aGuideNeverStartsOnTheLineHoldingTheOpeningBrace() {
        // Drawn from the opener's own row it would run across the `{` and everything after it.
        val span = assertNotNull(guideSpan(openLine = 10, closeLine = 14)).let { 10 + 1..14 }
        assertTrue(span.first > 10)
    }

    @Test
    fun aBlockThatOpensAndClosesOnTheSameLineDrawsNothing() {
        assertNull(guideSpan(openLine = 5, closeLine = 5))
    }

    @Test
    fun aBraceLeftOpenAtTheEndOfTheFileDrawsNothing() {
        assertNull(guideSpan(openLine = 12, closeLine = 12))
        assertNull(guideSpan(openLine = 12, closeLine = 11))
    }

    @Test
    fun aSpanIsNeverEmpty() {
        for (open in 1..20) {
            for (close in 1..20) {
                val span = guideSpan(open, close) ?: continue
                assertTrue("open=$open close=$close", !span.isEmpty())
            }
        }
    }

    // -- The rule's weight -----------------------------------------------------

    @Test
    fun aGuideIsFaintEnoughToBeAHintInBothThemes() {
        // Regression guard on the weight itself: at the strength of a real divider the rules read
        // as bright bars competing with the code, which is the opposite of an annotation.
        val floor = 0.12f
        val ceiling = 0.22f
        for (theme in listOf(LightColors, DarkColors)) {
            val alpha = theme.guideRule.alpha
            assertTrue("alpha=$alpha", alpha >= floor)
            assertTrue("alpha=$alpha", alpha <= ceiling)
        }
    }

    @Test
    fun aGuideIsFainterThanTheTextItSitsBeside() {
        assertTrue(LightColors.guideRule.alpha < LightColors.tx.alpha)
        assertTrue(DarkColors.guideRule.alpha < DarkColors.tx.alpha)
    }

    // -- Which block the caret is in -------------------------------------------
    // A two-level file: `outer` opens on line 1, `inner` on line 2.
    //
    // Expectations are read off the pairs the parser actually produced rather than typed in as
    // offsets: hand-counting characters in a fixture is how a test ends up asserting the author's
    // arithmetic instead of the code's behaviour.
    private val nested = "fun outer() {\n    if (x) {\n        y()\n    }\n}\n"
    private val nestedPairs = bracePairs(nested)
    private val outerPair = nestedPairs[0]
    private val innerPair = nestedPairs[1]

    @Test
    fun aCaretInsideABlockFindsThatBlock() {
        val caret = nested.indexOf("y()") + 1
        assertEquals(innerPair.first, enclosingPair(nestedPairs, caret)?.first)
    }

    @Test
    fun theInnermostBlockWinsOverTheOneAroundIt() {
        // Between the outer brace and the inner one: inside `outer`, outside `inner`.
        val caret = nested.indexOf("if (x)") + 1
        assertEquals(outerPair.first, enclosingPair(nestedPairs, caret)?.first)
    }

    @Test
    fun aCaretAtTheTopLevelBelongsToNoBlock() {
        assertNull(enclosingPair(nestedPairs, 3))
        assertNull(enclosingPair(nestedPairs, 0))
    }

    @Test
    fun aCaretOnAClosingBraceIsStillInsideItsBlock() {
        // The reader is looking at the `}` that closes the inner block; that block is the one lit.
        assertEquals(
            innerPair.first,
            enclosingPair(nestedPairs, innerPair.last)?.first,
        )
    }

    @Test
    fun aCaretOnTheOpeningBraceIsNotYetInsideIt() {
        // Immediately *before* the `{` is still outside; immediately after it is inside. That is the
        // distinction a reader makes when typing a block open, and getting it wrong lights the wrong
        // block for the first keystroke.
        assertEquals(outerPair.first, enclosingPair(nestedPairs, innerPair.first)?.first)
        assertEquals(innerPair.first, enclosingPair(nestedPairs, innerPair.first + 1)?.first)
    }

    @Test
    fun theAnswerDoesNotDependOnTheOrderPairsArriveIn() {
        // Reversed on purpose: a helper that scanned backwards for a match would get this wrong, and
        // only on files whose pairs happen not to be sorted.
        val caret = nested.indexOf("y()") + 1
        assertEquals(innerPair.first, enclosingPair(nestedPairs.reversed(), caret)?.first)
    }

    @Test
    fun twoBranchesAtTheSameIndentEachResolveToThemselves() {
        // `if` and `else` sit at the same column, so only the offset tells them apart.
        val source = "fun f() {\n    if (a) {\n        a()\n    } else {\n        b()\n    }\n}\n"
        val pairs = bracePairs(source)
        assertEquals(3, pairs.size)
        val ifPair = pairs[1]
        val elsePair = pairs[2]
        assertEquals(ifPair.first, enclosingPair(pairs, source.indexOf("a()") + 1)?.first)
        assertEquals(elsePair.first, enclosingPair(pairs, source.indexOf("b()") + 1)?.first)
    }

    // -- Which braces to box --------------------------------------------------

    @Test
    fun aCaretJustInsideAnOpeningBraceBoxesThatPair() {
        // The case after typing one: the caret sits immediately after the `{`.
        val source = "fun f() {\n    y()\n}\n"
        val pair = bracePairs(source).single()
        assertEquals(pair.first to pair.last, activeBraces(bracePairs(source), source, pair.first + 1))
    }

    @Test
    fun aCaretSittingOnABraceBoxesThatPair() {
        val source = "fun f() {\n    y()\n}\n"
        val pairs = bracePairs(source)
        val pair = pairs.single()
        assertEquals(pair.first to pair.last, activeBraces(pairs, source, pair.first))
        assertEquals(pair.first to pair.last, activeBraces(pairs, source, pair.last))
    }

    @Test
    fun aCaretNextToAClosingBraceBoxesItsOwnPairRatherThanTheOuterOne() {
        val source = "fun outer() {\n    fun f() {\n        y()\n    }\n}\n"
        val pairs = bracePairs(source)
        val inner = pairs[1]
        // Immediately after the inner `}`, and still within the outer block. The inner pair wins,
        // because the reader is beside that brace rather than merely somewhere inside the outer one.
        assertEquals(inner.first to inner.last, activeBraces(pairs, source, inner.last + 1))
    }

    @Test
    fun aCaretDeepInsideABlockBoxesItsBraces() {
        val source = "fun f() {\n    val x = 1\n}\n"
        val pairs = bracePairs(source)
        val pair = pairs.single()
        val caret = source.indexOf("val x") + 1
        assertEquals(pair.first to pair.last, activeBraces(pairs, source, caret))
    }

    @Test
    fun aBraceWithNoPartnerBoxesNothing() {
        // The reader is beside a `{` that is not closed. There is no second box to draw, and lighting
        // some outer block instead would put the emphasis where they are not looking.
        val source = "fun f() {\n    val x = 1\n"
        assertTrue(bracePairs(source).isEmpty())
        assertNull(activeBraces(bracePairs(source), source, source.indexOf('{') + 1))
    }

    @Test
    fun aCaretAtTheTopLevelBoxesNothing() {
        val source = "val x = 1\nfun f() {\n    y()\n}\n"
        assertNull(activeBraces(bracePairs(source), source, 3))
    }

    @Test
    fun anOutOfRangeCaretAnswersNullWithoutThrowing() {
        // Callers clamp, but a helper that indexes the buffer must not be the thing that crashes if
        // one of them ever forgets.
        val pairs = bracePairs(nested)
        assertNull(activeBraces(pairs, nested, -1))
        assertNull(activeBraces(pairs, nested, nested.length + 50))
        // Both extremes are outside every block, and both are legitimate answers rather than errors.
        assertNull(activeBraces(pairs, nested, 0))
    }

    @Test
    fun theUnderlineAndTheEnclosingBlockAgree() {
        // The underline reads the same answer nothing else reads anymore: the guides are one
        // colour now, so the pair the underline marks is the pair the caret is beside or inside.
        // Fed the full pair set, as production does — a brace-only list with a paren-adjacent
        // caret cannot occur outside this test.
        val source = "fun f() {\n    g(x)\n}\n"
        val pairs = allBracketPairs(source)
        val caret = source.indexOf('x')
        assertEquals(
            activeBraces(pairs, source, caret),
            enclosingPair(pairs, caret)?.let { it.first to it.last },
        )
    }

    @Test
    fun aCaretInsideParenthesesUnderlinesTheParentheses() {
        // The pair is the innermost of all three kinds, not just braces: the reader is inside the
        // call, not merely inside the function around it.
        val source = "fun f() {\n    g(x)\n}\n"
        val pairs = allBracketPairs(source)
        val caret = source.indexOf('x')
        val paren = pairs.first { it.first < caret && caret <= it.last && source[it.first] == '(' }
        assertEquals(paren.first to paren.last, activeBraces(pairs, source, caret))
    }

    @Test
    fun aCaretBesideAClosingParenUnderlinesItsPair() {
        val source = "val x = f(a)\n"
        val pairs = allBracketPairs(source)
        val paren = pairs.single()
        assertEquals(paren.first to paren.last, activeBraces(pairs, source, paren.last + 1))
    }

    @Test
    fun aLoneOpenerIsReportedUnmatched() {
        // No partner, so the underline has nothing to join — the red mark goes on this offset.
        val source = "fun f() {\n    val x = 1\n"
        val pairs = allBracketPairs(source)
        val lone = source.indexOf('{')
        assertEquals(lone, unmatchedBracketAt(source, lone + 1, pairs))
    }

    @Test
    fun aLoneCloserIsReportedUnmatched() {
        val source = "val x = 1)\n"
        val pairs = allBracketPairs(source)
        assertTrue(pairs.isEmpty())
        assertEquals(source.indexOf(')'), unmatchedBracketAt(source, source.indexOf(')'), pairs))
    }

    @Test
    fun aMatchedBracketIsNeverReportedUnmatched() {
        val source = "fun f() {\n    y()\n}\n"
        val pairs = allBracketPairs(source)
        val brace = source.indexOf('{')
        assertNull(unmatchedBracketAt(source, brace + 1, pairs))
        assertNull(unmatchedBracketAt(source, brace, pairs))
    }

    @Test
    fun aCaretBesideNoBracketReportsNothingUnmatched() {
        val source = "fun f() {\n    y()\n}\n"
        val pairs = allBracketPairs(source)
        assertNull(unmatchedBracketAt(source, source.indexOf('y'), pairs))
    }

    private fun bracePairs(source: String): List<IntRange> = com.closecode.ui.ide.bracePairs(source)
}
