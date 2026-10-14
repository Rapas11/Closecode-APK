import java.util.zip.ZipFile

val closecodeCompileSdk = 35

val closecodeSourceRevision = runCatching {
    val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0 && output.isNotBlank()) output else "unknown"
}.getOrDefault("unknown")
val closecodeSourceDirty = runCatching {
    val process = ProcessBuilder("git", "status", "--porcelain")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    process.waitFor() == 0 && output.isNotBlank()
}.getOrDefault(false)
val closecodeSourceLabel = closecodeSourceRevision +
    if (closecodeSourceDirty) "-dirty" else ""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.closecode"
    compileSdk = closecodeCompileSdk
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.closecode"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"
        buildConfigField("String", "SOURCE_REVISION", "\"$closecodeSourceLabel\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
            applicationIdSuffix = ".alt"
            versionNameSuffix = "-alt"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // One back stack for the top-level screens (list, recent, settings, IDE). Inner panels
    // stay explicit state with an ordered BackHandler chain — they share one composition and
    // its state, which a route per panel would destroy and rebuild on every switch.
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Version control. JGit is a pure-Java implementation, so the repository lives in the app's
    // own files directory with no git binary to ship or to sandbox. JavaEWAH, slf4j-api and
    // commons-codec arrive transitively from its POM.
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")
    // GitHub's Device Flow and REST responses are JSON. Parsed with JsonParser rather than
    // with mapped classes so nothing on this path depends on reflection.
    implementation("com.google.code.gson:gson:2.11.0")
    // On-device APK signing: apksig is a zero-dependency library (+0.5 MB) driven through
    // ApkSigner.Builder with the AndroidKeyStore debug key. Proven with 8.13.0.
    implementation("com.android.tools.build:apksig:8.13.0")
    // The GitHub token lives in EncryptedSharedPreferences (Keystore-backed), and the
    // device-flow step opens github.com/login/device in a Chrome Custom Tab.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.browser:browser:1.8.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
// ---------------------------------------------------------------------------
// Android API index
//
// Parses public members out of android.jar at build time and writes a compact
// text index to assets/android-api.txt. Shipping an index instead of the jar
// keeps the APK small: android.jar is ~90 MB, the index is ~1 MB.
// ---------------------------------------------------------------------------

val androidApiOutput = layout.buildDirectory.dir("generated/androidApi")
val localProps = rootProject.file("local.properties")
val sdkDirPath: String? = if (localProps.isFile) {
    localProps.readLines()
        .firstOrNull { it.trim().startsWith("sdk.dir") }
        ?.substringAfter("=")
        ?.trim()
        ?.ifEmpty { null }
} else null

val resolvedSdkDir: String? = sdkDirPath
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

val androidPlatformJar: File? = resolvedSdkDir?.let {
    file("$it/platforms/android-$closecodeCompileSdk/android.jar")
}

// Set -PclosecodeAllowMissingAndroidJar=true to build without an API index (Android
// member completion is then unavailable, but everything else works).
val allowMissingAndroidJar = providers.gradleProperty("closecodeAllowMissingAndroidJar")
    .map { it.toBoolean() }
    .getOrElse(false)

val generateAndroidApiIndex by tasks.registering {
    group = "build"
    description = "Generates assets/android-api.txt from android.jar"
    androidPlatformJar?.let { inputs.file(it).withPropertyName("androidJar") }
    val outputFile = androidApiOutput.map { it.file("assets/android-api.txt") }
    outputs.file(outputFile)

    doLast {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val jar = androidPlatformJar
        if (jar == null || !jar.isFile) {
            if (!allowMissingAndroidJar) {
                throw GradleException(
                    """
                    |Could not find android.jar for the Android API index.
                    |
                    |Looked for: ${jar?.absolutePath ?: "<sdk.dir not set>"}
                    |  sdk.dir in local.properties : ${sdkDirPath ?: "<not set>"}
                    |  ANDROID_HOME              : ${System.getenv("ANDROID_HOME") ?: "<not set>"}
                    |  ANDROID_SDK_ROOT          : ${System.getenv("ANDROID_SDK_ROOT") ?: "<not set>"}
                    |
                    |Install the platform, or set sdk.dir in local.properties, or build with
                    |  -PclosecodeAllowMissingAndroidJar=true
                    |to skip the index (Android member completion will not work).
                    """.trimMargin(),
                )
            }
            out.writeText("# android api index unavailable: android.jar not found\n")
            logger.lifecycle("generateAndroidApiIndex: android.jar not found, wrote empty index")
            return@doLast
        }

        val lines = ArrayList<String>()
        var classCount = 0
        var memberCount = 0

        ZipFile(jar).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val entryName = entry.name
                if (!entryName.endsWith(".class")) continue
                val internalName = entryName.removeSuffix(".class")
                if (!INCLUDED_PACKAGES.any { internalName.startsWith(it) }) continue
                if ('$' in internalName) continue
                val fqn = internalName.replace('/', '.')
                val simpleName = fqn.substringAfterLast('.')
                if (simpleName == "R" || simpleName == "BuildConfig" || simpleName == "Manifest") continue

                val bytes = zf.getInputStream(entry).use { it.readBytes() }
                val parsed = parseClassFile(bytes) ?: continue
                if (parsed.access and ACC_PUBLIC == 0) continue

                lines.add("C|$fqn|$simpleName|${parsed.superName?.replace('/', '.') ?: ""}")
                classCount++

                val seen = HashSet<String>()
                for (m in parsed.methods) {
                    if (m.access and ACC_PUBLIC == 0) continue
                    if (m.access and (ACC_SYNTHETIC or ACC_BRIDGE) != 0) continue
                    if (m.name == "<init>" || m.name == "<clinit>") continue
                    if ('$' in m.name || '<' in m.name || m.name.startsWith("access$")) continue
                    val sig = methodSignature(m.descriptor) ?: continue
                    if (!seen.add(m.name)) continue
                    lines.add("M|$fqn|${m.name}|${sig.arity}|${sig.ret}|${sig.params.joinToString(";")}")
                    memberCount++
                }
                for (f in parsed.fields) {
                    if (f.access and ACC_PUBLIC == 0) continue
                    if (f.access and ACC_SYNTHETIC != 0) continue
                    if ('$' in f.name || '<' in f.name) continue
                    if (!seen.add(f.name)) continue
                    val type = fieldTypeName(f.descriptor) ?: continue
                    lines.add("F|$fqn|${f.name}|$type")
                    memberCount++
                }
            }
        }

        out.writeText(lines.joinToString("\n", "# closecode android api index\n"))
        logger.lifecycle(
            "generateAndroidApiIndex: $classCount classes, $memberCount members, " +
                "%.2f MB".format(out.length() / 1048576.0),
        )
    }
}

android.sourceSets.getByName("main") {
    assets.srcDir(androidApiOutput.map { it.dir("assets") })
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(generateAndroidApiIndex)
}

val ACC_PUBLIC = 0x0001
val ACC_SYNTHETIC = 0x1000
val ACC_BRIDGE = 0x0040

val INCLUDED_PACKAGES = listOf(
    "android/app/", "android/os/", "android/view/", "android/widget/", "android/content/",
    "android/util/", "android/text/", "android/graphics/", "android/database/",
    "android/net/", "android/provider/", "android/animation/", "android/preference/",
    "android/print/", "android/webkit/", "android/nfc/", "android/accounts/",
    "java/lang/", "java/util/", "java/io/", "java/math/", "java/net/", "java/text/",
    "java/time/", "kotlin/",
)

data class ParsedMember(val access: Int, val name: String, val descriptor: String)

data class ParsedClass(
    val access: Int,
    val superName: String?,
    val fields: List<ParsedMember>,
    val methods: List<ParsedMember>,
)

data class MethodSignature(val arity: Int, val ret: String, val params: List<String>)

fun parseClassFile(b: ByteArray): ParsedClass? {
    var p = 0
    fun u1(): Int {
        val v = b[p].toInt() and 0xff
        p++
        return v
    }

    fun u2(): Int = (u1() shl 8) or u1()

    fun u4(): Long = (u2().toLong() shl 16) or u2().toLong()

    fun skip(count: Int) {
        p += count
    }

    if (u4() != 0xCAFEBABEL) return null
    skip(4)
    val cpCount = u2()
    val utf8 = arrayOfNulls<String>(cpCount)
    val classRefs = IntArray(cpCount)
    var i = 1
    while (i < cpCount) {
        when (u1()) {
            1 -> {
                val len = u2()
                utf8[i] = String(b, p, len, Charsets.UTF_8)
                skip(len)
            }

            7, 8, 16, 19, 20 -> classRefs[i] = u2()
            15 -> {
                u1()
                u2()
            }

            3, 4, 9, 10, 11, 12, 17, 18 -> skip(4)
            5, 6 -> {
                skip(8)
                i++
            }

            else -> return null
        }
        i++
    }

    val access = u2()
    u2()
    val superIdx = u2()
    skip(u2() * 2)

    fun readMembers(): List<ParsedMember> {
        val count = u2()
        val out = ArrayList<ParsedMember>(count)
        repeat(count) {
            val mAccess = u2()
            val nameIdx = u2()
            val descIdx = u2()
            repeat(u2()) {
                u2()
                skip(u4().toInt())
            }
            out.add(ParsedMember(mAccess, utf8[nameIdx] ?: "", utf8[descIdx] ?: ""))
        }
        return out
    }

    val fields = readMembers()
    val methods = readMembers()
    val superName = if (superIdx == 0) null else utf8[classRefs[superIdx]]
    return ParsedClass(access, superName, fields, methods)
}

fun readTypeToken(desc: String, i: Int): Pair<String, Int>? {
    if (i >= desc.length) return null
    return when (val c = desc[i]) {
        'B' -> "byte" to (i + 1)
        'C' -> "char" to (i + 1)
        'D' -> "double" to (i + 1)
        'F' -> "float" to (i + 1)
        'I' -> "int" to (i + 1)
        'J' -> "long" to (i + 1)
        'S' -> "short" to (i + 1)
        'Z' -> "boolean" to (i + 1)
        'V' -> "void" to (i + 1)
        'L' -> {
            val end = desc.indexOf(';', i)
            if (end < 0) null else desc.substring(i + 1, end) to (end + 1)
        }

        '[' -> readTypeToken(desc, i + 1)?.let { (t, n) -> "$t[]" to n }
        else -> null
    }
}

fun methodSignature(desc: String): MethodSignature? {
    if (!desc.startsWith("(")) return null
    var i = 1
    val params = ArrayList<String>()
    while (i < desc.length && desc[i] != ')') {
        val step = readTypeToken(desc, i) ?: return null
        params.add(displayTypeName(step.first))
        i = step.second
    }
    if (i >= desc.length) return null
    val ret = readTypeToken(desc, i + 1) ?: return null
    return MethodSignature(params.size, displayTypeName(ret.first), params)
}

fun fieldTypeName(desc: String): String? = readTypeToken(desc, 0)?.let { displayTypeName(it.first) }

fun displayTypeName(jvmType: String): String {
    if (jvmType.endsWith("[]")) return displayTypeName(jvmType.removeSuffix("[]")) + "[]"
    val base = jvmType.substringBefore('[')
    return when (base) {
        "int" -> "Int"
        "long" -> "Long"
        "short" -> "Short"
        "byte" -> "Byte"
        "char" -> "Char"
        "boolean" -> "Boolean"
        "float" -> "Float"
        "double" -> "Double"
        "void" -> "Unit"
        else -> {
            val simple = base.replace('/', '.').substringAfterLast('.').substringBefore('$')
            when (simple) {
                "Integer" -> "Int"
                "Character" -> "Char"
                "Boolean" -> "Boolean"
                else -> simple.ifEmpty { "Any" }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(generateAndroidApiIndex)
    val generatedIndex = androidApiOutput.map { it.file("assets/android-api.txt") }
    inputs.file(generatedIndex)
    doFirst {
        systemProperty("closecode.androidApiIndex", generatedIndex.get().asFile.absolutePath)
        // Milestone (a) proof: toolchain jars, aapt2 and android.jar reach the workers
        // from the environment, so the proof runs where they exist and skips elsewhere.
        for (key in listOf("toolchain.dir", "stdlib.jar", "aapt2.bin", "android.jar")) {
            System.getenv(key.replace('.', '_').uppercase())?.let { systemProperty(key, it) }
        }
    }
}
