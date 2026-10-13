package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile

/**
 * Commands that change what is on disk.
 *
 * The drawer is refreshed when a command *finishes*, not when one of these is recognised, so a
 * script that writes files without ever naming one still leaves the list correct. This is only used
 * to skip the refresh for the commands that provably cannot have changed anything, because the
 * refresh is a walk of the project directory and `ls` should not pay for it.
 *
 * Matching is on the first word of each pipeline stage rather than the whole line, so `ls | wc -l`
 * is not mistaken for a write and `mkdir a && mkdir b` is recognised by the first stage alone.
 */
internal fun refreshesFiles(command: String): Boolean {
    val heads = command.split('|', ';', '&').map { it.trim() }.filter { it.isNotEmpty() }
    return heads.any { head ->
        val name = head.substringBefore(' ').substringAfterLast('/')
        name in FILE_CHANGING_COMMANDS
    }
}

/**
 * The commands that can put a file, or a directory, somewhere new or take it away.
 *
 * `clear` is here because a test project uses it to clean up between runs, and the list would
 * otherwise be left showing files that are gone.
 */
internal val FILE_CHANGING_COMMANDS: Set<String> = setOf(
    "touch", "mkdir", "rm", "rmdir", "mv", "cp", "ln", "truncate", "install",
    "shred", "dd", "tee", "git", "make", "tar", "unzip", "zip", "chmod", "clear",
)

/**
 * The project files a line of terminal output names, in the order they appear.
 *
 * Matching is done against the tree rather than guessed at from the shape of the text: a line
 * containing "settings.gradle.kts" offers that file, and a line containing "error: no such file"
 * offers nothing. Guessing from slashes and dots would offer a tap on half the prose in a stack
 * trace, which is worse than offering none.
 *
 * A bare file name counts as well as a full path — output rarely prints the whole path — but only
 * when it looks like a file, by having a dot in it. Without that, every occurrence of a folder name
 * in a sentence would become a tap that does nothing.
 */
internal fun pathsMentionedIn(line: String, tree: List<ProjectFile>): List<ProjectFile> {
    if (tree.isEmpty()) return emptyList()
    val byPath = HashMap<String, ProjectFile>(tree.size * 2)
    val byName = HashMap<String, ProjectFile>(tree.size * 2)
    for (file in tree) {
        if (file.kind == FileKind.Folder) continue
        byPath[file.path] = file
        byName.putIfAbsent(file.name, file)
    }
    val found = LinkedHashMap<String, ProjectFile>()
    for (token in OUTPUT_TOKENS.findAll(line)) {
        val text = token.value
        byPath[text]?.let { found.putIfAbsent(it.path, it) }
        // A bare name, only when it looks like a filename: "error: no such file" must not offer one.
        if (text.contains('.')) byName[text]?.let { found.putIfAbsent(it.path, it) }
    }
    return found.values.toList()
}

/**
 * The pieces of a line that could be a path.
 *
 * Punctuation that frames a path in output — quotes, brackets, colons ending a sentence, commas —
 * is kept inside the match so `app/Theme.kt:` still finds the file, and stripped afterwards so the
 * match is on the path rather than on the character after it.
 */
private val OUTPUT_TOKENS = Regex("""[\w./~\-]+""")