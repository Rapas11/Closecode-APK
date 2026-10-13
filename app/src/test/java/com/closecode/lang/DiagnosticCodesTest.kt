package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the spec 6.5 codes and the spec 6.6 fixes that were added on top of the older engine.
 */
class DiagnosticCodesTest {

    private val index = AndroidApiIndex.EMPTY

    private fun analyze(source: String) = KotlinAnalyzer.analyze(source, index)

    @Test
    fun everyDiagnosticCarriesASpecShapedCode() {
        val source = """
            package com.example

            import android.widget.Button

            fun demo() {
                val a = 1
                val a = 2
            }
        """.trimIndent()
        val codes = analyze(source).diagnostics.map { it.code }
        assertTrue("expected at least one diagnostic, got $codes", codes.isNotEmpty())
        for (code in codes) {
            assertTrue(
                "code '$code' does not match the spec's E/W + 4 digits format",
                code.length >= 5 && (code.startsWith("E") || code.startsWith("W")),
            )
        }
    }

    @Test
    fun errorCodesStartWithEAndWarningCodesWithW() {
        assertTrue(DiagnosticCodes.isError(DiagnosticCodes.E0101_UNRESOLVED))
        assertTrue(DiagnosticCodes.isWarning(DiagnosticCodes.W0201_UNUSED_IMPORT))
        assertEquals(false, DiagnosticCodes.isError(DiagnosticCodes.W0202_TODO))
        assertEquals(false, DiagnosticCodes.isWarning(DiagnosticCodes.E0103_UNCLOSED))
    }

    @Test
    fun aTodoCommentIsReportedAsW0202() {
        val result = analyze("fun demo() {\n    // TODO: wire this up\n}")
        val todo = result.diagnostics.firstOrNull { it.code == DiagnosticCodes.W0202_TODO }
        assertNotNull("expected a W0202 for the TODO, got ${result.diagnostics}", todo)
        assertEquals(Severity.WARNING, todo!!.severity)
        assertEquals(2, todo.line)
        assertTrue(todo.message.startsWith("TODO: "))
        assertTrue(todo.message.contains("wire this up"))
    }

    @Test
    fun aTodoOnlyUnderlinesTheMarkerNotTheWholeComment() {
        val result = analyze("// TODO: something\nfun demo() {}")
        val todo = result.diagnostics.first { it.code == DiagnosticCodes.W0202_TODO }
        assertEquals(4, todo.length)
        assertEquals("// ".length, todo.offset)
    }

    @Test
    fun aCommentWithoutATodoIsNotReported() {
        val result = analyze("// just a note\nfun demo() {}")
        assertTrue(result.diagnostics.none { it.code == DiagnosticCodes.W0202_TODO })
    }

    @Test
    fun aTodoWordInsideAStringIsNotReported() {
        val result = analyze("val s = \"TODO: not a comment\"\nfun demo() {}")
        assertTrue(result.diagnostics.none { it.code == DiagnosticCodes.W0202_TODO })
    }

    @Test
    fun removeTokenDeletesExactlyTheBadCharacters() {
        // "fun demo() {\n" is 13 characters, then four spaces, so `b` sits at offset 17.
        val source = "fun demo() {\n    b\n}"
        assertEquals(17, source.indexOf('b', 13))
        assertEquals("fun demo() {\n    \n}", SourceEdits.removeToken(source, 17, 1))
    }

    @Test
    fun removeTokenClampsAnOutOfRangeOffset() {
        val source = "ab"
        // A stale diagnostic can point past the end after the user edits the line.
        assertEquals("a", SourceEdits.removeToken(source, 1, 999))
        assertNull(SourceEdits.removeToken(source, 5, 1))
    }

    @Test
    fun addSemicolonAppendsToTheEndOfTheLine() {
        assertEquals("val a = 1;\nval b = 2", SourceEdits.addSemicolon("val a = 1\nval b = 2", 1))
    }

    @Test
    fun addSemicolonIsANoOpWhenOneIsAlreadyThere() {
        val source = "val a = 1;"
        assertNull(SourceEdits.addSemicolon(source, 1))
    }

    @Test
    fun addSemicolonKeepsTrailingSpaceOutOfTheWay() {
        assertEquals("val a = 1;", SourceEdits.addSemicolon("val a = 1   ", 1))
    }

    @Test
    fun addSemicolonIgnoresALineThatDoesNotExist() {
        assertNull(SourceEdits.addSemicolon("val a = 1", 9))
    }
}