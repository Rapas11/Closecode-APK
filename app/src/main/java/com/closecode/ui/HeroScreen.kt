package com.closecode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll



import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.background.PageBackground
import com.closecode.ui.components.CodeCard
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.TokenStore
import com.closecode.ui.ide.GitHubSignIn
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.Templates
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.components.CodeGlyph
import com.closecode.ui.components.PlusGlyph
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Facts = listOf("Open source", "On-device builds", "Private by default")

@Composable
private fun Reveal(
    index: Int,
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                delayMillis = (index * 80).coerceAtMost(520),
            ),
        ) { value, _ -> progress = value }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12f * density
        }
    ) { content() }
}

@Composable
private fun Pill(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(if (primary) c.ab else c.cd)
            .border(1.dp, if (primary) c.ab else c.ln, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) leading()
        Text(
            text = label,
            color = if (primary) c.af else c.tx,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// The disclosure hangs below the GitHub button: a stem down out of the button, a trunk running
// left, and one stub into each choice. The trunk sits at BranchTrunkY; the stubs are the space
// left over before the pills start.
private val BranchLabelHeight = 44.dp
private val BranchGap = 14.dp
private val BranchTrunkY = 16.dp
private val BranchConnectorHeight = 26.dp

/**
 * The two things the GitHub button opens, drawn under it as a trunk with a branch per choice.
 *
 * The two pills sit side by side and each one owns its half of the trunk, so the line closes
 * without either side having to know how wide the other is. The account line sits underneath
 * both, where it cannot disturb that geometry — the stem has to reach the button above it.
 */
@Composable
private fun GitHubDisclosure(
    signedIn: Boolean,
    account: GitHubUser?,
    onSignIn: () -> Unit,
    onClone: () -> Unit,
) {
    Column(
        Modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BranchGap),
            verticalAlignment = Alignment.Top,
        ) {
            Branch(
                label = "Sign in GitHub",
                note = null,
                stem = false,
                trunkRunsLeft = false,
                onClick = onSignIn,
            )
            Branch(
                label = "Clone GitHub",
                note = null,
                stem = true,
                trunkRunsLeft = true,
                onClick = onClone,
            )
        }
        if (signedIn) AccountRow(account)
    }
}

/**
 * Who the device is actually signed in as. The pills say GitHub is connected; this says who it is
 * connected *as*, which is the thing worth checking before a clone writes into your projects.
 *
 * The account arrives from the network after sign-in, so "Connected" is a real state and not a
 * placeholder that ever claims a name it has not been told.
 */
@Composable
private fun AccountRow(account: GitHubUser?) {
    val c = LocalPageColors.current
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(c.success),
        )
        Text(
            text = accountLabel(account),
            color = c.mu,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Who the device is signed in as.
 *
 * "Connected" is a real answer and not a placeholder: it is what can honestly be said while the
 * account is still being fetched, and it never borrows a name that has not been told to us.
 */
internal fun accountLabel(account: GitHubUser?): String = when {
    account == null -> "Connected"
    account.name.isNullOrBlank() -> "@${account.login}"
    else -> "${account.name} · @${account.login}"
}

/**
 * One choice in [GitHubDisclosure]. Its box is exactly as wide as its pill, so the centre of the
 * canvas is the centre of the pill — that is where the stem into the button and the stub into the
 * label land.
 *
 * [stem] rises into the GitHub button above; only the branch directly under the button draws one.
 * The trunk always runs away from its own side and overshoots the edge by half the gap, so two
 * neighbouring branches meet in the middle of the gap.
 */
@Composable
private fun Branch(
    label: String,
    note: String?,
    stem: Boolean,
    trunkRunsLeft: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box {
        Column {
            Spacer(Modifier.height(BranchConnectorHeight))
            BranchLabel(label = label, note = note, onClick = onClick)
        }
        Canvas(Modifier.matchParentSize()) {
            val line = 1.dp.toPx()
            val cx = size.width / 2f
            val trunkY = BranchTrunkY.toPx()
            val stubEnd = BranchConnectorHeight.toPx()
            val reach = BranchGap.toPx() / 2f
            drawLine(
                color = c.ln,
                start = Offset(cx, trunkY),
                end = Offset(if (trunkRunsLeft) -reach else size.width + reach, trunkY),
                strokeWidth = line,
            )
            drawLine(
                color = c.ln,
                start = Offset(cx, trunkY),
                end = Offset(cx, stubEnd),
                strokeWidth = line,
            )
            if (stem) {
                drawLine(
                    color = c.ln,
                    start = Offset(cx, 0f),
                    end = Offset(cx, trunkY),
                    strokeWidth = line,
                )
            }
        }
    }
}

@Composable
private fun BranchLabel(label: String, note: String?, onClick: () -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .height(BranchLabelHeight)
            .clip(shape)
            .background(c.cd)
            .border(1.dp, c.ln, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        if (note != null) {
            Text(
                text = note,
                color = c.success,
                fontSize = 11.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
private fun Wordmark() {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .background(c.tx, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                color = c.bg,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Closecode",
            color = c.tx,
            fontSize = 18.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Headline(size: TextUnit) {
    val c = LocalPageColors.current
    Text(
        text = buildAnnotatedString {
            append("Build Android apps,\n")
            withStyle(SpanStyle(color = c.ac)) { append("anywhere.") }
        },
        color = c.tx,
        fontSize = size,
        lineHeight = size * 1.02f,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045f).em,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 620.dp),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HeroScreen(
    onProjectCreated: (name: String, selection: SelectedTemplate, templateName: String, config: ProjectConfig) -> Unit,
    onProjectCloned: (ProjectRecord) -> Unit,
    onOpenRecent: () -> Unit,
    recentCount: Int,
) {
    val c = LocalPageColors.current
    var sheetOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // GitHub. The token is read once from private preferences and kept in memory only as long as
    // this screen is composed; everything that touches the network runs off the main thread.
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    var githubToken by remember { mutableStateOf(tokenStore.read()) }
    // The token only says that *some* account is connected. This is which one, and it is fetched
    // rather than derived — off the main thread, because it is a network call made on screen load.
    val gitHubClient = remember { GitHubClient() }
    var githubAccount by remember { mutableStateOf<GitHubUser?>(null) }
    var githubOpen by remember { mutableStateOf(false) }
    var signInOpen by remember { mutableStateOf(false) }
    var cloneOpen by remember { mutableStateOf(false) }

    LaunchedEffect(githubToken) {
        val token = githubToken
        githubAccount = if (token == null) {
            null
        } else {
            withContext(Dispatchers.IO) { gitHubClient.currentUser(token).getOrNull() }
        }
    }

    LaunchedEffect(tokenStore.storageError) {
        tokenStore.storageError?.let { toast = it }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2400)
            toast = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxWidth
        val headlineSize = (viewport.value * 0.082f).coerceIn(38f, 76f).sp
        val subSize = (viewport.value * 0.024f).coerceIn(16f, 19f).sp

        PageBackground()

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Reveal(1) { Wordmark() }
            Spacer(Modifier.height(34.dp))

            Reveal(2) {
                Text(
                    text = "THE ANDROID IDE FOR YOUR PHONE",
                    color = c.ac,
                    fontSize = 12.5.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.08.em,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))

            Reveal(3) { Headline(headlineSize) }

            Spacer(Modifier.height(22.dp))

            Reveal(4) {
                Text(
                    text = "Write, build and ship real apps straight from your device. " +
                        "No laptop, no setup, no compromise.",
                    color = c.mu,
                    fontSize = subSize,
                    lineHeight = subSize * 1.6f,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }

            Spacer(Modifier.height(34.dp))

            Reveal(5) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill(
                            label = "Start a project",
                            primary = true,
                            onClick = { sheetOpen = true },
                        ) {
                            PlusGlyph(color = c.af)
                        }
                        Pill(
                            label = "GitHub",
                            primary = false,
                            onClick = { githubOpen = !githubOpen },
                            leading = {
                                CodeGlyph(color = c.tx)
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = githubOpen,
                        enter = expandVertically(tween(260)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
                    ) {
                        GitHubDisclosure(
                            signedIn = githubToken != null,
                            account = githubAccount,
                            onSignIn = {
                                if (githubToken != null) {
                                    toast = "Signed in to GitHub."
                                } else {
                                    signInOpen = true
                                }
                            },
                            onClone = { cloneOpen = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(56.dp))

            Reveal(6) { CodeCard() }

            Spacer(Modifier.height(38.dp))

            Reveal(7) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Facts.forEach { fact ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(c.ac, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = fact,
                                color = c.mu,
                                fontSize = 13.sp,
                                fontFamily = Sans,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = toast.orEmpty(),
                color = c.af,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.ab)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }

    if (sheetOpen) {
        StartProjectSheet(
            onOpenRecent = onOpenRecent,
            recentCount = recentCount,
            onDismiss = { sheetOpen = false },
            onCreate = { projectName, selection, config ->
                sheetOpen = false
                val templateName = Templates[selection.tab]
                    .orEmpty()
                    .getOrNull(selection.index)
                    ?.name
                    .orEmpty()
                onProjectCreated(projectName, selection, templateName, config)
            },
        )
    }

    if (signInOpen) {
        GitHubSignIn(
            onSignedIn = { token ->
                val saved = tokenStore.write(token)
                githubToken = token.takeIf { saved }
                signInOpen = false
                toast = if (saved) "Signed in to GitHub." else tokenStore.storageError
            },
            onDismiss = { signInOpen = false },
        )
    }

    if (cloneOpen) {
        GitHubCloneScreen(
            token = githubToken,
            onToken = { fresh ->
                val saved = tokenStore.write(fresh)
                githubToken = fresh.takeIf { saved }
                if (saved) null else tokenStore.storageError
            },
            onDismiss = { cloneOpen = false },
            onCloned = { record ->
                cloneOpen = false
                githubOpen = false
                onProjectCloned(record)
            },
            tokenStorageError = tokenStore.storageError,
        )
    }
}