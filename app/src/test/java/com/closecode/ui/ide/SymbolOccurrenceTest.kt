package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolOccurrenceTest {

    @Test
    fun caretInsideAWordFindsTheWholeWord() {
        assertEquals("foo", symbolAt("val foo = 1", 5))
        assertEquals("foo", symbolAt("val foo = 1", 7))
    }

    @Test
    fun caretRightAfterAWordStillBelongsToIt() {
        assertEquals("foo", symbolAt("val foo = 1", 7))
    }

    @Test
    fun caretBetweenWordsFindsNothing() {
        assertNull(symbolAt("val foo = 1", 8))
        assertNull(symbolAt("val foo = 1", 9))
    }

    @Test
    fun caretAtTheVeryEndsBehaves() {
        assertNull(symbolAt("", 0))
        assertEquals("foo", symbolAt("foo", 0))
        assertEquals("foo", symbolAt("foo", 3))
        assertNull(symbolAt("foo ", 4))
    }

    @Test
    fun punctuationIsNotPartOfTheWord() {
        assertEquals("bar", symbolAt("foo(bar)", 4))
        assertEquals("foo", symbolAt("foo(bar)", 1))
    }

    @Test
    fun underscoresAndDigitsBelongToTheWord() {
        assertEquals("foo_2", symbolAt("val foo_2 = 1", 6))
    }

    @Test
    fun occurrencesFindEveryWholeWord() {
        val text = "foo(bar)\nval foobar = foo\nFoo"
        assertEquals(listOf(0 until 3, 22 until 25), symbolOccurrences(text, "foo"))
    }

    @Test
    fun prefixedAndSuffixedNamesDoNotMatch() {
        assertEquals(emptyList<IntRange>(), symbolOccurrences("foobar my_foo", "foo"))
    }

    @Test
    fun matchingIsCaseSensitive() {
        assertEquals(listOf(10 until 13), symbolOccurrences("val Foo = foo", "foo"))
    }

    @Test
    fun emptyOrNonWordQueriesMatchNothing() {
        assertEquals(emptyList<IntRange>(), symbolOccurrences("foo foo", ""))
        assertEquals(emptyList<IntRange>(), symbolOccurrences("foo foo", "o o"))
    }

    @Test
    fun adjacentOccurrencesBothMatch() {
        assertEquals(listOf(0 until 2, 3 until 5), symbolOccurrences("aa aa", "aa"))
    }
}
