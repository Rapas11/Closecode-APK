package com.closecode.ui.ide

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.closecode.ui.model.ProjectFile
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import kotlinx.coroutines.channels.Channel

/**
 * One shell, with everything that belongs to it.
 *
 * Tabs are not views over a shared shell: each one is a separate process with its own working
 * directory, its own scrollback and its own half-typed line. That is the point of having more than
 * one — a server left running in the first while Git work goes on in the second — and it cannot be
 * faked by swapping which text is shown.
 *
 * The output channel is per session and is drained for every session for as long as the panel is
 * open, not only for the one on screen. A session that stopped being read while it was in the
 * background would fill its pipe and wedge its shell, which is precisely the hang that folding
 * standard error into standard output was there to prevent.
 */

/**
 * How far the list is from its bottom, in px: zero at the last line, unbounded when the last
 * line is not even composed. Follow mode treats 24 dp as the edge — nearer scrolls back on,
 * farther scrolls off.
 */
internal fun distanceToBottomPx(
    totalItems: Int,
    lastVisibleIndex: Int,
    lastVisibleEndPx: Int,
    viewportEndPx: Int,
): Float {
    if (totalItems <= 0) return 0f
    if (lastVisibleIndex < totalItems - 1) return Float.MAX_VALUE
    return (viewportEndPx - lastVisibleEndPx).toFloat().coerceAtLeast(0f)
}

internal class TerminalSession(val number: Int) {
    val output = Channel<String>(Channel.UNLIMITED)
    val lines = mutableStateListOf<String>()
    var input by mutableStateOf(TextFieldValue(""))
    var lastExit by mutableStateOf<Int?>(null)
    var cwd by mutableStateOf("")
    /** A reader-given name from a long press. Null means the numbered label. */
    var customName by mutableStateOf<String?>(null)
    /** True between sending a command and reading its exit marker. Drives the Stop button. */
    var commandRunning by mutableStateOf(false)
    var history by mutableStateOf(emptyList<String>())
    var historyIndex by mutableStateOf(0)
    var draft by mutableStateOf("")
    var followOutput by mutableStateOf(true)
    /** Output arrived while unfollowed: the Latest pill's blue dot until the reader catches up. */
    var hasNewOutput by mutableStateOf(false)

    private var shell: ShellProcess? = null
    // The last measured size. Kept here (not only on the process) so a resize that lands
    // before the shell starts — or while it is dead between commands — is still applied.
    private var pendingCols: Int = 80
    private var pendingRows: Int = 24

    val isRunning: Boolean get() = shell?.isRunning == true

    fun start(directory: File?) {
        if (shell != null) return
        val started = ShellProcess(
            directory = directory,
            onLine = { line -> output.trySend(line) },
        )
        started.start()
        started.setSize(pendingCols, pendingRows)
        shell = started
    }

    fun run(command: String) {
        shell?.run(command)
    }

    /**
     * The width the panel measured, in characters. Exported to the running shell now
     * (COLUMNS/LINES) and kept on the process for the PTY's own size call later.
     */
    fun setSize(cols: Int, rows: Int = 24) {
        pendingCols = cols.coerceIn(20, 512)
        pendingRows = rows.coerceIn(10, 512)
        shell?.setSize(pendingCols, pendingRows)
    }

    fun killRunning() {
        shell?.killRunning()
        commandRunning = false
    }

    fun close() {
        shell?.close()
        shell = null
    }

    /** Gives a session the reader's remembered commands, at the moment it is opened. */
    fun copyHistory(remembered: List<String>) {
        history = remembered
        historyIndex = remembered.size
    }

    /**
     * Runs one line, recording it and remembering it, and reports whether it changed anything on
     * disk so the drawer can follow.
     *
     * The caller deals with `code`, which never reaches here because it is not a program; everything
     * else goes to the shell. [prompt] is the prompt as drawn (directory, dollar, space), echoed
     * back in front of the command so the scrollback reads as one transcript.
     */
    fun submit(
        command: String,
        projectSlug: String,
        historyStore: TerminalHistoryStore,
        prompt: String,
    ): Boolean {
        val text = sanitizeCommand(command)
        if (text.isEmpty()) return false
        lines.appendTerminalLine("$prompt$text")
        run(text)
        commandRunning = true
        cwdAfterCommand(cwd, "", text)?.let { cwd = it }
        history = addToHistory(history, text)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
        return refreshesFiles(text)
    }

    /** Records a `code` command without running it, reporting whether it opened something. */
    fun recordAppCommand(
        command: String,
        tree: List<ProjectFile>,
        projectSlug: String,
        historyStore: TerminalHistoryStore,
        opened: ProjectFile?,
    ) {
        val text = sanitizeCommand(command)
        val target = codeTarget(text)
        lines.appendTerminalLine(
            when {
                opened != null -> "Opened ${opened.path} in the editor"
                target != null -> "No file called $target in this project"
                else -> "code needs a file name"
            }
        )
        history = addToHistory(history, text)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
    }
}

/**
 * The directory the prompt names: the project under the home mark, with the shell's
 * project-relative folder after it. `~/Landing page`, or `~/Landing page/assets` once it has
 * moved down.
 */
internal fun displayDir(projectName: String, cwd: String): String {
    val base = "~/${projectName.ifBlank { "project" }}"
    return if (cwd.isBlank()) base else "$base/$cwd"
}

/** The prompt as drawn and echoed: directory, dollar, space. */
internal fun promptPrefix(displayDir: String): String = "$displayDir $ "

/**
 * An echoed command line back into its prompt directory and its command, or null for ordinary
 * output. Echoes start under the home mark and carry " $ " between the two halves; output only
 * matches by accident, and then only its head is tinted.
 */
internal fun splitPrompt(line: String): Pair<String, String>? {
    if (!line.startsWith("~/")) return null
    val cut = line.indexOf(" $ ")
    if (cut < 0) return null
    return line.substring(0, cut) to line.substring(cut + 3)
}

/** The commands the Commands sheet offers out of the box, in the order they earn their place. */
internal val DefaultTerminalCommands = listOf(
    "ls -la", "pwd", "ps", "clear", "kill", "git status", "git diff",
)

/** Saved commands, joined for the store. Histories split on newlines, so these split on a unit separator a command cannot practically contain. */
internal fun encodeCommands(commands: List<String>): String = commands.joinToString("\u001F")

internal fun decodeCommands(raw: String?): List<String> =
    raw.orEmpty().split('\u001F').map { it.trim() }.filter { it.isNotEmpty() }

/** Preference key for the Commands sheet shelf. Global, not per project: tools are tools. */
internal const val TERMINAL_COMMANDS_KEY = "terminal.commands"

/** Preference key for the last keyboard height in dp. The commands panel matches it. */
internal const val TERMINAL_KEYBOARD_HEIGHT_KEY = "terminal.keyboard.height"

/**
 * What a tab is called.
 *
 * The number is the identity — it is what makes one tab distinguishable from another at a glance —
 * and the directory is appended only when the shell has left the project root, because that is the
 * one thing about a tab that is not obvious from a list of numbered labels.
 */
internal fun sessionTitle(number: Int, cwd: String): String {
    if (cwd.isEmpty()) return "Shell $number"
    return "Shell $number · ${cwd.substringAfterLast('/').ifBlank { cwd }}"
}

/** The most a tab label may be, so a long path cannot push the other tabs off the strip. */
internal const val TAB_TITLE_MAX_CHARS = 22

/**
 * A tab label that fits the strip.
 *
 * A reader-given name wins outright: renaming is the reader saying what the tab is for, and a
 * generated label on top of it would argue. Otherwise the number is never trimmed away, whatever
 * the path looks like: two tabs both called "…going" are worse than two tabs both called
 * "Shell 1", because the first loses the only thing that distinguishes them. So the path is
 * shortened and the head is kept.
 */
internal fun tabLabel(number: Int, cwd: String, custom: String? = null): String {
    val named = custom?.takeIf { it.isNotBlank() }
    if (named != null) return named
    val head = "Shell $number"
    if (cwd.isEmpty()) return head
    val leaf = cwd.substringAfterLast('/').ifBlank { cwd }
    val full = "$head · $leaf"
    if (full.length <= TAB_TITLE_MAX_CHARS) return full
    // " · " plus the ellipsis, taken off the end of the label before the path starts.
    val room = TAB_TITLE_MAX_CHARS - head.length - 4
    if (room <= 1) return head
    return "$head · …${leaf.takeLast(room)}"
}
/**
 * The row of terminal tabs: 26 dp chips, monospace labels, the active one in white with a
 * 2 dp accent line under it, and a plus chip for another shell. Long press renames or closes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TerminalTabStrip(
    sessions: List<TerminalSession>,
    activeId: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onAdd: () -> Unit,
    onRename: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var menuFor by remember { mutableStateOf<Int?>(null) }
    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sessions.forEach { session ->
            val active = session.number == activeId
            val label = tabLabel(session.number, session.cwd, session.customName)
            Box {
                Column(
                    Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) c.chipBg else Color.Transparent)
                        .combinedClickable(
                            onClick = { onSelect(session.number) },
                            onLongClick = { menuFor = session.number },
                        )
                        .padding(horizontal = 12.dp)
                        .semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else c.textMuted,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                    // The accent line under the active chip. Inside the chip rather than below
                    // it, so the strip stays 26 dp and the line never takes its own row.
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (active) c.accent else Color.Transparent),
                    )
                }
                DropdownMenu(
                    expanded = menuFor == session.number,
                    onDismissRequest = { menuFor = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", fontFamily = Mono) },
                        onClick = {
                            menuFor = null
                            onRename(session.number)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close shell", fontFamily = Mono) },
                        enabled = sessions.size > 1,
                        onClick = {
                            menuFor = null
                            onClose(session.number)
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, c.fieldBorder, RoundedCornerShape(7.dp))
                .clickable { onAdd() }
                .semantics { contentDescription = "New shell" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = c.text, fontSize = 14.sp, fontFamily = Mono)
        }
    }
}

/**
 * The shells, held above every screen that can be replaced.
 *
 * This exists because the sessions used to live in a `remember` inside the terminal panel, and the
 * terminal panel is unmounted by anything that takes the editor's place — including the settings
 * screen, which is a sibling branch of the IDE and not a dialog over it. So changing the terminal's
 * colour killed the shell before the new colour was in force, and there was nothing left to recolour
 * when the terminal came back: a fresh, empty shell. The reader saw a theme that appeared not to
 * work, and the cause was a lifetime, not a colour.
 *
 * Holding the list here means the processes outlive every panel swap and the settings screen, and a
 * theme change reaches a terminal that is already on screen with output in it.
 */
@Stable
internal class TerminalSessions {
    internal var sessions by mutableStateOf(listOf(TerminalSession(1)))
        private set

    var activeId by mutableIntStateOf(1)
        private set

    /** The shell on screen. Never null while there is at least one, which there always is. */
    fun active(): TerminalSession =
        sessions.firstOrNull { it.number == activeId } ?: sessions.first()

    fun select(number: Int) {
        if (sessions.any { it.number == number }) activeId = number
    }

    /** Opens another shell and switches to it. The new one starts empty; the others keep running. */
    fun openNew(): Int {
        val next = (sessions.maxOfOrNull { it.number } ?: 0) + 1
        sessions = sessions + TerminalSession(next)
        activeId = next
        return next
    }

    /**
     * Closes one shell, and only that one.
     *
     * The last one is never closed: there would be nothing left to type into, and a terminal with no
     * tabs is not a state a reader can get out of.
     */
    fun close(number: Int) {
        if (sessions.size <= 1) return
        val target = sessions.firstOrNull { it.number == number } ?: return
        target.close()
        val remaining = sessions.filterNot { it.number == number }
        sessions = remaining
        if (activeId == number) activeId = remaining.first().number
    }

    /** Replaces the remembered commands on every shell, on arrival. */
    fun applyHistory(commands: List<String>) {
        sessions = sessions.map { it.apply { copyHistory(commands) } }
    }

    /** Names one shell from a long press. Blank clears back to the numbered label. */
    fun rename(number: Int, name: String) {
        sessions.firstOrNull { it.number == number }?.customName =
            name.takeIf { it.isNotBlank() }
    }
}

/**
 * Whether a plain output line reads as a failure. The shell never colors its own complaints
 * ("sh: foo: not found"), so the panel tints them red itself. Deliberately narrow — a build
 * log mentioning "not found" in passing stays plain — and only ever applied to lines with no
 * ANSI of their own, so a program's own colors always win.
 */
internal fun isErrorLine(line: String): Boolean {
    val text = line.trim()
    if (text.isEmpty()) return false
    for (head in ErrorLineHeads) {
        if (text.startsWith(head, ignoreCase = true)) return true
    }
    for (fragment in ErrorLineFragments) {
        if (text.contains(fragment, ignoreCase = true)) return true
    }
    return false
}

/**
 * Short, plain shell errors. Removes the shell path and the script position the shell
 * prepends (`/system/bin/sh: <stdin>[13]: Ping: inaccessible or not found` becomes
 * `sh: Ping: command not found`). Everything else stays as the shell gave it. The
 * result still matches [isErrorLine], so it keeps its red tint.
 */
internal fun rewriteShellNoise(line: String): String {
    var text = line
    if (text.startsWith("/system/bin/")) {
        text = text.removePrefix("/system/bin/")
    }
    if (StdinTag.containsMatchIn(text)) {
        text = StdinTag.replaceFirst(text, "")
        if (!text.startsWith("sh:")) {
            text = "sh: $text"
        }
    }
    if ("inaccessible or not found" in text) {
        text = text.replace("inaccessible or not found", "command not found")
    }
    return text
}

private val StdinTag = Regex("<stdin>\\[\\d+\\]: ")

private val ErrorLineHeads = listOf("sh:", "error:", "fatal:", "E ", "FAILED")
private val ErrorLineFragments = listOf(
    "not found",
    "no such file",
    "permission denied",
    "command not found",
)
