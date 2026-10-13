package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the inline prompt's pieces: the directory it names, the echo split that recolors it,
 * the custom tab names, and the saved-commands shelf.
 */
class TerminalPromptTest {

    @Test
    fun promptNamesTheProjectAndFolder() {
        assertEquals("~/Landing page", displayDir("Landing page", ""))
        assertEquals("~/Landing page/assets", displayDir("Landing page", "assets"))
        assertEquals("~/project", displayDir("", ""))
    }

    @Test
    fun prefixIsDirDollarSpace() {
        assertEquals("~/Landing page $ ", promptPrefix("~/Landing page"))
    }

    @Test
    fun echoesSplitIntoDirAndCommand() {
        val split = splitPrompt("~/Landing page $ ls -la")
        assertEquals("~/Landing page", split?.first)
        assertEquals("ls -la", split?.second)
    }

    @Test
    fun ordinaryOutputDoesNotSplit() {
        assertNull(splitPrompt("total 12"))
        assertNull(splitPrompt("closecode $ ls"))
        assertNull(splitPrompt("~/Landing page"))
    }

    @Test
    fun aCustomNameWinsOverTheGeneratedLabel() {
        assertEquals("server", tabLabel(1, "assets", "server"))
        assertEquals("Shell 1", tabLabel(1, "", "   "))
        assertEquals("Shell 2 · res", tabLabel(2, "app/res"))
    }

    @Test
    fun commandsRoundTripThroughTheStore() {
        val commands = listOf("ls -la", "git status", "echo a b")
        assertEquals(commands, decodeCommands(encodeCommands(commands)))
        assertTrue(decodeCommands(null).isEmpty())
        assertTrue(decodeCommands("  ").isEmpty())
    }

    @Test
    fun defaultShelfHasNoGitBinariesButListsGit() {
        // The shell has no git binary, so git entries are typed for the drawer, never run —
        // and the shelf documents the seven it offers.
        assertEquals(
            listOf("ls -la", "pwd", "ps", "clear", "kill", "git status", "git diff"),
            DefaultTerminalCommands,
        )
    }

    // -- Failure lines ---------------------------------------------------------

    @Test
    fun shellComplaintsReadAsErrors() {
        assertTrue(isErrorLine("sh: foo: not found"))
        assertTrue(isErrorLine("fatal: not a git repository"))
        assertTrue(isErrorLine("ls: No such file or directory"))
        assertTrue(isErrorLine("Permission denied"))
    }

    @Test
    fun ordinaryOutputStaysPlain() {
        assertTrue(!isErrorLine("total 12"))
        assertTrue(!isErrorLine("On branch main"))
        assertTrue(!isErrorLine(""))
        assertTrue(!isErrorLine("~/Blank app $ ls"))
    }

}
