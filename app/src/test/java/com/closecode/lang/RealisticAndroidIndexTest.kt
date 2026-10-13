package com.closecode.lang
import com.closecode.lang.DiagnosticCodes

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The "missing Android import" rule only activates when the real API index is loaded, which is the
 * normal in-app condition. The main guardrail suite runs against an empty index, so without this
 * file a regression in that rule would never be caught.
 *
 * Samples here must produce zero ERROR diagnostics, including no "needs an import" complaints.
 */
class RealisticAndroidIndexTest {

    private fun loadIndex(): AndroidApiIndex {
        val path = System.getProperty("closecode.androidApiIndex")
        assumeTrue("android-api.txt not available", path != null && File(path).isFile)
        return AndroidApiIndex.parse(File(path!!).readText())
    }

    private fun assertNoErrorsWithRealIndex(label: String, src: String) {
        val index = loadIndex()
        val result = KotlinAnalyzer.analyze(src, index)
        val lines = src.lines()
        val messages = result.errors.map {
            "line ${it.line} [${it.code}]: ${it.message}   >>>  " +
                lines.getOrNull((it.line - 1).coerceIn(0, lines.lastIndex))?.trim().orEmpty()
        }
        assertTrue("$label produced false errors:\n${messages.joinToString("\n")}", messages.isEmpty())
    }

    @Test
    fun kotlinBuiltinsAndStdlibStayClean() = assertNoErrorsWithRealIndex(
        "kotlinBuiltinsAndStdlib",
        """
        package com.example

        fun demo(text: String, count: Int, items: List<String>) {
            val joined = items.joinToString(separator = ", ")
            val total = items.size + count
            val label = text.trim().uppercase()
            println(label + joined + total)
        }
        """.trimIndent(),
    )

    @Test
    fun androidTypesWithImportsStayClean() = assertNoErrorsWithRealIndex(
        "androidTypesWithImports",
        """
        package com.example

        import android.content.Intent
        import android.os.Bundle
        import android.widget.TextView

        class Demo {
            fun build(context: android.content.Context): Intent {
                return Intent(context, Demo::class.java)
            }

            fun label(view: TextView, state: Bundle) {
                view.text = state.toString()
            }
        }
        """.trimIndent(),
    )

    @Test
    fun composeTypesWithImportsStayClean() = assertNoErrorsWithRealIndex(
        "composeTypesWithImports",
        """
        package com.example

        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Screen() {
            Text(text = "hello", modifier = Modifier)
        }
        """.trimIndent(),
    )

    @Test
    fun aRealAndroidClassWithoutAnImportIsFlagged() {
        val index = loadIndex()
        val src = """
            package com.example

            fun demo(spinner: Spinner) {}
        """.trimIndent()
        val result = KotlinAnalyzer.analyze(src, index)
        assertTrue(
            "Spinner without an import should be reported",
            result.errors.any { it.code == DiagnosticCodes.E0101_UNRESOLVED && it.data == "Spinner" },
        )
    }

    @Test
    fun aStarImportSuppressesTheMissingImportComplaint() {
        val index = loadIndex()
        val src = """
            package com.example

            import android.widget.*

            fun demo(spinner: Spinner) {}
        """.trimIndent()
        val result = KotlinAnalyzer.analyze(src, index)
        assertTrue(
            "a wildcard import covers Spinner",
            result.errors.none { it.code == DiagnosticCodes.E0101_UNRESOLVED && it.data == "Spinner" },
        )
    }
}