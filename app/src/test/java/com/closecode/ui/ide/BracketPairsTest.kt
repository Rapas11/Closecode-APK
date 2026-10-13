package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto close brackets.
 *
 * Pairing is easy to get wrong in the direction that breaks the file: adding a closer where the
 * user did not mean one, inside a string, or on top of a closer that is already there. Every case
 * below is one of those, plus the plain case that has to keep working.
 */
class BracketPairsTest {

    @Test
    fun typingAnOpenerAddsItsCloserWithTheCaretLeftBetween() {
        val result = autoClosePair("val x = ", "val x = (", caret = "val x = (".length)

        assertEquals("val x = ()" to 9, result)
    }

    @Test
    fun everyOpenerGetsItsMatchingCloser() {
        val pairs = listOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')
        pairs.forEach { (open, close) ->
            val after = "v=$open"
            val result = autoClosePair("v=", after, caret = after.length)
            assertEquals("pair for $open", "v=$open$close" to after.length, result)
        }
    }

    @Test
    fun aCloserThatAlreadyFollowsTheCaretIsNotAddedAgain() {
        // The buffer holds a closing quote with the caret just in front of it: typing the opener
        // has to move over what is there rather than produce a third one.
        assertNull(autoClosePair("x\"", "x\"\"", caret = 2))
        assertNull(autoClosePair("f)", "f()", caret = 2))
    }

    @Test
    fun closingAnUnterminatedStringDoesNotAddASecondOne() {
        val before = "val s = \"ab"
        val after = before + "\""

        assertNull(autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun aQuoteTypedAfterAClosedStringStartsAFreshPair() {
        val before = "val s = \"ab\""
        val after = before + "\""

        assertEquals(after + "\"" to after.length, autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun aBracketTypedInsideAStringIsRefused() {
        val before = "val s = \"ab"
        val after = before + "("

        assertNull(autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun aBracketTypedInsideALineCommentIsRefused() {
        val before = "// note "
        val after = before + "("

        assertNull(autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun anEscapedQuoteDoesNotCloseTheStringItIsInside() {
        val before = "val s = \"a\\\""
        val after = before + "("

        assertNull(autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun aPasteIsNeverPaired() {
        val before = "val x = "
        val after = before + "(()"

        assertNull(autoClosePair(before, after, caret = after.length))
    }

    @Test
    fun aCharacterThatIsNotAnOpenerChangesNothing() {
        assertNull(autoClosePair("val x = ", "val x = a", caret = 9))
        assertNull(autoClosePair("val x = ", "val x = )", caret = 9))
        assertNull(autoClosePair("val x = ", "val x = +", caret = 9))
    }

    @Test
    fun nothingIsPairedAtTheVeryStartOfTheBuffer() {
        assertNull(autoClosePair("", "(", caret = 0))
    }

    @Test
    fun aPairingDecisionOnlyEverLooksAtTheLineBeingTyped() {
        assertTrue(insideLiteralOrComment("// done", 7))
        assertTrue(insideLiteralOrComment("val s = \"ab", 11))
        assertTrue(insideLiteralOrComment("val c = 'x", 10))
        assertFalse(insideLiteralOrComment("val x = 1", 9))
        assertFalse(insideLiteralOrComment("\"ab\"", 4))
        assertFalse(insideLiteralOrComment("val s = \"ab\" + 1", 17))
    }
}
