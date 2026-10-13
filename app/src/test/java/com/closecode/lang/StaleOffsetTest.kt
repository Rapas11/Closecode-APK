package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor runs analysis asynchronously, so a result can briefly describe text the user has
 * already changed. Any offset taken from the *current* caret can therefore point past the end of
 * the analysed source. That used to throw StringIndexOutOfBoundsException during composition,
 * which killed the app on the first keystroke. These tests pin the boundary behaviour.
 */
class StaleOffsetTest {

    private val old = "fun a() = 1"
    private val fresh = KotlinAnalyzer.analyze(old)

    @Test
    fun prefixAtBeyondEndOfSourceClampsToEnd() {
        assertEquals("1", fresh.prefixAt(old.length))
        assertEquals("1", fresh.prefixAt(old.length + 1))
        assertEquals("1", fresh.prefixAt(old.length + 500))
        assertEquals("1", fresh.prefixAt(Int.MAX_VALUE / 2))
    }

    @Test
    fun negativeOffsetsClampToStart() {
        assertEquals("", fresh.prefixAt(-1))
        assertEquals("", fresh.prefixAt(-10_000))
        assertEquals("", fresh.prefixAt(Int.MIN_VALUE))
    }

    @Test
    fun isMemberAccessOutOfRangeDoesNotThrow() {
        fresh.isMemberAccess(old.length + 3)
        fresh.isMemberAccess(-1)
        assertEquals(false, fresh.isMemberAccess(old.length + 3))
    }

    @Test
    fun completionsOutOfRangeDoNotThrow() {
        fresh.completionsAt(old.length + 3)
        fresh.completionsAt(-3)
        assertTrue(fresh.completionsAt(old.length + 3).isEmpty())
    }

    @Test
    fun aFreshAnalysisReportsItselfAsMatchingItsSource() {
        assertEquals(old, fresh.source)
        assertTrue(fresh.source != "fun a() = 12")
    }

    @Test
    fun inBoundsOffsetsStillBehaveNormally() {
        val src = "fun alpha(): Int = 1\nfun beta() { al }\n"
        val analysis = KotlinAnalyzer.analyze(src)
        val afterWord = src.indexOf("al }") + 2
        assertEquals("al", analysis.prefixAt(afterWord))
        assertTrue(analysis.completionsAt(afterWord).isNotEmpty())
    }

    @Test
    fun everyOffsetInASweepNeverThrows() {
        val longer = "fun a() = 1\nclass B { val x = 2 }\n"
        val analysis = KotlinAnalyzer.analyze(longer)
        var offset = -5
        while (offset <= longer.length + 5) {
            analysis.prefixAt(offset)
            analysis.isMemberAccess(offset)
            analysis.completionsAt(offset)
            offset += 3
        }
    }
}