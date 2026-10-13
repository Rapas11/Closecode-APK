package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRoutingTest {

    private fun file(path: String, kind: FileKind): ProjectFile =
        ProjectFile(
            name = path.substringAfterLast('/'),
            kind = kind,
            language = kind.name,
            depth = path.count { it == '/' },
            path = path,
        )

    @Test
    fun prefersARootIndexHtmlEntry() {
        val tree = listOf(
            file("index.html", FileKind.Html),
            file("about.html", FileKind.Html),
            file("MainActivity.kt", FileKind.Kotlin),
        )
        assertEquals("index.html", webPreviewEntry(tree, "about.html"))
    }

    @Test
    fun fallsBackToTheOpenHtmlFile() {
        val tree = listOf(
            file("about.html", FileKind.Html),
            file("MainActivity.kt", FileKind.Kotlin),
        )
        assertEquals("about.html", webPreviewEntry(tree, "about.html"))
    }

    @Test
    fun kotlinProjectsHaveNoWebEntry() {
        val tree = listOf(file("MainActivity.kt", FileKind.Kotlin))
        assertNull(webPreviewEntry(tree, "MainActivity.kt"))
    }

    @Test
    fun extractsComposeTextAndButtonLabels() {
        val source = """
            @Composable fun Notes() {
              Column {
                Text("My Notes")
                Text("3 notes saved")
                Button(onClick = {}) { Text("New note") }
              }
            }
        """.trimIndent()
        val content = extractComposePreview(source, "NotesScreen.kt")
        assertEquals("My Notes", content.title)
        assertEquals("3 notes saved", content.subtitle)
        assertEquals("New note", content.button)
    }

    @Test
    fun ignoresTextInsideCommentsAndStrings() {
        val source = """
            // Text("not ui")
            val label = "Text(\"not ui\")"
            @Composable fun Screen() { Text("Real") }
        """.trimIndent()
        val content = extractComposePreview(source, "Screen.kt")
        assertEquals("Real", content.title)
    }

    @Test
    fun aWebProjectPreviewsEvenWhenNoFileIsOpen() {
        assertEquals(
            PreviewRoute.Web,
            previewRoute(webEntry = "index.html", projectRootPresent = true, fileName = "", fileKind = null),
        )
    }

    @Test
    fun aWebEntryThatCannotBeServedSaysSoRatherThanAskingForAFile() {
        assertEquals(
            PreviewRoute.WebUnavailable,
            previewRoute(webEntry = "index.html", projectRootPresent = false, fileName = "", fileKind = null),
        )
    }

    @Test
    fun noPageAndNoOpenFileAsksForAFile() {
        assertEquals(
            PreviewRoute.NoFile,
            previewRoute(webEntry = null, projectRootPresent = true, fileName = "  ", fileKind = null),
        )
    }

    @Test
    fun aKotlinFileGetsThePhoneMock() {
        assertEquals(
            PreviewRoute.KotlinMock,
            previewRoute(webEntry = null, projectRootPresent = true, fileName = "MainActivity.kt", fileKind = FileKind.Kotlin),
        )
        // The extension alone is enough: a file kind can be missing and the mock still applies.
        assertEquals(
            PreviewRoute.KotlinMock,
            previewRoute(webEntry = null, projectRootPresent = true, fileName = "Theme.kt", fileKind = null),
        )
    }

    @Test
    fun anythingThatIsNotAPageOrAKotlinFileGetsTheStaticPreview() {
        assertEquals(
            PreviewRoute.Static,
            previewRoute(webEntry = null, projectRootPresent = true, fileName = "data.json", fileKind = FileKind.Json),
        )
    }

    // -- @Preview discovery ----------------------------------------------------

    @Test
    fun previewFunctionsAreFoundInOrder() {
        val source = "@Preview\n@Composable\nfun HomeScreen() {\n}\n\n@Preview\nfun LoginScreen() {\n}\n"
        assertEquals(
            listOf(PreviewFunction("HomeScreen", 3), PreviewFunction("LoginScreen", 7)),
            previewFunctionsOf(source),
        )
    }

    @Test
    fun stackedAnnotationsStillCount() {
        val source = "@Preview\n@MyTheme\nfun A() {\n}\n"
        assertEquals(listOf(PreviewFunction("A", 3)), previewFunctionsOf(source))
    }

    @Test
    fun aLonePreviewWithNoFunctionIsSkipped() {
        assertTrue(previewFunctionsOf("@Preview\n// nothing here\n").isEmpty())
        assertTrue(previewFunctionsOf("fun Plain() {\n}\n").isEmpty())
    }


    // -- Preview error matching --------------------------------------------------

    @Test
    fun anErrorBelongsToTheFunctionAboveIt() {
        val errors = listOf(
            previewError("MainActivity.kt", 12),
            previewError("MainActivity.kt", 40),
        )
        assertEquals(12, previewErrorFor(errors, "MainActivity.kt", 1, 30)?.diagnostic?.line)
        assertEquals(40, previewErrorFor(errors, "MainActivity.kt", 31, 100)?.diagnostic?.line)
    }

    @Test
    fun otherFilesAndWarningsDoNotFailACard() {
        val errors = listOf(
            ProjectDiagnostic(
                "Other.kt",
                com.closecode.lang.Diagnostic(
                    severity = com.closecode.lang.Severity.ERROR,
                    message = "x",
                    offset = 0,
                    length = 1,
                    line = 12,
                    column = 1,
                    code = "E",
                ),
            ),
        )
        assertNull(previewErrorFor(errors, "MainActivity.kt", 1, 100))
    }

    private fun previewError(file: String, line: Int) = ProjectDiagnostic(
        file,
        com.closecode.lang.Diagnostic(
            severity = com.closecode.lang.Severity.ERROR,
            message = "x",
            offset = 0,
            length = 1,
            line = line,
            column = 1,
            code = "E",
        ),
    )

    // -- Function-body scoping -----------------------------------------------------

    private val scopedSource = """
        fun HomeScreen() {
            Text("Welcome back")
            Button(onClick = {}) { Text("Sign in") }
        }
        fun SettingsScreen() {
            Text("Settings")
        }
        fun brokenOne() {
            val x: Int = "oops"
        }
    """.trimIndent()

    @Test
    fun bodiesCoverTheirOwnFunctionOnly() {
        val home = functionBodyOf(scopedSource, 1)!!
        val homeText = scopedSource.substring(home.first, home.last + 1)
        assertTrue(homeText.contains("Welcome back"))
        assertTrue(homeText.contains("Sign in"))
        assertTrue(!homeText.contains("Settings"))
        val settings = functionBodyOf(scopedSource, 5)!!
        assertTrue(!scopedSource.substring(settings.first, settings.last + 1).contains("Sign in"))
    }

    @Test
    fun cardsReadOnlyTheirOwnFunction() {
        val home = functionBodyOf(scopedSource, 1)!!
        val content = extractComposePreview(
            scopedSource.substring(home.first, home.last + 1),
            "HomeScreen",
        )
        assertEquals("Welcome back", content.title)
        assertEquals("Sign in", content.button)
    }

    @Test
    fun aNeighbourErrorDoesNotFailTheCard() {
        val home = functionBodyOf(scopedSource, 1)!!
        val settings = functionBodyOf(scopedSource, 5)!!
        val errors = listOf(previewError("MainActivity.kt", 9))
        fun lines(range: IntRange) =
            lineOfOffset(scopedSource, range.first) to lineOfOffset(scopedSource, range.last)
        val (homeFrom, homeEnd) = lines(home)
        val (setFrom, setEnd) = lines(settings)
        assertTrue(homeEnd < setFrom)
        assertNull(previewErrorFor(errors, "MainActivity.kt", homeFrom, homeEnd))
        assertNull(previewErrorFor(errors, "MainActivity.kt", setFrom, setEnd))
        val broken = functionBodyOf(scopedSource, 8)!!
        val (bFrom, bEnd) = lines(broken)
        assertEquals(9, previewErrorFor(errors, "MainActivity.kt", bFrom, bEnd)?.diagnostic?.line)
    }

    @Test
    fun expressionBodiesHaveNoBody() {
        assertNull(functionBodyOf("fun answer() = 42\n", 1))
        assertNull(functionBodyOf("fun missing() {\n", 1))
    }
}
