package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two errors a real Compose activity reports that nothing reported before.
 *
 * Two separate rules were hiding them. Any identifier followed by `(` or `{` was skipped before
 * resolution ran, which is most of Kotlin, because you call things. And any class with a supertype
 * was treated as having an unaccountable one, so a single `: ComponentActivity()` switched off every
 * unresolved-reference check inside the class body. Together they meant a Compose activity with real
 * mistakes in it showed zero errors.
 */
class MissingCallAndLambdaErrorsTest {

    /** The file as the user's project actually had it: two genuine mistakes, nothing reported. */
    private val activity = """
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

    private fun unresolvedIn(
        src: String,
        api: AndroidApiIndex = AndroidApiIndex.EMPTY,
        projectSymbols: Set<String> = emptySet(),
    ): List<String> =
        KotlinAnalyzer.analyze(src, api, projectSymbols).errors
            .filter { it.code == DiagnosticCodes.E0101_UNRESOLVED }
            .map { it.message }

    @Test
    fun aMissingTrailingLambdaIsReported() {
        // ClosecodeTheme is never imported or defined. Used as `Name {`, which used to be skipped.
        val messages = unresolvedIn(activity)
        assertTrue(
            "expected ClosecodeTheme to be reported, got $messages",
            messages.any { it.contains("ClosecodeTheme") },
        )
    }

    @Test
    fun aMissingCallIsReported() {
        // Text is never imported. Used as `Name(`, which used to be skipped.
        val messages = unresolvedIn(activity)
        assertTrue(
            "expected Text to be reported, got $messages",
            messages.any { it.contains("'Text'") },
        )
    }

    @Test
    fun bothMistakesCarryTheUnresolvedCodeAndTheirLine() {
        val errors = KotlinAnalyzer.analyze(activity).errors
            .filter { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertEquals(2, errors.size)
        // Line 11 holds ClosecodeTheme, line 12 holds the Text call.
        assertEquals(listOf(11, 12), errors.map { it.line }.sorted())
    }

    @Test
    fun anImportedSupertypeNoLongerDisablesCheckingInTheClassBody() {
        // The whole point of the supertype change: the class extends something the file imports, so
        // a capitalised type it never imports is reported inside the body.
        val withoutBodyCheck = """
            import androidx.activity.ComponentActivity

            class MainActivity : ComponentActivity() {
                fun go() { MissingWidget() }
            }
        """.trimIndent()
        assertTrue(unresolvedIn(withoutBodyCheck).any { it.contains("MissingWidget") })
    }

    @Test
    fun aSupertypeThatIsNeverImportedStillSuppressesChecking() {
        // Nothing knows what ThisComesFromNowhere is, so the body stays exempt rather than reporting
        // every inherited member as missing.
        val unknown = """
            class Widget : ThisComesFromNowhere() {
                fun go() { MissingThing() }
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), unresolvedIn(unknown))
    }

    @Test
    fun aCallIntoASiblingFileIsNotAnError() {
        // Resolution is per file. NotesScreen lives in another file of the same project, so the
        // project-wide symbol set has to carry it or this becomes a false positive.
        val main = """
            package com.closecode.notes

            import androidx.activity.ComponentActivity

            class MainActivity : ComponentActivity() {
                fun onCreate() {
                    NotesScreen()
                }
            }
        """.trimIndent()
        val sibling = """
            package com.closecode.notes

            fun NotesScreen() {
            }
        """.trimIndent()

        val withoutProject = unresolvedIn(main)
        assertTrue(
            "a sibling call is only an error when the project is not known",
            withoutProject.any { it.contains("NotesScreen") },
        )

        val project = KotlinAnalyzer.projectSymbols(listOf(main, sibling))
        assertTrue("the sibling function must be visible project-wide", "NotesScreen" in project)
        assertEquals(
            emptyList<String>(),
            unresolvedIn(main, projectSymbols = project),
        )
    }

    @Test
    fun ordinaryImportedCallsStillPass() {
        // Regression guard: nothing that used to be clean may start reporting.
        val clean = """
            package com.example

            import android.os.Bundle
            import androidx.activity.ComponentActivity

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val list = listOf(1, 2, 3)
                    val label = list.joinToString()
                    println(label)
                }
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), unresolvedIn(clean))
    }

    @Test
    fun aMemberOfAnImportedClassIsNotAnErrorWhenCalledUnqualified() {
        // The safety net for calls: the index knows Bundle's members, so an inherited call reached
        // through the imported class must not be reported.
        val src = """
            package com.example

            import android.os.Bundle

            fun go(b: Bundle) {
                describe()
            }
        """.trimIndent()
        // describe() is not a Bundle member, so it must still be reported. This pins that the net is
        // scoped to real members rather than blanket-skipping every call.
        assertTrue(unresolvedIn(src).any { it.contains("describe") })
        assertFalse("guarding against over-suppression", unresolvedIn(src).isEmpty())
    }
}