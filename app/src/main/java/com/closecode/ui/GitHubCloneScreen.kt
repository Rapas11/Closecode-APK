package com.closecode.ui

import androidx.activity.compose.BackHandler
import com.closecode.ui.ide.BackLog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.rememberProjectRepository
import com.closecode.ui.components.SheetHeader
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubClone
import com.closecode.ui.github.GitHubRepo
import com.closecode.ui.github.needsAuthentication
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.GitHubSignInBody
import com.closecode.ui.ide.SignInAction
import com.closecode.ui.ide.describeFailure
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The one download that is running, if any. Cancellation is cooperative: JGit does not stop
 * mid-clone, so the flag deletes the folder the moment the download returns instead. */
private data class ActiveClone(
    val folder: String,
    val remoteUrl: String,
    var cancelRequested: Boolean = false,
)

/** What Try again repeats: the exact arguments of the last attempt. */
private data class CloneAttempt(val folder: String, val remoteUrl: String, val token: String?)

/**
 * The repository picker, now the whole screen rather than a sheet over it.
 *
 * Two ways in, deliberately stacked rather than either-or. The paste box takes any https remote —
 * GitHub, GitLab, Bitbucket, a server on the office LAN — and needs no account at all, so it stays
 * where it is even when nobody has signed in; hiding it behind a sign-in would punish the common
 * case (a public link someone just copied) for the uncommon one. The list underneath is a
 * convenience that does need an account, so a signed-out user gets the sign-in card in its place
 * instead of an empty shelf they could never fill.
 *
 * Nothing here names a branch. `git clone` checks out the remote's own default HEAD, which is the
 * branch that list would have preselected anyway; picking among the rest is work for the Source
 * Control view once the code is on the device.
 *
 * Success is not reported back as "cloned" — it hands the new [ProjectRecord] to the caller, which
 * opens it the same way a freshly created project opens.
 */
@Composable
fun GitHubCloneScreen(
    token: String?,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
    onCloned: (ProjectRecord) -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val client = remember { GitHubClient() }
    val cloner = remember { GitHubClone() }
    val repository = rememberProjectRepository()

    var link by remember { mutableStateOf("") }
    // The folder fills itself in from the link until the user edits it by hand; after that the
    // link stops overwriting what was typed.
    var folder by remember { mutableStateOf("") }
    var folderTouched by remember { mutableStateOf(false) }
    // The red helper line waits a second after the last keystroke, so it never scolds mid-typing.
    var linkErrorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(link) {
        linkErrorVisible = false
        if (link.isNotBlank()) {
            delay(1_000)
            linkErrorVisible = true
        }
        if (!folderTouched) folder = folderNameFromRemote(link)
    }
    // null means "still asking GitHub"; an empty list means "you have none yet".
    var repos by remember { mutableStateOf<List<GitHubRepo>?>(null) }
    var repoPage by remember { mutableStateOf(1) }
    var repoQuery by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Which kind of failure it was, because only one of them has an answer that helps: a network
    // error wants a retry, an authentication error wants a token the user does not have here.
    var authFailure by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<ActiveClone?>(null) }
    var cloneJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var fraction by remember { mutableStateOf<Float?>(null) }
    var phaseText by remember { mutableStateOf("Connecting to GitHub") }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var cloneDir by remember { mutableStateOf<java.io.File?>(null) }
    var lastAttempt by remember { mutableStateOf<CloneAttempt?>(null) }
    // The sign-in card replaces the list while it is up. It starts out governed by the token alone
    // — signed out means the card — and this is only for asking again while a token already exists.
    var signingIn by remember { mutableStateOf(false) }
    // The clone that failed on auth, kept so it can run again the moment a token arrives rather
    // than making the user retype the link they already gave us.
    var retryAfterSignIn by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // The screen underneath installs its own handler, and it always goes one level up — out of
    // the project entirely. So this route has to claim Back for itself while it is composed.
    BackHandler(onBack = {
        BackLog.press("github-clone", "branch=dismiss")
        onDismiss()
    })

    LaunchedEffect(token) {
        val active = token
        if (active == null) {
            repos = null
            return@LaunchedEffect
        }
        // Reaching here means a token just arrived, so whatever the old one was complained about
        // is no longer the question.
        repos = null
        failure = null
        authFailure = false
        val listed = withContext(Dispatchers.IO) { client.repositories(active) }
        listed
            .onSuccess { repos = it }
            .onFailure { error ->
                failure = describeFailure(error, "GitHub could not list your repositories.")
                authFailure = needsAuthentication(error)
            }
    }

    fun startClone(
        folderName: String,
        remoteUrl: String,
        tokenUsed: String?,
    ) {
        if (active != null) return
        val name = folderName.trim().ifBlank { "cloned" }
        failure = null
        authFailure = false
        retryAfterSignIn = null
        lastAttempt = CloneAttempt(name, remoteUrl, tokenUsed)
        val holder = ActiveClone(name, remoteUrl)
        active = holder
        fraction = null
        phaseText = "Connecting to GitHub"
        downloadedBytes = 0
        cloneDir = null
        cloneJob = scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importCloned(name) { target ->
                        cloneDir = target
                        cloner.clone(remoteUrl, tokenUsed, target) { progress ->
                            // JGit reports on its own thread; state moves on the main one.
                            scope.launch(Dispatchers.Main) {
                                if (active === holder) {
                                    fraction = progress.fraction
                                    phaseText = progress.phase
                                }
                            }
                        }.getOrThrow()
                        if (holder.cancelRequested) {
                            target.deleteRecursively()
                            throw kotlinx.coroutines.CancellationException("Clone cancelled.")
                        }
                    }
                }
            }
            active = null
            cloneJob = null
            outcome
                .onSuccess { onCloned(it) }
                .onFailure { error ->
                    // A cancel is not a failure: the half-downloaded folder is already gone,
                    // so there is nothing to report and nothing to retry.
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                    failure = cloneErrorText(error)
                    authFailure = needsAuthentication(error)
                    if (authFailure) {
                        retryAfterSignIn = { withToken ->
                            startClone(name, remoteUrl, withToken)
                        }
                    }
                }
        }
    }

    fun cancelClone() {
        active?.cancelRequested = true
        cloneJob?.cancel()
    }

    // The folder size ticks while the download runs, so the progress line can count megabytes
    // even though no total is known up front.
    LaunchedEffect(active) {
        while (active != null) {
            delay(500)
            val dir = cloneDir
            if (dir != null) {
                downloadedBytes = withContext(Dispatchers.IO) { dirSize(dir) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(
                    onClose = onDismiss,
                    title = "Clone a repository",
                    subtitle = "Paste any https link, or pick one of your repositories. It opens " +
                        "as a project when the download finishes.",
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val running = active
                    if (running != null) {
                        CloneProgress(
                            folder = running.folder,
                            remoteUrl = running.remoteUrl,
                            phase = phaseText,
                            fraction = fraction,
                            downloadedBytes = downloadedBytes,
                            onCancel = ::cancelClone,
                        )
                    } else {
                        failure?.let { message ->
                            CloneFailure(
                                message = message,
                                authLabel = if (authFailure) "Sign in again" else null,
                                onAuthenticate = { signingIn = true },
                            )
                            // A failure that is not about signing in wants a second try with
                            // the same arguments, not a retyped form.
                            val retry = lastAttempt
                            if (!authFailure && retry != null) {
                                com.closecode.ui.components.OutlineButton(
                                    label = "Try again",
                                    onClick = {
                                        startClone(retry.folder, retry.remoteUrl, retry.token)
                                    },
                                )
                            }
                        }

                        CloneForm(
                            link = link,
                            onLink = { link = it },
                            linkError = if (linkErrorVisible) cloneLinkError(link) else null,
                            folder = folder,
                            onFolder = {
                                folder = it
                                folderTouched = true
                            },
                            folderTaken = remember(folder) {
                                folder.isNotBlank() && repository.folderTaken(folder)
                            },
                            onPaste = {
                                scope.launch {
                                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }
                                        ?.let { link = it }
                                }
                            },
                            canClone = cloneLinkError(link) == null && link.isNotBlank() &&
                                folder.isNotBlank(),
                            onClone = {
                                keyboard?.hide()
                                startClone(folder, link, token)
                            },
                        )

                        OrDivider()

                        if (signingIn) {
                            GitHubSignInBody(
                                onSignedIn = { fresh ->
                                    onToken(fresh)
                                    signingIn = false
                                    val retry = retryAfterSignIn
                                    retryAfterSignIn = null
                                    failure = null
                                    authFailure = false
                                    retry?.invoke(fresh)
                                },
                                onCancel = { signingIn = false },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (token == null) {
                            // Signed out: the card says what signing in unlocks. Public links
                            // above work without it, so the form stays usable either way.
                            com.closecode.ui.components.ScreenCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CcIcon(
                                        com.closecode.ui.ide.CcIcons.Github,
                                        c.text,
                                        20.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "See your repositories here",
                                        color = c.text,
                                        fontSize = 13.sp,
                                        fontFamily = Sans,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Sign in once to pick private and public repositories " +
                                        "from a list. Public links above work without signing in.",
                                    color = c.textMuted,
                                    fontSize = 12.sp,
                                    fontFamily = Sans,
                                    lineHeight = 17.sp,
                                )
                                Spacer(Modifier.height(10.dp))
                                com.closecode.ui.components.OutlineButton(
                                    label = "Sign in with GitHub",
                                    onClick = { signingIn = true },
                                )
                            }
                        } else {
                            RepositoryList(
                                repos = repos,
                                query = repoQuery,
                                onQuery = { repoQuery = it },
                                busy = active != null,
                                disabled = active != null,
                                loadingMore = loadingMore,
                                canLoadMore = (repos?.size ?: 0) >= repoPage * 30,
                                onLoadMore = {
                                    val activeToken = token ?: return@RepositoryList
                                    if (loadingMore) return@RepositoryList
                                    loadingMore = true
                                    scope.launch {
                                        val next = withContext(Dispatchers.IO) {
                                            client.repositories(activeToken, repoPage + 1)
                                        }
                                        next.onSuccess { more ->
                                            repoPage++
                                            repos = (repos.orEmpty() + more).distinctBy { it.fullName }
                                        }.onFailure { error ->
                                            failure = describeFailure(
                                                error,
                                                "GitHub could not list your repositories.",
                                            )
                                        }
                                        loadingMore = false
                                    }
                                },
                                onPick = { repo ->
                                    startClone(
                                        folderName = repo.fullName.substringAfter('/'),
                                        remoteUrl = repo.cloneUrl,
                                        tokenUsed = token,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A failure, said plainly, with the one action that would actually change the answer.
 *
 * The sign-in half only appears when signing in is not already on screen — offering it over a
 * card that is right there would just be a second way to press the same thing.
 */
@Composable
private fun CloneFailure(
    message: String,
    authLabel: String?,
    onAuthenticate: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.red.copy(alpha = 0.10f))
            .border(1.dp, c.red.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = c.red,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (authLabel != null) {
            Spacer(Modifier.width(10.dp))
            SignInAction(label = authLabel, primary = true, onClick = onAuthenticate)
        }
    }
}

/** The link form: labels, fields with their red lines, and the Clone button. */
@Composable
private fun CloneForm(
    link: String,
    onLink: (String) -> Unit,
    linkError: String?,
    folder: String,
    onFolder: (String) -> Unit,
    folderTaken: Boolean,
    onPaste: () -> Unit,
    canClone: Boolean,
    onClone: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Repository link",
            color = c.iconTint,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(start = 13.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (link.isEmpty()) {
                    Text(
                        text = "https://github.com/owner/repository",
                        color = c.textFaint,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = link,
                    onValueChange = onLink,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.text,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Fills the field from the clipboard: links arrive from a browser or a chat.
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.chipBg)
                    .clickable(onClick = onPaste)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Paste",
                    color = c.text,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (linkError != null) {
            Text(
                text = linkError,
                color = c.error,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
        }
        Text(
            text = "Save in folder",
            color = c.iconTint,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
        com.closecode.ui.components.ScreenField(
            value = folder,
            onChange = onFolder,
            hint = "repository",
        )
        if (folderTaken) {
            Text(
                text = "The folder already exists",
                color = c.error,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
        }
        com.closecode.ui.components.PrimaryButton(
            label = "Clone",
            enabled = canClone && !folderTaken,
            onClick = onClone,
        )
    }
}

/** The divider between pasting a link and picking from GitHub. */
@Composable
private fun OrDivider() {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(c.divider))
        Text(
            text = "OR CHOOSE FROM GITHUB",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Sans,
            letterSpacing = 0.72.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(c.divider))
    }
}

/**
 * The download as it happens: icon tile, name and link, bar with percent and size, three steps,
 * and a red Cancel that deletes the half-downloaded folder.
 */
@Composable
private fun CloneProgress(
    folder: String,
    remoteUrl: String,
    phase: String,
    fraction: Float?,
    downloadedBytes: Long,
    onCancel: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.primaryTint),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(
                com.closecode.ui.ide.CcIcons.CloudDownload,
                c.link,
                26.dp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Cloning $folder",
            color = c.text,
            fontSize = 17.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = remoteUrl.removePrefix("https://"),
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        // 8 dp bar: accent fill on a chip track. Unknown totals fill halfway and pulse by
        // indeterminate mode rather than lying about a percentage.
        if (fraction == null) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = c.accent,
                trackColor = c.chipBg,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.chipBg),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.accent),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = phase,
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (fraction == null) {
                    formatBytes(downloadedBytes)
                } else {
                    "${(fraction * 100).toInt()}% · ${formatBytes(downloadedBytes)}"
                },
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Mono,
            )
        }
        Spacer(Modifier.height(16.dp))
        CloneStep(done = true, current = false, label = "Connected to GitHub")
        Spacer(Modifier.height(8.dp))
        CloneStep(done = false, current = true, label = "Downloading files")
        Spacer(Modifier.height(8.dp))
        CloneStep(done = false, current = false, label = "Open as a project")
        Spacer(Modifier.height(16.dp))
        com.closecode.ui.components.TextLink(
            label = "Cancel",
            color = c.error,
            onClick = onCancel,
        )
    }
}

@Composable
private fun CloneStep(done: Boolean, current: Boolean, label: String) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            done -> CcIcon(com.closecode.ui.ide.CcIcons.Check, c.success, 16.dp)
            current -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = c.text,
                strokeWidth = 2.dp,
            )
            else -> Box(
                Modifier
                    .size(14.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .border(1.5.dp, c.textFaint, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = when {
                done -> c.success
                current -> c.text
                else -> c.textFaint
            },
            fontSize = 13.sp,
            fontFamily = Sans,
        )
    }
}

/**
 * The folder a remote is cloned into: the last path segment, with any trailing `.git` taken off.
 *
 * It only ever names the directory and then the project, so the shape of the answer matters more
 * than the shape of the input — a query string after the path, a fragment, a link copied with a
 * stray trailing slash all have to reduce to something a filesystem is happy to hold. Anything
 * that reduces to nothing (an empty line, a lone slash) falls back to a name rather than failing
 * the clone over it.
 */
internal fun folderNameFromRemote(url: String): String {
    val path = url.trim().substringBefore('?').substringBefore('#').trimEnd('/')
    return path.substringAfterLast('/').removeSuffix(".git").trim().ifBlank { "cloned" }
}

private val CLONE_HOSTS = setOf(
    "github.com", "www.github.com",
    "gitlab.com", "www.gitlab.com",
    "bitbucket.org", "www.bitbucket.org",
)

/**
 * Why [link] cannot be cloned, or null when it can. Empty shows no error — the button is
 * simply dead — so the screen does not scold an untouched field.
 */
internal fun cloneLinkError(link: String): String? {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("https://", ignoreCase = true)) {
        return "Enter a full link that starts with https://"
    }
    val rest = trimmed.substringAfter("://")
    val host = rest.substringBefore('/').lowercase()
    val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#').trim()
        .trimEnd('/').removeSuffix(".git")
    if (host !in CLONE_HOSTS || path.isEmpty()) {
        return "Enter a full link that starts with https://"
    }
    return null
}

/** A clone failure in plain words: offline, missing-or-private, full disk, or a plain defeat. */
internal fun cloneErrorText(error: Throwable): String {
    if (com.closecode.ui.ide.isOfflineFailure(error)) return "No internet connection."
    val haystack = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            append(current.message.orEmpty()).append(' ')
            current = current.cause
            depth++
        }
    }.lowercase()
    return when {
        "no space" in haystack || "enospc" in haystack -> "Not enough storage on this device."
        "not found" in haystack || "404" in haystack || "401" in haystack ||
            "403" in haystack || "private" in haystack || "auth" in haystack ||
            "credentials" in haystack -> "Repository not found or private."
        else -> "Could not clone this repository."
    }
}

/** Bytes as B, KB or MB with one decimal: the progress line's right half. */
internal fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe < 1_024 -> "$safe B"
        safe < 1_048_576 -> "%.1f KB".format(safe / 1_024.0)
        else -> "%.1f MB".format(safe / 1_048_576.0)
    }
}

/** The size of everything under [dir], for the progress line. Missing counts as empty. */
internal fun dirSize(dir: java.io.File): Long {
    if (!dir.exists()) return 0
    if (dir.isFile) return dir.length()
    return dir.listFiles()?.sumOf { dirSize(it) } ?: 0
}

/** The signed-in shelf: every repository the account can reach, most recently pushed first. */
@Composable
private fun RepositoryList(
    repos: List<GitHubRepo>?,
    query: String,
    onQuery: (String) -> Unit,
    busy: Boolean,
    disabled: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onPick: (GitHubRepo) -> Unit,
) {
    val c = LocalPageColors.current
    val listed = repos
    when {
        listed == null -> Text(
            text = "Asking GitHub for your repositories…",
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )

        listed.isEmpty() -> Text(
            text = "You have no repositories yet. Publish a project first and it will show up " +
                "here.",
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )

        else -> Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.closecode.ui.components.ScreenField(
                value = query,
                onChange = onQuery,
                hint = "Search your repositories",
            )
            val shown = if (query.isBlank()) {
                listed
            } else {
                listed.filter { it.fullName.contains(query.trim(), ignoreCase = true) }
            }
            shown.forEach { repo ->
                RepoRow(
                    repo = repo,
                    disabled = disabled || busy,
                    onClone = { onPick(repo) },
                )
            }
            if (query.isNotBlank() && shown.isEmpty()) {
                Text(
                    text = "Nothing matches “$query”.",
                    color = c.textMuted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
            if (canLoadMore && query.isBlank()) {
                com.closecode.ui.components.OutlineButton(
                    label = if (loadingMore) "Loading…" else "Show more",
                    enabled = !loadingMore && !disabled,
                    onClick = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: GitHubRepo,
    disabled: Boolean,
    onClone: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.surfaceBorder, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcIcon(
            if (repo.isPrivate) com.closecode.ui.ide.CcIcons.Lock else com.closecode.ui.ide.CcIcons.Book,
            c.textMuted,
            16.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = repo.fullName.substringAfter('/'),
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = updatedLine(repo),
                color = c.textMuted,
                fontSize = 11.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Per-row Clone: tapping clones with the repository's own name straight into progress,
        // no form round-trip.
        Box(
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (disabled) c.chipBg else c.popupSelected)
                .clickable(enabled = !disabled, onClick = onClone)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Clone",
                color = if (disabled) c.textFaint else androidx.compose.ui.graphics.Color.White,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun updatedLine(repo: GitHubRepo): String {
    val visibility = if (repo.isPrivate) "Private" else "Public"
    if (repo.pushedAtMillis <= 0) return visibility
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
        repo.pushedAtMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    )
    return "Updated $ago · $visibility"
}
