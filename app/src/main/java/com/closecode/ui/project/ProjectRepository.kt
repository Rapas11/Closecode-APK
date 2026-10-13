package com.closecode.ui.project

import android.content.Context
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import org.json.JSONObject

data class ProjectRecord(
    val slug: String,
    val name: String,
    val tab: TemplateTab,
    val templateIndex: Int,
    val templateName: String,
    val config: ProjectConfig,
    val createdAt: Long,
    val lastOpened: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("tab", tab.name)
        put("templateIndex", templateIndex)
        put("templateName", templateName)
        put("config", config.encode())
        put("createdAt", createdAt)
        put("lastOpened", lastOpened)
    }.toString()

    companion object {
        fun fromJson(slug: String, raw: String): ProjectRecord? = runCatching {
            val json = JSONObject(raw)
            ProjectRecord(
                slug = slug,
                name = json.getString("name"),
                tab = TemplateTab.valueOf(json.getString("tab")),
                templateIndex = json.getInt("templateIndex"),
                templateName = json.getString("templateName"),
                config = ProjectConfig.decode(json.getString("config")),
                createdAt = json.getLong("createdAt"),
                lastOpened = json.getLong("lastOpened"),
            )
        }.getOrNull()
    }
}

class ProjectRepository(context: Context) {

    private val store = ProjectStore(context)

    fun listProjects(): List<ProjectRecord> =
        store.listSlugs()
            .mapNotNull { slug ->
                val raw = runCatching {
                    store.readText(slug, ProjectStore.METADATA)
                }.getOrNull() ?: return@mapNotNull null
                ProjectRecord.fromJson(slug, raw)
            }
            .sortedByDescending { it.lastOpened }

    fun find(slug: String): ProjectRecord? = listProjects().firstOrNull { it.slug == slug }

    fun create(
        name: String,
        selection: SelectedTemplate,
        config: ProjectConfig,
        now: Long = System.currentTimeMillis(),
    ): ProjectRecord {
        store.ensureRoot()
        val slug = uniqueSlug(name)
        val templateName = templateNameFor(selection)

        Scaffold.filesFor(name, selection, config).forEach { file ->
            store.createFile(slug, file.path, file.content)
        }

        val record = ProjectRecord(
            slug = slug,
            name = name,
            tab = selection.tab,
            templateIndex = selection.index,
            templateName = templateName,
            config = config,
            createdAt = now,
            lastOpened = now,
        )
        store.createFile(slug, ProjectStore.METADATA, record.toJson())
        return record
    }

    fun delete(slug: String): Boolean {
        if (!store.exists(slug)) return false
        return store.deleteProject(slug)
    }

    /**
     * Clones a repository into a fresh project folder and registers the result, in that order.
     *
     * JGit refuses to clone into a non-empty directory, so the folder is claimed and left empty
     * while [clone] fills it, and only then is the record written — which is what puts the project
     * on the start screen. A failed clone removes the folder again, so a half-written repository
     * never shows up as a project or gets cloned into on the next attempt.
     */
    fun importCloned(
        name: String,
        now: Long = System.currentTimeMillis(),
        clone: (target: java.io.File) -> Unit,
    ): ProjectRecord {
        store.ensureRoot()
        val slug = uniqueSlug(name)
        val target = store.projectDir(slug)
        target.mkdirs()
        try {
            clone(target)
        } catch (failure: Throwable) {
            target.deleteRecursively()
            throw failure
        }
        val record = ProjectRecord(
            slug = slug,
            name = name,
            tab = TemplateTab.Web,
            templateIndex = 0,
            templateName = "",
            config = ProjectConfig(),
            createdAt = now,
            lastOpened = now,
        )
        store.createFile(slug, ProjectStore.METADATA, record.toJson())
        return record
    }

    fun touch(slug: String, now: Long = System.currentTimeMillis()): ProjectRecord? {
        val record = find(slug) ?: return null
        val updated = record.copy(lastOpened = now)
        store.createFile(slug, ProjectStore.METADATA, updated.toJson())
        return updated
    }

    fun projectDir(slug: String): java.io.File = store.projectDir(slug)

    /**
     * True when a project folder already exists for [name]. The clone screen asks before
     * cloning into it, because landing in "name-2" by silent rename is a surprise.
     */
    fun folderTaken(name: String): Boolean {
        val slug = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "project" }
        return store.projectDir(slug).exists()
    }

    /** Where every project on this device lives. Shown, not edited, on the settings screen. */
    fun projectsDir(): java.io.File = store.projectsDir()

    fun readFile(slug: String, relative: String): String = store.readText(slug, relative)

    /** Raw bytes, used by Preview to decode images. Must be called off the main thread. */
    fun readFileBytes(slug: String, relative: String): ByteArray = store.readBytes(slug, relative)

    fun writeFile(slug: String, relative: String, content: String) =
        store.writeText(slug, relative, content)

    fun createFile(slug: String, relative: String, content: String) =
        store.createFile(slug, relative, content)

    fun createFolder(slug: String, relative: String) = store.createFolder(slug, relative)

    fun deletePath(slug: String, relative: String) = store.deletePath(slug, relative)

    /** Renames the leaf of [relative]. Returns the new path so callers can follow the file. */
    fun renamePath(slug: String, relative: String, newName: String): String =
        store.renamePath(slug, relative, newName)

    /** Moves [relative] to a project-relative [destination]. Returns where it landed. */
    fun movePath(slug: String, relative: String, destination: String): String =
        store.movePath(slug, relative, destination)

    /** Copies [relative] to a project-relative [destination]. Returns where the copy is. */
    fun copyPath(slug: String, relative: String, destination: String): String =
        store.copyPath(slug, relative, destination)

    fun tree(slug: String): List<ProjectFile> = store.readTree(slug)

    /** The record for a path just created, without walking the tree to rediscover it. */
    fun entryFor(slug: String, relative: String, isFolder: Boolean): ProjectFile =
        store.entryFor(slug, relative, isFolder)

    private fun templateNameFor(selection: SelectedTemplate): String =
        com.closecode.ui.model.Templates[selection.tab]
            .orEmpty()
            .getOrNull(selection.index)
            ?.name
            .orEmpty()

    private fun uniqueSlug(name: String): String {
        val base = slugify(name)
        var candidate = base
        var counter = 2
        // Checks the folder as well as the record: a clone that fails leaves a directory behind
        // with no `.closecode.json` in it, and the next project must not be created inside it.
        while (store.exists(candidate) || store.projectDir(candidate).exists()) {
            candidate = "$base-$counter"
            counter++
        }
        return candidate
    }

    private fun slugify(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "project" }
}