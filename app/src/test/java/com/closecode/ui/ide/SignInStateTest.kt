package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pins the sign-in screen's failure sorting: offline looks offline, a decline reads as the
 * user's own tap, and the countdown always reads M:SS.
 */
class SignInStateTest {

    @Test
    fun deadSocketsReadAsOffline() {
        assertTrue(isOfflineFailure(UnknownHostException("nope")))
        assertTrue(isOfflineFailure(ConnectException("refused")))
        assertTrue(isOfflineFailure(SocketTimeoutException("timed out")))
        assertTrue(isOfflineFailure(RuntimeException("wrap", UnknownHostException("deep"))))
    }

    @Test
    fun serverAnswersAreNotOffline() {
        assertFalse(isOfflineFailure(RuntimeException("HTTP 500")))
        assertFalse(isOfflineFailure(IllegalStateException("bad json")))
    }

    @Test
    fun aDeclineReadsAsTheUsersOwnTap() {
        assertEquals("You did not allow access", refusedText("You declined the request on GitHub."))
        assertEquals("GitHub said \"oops\".", refusedText("GitHub said \"oops\"."))
    }

    @Test
    fun countdownReadsMinutesAndSeconds() {
        assertEquals("14:32", countdownText(872))
        assertEquals("0:05", countdownText(5))
        assertEquals("0:00", countdownText(0))
        assertEquals("0:00", countdownText(-30))
    }
}
