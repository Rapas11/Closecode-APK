package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.HashChip
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PageBackButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.ScreenCard
import com.closecode.ui.components.ScreenSectionLabel
import com.closecode.ui.components.SpecMenu
import com.closecode.ui.components.SpecMenuDivider
import com.closecode.ui.components.SpecMenuItem
import com.closecode.ui.components.StatusBadge
import com.closecode.ui.components.StatusKind
import com.closecode.ui.components.TextLink
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubException
import com.closecode.ui.github.GitHubRemote
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.GitHubRepo
import com.closecode.ui.github.TokenStore
import com.closecode.ui.git.GitChange
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.git.GitCommit
import com.closecode.ui.git.GitService
import com.closecode.ui.git.GitSnapshot
import com.closecode.ui.git.splitDiff
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Source Control tab is showing. Kept as one value so a reload can never show half a state. */
private sealed interface GitUiState {
    object Loading : GitUiState
    object NoProject : GitUiState
    object NoRepository : GitUiState
    data class Failed(val message: String) : GitUiState
    data class Loaded(val snap: GitSnapshot) : GitUiState
}

/**
 * The push/pull line under the branch name, in plain words: where the commits are, not counts
 * and arrows. Pinned by test because it is the one sentence every Git beginner reads.
 */
internal fun gitStatusText(hasRemote: Boolean, ahead: Int, behind: Int): String = when {
    !hasRemote -> "Not published"
    behind > 0 && ahead > 0 -> "$behind to pull · $ahead to push"
    behind > 0 -> "$behind to pull"
    ahead > 0 -> "$ahead to push"
    else -> "Up to date"
}

/** A change path split for its row: the file name white, the folder small and gray after it. */
internal fun displayFolder(path: String): String = path.substringBeforeLast('/', "")

/**
 * The Source Control tab: local version control first, GitHub second.
 *
 * Reads and writes only the repository inside [projectRoot] through [GitService]. Everything
 * blocking runs off the main thread, and every failure comes back as a sentence on screen
 * rather than as a crash. Sign-in, remotes and pushing arrive with the GitHub client.
 */
@Composable
fun IdeGitPanel(
    projectName: String,
    projectRoot: File?,
    // Bumped by the editor whenever a file is written, created or deleted. CHANGES describes
    // the working tree, so a snapshot taken when the project opened would otherwise go on
    // showing files that have since been edited, reverted or removed.
    revision: Int = 0,
    // A clone builds a brand new project and leaves this one. This panel decides *when* one
    // happens and reports it; the route that runs it lives at screen level, and the caller owns
    // where the finished record lands. No token crosses this boundary: the route reads the store
    // itself, so a sign-in that happens over there is not this panel's to know about.
    onCloneRequest: () -> Unit = {},
    // Bumped whenever the stored token is written from outside this panel — the clone route signs
    // in too. Re-keying the read on it is what stops this panel pushing with a token that has
    // since been replaced, which would look exactly like the failure it replaced.
    tokenEpoch: Int = 0,
    // Opens a side-by-side diff of the file. The panel lives in the drawer, so the route itself
    // lives at screen level; this only says which path was tapped.
    onDiff: (String) -> Unit = {},
    // Opens a commit's before/after diff of one file. Same routing: which commit and path.
    onCommitDiff: (commitId: String, path: String) -> Unit = { _, _ -> },
    // Opens the full history page and one commit's detail page. Routes live at screen level.
    onHistory: () -> Unit = {},
    onCommitOpen: (commitId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()

    val git = remember(projectRoot) { projectRoot?.let { GitService(it) } }
    var state by remember(projectRoot) { mutableStateOf<GitUiState>(GitUiState.Loading) }
    var message by remember(projectRoot) { mutableStateOf("") }
    var busy by remember(projectRoot) { mutableStateOf(false) }
    var error by remember(projectRoot) { mutableStateOf<String?>(null) }
    var namingBranch by remember(projectRoot) { mutableStateOf(false) }
    var switchingBranch by remember(projectRoot) { mutableStateOf(false) }
    var confirmingDiscard by remember(projectRoot) { mutableStateOf(false) }
    var branches by remember(projectRoot) { mutableStateOf(emptyList<String>()) }
    // Composed deepest in the drawer, so Back dismisses these before the drawer's own chain
    // can close the drawer out from under them.
    androidx.activity.compose.BackHandler(
        enabled = namingBranch || switchingBranch || confirmingDiscard,
    ) {
        BackLog.press("git-drawer", "branch=dialogs")
        namingBranch = false
        switchingBranch = false
        confirmingDiscard = false
    }

    // GitHub. The token is read once from private preferences and kept in memory only for as
    // long as this panel is composed; everything that touches the network runs off the main
    // thread through githubAct.
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    val gitHubClient = remember { GitHubClient() }
    var githubToken by remember(tokenEpoch) { mutableStateOf(tokenStore.read()) }
    var account by remember { mutableStateOf<GitHubUser?>(null) }
    // Everything below describes one project. Keyed by it so an error, a note or a linked
    // remote from the last project cannot travel into this one and look like its own.
    var remoteUrl by remember(projectRoot) { mutableStateOf<String?>(null) }
    var githubBusy by remember(projectRoot) { mutableStateOf(false) }
    var githubError by remember(projectRoot) { mutableStateOf<String?>(null) }
    var githubNote by remember(projectRoot) { mutableStateOf<String?>(null) }
    var signInOpen by remember { mutableStateOf(false) }

    var showRepos by remember(projectRoot) { mutableStateOf(false) }
    var repos by remember(projectRoot) { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    // Read straight from `refs/stash` rather than carried on GitSnapshot, so restoring a stash
    // shows up after the reload that follows it without reshaping every snapshot test.
    var hasStash by remember(projectRoot) { mutableStateOf(false) }
    // Commit history: up to 100 rows, newest first. Loaded with the snapshot so a commit
    // made here appears without reopening the tab; null until the first load finishes.
    var history by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    // How many history rows show: 50 first, the rest behind a Load-more button.
    var historyShown by remember(projectRoot) { mutableStateOf(50) }
    // The expanded commit, by id. Its file list loads on demand, not with the history.
    var historyOpen by remember(projectRoot) { mutableStateOf<String?>(null) }
    var historyFiles by remember(projectRoot) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    suspend fun reload() {
        val service = git
        if (service == null) {
            state = GitUiState.NoProject
            hasStash = false
            return
        }
        state = withContext(Dispatchers.IO) {
            if (!service.isVersioned()) {
                GitUiState.NoRepository
            } else {
                // Older projects were initialized before the app kept its rules out of the
                // working tree, so their first status lists generated files the user never
                // touched. Add them to the local exclude file, once.
                service.ensureLocalExcludes()
                val snap = service.snapshot()
                if (snap.isFailure) {
                    GitUiState.Failed(snap.exceptionOrNull()?.message ?: "Could not read the repository.")
                } else {
                    branches = service.branches().getOrElse { emptyList() }
                    history = service.history(100).getOrElse { emptyList() }
                    GitUiState.Loaded(snap.getOrThrow())
                }
            }
        }
        hasStash = withContext(Dispatchers.IO) { service.hasStash() }
        remoteUrl = withContext(Dispatchers.IO) {
            projectRoot?.let { GitHubRemote(it).originUrl() }
        }
    }

    fun actOn(block: GitService.() -> Result<*>) {
        val service = git ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { service.block().getOrThrow() } }
            error = outcome.exceptionOrNull()
                ?.let { describeFailure(it, "That git action did not finish.") }
            reload()
            busy = false
        }
    }

    fun githubAct(block: () -> Result<*>) {
        if (githubBusy) return
        githubBusy = true
        githubError = null
        githubNote = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block().getOrThrow() } }
            githubError = outcome.exceptionOrNull()
                ?.let { describeFailure(it, "GitHub did not finish that.") }
            githubNote = outcome.getOrNull() as? String
            remoteUrl = withContext(Dispatchers.IO) {
                projectRoot?.let { GitHubRemote(it).originUrl() }
            }
            // A push or a pull changes what CHANGES and HISTORY should say. Without this the
            // panel kept the snapshot it read when the project opened, so a successful push
            // left the history looking untouched.
            reload()
            githubBusy = false
        }
    }

    LaunchedEffect(projectRoot, revision) { reload() }

    LaunchedEffect(githubToken) {
        val token = githubToken
        account = if (token == null) {
            null
        } else {
            withContext(Dispatchers.IO) { gitHubClient.currentUser(token).getOrNull() }
        }
    }

    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            // Opaque so the file tree sitting underneath this panel in the drawer never shows
            // through while the Source Control tab is open.
            .background(c.panel)
            .verticalScroll(scroll)
            .padding(bottom = 20.dp),
    ) {
        when (val current = state) {
            GitUiState.NoProject -> PanelNote(
                title = "No project open",
                body = "Open a project to see its version control.",
            )

            GitUiState.Loading -> SectionLabel("VERSION CONTROL")

            GitUiState.NoRepository -> GitIntro(
                busy = busy,
                onTrack = { actOn { init() } },
                onClone = onCloneRequest,
                onSignIn = { signInOpen = true },
            )

            is GitUiState.Failed -> PanelNote(
                title = "Could not read the repository",
                body = current.message,
            )

            is GitUiState.Loaded -> {
                error?.let { ErrorLine(it) }
                BranchRow(
                    snap = current.snap,
                    status = gitStatusText(remoteUrl != null, current.snap.ahead, current.snap.behind),
                    branches = branches,
                    hasStash = hasStash,
                    hasChanges = !current.snap.isClean,
                    busy = busy,
                    hasRemote = remoteUrl != null,
                    toPull = current.snap.behind,
                    toPush = current.snap.ahead,
                    onRefresh = { scope.launch { reload() } },
                    onHistory = onHistory,
                    onSwitchBranch = { switchingBranch = true },
                    onNewBranch = { namingBranch = true },
                    onPull = {
                        val token = githubToken
                        val root = projectRoot
                        if (token != null && root != null) {
                            githubAct { GitHubRemote(root).pull(token) }
                        }
                    },
                    onPush = {
                        val token = githubToken
                        val root = projectRoot
                        if (token != null && root != null) {
                            githubAct { GitHubRemote(root).push(token) }
                        }
                    },
                    onStash = { actOn { stash() } },
                    onRestoreStash = { actOn { stashPop() } },
                    onClone = { onCloneRequest() },
                    onDiscardAll = { confirmingDiscard = true },
                )

                if (current.snap.isClean) {
                    // Nothing to stage, so no commit box either: the screen says done and stops.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 17.dp, end = 14.dp, top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CcIcon(CcIcons.Check, c.success, 16.dp)
                        Text(
                            text = "Everything is saved",
                            color = c.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }
                } else {
                    if (current.snap.conflicts.isNotEmpty()) {
                        ChangeGroup(
                            label = "Conflicts · ${current.snap.conflicts.size}",
                            rows = current.snap.conflicts.map {
                                GitChange(it, false, GitChangeKind.Deleted)
                            },
                            conflicted = true,
                            actionLabel = null,
                            onAction = null,
                            onRow = {},
                            interactive = false,
                        )
                    }
                    ChangeGroup(
                        label = "Staged · ${current.snap.staged.size}",
                        rows = current.snap.staged,
                        actionLabel = if (current.snap.staged.isEmpty()) null else "Unstage all",
                        onAction = { actOn { unstage(current.snap.staged.map { it.path }) } },
                        onRow = { change -> actOn { unstage(listOf(change.path)) } },
                        onDiff = onDiff,
                    )
                    val unstaged = current.snap.unstaged + current.snap.untracked
                    ChangeGroup(
                        label = "Changes · ${unstaged.size}",
                        rows = unstaged,
                        actionLabel = if (unstaged.isEmpty()) null else "Stage all",
                        onAction = { actOn { stage(unstaged.map { it.path }) } },
                        onRow = { change -> actOn { stage(listOf(change.path)) } },
                        onDiff = onDiff,
                    )
                }

                if (!current.snap.isClean) {
                    CommitBox(
                        value = message,
                        busy = busy,
                        staged = current.snap.staged.size,
                        branches = branches,
                        currentBranch = current.snap.branch,
                        onChange = { message = it },
                        onCommit = {
                            val text = message
                            actOn { commit(text).onSuccess { message = "" } }
                        },
                        onSwitchBranch = { name -> actOn { checkout(name) } },
                    )
                }

                if (current.snap.hasCommits) {
                    HistorySection(
                        onHistory = onHistory,
                        commits = history,
                        shown = historyShown,
                        openId = historyOpen,
                        files = historyFiles,
                        onMore = { historyShown = (historyShown + 50).coerceAtMost(100) },
                        onFile = { id, path -> onCommitDiff(id, path) },
                        onToggle = { commit ->
                            historyOpen = if (historyOpen == commit.id) null else commit.id
                            if (historyOpen != null && historyFiles[commit.id] == null) {
                                val service = git
                                if (service != null) {
                                    scope.launch {
                                        val rows = withContext(Dispatchers.IO) {
                                            service.commitFiles(commit.id).getOrElse { emptyList() }
                                        }
                                        historyFiles = historyFiles + (commit.id to rows)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        // Outside the switch on purpose. Signing in, cloning and publishing are what most people
        // open this tab for, and none of them need a repository to exist yet — which is exactly
        // the state someone is in when they want to clone a different one.
        val hasCommits = (state as? GitUiState.Loaded)?.snap?.hasCommits == true
        val snap = (state as? GitUiState.Loaded)?.snap
        GitHubSection(
            projectName = projectName,
            token = githubToken,
            account = account,
            remoteUrl = remoteUrl,
            hasCommits = hasCommits,
            toPull = snap?.behind ?: 0,
            toPush = snap?.ahead ?: 0,
            busy = githubBusy,
            error = githubError,
            note = githubNote,
            showRepos = showRepos,
            repos = repos,
            onToggleRepos = {
                showRepos = !showRepos
                if (showRepos && repos.isEmpty()) {
                    val token = githubToken
                    if (token != null) {
                        githubAct {
                            gitHubClient.repositories(token).onSuccess { repos = it }
                        }
                    }
                }
            },
            onSignIn = { signInOpen = true },
            onClone = { onCloneRequest() },
            onSignOut = {
                tokenStore.clear()
                githubToken = null
                account = null
                repos = emptyList()
                showRepos = false
                githubNote = null
                githubError = null
            },
            onPublish = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct {
                        val created = gitHubClient
                            .createRepository(token, projectName, isPrivate = true)
                            .getOrElse { failure ->
                                // 422 is GitHub saying the name is taken on this account —
                                // usually the same Publish pressed twice. Link what is
                                // already there instead of failing the second time.
                                val taken = (failure as? GitHubException)?.code == 422
                                if (!taken) throw failure
                                gitHubClient.repositories(token).getOrThrow()
                                    .firstOrNull {
                                        it.fullName.substringAfter('/') == projectName
                                    } ?: throw failure
                            }
                        GitHubRemote(root).link(created.cloneUrl).getOrThrow()
                        GitHubRemote(root).push(token)
                    }
                }
            },
            onPush = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct { GitHubRemote(root).push(token) }
                }
            },
            onPull = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct { GitHubRemote(root).pull(token) }
                }
            },
            onLink = { repo ->
                val root = projectRoot
                if (root != null) {
                    showRepos = false
                    githubAct { GitHubRemote(root).link(repo.cloneUrl).map { "Linked ${repo.fullName}." } }
                }
            },
        )
    }

    if (namingBranch && state is GitUiState.Loaded) {
        IdeNameDialog(
            title = "New branch",
            hint = "branch-name",
            onCreate = { name ->
                namingBranch = false
                actOn { createBranch(name) }
            },
            onDismiss = { namingBranch = false },
        )
    }

    if (switchingBranch && state is GitUiState.Loaded) {
        BranchDialog(
            current = (state as GitUiState.Loaded).snap.branch,
            branches = branches,
            onSwitch = { name ->
                switchingBranch = false
                actOn { checkout(name) }
            },
            onDismiss = { switchingBranch = false },
        )
    }

    if (confirmingDiscard && state is GitUiState.Loaded) {
        val snap = (state as GitUiState.Loaded).snap
        val paths = snap.staged.map { it.path } + snap.unstaged.map { it.path } +
            snap.untracked.map { it.path }
        IdeConfirmDialog(
            title = "Throw away ${paths.size} ${if (paths.size == 1) "change" else "changes"}?",
            message = "Every edit goes back to the last commit, and new files are deleted. " +
                "This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmingDiscard = false
                actOn { discard(paths) }
            },
            onDismiss = { confirmingDiscard = false },
        )
    }

    if (signInOpen) {
        GitHubSignIn(
            onSignedIn = { token ->
                tokenStore.write(token)
                githubToken = token
                signInOpen = false
            },
            onDismiss = { signInOpen = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun PanelNote(title: String, body: String) {
    val c = LocalPageColors.current
    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ErrorLine(message: String) {
    val c = LocalPageColors.current
    Text(
        text = message,
        color = c.red,
        fontSize = 11.sp,
        fontFamily = Sans,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 10.dp),
    )
}

/**
 * The first-time screen: a project with no Git history yet. One intro, three option cards,
 * nothing else. "Initialize repository" and "Not under version control" must never appear —
 * the first card says what happens in the words a beginner reads.
 */
@Composable
private fun GitIntro(
    busy: Boolean,
    onTrack: () -> Unit,
    onClone: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.primaryTint),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Branch, c.link, 24.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Get started with Git",
            color = c.text,
            fontSize = 17.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Git saves versions of your project so you can undo mistakes and back it up online.",
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        // The one primary action: start tracking. The other two are plain cards.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.primary)
                .clickable(enabled = !busy, onClick = onTrack)
                .padding(12.dp),
        ) {
            IntroCardRow(
                tileBg = Color.White.copy(alpha = 0.2f),
                tileFg = Color.White,
                icon = CcIcons.Play,
                title = "Start tracking this project",
                subtitle = if (busy) "Working…" else "Create a Git history on this device",
                titleColor = Color.White,
                subtitleColor = Color.White.copy(alpha = 0.85f),
                chevronColor = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        IntroCard(
            icon = CcIcons.CloudDownload,
            title = "Clone a repository",
            subtitle = "Download a project from GitHub",
            onClick = onClone,
        )
        Spacer(Modifier.height(12.dp))
        IntroCard(
            icon = CcIcons.Github,
            title = "Sign in with GitHub",
            subtitle = "Back up and sync your projects",
            onClick = onSignIn,
        )
    }
}

@Composable
private fun IntroCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        IntroCardRow(
            tileBg = c.chipBg,
            tileFg = c.textMuted,
            icon = icon,
            title = title,
            subtitle = subtitle,
            titleColor = c.text,
            subtitleColor = c.textMuted,
            chevronColor = c.textFaint,
        )
    }
}

@Composable
private fun IntroCardRow(
    tileBg: androidx.compose.ui.graphics.Color,
    tileFg: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color,
    subtitleColor: androidx.compose.ui.graphics.Color,
    chevronColor: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tileBg),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, tileFg, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 11.sp,
                fontFamily = Sans,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CcIcon(CcIcons.Chevron, chevronColor, 16.dp)
    }
}

/**
 * The branch row: branch icon and name on the left, refresh plus the plain-words status on the
 * right, and a three-dot menu holding everything that is not staging, committing or pushing —
 * switch and new branch, stash and restore, discard all.
 */
@Composable
private fun BranchRow(
    snap: GitSnapshot,
    status: String,
    branches: List<String>,
    hasStash: Boolean,
    hasChanges: Boolean,
    busy: Boolean,
    hasRemote: Boolean,
    toPull: Int,
    toPush: Int,
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    onSwitchBranch: () -> Unit,
    onNewBranch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onStash: () -> Unit,
    onRestoreStash: () -> Unit,
    onClone: () -> Unit,
    onDiscardAll: () -> Unit,
) {
    val c = LocalPageColors.current
    var menuOpen by remember { mutableStateOf(false) }
    // The menu floats under the row, aligned to the row's right edge. The transparent
    // layer eats outside taps; there is no dim behind the menu itself.
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 17.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Branch chip: icon, name and chevron open the switcher.
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = branches.size > 1, onClick = onSwitchBranch)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcIcon(CcIcons.Branch, c.textMuted, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = snap.branch,
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CcIcon(CcIcons.Down, c.textMuted, 13.dp)
            }
            if (snap.detached) {
                Text(
                    text = "detached HEAD",
                    color = c.warning,
                    fontSize = 10.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            BranchIconButton(icon = CcIcons.Clock, label = "History", onClick = onHistory)
            BranchBadgeButton(
                icon = CcIcons.Download,
                label = "Pull",
                count = toPull,
                enabled = hasRemote && !busy,
                onClick = onPull,
            )
            BranchBadgeButton(
                icon = CcIcons.Upload,
                label = "Push",
                count = toPush,
                enabled = hasRemote && !busy,
                onClick = onPush,
            )
            // A 30 dp touch box with a 16 dp glyph inside: the thumb gets room, the eye gets the
            // small icon from the mockup. Passing a big box *as* the icon's own modifier stretches
            // the glyph to the box, which is how the refresh drew huge.
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Refresh, c.textMuted, 16.dp)
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.More, c.textMuted, 16.dp)
            }
        }
        if (menuOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = false },
                    ),
            )
            SpecMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 46.dp, end = 8.dp)
                    .width(220.dp),
            ) {
                SpecMenuItem(
                    icon = CcIcons.Clock, label = "View history",
                    onClick = { menuOpen = false; onHistory() },
                )
                SpecMenuDivider()
                SpecMenuItem(
                    icon = CcIcons.Branch, label = "Switch branch",
                    enabled = branches.size > 1,
                    onClick = { menuOpen = false; onSwitchBranch() },
                )
                SpecMenuItem(
                    icon = CcIcons.Plus, label = "New branch",
                    onClick = { menuOpen = false; onNewBranch() },
                )
                SpecMenuDivider()
                SpecMenuItem(
                    icon = CcIcons.Download, label = "Pull",
                    enabled = hasRemote && !busy,
                    onClick = { menuOpen = false; onPull() },
                )
                SpecMenuItem(
                    icon = CcIcons.Upload, label = "Push",
                    enabled = hasRemote && !busy,
                    onClick = { menuOpen = false; onPush() },
                )
                if (hasChanges && !busy) {
                    SpecMenuItem(
                        icon = CcIcons.Archive, label = "Stash changes",
                        onClick = { menuOpen = false; onStash() },
                    )
                }
                if (hasStash && !busy) {
                    SpecMenuItem(
                        icon = CcIcons.Archive, label = "Restore stash",
                        onClick = { menuOpen = false; onRestoreStash() },
                    )
                }
                SpecMenuItem(
                    icon = CcIcons.CloudDownload, label = "Clone a repository",
                    onClick = { menuOpen = false; onClone() },
                )
                SpecMenuDivider()
                if (hasChanges && !busy) {
                    SpecMenuItem(
                        icon = CcIcons.Trash, label = "Discard all changes", danger = true,
                        onClick = { menuOpen = false; onDiscardAll() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, c.textMuted, 16.dp)
    }
}

@Composable
private fun BranchBadgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, if (enabled) c.textMuted else c.textFaint, 16.dp)
        // The blue count bubble: 16 dp tall, white number. Hidden at zero.
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(c.runFill)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else "$count",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Every branch, with a check on the current one. Tapping one switches to it. */
@Composable
private fun BranchDialog(
    current: String,
    branches: List<String>,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch branch", fontSize = 17.sp, fontFamily = Sans) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                branches.forEach { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSwitch(name) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            color = c.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = if (name == current) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (name == current) CcIcon(CcIcons.Check, c.success, 16.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * One list inside the Changes section — staged, changes or conflicts — with a bulk action in
 * its header. Groups with nothing in them disappear entirely. Tapping a row opens its diff;
 * the round button on the right stages or unstages.
 */
@Composable
private fun ChangeGroup(
    label: String,
    rows: List<GitChange>,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onRow: (GitChange) -> Unit,
    onDiff: (String) -> Unit = {},
    conflicted: Boolean = false,
    interactive: Boolean = true,
) {
    val c = LocalPageColors.current
    if (rows.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenSectionLabel(label, Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextLink(actionLabel, onClick = onAction)
        }
    }

    rows.forEach { change ->
        ChangeRow(
            kind = change.kind,
            path = change.path,
            staged = change.staged,
            conflicted = conflicted,
            interactive = interactive,
            onClick = { onDiff(change.path) },
            onToggle = { onRow(change) },
        )
    }
}

@Composable
private fun ChangeRow(
    kind: GitChangeKind,
    path: String,
    staged: Boolean,
    conflicted: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val c = LocalPageColors.current
    val name = path.substringAfterLast('/')
    val folder = displayFolder(path)
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clickable(enabled = interactive, onClick = onClick)
            .padding(start = 17.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conflicted) {
            // Conflicted files sort first with a red C: the letter is the alarm here, so the
            // badge borrows the error colors rather than inventing new ones.
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.statusDeletedBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    color = c.error,
                    fontSize = 10.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            StatusBadge(
                when (kind) {
                    GitChangeKind.Added -> StatusKind.Added
                    GitChangeKind.Modified -> StatusKind.Modified
                    GitChangeKind.Deleted -> StatusKind.Deleted
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder.isNotEmpty()) {
                Text(
                    text = folder,
                    color = c.textMuted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (interactive) {
            // Round, 26 dp, minus to unstage and plus to stage — the row's tap opens the diff,
            // so the button owns the stage action alone and never fights the row over a tap.
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c.chipBg)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (staged) "−" else "+",
                    color = c.text,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CommitBox(
    value: String,
    busy: Boolean,
    staged: Int,
    branches: List<String>,
    currentBranch: String,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
    onSwitchBranch: (String) -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = !busy,
            textStyle = TextStyle(
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Message: what did you change?",
                            color = c.textFaint,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }
                    inner()
                }
            },
        )
        // The one primary button, split: the commit action plus a chevron that shows
        // the branches to commit onto. Gray and dead until a message and a staged
        // file both exist.
        var branchPicker by remember { mutableStateOf(false) }
        val commitReady = staged > 0 && value.isNotBlank() && !busy
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (commitReady) c.primary else c.chipBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = commitReady, onClick = onCommit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        busy -> "Working…"
                        else -> "Commit $staged ${if (staged == 1) "file" else "files"}"
                    },
                    color = if (commitReady) Color.White else c.textFaint,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        if (commitReady) Color.White.copy(alpha = 0.35f) else c.fieldBorder,
                    ),
            )
            Box(
                Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(enabled = !busy, onClick = { branchPicker = true })
                    .semantics { contentDescription = "Choose branch" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(
                    CcIcons.Down,
                    if (commitReady) Color.White else c.textFaint,
                    16.dp,
                )
            }
        }
        if (branchPicker) {
            BranchDialog(
                current = currentBranch,
                branches = branches,
                onSwitch = { name ->
                    branchPicker = false
                    onSwitchBranch(name)
                },
                onDismiss = { branchPicker = false },
            )
        }
    }
}

/**
 * The GitHub half of the panel: sign in, publish, pull and push.
 *
 * Everything here is downstream of a local repository — a project with no commits has nothing
 * to publish, and a project with no remote has nothing to push to — so each action states its
 * own precondition rather than failing after the press.
 */
@Composable
private fun GitHubSection(
    projectName: String,
    token: String?,
    account: GitHubUser?,
    remoteUrl: String?,
    hasCommits: Boolean,
    toPull: Int,
    toPush: Int,
    busy: Boolean,
    error: String?,
    note: String?,
    showRepos: Boolean,
    repos: List<GitHubRepo>,
    onToggleRepos: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPublish: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onLink: (GitHubRepo) -> Unit,
    onClone: () -> Unit,
) {
    val c = LocalPageColors.current
    // The section says what it is *and* whether it can do anything. "GITHUB" alone reads like a
    // heading over a panel that is about to work; with no token it is a heading over a sign-in
    // button, and saying so saves the tap that finds that out.
    SectionLabel(if (token == null) "GITHUB · NOT CONNECTED" else "GITHUB")

    error?.let { ErrorLine(it) }
    note?.let {
        Text(
            text = it,
            color = c.success,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 8.dp),
        )
    }

    // The X hides the card for this view; signing in replaces the whole section anyway.
    var cardHidden by remember { mutableStateOf(false) }
    if (token == null && !cardHidden) {
        // Compact card: what is missing, why it matters, Connect and a dismiss X.
        // Shown only when not signed in.
        Column(Modifier.padding(start = 17.dp, end = 14.dp, top = 16.dp)) {
            ScreenCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Github, c.text, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "GitHub is not connected",
                            color = c.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Back up and sync this project",
                            color = c.textMuted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "Connect",
                        color = c.link,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onSignIn)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    CcIcon(
                        CcIcons.Close,
                        c.textMuted,
                        16.dp,
                        Modifier
                            .clickable(onClick = { cardHidden = true })
                            .padding(4.dp),
                    )
                }
            }
        }
        return
    }
    if (token == null) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "@${account?.login ?: "signed in"}",
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Sign out",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onSignOut),
        )
    }

    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (remoteUrl == null) {
            if (!hasCommits) {
                Text(
                    text = "Commit something first — a repository with no history has nothing to publish.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
            } else {
                Text(
                    text = "A private repository named \"$projectName\" will be created under " +
                        "${account?.login ?: "your account"} and this project pushed to it.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
                OutlineButton(
                    label = if (busy) "Working…" else "Publish to GitHub",
                    enabled = !busy,
                    onClick = onPublish,
                )
            }
        } else {
            if (!hasCommits) {
                Text(
                    text = "Commit something first — a repository with no history has nothing to push.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = if (toPull > 0) "Pull · $toPull" else "Pull",
                        enabled = !busy,
                        onClick = onPull,
                    )
                }
                // Push with an unborn HEAD is a guaranteed transport failure; say so up front
                // rather than handing the user JGit's opaque wrapper.
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = if (toPush > 0) "Push · $toPush" else "Push",
                        enabled = !busy && hasCommits,
                        onClick = onPush,
                    )
                }
            }
            Text(
                text = remoteUrl,
                color = c.mu,
                fontSize = 10.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = if (showRepos) "Hide repositories" else "Use an existing repository",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onToggleRepos),
        )
        Text(
            text = "Clone a repository",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(enabled = !busy, onClick = onClone),
        )
    }

    if (showRepos) {
        if (repos.isEmpty()) {
            Text(
                text = "Loading your repositories…",
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        } else {
            repos.forEach { repo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clickable(enabled = !busy) { onLink(repo) }
                        .padding(start = 17.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = repo.fullName,
                        color = c.tx,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (repo.isPrivate) "private" else "public",
                        color = c.mu,
                        fontSize = 10.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * A side-by-side diff of one file. Working-tree mode (no commit id): HEAD on the left,
 * the working tree on the right. Commit mode: the commit's parent on the left, the commit
 * itself on the right — the before and after of that commit. Changed rows tint green on
 * the new side and red on the old side; added and removed lines show on their side only.
 * Each side scrolls sideways on its own (one shared state per side, so all rows travel
 * together) while the rows scroll vertically as one.
 */
@Composable
internal fun DiffScreen(
    projectRoot: File?,
    path: String,
    onBack: () -> Unit,
    commitId: String? = null,
    onOpenFile: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    var rows by remember(path, commitId) { mutableStateOf<List<com.closecode.ui.git.DiffRow>?>(null) }
    var failed by remember(path, commitId) { mutableStateOf(false) }
    var binary by remember(path, commitId) { mutableStateOf(false) }
    // Unified by default; side by side only on wide screens.
    var unified by remember(path, commitId) { mutableStateOf(true) }
    var wrapLines by remember(path, commitId) { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var hint by remember(path, commitId) { mutableStateOf<String?>(null) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(path, commitId) {
        val root = projectRoot
        if (root == null) {
            failed = true
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val id = commitId
                if (id == null) {
                    val old = GitService(root).fileAtHead(path).getOrNull()
                    val file = File(root, path)
                    old to if (file.isFile) file.readBytes() else ByteArray(0)
                } else {
                    val sides = GitService(root).commitFileContents(id, path).getOrNull()
                    (sides?.first) to (sides?.second ?: ByteArray(0))
                }
            }.getOrNull()
        }
        if (loaded == null) {
            failed = true
            return@LaunchedEffect
        }
        // A nul byte in the probed head means binary: decoded text would be garbage, and a
        // wall of replacement characters is not a diff.
        if (loaded.first?.take(8000)?.any { it == 0.toByte() } == true ||
            loaded.second.take(8000).any { it == 0.toByte() }
        ) {
            binary = true
            return@LaunchedEffect
        }
        rows = splitDiff(
            loaded.first?.toString(Charsets.UTF_8).orEmpty(),
            loaded.second.toString(Charsets.UTF_8),
        )
    }

    BackHandler(onBack = {
        BackLog.press("git-diff", "branch=close")
        onBack()
    })
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
    ) {
        val current = rows
        val added = current?.count { it.newLine != null && it.newLine != it.oldLine } ?: 0
        val removed = current?.count { it.oldLine != null && it.oldLine != it.newLine } ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = path.substringAfterLast('/'),
                    color = c.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (commitId != null) {
                    Text(
                        text = commitId.take(7),
                        color = c.textFaint,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                    )
                }
            }
            if (current != null) {
                Text(
                    text = "+$added ",
                    color = c.success,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
                Text(
                    text = "−$removed",
                    color = c.error,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { moreOpen = true }
                        .semantics { contentDescription = "Diff options" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.More, c.textMuted, 18.dp)
                }
                if (moreOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { moreOpen = false },
                            ),
                    )
                    SpecMenu(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(220.dp),
                    ) {
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (wrapLines) "✓ " else "") + "Wrap lines",
                            onClick = { moreOpen = false; wrapLines = !wrapLines },
                        )
                    }
                }
            }
        }
        com.closecode.ui.components.AppSegmentedControl(
            items = listOf(
                com.closecode.ui.components.SegmentItem("Unified"),
                com.closecode.ui.components.SegmentItem("Side by side"),
            ),
            selected = if (unified) 0 else 1,
            onSelect = {
                if (it == 1) {
                    if (wide) {
                        unified = false
                        hint = null
                    } else {
                        hint = "Rotate to use side by side"
                    }
                } else {
                    unified = true
                    hint = null
                }
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        hint?.let {
            Text(
                text = it,
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 6.dp),
            )
        }
        when {
            failed -> DiffNote("Could not read the diff.")
            binary -> DiffNote("This file is not text, so there is nothing to show side by side.")
            current == null -> DiffNote("Reading the diff…")
            current.isEmpty() -> DiffNote("No differences.")
            current.size > 5000 -> {
                DiffNote("Too large to show here (${current.size} rows).")
                if (onOpenFile != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        com.closecode.ui.components.OutlineButton(
                            label = "Open the file",
                            onClick = onOpenFile,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
            unified -> {
                val unifiedRows = remember(current) { unify(current) }
                val isNew = unifiedRows.all { it.oldNo == null } && unifiedRows.any { it.added }
                val isDel = unifiedRows.all { it.newNo == null } && unifiedRows.any { it.removed }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    UnifiedFileCard(
                        fileName = path.substringAfterLast('/'),
                        folder = path.substringBeforeLast('/', ""),
                        tag = when {
                            isNew -> "New file"
                            isDel -> "Deleted file"
                            else -> "+$added −$removed"
                        },
                        tagAdded = isNew,
                        tagDeleted = isDel,
                        added = added,
                        removed = removed,
                        rows = unifiedRows,
                        wrap = wrapLines,
                    )
                }
            }
            else -> {
                // One sideways state per side: every row's cell shares its side's state, so a
                // long line scrolled on one row stays readable while the rows move together.
                val leftScroll = rememberScrollState()
                val rightScroll = rememberScrollState()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        current.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                DiffCell(
                                    text = row.oldLine,
                                    changed = row.oldLine != row.newLine,
                                    isOld = true,
                                    scroll = leftScroll,
                                )
                                DiffCell(
                                    text = row.newLine,
                                    changed = row.oldLine != row.newLine,
                                    isOld = false,
                                    scroll = rightScroll,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Commit history: newest first, up to 100 rows with 50 shown before Load more.
 * Tapping a row expands it to the full id, the date and the files that commit
 * touched, so the reader sees what changed and when without leaving the drawer.
 */
@Composable
private fun HistorySection(
    commits: List<GitCommit>?,
    shown: Int,
    openId: String?,
    files: Map<String, List<String>>,
    onMore: () -> Unit,
    onToggle: (GitCommit) -> Unit,
    onFile: (commitId: String, path: String) -> Unit = { _, _ -> },
    onHistory: () -> Unit = {},
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SectionLabel("HISTORY · ${commits?.size ?: 0}")
        }
        Text(
            text = "View all",
            color = c.link,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.clickable(onClick = onHistory),
        )
    }
    when {
        commits == null -> {
            Text(
                text = "Loading history…",
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        }
        commits.isEmpty() -> {
            Text(
                text = "No commits yet. Commit staged files to start the history.",
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        }
        else -> {
            commits.take(shown.coerceAtMost(commits.size)).forEach { commit ->
                val open = openId == commit.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onToggle(commit) })
                        .padding(start = 17.dp, end = 14.dp, top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = commit.shortId,
                            color = c.accent,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = commit.summary,
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            maxLines = if (open) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "${commit.author} · ${relativeTime(commit.whenMillis)}",
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (open) {
                        Text(
                            text = commit.id,
                            color = c.textFaint,
                            fontSize = 10.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val rows = files[commit.id]
                        when {
                            rows == null -> {
                                Text(
                                    text = "Loading files…",
                                    color = c.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = Sans,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            rows.isEmpty() -> {
                                Text(
                                    text = "No file changes listed for this commit.",
                                    color = c.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = Sans,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            else -> {
                                rows.forEach { path ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = { onFile(commit.id, path) })
                                            .padding(top = 2.dp),
                                    ) {
                                        Text(
                                            text = path,
                                            color = c.accent,
                                            fontSize = 11.sp,
                                            fontFamily = Mono,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        CcIcon(
                                            CcIcons.Chevron,
                                            c.textFaint,
                                            14.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (shown < commits.size) {
                Text(
                    text = "Show more (${commits.size - shown} older)",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onMore)
                        .padding(start = 17.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                )
            }
        }
    }
}

private fun relativeTime(whenMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - whenMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/**
 * One unified diff row: old/new line numbers, a sign column and the text.
 */
internal data class UnifiedRow(
    val oldNo: Int?,
    val newNo: Int?,
    val sign: Char,
    val text: String,
    val added: Boolean,
    val removed: Boolean,
)

internal fun unify(rows: List<com.closecode.ui.git.DiffRow>): List<UnifiedRow> {
    var oldNo = 0
    var newNo = 0
    return rows.map { row ->
        if (row.oldLine != null) oldNo++
        if (row.newLine != null) newNo++
        val added = row.newLine != null && row.newLine != row.oldLine
        val removed = row.oldLine != null && row.oldLine != row.newLine
        UnifiedRow(
            oldNo = row.oldLine?.let { oldNo },
            newNo = row.newLine?.let { newNo },
            sign = when {
                added -> '+'
                removed -> '−'
                else -> ' '
            },
            text = row.newLine ?: row.oldLine.orEmpty(),
            added = added,
            removed = removed,
        )
    }
}

internal sealed interface DiffBlock {
    data class Lines(val rows: List<IndexedValue<UnifiedRow>>) : DiffBlock
    data class Gap(val id: Int, val hidden: Int) : DiffBlock
}

/**
 * Collapses long unchanged runs: 3 lines of context around changes, a `Show N more
 * lines` bar for the rest (+100 per tap). A new file shows its first 6 lines.
 */
internal fun collapseUnified(
    rows: List<UnifiedRow>,
    isNew: Boolean,
    revealed: Map<Int, Int>,
): List<DiffBlock> {
    if (rows.isEmpty()) return emptyList()
    val changed = rows.indices.filter { rows[it].added || rows[it].removed }
    if (changed.isEmpty()) return listOf(DiffBlock.Lines(rows.withIndex().toList()))
    if (isNew) {
        val show = (6 + (revealed[-1] ?: 0)).coerceAtMost(rows.size)
        val out = mutableListOf<DiffBlock>(
            DiffBlock.Lines(rows.subList(0, show).mapIndexed { i, r -> IndexedValue(i, r) }),
        )
        if (show < rows.size) out.add(DiffBlock.Gap(-1, rows.size - show))
        return out
    }
    val out = mutableListOf<DiffBlock>()
    var gap = 0
    fun unchanged(from: Int, until: Int, head: Boolean, tail: Boolean) {
        val len = until - from
        if (len <= 0) return
        val keep = when {
            head && tail -> len
            else -> 3
        }
        if (len <= keep * 2 && !(head xor tail)) {
            out.add(DiffBlock.Lines((from until until).map { IndexedValue(it, rows[it]) }))
            return
        }
        val extra = revealed[gap] ?: 0
        if (head && !tail) {
            // Leading run: keep the last lines.
            val keepCount = (3 + extra).coerceAtMost(len)
            if (keepCount < len) {
                out.add(DiffBlock.Gap(gap, len - keepCount))
                gap++
            }
            out.add(
                DiffBlock.Lines(
                    ((from + len - keepCount) until until).map { IndexedValue(it, rows[it]) },
                ),
            )
        } else {
            // Middle or trailing run: keep the first lines, tail 3 for middle runs.
            val keepCount = (3 + extra).coerceAtMost(len)
            val tailKeep = if (!head && !tail) 3 else 0
            val headEnd = (from + keepCount).coerceAtMost(until - tailKeep)
            out.add(DiffBlock.Lines((from until headEnd).map { IndexedValue(it, rows[it]) }))
            val hidden = (until - tailKeep) - headEnd
            if (hidden > 0) {
                out.add(DiffBlock.Gap(gap, hidden))
                gap++
            }
            if (tailKeep > 0) {
                out.add(
                    DiffBlock.Lines(
                        ((until - tailKeep) until until).map { IndexedValue(it, rows[it]) },
                    ),
                )
            }
        }
    }
    // Leading context, changed blocks with middle context between them, then trailing
    // context: each handled once, so the end of the file can never vanish or repeat.
    val first = changed.first()
    val last = changed.last()
    unchanged(0, first, head = true, tail = false)
    var cursor = first
    while (cursor <= last) {
        var j = cursor
        while (j <= last && (rows[j].added || rows[j].removed)) j++
        out.add(DiffBlock.Lines((cursor until j).map { IndexedValue(it, rows[it]) }))
        cursor = j
        if (cursor > last) break
        var k = cursor
        while (k <= last && !(rows[k].added || rows[k].removed)) k++
        unchanged(cursor, k, head = false, tail = false)
        cursor = k
    }
    if (last + 1 < rows.size) {
        unchanged(last + 1, rows.size, head = false, tail = true)
    }
    return out
}

/**
 * One file's unified diff with collapsing. Used inline on the commit page and,
 * for a single file, inside the full-screen diff.
 */
@Composable
private fun UnifiedFileCard(
    fileName: String,
    folder: String,
    tag: String,
    tagAdded: Boolean,
    tagDeleted: Boolean,
    added: Int,
    removed: Int,
    rows: List<UnifiedRow>?,
    onOpenFull: (() -> Unit)? = null,
    wrap: Boolean = false,
) {
    val c = LocalPageColors.current
    var revealed by remember(fileName) { mutableStateOf(emptyMap<Int, Int>()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.fieldBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(c.panel2)
                .clickable(enabled = onOpenFull != null, onClick = { onOpenFull?.invoke() })
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon(CcIcons.Document, c.textMuted, 15.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = fileName,
                color = c.text,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val (tagBg, tagFg) = when {
                tagAdded -> c.spec.diffAddBg to c.success
                tagDeleted -> c.spec.diffDelBg to c.error
                else -> c.chipBg to c.textMuted
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(tagBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = tag, color = tagFg, fontSize = 11.sp, fontFamily = Sans, maxLines = 1)
            }
        }
        if (rows == null) {
            Text(
                text = "Reading…",
                color = c.textMuted,
                fontSize = 11.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(10.dp),
            )
            return@Column
        }
        if (rows.size > 5000) {
            Text(
                text = "Too large to show here",
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(10.dp),
            )
            return@Column
        }
        val isNew = rows.all { it.newNo != null && it.oldNo == null } && rows.any { it.added }
        val blocks = remember(rows) { collapseUnified(rows, isNew, emptyMap()) }
        // Re-collapse when a gap opens: revealed grows, blocks recompute.
        val live = collapseUnified(rows, isNew, revealed)
        (if (revealed.isEmpty()) blocks else live).forEach { block ->
            when (block) {
                is DiffBlock.Lines -> {
                    block.rows.forEach { indexed ->
                        val row = indexed.value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        row.added -> c.spec.diffAddBg
                                        row.removed -> c.spec.diffDelBg
                                        else -> c.fieldBg
                                    },
                                )
                                .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.oldNo?.toString().orEmpty(),
                                color = c.textFaint,
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                modifier = Modifier.width(26.dp),
                                maxLines = 1,
                            )
                            if (rows.any { it.oldNo != null } && rows.any { it.newNo != null }) {
                                Text(
                                    text = row.newNo?.toString().orEmpty(),
                                    color = c.textFaint,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    modifier = Modifier.width(26.dp),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = "${row.sign} ",
                                color = when {
                                    row.added -> c.success
                                    row.removed -> c.error
                                    else -> c.spec.diffContext
                                },
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                modifier = Modifier.width(14.dp),
                                maxLines = 1,
                            )
                            Text(
                                text = row.text,
                                color = when {
                                    row.added || row.removed -> c.text
                                    else -> c.spec.diffContext
                                },
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                lineHeight = 20.sp,
                                softWrap = wrap,
                                maxLines = if (wrap) Int.MAX_VALUE else 1,
                            )
                        }
                    }
                }
                is DiffBlock.Gap -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(c.panel2)
                            .clickable {
                                revealed = revealed + (block.id to ((revealed[block.id] ?: 0) + 100))
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon(CcIcons.Down, c.link, 14.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Show ${block.hidden} more lines",
                            color = c.link,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                }
            }
        }
    }
}

private fun monthLabel(whenMillis: Long): String {
    val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US)
    return format.format(java.util.Date(whenMillis)).uppercase()
}

private fun historyFilterLabel(filter: Int): String = when (filter) {
    1 -> "This week"
    2 -> "This month"
    else -> "All commits"
}

/**
 * The full history page: month groups on a timeline rail, author, relative time and
 * hash/HEAD/branch chips per row. Search matches message, author, hash and file names;
 * the funnel limits by time.
 */
@Composable
internal fun HistoryPage(
    projectName: String,
    branch: String,
    projectRoot: File?,
    headId: String?,
    onBack: () -> Unit,
    onCommit: (commitId: String) -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    var commits by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }
    var filterOpen by remember { mutableStateOf(false) }
    var fileIndex by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    LaunchedEffect(projectRoot) {
        val root = projectRoot ?: return@LaunchedEffect
        commits = withContext(Dispatchers.IO) {
            GitService(root).history(100).getOrElse { emptyList() }
        }
    }
    // File names join the search once the query starts: one IO pass over the commits.
    LaunchedEffect(query, commits) {
        if (query.length < 2 || commits == null || projectRoot == null) return@LaunchedEffect
        val root = projectRoot
        fileIndex = withContext(Dispatchers.IO) {
            val git = GitService(root)
            commits.orEmpty().associate { commit ->
                commit.id to git.commitFiles(commit.id).getOrElse { emptyList() }
            }
        }
    }

    BackHandler(onBack = {
        com.closecode.ui.ide.BackLog.press("history", "branch=close")
        onBack()
    })
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "History",
                    color = c.brightText,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = "$projectName · $branch",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!searching) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = { searching = true })
                        .semantics { contentDescription = "Search history" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Search, c.textMuted, 18.dp)
                }
            }
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = { filterOpen = true })
                        .semantics { contentDescription = "Filter history" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Filter, c.textMuted, 18.dp)
                }
                if (filterOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { filterOpen = false },
                            ),
                    )
                    SpecMenu(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(200.dp),
                    ) {
                        listOf("All commits", "This week", "This month").forEachIndexed { i, label ->
                            SpecMenuItem(
                                icon = CcIcons.Filter,
                                label = label + if (filter == i) " ✓" else "",
                                onClick = { filterOpen = false; filter = i },
                            )
                        }
                    }
                }
            }
        }
        if (searching) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = Sans),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.fieldBg)
                    .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search message, author, hash or file…",
                                color = c.textFaint,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        val all = commits
        if (all == null) {
            Text(
                text = "Reading history…",
                color = c.textMuted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val shown = all.filter { commit ->
            if (filter == 1 && commit.whenMillis < weekAgo) return@filter false
            if (filter == 2 && commit.whenMillis < monthAgo) return@filter false
            if (query.isBlank()) return@filter true
            val q = query.trim().lowercase()
            commit.summary.lowercase().contains(q) ||
                commit.author.lowercase().contains(q) ||
                commit.id.lowercase().startsWith(q) ||
                (fileIndex[commit.id].orEmpty().any { it.lowercase().contains(q) })
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Newest commit reads as HEAD unless the caller says otherwise.
            val head = headId ?: all.firstOrNull()?.id
            var lastMonth = ""
            shown.forEach { commit ->
                val isHead = commit.id == head
                val month = monthLabel(commit.whenMillis)
                if (month != lastMonth) {
                    lastMonth = month
                    Text(
                        text = month,
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onCommit(commit.id) })
                        .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    // Timeline rail: a 2 dp line with a filled dot for HEAD, hollow below.
                    Box(
                        Modifier
                            .width(20.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(c.spec.railLine),
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isHead) {
                                        Modifier.background(c.runFill)
                                    } else {
                                        Modifier.border(2.dp, c.runFill, CircleShape)
                                    },
                                ),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = commit.summary,
                            color = c.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(c.panel2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = commit.author.take(1).uppercase(),
                                    color = c.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = Sans,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${commit.author} · ${relativeTime(commit.whenMillis)}",
                                color = c.textMuted,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            HashChip(commit.shortId)
                            if (isHead) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(c.spec.diffAddBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = "HEAD",
                                        color = c.success,
                                        fontSize = 11.sp,
                                        fontFamily = Sans,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(c.spec.hashChipBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = branch,
                                        color = c.spec.hashChipText,
                                        fontSize = 11.sp,
                                        fontFamily = Sans,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    text = "No commits match.",
                    color = c.textMuted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * One commit's detail page: message, author, full hash with copy, file stats, the
 * changed files with their counts, and each file's unified diff inline. The ⋮ menu
 * holds Copy full hash, Checkout this version and Revert this commit.
 */
@Composable
internal fun CommitPage(
    commitId: String,
    projectRoot: File?,
    revision: Int,
    onBack: () -> Unit,
    onDiff: (commitId: String, path: String) -> Unit,
    onChanged: () -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var commit by remember(commitId) { mutableStateOf<GitCommit?>(null) }
    var files by remember(commitId) { mutableStateOf<List<String>?>(null) }
    var diffs by remember(commitId) { mutableStateOf<Map<String, List<UnifiedRow>>>(emptyMap()) }
    var tooLarge by remember(commitId) { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(commitId, projectRoot, revision) {
        val root = projectRoot ?: return@LaunchedEffect
        val found = withContext(Dispatchers.IO) {
            GitService(root).history(100).getOrElse { emptyList() }.firstOrNull { it.id == commitId }
        }
        commit = found
        if (found == null) return@LaunchedEffect
        val paths = withContext(Dispatchers.IO) {
            GitService(root).commitFiles(commitId).getOrElse { emptyList() }
        }
        files = paths
        withContext(Dispatchers.IO) {
            paths.forEach { path ->
                val sides = GitService(root).commitFileContents(commitId, path).getOrNull()
                if (sides != null) {
                    val (before, after) = sides
                    if ((before?.size ?: 0) + (after?.size ?: 0) > 400_000) {
                        tooLarge = tooLarge + path
                    } else {
                        val rows = unify(
                            splitDiff(
                                before?.toString(Charsets.UTF_8).orEmpty(),
                                after?.toString(Charsets.UTF_8).orEmpty(),
                            ),
                        )
                        diffs = diffs + (path to rows)
                    }
                }
            }
        }
    }

    fun runGit(label: String, block: GitService.() -> Result<*>) {
        val root = projectRoot ?: return
        if (busy) return
        busy = true
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { GitService(root).block().getOrThrow() }
            }
            note = outcome.exceptionOrNull()?.message ?: "$label done."
            busy = false
            if (outcome.isSuccess) onChanged()
        }
    }

    BackHandler(onBack = {
        com.closecode.ui.ide.BackLog.press("commit", "branch=close")
        onBack()
    })
    confirm?.let { kind ->
        IdeConfirmDialog(
            title = if (kind == "checkout") "Go back to this version?" else "Undo this commit?",
            message = if (kind == "checkout") {
                "Your files will look exactly like this commit. Uncommitted work stays, " +
                    "but switch back to your branch to keep working."
            } else {
                "A new commit will undo these changes. Conflicts stop it with a message."
            },
            confirmLabel = if (kind == "checkout") "Check out" else "Revert",
            onConfirm = {
                confirm = null
                if (kind == "checkout") runGit("Checked out") { checkoutCommit(commitId) }
                else runGit("Reverted") { revertCommit(commitId) }
            },
            onDismiss = { confirm = null },
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Commit",
                    color = c.brightText,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = commitId.take(7),
                    color = c.textMuted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
            }
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { menuOpen = true }
                        .semantics { contentDescription = "Commit actions" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.More, c.textMuted, 18.dp)
                }
                if (menuOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { menuOpen = false },
                            ),
                    )
                    SpecMenu(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(220.dp),
                    ) {
                        SpecMenuItem(
                            icon = CcIcons.Copy, label = "Copy full hash",
                            onClick = {
                                menuOpen = false
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(commitId))
                                note = "Copied."
                            },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Check, label = "Go back to this version",
                            enabled = !busy,
                            onClick = { menuOpen = false; confirm = "checkout" },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Undo, label = "Undo this commit", danger = true,
                            enabled = !busy,
                            onClick = { menuOpen = false; confirm = "revert" },
                        )
                    }
                }
            }
        }
        val current = commit
        if (current == null) {
            Text(
                text = "Reading commit…",
                color = c.textMuted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = current.summary,
                color = c.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, end = 14.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(c.panel2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = current.author.take(1).uppercase(),
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${current.author} · ${relativeTime(current.whenMillis)}",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current.id,
                    color = c.textFaint,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(current.id))
                            note = "Copied."
                        }
                        .semantics { contentDescription = "Copy full hash" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Copy, c.textMuted, 15.dp)
                }
            }
            note?.let {
                Text(
                    text = it,
                    color = c.success,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 4.dp),
                )
            }
            val paths = files
            if (paths == null) {
                Text(
                    text = "Reading files…",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            var totalAdded = 0
            var totalRemoved = 0
            paths.forEach { path ->
                diffs[path]?.forEach { row ->
                    if (row.added) totalAdded++
                    if (row.removed) totalRemoved++
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(start = 16.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.chipBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "${paths.size} files",
                        color = c.text,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.chipBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "+$totalAdded −$totalRemoved",
                        color = c.text,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "CHANGED FILES",
                color = c.textMuted,
                fontSize = 11.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 16.dp, end = 14.dp),
            )
            Spacer(Modifier.height(6.dp))
            paths.forEach { path ->
                val rows = diffs[path]
                val isLarge = path in tooLarge
                val added = rows?.count { it.added } ?: 0
                val removed = rows?.count { it.removed } ?: 0
                val isNew = rows != null && rows.all { it.oldNo == null } && rows.any { it.added }
                val isDel = rows != null && rows.all { it.newNo == null } && rows.any { it.removed }
                val tag = when {
                    isLarge -> "Large file"
                    isNew -> "New file"
                    isDel -> "Deleted file"
                    else -> "+$added −$removed"
                }
                UnifiedFileCard(
                    fileName = path.substringAfterLast('/'),
                    folder = path.substringBeforeLast('/', ""),
                    tag = tag,
                    tagAdded = isNew,
                    tagDeleted = isDel,
                    added = added,
                    removed = removed,
                    rows = if (isLarge) emptyList() else rows,
                    onOpenFull = { onDiff(commitId, path) },
                )
                if (isLarge) {
                    Text(
                        text = "Too large to show here — tap the card to open the full diff.",
                        color = c.textMuted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiffNote(text: String) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
        )
    }
}

@Composable
private fun RowScope.DiffCell(
    text: String?,
    changed: Boolean,
    isOld: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val c = LocalPageColors.current
    val bg = when {
        text == null -> c.fieldBg
        !changed -> androidx.compose.ui.graphics.Color.Transparent
        isOld -> c.statusDeletedBg
        else -> c.statusAddedBg
    }
    Box(
        Modifier
            .weight(1f)
            .background(bg)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.orEmpty(),
            color = if (text == null) c.textFaint else c.text,
            fontSize = 11.sp,
            fontFamily = Mono,
            softWrap = false,
            maxLines = 1,
        )
    }
}
