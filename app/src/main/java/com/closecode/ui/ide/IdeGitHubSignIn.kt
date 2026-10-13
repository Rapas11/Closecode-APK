package com.closecode.ui.ide

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.SheetHeader
import com.closecode.ui.components.TextLink
import com.closecode.ui.github.DeviceCode
import com.closecode.ui.github.DevicePoll
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A failure a person can act on. The exception class comes first because the messages Android
 * throws on a dead socket are often null or uselessly short, and the cause chain is what names
 * the real reason (no route, TLS failure, HTTP status). Never contains the token: the client
 * only ever throws messages it built itself.
 */
internal fun describeFailure(error: Throwable, fallback: String): String {
    val head = error.message?.takeIf { it.isNotBlank() }
        ?: "${error::class.simpleName ?: "IOException"}: no detail"
    // JGit wraps transport failures several deep — a push failure is "Exception caught during
    // execution of push command" around a TransportException around the HTTP status that
    // actually says why. One level of cause keeps the sentence useless, so walk a few.
    val seen = mutableSetOf(head)
    val chain = mutableListOf<String>()
    var current: Throwable? = error.cause
    var depth = 0
    while (current != null && depth < 4 && chain.size < 3) {
        val step: Throwable = current
        val cls = step::class.simpleName ?: "Throwable"
        val text = step.message?.takeIf { it.isNotBlank() && it != head }
        val label = if (text == null) cls else "$cls: $text"
        if (seen.add(label)) chain += label
        current = step.cause
        depth++
    }
    return (listOf(head) + chain).joinToString(" — ")
}

/**
 * True when the failure is the phone being offline rather than GitHub saying no: dead route,
 * refused connection or a timeout, at any depth of the cause chain. The sign-in shows
 * "No internet connection" for these instead of a stack-trace sentence.
 */
internal fun isOfflineFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        if (current is java.net.UnknownHostException ||
            current is java.net.ConnectException ||
            current is java.net.SocketTimeoutException ||
            current is java.net.NoRouteToHostException
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/** What a refused authorization reads as. A decline is the user's own tap, not an error. */
internal fun refusedText(reason: String): String =
    if (reason.contains("declin", ignoreCase = true)) "You did not allow access" else reason

/** Seconds as M:SS for the code-expiry countdown. */
internal fun countdownText(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

/**
 * The GitHub Device Flow sign-in, as a full-screen page.
 *
 * GitHub hands out a short code, the user types it at github.com/login/device on whatever
 * device is near them, and this screen polls until they approve. No browser callback, no
 * client secret, and no token ever leaves this process except to GitHub itself.
 */
@Composable
fun GitHubSignIn(
    onSignedIn: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    BackHandler(onBack = {
        BackLog.press("github-signin", "branch=dismiss")
        onDismiss()
    })
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
    ) {
        SheetHeader(
            onClose = onDismiss,
            title = "Connect GitHub",
            subtitle = "Three quick steps. This only links the app to your GitHub account.",
        )
        GitHubSignInBody(
            onSignedIn = onSignedIn,
            onCancel = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * The device-flow steps on their own: the code, what it is waiting for, and the way out.
 *
 * [onCancel] is nullable because the steps have two homes. Full-screen they need a Cancel;
 * embedded in the clone screen they do not — the screen behind them is the way out, and a
 * Cancel there would say the wrong thing.
 */
@Composable
internal fun GitHubSignInBody(
    onSignedIn: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val client = remember { GitHubClient() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var remainingSec by remember { mutableStateOf(0) }
    // Bumped by "Try again" and "Get a new code": re-keying the effect restarts the request.
    var attempt by remember { mutableStateOf(0) }

    fun reset() {
        code = null
        waiting = false
        username = null
        expired = false
        refused = null
        offline = false
        failure = null
        attempt++
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    // The code's own clock, independent of the poll loop: even if a poll is slow, the
    // countdown says how long the code lives.
    LaunchedEffect(code) {
        val start = code ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + start.expiresInSec * 1_000L
        while (true) {
            val left = ((deadline - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            remainingSec = left
            if (left <= 0) return@LaunchedEffect
            delay(1_000)
        }
    }

    LaunchedEffect(attempt) {
        // HttpURLConnection blocks, and Android answers that with NetworkOnMainThreadException —
        // whose message is null, which is why this runs off the main thread and classifies
        // offline separately instead of printing a bare exception sentence.
        val requested = withContext(Dispatchers.IO) { client.requestDeviceCode() }
        val start = requested.getOrElse { error ->
            if (isOfflineFailure(error)) offline = true
            else failure = describeFailure(error, "GitHub could not start the sign-in.")
            return@LaunchedEffect
        }
        code = start
        waiting = true
        // GitHub names its own pace; never ask faster than every 5 seconds.
        var interval = maxOf(start.intervalSec, 5)
        while (true) {
            delay(interval * 1_000L)
            val polled = withContext(Dispatchers.IO) { client.pollForToken(start.deviceCode) }
            val step = polled.getOrElse { error ->
                waiting = false
                if (isOfflineFailure(error)) offline = true
                else failure = describeFailure(error, "GitHub could not finish the sign-in.")
                return@LaunchedEffect
            }
            when (step) {
                is DevicePoll.Authorized -> {
                    // The name for the success line, then a beat to read it before leaving.
                    val login = withContext(Dispatchers.IO) {
                        client.currentUser(step.accessToken).getOrNull()?.login
                    }
                    waiting = false
                    username = login ?: "your account"
                    delay(1_500)
                    onSignedIn(step.accessToken)
                    return@LaunchedEffect
                }
                DevicePoll.Pending -> Unit
                DevicePoll.SlowDown -> interval += 5
                DevicePoll.Expired -> {
                    waiting = false
                    expired = true
                    return@LaunchedEffect
                }
                is DevicePoll.Refused -> {
                    waiting = false
                    refused = step.reason
                    return@LaunchedEffect
                }
            }
        }
    }

    fun openGitHub() {
        val uri = android.net.Uri.parse("https://github.com/login/device")
        runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SignInStep(number = 1, title = "Copy this code") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.fieldBg)
                    .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = code?.userCode ?: "····-····",
                    color = c.link,
                    fontSize = 20.sp,
                    fontFamily = Mono,
                    letterSpacing = 2.8.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.popupSelected)
                        .clickable(enabled = code != null) {
                            code?.userCode?.let { clipboard.setText(AnnotatedString(it)) }
                            copied = true
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
            }
        }

        SignInStep(
            number = 2,
            title = "Open GitHub",
            subtitle = "It opens github.com/login/device in your browser.",
        ) {
            OutlineButton(label = "Open GitHub", onClick = ::openGitHub)
        }

        SignInStep(
            number = 3,
            title = "Paste the code and tap Authorize",
            subtitle = "Come back here. It connects by itself.",
        ) {
        }

        // One status row for every outcome, so the steps above never have to change shape.
        val statusText = when {
            username != null -> "Connected as @$username"
            expired -> "Code expired"
            refused != null -> refusedText(refused.orEmpty())
            offline -> "No internet connection"
            failure != null -> failure.orEmpty()
            waiting -> if (code != null) {
                "Waiting for approval · code expires in ${countdownText(remainingSec)}"
            } else {
                "Waiting for approval…"
            }
            else -> "Asking GitHub for a code…"
        }
        val dot = when {
            username != null -> c.success
            expired || refused != null || offline || failure != null -> c.error
            waiting -> c.warning
            else -> c.textFaint
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (username == null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(dot),
                )
                Spacer(Modifier.width(10.dp))
            } else {
                CcIcon(CcIcons.Check, c.success, 16.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = statusText,
                color = if (username != null) c.success else c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
        }

        when {
            expired || refused != null -> PrimaryButton(label = "Get a new code", onClick = ::reset)
            offline || failure != null -> OutlineButton(label = "Try again", onClick = ::reset)
        }

        Text(
            text = "Your password is never shared with this app. You can disconnect any time in settings.",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 16.sp,
        )

        if (onCancel != null) {
            TextLink(label = "Cancel", onClick = onCancel)
        }
    }
}

/** One numbered step: a 22 dp circle, a title, an optional subtitle, and its control. */
@Composable
private fun SignInStep(
    number: Int,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c.popupSelected),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = c.link,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = c.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
            content()
        }
    }
}

@Composable
internal fun SignInAction(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) c.ac.copy(alpha = 0.16f) else c.panel2)
            .border(1.dp, if (primary) c.ac else c.ln, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) c.ac else c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
