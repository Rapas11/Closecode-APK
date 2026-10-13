package com.closecode.ui.project

import android.content.Context
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import java.io.File

class ProjectStore(context: Context) {

    private val projectsRoot: File = File(context.filesDir, "projects")

    fun projectsDir(): File = projectsRoot

    fun projectDir(slug: String): File = File(projectsRoot, ProjectSafety.sanitizeSlug(slug))

    fun ensureRoot() {
        if (!projectsRoot.exists()) projectsRoot.mkdirs()
    }

    fun exists(slug: String): Boolean = File(projectDir(slug), METADATA).isFile

    fun listSlugs(): List<String> =
        projectsRoot.listFiles()?.filter { File(it, METADATA).isFile }?.map { it.name } ?: emptyList()

    fun readText(slug: String, relative: String): String {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        if (!file.isFile) throw ProjectPathException("Not a file: $relative")
        return ProjectSafety.readTextOrThrow(file)
    }

    /**
     * Reads a file as raw bytes, for Preview to decode images. Same path containment checks as
     * [readText]; [ProjectSafety.ensureWritable] is deliberate so Preview cannot be used to read
     * outside the project the user opened.
     */
    fun readBytes(slug: String, relative: String): ByteArray {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        if (!file.isFile) throw ProjectPathException("Not a file: $relative")
        return file.readBytes()
    }

    fun writeText(slug: String, relative: String, content: String) {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        ProjectSafety.writeAtomic(file, content)
    }

    fun deletePath(slug: String, relative: String): Boolean {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun createFile(slug: String, relative: String, content: String) {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        ProjectSafety.writeAtomic(file, content)
    }

    /**
     * Creates a directory and any missing parents. Separate from [createFile] because an empty
     * string written through [ProjectSafety.writeAtomic] would leave a file where a folder belongs,
     * and the drawer would then refuse to expand it.
     */
    fun createFolder(slug: String, relative: String) {
        val dir = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(dir)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw ProjectPathException("Could not create $relative")
        }
    }

    fun readTree(slug: String): List<ProjectFile> {
        val root = projectDir(slug)
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<ProjectFile>()
        walk(root, into = out)
        return out
    }

    /**
     * The record for a path that was just created. Creating a file used to be followed by a second
     * full [readTree] purely to find it again — that walk raced the refresh every mutation already
     * triggers, so the row it was looking for arrived at an unpredictable moment and the file
     * opened or did not. The path is all the walk was being asked for, and it is what
     * [projectEntry] derives everything from.
     */
    fun entryFor(slug: String, relative: String, isFolder: Boolean): ProjectFile =
        projectEntry(relative, isFolder)

    /**
     * Renames the leaf of [relative], keeping it in its own folder. Returns the new path, because
     * every caller — the tree, the tab strip, the open buffer — has to be told where it went.
     */
    fun renamePath(slug: String, relative: String, newName: String): String {
        val name = newName.trim()
        if (name.isEmpty() || name == "." || name == ".." || '/' in name) {
            throw ProjectPathException("That is not a name.")
        }
        val parent = relative.substringBeforeLast('/', "")
        return movePath(slug, relative, if (parent.isEmpty()) name else "$parent/$name")
    }

    /**
     * Moves [relative] to [destination], a project-relative path, and returns where it landed.
     * Rename and move are one call on the filesystem — a rename edits the last segment of a path
     * and a move edits the rest of it — so they are one function here too.
     */
    fun movePath(slug: String, relative: String, destination: String): String {
        val source = resolveExisting(slug, relative)
        val target = checkedDestination(slug, relative, destination)
        if (!source.renameTo(target.first)) throw ProjectPathException("Could not move $relative")
        return target.second
    }

    /** Copies [relative] to [destination], a project-relative path, and returns where the copy is. */
    fun copyPath(slug: String, relative: String, destination: String): String {
        val source = resolveExisting(slug, relative)
        val (target, landed) = checkedDestination(slug, relative, destination)
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = false)
        } else {
            source.copyTo(target, overwrite = false)
        }
        return landed
    }

    private fun resolveExisting(slug: String, relative: String): File {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        if (!file.exists()) throw ProjectPathException("Not found: $relative")
        return file
    }

    /**
     * Resolves and vetoes a destination before anything is written. Every one of these is a way to
     * lose data by accident: landing on top of a file that already exists, escaping the project, or
     * copying a folder into a descendant of itself — which never terminates.
     */
    private fun checkedDestination(slug: String, relative: String, destination: String): Pair<File, String> {
        val target = destination.trim().removePrefix("/")
        if (target.isEmpty()) throw ProjectPathException("That is not a path.")
        if (target == relative) throw ProjectPathException("That is the same place.")
        val targetFile = ProjectSafety.resolveInProject(projectDir(slug), target)
        ProjectSafety.ensureWritable(targetFile)
        if (targetFile.exists()) throw ProjectPathException("Already exists: $target")
        val source = ProjectSafety.resolveInProject(projectDir(slug), relative)
        if (targetFile.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
            throw ProjectPathException("A folder cannot contain itself.")
        }
        targetFile.parentFile?.mkdirs()
        return targetFile to target
    }

    private fun walk(dir: File, into: MutableList<ProjectFile>, prefix: String = "") {
        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: return

        for (child in children) {
            if (isHiddenEntry(child.name)) continue
            val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            into.add(projectEntry(relative, child.isDirectory))
            if (child.isDirectory) walk(child, into, relative)
        }
    }

    /**
     * Entries the drawer never shows. `.git` is an implementation detail of version control: the
     * repository is manipulated through the GitHub tab, never by browsing inside it, and showing
     * hundreds of objects under a folder named `.git` only invites the user to edit one.
     */
    private fun isHiddenEntry(name: String): Boolean =
        ProjectSafety.isHiddenName(name) || name == ".git"

    fun deleteProject(slug: String): Boolean {
        val dir = projectDir(slug)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    companion object {
        const val METADATA = ".closecode.json"
    }
}

/**
 * The record the drawer shows for [relative]. One builder serves both the walk and a path that was
 * just created, so a file the user has never seen before gets exactly the row a full tree walk
 * would have given it: the leaf, the kind, the language, the depth and whether it expands, all
 * read off the path itself.
 */
internal fun projectEntry(relative: String, isFolder: Boolean): ProjectFile {
    val leaf = relative.substringAfterLast('/')
    return ProjectFile(
        name = leaf,
        kind = if (isFolder) FileKind.Folder else kindOf(leaf),
        language = if (isFolder) "" else languageOf(leaf),
        depth = relative.count { it == '/' },
        expandable = isFolder,
        path = relative,
    )
}

internal fun kindOf(name: String): FileKind = when {
    name.endsWith(".kt") || name.endsWith(".kts") -> FileKind.Kotlin
    name.endsWith(".xml") -> FileKind.Xml
    name.endsWith(".html") -> FileKind.Html
    name.endsWith(".css") -> FileKind.Css
    name.endsWith(".js") -> FileKind.Js
    name.endsWith(".json") -> FileKind.Json
    name.endsWith(".csv") -> FileKind.Csv
    name.endsWith(".md") -> FileKind.Markdown
    name.endsWith(".gradle") -> FileKind.Gradle
    // Anything without a recognised extension gets no badge. Treating it as Gradle made
    // files like .gitkeep render with a red "G".
    else -> FileKind.Unknown
}

internal fun languageOf(name: String): String = when {
    name.endsWith(".kt") -> "Kotlin"
    name.endsWith(".kts") -> "Gradle Kotlin"
    name.endsWith(".xml") -> "XML"
    name.endsWith(".html") -> "HTML"
    name.endsWith(".css") -> "CSS"
    name.endsWith(".js") -> "JavaScript"
    name.endsWith(".json") -> "JSON"
    name.endsWith(".csv") -> "CSV"
    name.endsWith(".md") -> "Markdown"
    name.endsWith(".properties") -> "Properties"
    else -> "Text"
}