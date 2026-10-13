package com.closecode.ui.github

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One HTTP response flattened to strings, so nothing above this line touches a stream. */
data class HttpResponse(val code: Int, val body: String)

/**
 * The network edge. Production uses [UrlConnectionTransport]; tests swap in a fake so the whole
 * sign-in flow can be driven offline.
 */
interface HttpTransport {
    fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>): HttpResponse

    fun postJson(url: String, headers: Map<String, String>, json: String): HttpResponse

    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** A failure GitHub caused, with a sentence a person can read. Never carries the token. */
class GitHubException(message: String, val code: Int = 0) : IOException(message)

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int,
)

/** One step of the Device Flow poll loop. */
sealed interface DevicePoll {
    data class Authorized(val accessToken: String) : DevicePoll
    object Pending : DevicePoll
    object SlowDown : DevicePoll
    object Expired : DevicePoll
    data class Refused(val reason: String) : DevicePoll
}

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
)

data class GitHubRepo(
    val fullName: String,
    val cloneUrl: String,
    val isPrivate: Boolean,
    /** When the repo was last pushed to, as epoch millis. 0 when GitHub did not say. */
    val pushedAtMillis: Long = 0,
)

/**
 * GitHub's Device Flow and REST API.
 *
 * Device Flow is the only OAuth flow that fits an app without a browser callback: GitHub hands
 * out a short code, the user types it on another device, and this app polls until they approve.
 * The client id is public by design — it identifies the app, not a person — and the access
 * token never appears in a log or an exception message.
 */
class GitHubClient(private val http: HttpTransport = UrlConnectionTransport()) {

    fun requestDeviceCode(): Result<DeviceCode> = runCatching {
        val resp = http.postForm(
            DEVICE_CODE_URL,
            acceptJson(),
            mapOf("client_id" to CLIENT_ID, "scope" to SCOPE),
        )
        val json = bodyOrThrow(resp, "start the sign-in")
        DeviceCode(
            deviceCode = json.requireString("device_code"),
            userCode = json.requireString("user_code"),
            verificationUri = json.optionalString("verification_uri") ?: VERIFY_URL,
            expiresInSec = json.optionalInt("expires_in") ?: 900,
            intervalSec = json.optionalInt("interval") ?: DEFAULT_INTERVAL,
        )
    }

    fun pollForToken(deviceCode: String): Result<DevicePoll> = runCatching {
        val resp = http.postForm(
            ACCESS_TOKEN_URL,
            acceptJson(),
            mapOf(
                "client_id" to CLIENT_ID,
                "device_code" to deviceCode,
                "grant_type" to DEVICE_GRANT_TYPE,
            ),
        )
        val json = bodyOrThrow(resp, "finish the sign-in")
        when (val error = json.optionalString("error")) {
            null -> {
                val token = json.optionalString("access_token")
                    ?: throw GitHubException("GitHub accepted the code but sent no token.")
                DevicePoll.Authorized(token)
            }
            ERROR_PENDING -> DevicePoll.Pending
            ERROR_SLOW_DOWN -> DevicePoll.SlowDown
            ERROR_EXPIRED -> DevicePoll.Expired
            ERROR_DENIED -> DevicePoll.Refused("You declined the request on GitHub.")
            else -> DevicePoll.Refused("GitHub said \"$error\".")
        }
    }

    fun currentUser(token: String): Result<GitHubUser> = runCatching {
        val json = bodyOrThrow(http.get("$API/user", authorized(token)), "look up your account")
        GitHubUser(
            login = json.requireString("login"),
            name = json.optionalString("name"),
            avatarUrl = json.optionalString("avatar_url"),
        )
    }

    fun repositories(token: String, page: Int = 1): Result<List<GitHubRepo>> = runCatching {
        val array = arrayOrThrow(
            http.get("$API/user/repos?per_page=30&sort=pushed&page=${page.coerceAtLeast(1)}", authorized(token)),
            "list your repositories",
        )
        array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val repo = element.asJsonObject
            val name = repo.optionalString("full_name") ?: return@mapNotNull null
            val clone = repo.optionalString("clone_url") ?: return@mapNotNull null
            GitHubRepo(
                fullName = name,
                cloneUrl = clone,
                isPrivate = repo.optionalBoolean("private") ?: true,
                pushedAtMillis = parseInstant(repo.optionalString("pushed_at")),
            )
        }
    }

    /** Creates the repository a Publish press turns into. Private unless asked otherwise. */
    fun createRepository(token: String, name: String, isPrivate: Boolean): Result<GitHubRepo> =
        runCatching {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "A repository needs a name." }
            val payload = JsonObject().apply {
                addProperty("name", trimmed)
                addProperty("private", isPrivate)
                addProperty("description", "Published from Closecode")
                addProperty("auto_init", false)
            }
            val resp = http.postJson("$API/user/repos", authorizedJson(token), payload.toString())
            val json = bodyOrThrow(resp, "create the repository")
            GitHubRepo(
                fullName = json.requireString("full_name"),
                cloneUrl = json.optionalString("clone_url")
                    ?: "https://github.com/${json.requireString("full_name")}.git",
                isPrivate = json.optionalBoolean("private") ?: isPrivate,
            )
        }

    // -----------------------------------------------------------------------------------------

    private fun acceptJson() = mapOf(
        "Accept" to "application/json",
        "User-Agent" to USER_AGENT,
        "Content-Type" to "application/x-www-form-urlencoded",
    )

    private fun authorized(token: String) = mapOf(
        "Accept" to "application/vnd.github+json",
        "Authorization" to "Bearer $token",
        "X-GitHub-Api-Version" to API_VERSION,
        "User-Agent" to USER_AGENT,
    )

    private fun authorizedJson(token: String) = authorized(token) + ("Content-Type" to "application/json")

    private fun check(resp: HttpResponse, doing: String) {
        if (resp.code in 200..299) return
        val parsed = runCatching { JsonParser.parseString(resp.body) }.getOrNull()
        val message = (parsed as? JsonObject)?.let {
            it.optionalString("message") ?: it.optionalString("error_description") ?: it.optionalString("error")
        }
        throw GitHubException("GitHub could not $doing: ${message ?: "HTTP ${resp.code}"}", resp.code)
    }

    private fun bodyOrThrow(resp: HttpResponse, doing: String): JsonObject {
        check(resp, doing)
        return runCatching { JsonParser.parseString(resp.body).asJsonObject }
            .getOrElse { throw GitHubException("GitHub sent a response this app could not read while trying to $doing.", resp.code) }
    }

    private fun arrayOrThrow(resp: HttpResponse, doing: String): JsonArray {
        check(resp, doing)
        return runCatching { JsonParser.parseString(resp.body).asJsonArray }
            .getOrElse { throw GitHubException("GitHub sent a response this app could not read while trying to $doing.", resp.code) }
    }

    private fun JsonObject.requireString(key: String): String =
        optionalString(key) ?: throw GitHubException("GitHub's response was missing \"$key\".")

    private fun JsonObject.optionalString(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return value.asString.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.optionalInt(key: String): Int? =
        optionalString(key)?.toIntOrNull()

    private fun JsonObject.optionalBoolean(key: String): Boolean? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asBoolean }.getOrNull()
    }

    companion object {
        /**
         * Device Flow client id for Closecode. Public on purpose: it names the application and
         * grants no access by itself. There is no client secret in this app because Device Flow
         * does not use one.
         */
        const val CLIENT_ID = "Ov23li4Ajd4dvN9uy8Ch"

        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val VERIFY_URL = "https://github.com/login/device"
        const val API = "https://api.github.com"
        const val API_VERSION = "2022-11-28"
        const val USER_AGENT = "Closecode"
        const val SCOPE = "repo read:user"
        const val DEFAULT_INTERVAL = 5

        private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val ERROR_PENDING = "authorization_pending"
        private const val ERROR_SLOW_DOWN = "slow_down"
        private const val ERROR_EXPIRED = "expired_token"
        private const val ERROR_DENIED = "access_denied"
    }
}

/** Plain [HttpURLConnection] so no extra HTTP dependency ships with the app. */
class UrlConnectionTransport : HttpTransport {

    override fun postForm(
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>,
    ): HttpResponse = request(url, headers, "POST", encode(form))

    override fun postJson(url: String, headers: Map<String, String>, json: String): HttpResponse =
        request(url, headers + ("Content-Type" to "application/json"), "POST", json)

    override fun get(url: String, headers: Map<String, String>): HttpResponse =
        request(url, headers, "GET", null)

    private fun request(
        url: String,
        headers: Map<String, String>,
        method: String,
        body: String?,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(form: Map<String, String>): String = form.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/**
 * GitHub's ISO instant ("2026-10-09T20:35:00Z") as epoch millis, or 0 when it is missing or
 * malformed. Hand-rolled over SimpleDateFormat so the shape stays obvious; the format is fixed
 * by GitHub's API, not by the device.
 */
internal fun parseInstant(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0
    return runCatching {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(raw)?.time ?: 0
    }.getOrDefault(0)
}
