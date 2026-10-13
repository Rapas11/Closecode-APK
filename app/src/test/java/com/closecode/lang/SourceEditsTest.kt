package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceEditsTest {

    // ---- addImport -------------------------------------------------------------------

    @Test
    fun addsImportAfterPackageDeclaration() {
        val src = "package com.example\n\nfun main() {}\n"
        val out = SourceEdits.addImport(src, "android.widget.TextView")
        assertEquals(
            "package com.example\n\nimport android.widget.TextView\n\nfun main() {}\n",
            out,
        )
    }

    @Test
    fun addsImportIntoAnExistingBlock() {
        val src = "package com.example\n\nimport android.os.Bundle\n\nfun main() {}\n"
        val out = SourceEdits.addImport(src, "android.widget.TextView")
        assertTrue(out.contains("import android.os.Bundle\nimport android.widget.TextView"))
        assertEquals(2, out.lines().count { it.startsWith("import ") })
    }

    @Test
    fun addsImportWhenNoPackageExists() {
        val out = SourceEdits.addImport("fun main() {}\n", "android.os.Bundle")
        assertEquals("import android.os.Bundle\n\nfun main() {}\n", out)
    }

    @Test
    fun isANoOpWhenTheSameImportExists() {
        val src = "package com.example\n\nimport android.os.Bundle\n"
        assertEquals(src, SourceEdits.addImport(src, "android.os.Bundle"))
    }

    @Test
    fun refusesToAddASecondImportOfTheSameSimpleName() {
        val src = "package com.example\n\nimport androidx.core.view.ViewCompat\n"
        // Different package, same simple name: adding it would be ambiguous.
        assertEquals(src, SourceEdits.addImport(src, "android.view.ViewCompat"))
    }

    @Test
    fun refusesToAddAnImportWhenTheNameIsAlreadyAliased() {
        val src = "package com.example\n\nimport android.widget.TextView as TView\n"
        assertEquals(src, SourceEdits.addImport(src, "android.widget.TextView"))
    }

    @Test
    fun ignoresWildcardImportsWhenCheckingForDuplicates() {
        val src = "package com.example\n\nimport android.widget.*\n"
        assertNotEquals(src, SourceEdits.addImport(src, "android.widget.TextView"))
    }

    @Test
    fun blankInputIsHandled() {
        assertEquals("import android.os.Bundle\n\n", SourceEdits.addImport("", "android.os.Bundle"))
        assertEquals("", SourceEdits.addImport("", ""))
    }

    @Test
    fun preservesCarriageReturnLineEndings() {
        val src = "package com.example\r\n\r\nfun main() {}\r\n"
        val out = SourceEdits.addImport(src, "android.os.Bundle")
        assertTrue(out.contains("import android.os.Bundle\r\n"))
        assertTrue(!out.contains("\n\nimport"))
    }

    // ---- removeImport ----------------------------------------------------------------

    @Test
    fun removesAnImportLine() {
        val src = "package com.example\n\nimport android.widget.TextView\n\nfun main() {}\n"
        val out = SourceEdits.removeImport(src, "android.widget.TextView")
        assertTrue(!out.contains("TextView"))
        assertTrue(out.contains("fun main()"))
    }

    @Test
    fun removesAnAliasedImport() {
        val src = "package com.example\n\nimport android.widget.TextView as TView\n\nfun main() {}\n"
        val out = SourceEdits.removeImport(src, "android.widget.TextView")
        assertTrue(!out.contains("TView"))
    }

    @Test
    fun removeIsANoOpForAnAbsentImport() {
        val src = "package com.example\n\nimport android.os.Bundle\n"
        assertEquals(src, SourceEdits.removeImport(src, "android.widget.TextView"))
    }

    @Test
    fun removeDoesNotTouchAFileWithNoImports() {
        val src = "package com.example\n\nfun main() {}\n"
        assertEquals(src, SourceEdits.removeImport(src, "android.os.Bundle"))
    }

    // ---- round trip ------------------------------------------------------------------

    @Test
    fun addThenRemoveRestoresTheOriginal() {
        val src = "package com.example\n\nimport android.os.Bundle\n\nfun main() {}\n"
        val added = SourceEdits.addImport(src, "android.widget.TextView")
        assertNotEquals(src, added)
        assertEquals(src, SourceEdits.removeImport(added, "android.widget.TextView"))
    }

    @Test
    fun caretLandsAfterTheNewImportLine() {
        val src = "package com.example\n\nimport android.os.Bundle\n\nfun main() {}\n"
        val caret = SourceEdits.caretAfterAddImport(src, "android.widget.TextView")
        assertTrue(caret != null)
        val updated = SourceEdits.addImport(src, "android.widget.TextView")
        val text = updated.substring(0, caret!!)
        assertTrue("caret should sit at the end of the import line", text.endsWith("\n"))
    }

    @Test
    fun caretIsNullWhenNoImportWasAdded() {
        val src = "package com.example\n\nimport android.os.Bundle\n"
        assertNull(SourceEdits.caretAfterAddImport(src, "android.os.Bundle"))
    }

    // ---- helpers ---------------------------------------------------------------------

    @Test
    fun simpleNameAndPackageSplitAnFqn() {
        assertEquals("TextView", SourceEdits.simpleNameOf("android.widget.TextView"))
        assertEquals("android.widget", SourceEdits.packageOf("android.widget.TextView"))
        assertEquals("Cursor", SourceEdits.simpleNameOf("Cursor"))
        assertEquals("", SourceEdits.packageOf("Cursor"))
    }

    @Test
    fun editedSourceStillParsesCleanly() {
        val src = "package com.example\n\nfun main() {\n    val v = TextView(null)\n}\n"
        val added = SourceEdits.addImport(src, "android.widget.TextView")
        val result = KotlinAnalyzer.analyze(added)
        assertEquals(
            emptyList<String>(),
            result.errors.map { "${it.line}: ${it.message}" },
        )
    }

    // ---- line actions ----------------------------------------------------------------

    @Test
    fun toggleLineCommentAddsThenRemoves() {
        val src = "package com.example\n\nfun main() {}\n"
        val commented = SourceEdits.toggleLineComment(src, 3)
        assertEquals("package com.example\n\n// fun main() {}\n", commented)
        assertEquals(src, SourceEdits.toggleLineComment(commented, 3))
    }

    @Test
    fun toggleLineCommentKeepsIndentation() {
        val src = "fun main() {\n    val x = 1\n}\n"
        val commented = SourceEdits.toggleLineComment(src, 2)
        assertTrue(commented.contains("    // val x = 1"))
    }

    @Test
    fun toggleLineCommentOnABlankLine() {
        val src = "a\n\nb\n"
        assertEquals("a\n// \nb\n", SourceEdits.toggleLineComment(src, 2))
    }

    @Test
    fun toggleLineCommentIgnoresLinesThatDoNotExist() {
        val src = "a\nb\n"
        assertEquals(src, SourceEdits.toggleLineComment(src, 0))
        assertEquals(src, SourceEdits.toggleLineComment(src, -1))
        assertEquals(src, SourceEdits.toggleLineComment(src, 99))
    }

    @Test
    fun moveLineUpSwapsWithTheLineAbove() {
        assertEquals("b\na\nc\n", SourceEdits.moveLineUp("a\nb\nc\n", 2))
    }

    @Test
    fun moveLineDownSwapsWithTheLineBelow() {
        assertEquals("a\nc\nb\n", SourceEdits.moveLineDown("a\nb\nc\n", 2))
    }

    @Test
    fun moveLineRefusesTheFileEdges() {
        val src = "a\nb\nc\n"
        assertEquals(src, SourceEdits.moveLineUp(src, 1))
        assertEquals(src, SourceEdits.moveLineDown(src, 3))
    }

    @Test
    fun moveRoundTrips() {
        val src = "a\nb\nc\n"
        val down = SourceEdits.moveLineDown(src, 1)
        assertEquals(src, SourceEdits.moveLineUp(down, 2))
    }

    @Test
    fun lineActionsPreserveCarriageReturnLineEndings() {
        val src = "a\r\nb\r\nc\r\n"
        assertEquals("b\r\na\r\nc\r\n", SourceEdits.moveLineUp(src, 2))
        assertEquals("a\r\n// b\r\nc\r\n", SourceEdits.toggleLineComment(src, 2))
    }

    @Test
    fun caretFollowsALineMove() {
        val src = "aaa\nbbb\nccc\n"
        val moved = SourceEdits.moveLineDown(src, 1)
        assertEquals("bbb\naaa\nccc\n", moved)
        // The caret keeps its column but lands on the text that moved.
        val caret = SourceEdits.caretAfterLineMove(src, line = 1, column = 2, down = true)
        assertEquals("aa", moved.substring(caret, caret + 2))
    }

    @Test
    fun caretStaysPutAtTheTopEdge() {
        val src = "aaa\nbbb\n"
        val caret = SourceEdits.caretAfterLineMove(src, line = 1, column = 1, down = false)
        assertTrue(caret in 0..src.length)
    }

    // ---- formatKotlin ------------------------------------------------------------------

    @Test
    fun formatKotlinNormalizesIndentationToTwoSpaces() {
        val src = "fun main() {\n        val x = 1\n    }\n"
        assertEquals("fun main() {\n  val x = 1\n}\n", SourceEdits.formatKotlin(src))
    }

    @Test
    fun formatKotlinKeepsElseAttachedToItsBraces() {
        val src = "if (ok) {\n    show()\n    } else {\n    hide()\n}\n"
        assertEquals("if (ok) {\n  show()\n} else {\n  hide()\n}\n", SourceEdits.formatKotlin(src))
    }

    @Test
    fun formatKotlinIgnoresBracesInsideStringsAndComments() {
        val src = "fun main() {\nval s = \"{ not code }\" // }\n}\n"
        assertEquals("fun main() {\n  val s = \"{ not code }\" // }\n}\n", SourceEdits.formatKotlin(src))
    }

    @Test
    fun formatKotlinLeavesRawStringBodiesAlone() {
        val src = "val text = \"\"\"\n        keep    me\n    \"\"\"\n"
        assertEquals(src, SourceEdits.formatKotlin(src))
    }

    @Test
    fun formatKotlinPreservesFinalNewlineState() {
        assertEquals("fun main() {}", SourceEdits.formatKotlin("fun main() {}"))
        assertEquals("fun main() {}\n", SourceEdits.formatKotlin("fun main() {}\n"))
    }

    @Test
    fun normalizeTrailingWhitespaceKeepsLayout() {
        val src = "line one   \n\tline two\t\n\n"
        assertEquals("line one\n\tline two\n\n", SourceEdits.normalizeTrailingWhitespace(src))
    }
}
/** Re-indenting only what a selection covers. */
class FormatRangeTest {

    private val file = """
        fun main() {
        val a = 1
        if (a > 0) {
        println(a)
        }
        val b = 2
        }
    """.trimIndent()

    @Test
    fun onlyTheSelectedLinesMove() {
        val from = file.indexOf("val a")
        val to = file.indexOf("println")
        val out = SourceEdits.formatRange(file, from, to)
        assertTrue("the line above must not move", out.startsWith("fun main() {\n  val a = 1"))
        // Line 5 is outside the selection, so it is not touched at all — including its indentation,
        // which is wrong in this fixture on purpose.
        assertTrue("the line below must not move", out.contains("\nval b = 2\n}"))
    }

    @Test
    fun theSelectionKeepsTheLevelItWasWrittenAt() {
        // Selected inside a function, not at the margin: the block's own indentation is part of
        // where it lives, and dropping it to column zero would move code away from its block.
        val from = file.indexOf("val b")
        val out = SourceEdits.formatRange(file, from, file.length)
        assertTrue(out.contains("\n  val b = 2"))
    }

    @Test
    fun aWholeBlockComesOutRight() {
        val from = file.indexOf("if (a > 0)")
        val to = file.indexOf("}", from) + 1
        val out = SourceEdits.formatRange(file, from, to)
        assertTrue(out.contains("\n  if (a > 0) {\n    println(a)\n  }"))
    }

    @Test
    fun theReadersOwnIndentSizeIsUsed() {
        val from = file.indexOf("val a")
        val two = SourceEdits.formatRange(file, from, file.indexOf("println"), tabSize = 2)
        assertTrue(two.contains("\n  val a = 1"))
        val four = SourceEdits.formatRange(file, from, file.indexOf("println"), tabSize = 4)
        assertTrue(four.contains("\n    val a = 1"))
    }

    @Test
    fun aBraceInsideAStringDoesNotChangeTheLevel() {
        val src = "fun a() {\nval s = \"{\"\nval b = 1\n}"
        val out = SourceEdits.formatRange(src, src.indexOf("val b"), src.length)
        // One level of braces is open: the brace inside the string is a character, not a block.
        assertTrue(out.contains("\n  val b = 1"))
    }

    @Test
    fun aSelectionThatIsNotThereChangesNothing() {
        assertEquals(file, SourceEdits.formatRange(file, 5, 5))
        assertEquals(file, SourceEdits.formatRange(file, 400, 500))
        assertEquals("", SourceEdits.formatRange("", 0, 0))
    }
}

class SelectionScopeTest {

    private val file = "fun a() {\n    val x = 1\n}\n"

    @Test
    fun wholeLinesFromMarginToNewlineCount() {
        val from = file.indexOf("    val x")
        val to = file.indexOf('\n', from) + 1
        assertTrue(SourceEdits.selectionCoversWholeLines(file, from, to))
    }

    @Test
    fun theWholeFileCounts() {
        assertTrue(SourceEdits.selectionCoversWholeLines(file, 0, file.length))
    }

    @Test
    fun aPartialFirstLineDoesNot() {
        val from = file.indexOf("val x")
        val to = file.indexOf('\n', from) + 1
        assertFalse(SourceEdits.selectionCoversWholeLines(file, from, to))
    }

    @Test
    fun aPartialLastLineDoesNot() {
        val from = file.indexOf("    val x")
        val to = from + 5
        assertFalse(SourceEdits.selectionCoversWholeLines(file, from, to))
    }

    @Test
    fun anEmptySelectionCoversNothing() {
        assertFalse(SourceEdits.selectionCoversWholeLines(file, 4, 4))
        assertFalse(SourceEdits.selectionCoversWholeLines("", 0, 0))
    }
}
