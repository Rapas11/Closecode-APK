package com.closecode.ui

import com.closecode.ui.github.GitHubUser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hero screen's account line. The two GitHub pills can only say that *a* token exists, so
 * this string is the one place that says who it belongs to — and it must never borrow a name it
 * has not been told.
 */
class AccountLabelTest {

    @Test
    fun withNoAccountLoadedItSaysConnectedRatherThanGuessing() {
        assertEquals("Connected", accountLabel(null))
    }

    @Test
    fun withALoginButNoDisplayNameItShowsTheHandle() {
        assertEquals(
            "@rapas11",
            accountLabel(GitHubUser(login = "rapas11", name = null, avatarUrl = null)),
        )
        assertEquals(
            "@rapas11",
            accountLabel(GitHubUser(login = "rapas11", name = "   ", avatarUrl = null)),
        )
    }

    @Test
    fun withBothItShowsTheNameAlongsideTheHandle() {
        assertEquals(
            "Rapas · @rapas11",
            accountLabel(GitHubUser(login = "rapas11", name = "Rapas", avatarUrl = null)),
        )
    }
}
