package com.closecode.ui

import com.closecode.ui.ide.ancestorFoldersOf
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.project.kindOf
import com.closecode.ui.project.languageOf
import com.closecode.ui.project.projectEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two presentation bugs found on device:
 *  - an unrecognised file was classified as Gradle, so .gitkeep rendered a red "G"
 *  - red was used decoratively for file-type badges, so red stopped meaning "error"
 *
 * It calls the mapping the drawer actually uses rather than a copy of it: a test that re-implements
 * the table it is checking proves nothing when the table changes.
 */
class FileKindMappingTest {

    @Test
    fun extensionlessFilesAreUnknownNotGradle() {
        assertEquals(FileKind.Unknown, kindOf(".gitkeep"))
        assertEquals(FileKind.Unknown, kindOf("LICENSE"))
        assertEquals(FileKind.Unknown, kindOf("Dockerfile"))
    }

    @Test
    fun knownExtensionsStillResolve() {
        assertEquals(FileKind.Kotlin, kindOf("MainActivity.kt"))
        assertEquals(FileKind.Gradle, kindOf("build.gradle"))
        assertEquals(FileKind.Js, kindOf("main.js"))
        assertEquals(FileKind.Json, kindOf("package.json"))
        assertEquals(FileKind.Xml, kindOf("strings.xml"))
        assertEquals(FileKind.Html, kindOf("index.html"))
        assertEquals(FileKind.Css, kindOf("style.css"))
        assertEquals(FileKind.Markdown, kindOf("README.md"))
    }

    @Test
    fun unknownFilesHaveNoBadgeGlyph() {
        assertEquals("", com.closecode.ui.ide.model.kindGlyph(FileKind.Unknown))
    }

    @Test
    fun aFolderIsAFolderWhateverItIsCalled() {
        val folder = projectEntry("src/main", isFolder = true)

        assertEquals(FileKind.Folder, folder.kind)
        assertEquals("", folder.language)
        assertTrue(folder.expandable)
        assertEquals("main", folder.name)
        assertEquals("src/main", folder.path)
        assertEquals(1, folder.depth)
    }

    @Test
    fun aBuiltRecordReadsTheSameAsTheOneAWalkWouldProduce() {
        val root = projectEntry("MainActivity.kt", isFolder = false)
        assertEquals(
            ProjectFile(
                name = "MainActivity.kt",
                kind = FileKind.Kotlin,
                language = "Kotlin",
                depth = 0,
                expandable = false,
                path = "MainActivity.kt",
            ),
            root,
        )

        val nested = projectEntry("app/src/main/java/Main.kt", isFolder = false)
        assertEquals("Main.kt", nested.name)
        assertEquals(FileKind.Kotlin, nested.kind)
        assertEquals("Kotlin", nested.language)
        assertEquals(4, nested.depth)
        assertEquals("app/src/main/java/Main.kt", nested.path)
    }

    @Test
    fun theLanguageOfEachExtensionMatchesWhatTheEditorSelects() {
        assertEquals("Kotlin", languageOf("Main.kt"))
        assertEquals("Gradle Kotlin", languageOf("build.gradle.kts"))
        assertEquals("XML", languageOf("strings.xml"))
        assertEquals("HTML", languageOf("index.html"))
        assertEquals("Properties", languageOf("gradle.properties"))
        assertEquals("Text", languageOf("LICENSE"))
    }

    @Test
    fun theFoldersThatHoldAPathAreOutermostFirst() {
        assertEquals(emptyList<String>(), ancestorFoldersOf("Main.kt"))
        assertEquals(listOf("app"), ancestorFoldersOf("app/Main.kt"))
        assertEquals(listOf("a", "a/b"), ancestorFoldersOf("a/b/c.kt"))
        assertEquals(
            listOf("app", "app/src", "app/src/main"),
            ancestorFoldersOf("app/src/main/AndroidManifest.xml"),
        )
    }
}
