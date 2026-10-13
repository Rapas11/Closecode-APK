package com.closecode.ui.model

import androidx.compose.runtime.Immutable
import java.util.Locale

private const val FieldSep = "\u0001"

@Immutable
data class ProjectConfig(
    val packageName: String = "com.closecode.myproject",
    val language: String = "Kotlin",
    val gradleVersion: String = "8.13",
    val minSdk: String = "29",
    val compileSdk: String = "35",
    val targetSdk: String = "35",
    val buildType: String = "Debug",
) {
    fun encode(): String = listOf(
        packageName,
        language,
        gradleVersion,
        minSdk,
        compileSdk,
        targetSdk,
        buildType,
    ).joinToString(FieldSep)

    companion object {
        fun decode(raw: String): ProjectConfig = runCatching {
            val parts = raw.split(FieldSep)
            ProjectConfig(
                packageName = parts[0],
                language = parts[1],
                gradleVersion = parts[2],
                minSdk = parts[3],
                compileSdk = parts[4],
                targetSdk = parts[5],
                buildType = parts[6],
            )
        }.getOrDefault(ProjectConfig())
    }
}

val LanguageOptions = listOf("Kotlin", "Java")

val GradleOptions = listOf("8.13", "8.12", "8.9", "8.5")

val MinSdkOptions = listOf("21", "23", "24", "26", "29", "31", "33", "34", "35")

val SdkOptions = listOf("33", "34", "35", "36")

val BuildTypeOptions = listOf("Debug", "Release")

fun defaultPackageName(projectName: String): String {
    val slug = projectName
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
        .ifEmpty { "myproject" }
    return "com.closecode.$slug"
}

private val JavaReservedWords = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "null", "_",
)

fun packageNameError(packageName: String): String? {
    val parts = packageName.split('.')
    if (parts.size < 2) return "The package name must have at least two parts."
    if (parts.any { part ->
            part.isEmpty() ||
                !part.first().isLetter() ||
                part.any { !it.isLetterOrDigit() && it != '_' }
        }
    ) {
        return "Each package part must start with a letter and contain only letters, numbers, or underscores."
    }
    if (parts.any { it in JavaReservedWords }) {
        return "Package parts cannot use reserved Java words."
    }
    return null
}

fun packageNameFromProject(manifestXml: String, appBuildFile: String): String? {
    return packageNamesFromProject(manifestXml, appBuildFile).firstOrNull()
}

fun packageNamesFromProject(manifestXml: String, appBuildFile: String): List<String> {
    val manifestPackage = Regex("""package\s*=\s*["']([^"']+)["']""")
        .find(manifestXml)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
    val buildPackages = sequenceOf("applicationId", "namespace")
        .mapNotNull { property ->
            Regex("""(?m)^\s*$property\s*=?\s*["']([^"']+)["']""")
                .find(appBuildFile)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
        }
        .toList()
    return (listOfNotNull(manifestPackage) + buildPackages).distinct()
}

fun replaceManifestPackage(manifestXml: String, packageName: String): String {
    val attribute = Regex("""\bpackage\s*=\s*["'][^"']*["']""")
    if (attribute.containsMatchIn(manifestXml)) {
        return attribute.replaceFirst(manifestXml, "package=\"$packageName\"")
    }
    val manifestTag = Regex("""<manifest\b""")
    require(manifestTag.containsMatchIn(manifestXml)) {
        "The Android manifest has no <manifest> tag."
    }
    return manifestTag.replaceFirst(manifestXml, "<manifest package=\"$packageName\"")
}

fun replaceAndroidBuildPackage(buildFile: String, packageName: String): String {
    val assignment = Regex("""\b(namespace|applicationId)\s*(=\s*)?(["'])[^"']*\3""")
    return assignment.replace(buildFile) { match ->
        val quote = match.groupValues[3]
        "${match.groupValues[1]} ${match.groupValues[2]}$quote$packageName$quote"
    }
}

fun replaceSourcePackagePrefixes(
    source: String,
    oldPackageNames: List<String>,
    newPackageName: String,
): String {
    var updated = source
    oldPackageNames.distinct().sortedByDescending { it.length }.forEach { oldPackage ->
        val declaration = Regex(
            """(?m)^(\s*package\s+)${Regex.escape(oldPackage)}(?=\.|\s*$)""",
        )
        updated = declaration.replace(updated) { match ->
            "${match.groupValues[1]}$newPackageName"
        }
    }
    return updated
}