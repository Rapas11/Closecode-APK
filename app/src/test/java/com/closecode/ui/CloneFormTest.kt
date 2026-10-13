package com.closecode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.UnknownHostException

/**
 * Pins the clone form's rules: which links turn the button blue, what failures read as,
 * and how the progress line counts bytes.
 */
class CloneFormTest {

    @Test
    fun emptyShowsNoError() {
        assertNull(cloneLinkError(""))
        assertNull(cloneLinkError("   "))
    }

    @Test
    fun fullHttpsLinksAreValid() {
        assertNull(cloneLinkError("https://github.com/owner/repo"))
        assertNull(cloneLinkError("https://github.com/owner/repo.git"))
        assertNull(cloneLinkError("https://gitlab.com/owner/repo"))
        assertNull(cloneLinkError("https://bitbucket.org/owner/repo"))
        assertNull(cloneLinkError("https://github.com/owner/repo/"))
    }

    @Test
    fun anythingElseIsInvalid() {
        val expected = "Enter a full link that starts with https://"
        assertEquals(expected, cloneLinkError("git@github.com:owner/repo.git"))
        assertEquals(expected, cloneLinkError("http://github.com/owner/repo"))
        assertEquals(expected, cloneLinkError("https://example.com/owner/repo"))
        assertEquals(expected, cloneLinkError("https://github.com"))
        assertEquals(expected, cloneLinkError("https://github.com/"))
    }

    @Test
    fun failuresReadAsPlainWords() {
        assertEquals("No internet connection.", cloneErrorText(UnknownHostException("x")))
        assertEquals(
            "Repository not found or private.",
            cloneErrorText(RuntimeException("repository not found")),
        )
        assertEquals(
            "Repository not found or private.",
            cloneErrorText(RuntimeException("HTTP 404")),
        )
        assertEquals(
            "Not enough storage on this device.",
            cloneErrorText(RuntimeException("No space left on device")),
        )
        assertEquals(
            "Could not clone this repository.",
            cloneErrorText(RuntimeException("weird")),
        )
    }

    @Test
    fun bytesReadAsBandKbAndMb() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.2 MB", formatBytes(1_258_291))
    }
}
