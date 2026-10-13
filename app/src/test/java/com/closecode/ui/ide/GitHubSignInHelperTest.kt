package com.closecode.ui.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubSignInHelperTest {

    @Test
    fun identifiesOnlyClientConfigurationErrorsForClientIdPrompt() {
        assertTrue(needsReplacementGitHubClientId("GitHub said device_flow_disabled"))
        assertTrue(needsReplacementGitHubClientId("incorrect_client_credentials"))
        assertFalse(needsReplacementGitHubClientId("You declined the request on GitHub."))
        assertFalse(needsReplacementGitHubClientId("No internet connection"))
        assertFalse(needsReplacementGitHubClientId(null))
    }
}
