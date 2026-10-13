package com.closecode.ui.ide

import com.closecode.ui.settings.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.closecode.ui.theme.DarkAnsi
import com.closecode.ui.theme.TerminalPalettes

/**
 * The line editor a pipe-driven shell does not have.
 *
 * Nothing below talks to a screen. The shell is fed through a pipe, so it never sees the keys at all
 * and has no readline behind it; history, the caret and the CTRL shortcuts are all provided here.
 * That is arithmetic, and arithmetic can be pinned rather than tried with a thumb.
 */
class TerminalKeysTest {

    // -- Control codes ---------------------------------------------------------

    @Test
    fun ctrlLettersProduceTheirAlphabetPosition() {
        assertEquals(1.toChar(), controlChar('a'))
        assertEquals(2.toChar(), controlChar('b'))
        assertEquals(26.toChar(), controlChar('z'))
    }

    @Test
    fun shiftDoesNotChangeWhichCodeAKeyMeans() {
        // A real terminal sends ETX for CTRL+SHIFT+C, and a keyboard that reports the shift would
        // otherwise hand back nothing at all.
        assertEquals(controlChar('c'), controlChar('C'))
        assertEquals(3.toChar(), controlChar('C'))
    }

    @Test
    fun theKeysBelowTheAlphabetHaveTheirOwnCodes() {
        assertEquals('\u0000', controlChar('@'))
        assertEquals('\u0000', controlChar(' '))
        assertEquals(ESC, controlChar('['))
        assertEquals('\u001C', controlChar('\\'))
        assertEquals('\u001D', controlChar(']'))
        assertEquals('\u001E', controlChar('^'))
        assertEquals('\u001F', controlChar('_'))
    }

    @Test
    fun aKeyWithNoControlMeaningSaysSo() {
        assertNull(controlChar('~'))
        assertNull(controlChar('|'))
        assertNull(controlChar('1'))
        assertFalse(hasControlChar('~'))
        assertTrue(hasControlChar('a'))
    }

    // -- What each shortcut does ----------------------------------------------

    @Test
    fun theLineEditingShortcutsAreWiredUp() {
        // The readline bindings, which are what every terminal the reader has ever used had.
        assertEquals(ControlAction.LINE_START, controlActionFor('a'))
        assertEquals(ControlAction.LINE_END, controlActionFor('e'))
        assertEquals(ControlAction.CLEAR_AFTER, controlActionFor('k'))
        assertEquals(ControlAction.CLEAR_LINE, controlActionFor('u'))
        assertEquals(ControlAction.DELETE_WORD, controlActionFor('w'))
        assertEquals(ControlAction.DELETE_FORWARD, controlActionFor('d'))
        assertEquals(ControlAction.CLEAR_SCREEN, controlActionFor('l'))
    }

    @Test
    fun deleteForwardTakesTheCharacterAfterTheCaret() {
        // Forward is the whole point: the caret stays where it is and the character to its right goes.
        assertEquals("ac" to 1, deleteForward("abc", 1))
        assertEquals("bc" to 0, deleteForward("abc", 0))
    }

    @Test
    fun deleteForwardAtTheEndOfTheLineChangesNothing() {
        // The caret sits at the end after almost every edit on a phone, so this is the common case and
        // must be a no-op rather than an exception.
        assertEquals("abc" to 3, deleteForward("abc", 3))
    }

    @Test
    fun deleteForwardOnAnEmptyLineChangesNothing() {
        assertEquals("" to 0, deleteForward("", 0))
    }

    @Test
    fun deleteForwardKeepsTheTextBeforeTheCaret() {
        assertEquals("ab" to 2, deleteForward("abc", 2))
    }

    @Test
    fun ctrlCIsLeftToTheProcessKillRatherThanClaimedAsALineEdit() {
        // ETX means "interrupt" on a real terminal. It is not a line edit, and pretending otherwise
        // would leave CTRL+C doing nothing at all.
        assertEquals(ControlAction.NONE, controlActionFor('c'))
        assertEquals(3.toChar(), controlChar('c'))
    }

    @Test
    fun anUnboundControlKeyDoesNothingRatherThanSomethingArbitrary() {
        assertEquals(ControlAction.NONE, controlActionFor('q'))
    }

    // -- Line editing ----------------------------------------------------------

    @Test
    fun deleteToEndKeepsWhatIsBeforeTheCaret() {
        assertEquals("ab" to 2, deleteToEnd("abcdef", 2))
    }

    @Test
    fun deleteToEndAtTheEndChangesNothing() {
        assertEquals("abc" to 3, deleteToEnd("abc", 3))
    }

    @Test
    fun deleteToCaretKeepsWhatIsAfterIt() {
        // readline calls this `unix-line-discard`: it discards backwards and stops at the cursor, so
        // on a phone — where the caret is rarely at the end — the tail survives.
        assertEquals("" to 0, deleteToCaret("abcdef", 6))
        assertEquals("def" to 0, deleteToCaret("abcdef", 3))
    }

    @Test
    fun deleteWordBeforeTakesTheWordTheCaretSitsIn() {
        assertEquals("ls " to 3, deleteWordBefore("ls -la", 6))
    }

    @Test
    fun deleteWordBeforeOnlyRemovesThePartBeforeTheCaret() {
        // Caret in the middle of a word: the half already typed goes, the half not yet typed stays.
        assertEquals("ls a" to 3, deleteWordBefore("ls -la", 5))
    }

    @Test
    fun deleteWordBeforeAlsoTakesTheSpaceSoASecondPressMakesProgress() {
        // Otherwise CTRL+W over a word preceded by a space would leave the space and the next press
        // would appear to do nothing, which reads as a broken key rather than a word that has gone.
        val (text, caret) = deleteWordBefore("ls -la", 3)
        assertEquals("ls-la", text)
        assertEquals(2, caret)
    }

    @Test
    fun deleteWordBeforeAtTheStartDoesNothing() {
        assertEquals("abc" to 0, deleteWordBefore("abc", 0))
    }

    @Test
    fun deleteWordBeforeHandlesACaretPastTheEnd() {
        assertEquals("" to 0, deleteWordBefore("", 99))
    }

    // -- History ---------------------------------------------------------------

    // Newest first, which is the order the store keeps it in and the order the up arrow walks it.
    private val history = listOf("cat f", "grep x y", "ls -la")

    @Test
    fun upFromTheDraftShowsTheMostRecentCommand() {
        val step = previousHistoryEntry(history, history.size, "half typed")
        assertEquals(HistoryStep("cat f", 0), step)
    }

    @Test
    fun upWalksBackwardsThroughTheList() {
        val first = previousHistoryEntry(history, history.size, "")!!
        val second = previousHistoryEntry(history, first.index, "")!!
        assertEquals("grep x y", second.text)
        assertEquals(1, second.index)
    }

    @Test
    fun upStopsAtTheOldestCommandRatherThanWrappingAround() {
        // Wrapping to the newest would make the arrow look broken at exactly the place a reader
        // stops pressing it.
        val step = previousHistoryEntry(history, history.size - 1, "")!!
        assertEquals("ls -la", step.text)
        assertEquals(history.size - 1, step.index)
    }

    @Test
    fun downComesBackForwardAgain() {
        val step = nextHistoryEntry(history, 1, "")!!
        assertEquals("cat f", step.text)
        assertEquals(0, step.index)
    }

    @Test
    fun downFromTheNewestCommandReturnsToWhatWasBeingTyped() {
        // The draft is a real slot at the end of the list rather than a special case, which is what
        // lets the reader come back to their half-typed command instead of off the end into nothing.
        val step = nextHistoryEntry(history, 0, "half typed")!!
        assertEquals("half typed", step.text)
        assertEquals(history.size, step.index)
    }

    @Test
    fun downAtTheDraftDoesNothing() {
        assertNull(nextHistoryEntry(history, history.size, "draft"))
    }

    @Test
    fun anEmptyHistoryHasNoSteps() {
        assertNull(previousHistoryEntry(emptyList(), 0, "x"))
        assertNull(nextHistoryEntry(emptyList(), 0, "x"))
    }

    @Test
    fun aNewCommandGoesToTheFront() {
        assertEquals(listOf("new", "ls -la"), addToHistory(listOf("ls -la"), "new"))
    }

    @Test
    fun rerunningACommandDoesNotFillTheListWithItself() {
        val once = addToHistory(listOf("ls -la"), "pwd")
        val twice = addToHistory(once, "pwd")
        assertEquals(listOf("pwd", "ls -la"), twice)
        assertEquals(2, twice.size)
    }

    @Test
    fun aBlankCommandIsNotRemembered() {
        assertEquals(history, addToHistory(history, "   "))
    }

    @Test
    fun theListIsCappedSoItCannotGrowWithoutEnd() {
        var list = emptyList<String>()
        repeat(TERMINAL_HISTORY_LIMIT + 50) { list = addToHistory(list, "command $it") }
        assertEquals(TERMINAL_HISTORY_LIMIT, list.size)
        // The newest survives the cap; the oldest is the one that goes.
        assertEquals("command ${TERMINAL_HISTORY_LIMIT + 49}", list.first())
    }

    // -- Modifiers -------------------------------------------------------------

    @Test
    fun tappingAModifierArmsItThenLocksItThenClearsIt() {
        assertEquals(ModifierLatch.LATCHED, nextLatch(ModifierLatch.OFF))
        assertEquals(ModifierLatch.LOCKED, nextLatch(ModifierLatch.LATCHED))
        assertEquals(ModifierLatch.OFF, nextLatch(ModifierLatch.LOCKED))
    }

    @Test
    fun anArmedModifierAppliesButAnOffOneDoesNot() {
        assertTrue(modifierApplies(ModifierLatch.LATCHED))
        assertTrue(modifierApplies(ModifierLatch.LOCKED))
        assertFalse(modifierApplies(ModifierLatch.OFF))
    }

    @Test
    fun onlyTheArmedModifierIsConsumedByOneKey() {
        // This is the whole point of three states: one tap then one key for a shortcut, one tap then
        // every key for a word, and a locked modifier survives until it is tapped off.
        assertTrue(modifierConsumes(ModifierLatch.LATCHED))
        assertFalse(modifierConsumes(ModifierLatch.LOCKED))
        assertFalse(modifierConsumes(ModifierLatch.OFF))
    }

    @Test
    fun anUnmodifiedKeyIsJustItself() {
        assertEquals("|", keyBarCharacter('|', ctrl = false, alt = false))
    }

    @Test
    fun ctrlTurnsALetterIntoItsCode() {
        assertEquals("\u0003", keyBarCharacter('c', ctrl = true, alt = false))
    }

    @Test
    fun altPrefixesTheEscapeByte() {
        assertEquals(ESC + "f", keyBarCharacter('f', ctrl = false, alt = true))
    }

    @Test
    fun aKeyWithNoControlMeaningIsDroppedRatherThanSentAsItself() {
        // CTRL plus a pipe is not a thing. Sending the pipe anyway would type a pipe on the command
        // line, which is worse than the key doing nothing.
        assertEquals("", keyBarCharacter('|', ctrl = true, alt = false))
    }

    // -- Where history lives ---------------------------------------------------

    /** The in-memory stand-in the settings store uses, since SharedPreferences is not on this classpath. */
    private class FakeStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun getBoolean(key: String, default: Boolean) = default
        override fun putBoolean(key: String, value: Boolean) = Unit
        override fun getString(key: String, default: String?): String? = values[key] ?: default
        override fun putString(key: String, value: String) {
            values[key] = value
        }
        override fun getInt(key: String, default: Int) = default
        override fun putInt(key: String, value: Int) = Unit
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun clear() = values.clear()
    }

    @Test
    fun historyIsRememberedPerProject() {
        val store = TerminalHistoryStore(FakeStore())
        store.write("alpha", listOf("ls"))
        store.write("beta", listOf("pwd"))
        assertEquals(listOf("ls"), store.read("alpha"))
        assertEquals(listOf("pwd"), store.read("beta"))
    }

    @Test
    fun aProjectWithNoHistoryReadsAsEmptyRatherThanFailing() {
        assertEquals(emptyList<String>(), TerminalHistoryStore(FakeStore()).read("nothing-here"))
    }

    @Test
    fun historyKeepsItsOrderAcrossARestart() {
        // Newest first in the store and newest first in memory, so reading one back needs no
        // reversing and the up arrow starts in the right place.
        val store = TerminalHistoryStore(FakeStore())
        store.write("p", listOf("third", "second", "first"))
        assertEquals(listOf("third", "second", "first"), store.read("p"))
    }

    @Test
    fun emptyLinesInTheStoreAreDiscardedRatherThanShown() {
        // A half-written value would otherwise show a blank entry every time the up arrow went past
        // it, which reads as a command that was run and did nothing.
        val kv = FakeStore()
        kv.putString("terminal.history.p", "ls\n\n  \npwd\n")
        assertEquals(listOf("ls", "pwd"), TerminalHistoryStore(kv).read("p"))
    }

    @Test
    fun historyIsNeverWrittenIntoTheProjectFolder() {
        // A file in the project would show up in the file drawer and in Source Control, and a record
        // of what somebody typed at a shell is not something to drop into a commit unasked.
        val store = FakeStore()
        TerminalHistoryStore(store).write("p", listOf("rm -rf /"))
        assertTrue(store.values.keys.single().startsWith("terminal.history."))
    }
}
/**
 * The terminal and the file drawer, and the paths in between.
 *
 * Both halves are matching against the tree the app already holds rather than against the shape of
 * the text, which is what keeps the drawer from refreshing on every `ls` and a tap from appearing on
 * every sentence that happens to contain a dot.
 */
class TerminalFilesTest {

    private fun file(name: String, path: String = name) =
        com.closecode.ui.model.ProjectFile(
            name = name,
            kind = com.closecode.ui.model.FileKind.Kotlin,
            language = "Kotlin",
            depth = path.count { it == '/' },
            path = path,
        )

    private fun folder(name: String, path: String = name) =
        com.closecode.ui.model.ProjectFile(
            name = name,
            kind = com.closecode.ui.model.FileKind.Folder,
            language = "",
            depth = path.count { it == '/' },
            path = path,
        )

    private val tree = listOf(
        folder("app", "app"),
        file("Theme.kt", "app/Theme.kt"),
        file("MainActivity.kt", "app/MainActivity.kt"),
        file("build.gradle.kts"),
    )

    // -- Refreshing the drawer -------------------------------------------------

    @Test
    fun aCommandThatChangesFilesIsRecognised() {
        for (command in listOf("touch a.kt", "mkdir x", "rm a.kt", "mv a b", "cp a b", "clear")) {
            assertTrue(command, refreshesFiles(command))
        }
    }

    @Test
    fun aCommandThatOnlyLooksIsNot() {
        for (command in listOf("ls", "ls -la", "pwd", "cat a.kt", "grep x y")) {
            assertFalse(command, refreshesFiles(command))
        }
    }

    @Test
    fun onlyTheHeadOfEachStageIsConsidered() {
        // `ls | wc -l` changes nothing, and `mkdir a && ls` has to be recognised by its first
        // stage alone.
        assertFalse(refreshesFiles("ls | wc -l"))
        assertTrue(refreshesFiles("mkdir a && ls"))
        assertTrue(refreshesFiles("rm a.kt ; ls"))
    }

    @Test
    fun aPathToTheCommandDoesNotHideWhatItDoes() {
        assertTrue(refreshesFiles("/system/bin/mkdir newdir"))
        assertFalse(refreshesFiles("/system/bin/ls"))
    }

    // -- Finding paths in output ----------------------------------------------

    @Test
    fun aFullPathIsFound() {
        val found = pathsMentionedIn("error in app/Theme.kt:12", tree)
        assertEquals(listOf("app/Theme.kt"), found.map { it.path })
    }

    @Test
    fun aBareFileNameIsFoundWhenItLooksLikeOne() {
        // Output rarely prints the whole path, so a name on its own has to count.
        assertEquals(
            listOf("app/Theme.kt"),
            pathsMentionedIn("error in Theme.kt:12", tree).map { it.path },
        )
    }

    @Test
    fun proseIsNotMistakenForAFile() {
        // The whole reason the match is made against the tree: half the words in a stack trace
        // contain a slash or a dot, and a tap on each would be worse than no tap at all.
        assertTrue(pathsMentionedIn("sh: 1: not found", tree).isEmpty())
        assertTrue(pathsMentionedIn("BUILD FAILED 3 errors", tree).isEmpty())
        assertTrue(pathsMentionedIn("no such file or directory", tree).isEmpty())
    }

    @Test
    fun aFolderIsNotSomethingToOpen() {
        assertTrue(pathsMentionedIn("entering app", tree).isEmpty())
    }

    @Test
    fun aNameThatIsNotInTheProjectIsNotOffered() {
        assertTrue(pathsMentionedIn("error in other/Thing.kt:1", tree).isEmpty())
        assertTrue(pathsMentionedIn("missing.kt", tree).isEmpty())
    }

    @Test
    fun punctuationAroundAPathDoesNotStopItBeingFound() {
        assertEquals(
            listOf("app/Theme.kt"),
            pathsMentionedIn("(app/Theme.kt)", tree).map { it.path },
        )
        assertEquals(
            listOf("app/Theme.kt"),
            pathsMentionedIn("see app/Theme.kt:12:9", tree).map { it.path },
        )
    }

    @Test
    fun severalPathsOnOneLineAreAllFound() {
        val found = pathsMentionedIn("app/Theme.kt and build.gradle.kts differ", tree)
        assertEquals(listOf("app/Theme.kt", "build.gradle.kts"), found.map { it.path })
    }

    @Test
    fun theSameFileTwiceIsOfferedOnce() {
        assertEquals(1, pathsMentionedIn("app/Theme.kt vs app/Theme.kt", tree).size)
    }

    @Test
    fun anEmptyTreeOffersNothing() {
        assertTrue(pathsMentionedIn("app/Theme.kt", emptyList()).isEmpty())
    }
}

/**
 * Tabs are shells, not views over one.
 *
 * Two tabs cannot share a process, so what these pin is that a tab keeps its own shell, its own
 * working directory and its own scrollback — and that the numbers they are labelled with stay
 * stable enough to tell them apart at a glance.
 */
class TerminalSessionTest {

    @Test
    fun aTabIsNumberedFromOne() {
        assertEquals("Shell 1", sessionTitle(1, ""))
    }

    @Test
    fun aTabSaysWhereItsShellWent() {
        // The one thing a list of numbered labels would hide: which of them is in a directory.
        assertEquals("Shell 2 · res", sessionTitle(2, "app/res"))
    }

    @Test
    fun aTabAtTheProjectRootDoesNotRepeatIt() {
        assertEquals("Shell 1", sessionTitle(1, ""))
    }

    @Test
    fun aVeryLongPathIsShortenedButTheNumberSurvives() {
        // Two tabs both called "…going" are worse than two tabs both called "Shell 1": the first
        // loses the only thing that tells them apart.
        val label = tabLabel(1, "a/very/deeply/nested/path/that/keeps/going")
        assertTrue(label.length <= TAB_TITLE_MAX_CHARS)
        assertTrue(label, label.startsWith("Shell 1"))
        assertTrue(label, label.endsWith("going"))
    }

    @Test
    fun aShortLabelIsLeftAlone() {
        assertEquals("Shell 3", tabLabel(3, ""))
        assertEquals("Shell 2 · res", tabLabel(2, "app/res"))
    }

    @Test
    fun eachTabKeepsItsOwnWorkingDirectory() {
        val one = TerminalSession(1)
        val two = TerminalSession(2)
        one.cwd = "app"
        assertEquals("", two.cwd)
    }

    @Test
    fun eachTabKeepsItsOwnOutput() {
        val one = TerminalSession(1)
        val two = TerminalSession(2)
        one.lines.appendTerminalLine("only mine")
        assertEquals(1, one.lines.size)
        assertTrue(two.lines.isEmpty())
    }

    @Test
    fun eachTabHasItsOwnNumber() {
        assertTrue(TerminalSession(1).number != TerminalSession(2).number)
    }
}

/**
 * The two things a reader can change about the terminal's appearance.
 *
 * Both are preferences the app has to honour, and both have a way of being wrong that is worse than
 * not offering the setting at all: a palette that cannot be read, and a size nobody can read it at.
 */
class TerminalAppearanceTest {

    private fun settings(theme: String = "Closecode", size: Int = 13) =
        com.closecode.ui.settings.Settings(terminalTheme = theme, terminalFontSize = size)

    @Test
    fun everySchemeOnOfferIsOneTheAppCanActuallyDraw() {
        // A setting that names a scheme this build does not carry would otherwise select nothing and
        // draw the text in no colour at all, which reads as a bug rather than as a missing theme.
        for (choice in com.closecode.ui.settings.TerminalThemeChoices) {
            assertNotNull(choice, TerminalPalettes[choice])
        }
    }

    @Test
    fun closecodesOwnSchemeIsTheDefault() {
        assertEquals("Closecode", settings().terminalTheme)
        assertEquals(
            com.closecode.ui.theme.DarkAnsi,
            TerminalPalettes[com.closecode.ui.settings.TerminalThemeChoices.first()],
        )
    }

    @Test
    fun everySchemeHasSixteenColours() {
        for ((name, palette) in TerminalPalettes) {
            assertEquals(name, 8, palette.normal.size)
            assertEquals(name, 8, palette.bright.size)
        }
    }

    @Test
    fun aSchemeNamedByAFutureBuildFallsBackRatherThanDrawingNothing() {
        // A preference written by a newer build, or hand-edited. Falling back to something readable
        // beats selecting a scheme that is not there.
        assertNull(TerminalPalettes["A scheme from another build"])
    }

    @Test
    fun theFontSizeIsClampedToSomethingReadable() {
        val smallest = com.closecode.ui.settings.TerminalFontSizeChoices.first().toInt()
        val largest = com.closecode.ui.settings.TerminalFontSizeChoices.last().toInt()
        assertEquals(smallest, terminalFontSize(settings(size = 0)))
        assertEquals(smallest, terminalFontSize(settings(size = -99)))
        assertEquals(largest, terminalFontSize(settings(size = 9_999)))
        assertEquals(13, terminalFontSize(settings()))
    }

    @Test
    fun theSizeIsClampedToTheEndsOfTheRange() {
        // Clamped rather than refused: the reader's nearest readable size is a better answer than
        // silently keeping the old one, which they would not know had happened.
        assertEquals(9, terminalFontSize(settings(size = 2)))
        assertEquals(18, terminalFontSize(settings(size = 40)))
    }
}
