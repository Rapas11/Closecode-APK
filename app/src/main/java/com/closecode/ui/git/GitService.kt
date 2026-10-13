package com.closecode.ui.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent

/** A single path the index and the working tree disagree about. */
data class GitChange(
    val path: String,
    /** True once the change is already in the index waiting to be committed. */
    val staged: Boolean,
    val kind: GitChangeKind,
)

enum class GitChangeKind { Added, Modified, Deleted }

/**
 * Everything the Changes screen needs from one `git status`.
 *
 * The three lists are mutually exclusive: [staged] is index-vs-HEAD, [unstaged] is
 * working-tree-vs-index, [untracked] is a file git has never seen. `conflicts` are
 * paths that are merged badly and must be resolved before anything else can move.
 */
data class GitSnapshot(
    val branch: String,
    val detached: Boolean,
    val hasCommits: Boolean,
    val ahead: Int,
    val behind: Int,
    val staged: List<GitChange>,
    val unstaged: List<GitChange>,
    val untracked: List<GitChange>,
    val conflicts: List<String>,
) {
    val changeCount: Int
        get() = staged.size + unstaged.size + untracked.size + conflicts.size

    val isClean: Boolean
        get() = changeCount == 0

    companion object {
        val Empty = GitSnapshot(
            branch = "main",
            detached = false,
            hasCommits = false,
            ahead = 0,
            behind = 0,
            staged = emptyList(),
            unstaged = emptyList(),
            untracked = emptyList(),
            conflicts = emptyList(),
        )
    }
}

/** One row of `git log`. */
data class GitCommit(
    val id: String,
    val summary: String,
    val author: String,
    val whenMillis: Long,
) {
    val shortId: String get() = id.take(7)
}

/**
 * The local half of version control: init, status, stage, commit, log, branches.
 *
 * Wraps JGit so nothing in the UI ever sees an exception type from the library — every
 * entry point returns [Result] and failures carry a message a person can read. GitHub,
 * remotes and pushing live in a separate client; this class only ever touches the
 * repository inside the project directory.
 */
class GitService(private val root: File) {

    /** True once `git init` has run. `.git` is filtered out of the file tree elsewhere. */
    fun isVersioned(): Boolean = File(root, ".git").exists()

    fun init(initialBranch: String = "main"): Result<Unit> = runCatching {
        if (isVersioned()) return@runCatching
        Git.init().setDirectory(root).setInitialBranch(initialBranch).call()
    }

    /**
     * Writes the app's ignore rules into the repository's local exclude file, once.
     *
     * `.git/info/exclude` does what a `.gitignore` does without ever being tracked: the rules hold
     * for this repository on this machine and cannot reach a commit, a push or anyone else's
     * clone. So the project's own `.gitignore` stays the user's file — this never creates one and
     * never touches one that is already there.
     *
     * True when a rule was actually added, false when the file already carries them or the
     * directory is not a repository.
     */
    fun ensureLocalExcludes(): Boolean = runCatching {
        if (!isVersioned()) return@runCatching false
        val target = File(root, EXCLUDES)
        val existing = if (target.isFile) target.readText() else ""
        val merged = mergeExcludes(existing)
        if (merged == existing) return@runCatching false
        target.parentFile?.mkdirs()
        target.writeText(merged)
        true
    }.getOrDefault(false)

    fun snapshot(): Result<GitSnapshot> = runCatching {
        open().use { git ->
            val repo = git.repository
            val status = git.status().call()
            val hasCommits = repo.resolve(Constants.HEAD) != null
            // Detached HEAD reads as the object id, not a ref, so the short branch name
            // alone can never say it: a full ref outside refs/heads is the tell.
            val fullBranch = runCatching { repo.fullBranch }.getOrNull()
            val isDetached = fullBranch != null &&
                !fullBranch.startsWith("refs/heads/") &&
                !fullBranch.startsWith("refs/tags/")
            val rawBranch = repo.branch
            var ahead = 0
            var behind = 0
            if (hasCommits && !isDetached && rawBranch != null) {
                val tracking = BranchTrackingStatus.of(repo, rawBranch)
                ahead = tracking?.aheadCount ?: 0
                behind = tracking?.behindCount ?: 0
            }
            GitSnapshot(
                branch = if (isDetached) DETACHED else (rawBranch ?: DETACHED),
                detached = isDetached,
                hasCommits = hasCommits,
                ahead = ahead,
                behind = behind,
                staged = stagedOf(status),
                unstaged = unstagedOf(status),
                untracked = status.untracked.sorted().map { GitChange(it, false, GitChangeKind.Added) },
                conflicts = status.conflicting.sorted(),
            )
        }
    }

    /** Adds work-tree changes to the index so the next commit picks them up. */
    fun stage(paths: List<String>): Result<Unit> = runCatching {
        open().use { git ->
            paths.forEach { path ->
                if (File(root, path).exists()) {
                    git.add().addFilepattern(path).call()
                } else {
                    // The file was deleted in the editor: stage the deletion, not a re-add.
                    git.rm().addFilepattern(path).setCached(true).call()
                }
            }
        }
    }

    /** Takes paths back out of the index, leaving the files on disk exactly as they are. */
    fun unstage(paths: List<String>): Result<Unit> = runCatching {
        open().use { git ->
            val hasCommits = git.repository.resolve(Constants.HEAD) != null
            paths.forEach { path ->
                if (hasCommits) {
                    git.reset().setRef(Constants.HEAD).addPath(path).call()
                } else {
                    // No HEAD to reset against yet, so drop the index entry outright. The
                    // working copy stays put and the path simply becomes untracked again.
                    git.rm().addFilepattern(path).setCached(true).call()
                }
            }
        }
    }

    /**
     * Throws away changes to [paths]: tracked files go back to their HEAD content and untracked
     * ones are deleted. Staged entries for those paths are reset first, so the index ends up
     * matching HEAD too rather than keeping a staged copy of what was just thrown away. Paths
     * that are staged-new (no HEAD version) are unstaged and deleted, which is the same end
     * state by the only route that reaches it.
     */
    fun discard(paths: List<String>): Result<Unit> = runCatching {
        open().use { git ->
            val repo = git.repository
            val hasCommits = repo.resolve(Constants.HEAD) != null
            val untracked = git.status().call().untracked
            paths.forEach { path ->
                val file = File(root, path)
                if (path in untracked || !hasCommits && !inHead(repo, path)) {
                    if (file.exists()) file.deleteRecursively()
                    return@forEach
                }
                if (inHead(repo, path)) {
                    git.reset().setRef(Constants.HEAD).addPath(path).call()
                    git.checkout().setStartPoint(Constants.HEAD).addPath(path).call()
                } else {
                    git.rm().addFilepattern(path).setCached(true).call()
                    if (file.exists()) file.deleteRecursively()
                }
            }
        }
    }

    /** True when [relativePath] exists in the HEAD tree. */
    private fun inHead(repo: org.eclipse.jgit.lib.Repository, relativePath: String): Boolean {        val headTree = repo.resolve(Constants.HEAD + "^{tree}") ?: return false
        val walk = org.eclipse.jgit.treewalk.TreeWalk(repo).apply {
            addTree(headTree)
            isRecursive = true
            filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath)
        }
        val found = walk.next()
        walk.close()
        return found
    }

    /** The file's bytes at HEAD, or null when no commit contains it. For the diff view. */    fun fileAtHead(relativePath: String): Result<ByteArray?> = runCatching {
        if (!isVersioned()) return@runCatching null
        Git.open(root).use { git ->
            val repo = git.repository
            val headTree = repo.resolve(Constants.HEAD + "^{tree}") ?: return@runCatching null
            val walk = org.eclipse.jgit.treewalk.TreeWalk(repo).apply {
                addTree(headTree)
                isRecursive = true
                filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath)
            }
            val id = if (walk.next()) walk.getObjectId(0) else null
            walk.close()
            if (id == null || id == org.eclipse.jgit.lib.ObjectId.zeroId()) return@runCatching null
            repo.newObjectReader().use { it.open(id).bytes }
        }
    }

    /** Commits whatever is staged. Returns the id of the new commit. */
    fun commit(message: String): Result<String> = runCatching {        val trimmed = message.trim()
        require(trimmed.isNotEmpty()) { "A commit needs a message." }
        open().use { git ->
            val ident = identity(git)
            git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(trimmed)
                .call()
                .name
        }
    }

    /**
     * True when `refs/stash` exists, so the panel can offer to restore work it knows is there
     * rather than showing a button that only ever answers "nothing stashed".
     */
    fun hasStash(): Boolean = runCatching {
        if (!isVersioned()) return@runCatching false
        open().use { it.repository.exactRef(Constants.R_STASH) != null }
    }.getOrDefault(false)

    /**
     * Parks the tracked edits and the index and returns the working tree to HEAD, which is what
     * `git stash` does — the CHANGES list empties instead of the button looking inert. Untracked
     * files stay, matching git's own default.
     */
    fun stash(): Result<String> = runCatching {
        open().use { git ->
            require(git.repository.resolve(Constants.HEAD) != null) {
                "Stash needs at least one commit to save your work against."
            }
            val created = git.stashCreate().call()
                ?: throw IllegalArgumentException("There is nothing to stash.")
            "Stashed your work as ${created.name.take(7)}."
        }
    }

    /**
     * Re-applies the newest stash and drops it only after the apply succeeded, so a conflict
     * keeps the stash instead of quietly losing it.
     */
    fun stashPop(): Result<String> = runCatching {
        open().use { git ->
            require(git.repository.exactRef(Constants.R_STASH) != null) {
                "There is no stash to restore."
            }
            git.stashApply().setRestoreIndex(true).call()
            git.stashDrop().call()
            "Restored your stashed changes."
        }
    }

    fun history(limit: Int = 50): Result<List<GitCommit>> = runCatching {
        open().use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) return@runCatching emptyList()
            val commits = git.log().setMaxCount(limit.coerceAtLeast(1)).call()
            val rows = commits.map { rev ->
                GitCommit(
                    id = rev.name,
                    summary = rev.shortMessage,
                    author = rev.authorIdent.name,
                    whenMillis = rev.commitTime * 1000L,
                )
            }
            (commits as? AutoCloseable)?.close()
            rows
        }
    }

    /**
     * Paths one commit touched, against its first parent (or against nothing for the
     * root commit). Powers the History section's expandable rows. Sorted, so the eye
     * can scan a large commit the same way every time.
     */
    fun commitFiles(id: String): Result<List<String>> = runCatching {
        open().use { git ->
            val repo = git.repository
            org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
                val commit = walk.parseCommit(repo.resolve(id))
                    ?: throw IllegalArgumentException("No such commit.")
                val parent = commit.parents.firstOrNull()?.let { walk.parseCommit(it) }
                repo.newObjectReader().use { reader ->
                    fun parser(tree: org.eclipse.jgit.lib.ObjectId?): org.eclipse.jgit.treewalk.AbstractTreeIterator =
                        if (tree == null) {
                            org.eclipse.jgit.treewalk.EmptyTreeIterator()
                        } else {
                            org.eclipse.jgit.treewalk.CanonicalTreeParser().apply { reset(reader, tree) }
                        }
                    org.eclipse.jgit.diff.DiffFormatter(
                        org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE,
                    ).use { formatter ->
                        formatter.setRepository(repo)
                        formatter.scan(parser(parent?.tree), parser(commit.tree))
                            .map { entry ->
                                if (entry.newPath != "/dev/null") entry.newPath else entry.oldPath
                            }
                            .sorted()
                    }
                }
            }
        }
    }

    /**
     * One file's bytes at two points in history: the commit itself and its first parent.
     * Null means "no such file there" — added files have no parent side, deleted files
     * have no commit side. The pair feeds the same side-by-side diff as the working tree.
     */
    fun commitFileContents(id: String, relativePath: String): Result<Pair<ByteArray?, ByteArray?>> =
        runCatching {
            open().use { git ->
                val repo = git.repository
                org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
                    val commit = walk.parseCommit(repo.resolve(id))
                        ?: throw IllegalArgumentException("No such commit.")
                    val parent = commit.parents.firstOrNull()?.let { walk.parseCommit(it) }
                    fun bytesAt(tree: org.eclipse.jgit.lib.ObjectId?): ByteArray? {
                        if (tree == null) return null
                        val tw = org.eclipse.jgit.treewalk.TreeWalk(repo).apply {
                            addTree(tree)
                            isRecursive = true
                            filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath)
                        }
                        val objectId = if (tw.next()) tw.getObjectId(0) else null
                        tw.close()
                        if (objectId == null || objectId == org.eclipse.jgit.lib.ObjectId.zeroId()) {
                            return null
                        }
                        return repo.newObjectReader().use { it.open(objectId).bytes }
                    }
                    bytesAt(parent?.tree) to bytesAt(commit.tree)
                }
            }
        }

    /**
     * Moves the working tree to one commit. Detached HEAD on purpose: history is for
     * looking, and the branch row keeps showing the state plainly afterwards.
     */
    fun checkoutCommit(id: String): Result<Unit> = runCatching {
        open().use { git ->
            git.checkout().setName(id).call()
        }
    }

    /**
     * Undoes one commit by applying its inverse as a new commit. Fails loudly on
     * conflicts instead of leaving the tree half-reverted.
     */
    fun revertCommit(id: String): Result<String> = runCatching {
        open().use { git ->
            git.revert().include(git.repository.resolve(id)).call()
            git.repository.resolve("HEAD")?.name
                ?: throw IllegalStateException("Revert did not move HEAD.")
        }
    }

    fun branches(): Result<List<String>> = runCatching {
        open().use { git ->
            git.branchList().call()
                .map { it.name.removePrefix(Constants.R_HEADS) }
                .sorted()
        }
    }

    fun createBranch(name: String): Result<Unit> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A branch needs a name." }
        open().use { git -> git.branchCreate().setName(trimmed).call() }
    }

    fun checkout(name: String): Result<Unit> = runCatching {
        open().use { git -> git.checkout().setName(name).call() }
    }

    /**
     * Which lines of one file the working tree changed, keyed by 1-based new-file line.
     *
     * Compares HEAD to the file on disk, so staged and unstaged edits both mark: the gutter
     * answers "what is different", not "what is staged". Inserted lines read Added, replaced
     * lines Modified, and a pure deletion — which has no line of its own — points at the line
     * that follows it, or at nothing when the deletion runs past the end of the file.
     *
     * Untracked files, repositories without commits, and anything JGit cannot parse (binary
     * files, a corrupt index) mark nothing: an empty map is always a safe answer for a margin.
     */
    fun workingTreeMarks(relativePath: String): Result<Map<Int, GitChangeKind>> =
        runCatching {
            val newBytes = File(root, relativePath).takeIf { it.isFile }?.readBytes()
            marksAgainstHead(relativePath, newBytes) ?: emptyMap()
        }

    /**
     * The same comparison against the live buffer instead of the file on disk, so the gutter
     * reflects what is on screen including unsaved edits. The line numbers in the returned map
     * count the buffer's lines, which is also what the gutter numbers.
     */
    fun bufferMarks(relativePath: String, newText: String): Result<Map<Int, GitChangeKind>> =
        runCatching {
            marksAgainstHead(relativePath, newText.toByteArray()) ?: emptyMap()
        }

    /**
     * The version-control marks for one file, by 1-based new-side line. Null [newBytes] means the
     * file is absent from the worktree — a deletion, whose mark points at the follower line, or
     * nothing when the file never existed. Null is also returned when there is no HEAD to compare
     * against, or the path was never tracked: with no old side there is nothing to mark.
     */
    private fun marksAgainstHead(relativePath: String, newBytes: ByteArray?): Map<Int, GitChangeKind>? {
        if (!isVersioned()) return null
        return Git.open(root).use { git ->
            if (git.status().call().untracked.contains(relativePath)) {
                return@use null
            }
            val repo = git.repository
            val headTree = repo.resolve(Constants.HEAD + "^{tree}") ?: return@use null
            // The old blob comes straight from the HEAD tree for this path, not from a
            // worktree diff: when only the buffer changed, the worktree diff is empty and
            // there is nothing to iterate, yet the buffer still differs from HEAD.
            val walk = org.eclipse.jgit.treewalk.TreeWalk(repo).apply {
                addTree(headTree)
                isRecursive = true
                filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath)
            }
            val oldId = if (walk.next()) walk.getObjectId(0) else null
            walk.close()
            if (oldId == null) return@use null
            val reader = repo.newObjectReader()
            try {
                // The old bytes come straight from this reader, and the new bytes arrive as a
                // parameter: no formatter in between. DiffFormatter loads both sides through its
                // own content pipeline, which is also where binary detection and large-file guards
                // live — none of which a 3 dp margin mark needs. A null new side is a file absent
                // from the worktree; an empty new side is how a deletion diffs.
                val newSide = newBytes ?: ByteArray(0)
                val newLineCount = org.eclipse.jgit.diff.RawText(newSide).size()
                val oldBytes = if (oldId == org.eclipse.jgit.lib.ObjectId.zeroId()) {
                    ByteArray(0)
                } else {
                    reader.open(oldId).bytes
                }
                val algorithm = org.eclipse.jgit.diff.DiffAlgorithm.getAlgorithm(
                    org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm.HISTOGRAM,
                )
                val edits = algorithm.diff(
                    org.eclipse.jgit.diff.RawTextComparator.DEFAULT,
                    org.eclipse.jgit.diff.RawText(oldBytes),
                    org.eclipse.jgit.diff.RawText(newSide),
                )
                val marks = LinkedHashMap<Int, GitChangeKind>()
                for (i in 0 until edits.size) {
                    val edit = edits[i]
                    when (edit.type) {
                        org.eclipse.jgit.diff.Edit.Type.INSERT -> {
                            for (line in edit.beginB + 1..edit.endB) {
                                marks[line] = GitChangeKind.Added
                            }
                        }
                        org.eclipse.jgit.diff.Edit.Type.REPLACE -> {
                            for (line in edit.beginB + 1..edit.endB) {
                                marks[line] = GitChangeKind.Modified
                            }
                        }
                        org.eclipse.jgit.diff.Edit.Type.DELETE -> {
                            val follower = edit.beginB + 1
                            if (follower in 1..newLineCount) {
                                marks.putIfAbsent(follower, GitChangeKind.Deleted)
                            }
                        }
                        org.eclipse.jgit.diff.Edit.Type.EMPTY -> Unit
                    }
                }
                marks
            } finally {
                reader.close()
            }
        }
    }

    private fun open(): Git = Git.open(root)

    private fun identity(git: Git): PersonIdent {
        val cfg = git.repository.config
        val name = cfg.getString("user", null, "name")?.takeIf { it.isNotBlank() } ?: DEFAULT_USER
        val email = cfg.getString("user", null, "email")?.takeIf { it.isNotBlank() } ?: DEFAULT_EMAIL
        return PersonIdent(name, email)
    }

    companion object {
        const val DETACHED = "detached"
        const val DEFAULT_USER = "Closecode"
        const val DEFAULT_EMAIL = "closecode@localhost"

        /** The repository's local exclude file, relative to the project directory. */
        internal const val EXCLUDES = ".git/info/exclude"

        /** The line the app's block of rules starts with, so it is recognisable on a second run. */
        internal const val EXCLUDE_MARKER = "# Closecode local excludes"

        /**
         * What a repository Closecode touches gets in its local exclude file, so generated files
         * never look like edits. Local only: none of it can reach a commit or anyone else's clone.
         */
        internal val DEFAULT_EXCLUDES = """
            # Closecode local excludes
            # The app's own project state. It changes every time the project is opened.
            .closecode.json

            # Machine-local settings and build output.
            local.properties
            .gradle/
            build/
            .idea/
            *.iml
            captures/
            .externalNativeBuild/
            .cxx/
            *.apk
            *.aab
        """.trimIndent()

        /**
         * Appends the app's rules to whatever a repository's local exclude file already holds.
         *
         * The block sits behind [EXCLUDE_MARKER], so a second run finds it and hands the file
         * straight back: nothing the user added before or after it is rewritten, and the rules
         * never land twice. An empty file gets the block on its own; a file with content in it
         * gets a blank line and then the block, so the two stay readable side by side.
         */
        internal fun mergeExcludes(existing: String): String {
            if (EXCLUDE_MARKER in existing) return existing
            val head = if (existing.isEmpty()) "" else existing.trimEnd('\n') + "\n\n"
            return head + DEFAULT_EXCLUDES
        }

        /**
         * JGit's `Status` splits the index and the working tree into two disjoint halves:
         * added/changed/removed are index-vs-HEAD (staged), modified/missing are
         * working-tree-vs-index (not staged).
         */
        internal fun stagedOf(status: Status): List<GitChange> = buildList {
            status.added.sorted().forEach { add(GitChange(it, true, GitChangeKind.Added)) }
            status.changed.sorted().forEach { add(GitChange(it, true, GitChangeKind.Modified)) }
            status.removed.sorted().forEach { add(GitChange(it, true, GitChangeKind.Deleted)) }
        }

        internal fun unstagedOf(status: Status): List<GitChange> = buildList {
            status.modified.sorted().forEach { add(GitChange(it, false, GitChangeKind.Modified)) }
            status.missing.sorted().forEach { add(GitChange(it, false, GitChangeKind.Deleted)) }
        }
    }
}

/** One aligned row of a side-by-side diff. A missing side is an added or removed line. */
internal data class DiffRow(val oldLine: String?, val newLine: String?)

/**
 * Aligns two file texts for a side-by-side diff, row by row. Changed blocks pair up line for
 * line first (so a one-word edit reads as one row), and only the leftover lines go one-sided.
 * Line lists come from the same [RawText] objects the diff runs on, so the edit indices can
 * never point past them no matter how the text ends. Capped at [maxRows] for very large files.
 */
internal fun splitDiff(oldText: String, newText: String, maxRows: Int = 2000): List<DiffRow> {
    val oldBytes = oldText.toByteArray()
    val newBytes = newText.toByteArray()
    val oldRaw = org.eclipse.jgit.diff.RawText(oldBytes)
    val newRaw = org.eclipse.jgit.diff.RawText(newBytes)
    val a = (0 until oldRaw.size()).map { oldRaw.getString(it) }
    val b = (0 until newRaw.size()).map { newRaw.getString(it) }
    if (a == b) return a.map { DiffRow(it, it) }.take(maxRows)
    val algorithm = org.eclipse.jgit.diff.DiffAlgorithm.getAlgorithm(
        org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm.HISTOGRAM,
    )
    val edits = algorithm.diff(org.eclipse.jgit.diff.RawTextComparator.DEFAULT, oldRaw, newRaw)
    val rows = ArrayList<DiffRow>()
    var ai = 0
    var bi = 0
    for (i in 0 until edits.size) {
        val edit = edits[i]
        while (ai < edit.beginA && rows.size < maxRows) {
            rows.add(DiffRow(a[ai], b[bi]))
            ai++
            bi++
        }
        var pa = edit.beginA
        var pb = edit.beginB
        while (pa < edit.endA && pb < edit.endB && rows.size < maxRows) {
            rows.add(DiffRow(a[pa], b[pb]))
            pa++
            pb++
        }
        while (pa < edit.endA && rows.size < maxRows) {
            rows.add(DiffRow(a[pa], null))
            pa++
        }
        while (pb < edit.endB && rows.size < maxRows) {
            rows.add(DiffRow(null, b[pb]))
            pb++
        }
        ai = edit.endA
        bi = edit.endB
    }
    while (ai < a.size && bi < b.size && rows.size < maxRows) {
        rows.add(DiffRow(a[ai], b[bi]))
        ai++
        bi++
    }
    return rows
}
