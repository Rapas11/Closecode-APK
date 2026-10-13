package com.closecode

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab

private const val FIELD = "|"

data class Project(
    val name: String,
    val selection: SelectedTemplate,
    val templateName: String,
    val config: ProjectConfig = ProjectConfig(),
    val slug: String = slugify(name),
) {
    companion object {
        fun slugify(value: String): String = value
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "project" }
    }
}

object ProjectCodec {

    fun encode(project: Project): String = listOf(
        esc(project.name),
        esc(project.selection.tab.name),
        esc(project.selection.index.toString()),
        esc(project.templateName),
        esc(project.config.encode()),
        esc(project.slug),
    ).joinToString(FIELD)

    fun decode(raw: String): Project? {
        if (raw.isBlank()) return null
        val parts = raw.split(FIELD)
        if (parts.size != 6) return null
        return runCatching {
            // A project id is one directory name under the projects root, not a path. The record is
            // a saved string, so it is re-checked here: a stale or hand-edited one must fall back to
            // the start screen rather than let `projectDir` aim a project root outside its folder.
            val slug = unesc(parts[5])
            require(
                slug.isNotBlank() && slug != "." && slug != ".." &&
                    '/' !in slug && '\\' !in slug && slug.none { it.isISOControl() },
            ) { "Illegal project id." }
            Project(
                name = unesc(parts[0]),
                selection = SelectedTemplate(
                    tab = TemplateTab.valueOf(unesc(parts[1])),
                    index = unesc(parts[2]).toInt(),
                ),
                templateName = unesc(parts[3]),
                config = ProjectConfig.decode(unesc(parts[4])),
                slug = slug,
            )
        }.getOrNull()
    }

    private fun esc(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun unesc(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}