package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tab completion, away from the keyboard.
 *
 * A shell behind a pipe has no readline, so nothing else will complete a path for the reader. This
 * is all arithmetic over the tree the app already holds, which is why it can be pinned here rather
 * than tried with a thumb.
 */
class TerminalCompletionTest {

    private fun file(name: String, path: String = name, kind: FileKind = FileKind.Kotlin) =
        ProjectFile(name = name, kind = kind, language = "Kotlin", depth = path.count { it == '/' }, path = path)

    private fun folder(name: String, path: String = name) = file(name, path, FileKind.Folder)

    private val tree = listOf(
        folder("app", "app"),
        file("MainActivity.kt", "app/MainActivity.kt"),
        file("Theme.kt", "app/Theme.kt"),
        folder("res", "app/res"),
        file("strings.xml", "app/res/strings.xml"),
        file("build.gradle.kts"),
        file("README.md", "README.md", FileKind.Markdown),
        folder(".git", ".git"),
    )

    // -- The word under the caret ---------------------------------------------

    @Test
    fun theWordUnderTheCaretIsFound() {
        assertEquals(3 to "grep", wordAt("ls grep", 7))
    }

    @Test
    fun theWordStopsAtWhitespace() {
        assertEquals(0 to "ls", wordAt("ls  ", 2))
    }

    @Test
    fun anEmptyLineHasNoWord() {
        assertEquals(0 to "", wordAt("", 0))
    }

    @Test
    fun aCaretPastTheEndIsClampedRatherThanThrowing() {
        assertEquals(0 to "ls", wordAt("ls", 99))
    }

    // -- Splitting a path ------------------------------------------------------

    @Test
    fun aBareNameHasNoDirectory() {
        assertEquals("" to "Mai", splitPath("Mai"))
    }

    @Test
    fun aPathSplitsAtItsLastSeparator() {
        assertEquals("app/" to "Main", splitPath("app/Main"))
        assertEquals("app/res/" to "str", splitPath("app/res/str"))
    }

    @Test
    fun aTrailingSeparatorLeavesAnEmptyNameToComplete() {
        assertEquals("app/" to "", splitPath("app/"))
    }

    // -- Resolving against a working directory ---------------------------------

    @Test
    fun aRelativePathIsResolvedAgainstWhereTheShellIs() {
        assertEquals("app/res", resolvePath("app", "res"))
        assertEquals("a/b/c", resolvePath("a/b", "c"))
    }

    @Test
    fun anAbsolutePathIgnoresTheWorkingDirectory() {
        assertEquals("etc", resolvePath("app/res", "/etc"))
    }

    @Test
    fun parentSegmentsAreCollapsed() {
        assertEquals("a", resolvePath("a/b", ".."))
        assertEquals("a/c", resolvePath("a/b", "../c"))
        assertEquals("a/b", resolvePath("a/b", "."))
    }

    @Test
    fun goingUpMoreFarThanThereIsStaysAtTheTop() {
        // `cd ../..` from two deep must not climb above the project; an empty answer is the honest
        // one, and leaving it is better than pointing completion at a directory that is not there.
        assertEquals("", normalisePath("../../.."))
    }

    @Test
    fun aPathInsideTheProjectBecomesProjectRelative() {
        assertEquals("app/res", projectRelative("/data/blankapp", "/data/blankapp/app/res"))
    }

    @Test
    fun aPathOutsideTheProjectIsNotCompletedAgainstIt() {
        assertNull(projectRelative("/data/blankapp", "/data/other/app"))
        assertNull(projectRelative("/data/blankapp", "/etc"))
    }

    // -- Following cd ----------------------------------------------------------

    @Test
    fun cdMovesTheWorkingDirectoryTheAppIsWatching() {
        assertEquals("app", cwdAfterCommand("", "", "cd app"))
        assertEquals("app/res", cwdAfterCommand("app", "", "cd res"))
    }

    @Test
    fun cdOnItsOwnGoesHomeToTheProject() {
        assertEquals("", cwdAfterCommand("app/res", "", "cd"))
        assertEquals("", cwdAfterCommand("app", "", "cd ~"))
    }

    @Test
    fun cdParentGoesUp() {
        assertEquals("app", cwdAfterCommand("app/res", "", "cd .."))
    }

    @Test
    fun aCommandThatIsNotCdLeavesTheDirectoryAlone() {
        assertNull(cwdAfterCommand("app", "", "ls"))
        assertNull(cwdAfterCommand("app", "", "echo hi"))
    }

    @Test
    fun cdFlagsAreNotFollowed() {
        // `-` reads the shell's own previous-directory variable, which the app cannot see.
        assertNull(cwdAfterCommand("app", "", "cd -"))
        assertNull(cwdAfterCommand("app", "", "cd -P"))
    }

    // -- Finding candidates ----------------------------------------------------

    @Test
    fun candidatesComeFromTheDirectoryBeingTyped() {
        val names = completionCandidates("app/", "", tree).map { it.name }
        assertTrue(names.toString(), names.containsAll(listOf("MainActivity.kt", "Theme.kt", "res")))
    }

    @Test
    fun onlyTheLeavesOfThatDirectoryAreOffered() {
        // `strings.xml` lives under app/res, so it is not something that can be named in app.
        val names = completionCandidates("app/", "", tree).map { it.name }
        assertTrue(names.toString(), !names.contains("strings.xml"))
    }

    @Test
    fun aFolderIsMarkedAsOne() {
        val candidates = completionCandidates("app/", "", tree)
        assertTrue(candidates.first { it.name == "res" }.isFolder)
        assertTrue(!candidates.first { it.name == "Theme.kt" }.isFolder)
    }

    @Test
    fun completionNarrowsAsMoreIsTyped() {
        val names = completionCandidates("app/", "The", tree).map { it.name }
        assertEquals(listOf("Theme.kt"), names)
    }

    @Test
    fun theGitDirectoryIsNeverOffered() {
        // It is a real directory, never what anyone is reaching for, and walking into it produces
        // paths the shell cannot usefully do anything with.
        val names = completionCandidates("", ".g", tree).map { it.name }
        assertTrue(names.toString(), !names.contains(".git"))
    }

    @Test
    fun aDirectoryWithNothingInItCompletesToNothing() {
        assertEquals(emptyList<Completion>(), completionCandidates("app/res/drawable/", "", tree))
    }

    @Test
    fun theProjectRootCompletesToItsOwnEntries() {
        val names = completionCandidates("", "", tree).map { it.name }
        assertTrue(names.toString(), names.containsAll(listOf("app", "README.md", "build.gradle.kts")))
    }

@Test
    fun candidatesComeBackInAFixedOrderSoTheListNeverReordersItself() {
        // Two Taps in a row must not show the reader the same names in a different order, which is
        // what makes a cycling list feel like a bug rather than a list.
        val names = completionCandidates("app/", "", tree).map { it.name }
        assertEquals(names.sorted(), names)
        // And the order cannot depend on the order the tree happened to be walked in.
        assertEquals(names, completionCandidates("app/", "", tree.reversed()).map { it.name })
    }

    @Test
    fun twoEntriesWithTheSameNameAreOfferedOnce() {
        val dupes = tree + file("Theme.kt", "lib/Theme.kt")
        val names = completionCandidates("app/", "The", dupes).map { it.name }
        assertEquals(listOf("Theme.kt"), names)
    }

    // -- The shared prefix -----------------------------------------------------

    @Test
    fun oneCandidateIsItsOwnPrefix() {
        val candidates = completionCandidates("app/", "The", tree)
        assertEquals("Theme.kt", commonPrefixOf(candidates))
    }

    @Test
    fun candidatesThatShareNothingAddNothing() {
        // An empty prefix is the signal that there is nothing useful to add, and the caller turns it
        // into showing the list rather than into a Tab that silently does nothing.
        assertEquals("ap", commonPrefixOf(listOf(Completion("apple", false), Completion("apricot", false))))
        assertEquals("", commonPrefixOf(listOf(Completion("apple", false), Completion("banana", false))))
        assertEquals("", commonPrefixOf(emptyList()))
    }

    @Test
    fun candidatesAreJoinedByWhateverTheyShare() {
        val candidates = listOf(Completion("Theme.kt", false), Completion("Themes.kt", false))
        assertEquals("Theme", commonPrefixOf(candidates))
    }

    @Test
    fun aSharedPrefixThatGoesBackwardsStopsThere() {
        // "app" and "application" share "app", but "apricot" shares nothing — and the prefix must not
        // be grown on the strength of the first two alone.
        val candidates = listOf(Completion("app", true), Completion("apricot", false))
        assertEquals("ap", commonPrefixOf(candidates))
    }

    // -- What a Tab inserts ----------------------------------------------------

    @Test
    fun theFirstTabExtendsTheWordAsFarAsItCan() {
        val candidates = listOf(Completion("Theme.kt", false), Completion("Themes.kt", false))
        assertEquals("Theme", nextCompletion("The", candidates, 0))
    }

    @Test
    fun aFirstTabWithNothingToExtendOffersTheListRatherThanGuessing() {
        val candidates = listOf(Completion("apple", false), Completion("apricot", false))
        assertNull(nextCompletion("ap", candidates, 0))
    }

    @Test
    fun aLaterTabCyclesThroughTheCandidates() {
        val candidates = listOf(Completion("apple", false), Completion("apricot", false))
        assertEquals("apple", nextCompletion("ap", candidates, 1))
        assertEquals("apricot", nextCompletion("ap", candidates, 2))
    }

    @Test
    fun cyclingWrapsBackAroundToTheFirst() {
        val candidates = listOf(Completion("apple", false), Completion("apricot", false))
        assertEquals("apple", nextCompletion("ap", candidates, 3))
    }

    @Test
    fun oneCandidateIsFinishedInOneTab() {
        val candidates = completionCandidates("app/", "The", tree)
        assertEquals("Theme.kt", nextCompletion("The", candidates, 0))
    }

    @Test
    fun aFolderGetsATrailingSlashSoThereIsMoreToCome() {
        val candidates = listOf(Completion("res", true))
        assertEquals("res/", nextCompletion("r", candidates, 0))
    }

    @Test
    fun aFileDoesNotGetATrailingSlash() {
        val candidates = listOf(Completion("Theme.kt", false))
        assertEquals("Theme.kt", nextCompletion("The", candidates, 0))
    }

    @Test
    fun nothingToCompleteInsertsNothing() {
        assertNull(nextCompletion("zz", emptyList(), 0))
    }

    @Test
    fun theLabelsShownAreTheNamesWithSlashesOnFolders() {
        val labels = completionLabels(listOf(Completion("res", true), Completion("Theme.kt", false)))
        assertEquals(listOf("res/", "Theme.kt"), labels)
    }

    // -- The saved-commands shelf ------------------------------------------------

    @Test
    fun everyShelfEntryIsTypable() {
        // Nothing on the shelf is allowed to be decorative: every entry is a line the shell
        // can take, typed at the prompt without running so it can be edited first.
        for (command in DefaultTerminalCommands) {
            assertTrue(command, command.isNotBlank())
            assertTrue(command, '\n' !in command)
        }
    }

    @Test
    fun theShelfOpensWithTheWorkingSet() {
        assertEquals(
            listOf("ls -la", "pwd", "ps", "clear", "kill", "git status", "git diff"),
            DefaultTerminalCommands,
        )
    }

    @Test
    fun aCommandIsNeverEmpty() {
        assertTrue(DefaultTerminalCommands.isNotEmpty())
    }

    // -- code ------------------------------------------------------------------

    @Test
    fun codeNamesTheFileItShouldOpen() {
        assertEquals("app/Theme.kt", codeTarget("code app/Theme.kt"))
    }

    @Test
    fun codeToleratesQuotedPaths() {
        // A path with a space is the one case somebody reaches for quotes, and a chip that silently
        // failed on those would be worse than no chip.
        assertEquals("my docs/notes.md", codeTarget("code \"my docs/notes.md\""))
        assertEquals("my docs/notes.md", codeTarget("code 'my docs/notes.md'"))
    }

    @Test
    fun codeOnItsOwnNamesNothing() {
        assertNull(codeTarget("code"))
        assertNull(codeTarget("code   "))
    }

    @Test
    fun anOrdinaryCommandIsNotCode() {
        assertNull(codeTarget("ls -la"))
        assertNull(codeTarget("echo code"))
        // Only the whole first word counts: a command that merely starts with those letters is not it.
        assertNull(codeTarget("codes something"))
        assertNull(codeTarget("encode x"))
    }

    @Test
    fun codeFindsTheFileItNames() {
        val found = codeTargetFile(tree, "", "app/Theme.kt")
        assertEquals("app/Theme.kt", found?.path)
    }

    @Test
    fun codeResolvesFromWhereverTheShellIs() {
        val found = codeTargetFile(tree, "app", "res/strings.xml")
        assertEquals("app/res/strings.xml", found?.path)
    }

    @Test
    fun codeAcceptsAnAbsolutePath() {
        assertEquals("README.md", codeTargetFile(tree, "app", "/README.md")?.path)
    }

    @Test
    fun codeRefusesAFolderBecauseThereIsNothingToOpen() {
        assertNull(codeTargetFile(tree, "", "app"))
        assertNull(codeTargetFile(tree, "", "app/res"))
    }

    @Test
    fun codeSaysSoWhenTheFileIsNotThere() {
        // The alternative is running `code` in the shell, which would report that no such command
        // exists — technically true and completely useless.
        assertNull(codeTargetFile(tree, "", "nope.kt"))
        assertNull(codeTargetFile(emptyList(), "", "anything.kt"))
    }
}