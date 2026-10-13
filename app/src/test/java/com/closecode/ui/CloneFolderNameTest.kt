package com.closecode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a pasted link calls the folder it lands in. This string becomes the directory on disk and
 * then the project's name, so it has to survive every shape a link arrives in — with or without
 * the `.git` suffix, with something hanging off the end of it, from a host that is not GitHub —
 * and still come back with something a filesystem will hold.
 */
class CloneFolderNameTest {

    @Test
    fun itTakesTheLastSegmentAndDropsTheGitSuffix() {
        assertEquals(
            "hello-world",
            folderNameFromRemote("https://github.com/octocat/hello-world.git"),
        )
        assertEquals(
            "hello-world",
            folderNameFromRemote("https://github.com/octocat/hello-world"),
        )
    }

    @Test
    fun remotesThatAreNotGitHubAreNamedTheSameWay() {
        assertEquals(
            "closecode",
            folderNameFromRemote("https://gitlab.com/group/sub/closecode.git"),
        )
        assertEquals(
            "app",
            folderNameFromRemote("https://git.example.com:8443/team/app.git"),
        )
        assertEquals(
            "site",
            folderNameFromRemote("https://bitbucket.org/me/site.git"),
        )
    }

    @Test
    fun aQueryStringOrFragmentAfterThePathIsNotPartOfTheName() {
        assertEquals("repo", folderNameFromRemote("https://github.com/o/repo.git?ref=main"))
        assertEquals("repo", folderNameFromRemote("https://github.com/o/repo#readme"))
    }

    @Test
    fun aTrailingSlashIsStrippedBeforeTheSuffixIsLookedFor() {
        assertEquals("repo", folderNameFromRemote("https://github.com/o/repo.git/"))
        assertEquals("repo", folderNameFromRemote("  https://github.com/o/repo/  "))
    }

    @Test
    fun anythingThatReducesToNothingGetsANameRatherThanAFailure() {
        assertEquals("cloned", folderNameFromRemote(""))
        assertEquals("cloned", folderNameFromRemote("     "))
        assertEquals("cloned", folderNameFromRemote("/"))
    }
}
