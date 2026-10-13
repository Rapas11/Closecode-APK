package com.closecode.build

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.closecode.ui.model.packageNameFromProject
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.model.packageNameError
import com.closecode.ui.project.ProjectRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ToolchainComponent(
    val id: String,
    val fileName: String,
    val version: String,
    val sizeBytes: Long,
    val url: String,
    val sha256: String,
    val license: String,
    val unpackAndroidPlatform: Boolean = false,
)

object ToolchainManifest {
    val components = listOf(
        ToolchainComponent(
            "kotlin-compiler",
            "kotlin-compiler-embeddable-2.4.0.jar",
            "2.4.0",
            60_351_320L,
            "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/" +
                "kotlin-compiler-embeddable/2.4.0/kotlin-compiler-embeddable-2.4.0.jar",
            "c2b25e8c1c93ec416ba4327f5e31eaec0f0c8847241b9353e294b8db9dce564f",
            "Apache-2.0",
        ),
        ToolchainComponent(
            "compose-compiler",
            "kotlin-compose-compiler-plugin-embeddable-2.4.0.jar",
            "2.4.0",
            949_087L,
            "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/" +
                "kotlin-compose-compiler-plugin-embeddable/2.4.0/" +
                "kotlin-compose-compiler-plugin-embeddable-2.4.0.jar",
            "95ccd5c4008e454a5cb26f89405613f2ae33f46bf950c8be4d01bbdbdcfbcd08",
            "Apache-2.0",
        ),
        ToolchainComponent(
            "ecj",
            "ecj-3.38.0.jar",
            "3.38.0",
            3_285_717L,
            "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar",
            "97c566b120009c203a2fc8b291f4a9adbc171cf1ccb70f06f6b4e1828c00ce8e",
            "EPL-2.0",
        ),
        ToolchainComponent(
            "r8",
            "r8-8.13.25.jar",
            "8.13.25",
            18_273_598L,
            "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.13.25/" +
                "r8-8.13.25.jar",
            "0d4f2f68c4fcbc9434eff122f41feb6fb1d98efd2faa530cec1abacab1cea03d",
            "BSD-3-Clause",
        ),
        ToolchainComponent(
            "apksig",
            "apksig-8.13.0.jar",
            "8.13.0",
            506_969L,
            "https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/" +
                "8.13.0/apksig-8.13.0.jar",
            "c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89",
            "Apache-2.0",
        ),
        ToolchainComponent(
            "android-platform",
            "platform-35_r02.zip",
            "35 r02",
            64_273_788L,
            "https://dl.google.com/android/repository/platform-35_r02.zip",
            "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0",
            "Android SDK License",
            unpackAndroidPlatform = true,
        ),
    )
    val totalBytes: Long = components.sumOf { it.sizeBytes }
    const val MINIMUM_FREE_BYTES = 1_610_612_736L
}

sealed class ToolchainState {
    object NotStarted : ToolchainState()
    data class Downloading(val current: String, val doneBytes: Long) : ToolchainState()
    data class Verifying(val fileName: String) : ToolchainState()
    data class Extracting(val fileName: String) : ToolchainState()
    data class DownloadedNotReady(val message: String) : ToolchainState()
    object Ready : ToolchainState()
    data class Failed(val message: String) : ToolchainState()
}

class ToolchainStore(
    val dir: File,
    val kv: KeyValueStore,
    private val connectivityManager: ConnectivityManager? = null,
    private val components: List<ToolchainComponent> = ToolchainManifest.components,
    private val minimumFreeBytes: Long = ToolchainManifest.MINIMUM_FREE_BYTES,
) {
    private val _state = MutableStateFlow<ToolchainState>(ToolchainState.NotStarted)
    val state: StateFlow<ToolchainState> = _state

    init {
        dir.mkdirs()
    }

    /** The raw compiler jars still need an Android DEX runtime pack and arm64 aapt2 runner. */
    fun isReady(): Boolean = false

    fun androidSdkLicenseAccepted(): Boolean =
        kv.getBoolean("android_sdk_license_accepted", false)

    fun setAndroidSdkLicenseAccepted(accepted: Boolean) {
        kv.putBoolean("android_sdk_license_accepted", accepted)
    }

    suspend fun ensureAll(allowMobileData: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (components.any { it.license == "Android SDK License" } &&
                !androidSdkLicenseAccepted()
            ) {
                throw IOException("Accept the Android SDK License before downloading Platform 35.")
            }
            checkNetwork(allowMobileData)
            if (dir.usableSpace < minimumFreeBytes) {
                throw IOException("Free at least 1.5 GB of storage before downloading build tools.")
            }
            dir.mkdirs()
            var completedBytes = 0L
            for (component in components) {
                currentCoroutineContext().ensureActive()
                val target = File(dir, component.fileName)
                if (verify(target, component.sha256, component.sizeBytes)) {
                    completedBytes += component.sizeBytes
                    continue
                }
                target.delete()
                download(component, target) { downloadedBytes ->
                    _state.value = ToolchainState.Downloading(
                        component.fileName,
                        completedBytes + downloadedBytes,
                    )
                }
                completedBytes += component.sizeBytes
            }
            components.filter(ToolchainComponent::unpackAndroidPlatform).forEach { component ->
                currentCoroutineContext().ensureActive()
                _state.value = ToolchainState.Extracting(component.fileName)
                extractAndroidPlatform(File(dir, component.fileName))
            }
            _state.value = ToolchainState.DownloadedNotReady(
                "Compiler files downloaded and verified. They are not yet usable on Android: " +
                    "the DEX runtime pack and arm64 aapt2 are still missing. No APK was created.",
            )
        } catch (error: CancellationException) {
            _state.value = ToolchainState.Failed("Download cancelled. Partial files are kept to resume.")
            throw error
        } catch (error: IOException) {
            _state.value = ToolchainState.Failed(
                error.message ?: "Could not download the build tool files.",
            )
        }
    }

    private fun checkNetwork(allowMobileData: Boolean) {
        val manager = connectivityManager ?: return
        val network = manager.activeNetwork
            ?: throw IOException("Connect to Wi-Fi before downloading build tools.")
        val capabilities = manager.getNetworkCapabilities(network)
            ?: throw IOException("Connect to Wi-Fi before downloading build tools.")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
        if (allowMobileData && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return
        throw IOException(
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                "Build-tool downloads use Wi-Fi by default. Turn on mobile data downloads to continue."
            } else {
                "Connect to Wi-Fi before downloading build tools."
            },
        )
    }

    private suspend fun download(
        component: ToolchainComponent,
        target: File,
        onProgress: (Long) -> Unit,
    ) {
        val artifactUrl = URL(component.url)
        if (artifactUrl.protocol != "https" &&
            !(artifactUrl.protocol == "http" && artifactUrl.host == "127.0.0.1")
        ) {
            throw IOException("Build tools must be downloaded over HTTPS.")
        }
        val partial = File(dir, "${component.fileName}.part")
        var offset = partial.takeIf(File::isFile)?.length() ?: 0L
        if (offset > component.sizeBytes) {
            partial.delete()
            offset = 0L
        }
        if (offset == component.sizeBytes && verify(partial, component.sha256, component.sizeBytes)) {
            if (!partial.renameTo(target)) throw IOException("Could not save ${component.fileName}.")
            return
        }
        if (offset == component.sizeBytes) {
            partial.delete()
            offset = 0L
        }

        val connection = artifactUrl.openConnection() as? HttpURLConnection
            ?: throw IOException("The build-tool address is not an HTTP download.")
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            if (connection.url.protocol != "https" &&
                !(connection.url.protocol == "http" && connection.url.host == "127.0.0.1")
            ) {
                throw IOException("The server redirected ${component.fileName} away from HTTPS.")
            }
            if (offset > 0L && status == HttpURLConnection.HTTP_OK) {
                partial.delete()
                offset = 0L
            } else if ((offset == 0L && status != HttpURLConnection.HTTP_OK) ||
                (offset > 0L && status != HttpURLConnection.HTTP_PARTIAL)
            ) {
                throw IOException("The server could not resume ${component.fileName} (HTTP $status).")
            }
            if (offset > 0L && !connection.getHeaderField("Content-Range")
                    .orEmpty().startsWith("bytes $offset-")
            ) {
                throw IOException("The server returned an invalid resume range for ${component.fileName}.")
            }

            var received = offset
            connection.inputStream.use { input ->
                FileOutputStream(partial, offset > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > component.sizeBytes) {
                            throw IOException("${component.fileName} was larger than its pinned size.")
                        }
                        output.write(buffer, 0, count)
                        onProgress(received)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        _state.value = ToolchainState.Verifying(component.fileName)
        if (partial.length() < component.sizeBytes) {
            throw IOException(
                "The download of ${component.fileName} ended early. Retry to resume it.",
            )
        }
        if (!verify(partial, component.sha256, component.sizeBytes)) {
            partial.delete()
            throw IOException(
                "The downloaded ${component.fileName} did not match its SHA-256 checksum. Try again.",
            )
        }
        if (!partial.renameTo(target)) throw IOException("Could not save ${component.fileName}.")
    }

    private fun verify(file: File, expectedHash: String, expectedSize: Long): Boolean {
        if (!file.isFile || file.length() != expectedSize) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedHash, ignoreCase = true)
    }

    private suspend fun extractAndroidPlatform(archive: File) {
        val installed = File(dir, "platform-35")
        if (File(installed, ".closecode-platform-ready").isFile &&
            File(installed, "android.jar").isFile
        ) return

        val staging = File(dir, "platform-35.extracting")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Could not prepare the Android Platform 35 files.")
        val stagingRoot = staging.canonicalFile
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                if (entry.name == "android-35/") continue
                if (!entry.name.startsWith("android-35/")) {
                    throw IOException("The Platform 35 archive contained an unexpected path.")
                }
                val relativePath = entry.name.removePrefix("android-35/")
                if (relativePath.split('/').any { it == ".." } || relativePath.isBlank()) {
                    throw IOException("The Platform 35 archive contained an unsafe path.")
                }
                val output = File(stagingRoot, relativePath).canonicalFile
                if (!output.toPath().startsWith(stagingRoot.toPath())) {
                    throw IOException("The Platform 35 archive contained an unsafe path.")
                }
                if (entry.isDirectory) {
                    if (!output.isDirectory && !output.mkdirs()) {
                        throw IOException("Could not create a Platform 35 folder.")
                    }
                } else {
                    checkNotNull(output.parentFile).mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(output).use { destination ->
                            input.copyTo(destination)
                            destination.fd.sync()
                        }
                    }
                }
            }
        }
        if (!File(staging, "android.jar").isFile || File(staging, "android.jar").length() == 0L) {
            staging.deleteRecursively()
            throw IOException("The Platform 35 archive did not contain android.jar.")
        }
        File(staging, ".closecode-platform-ready").writeText("35")
        if (installed.exists() && !installed.deleteRecursively()) {
            staging.deleteRecursively()
            throw IOException("Could not replace the existing Android Platform 35 files.")
        }
        if (!staging.renameTo(installed)) {
            staging.deleteRecursively()
            throw IOException("Could not install the extracted Android Platform 35 files.")
        }
    }
}

sealed class InstallState {
    object Idle : InstallState()
    data class Installing(val sessionId: Int) : InstallState()
    data class Installed(val packageName: String) : InstallState()
    object NeedsPermission : InstallState()
    data class Failed(val message: String) : InstallState()
}

sealed class InstallRequest {
    data class Started(val sessionId: Int) : InstallRequest()
    object NeedsPermission : InstallRequest()
    data class Failed(val message: String) : InstallRequest()
}

sealed class InstallEvent {
    data class SessionOpened(val sessionId: Int) : InstallEvent()
    data class SystemStatus(val status: Int, val packageName: String?, val message: String?) : InstallEvent()
}

fun nextInstallState(current: InstallState, event: InstallEvent): InstallState {
    return when (event) {
        is InstallEvent.SessionOpened -> InstallState.Installing(event.sessionId)
        is InstallEvent.SystemStatus -> {
            when (event.status) {
                PackageInstaller.STATUS_SUCCESS -> event.packageName
                    ?.takeIf(String::isNotBlank)
                    ?.let(InstallState::Installed)
                    ?: InstallState.Failed("Android reported success but did not identify the installed app.")
                PackageInstaller.STATUS_PENDING_USER_ACTION -> current
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Failed("Installation was cancelled.")
                PackageInstaller.STATUS_FAILURE_CONFLICT ->
                    InstallState.Failed("A different app version is installed. Uninstall it first, then try again.")
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    InstallState.Failed("This app is not compatible with your phone.")
                PackageInstaller.STATUS_FAILURE_INVALID ->
                    InstallState.Failed("Android could not read this APK. Build it again and retry.")
                PackageInstaller.STATUS_FAILURE_STORAGE ->
                    InstallState.Failed("Your phone does not have enough free storage to install this app.")
                PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    InstallState.Failed("Android blocked the install. Allow CloseCode to install apps, then retry.")
                else -> InstallState.Failed(
                    event.message?.takeIf(String::isNotBlank)
                        ?: "Android could not install the app (status ${event.status}).",
                )
            }
        }
    }
}

object TemplateContract {
    fun manifestPackage(manifestXml: String): String? {
        val match = Regex("""package\s*=\s*["']([^"']+)["']""").find(manifestXml)
        return match?.groupValues?.get(1)
    }

    fun packageError(packageName: String): String? = packageNameError(packageName)

    fun foreignPackageWarning(packageName: String): String? =
        if (packageName == "com.closecode" || packageName.startsWith("com.closecode.")) {
            null
        } else {
            "This app will install as $packageName."
        }

    fun packageForProject(projectDir: File): String? {
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        val gradle = sequenceOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle"),
        ).firstOrNull { it.isFile }
        val manifestText = if (manifest.isFile) manifest.readText() else ""
        val gradleText = gradle?.readText().orEmpty()
        val declared = packageNameFromProject(manifestText, gradleText)
        if (declared != null) return declared
        if (!manifest.isFile && gradle == null) return null

        val metadata = File(projectDir, ".closecode.json")
        if (!metadata.isFile) return null
        val record = ProjectRecord.fromJson(projectDir.name, metadata.readText())
        return record?.config?.packageName
    }

    fun packageForSlug(slug: String): String {
        val clean = slug.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
        return if (clean.startsWith("test")) {
            "com.closecode.testing"
        } else {
            "com.closecode.${if (clean.isEmpty()) "blankapp" else clean}"
        }
    }
}

object InstallHelper {
    const val ACTION_INSTALL_STATUS = "com.closecode.INSTALL_STATUS"

    fun isInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    fun launchIntent(context: Context, packageName: String): Intent? {
        if (packageName.isBlank()) return null
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchApp(context: Context, packageName: String): Result<Unit> = runCatching {
        require(packageName.isNotBlank()) { "The app package name is missing." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sender = context.packageManager.getLaunchIntentSenderForPackage(packageName)
            context.startIntentSender(
                sender,
                null,
                Intent.FLAG_ACTIVITY_NEW_TASK,
                0,
                0,
            )
        } else {
            val launch = launchIntent(context, packageName)
                ?: throw IllegalStateException("Android could not find an installed app for $packageName.")
            context.startActivity(launch)
        }
    }

    fun install(context: Context, apkFile: File): InstallRequest {
        if (!apkFile.isFile || apkFile.length() == 0L) {
            return InstallRequest.Failed("The built APK is missing or empty. Build the app again.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                return try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    InstallRequest.NeedsPermission
                } catch (error: Exception) {
                    InstallRequest.Failed(
                        "Could not open install permission settings: " +
                            (error.message ?: error::class.simpleName.orEmpty()),
                    )
                }
            }
        }

        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                apkFile.inputStream().use { input ->
                    session.openWrite("app.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val statusIntent = Intent(ACTION_INSTALL_STATUS).apply {
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )

                session.commit(pendingIntent.intentSender)
                InstallRequest.Started(sessionId)
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            InstallRequest.Failed(
                "Could not start the Android installer: " +
                    (e.message ?: e::class.simpleName.orEmpty()),
            )
        }
    }
}

data class ContractViolation(val path: String, val message: String)
data class BuildWarning(val message: String)
data class CompileDiagnostic(val path: String, val line: Int, val message: String)

sealed class BuildOutcome {
    data class Success(val apkFile: File) : BuildOutcome()
    data class ContractFailed(val violations: List<ContractViolation>) : BuildOutcome()
    data class NeedsSetup(val warnings: List<BuildWarning>) : BuildOutcome()
    data class CompileFailed(val diagnostics: List<CompileDiagnostic>) : BuildOutcome()
}

class BuildEngine(
    val store: ToolchainStore,
) {
    suspend fun build(projectDir: File): BuildOutcome = withContext(Dispatchers.IO) {
        if (!projectDir.isDirectory) {
            return@withContext BuildOutcome.ContractFailed(
                listOf(ContractViolation(projectDir.path, "The project folder was not found.")),
            )
        }
        val packageName = try {
            TemplateContract.packageForProject(projectDir)
        } catch (e: IOException) {
            return@withContext BuildOutcome.ContractFailed(
                listOf(
                    ContractViolation(
                        ".closecode.json",
                        "Could not read the saved app package: ${e.message ?: "file access failed"}.",
                    ),
                ),
            )
        }
        if (packageName != null) {
            val packageError = TemplateContract.packageError(packageName)
            if (packageError != null) {
                return@withContext BuildOutcome.ContractFailed(
                    listOf(ContractViolation("app/src/main/AndroidManifest.xml", packageError)),
                )
            }
        }
        val packageWarning = packageName?.let(TemplateContract::foreignPackageWarning)
            ?.let(::BuildWarning)
        val collisionWarning = packageName?.let { findPackageCollision(projectDir, it) }
        if (!store.isReady()) {
            return@withContext BuildOutcome.NeedsSetup(
                listOfNotNull(
                    packageWarning,
                    collisionWarning,
                    BuildWarning(
                        "The on-device build tools are not installed. No APK was created.",
                    ),
                ),
            )
        }
        BuildOutcome.NeedsSetup(
            listOfNotNull(
                packageWarning,
                collisionWarning,
                BuildWarning("The on-device build engine is unavailable. No APK was created."),
            ),
        )
    }

    private fun findPackageCollision(projectDir: File, packageName: String): BuildWarning? {
        val siblings = projectDir.parentFile?.listFiles()
            ?: return BuildWarning("Could not check whether another project uses $packageName.")
        for (candidate in siblings) {
            if (!candidate.isDirectory || candidate == projectDir) continue
            if (!File(candidate, ".closecode.json").isFile) continue
            val manifest = File(candidate, "app/src/main/AndroidManifest.xml")
            val appBuild = File(candidate, "app/build.gradle.kts")
            val groovyBuild = File(candidate, "app/build.gradle")
            if (!manifest.isFile && !appBuild.isFile && !groovyBuild.isFile) continue
            if (listOf(manifest, appBuild, groovyBuild).any { it.isFile && !it.canRead() }) {
                return BuildWarning("Could not check package conflicts because ${candidate.name} is unreadable.")
            }
            val candidatePackage = try {
                TemplateContract.packageForProject(candidate)
            } catch (e: IOException) {
                return BuildWarning("Could not check package conflicts: ${e.message ?: "manifest read failed"}.")
            }
            if (candidatePackage == packageName) {
                return BuildWarning(
                    "Another project (${candidate.name}) uses $packageName. Installing this app would replace it.",
                )
            }
        }
        return null
    }
}
