package com.closecode

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.project.ProjectPathException
import com.closecode.ui.project.ProjectSafety
import com.closecode.ui.project.Scaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun root(): File = temp.newFolder("project")

    private fun selection(tab: TemplateTab, index: Int) = SelectedTemplate(tab, index)

    private fun config(pkg: String = "com.closecode.notes") = ProjectConfig(
        packageName = pkg,
        minSdk = "29",
        compileSdk = "35",
        targetSdk = "35",
    )

    // ---- path confinement -------------------------------------------------

    @Test
    fun resolvesPathsInsideTheProject() {
        val root = root()
        val resolved = ProjectSafety.resolveInProject(root, "app/src/main/java/A.kt")
        assertTrue(resolved.canonicalPath.startsWith(root.canonicalPath))
    }

    @Test
    fun rejectsParentTraversal() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "../escaped.txt") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsDeepParentTraversal() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "a/b/../../../out.txt") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsAbsolutePaths() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "/etc/passwd") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsEmptyPaths() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsSymlinkEscape() {
        val root = root()
        val outside = temp.newFolder("outside")
        val secret = File(outside, "secret.txt").apply { writeText("nope") }

        val link = File(root, "link")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), secret.toPath())
        } catch (unsupported: UnsupportedOperationException) {
            return // filesystem cannot make symlinks; nothing to assert
        } catch (denied: SecurityException) {
            return
        } catch (io: java.io.IOException) {
            return
        }

        val error = runCatching { ProjectSafety.resolveInProject(root, "link") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    // ---- secrets ----------------------------------------------------------

    @Test
    fun refusesSecretFileNames() {
        listOf(
            "local.properties",
            ".env",
            "release.jks",
            "release.keystore",
            "server.pem",
            "google-services.json"
        ).forEach { name ->
            assertTrue("expected $name to be treated as secret", ProjectSafety.isSecretName(name))
        }
    }

    @Test
    fun allowsOrdinaryFileNames() {
        listOf("MainActivity.kt", "build.gradle.kts", "strings.xml", "index.html").forEach { name ->
            assertFalse("expected $name to be allowed", ProjectSafety.isSecretName(name))
        }
    }

    @Test
    fun hidesMetadataFile() {
        assertTrue(ProjectSafety.isHiddenName(".closecode.json"))
        assertFalse(ProjectSafety.isHiddenName("MainActivity.kt"))
    }

    // ---- atomic writes ---------------------------------------------------

    @Test
    fun atomicWriteReplacesContent() {
        val target = File(root(), "a.txt")
        ProjectSafety.writeAtomic(target, "first")
        assertEquals("first", target.readText())
        ProjectSafety.writeAtomic(target, "second")
        assertEquals("second", target.readText())
    }

    @Test
    fun atomicWriteLeavesNoTempFileBehind() {
        val root = root()
        val target = File(root, "b.txt")
        ProjectSafety.writeAtomic(target, "hello")
        val leftovers = root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue("temp files left behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun atomicWriteCreatesParentDirectories() {
        val target = File(File(root(), "deep/nested"), "c.txt")
        ProjectSafety.writeAtomic(target, "ok")
        assertEquals("ok", target.readText())
    }

    // ---- scaffolding ------------------------------------------------------

    @Test
    fun androidScaffoldWritesExpectedFiles() {
        val files = Scaffold.filesFor("Notes", selection(TemplateTab.App, 2), config())
        val paths = files.map { it.path }.toSet()

        assertTrue(paths.contains("settings.gradle.kts"))
        assertTrue(paths.contains("build.gradle.kts"))
        assertTrue(paths.contains("gradle.properties"))
        assertTrue(paths.contains("app/build.gradle.kts"))
        assertTrue(paths.contains("app/src/main/AndroidManifest.xml"))
        assertTrue(paths.contains("app/src/main/res/values/strings.xml"))
        assertTrue(paths.contains("app/src/main/res/values/themes.xml"))
    }

    @Test
    fun androidScaffoldPlacesKotlinUnderPackagePath() {
        val files = Scaffold.filesFor("Notes", selection(TemplateTab.App, 0), config("com.example.demo"))
        val main = files.firstOrNull { it.path.endsWith("MainActivity.kt") }
        assertNotNull(main)
        assertEquals("app/src/main/java/com/example/demo/MainActivity.kt", main!!.path)
        assertTrue(main.content.contains("package com.example.demo"))
    }

    @Test
    fun gradleFileSubstitutesChosenValues() {
        val cfg = ProjectConfig(
            packageName = "com.example.tool",
            minSdk = "24",
            compileSdk = "34",
            targetSdk = "34",
            buildType = "Release",
            gradleVersion = "8.9",
        )
        val gradle = Scaffold.filesFor("Tool", selection(TemplateTab.App, 0), cfg)
            .first { it.path == "app/build.gradle.kts" }.content

        assertTrue(gradle.contains("com.example.tool"))
        assertTrue(gradle.contains("minSdk = 24"))
        assertTrue(gradle.contains("compileSdk = 34"))
        assertTrue(gradle.contains("release {"))
        assertTrue(!gradle.contains("minSdk = 29"))
    }

    @Test
    fun manifestMatchesPackageName() {
        val manifest = Scaffold.filesFor("Demo", selection(TemplateTab.App, 0), config("com.example.pkg"))
            .first { it.path.endsWith("AndroidManifest.xml") }.content
        assertTrue(manifest.contains("<manifest"))
        assertTrue(manifest.contains(".MainActivity"))
    }

    @Test
    fun webScaffoldHasNoGradleFiles() {
        val files = Scaffold.filesFor("Blog", selection(TemplateTab.Web, 2), config())
        val paths = files.map { it.path }
        assertTrue(paths.contains("index.html"))
        assertTrue(paths.contains("styles.css"))
        assertTrue(paths.contains("README.md"))
        assertFalse(paths.any { it.contains("build.gradle") })
    }

    @Test
    fun templateExtrasAppear() {
        val weather = Scaffold.filesFor("W", selection(TemplateTab.App, 3), config())
            .map { it.path }
        assertTrue(weather.any { it.endsWith("WeatherApi.kt") })

        val blog = Scaffold.filesFor("B", selection(TemplateTab.Web, 2), config())
            .map { it.path }
        assertTrue(blog.any { it.endsWith("main.js") })

        val blank = Scaffold.filesFor("B", selection(TemplateTab.App, 0), config())
            .map { it.path }
        assertFalse(blank.any { it.endsWith("WeatherApi.kt") })
    }

    @Test
    fun stringsXmlCarriesProjectName() {
        val strings = Scaffold.filesFor("My App", selection(TemplateTab.App, 0), config())
            .first { it.path.endsWith("strings.xml") }.content
        assertTrue(strings.contains("My App"))
    }

    @Test
    fun everyScaffoldFileHasANonEmptyRelativePath() {
        val files = Scaffold.filesFor("Chk", selection(TemplateTab.App, 0), config()) +
            Scaffold.filesFor("Chk", selection(TemplateTab.Web, 0), config())
        files.forEach { file ->
            assertTrue("absolute path: ${file.path}", !file.path.startsWith("/"))
            assertTrue("traversal in ${file.path}", !file.path.contains(".."))
        }
    }
}