package com.closecode.build

import com.closecode.ui.settings.InMemoryKeyValueStore
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.project.Scaffold
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.concurrent.thread

class BuildEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unavailableToolchainDoesNotCreateOrClaimAnApk() = runBlocking {
        val project = temporaryFolder.newFolder("project")
        val store = ToolchainStore(
            File(temporaryFolder.root, "toolchain"),
            InMemoryKeyValueStore(),
        )

        assertFalse(store.isReady())
        val result = BuildEngine(store).build(project)

        assertTrue(result is BuildOutcome.NeedsSetup)
        assertFalse(File(project, "build/outputs/apk/debug/app-debug.apk").exists())
    }

    @Test
    fun downloadingVerifiedSourcesDoesNotClaimThePhoneToolchainIsReady() = runBlocking {
        val store = ToolchainStore(
            File(temporaryFolder.root, "toolchain"),
            InMemoryKeyValueStore(),
            components = emptyList(),
            minimumFreeBytes = 0,
        )

        store.ensureAll()

        val state = store.state.value
        assertTrue(state is ToolchainState.DownloadedNotReady)
        assertFalse(store.isReady())
        assertTrue((state as ToolchainState.DownloadedNotReady).message.contains("arm64 aapt2"))
    }

    @Test
    fun platformAndDesktopCompilerJarDoNotEnableApkCreation() = runBlocking {
        val toolchain = File(temporaryFolder.root, "partial-toolchain").apply { mkdirs() }
        val platform = File(toolchain, "platform-35").apply { mkdirs() }
        File(platform, "android.jar").writeText("platform")
        File(platform, ".closecode-platform-ready").writeText("35")
        File(toolchain, "kotlin-compiler-embeddable-2.4.0.jar").writeText("desktop jar")
        val project = temporaryFolder.newFolder("partial-project")
        val manifest = File(project, "app/src/main/AndroidManifest.xml")
        checkNotNull(manifest.parentFile).mkdirs()
        manifest.writeText("""<manifest package="com.closecode.demo" />""")

        val store = ToolchainStore(
            toolchain,
            InMemoryKeyValueStore(),
            components = listOf(ToolchainManifest.components.first()),
            minimumFreeBytes = 0,
        )

        assertFalse(store.isReady())
        val result = BuildEngine(store).build(project)

        assertTrue(result is BuildOutcome.NeedsSetup)
        assertFalse(File(project, "build/outputs/apk/debug/app-debug.apk").exists())
    }

    @Test
    fun missingBuildEnginePreservesLastGoodApkButDoesNotReportSuccess() = runBlocking {
        val project = temporaryFolder.newFolder("project-with-last-good-apk")
        val lastGoodApk = File(project, "build/outputs/apk/debug/app-debug.apk")
        checkNotNull(lastGoodApk.parentFile).mkdirs()
        val existingBytes = byteArrayOf(1, 2, 3, 4)
        lastGoodApk.writeBytes(existingBytes)

        val result = BuildEngine(newStore()).build(project)

        assertTrue(result is BuildOutcome.NeedsSetup)
        assertEquals(existingBytes.toList(), lastGoodApk.readBytes().toList())
    }

    @Test
    fun currentBlankAppScaffoldPassesTheBuildContract() {
        val project = temporaryFolder.newFolder("blank-template")
        Scaffold.filesFor(
            projectName = "Blank App",
            selection = SelectedTemplate(TemplateTab.App, 0),
            config = ProjectConfig(packageName = "com.closecode.blankapp"),
        ).forEach { file ->
            val destination = File(project, file.path)
            checkNotNull(destination.parentFile).mkdirs()
            destination.writeText(file.content)
        }

        assertTrue(ProjectBuildContract.validate(project).toString(), ProjectBuildContract.validate(project).isEmpty())
    }

    @Test
    fun unsupportedPluginNamesItsGradleFileAndLine() {
        val project = temporaryFolder.newFolder("unsupported-plugin")
        writeGradleFiles(
            project,
            appGradle = """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("com.google.gms.google-services")
                }
            """.trimIndent(),
        )

        val violations = ProjectBuildContract.validate(project)

        assertTrue(violations.any {
            it.path == "app/build.gradle.kts" &&
                it.message.startsWith("Line 4:") &&
                it.message.contains("com.google.gms.google-services")
        })
    }

    @Test
    fun changedKotlinPluginVersionIsRejected() {
        val project = temporaryFolder.newFolder("changed-plugin-version")
        val root = File(project, "build.gradle.kts")
        root.writeText(
            """
                plugins {
                    id("com.android.application") version "8.12.0" apply false
                    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
                }
            """.trimIndent(),
        )
        val app = File(project, "app/build.gradle.kts")
        checkNotNull(app.parentFile).mkdirs()
        app.writeText(
            """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
            """.trimIndent(),
        )

        val violations = ProjectBuildContract.validate(project)

        assertTrue(violations.any {
            it.path == "build.gradle.kts" &&
                it.message.contains("version 2.0.0") &&
                it.message.contains("supports 1.9.24")
        })
    }

    @Test
    fun unsupportedLibraryIsRejectedBeforeToolchainSetup() = runBlocking {
        val project = temporaryFolder.newFolder("unsupported-library")
        writeGradleFiles(
            project,
            appGradle = """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                dependencies {
                    implementation("androidx.room:room-runtime:2.6.1")
                }
            """.trimIndent(),
        )

        val result = BuildEngine(newStore()).build(project)

        assertTrue(result is BuildOutcome.ContractFailed)
        val violation = (result as BuildOutcome.ContractFailed).violations.single()
        assertEquals("app/build.gradle.kts", violation.path)
        assertTrue(violation.message.startsWith("Line 6:"))
        assertTrue(violation.message.contains("androidx.room:room-runtime:2.6.1"))
    }

    @Test
    fun toolchainManifestUsesPinnedHttpsArtifactsWithChecksumsAndLicenses() {
        assertTrue(ToolchainManifest.components.isNotEmpty())
        assertTrue(ToolchainManifest.components.all { component ->
            component.url.startsWith("https://") &&
                component.sha256.matches(Regex("[a-f0-9]{64}")) &&
                component.sizeBytes > 0L &&
                component.license.isNotBlank()
        })
    }

    @Test
    fun androidPlatformDownloadRequiresTheSdkLicense() = runBlocking {
        val store = ToolchainStore(
            dir = File(temporaryFolder.root, "toolchain-license"),
            kv = InMemoryKeyValueStore(),
            components = listOf(
                ToolchainManifest.components.single { it.license == "Android SDK License" },
            ),
            minimumFreeBytes = 0,
        )

        store.ensureAll()

        assertTrue(store.state.value is ToolchainState.Failed)
        assertTrue(
            (store.state.value as ToolchainState.Failed).message
                .contains("Accept the Android SDK License"),
        )
        assertFalse(store.androidSdkLicenseAccepted())
        store.setAndroidSdkLicenseAccepted(true)
        assertTrue(store.androidSdkLicenseAccepted())
    }

    @Test
    fun downloadsRefuseToStartBelowTheFreeSpaceThreshold() = runBlocking {
        val store = ToolchainStore(
            dir = File(temporaryFolder.root, "toolchain-space"),
            kv = InMemoryKeyValueStore(),
            components = emptyList(),
            minimumFreeBytes = Long.MAX_VALUE,
        )

        store.ensureAll()

        assertTrue(store.state.value is ToolchainState.Failed)
        assertTrue(
            (store.state.value as ToolchainState.Failed).message.contains("1.5 GB"),
        )
    }

    @Test
    fun aVerifiedPlatformArchiveIsSafelyExtractedForTheCompiler() = runBlocking {
        val directory = File(temporaryFolder.root, "toolchain-platform").apply { mkdirs() }
        val archive = File(directory, "platform.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("android-35/android.jar"))
            zip.write("android-api".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("android-35/data/res/values/strings.xml"))
            zip.write("<resources/>".toByteArray())
            zip.closeEntry()
        }
        val component = platformComponent(archive, unpack = true)
        val store = ToolchainStore(
            dir = directory,
            kv = InMemoryKeyValueStore(),
            components = listOf(component),
            minimumFreeBytes = 0,
        )

        store.ensureAll()

        assertTrue(store.state.value is ToolchainState.DownloadedNotReady)
        assertEquals(
            "android-api",
            File(directory, "platform-35/android.jar").readText(),
        )
        assertTrue(
            File(directory, "platform-35/data/res/values/strings.xml").isFile,
        )
        assertTrue(File(directory, "platform-35/.closecode-platform-ready").isFile)
    }

    @Test
    fun platformArchiveCannotWriteOutsideItsInstallDirectory() = runBlocking {
        val directory = File(temporaryFolder.root, "toolchain-unsafe-platform").apply { mkdirs() }
        val archive = File(directory, "platform.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("android-35/../../outside.txt"))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }
        val store = ToolchainStore(
            dir = directory,
            kv = InMemoryKeyValueStore(),
            components = listOf(platformComponent(archive, unpack = true)),
            minimumFreeBytes = 0,
        )

        store.ensureAll()

        assertTrue(store.state.value is ToolchainState.Failed)
        assertFalse(File(temporaryFolder.root, "outside.txt").exists())
    }

    @Test
    fun downloaderResumesPartialFileAndVerifiesItsSha256() = runBlocking {
        val payload = ByteArray(32_768) { (it % 251).toByte() }
        val resumeAt = 4096
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        val server = ServerSocket(0)
        val failure = AtomicReference<Throwable?>()
        val requestLine = AtomicReference<String?>()
        val serverThread = thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = BufferedReader(
                        InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII),
                    )
                    requestLine.set(reader.readLine())
                    var range: String? = null
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Range:", ignoreCase = true)) {
                            range = header.substringAfter(':').trim()
                        }
                    }
                    check(range == "bytes=$resumeAt-")
                    val body = payload.copyOfRange(resumeAt, payload.size)
                    val headers = "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Content-Range: bytes $resumeAt-${payload.lastIndex}/${payload.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(headers.toByteArray(StandardCharsets.US_ASCII))
                        write(body)
                        flush()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                server.close()
            }
        }
        val directory = File(temporaryFolder.root, "toolchain")
        directory.mkdirs()
        File(directory, "test.jar.part").writeBytes(payload.copyOfRange(0, resumeAt))
        val component = ToolchainComponent(
            id = "test",
            fileName = "test.jar",
            version = "1",
            sizeBytes = payload.size.toLong(),
            url = "http://127.0.0.1:${server.localPort}/artifact",
            sha256 = digest,
            license = "Test",
        )
        val store = ToolchainStore(
            dir = directory,
            kv = InMemoryKeyValueStore(),
            components = listOf(component),
            minimumFreeBytes = 0,
        )

        store.ensureAll()
        serverThread.join(5_000)

        assertTrue(requestLine.get()?.startsWith("GET /artifact HTTP/1.1") == true)
        assertEquals(null, failure.get())
        assertEquals(payload.toList(), File(directory, "test.jar").readBytes().toList())
        assertFalse(File(directory, "test.jar.part").exists())
        assertTrue(store.state.value is ToolchainState.DownloadedNotReady)
    }

    @Test
    fun legalForeignPackageWarnsWithoutBlocking() = runBlocking {
        val project = projectWithPackage("com.example.biz")
        val result = BuildEngine(newStore()).build(project)

        assertTrue(result is BuildOutcome.NeedsSetup)
        val warnings = (result as BuildOutcome.NeedsSetup).warnings.map { it.message }
        assertTrue(warnings.any { it == "This app will install as com.example.biz." })
    }

    @Test
    fun invalidManifestPackageFailsTheContract() = runBlocking {
        val project = projectWithPackage("com..bad")

        val result = BuildEngine(newStore()).build(project)

        assertTrue(result is BuildOutcome.ContractFailed)
        val violation = (result as BuildOutcome.ContractFailed).violations.single()
        assertEquals("app/src/main/AndroidManifest.xml", violation.path)
        assertTrue(violation.message.contains("Each package part"))
    }

    @Test
    fun packageCollisionWarnsThatInstallingWouldReplaceTheOtherProject() = runBlocking {
        val project = projectWithPackage("com.example.shared")
        val other = File(temporaryFolder.root, "other-project").apply { mkdirs() }
        File(other, ".closecode.json").writeText("{}")
        val otherManifest = File(other, "app/src/main/AndroidManifest.xml")
        checkNotNull(otherManifest.parentFile).mkdirs()
        otherManifest.writeText("""<manifest package="com.example.shared" />""")

        val result = BuildEngine(newStore()).build(project)

        assertTrue(result is BuildOutcome.NeedsSetup)
        val warnings = (result as BuildOutcome.NeedsSetup).warnings.map { it.message }
        assertTrue(warnings.any { it.contains("Installing this app would replace it.") })
    }

    @Test
    fun javaKeywordsCannotBePackageParts() {
        assertTrue(TemplateContract.packageError("com.class.app")!!.contains("reserved Java"))
        assertTrue(TemplateContract.packageError("com.new.app")!!.contains("reserved Java"))
    }

    @Test
    fun packagePartsMustBeValidAndroidIdentifiers() {
        assertTrue(TemplateContract.packageError("com..bad") != null)
        assertTrue(TemplateContract.packageError("9com.example") != null)
        assertTrue(TemplateContract.packageError("com.example") == null)
    }

    private fun newStore() = ToolchainStore(
        File(temporaryFolder.root, "toolchain"),
        InMemoryKeyValueStore(),
    )

    private fun platformComponent(archive: File, unpack: Boolean) = ToolchainComponent(
        id = "android-platform-test",
        fileName = archive.name,
        version = "35 test",
        sizeBytes = archive.length(),
        url = "https://example.invalid/platform.zip",
        sha256 = MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes())
            .joinToString("") { "%02x".format(it) },
        license = "Test",
        unpackAndroidPlatform = unpack,
    )

    private fun projectWithPackage(packageName: String): File {
        val project = temporaryFolder.newFolder("project-$packageName".replace('.', '-'))
        val manifest = File(project, "app/src/main/AndroidManifest.xml")
        checkNotNull(manifest.parentFile).mkdirs()
        manifest.writeText("""<manifest package="$packageName" />""")
        return project
    }

    private fun writeGradleFiles(project: File, appGradle: String) {
        File(project, "build.gradle.kts").writeText(
            """
                plugins {
                    id("com.android.application") version "8.12.0" apply false
                    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
                }
            """.trimIndent(),
        )
        val app = File(project, "app/build.gradle.kts")
        checkNotNull(app.parentFile).mkdirs()
        app.writeText(appGradle)
    }
}
