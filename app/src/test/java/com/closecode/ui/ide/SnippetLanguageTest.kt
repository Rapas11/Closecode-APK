package com.closecode.ui.ide

import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.PageColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An unknown language must never inherit another language's starter snippet. A .gitkeep is
 * mapped to "Text", which used to fall through to the Kotlin template and display Kotlin code
 * inside a file that contains nothing.
 */
class SnippetLanguageTest {

    private val colors: PageColors = DarkColors

    @Test
    fun unknownLanguagesGetNoSnippet() {
        assertTrue(snippetFor("Text", colors).isEmpty())
        assertTrue(snippetFor("", colors).isEmpty())
        assertTrue(snippetFor("Rust", colors).isEmpty())
    }

    @Test
    fun everyMappedLanguageGetsASnippet() {
        val languages = listOf(
            "Kotlin", "Gradle Kotlin", "XML", "HTML", "JavaScript",
            "CSS", "JSON", "Markdown",
        )
        for (language in languages) {
            assertTrue(
                "$language should have a starter snippet",
                snippetFor(language, colors).isNotEmpty(),
            )
        }
    }

    @Test
    fun kotlinAndGradleAreNotTheSameSnippet() {
        val kotlin = snippetFor("Kotlin", colors).joinToString("\n") { it.text }
        val gradle = snippetFor("Gradle Kotlin", colors).joinToString("\n") { it.text }
        assertTrue(kotlin != gradle)
    }
}