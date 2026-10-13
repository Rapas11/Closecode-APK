package com.closecode.ui.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Device Flow and REST calls are exercised against a fake transport, so the whole sign-in
 * conversation runs offline and every branch GitHub can answer with is pinned here.
 */
class GitHubClientTest {

    private class FakeTransport : HttpTransport {
        data class Recorded(val method: String, val url: String, val headers: Map<String, String>, val body: String)

        val recorded = mutableListOf<Recorded>()
        var onForm: (url: String, headers: Map<String, String>, form: Map<String, String>) -> HttpResponse =
            { _, _, _ -> HttpResponse(500, "{}") }
        var onJson: (url: String, headers: Map<String, String>, payload: String) -> HttpResponse =
            { _, _, _ -> HttpResponse(500, "{}") }
        var onGet: (url: String, headers: Map<String, String>) -> HttpResponse =
            { _, _ -> HttpResponse(500, "{}") }

        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>): HttpResponse {
            recorded += Recorded(
                "POST_FORM",
                url,
                headers,
                form.entries.joinToString("&") { "${it.key}=${it.value}" },
            )
            return onForm(url, headers, form)
        }

        override fun postJson(url: String, headers: Map<String, String>, json: String): HttpResponse {
            recorded += Recorded("POST_JSON", url, headers, json)
            return onJson(url, headers, json)
        }

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            recorded += Recorded("GET", url, headers, "")
            return onGet(url, headers)
        }
    }

    private fun json(code: Int, body: String) = HttpResponse(code, body)

    @Test
    fun deviceFlowHandsOutACodeTheUserCanType() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ ->
                json(
                    200,
                    """{"device_code":"dev-1","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                )
            }
        }
        val client = GitHubClient(transport)

        val code = client.requestDeviceCode().getOrThrow()
        assertEquals("dev-1", code.deviceCode)
        assertEquals("ABCD-1234", code.userCode)
        assertEquals("https://github.com/login/device", code.verificationUri)
        assertEquals(900, code.expiresInSec)
        assertEquals(5, code.intervalSec)

        val sent = transport.recorded.single()
        assertEquals("POST_FORM", sent.method)
        assertEquals(GitHubClient.DEVICE_CODE_URL, sent.url)
        assertTrue(sent.body.contains("client_id=${GitHubClient.CLIENT_ID}"))
        assertTrue(sent.body.contains("scope=repo read:user"))
    }

    @Test
    fun anApprovedCodeReturnsTheToken() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, """{"access_token":"gho_secret","token_type":"bearer"}""") }
        }
        val poll = GitHubClient(transport).pollForToken("dev-1").getOrThrow()
        assertEquals(DevicePoll.Authorized("gho_secret"), poll)
    }

    @Test
    fun pendingKeepsTheLoopRunning() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, """{"error":"authorization_pending"}""") }
        }
        assertEquals(DevicePoll.Pending, GitHubClient(transport).pollForToken("dev-1").getOrThrow())
    }

    @Test
    fun slowDownIsReportedSoTheLoopCanBackOff() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, """{"error":"slow_down"}""") }
        }
        assertEquals(DevicePoll.SlowDown, GitHubClient(transport).pollForToken("dev-1").getOrThrow())
    }

    @Test
    fun anExpiredCodeStopsTheLoop() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, """{"error":"expired_token"}""") }
        }
        assertEquals(DevicePoll.Expired, GitHubClient(transport).pollForToken("dev-1").getOrThrow())
    }

    @Test
    fun aDeclinedRequestReportsTheReason() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, """{"error":"access_denied"}""") }
        }
        val poll = GitHubClient(transport).pollForToken("dev-1").getOrThrow()
        assertEquals(DevicePoll.Refused("You declined the request on GitHub."), poll)
    }

    @Test
    fun aPollFailureIsAPermissionProblemNotACrash() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(404, """{"error":"bad_verification_code","error_description":"The code is not valid."}""") }
        }
        val failure = GitHubClient(transport).pollForToken("dev-1")
        assertTrue(failure.isFailure)
        val message = failure.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("The code is not valid."))
    }

    @Test
    fun currentUserIsFetchedWithTheTokenAndParsesTheProfile() {
        val transport = FakeTransport().apply {
            onGet = { url, headers ->
                assertTrue(url.endsWith("/user"))
                assertEquals("Bearer gho_secret", headers["Authorization"])
                assertEquals(GitHubClient.API_VERSION, headers["X-GitHub-Api-Version"])
                json(200, """{"login":"ada","name":"Ada Lovelace","avatar_url":"https://x/a.png"}""")
            }
        }
        val user = GitHubClient(transport).currentUser("gho_secret").getOrThrow()
        assertEquals(GitHubUser("ada", "Ada Lovelace", "https://x/a.png"), user)
    }

    @Test
    fun repositoriesAreParsedByNameCloneUrlAndVisibility() {
        val transport = FakeTransport().apply {
            onGet = { _, _ ->
                json(
                    200,
                    """
                    [
                      {"full_name":"ada/monolith","clone_url":"https://github.com/ada/monolith.git","private":true},
                      {"full_name":"ada/site","clone_url":"https://github.com/ada/site.git","private":false},
                      {"full_name":null}
                    ]
                    """.trimIndent(),
                )
            }
        }
        val repos = GitHubClient(transport).repositories("gho_secret").getOrThrow()
        assertEquals(2, repos.size)
        assertEquals("ada/monolith", repos[0].fullName)
        assertTrue(repos[0].isPrivate)
        assertEquals("https://github.com/ada/site.git", repos[1].cloneUrl)
        assertFalse(repos[1].isPrivate)
    }

    @Test
    fun createRepositorySendsTheNameAndTheVisibilityItWasAskedFor() {
        var payload = ""
        val transport = FakeTransport().apply {
            onJson = { _, headers, body ->
                payload = body
                assertEquals("Bearer gho_secret", headers["Authorization"])
                assertEquals("application/json", headers["Content-Type"])
                json(201, """{"full_name":"ada/closecode","clone_url":"https://github.com/ada/closecode.git","private":true}""")
            }
        }
        val repo = GitHubClient(transport).createRepository("gho_secret", " closecode ", isPrivate = true)
            .getOrThrow()
        assertEquals("ada/closecode", repo.fullName)
        val sent = com.google.gson.JsonParser.parseString(payload).asJsonObject
        assertEquals("closecode", sent["name"].asString)
        assertTrue(sent["private"].asBoolean)
    }

    @Test
    fun anEmptyRepositoryNameIsRejectedBeforeAnyRequestIsMade() {
        val transport = FakeTransport()
        val failure = GitHubClient(transport).createRepository("gho_secret", "   ", isPrivate = true)
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(transport.recorded.isEmpty())
    }

    @Test
    fun aFailureMessageNeverCarriesTheToken() {
        val token = "gho_super_secret_value"
        val transport = FakeTransport().apply {
            onGet = { _, _ -> json(401, """{"message":"Bad credentials"}""") }
        }
        val failure = GitHubClient(transport).currentUser(token)
        assertTrue(failure.isFailure)
        val message = failure.exceptionOrNull()?.message.orEmpty()
        assertEquals("GitHub could not look up your account: Bad credentials", message)
        assertFalse(message.contains(token))
        assertFalse(message.contains("Bearer"))
    }

    @Test
    fun anUnreadableResponseFailsWithASentenceNotAStack() {
        val transport = FakeTransport().apply {
            onForm = { _, _, _ -> json(200, "<html>rate limited</html>") }
        }
        val failure = GitHubClient(transport).requestDeviceCode()
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is GitHubException)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("could not read"))
    }
}

/**
 * GitHub's pushed_at parses to millis; anything else is 0 rather than a throw, because a
 * missing timestamp must never break the repository list.
 */
class ParseInstantTest {

    @Test
    fun knownInstantParses() {
        // 2026-10-09T20:35:00Z
        assertEquals(1_791_578_100_000L, parseInstant("2026-10-09T20:35:00Z"))
    }

    @Test
    fun garbageIsZero() {
        assertEquals(0L, parseInstant(null))
        assertEquals(0L, parseInstant(""))
        assertEquals(0L, parseInstant("yesterday"))
    }
}
