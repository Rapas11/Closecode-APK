package com.closecode.ui.project

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates

data class ScaffoldFile(
    val path: String,
    val content: String,
)

object Scaffold {

    fun filesFor(
        projectName: String,
        selection: SelectedTemplate,
        config: ProjectConfig,
    ): List<ScaffoldFile> {
        val template = Templates[selection.tab].orEmpty()[selection.index]
        return if (selection.tab == TemplateTab.App) {
            androidFiles(projectName, template.name, config)
        } else {
            webFiles(projectName, template.name)
        }
    }

    private fun androidFiles(
        projectName: String,
        templateName: String,
        config: ProjectConfig,
    ): List<ScaffoldFile> {
        val packagePath = config.packageName.replace('.', '/')
        return buildList {
            add(ScaffoldFile("settings.gradle.kts", settingsGradle(projectName)))
            add(ScaffoldFile("build.gradle.kts", rootGradle()))
            add(ScaffoldFile("gradle.properties", gradleProperties()))
            add(
                ScaffoldFile(
                    "app/build.gradle.kts",
                    appGradle(config),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/AndroidManifest.xml",
                    manifest(config.packageName),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/java/$packagePath/MainActivity.kt",
                    mainActivity(config.packageName),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/java/$packagePath/Theme.kt",
                    themeKotlin(config.packageName),
                )
            )
            add(ScaffoldFile("app/src/main/res/values/strings.xml", stringsXml(projectName)))
            add(ScaffoldFile("app/src/main/res/values/themes.xml", themesXml()))
            addAll(androidExtras(templateName, config.packageName, packagePath))
        }
    }

    private fun androidExtras(
        templateName: String,
        packageName: String,
        packagePath: String,
    ): List<ScaffoldFile> = when (templateName) {
        "To-do list" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Task.kt",
                dataClassKotlin(packageName, "Task", "id: Long, title: String, done: Boolean"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/TaskDao.kt",
                interfaceKotlin(packageName, "TaskDao", "fun all(): List<Task>"),
            ),
        )

        "Notes" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Note.kt",
                dataClassKotlin(packageName, "Note", "id: Long, title: String, body: String"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/NoteDao.kt",
                interfaceKotlin(packageName, "NoteDao", "fun all(): List<Note>"),
            ),
        )

        "Weather" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Forecast.kt",
                dataClassKotlin(packageName, "Forecast", "day: String, high: Int, low: Int"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherApi.kt",
                interfaceKotlin(
                    packageName,
                    "WeatherApi",
                    "fun forecast(city: String): List<Forecast>",
                ),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherScreen.kt",
                screenKotlin(packageName, "Weather"),
            ),
        )

        "Chat" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Message.kt",
                dataClassKotlin(packageName, "Message", "id: String, body: String, mine: Boolean"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/ChatScreen.kt",
                screenKotlin(packageName, "Chat"),
            ),
        )

        "Tip calculator" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/TipScreen.kt",
                screenKotlin(packageName, "Tip"),
            ),
        )

        else -> emptyList()
    }

    private fun webFiles(
        projectName: String,
        templateName: String,
    ): List<ScaffoldFile> = buildList {
        add(ScaffoldFile("index.html", indexHtml(projectName)))
        add(ScaffoldFile("styles.css", stylesCss()))
        add(ScaffoldFile("README.md", readme(projectName, templateName)))
        add(ScaffoldFile("assets/.gitkeep", ""))
        addAll(webExtras(templateName))
    }

    private fun webExtras(templateName: String): List<ScaffoldFile> =
        when (templateName) {
            "Portfolio" -> listOf(
                ScaffoldFile("projects.html", indexHtml("Projects")),
                ScaffoldFile("projects.json", jsonList("title")),
            )

            "Blog" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("posts/.gitkeep", ""),
            )

            "Online store" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("products.json", jsonList("title", "price")),
            )

            "Dashboard" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("charts.js", mainJs()),
                ScaffoldFile("data.csv", "label,value\nAlpha,10\nBeta,20\nGamma,30\n"),
            )

            "Documentation" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("docs/index.md", readme("Documentation", "Documentation")),
            )

            else -> emptyList()
        }

    private fun settingsGradle(projectName: String): String = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
            }
        }

        rootProject.name = "${slugify(projectName)}"
        include(":app")
    """.trimIndent() + "\n"

    private fun rootGradle(): String = """
        plugins {
            id("com.android.application") version "8.12.0" apply false
            id("org.jetbrains.kotlin.android") version "${KOTLIN_PLUGIN}" apply false
        }
    """.trimIndent() + "\n"

    private fun gradleProperties(): String = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        android.useAndroidX=true
    """.trimIndent() + "\n"

    private fun appGradle(config: ProjectConfig): String {
        val androidPlugin = "com.android.application"
        val kotlinPlugin = if (config.language == "Java") {
            "org.jetbrains.kotlin.android"
        } else {
            "org.jetbrains.kotlin.android"
        }
        return """
            plugins {
                id("$androidPlugin")
                id("$kotlinPlugin")
            }

            android {
                namespace = "${config.packageName}"
                compileSdk = ${config.compileSdk}

                defaultConfig {
                    applicationId = "${config.packageName}"
                    minSdk = ${config.minSdk}
                    targetSdk = ${config.targetSdk}
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    ${config.buildType.lowercase()} {
                        isMinifyEnabled = false
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation(platform("androidx.compose:compose-bom:2024.04.01"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
            }
        """.trimIndent() + "\n"
    }

    private fun manifest(packageName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

            <application
                android:label="@string/app_name"
                android:theme="@style/Theme.${simpleName(packageName)}">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
    """.trimIndent() + "\n"

    private fun mainActivity(packageName: String): String = """
        package $packageName

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent {
                    ClosecodeTheme {
                        Text("Hello, world")
                    }
                }
            }
        }
    """.trimIndent() + "\n"

    private fun themeKotlin(packageName: String): String = """
        package $packageName

        import androidx.compose.runtime.Composable

        @Composable
        fun ClosecodeTheme(content: @Composable () -> Unit) {
            content()
        }
    """.trimIndent() + "\n"

    private fun dataClassKotlin(
        packageName: String,
        name: String,
        fields: String,
    ): String {
        val constructorFields = fields.split(", ").joinToString(",\n    ") { field ->
            val clean = field.split(":").first().trim()
            val type = field.substringAfter(":").trim()
            "val $clean: $type"
        }
        return """
            package $packageName

            data class $name(
                $constructorFields
            )
        """.trimIndent() + "\n"
    }

    private fun interfaceKotlin(
        packageName: String,
        name: String,
        members: String,
    ): String = """
        package $packageName

        interface $name {
            $members
        }
    """.trimIndent() + "\n"

    private fun screenKotlin(packageName: String, name: String): String = """
        package $packageName

        import androidx.compose.runtime.Composable
        import androidx.compose.material3.Text

        @Composable
        fun ${name}Screen() {
            Text("$name")
        }
    """.trimIndent() + "\n"

    private fun stringsXml(projectName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${escapeXml(projectName)}</string>
        </resources>
    """.trimIndent() + "\n"

    private fun themesXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <style name="Theme.App" parent="android:Theme.Material.NoActionBar" />
        </resources>
    """.trimIndent() + "\n"

    /**
     * The page a new project starts with.
     *
     * Richer than it was on purpose. It used to be a bare `<h1>`, which made Preview impossible to
     * tell apart from a failure: an empty-looking page is what a broken `styles.css` also looks
     * like. A heading, a line of copy, a button and a rule give the preview something to show and the
     * editor something to open, and it is all the reader can delete.
     *
     * The title and the heading both say the project's name, which is the same thing the editor's own
     * HTML snippet now says — the two used to disagree, so the file on disk and the file the snippet
     * produced were visibly different documents.
     */
    private fun indexHtml(projectName: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${escapeHtml(projectName)}</title>
          <link rel="stylesheet" href="styles.css">
        </head>
        <body>
          <main class="hero">
            <h1>${escapeHtml(projectName)}</h1>
            <p>Edit this page and watch it change.</p>
            <button type="button" class="primary">Run</button>
            <hr>
            <small>Created by Closecode</small>
          </main>
        </body>
        </html>
    """.trimIndent() + "\n"

    private fun stylesCss(): String = """
        :root {
          --bg: #f8f7f3;
          --tx: #12151c;
        }

        * {
          box-sizing: border-box;
          margin: 0;
        }

        body {
          background: var(--bg);
          color: var(--tx);
          font-family: system-ui, sans-serif;
        }

        .hero {
          display: grid;
          gap: 12px;
          justify-items: start;
          padding: 48px 22px;
        }

        .hero p {
          margin: 0;
          color: #5b6b83;
          max-width: 34ch;
          line-height: 1.5;
        }

        .hero .primary {
          background: #2457ff;
          color: #fff;
          border: 0;
          border-radius: 10px;
          padding: 12px 22px;
          font: inherit;
          font-weight: 600;
        }

        .hero small {
          color: #8793a8;
        }
    """.trimIndent() + "\n"

    private fun mainJs(): String = """
        document.addEventListener('DOMContentLoaded', function () {
          console.log('ready');
        });
    """.trimIndent() + "\n"

    private fun jsonList(vararg fields: String): String {
        val keys = fields.joinToString(", ") { "\"$it\"" }
        return """
            [
              { $keys },
              { $keys }
            ]
        """.trimIndent() + "\n"
    }

    private fun readme(projectName: String, templateName: String): String = """
        # $projectName

        Created with Closecode from the $templateName template.
    """.trimIndent() + "\n"

    private fun simpleName(packageName: String): String =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    private fun slugify(value: String): String = value
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '-' }
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "project" }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "\\\"")

    private const val KOTLIN_PLUGIN = "1.9.24"
}