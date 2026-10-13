package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each warning the checker claims to support, proved against a file that should trigger it and one
 * that should not.
 *
 * These exist because "0 warnings" is ambiguous: it can mean the file is clean, or it can mean the
 * rule silently never fires. Every rule here fires on a positive case and stays quiet on a negative
 * one, so a zero on screen means something.
 */
class WarningRulesTest {

    private fun warnings(src: String, projectSymbols: Set<String> = emptySet()): List<String> =
        KotlinAnalyzer.analyze(src, projectSymbols = projectSymbols).warnings.map { it.message }

    private fun codes(src: String): List<String> =
        KotlinAnalyzer.analyze(src).warnings.map { it.code }

    @Test
    fun anUnusedImportIsReported() {
        val src = """
            package com.example

            import android.os.Bundle
            import java.util.UUID

            fun go(b: Bundle) {}
        """.trimIndent()
        assertTrue(
            "UUID is imported and never used: ${warnings(src)}",
            warnings(src).any { it.contains("UUID") },
        )
        assertTrue(codes(src).contains(DiagnosticCodes.W0201_UNUSED_IMPORT))
    }

    @Test
    fun everyUsedImportIsLeftAlone() {
        val src = """
            package com.example

            import android.os.Bundle
            import java.util.UUID

            fun go(b: Bundle, id: UUID) {}
        """.trimIndent()
        assertEquals(emptyList<String>(), warnings(src))
    }

    @Test
    fun aTodoCommentIsReported() {
        val src = """
            package com.example

            // TODO: write the storage layer
            fun go() {}
        """.trimIndent()
        assertTrue(
            "the TODO should warn: ${warnings(src)}",
            warnings(src).any { it.contains("write the storage layer") },
        )
        assertTrue(codes(src).contains(DiagnosticCodes.W0202_TODO))
    }

    @Test
    fun anOrdinaryCommentIsNotATodo() {
        val src = """
            package com.example

            // this is just a note about the code below
            fun go() {}
        """.trimIndent()
        assertEquals(emptyList<String>(), warnings(src))
    }

    @Test
    fun aVariableThatIsOnlyDeclaredIsReported() {
        val src = """
            package com.example

            fun go() {
                val unusedName = 5
            }
        """.trimIndent()
        assertTrue(
            "an unused local should warn: ${warnings(src)}",
            warnings(src).any { it.contains("unusedName") },
        )
        assertTrue(codes(src).contains(DiagnosticCodes.W0203_UNUSED_VARIABLE))
    }

    @Test
    fun aVariableThatIsReadIsLeftAlone() {
        val src = """
            package com.example

            fun go(): Int {
                val usedName = 5
                return usedName
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), warnings(src))
    }

    @Test
    fun aScaffoldedActivityHasNothingToWarnAbout() {
        // The file the user has been editing. All three imports are used and there are no TODOs, so
        // zero warnings is the correct answer here and this pins that it is reached for the right
        // reason rather than because a rule failed to fire.
        val activity = """
            package com.closecode.blankapp

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.compose.setContent

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        ClosecodeTheme {
                            Text("Hello, world boos")
                        }
                    }
                }
            }
        """.trimIndent()
        val project = KotlinAnalyzer.projectSymbols(
            listOf(activity, "package com.closecode.blankapp\n\nfun ClosecodeTheme() {}\n"),
        )
        assertEquals(
            "all three imports are used and there are no TODOs",
            emptyList<String>(),
            warnings(activity, project),
        )
    }
}