package com.closecode.ui.github

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling "nobody is signed in" apart from "the network is down". The clone screen offers a
 * different answer to each — one opens a sign-in, the other is a retry — and the wrong one wastes
 * a retry the user cannot win, so both halves of that decision are pinned here.
 */
class NeedsAuthenticationTest {

    @Test
    fun aStatusTheClientRecordedIsReadStraightFromTheException() {
        assertTrue(
            needsAuthentication(
                GitHubException("GitHub could not list your repositories: Bad credentials", 401),
            ),
        )
        assertTrue(needsAuthentication(GitHubException("GitHub could not publish: Forbidden", 403)))
        assertFalse(
            needsAuthentication(
                GitHubException("GitHub could not list your repositories: Not Found", 404),
            ),
        )
        assertFalse(needsAuthentication(GitHubException("GitHub sent a response", 500)))
    }

    @Test
    fun aTransportSentenceThatQuotesTheStatusCounts() {
        assertTrue(needsAuthentication(RuntimeException("Unauthorized (HTTP 401)")))
        assertTrue(needsAuthentication(RuntimeException("Invalid username or password")))
        assertTrue(needsAuthentication(IOException("Access denied by the server")))
        assertTrue(needsAuthentication(RuntimeException("Requires authentication")))
    }

    @Test
    fun theStatusIsReadSeveralCausesDeep() {
        var wrapped: Throwable = RuntimeException("Unauthorized (HTTP 401)")
        repeat(4) { wrapped = RuntimeException("Exception caught during clone", wrapped) }
        assertTrue(needsAuthentication(wrapped))
    }

    @Test
    fun aNetworkFailureThatNeverMentionsAuthDoesNotCount() {
        assertFalse(needsAuthentication(IOException("Failed to connect to github.com")))
        assertFalse(needsAuthentication(RuntimeException("Connection timed out")))
        assertFalse(needsAuthentication(RuntimeException("404 Not Found")))
        assertFalse(needsAuthentication(IOException("SSL handshake failed")))
    }

    @Test
    fun anExceptionWithNothingSaidIsNotAnAuthenticationFailure() {
        assertFalse(needsAuthentication(RuntimeException()))
        assertFalse(needsAuthentication(IOException()))
    }

    @Test
    fun aCauseChainThatCirclesBackStopsInsteadOfRunningForever() {
        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)
        assertFalse(needsAuthentication(first))
    }
}
