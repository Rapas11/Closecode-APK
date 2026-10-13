package com.closecode.ui.model

import androidx.compose.runtime.Immutable

enum class TemplateTab(val label: String, val noun: String) {
    Web("Websites", "Website"),
    App("Android apps", "Android app"),
}

@Immutable
data class ProjectTemplate(
    val name: String,
    val description: String,
    val tags: String,
    val hue: Float,
)

@Immutable
data class SelectedTemplate(val tab: TemplateTab, val index: Int)

@Immutable
data class ProjectFile(
    val name: String,
    val kind: FileKind,
    val language: String,
    val depth: Int,
    val expandable: Boolean = false,
    val path: String = name,
)

enum class FileKind { Folder, Kotlin, Xml, Gradle, Properties, Markdown, Json, Html, Css, Js, Csv, Unknown }

val Templates: Map<TemplateTab, List<ProjectTemplate>> = mapOf(
    TemplateTab.Web to listOf(
        ProjectTemplate("Landing page", "Hero, features and a call to action.", "HTML · CSS", 24f),
        ProjectTemplate("Portfolio", "Show your work in a clean gallery.", "HTML · CSS", 200f),
        ProjectTemplate("Blog", "Posts, tags and an about page.", "HTML · JS", 330f),
        ProjectTemplate("Online store", "Product grid with a working cart.", "HTML · JS", 20f),
        ProjectTemplate("Dashboard", "Charts, stats and data tables.", "HTML · JS", 160f),
        ProjectTemplate("Documentation", "Sidebar navigation with search.", "HTML · JS", 265f),
    ),
    TemplateTab.App to listOf(
        ProjectTemplate("Blank app", "An empty Jetpack Compose project.", "Kotlin · Compose", 150f),
        ProjectTemplate("To-do list", "Add, check off and delete tasks.", "Kotlin · Room", 24f),
        ProjectTemplate("Notes", "Offline notes with instant search.", "Kotlin · Room", 45f),
        ProjectTemplate("Weather", "Live forecast from a public API.", "Kotlin · Retrofit", 205f),
        ProjectTemplate("Chat", "A messaging screen with live updates.", "Kotlin · Firebase", 300f),
        ProjectTemplate("Tip calculator", "Split a bill in a few taps.", "Kotlin · Compose", 100f),
    ),
)

private fun androidExtras(vararg names: String): List<ProjectFile> = names.map {
    ProjectFile(it, FileKind.Kotlin, "Kotlin", 3)
}

private val androidSpine = listOf(
    ProjectFile("build.gradle.kts", FileKind.Gradle, "Gradle Kotlin", 1),
    ProjectFile("settings.gradle.kts", FileKind.Gradle, "Gradle Kotlin", 0),
    ProjectFile("gradle.properties", FileKind.Properties, "Properties", 0),
)

private val webSpine = listOf(
    ProjectFile("index.html", FileKind.Html, "HTML", 0),
    ProjectFile("styles.css", FileKind.Css, "CSS", 0),
    ProjectFile("README.md", FileKind.Markdown, "Markdown", 0),
    ProjectFile("assets", FileKind.Folder, "", 0, expandable = true),
)

fun projectTree(selection: SelectedTemplate): List<ProjectFile> {
    val template = Templates[selection.tab].orEmpty()[selection.index]
    return when (selection.tab) {
        TemplateTab.App -> {
            val extras = when (template.name) {
                "Blank app" -> emptyList()
                "To-do list" -> androidExtras("Task.kt", "TaskDao.kt")
                "Notes" -> androidExtras("Note.kt", "NoteDao.kt")
                "Weather" -> androidExtras("WeatherApi.kt", "Forecast.kt", "WeatherScreen.kt")
                "Chat" -> androidExtras("ChatScreen.kt", "Message.kt", "ChatViewModel.kt")
                "Tip calculator" -> androidExtras("TipScreen.kt")
                else -> emptyList()
            }
            listOf(
                ProjectFile("app", FileKind.Folder, "", 1, expandable = true),
                ProjectFile("build.gradle.kts", FileKind.Gradle, "Gradle Kotlin", 2),
                ProjectFile("src", FileKind.Folder, "", 2, expandable = true),
                ProjectFile("main", FileKind.Folder, "", 3, expandable = true),
                ProjectFile("MainActivity.kt", FileKind.Kotlin, "Kotlin", 4),
                ProjectFile("AndroidManifest.xml", FileKind.Xml, "XML", 4),
                ProjectFile("res", FileKind.Folder, "", 4, expandable = true),
            ) + extras + androidSpine +
                listOf(ProjectFile("test", FileKind.Folder, "", 1, expandable = true))
        }
        TemplateTab.Web -> {
            val extras = when (template.name) {
                "Landing page" -> emptyList()
                "Portfolio" -> listOf(
                    ProjectFile("projects.html", FileKind.Html, "HTML", 0),
                    ProjectFile("projects.json", FileKind.Json, "JSON", 0),
                )
                "Blog" -> listOf(
                    ProjectFile("main.js", FileKind.Js, "JavaScript", 0),
                    ProjectFile("posts", FileKind.Folder, "", 0, expandable = true),
                )
                "Online store" -> listOf(
                    ProjectFile("main.js", FileKind.Js, "JavaScript", 0),
                    ProjectFile("products.json", FileKind.Json, "JSON", 0),
                )
                "Dashboard" -> listOf(
                    ProjectFile("main.js", FileKind.Js, "JavaScript", 0),
                    ProjectFile("charts.js", FileKind.Js, "JavaScript", 0),
                    ProjectFile("data.csv", FileKind.Csv, "CSV", 0),
                )
                "Documentation" -> listOf(
                    ProjectFile("main.js", FileKind.Js, "JavaScript", 0),
                    ProjectFile("docs", FileKind.Folder, "", 0, expandable = true),
                )
                else -> emptyList()
            }
            webSpine + extras
        }
    }
}

fun recentFiles(selection: SelectedTemplate): List<ProjectFile> = when (selection.tab) {
    TemplateTab.App -> listOf(
        ProjectFile("HomeScreen.kt", FileKind.Kotlin, "Kotlin", 0),
        ProjectFile("Theme.kt", FileKind.Kotlin, "Kotlin", 0),
        ProjectFile("strings.xml", FileKind.Xml, "XML", 0),
    )
    TemplateTab.Web -> listOf(
        ProjectFile("main.js", FileKind.Js, "JavaScript", 0),
        ProjectFile("styles.css", FileKind.Css, "CSS", 0),
        ProjectFile("projects.json", FileKind.Json, "JSON", 0),
    )
}

fun openTabs(selection: SelectedTemplate): List<ProjectFile> =
    projectTree(selection).filter { it.kind != FileKind.Folder }.take(3)