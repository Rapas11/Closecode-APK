package com.closecode.build

import java.io.File

object ProjectBuildContract {
    private val supportedPlugins = setOf(
        "com.android.application",
        "org.jetbrains.kotlin.android",
    )

    private val supportedDependencies = setOf(
        "androidx.core:core-ktx:1.13.1",
        "androidx.activity:activity-compose:1.9.3",
        "androidx.compose:compose-bom:2024.04.01",
        "androidx.compose.ui:ui",
        "androidx.compose.material3:material3",
    )

    fun validate(projectDir: File): List<ContractViolation> {
        val violations = mutableListOf<ContractViolation>()
        val appGradle = listOf("app/build.gradle.kts", "app/build.gradle")
            .map { it to File(projectDir, it) }
            .firstOrNull { (_, file) -> file.isFile }
            ?: return violations
        val rootGradle = listOf("build.gradle.kts", "build.gradle")
            .map { it to File(projectDir, it) }
            .firstOrNull { (_, file) -> file.isFile }

        if (File(projectDir, "gradle/libs.versions.toml").isFile) {
            violations += ContractViolation(
                "gradle/libs.versions.toml",
                "Version catalogs are not supported by the first on-device builder.",
            )
        }
        if (File(projectDir, "gradle.properties").isFile) {
            violations += unsupportedFeatures(
                "gradle.properties",
                File(projectDir, "gradle.properties"),
                listOf(Regex("""(?m)^\s*org\.gradle\.java\.home\s*=""") to
                    "Setting a desktop Java path is not supported on the phone."),
            )
        }

        rootGradle?.let { (path, file) ->
            val text = read(file, path, violations) ?: return@let
            checkPlugins(text, path, violations)
            checkFixedPluginVersions(text, path, violations)
            checkNoCustomExecution(text, path, violations)
        }

        val (appPath, appFile) = appGradle
        if (rootGradle == null) {
            violations += ContractViolation(
                appPath,
                "The root build.gradle(.kts) file is missing; the first on-device builder requires the template build setup.",
            )
        }
        val appText = read(appFile, appPath, violations) ?: return violations
        checkPlugins(appText, appPath, violations)
        val appPluginIds = pluginIds(appText)
        supportedPlugins.forEach { expected ->
            if (expected !in appPluginIds) {
                violations += ContractViolation(
                    appPath,
                    "The app build must apply '$expected' as in a CloseCode Android template.",
                )
            }
        }
        checkDependencies(appText, appPath, violations)
        checkNoCustomExecution(appText, appPath, violations)
        checkUnsupportedAndroidFeatures(appText, appPath, violations)

        return violations.distinct()
    }

    private fun checkPlugins(
        text: String,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        text.lineSequence().forEachIndexed { index, line ->
            val pluginId = Regex("""\bid\s*\(?\s*["']([^"']+)["']""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
            if (pluginId != null && pluginId !in supportedPlugins) {
                violations += unsupported(path, index, "Gradle plugin '$pluginId' is not supported yet.")
            } else if (Regex("""\balias\s*\(""").containsMatchIn(line)) {
                violations += unsupported(path, index, "Plugin aliases are not supported by the first on-device builder.")
            }
        }
    }

    private fun pluginIds(text: String): Set<String> =
        Regex("""\bid\s*\(?\s*["']([^"']+)["']""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

    private fun checkFixedPluginVersions(
        text: String,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        val expectedVersions = mapOf(
            "com.android.application" to "8.12.0",
            "org.jetbrains.kotlin.android" to "1.9.24",
        )
        val foundPlugins = mutableSetOf<String>()
        text.lineSequence().forEachIndexed { index, line ->
            val declaredPlugin = Regex("""\bid\s*\(?\s*["']([^"']+)["']""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?: return@forEachIndexed
            if (declaredPlugin !in expectedVersions) return@forEachIndexed
            foundPlugins += declaredPlugin
            val match = Regex(
                """\bid\s*\(?\s*["']([^"']+)["']\s*\)?\s+version\s+["']([^"']+)["']""",
            ).find(line)
            if (match == null) {
                violations += unsupported(
                    path,
                    index,
                    "Plugin '$declaredPlugin' must pin version ${expectedVersions.getValue(declaredPlugin)}.",
                )
                return@forEachIndexed
            }
            val plugin = match.groupValues[1]
            val version = match.groupValues[2]
            val expected = expectedVersions[plugin] ?: return@forEachIndexed
            if (version != expected) {
                violations += unsupported(
                    path,
                    index,
                    "Plugin '$plugin' uses version $version; this builder supports $expected.",
                )
            }
        }
        expectedVersions.keys.subtract(foundPlugins).forEach { plugin ->
            violations += ContractViolation(
                path,
                "Line 1: The root build must pin '$plugin' to the CloseCode template version ${expectedVersions.getValue(plugin)}.",
            )
        }
    }

    private fun checkDependencies(
        text: String,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        val dependencyCall = Regex(
            """\b(implementation|api|compileOnly|runtimeOnly|kapt|ksp|testImplementation|androidTestImplementation)\s*\(""",
        )
        text.lineSequence().forEachIndexed { index, line ->
            val call = dependencyCall.find(line) ?: return@forEachIndexed
            val configuration = call.groupValues[1]
            val coordinate = Regex("""["']([^"']+)["']""")
                .find(line.substring(call.range.last + 1))
                ?.groupValues
                ?.getOrNull(1)
            val supported = configuration == "implementation" &&
                coordinate != null &&
                coordinate in supportedDependencies
            if (!supported) {
                val detail = coordinate?.let { " '$it'" }.orEmpty()
                violations += unsupported(
                    path,
                    index,
                    "Dependency$detail in '$configuration' is not supported by the first on-device builder.",
                )
            }
        }
    }

    private fun checkNoCustomExecution(
        text: String,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        val unsupported = listOf(
            Regex("""\b(buildscript|task\s+\w+|tasks\.|apply\s*\(|apply\s+from:)""") to
                "Custom Gradle scripts and tasks are not run by the on-device builder.",
            Regex("""\b(kapt|ksp|annotationProcessor)\s*\(""") to
                "Code-generation plugins and processors are not supported by the first on-device builder.",
        )
        text.lineSequence().forEachIndexed { index, line ->
            unsupported.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }
                ?.let { (_, message) -> violations += unsupported(path, index, message) }
        }
    }

    private fun checkUnsupportedAndroidFeatures(
        text: String,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        val unsupported = listOf(
            Regex("""\b(viewBinding|aidl|buildConfig)\s*=\s*true""") to
                "Generated Android code features are not supported by the first on-device builder.",
            Regex("""\bisMinifyEnabled\s*=\s*true""") to
                "Release shrinking is not supported by the first on-device builder.",
        )
        text.lineSequence().forEachIndexed { index, line ->
            unsupported.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }
                ?.let { (_, message) -> violations += unsupported(path, index, message) }
        }
    }

    private fun unsupportedFeatures(
        path: String,
        file: File,
        patterns: List<Pair<Regex, String>>,
    ): List<ContractViolation> {
        val text = try {
            file.readText()
        } catch (error: Exception) {
            return listOf(
                ContractViolation(
                    path,
                    "Could not read this build file: ${error.message ?: "access failed"}.",
                ),
            )
        }
        return text.lineSequence().mapIndexedNotNull { index, line ->
            patterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }
                ?.let { (_, message) -> unsupported(path, index, message) }
        }.toList()
    }

    private fun read(
        file: File,
        path: String,
        violations: MutableList<ContractViolation>,
    ): String? = try {
        file.readText()
    } catch (error: Exception) {
        violations += ContractViolation(path, "Could not read this build file: ${error.message ?: "access failed"}.")
        null
    }

    private fun unsupported(path: String, zeroBasedLine: Int, message: String) =
        ContractViolation(path, "Line ${zeroBasedLine + 1}: $message")
}
