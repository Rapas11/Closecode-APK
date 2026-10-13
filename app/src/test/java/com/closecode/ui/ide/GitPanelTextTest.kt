package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the branch row's plain-words status and the change rows' name/folder split: the one
 * sentence every Git beginner reads, and the two halves of every row.
 */
class GitPanelTextTest {

    @Test
    fun noRemoteIsNotPublished() {
        assertEquals("Not published", gitStatusText(false, 0, 0))
        assertEquals("Not published", gitStatusText(false, 2, 1))
    }

    @Test
    fun countsReadAsToPushAndToPull() {
        assertEquals("Up to date", gitStatusText(true, 0, 0))
        assertEquals("2 to push", gitStatusText(true, 2, 0))
        assertEquals("1 to pull", gitStatusText(true, 0, 1))
        assertEquals("1 to pull · 2 to push", gitStatusText(true, 2, 1))
    }

    @Test
    fun folderIsEverythingBeforeTheName() {
        assertEquals("assets", displayFolder("assets/.gitkeep"))
        assertEquals("a/b", displayFolder("a/b/c.kt"))
    }

    @Test
    fun topLevelFilesHaveNoFolder() {
        assertEquals("", displayFolder("README.md"))
    }
}
