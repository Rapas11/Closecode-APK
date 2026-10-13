package com.closecode.ui.ide

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.ScreenCard
import com.closecode.ui.components.TextLink
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Spec 7 logcat levels. Verbose is muted, Debug accent, Info green, Warn amber, Error red. */
enum class LogLevel(val label: String, val letter: Char) {
    VERBOSE("Verbose", 'V'),
    DEBUG("Debug", 'D'),
    INFO("Info", 'I'),
    WARN("Warn", 'W'),
    ERROR("Error", 'E'),
}

/** One logcat row. [formatted] matches the spec's `HH:mm:ss.SSS L/Tag: message`. */
data class LogLine(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val time: String = NOW.format(LocalTime.now()),
) {
    val formatted: String get() = "$time ${level.letter}/$tag: $message"
    val sortableLevel: Int get() = level.ordinal

    companion object {
        private val NOW: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

/** Spec 7: the log is capped at 1,000 lines. */
const val LOGCAT_MAX_LINES = 1_000

/**
 * Appends while enforcing the cap. Dropping the oldest lines is one array copy of at most a
 * thousand elements, which is cheap enough per line and keeps the bound strict: the list never
 * holds more than [LOGCAT_MAX_LINES], rather than drifting up to a batch threshold.
 */
fun MutableList<LogLine>.appendCapped(line: LogLine) {
    add(line)
    if (size > LOGCAT_MAX_LINES) {
        // Excess is usually 1, so this clears a short prefix and shifts the rest once.
        subList(0, size - LOGCAT_MAX_LINES).clear()
    }
}

/**
 * How far through a build a step is.
 *
 * There is no FAILED state: a build that reports an error still completed its work, and showing a
 * red cross beside a step that actually finished would be a second lie on top of the first one.
 * Whether the project compiled is the result line's business, not a step's.
 */
internal enum class StepState { PENDING, RUNNING, DONE }

/** Which part of the sweep a build is in, in the order it happens. */
enum class BuildPhase { IDLE, SYMBOLS, FILES, DONE }

/** The steps a build goes through, named after what they actually do. */
internal fun buildStepLabels(fileCount: Int): List<String> = listOf(
    "Read project symbols",
    when (fileCount) {
        0 -> "No Kotlin files"
        1 -> "Analyse 1 file"
        else -> "Analyse $fileCount files"
    }
)

/**
 * Which step each phase of a build is in.
 *
 * One number would have been tidier — divide the bar into equal shares — but it would have been
 * wrong. Reading the project's symbols is a single quick pass over every file, while analysing them
 * is the long haul, so on a real project the first step is finished long before a half-full bar.
 * Tracking the phase instead of guessing at a fraction is what lets the list say something true.
 */
internal fun buildStepStates(phase: BuildPhase): List<StepState> = when (phase) {
    BuildPhase.IDLE -> listOf(StepState.PENDING, StepState.PENDING)
    BuildPhase.SYMBOLS -> listOf(StepState.RUNNING, StepState.PENDING)
    BuildPhase.FILES -> listOf(StepState.DONE, StepState.RUNNING)
    BuildPhase.DONE -> listOf(StepState.DONE, StepState.DONE)
}

/** The length a build is reported in, so a sub-second check does not read as "0 ms". */
internal fun formatBuildTime(elapsedMs: Long): String =
    if (elapsedMs < 1000) {
        "$elapsedMs ms"
    } else {
        String.format(java.util.Locale.US, "%.1f s", elapsedMs / 1000.0)
    }

/**
 * The app's package name from its manifest, or null when no manifest is loaded. Reads the
 * `package` attribute rather than guessing from the folder, because the folder is the reader's
 * words and the attribute is the system's.
 */
internal fun manifestPackageOf(manifest: String?): String? {
    if (manifest.isNullOrBlank()) return null
    val match = Regex("""package\s*=\s*["']([^"']+)["']""").find(manifest)
    return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}

/**
 * The app's version from its Gradle file, or null when it is not declared there. Accepts both
 * quoted styles and ignores surrounding configuration, because the line sits among other lines
 * that look almost like it.
 */
internal fun gradleVersionOf(buildFile: String?): String? {
    if (buildFile.isNullOrBlank()) return null
    val match = Regex("""(?m)^\s*versionName\s*=?\s*["']([^"']+)["']""").find(buildFile)
    return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}

private fun countOf(n: Int, one: String, many: String): String =
    "$n ${if (n == 1) one else many}"

/**
 * The one line that reports what the static check found.
 *
 * Counts and time come from the sweep that just ran, so this is a report rather than a mood. The
 * failing case says the errors are in the marked lines, because that is where they can be acted on
 * — the panel opens the Problems sheet from a tap, and the editor has already underlined them.
 */
internal fun buildResultLine(errorCount: Int, warningCount: Int, elapsedMs: Long): String {
    val time = formatBuildTime(elapsedMs)
    return when {
        errorCount > 0 -> "CHECK FAILED · ${countOf(errorCount, "error", "errors")}, " +
            "${countOf(warningCount, "warning", "warnings")} in $time. Open the marked lines to fix."
        warningCount > 0 -> "CHECK PASSED · ${countOf(warningCount, "warning", "warnings")} in $time"
        else -> "CHECK PASSED · 0 errors in $time"
    }
}

/**
 * The way out of Logcat and Build. Neither panel has a path bar, so without this the bottom
 * navigation was the only escape from a screen the top bar's build button had just opened.
 */
@Composable
internal fun PanelBackBar(title: String, onBack: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(c.panel2)
                .border(1.dp, c.ln, RoundedCornerShape(9.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "‹ Code",
                color = c.ac,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Spec 7 Logcat panel: filter chips, one mono row per line with a divider, long lines wrapping.
 *
 * The log is generated by the app's own analysis and build events. There is no real device
 * logcat on Android, so the header says so rather than implying otherwise.
 */
@Composable
fun IdeLogcatPanel(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
    filter: LogLevel? = null,
    onFilterChange: (LogLevel?) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val visible = remember(lines, filter) {
        if (filter == null) lines else lines.filter { it.level == filter }
    }

    Column(modifier.fillMaxSize().background(c.bg)) {
        PanelBackBar("Logcat", onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip("All", filter == null) { onFilterChange(null) }
            for (level in LogLevel.entries) {
                FilterChip(level.label, filter == level) { onFilterChange(level) }
            }
        }

        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (visible.isEmpty()) {
                Text(
                    text = "Run the app to see logs.",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    modifier = Modifier.padding(40.dp),
                )
                return@Column
            }
            Column(Modifier.fillMaxWidth()) {
                visible.forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = line.formatted,
                            color = when (line.level) {
                                LogLevel.INFO -> c.success
                                LogLevel.WARN -> c.amber
                                LogLevel.ERROR -> c.red
                                LogLevel.DEBUG -> c.ac
                                LogLevel.VERBOSE -> c.mu
                            },
                            fontSize = 13.sp,
                            fontFamily = Mono,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

/**
 * Spec 7 compile-check panel: the app header, a result bar, one row per phase of the check
 * with its measured time, and the actions that mean something.
 *
 * This is a real check, not a Gradle build: there is no Gradle daemon inside an Android app and
 * there never will be, so the panel runs the app's own Kotlin analyser across the project's files
 * and reports what it genuinely found. The old version of this panel named Gradle tasks it never
 * ran and walked them on a timer, which is worse than useless — it looked like a build log without
 * being one. Everything drawn here now comes from the sweep: the bar from the files it has done,
 * the steps from the phase it is in, and the result line from the counts and time it measured.
 *
 * Two spec rows appear only when a real APK exists: Share APK and Open app / install.
 * With no APK there is nothing to share or launch, so the rows stay hidden rather than
 * decorative — the primary button is View errors on a failed build and Run checks otherwise.
 */
@Composable
internal fun IdeBuildPanel(
    appName: String,
    packageName: String?,
    version: String?,
    building: Boolean,
    errorCount: Int,
    warningCount: Int,
    elapsedMs: Long,
    completedAtMillis: Long? = null,
    phase: BuildPhase,
    fileCount: Int,
    progress: Float,
    symbolsMs: Long = 0L,
    filesMs: Long = 0L,
    hasBuilt: Boolean = false,
    errors: List<ProjectDiagnostic> = emptyList(),
    onRun: () -> Unit,
    onCancel: () -> Unit = {},
    onViewErrors: () -> Unit = {},
    onOpenLog: () -> Unit = {},
    onOpenFile: (String, Int) -> Unit = { _, _ -> },
    onAskAi: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    // Phase 3: present only when the engine produced a real APK. Null hides every
    // install row, so an unbuilt project can never show a decorative Open button.
    apkName: String? = null,
    installLabel: String? = null,
    appInstalled: Boolean = false,
    onInstall: () -> Unit = {},
    onOpenApp: () -> Unit = {},
    onShareApk: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val failed = errorCount > 0
    // While nothing is running the bar shows the whole sweep: full on a clean project, and stopped
    // where it got to on one that failed, so the last state is still visible rather than snapping
    // back to empty the moment the button is released.
    val fraction = when {
        building -> progress.coerceIn(0f, 1f)
        failed -> 1f
        else -> 1f
    }

    // A build must survive leaving the app: the sweep continues either way, but the screen
    // going dark halfway through is how a finished build gets missed.
    val view = LocalView.current
    DisposableEffect(building) {
        view.keepScreenOn = building
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier.fillMaxSize().background(c.bg)) {
        PanelBackBar("Compile check", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The app being built: icon tile, name, and package with version underneath.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1D2B57)),
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Android, c.toolbarAccent, 24.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        color = c.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${packageName ?: "—"} · v${version ?: "—"}",
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (building) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.panel2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.accent),
                    )
                }
            }

            if (hasBuilt && !building) {
                // The result bar: green success or dark-red failure with what it found and
                // how long honesty took.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (failed) Color(0xFF2A1519) else Color(0xFF12281F))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (failed) {
                        CcIcon(CcIcons.Warning, Color(0xFFF2A0A0), 16.dp)
                    } else {
                        CcIcon(CcIcons.Check, Color(0xFF5FD19A), 16.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (failed) {
                            "Checks found $errorCount ${if (errorCount == 1) "error" else "errors"}"
                        } else {
                            "Checks passed · no APK was created"
                        },
                        color = if (failed) Color(0xFFF2A0A0) else Color(0xFF5FD19A),
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // One row per sweep phase with its measured time: checks while running, times
            // once done, gray for whatever a failure never reached.
            val labels = buildStepLabels(fileCount)
            val times = listOf(symbolsMs, filesMs)
            buildStepStates(phase).forEachIndexed { index, state ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state) {
                        StepState.DONE -> CcIcon(CcIcons.Check, c.success, 15.dp)
                        StepState.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = c.text,
                            strokeWidth = 2.dp,
                        )
                        StepState.PENDING -> Box(
                            Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(c.textFaint),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = labels[index],
                        color = if (state == StepState.PENDING) c.textFaint else c.text,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (state == StepState.DONE && hasBuilt) {
                        Text(
                            text = formatBuildTime(times.getOrElse(index) { 0L }),
                            color = c.textMuted,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                    }
                }
            }

            if (building) {
                // Destructive in red text only, never a filled red button.
                TextLink(label = "Cancel", color = c.error, onClick = onCancel)
            } else {
                if (failed) {
                    PrimaryButton(label = "View errors", onClick = onViewErrors)
                } else if (hasBuilt && apkName != null) {
                    if (appInstalled) {
                        PrimaryButton(label = "Open app", onClick = onOpenApp)
                    } else {
                        PrimaryButton(
                            label = installLabel ?: "Install",
                            onClick = onInstall,
                        )
                    }
                } else {
                    PrimaryButton(label = "Run checks", onClick = onRun)
                }

                if (hasBuilt) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlineButton(
                            label = "Build log",
                            onClick = onOpenLog,
                            modifier = Modifier.weight(1f),
                        )
                        if (apkName != null && !failed) {
                            OutlineButton(
                                label = "Share APK",
                                onClick = onShareApk,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (!failed) {
                        TextLink(label = "Run checks again", color = c.link, onClick = onRun)
                    }
                    if (apkName != null && !failed) {
                        Text(
                            text = apkName,
                            color = c.textFaint,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                        )
                    }
                    Text(
                        text = "Last check · " +
                            (completedAtMillis?.let {
                                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                                    .format(java.util.Date(it))
                            } ?: "time unavailable") +
                            " · ${formatBuildTime(elapsedMs)} · " +
                            "$errorCount ${if (errorCount == 1) "error" else "errors"}",
                        color = c.textFaint,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                    )
                }
                // Failed builds list every error as a card: plain words, a file-and-line link,
                // and a door to the AI panel. Capped — a hundred-error project needs the log,
                // not a hundred cards.
                errors.take(50).forEach { row ->
                    BuildErrorCard(
                        row = row,
                        onOpenFile = onOpenFile,
                        onAskAi = onAskAi,
                    )
                }
                if (errors.size > 50) {
                    Text(
                        text = "…and ${errors.size - 50} more in the build log.",
                        color = c.textFaint,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
            }
        }
    }
}

/** One build failure: what it says, where it is, and a door to the AI panel. */
@Composable
private fun BuildErrorCard(
    row: ProjectDiagnostic,
    onOpenFile: (String, Int) -> Unit,
    onAskAi: () -> Unit,
) {
    val c = LocalPageColors.current
    ScreenCard {
        Text(
            text = row.diagnostic.message,
            color = c.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${row.fileName}:${row.diagnostic.line}",
                color = c.link,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onOpenFile(row.fileName, row.diagnostic.line.coerceAtLeast(1))
                    },
            )
            TextLink(label = "Ask AI", onClick = onAskAi)
        }
    }
}



@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) c.ac else Color.Transparent)
            .border(1.dp, if (active) c.ac else c.ln, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = if (active) Color.White else c.tx,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
/**
 * The full build log: every error and warning with its file and line, newest check last.
 * A log the reader can tap through to the code, rather than a count they cannot act on.
 */
@Composable
internal fun BuildLogScreen(
    errors: List<ProjectDiagnostic>,
    warnings: List<ProjectDiagnostic>,
    elapsedMs: Long,
    onOpenFile: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalPageColors.current
    // No BackHandler here: the IDE's single ordered chain owns Back (buildLogOpen branch),
    // so a competing handler can never steal it out of order.
    Column(Modifier.fillMaxSize().background(c.bg)) {
        PanelBackBar("Build log", onBack)
        if (errors.isEmpty() && warnings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Clean in ${formatBuildTime(elapsedMs)} — nothing to show.",
                    color = c.textMuted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(errors.size + warnings.size) { index ->
                val row = if (index < errors.size) {
                    errors[index] to true
                } else {
                    warnings[index - errors.size] to false
                }
                val (entry, isError) = row
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isError) Color(0xFF1D1215) else Color(0xFF1D1A10))
                        .clickable {
                            onOpenFile(entry.fileName, entry.diagnostic.line.coerceAtLeast(1))
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = entry.diagnostic.message,
                        color = if (isError) Color(0xFFF2A0A0) else c.warning,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        lineHeight = 17.sp,
                    )
                    Text(
                        text = "${entry.fileName}:${entry.diagnostic.line}",
                        color = c.link,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
        }
    }
}
