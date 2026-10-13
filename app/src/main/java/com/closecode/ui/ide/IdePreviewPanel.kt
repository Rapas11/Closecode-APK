package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.lang.Severity
import com.closecode.ui.project.PreviewServer
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Extensions Preview renders as a bitmap rather than as text. */
private val ImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/**
 * The conventional two-tone board behind a decoded bitmap, so a transparent PNG reads as
 * transparent instead of taking on whatever the page background happens to be.
 */
private fun DrawScope.checkerboard(even: Color, odd: Color) {
    val cell = 14.dp.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = 0
        var x = 0f
        while (x < size.width) {
            val square = if ((row + col) % 2 == 0) even else odd
            drawRect(
                color = square,
                topLeft = Offset(x, y),
                size = Size(minOf(cell, size.width - x), minOf(cell, size.height - y)),
            )
            x += cell
            col++
        }
        y += cell
        row++
    }
}

/** Above this, images are downscaled before decoding so a large photo cannot exhaust memory. */
const val PREVIEW_MAX_IMAGE_EDGE = 2048

private fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").lowercase()

/**
 * Chooses the HTML entry shown by live web preview. A root `index.html` wins; otherwise the open
 * HTML file wins; otherwise the first HTML file wins. Null means this project has no live page.
 */
internal fun webPreviewEntry(diskTree: List<ProjectFile>, activePath: String): String? {
    val byPath = diskTree.associateBy { it.path }
    if (byPath["index.html"]?.kind == FileKind.Html) return "index.html"
    val active = byPath[activePath]
    if (active?.kind == FileKind.Html) return active.path
    return diskTree.firstOrNull { it.kind == FileKind.Html }?.path
}

/** Which preview the panel draws. */
internal enum class PreviewRoute {
    /** A live page served from the project directory. */
    Web,

    /** A live page the panel cannot serve: the project's own entry exists but its root does not. */
    WebUnavailable,

    /** Nothing to show, because no tab is open and this project has no page of its own. */
    NoFile,

    /** The spec's static phone mock, for a Kotlin file. */
    KotlinMock,

    /** The ordinary file preview: image, JSON, XML, CSV, plain text. */
    Static,
}

/**
 * The order the panel tries things in, decided in one place so the order itself can be pinned.
 *
 * The web entry comes first on purpose. A project's own `index.html` is a preview whether or not
 * anyone has a file open, and telling someone to open a file so they can see a page that already
 * exists is a gate guarding nothing — the first thing on screen for a web project is the page.
 * Everything after it does need a file, because each of those previews *is* one.
 */
internal fun previewRoute(
    webEntry: String?,
    projectRootPresent: Boolean,
    fileName: String,
    fileKind: FileKind?,
): PreviewRoute = when {
    webEntry != null ->
        if (projectRootPresent) PreviewRoute.Web else PreviewRoute.WebUnavailable

    fileName.isBlank() -> PreviewRoute.NoFile

    fileKind == FileKind.Kotlin || fileName.endsWith(".kt", ignoreCase = true) ->
        PreviewRoute.KotlinMock

    else -> PreviewRoute.Static
}

internal data class ComposePreviewContent(
    val title: String,
    val subtitle: String?,
    val button: String?,
)

/**
 * Reads representative UI strings from Kotlin source for the static phone mock.
 *
 * This intentionally parses only enough Compose to find `Text("...")` labels and a `Button`
 * label. It is a preview aid, not a compiler; anything it cannot understand is omitted rather
 * than guessed.
 */
internal fun extractComposePreview(source: String, fileName: String): ComposePreviewContent {
    val texts = extractComposeTexts(source, limit = 5)
    val button = findComposeButtonLabel(source)
    val body = texts.filterNot { it == button }
    return ComposePreviewContent(
        title = body.firstOrNull()?.ifBlank { fileName } ?: fileName,
        subtitle = body.getOrNull(1),
        button = button ?: body.getOrNull(2),
    )
}

/**
 * The 0-based offset range of the body of the function declared on 1-based [funLine], or null
 * when the declaration cannot be delimited (expression bodies, unparseable headers).
 *
 * Cards read texts only from inside their own function's braces: without this, a card for
 * HomeScreen would show the button of whatever function comes next in the file. Errors are
 * scoped to the same range, so a broken function fails its own card instead of its neighbour's.
 */
internal fun functionBodyOf(source: String, funLine: Int): IntRange? {
    if (funLine < 1) return null
    var lineStart = 0
    var line = 1
    while (line < funLine && lineStart < source.length) {
        if (source[lineStart] == '\n') line++
        lineStart++
    }
    if (line != funLine) return null
    val funAt = source.indexOf("fun", lineStart)
    if (funAt < 0 || (funAt > 0 && isKotlinIdentifierChar(source[funAt - 1]))) return null
    // The declaration lives on this line: a `fun` past the next newline belongs to a function
    // below, and borrowing its body would reintroduce the neighbour's texts.
    if (source.indexOf('\n', lineStart).let { it >= 0 && it < funAt }) return null
    val parenAt = source.indexOf('(', funAt)
    if (parenAt < 0) return null
    val params = parseDelimitedRange(source, parenAt, '(', ')') ?: return null
    // Inner span again: the character past the closer sits two past its end.
    val afterParams = params.last + 2
    var next = skipTriviaAndComments(source, afterParams)
    // A return type sits between the parameters and the body: skip one `: Type` span, which
    // ends at the first character that can open neither a type nor the body.
    if (next < source.length && source[next] == ':') {
        var t = next + 1
        var angle = 0
        while (t < source.length) {
            val ch = source[t]
            if (ch == '<') angle++
            else if (ch == '>') angle--
            else if (angle == 0 && (ch == '{' || ch == '=' || ch == '\n')) break
            t++
        }
        // The brace may sit on the next line; an `=` means an expression body, which has none.
        val braced = skipTriviaAndComments(source, t).takeIf {
            it < source.length && source[it] == '{'
        }
        next = braced ?: skipTriviaAndComments(source, afterParams)
    }
    if (next >= source.length || source[next] != '{') return null
    return parseDelimitedRange(source, next, '{', '}')
}

private fun extractComposeTexts(source: String, limit: Int): List<String> {
    val out = ArrayList<String>()
    var search = 0
    while (out.size < limit) {
        val call = findCallStart(source, "Text", search)
        if (call < 0) break
        val arguments = parseDelimitedRange(source, call + "Text".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        firstStringLiteralInRange(source, arguments)?.let { out.add(it) }
        search = arguments.last + 1
    }
    return out
}

private fun findComposeButtonLabel(source: String): String? {
    var search = 0
    while (true) {
        val call = findCallStart(source, "Button", search)
        if (call < 0) return null
        val arguments = parseDelimitedRange(source, call + "Button".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        // parseDelimitedRange returns the inner span, so last + 1 is the closer itself:
        // the body hunt starts past it.
        var next = skipTriviaAndComments(source, arguments.last + 2)
        if (next < source.length && source[next] == '{') {
            val body = parseDelimitedRange(source, next, '{', '}')
            if (body != null) {
                firstTextLiteralAfter(source, body.first, body.last)?.let { return it }
            }
        }
        search = arguments.last + 1
    }
}

private fun firstTextLiteralAfter(source: String, from: Int, to: Int): String? {
    var search = from.coerceIn(0, source.length)
    val end = to.coerceIn(0, source.length)
    while (search <= end) {
        val call = findCallStart(source, "Text", search)
        if (call < 0 || call > end) return null
        val arguments = parseDelimitedRange(source, call + "Text".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        firstStringLiteralInRange(source, arguments.first.coerceAtLeast(0)..arguments.last.coerceAtMost(end))
            ?.let { return it }
        search = arguments.last + 1
    }
    return null
}

private fun isKotlinIdentifierChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

private fun findCallStart(source: String, name: String, from: Int): Int {
    var i = from.coerceIn(0, source.length)
    var blockDepth = 0
    var inRawString = false
    var inDouble = false
    var inSingle = false
    var inBacktick = false
    var escaped = false
    while (i + name.length <= source.length) {
        if (inRawString) {
            if (source.startsWith("\"\"\"", i)) {
                inRawString = false
                i += 3
            } else {
                i++
            }
            continue
        }
        val ch = source[i]
        if (escaped) {
            escaped = false
            i++
            continue
        }
        if (inDouble) {
            if (ch == '\\') escaped = true
            else if (ch == '"') inDouble = false
            i++
            continue
        }
        if (inSingle) {
            if (ch == '\\') escaped = true
            else if (ch == '\'') inSingle = false
            i++
            continue
        }
        if (inBacktick) {
            if (ch == '`') inBacktick = false
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            inRawString = true
            i += 3
            continue
        }
        if (ch == '"') {
            inDouble = true
            i++
            continue
        }
        if (ch == '\'') {
            inSingle = true
            i++
            continue
        }
        if (ch == '`') {
            inBacktick = true
            i++
            continue
        }
        if (source.startsWith(name, i) && (i == 0 || !isKotlinIdentifierChar(source[i - 1]))) {
            var j = i + name.length
            while (j < source.length && source[j].isWhitespace()) j++
            if (j < source.length && source[j] == '(') return i
        }
        i++
    }
    return -1
}

private fun parseDelimitedRange(source: String, openIndex: Int, open: Char, close: Char): IntRange? {
    var i = openIndex.coerceIn(0, source.length)
    while (i < source.length && source[i].isWhitespace()) i++
    if (i >= source.length || source[i] != open) return null
    var depth = 0
    var blockDepth = 0
    var inRawString = false
    var inDouble = false
    var inSingle = false
    var inBacktick = false
    var escaped = false
    while (i < source.length) {
        if (inRawString) {
            if (source.startsWith("\"\"\"", i)) {
                inRawString = false
                i += 3
            } else {
                i++
            }
            continue
        }
        val ch = source[i]
        if (escaped) {
            escaped = false
            i++
            continue
        }
        if (inDouble) {
            if (ch == '\\') escaped = true
            else if (ch == '"') inDouble = false
            i++
            continue
        }
        if (inSingle) {
            if (ch == '\\') escaped = true
            else if (ch == '\'') inSingle = false
            i++
            continue
        }
        if (inBacktick) {
            if (ch == '`') inBacktick = false
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            inRawString = true
            i += 3
            continue
        }
        if (ch == '"') {
            inDouble = true
            i++
            continue
        }
        if (ch == '\'') {
            inSingle = true
            i++
            continue
        }
        if (ch == '`') {
            inBacktick = true
            i++
            continue
        }
        if (ch == open) depth++
        if (ch == close) {
            depth--
            if (depth == 0) return (openIndex + 1)..(i - 1)
        }
        i++
    }
    return null
}

private fun firstStringLiteralInRange(source: String, range: IntRange): String? {
    var i = range.first.coerceAtLeast(0)
    val end = range.last.coerceAtMost(source.length - 1)
    var blockDepth = 0
    while (i <= end) {
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) end + 1 else (newline + 1).coerceAtMost(end + 1)
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            val close = source.indexOf("\"\"\"", i + 3)
            if (close < 0 || close > end) return null
            return source.substring(i + 3, close)
        }
        if (source[i] == '"') {
            return readKotlinStringLiteral(source, i)?.first
        }
        i++
    }
    return null
}

private fun readKotlinStringLiteral(source: String, start: Int): Pair<String, Int>? {
    if (start >= source.length || source[start] != '"') return null
    val out = StringBuilder()
    var i = start + 1
    while (i < source.length) {
        val ch = source[i]
        if (ch == '\\' && i + 1 < source.length) {
            when (val next = source[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                '\'', '"', '\\', '$' -> out.append(next)
                'u' -> {
                    val hex = source.substring(i + 2, (i + 6).coerceAtMost(source.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: next)
                    i += 6
                    continue
                }
                else -> {
                    out.append(ch)
                    out.append(next)
                }
            }
            i += 2
            continue
        }
        if (ch == '"') return out.toString() to (i + 1)
        if (ch == '\n') return null
        out.append(ch)
        i++
    }
    return null
}

private fun skipTriviaAndComments(source: String, from: Int): Int {
    var i = from.coerceIn(0, source.length)
    var blockDepth = 0
    while (i < source.length) {
        val ch = source[i]
        if (ch.isWhitespace()) {
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        return i
    }
    return i
}

/** Calculate the sample size for downsampling an image. */
fun sampleSizeFor(
    srcWidth: Int,
    srcHeight: Int,
    maxEdge: Int = PREVIEW_MAX_IMAGE_EDGE,
): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var longest = maxOf(srcWidth, srcHeight)
    var sample = 1
    while (longest / 2 >= maxEdge) {
        longest /= 2
        sample *= 2
    }
    return sample
}

/** A decoded bitmap plus a note when it had to be downscaled. */
class PreviewImage(val bitmap: ImageBitmap, val note: String? = null)

/** Two-pass decode: bounds first, then a sampled decode. Never runs on the main thread. */
fun decodeScaled(bytes: ByteArray, maxEdge: Int = PREVIEW_MAX_IMAGE_EDGE): PreviewImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalArgumentException("Not a decodable image")
    val note = if (sample > 1) {
        "Downscaled to ${bitmap.width}x${bitmap.height} (original ${bounds.outWidth}x${bounds.outHeight})."
    } else {
        null
    }
    return PreviewImage(bitmap.asImageBitmap(), note)
}

/**
 * Hybrid preview: web projects get a live page from a local HTTP server, Kotlin files get the
 * spec's static phone mock, and all other assets keep the existing static file preview.
 */
@Composable
internal fun IdePreviewPanel(
    fileName: String,
    fileKind: FileKind?,
    projectPath: String,
    loader: (String) -> String?,
    binaryLoader: (String) -> ByteArray?,
    diskTree: List<ProjectFile>,
    projectRepository: ProjectRepository,
    refreshToken: Int = 0,
    onRefresh: () -> Unit = {},
    projectRoot: java.io.File? = null,
    // Tapping a console source link lands here with the project path and 1-based line.
    onOpenFile: (String, Int) -> Unit = { _, _ -> },
    // Project errors for the preview cards' Failed state, matched by file name.
    previewErrors: List<ProjectDiagnostic> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val extension = remember(fileName) { extensionOf(fileName) }
    val isImage = extension in ImageExtensions
    val webEntry = remember(diskTree, projectPath) { webPreviewEntry(diskTree, projectPath) }
    // Hoisted out of WebPreview so the toolbar can hand the live page to the device's browser.
    var webUrl by remember(webEntry) { mutableStateOf<String?>(null) }
    val startingWeb = webEntry != null && projectRoot != null && webUrl == null
    // The console belongs to the page, so it resets with it — but the open state survives a
    // reload, because a reload the reader asked for must not slam their log shut.
    val consoleLines = remember(webEntry) { mutableStateOf(emptyList<ConsoleLine>()) }
    val preserveLog = remember(webEntry) { mutableStateOf(false) }
    var consoleOpen by remember(webEntry) { mutableStateOf(false) }
    val errorCount = consoleLines.value.count { it.level == ConsoleLevel.Error }

    Column(
        modifier
            .fillMaxSize().background(c.bg)) {
        val route = previewRoute(
            webEntry = webEntry,
            projectRootPresent = projectRoot != null,
            fileName = fileName,
            fileKind = fileKind,
        )
        if (route == PreviewRoute.Web) {
            WebToolbar(
                fileName = webEntry?.substringAfterLast('/') ?: fileName,
                address = webUrl?.substringAfter("://") ?: "localhost",
                errorCount = errorCount,
                loading = startingWeb,
                canOpenInBrowser = webUrl != null,
                onRefresh = onRefresh,
                onOpenInBrowser = {
                    webUrl?.let { url ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                            )
                        }
                    }
                },
                onConsole = { consoleOpen = !consoleOpen },
            )
        } else {
            // The screens panel owns its own chrome (device row, theme, refresh): a second
            // toolbar above it would be an empty gray strip with a duplicate refresh icon.
            if (route != PreviewRoute.KotlinMock) {
                PreviewToolbar(
                    loading = startingWeb,
                    canOpenInBrowser = webUrl != null,
                    onRefresh = onRefresh,
                    onOpenInBrowser = {
                        webUrl?.let { url ->
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                )
                            }
                        }
                    },
                )
            }
        }

        when (route) {
            PreviewRoute.WebUnavailable -> PreviewMessage("Preview is unavailable for this project.")

            PreviewRoute.Web -> {
                // previewRoute only says Web when both are there; the pair is split so the branch
                // does not have to convince the compiler of that a second time.
                val root = projectRoot
                val entry = webEntry
                if (root != null && entry != null) {
                    WebPreview(
                        projectRoot = root,
                        entryPath = entry,
                        refreshToken = refreshToken,
                        onUrl = { webUrl = it },
                        linesState = consoleLines,
                        preserveState = preserveLog,
                        consoleOpen = consoleOpen,
                        onConsoleOpen = { consoleOpen = it },
                        onClearLines = { consoleLines.value = emptyList() },
                        onOpenFile = onOpenFile,
                    )
                }
            }

            PreviewRoute.NoFile -> PreviewMessage("Open a file to preview it.")

            PreviewRoute.KotlinMock -> ScreensPanel(
                files = diskTree.filter { it.kind == FileKind.Kotlin },
                loader = loader,
                refreshToken = refreshToken,
                activePath = projectPath,
                errors = previewErrors,
                onOpenFile = onOpenFile,
            )

            PreviewRoute.Static -> StaticPreview(
                fileName = fileName,
                fileKind = fileKind,
                projectPath = projectPath,
                loader = loader,
                binaryLoader = binaryLoader,
                refreshToken = refreshToken,
            )
        }
    }
}

/**
 * The bridge the page's own errors cross on. Console messages (log/warn/error) arrive through
 * the native [WebChromeClient] instead — wiring both would print every console.error twice —
 * so this carries only what nothing else can: uncaught exceptions, installed per page load.
 */
private class ConsoleBridge(private val onError: (String, String, Int) -> Unit) {
    @android.webkit.JavascriptInterface
    fun postError(message: String, source: String, line: Int) {
        onError(message, source, line)
    }
}

private const val ERROR_HOOK =
    "(function(){if(window.__ccDone)return;window.__ccDone=true;" +
        "window.onerror=function(m,s,l){__ccBridge.postError(String(m),String(s||''),l|0);};})();"

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0 Safari/537.36"

/** A loading failure in plain words, with the file that failed first when it is known. */
private fun pageErrorText(url: String?, code: Int): String {
    val name = url?.substringAfterLast('/')?.substringBefore('?')?.ifBlank { null }
    return if (name == null) {
        "This page failed to load ($code)."
    } else {
        "This page failed to load ($code): $name."
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ColumnScope.WebPreview(
    projectRoot: java.io.File,
    entryPath: String,
    refreshToken: Int,
    onUrl: (String) -> Unit,
    linesState: androidx.compose.runtime.MutableState<List<ConsoleLine>>,
    preserveState: androidx.compose.runtime.MutableState<Boolean>,
    consoleOpen: Boolean,
    onConsoleOpen: (Boolean) -> Unit,
    onClearLines: () -> Unit,
    onOpenFile: (String, Int) -> Unit,
) {
    val c = LocalPageColors.current
    var device by remember(entryPath) { mutableStateOf(PreviewDevice.Phone) }
    var previewUrl by remember(projectRoot, entryPath) { mutableStateOf<String?>(null) }
    var loadedToken by remember(projectRoot, entryPath) { mutableStateOf<Int?>(null) }
    var loadStart by remember(projectRoot, entryPath) { mutableStateOf(0L) }
    var pendingScroll by remember(projectRoot, entryPath) { mutableStateOf<Pair<Int, Int>?>(null) }
    var pageError by remember(projectRoot, entryPath) { mutableStateOf<String?>(null) }
    var loading by remember(projectRoot, entryPath) { mutableStateOf(true) }
    var localToken by remember(projectRoot, entryPath) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // The state objects are stable, so the clients below — created once per WebView — always
    // read and write the current log rather than the one from the first frame.
    fun append(line: ConsoleLine) {
        linesState.value = appendConsole(linesState.value, line)
    }

    DisposableEffect(projectRoot, entryPath) {
        var server: PreviewServer? = null
        val job = scope.launch(Dispatchers.IO) {
            val started = PreviewServer(projectRoot)
            started.start()
            server = started
            withContext(Dispatchers.Main) {
                previewUrl = "${started.baseUrl()}/$entryPath"
                loadedToken = null
            }
        }
        onDispose {
            job.cancel()
            runCatching { server?.stop() }
        }
    }

    val url = previewUrl
    if (url == null) {
        // The toolbar already owns the loading state, so there is nothing left to draw here.
        return
    }
    LaunchedEffect(url) { onUrl(url) }
    DeviceRow(
        device = device,
        onDevice = { device = it },
    )
    BoxWithConstraints(
        Modifier
            .then(if (consoleOpen) Modifier.height(150.dp).fillMaxWidth() else Modifier.weight(1f))
            .background(c.fieldBg),
    ) {
        val viewportDp = maxWidth
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Desktop lays out 1280 wide and scales the drawing to fit: layouts with
            // breakpoints behave correctly, and rotation rescales without a reload because
            // the scale is draw-time, not a setting. (WebSettings.setInitialScale is gone
            // from current compile stubs, which is just as well.)
            val desktopScale = (viewportDp.value / 1280f).coerceIn(0.1f, 1f)
            val contentMod = when (device) {
                PreviewDevice.Phone -> Modifier.fillMaxSize()
                PreviewDevice.Tablet -> Modifier.fillMaxWidth(0.78f).fillMaxHeight()
                PreviewDevice.Desktop -> Modifier
                    .width(1280.dp)
                    .fillMaxHeight()
                    .graphicsLayer(
                        scaleX = desktopScale,
                        scaleY = desktopScale,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    )
            }
            // A device switch rebuilds the view: it is an explicit tap, and a fresh view is
            // the only honest way to apply a new user agent and viewport scale.
            androidx.compose.runtime.key(device) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            applyDeviceSettings(this, device)
                            addJavascriptInterface(
                                ConsoleBridge { message, source, line ->
                                    append(
                                        ConsoleLine(
                                            ConsoleLevel.Error,
                                            message.ifBlank { "Uncaught error" },
                                            relativeSource(source).ifBlank { null },
                                            line,
                                        ),
                                    )
                                },
                                "__ccBridge",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    loadStart = System.currentTimeMillis()
                                    loading = true
                                    pageError = null
                                    if (!preserveState.value) {
                                        linesState.value = emptyList()
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val web = view ?: return
                                    web.evaluateJavascript(ERROR_HOOK, null)
                                    pendingScroll?.let { (x, y) ->
                                        web.scrollTo(x, y)
                                        pendingScroll = null
                                    }
                                    append(loadStampLine(System.currentTimeMillis() - loadStart))
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageError = pageErrorText(url, error?.errorCode ?: -1)
                                        loading = false
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?,
                                ) {
                                    val code = errorResponse?.statusCode ?: 0
                                    val failed = request?.url?.lastPathSegment
                                    if (request?.isForMainFrame == true && code >= 400) {
                                        pageError = pageErrorText(url, code)
                                        loading = false
                                    } else if (failed != null) {
                                        // The page lived; one of its files did not. The source
                                        // link points at the page: its line is unknowable here,
                                        // so the link opens the file rather than a line in it.
                                        append(
                                            ConsoleLine(
                                                ConsoleLevel.Error,
                                                "Failed to load $failed ($code)",
                                                entryPath,
                                                0,
                                            ),
                                        )
                                    }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(
                                    message: android.webkit.ConsoleMessage?,
                                ): Boolean {
                                    val level = when (message?.messageLevel()) {
                                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.Error
                                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.Warning
                                        else -> ConsoleLevel.Log
                                    }
                                    append(
                                        ConsoleLine(
                                            level,
                                            message?.message().orEmpty(),
                                            relativeSource(message?.sourceId()).ifBlank { null },
                                            message?.lineNumber() ?: 0,
                                        ),
                                    )
                                    return true
                                }

                                override fun onProgressChanged(view: WebView?, progress: Int) {
                                    loading = progress < 100
                                }
                            }
                            loadStart = System.currentTimeMillis()
                            loadUrl(url)
                        }
                    },
                    update = { view ->
                        if (view.url?.substringBefore('#') != url) {
                            pendingScroll = view.scrollX to view.scrollY
                            view.clearCache(true)
                            view.loadUrl(url)
                            loadedToken = refreshToken + localToken
                        } else if (loadedToken != refreshToken + localToken) {
                            pendingScroll = view.scrollX to view.scrollY
                            view.clearCache(true)
                            view.reload()
                            loadedToken = refreshToken + localToken
                        }
                    },
                    modifier = contentMod,
                )
            }
        }
        // The 2 dp accent line runs along the top of the stage while loading.
        if (loading && pageError == null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.accent),
            )
        }
        if (pageError != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = pageError.orEmpty(),
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                com.closecode.ui.components.OutlineButton(
                    label = "Try again",
                    onClick = {
                        pageError = null
                        localToken++
                    },
                )
            }
        }
    }
    ConsoleBar(
        errorCount = linesState.value.count { it.level == ConsoleLevel.Error },
        open = consoleOpen,
        onToggle = { onConsoleOpen(!consoleOpen) },
    )
    if (consoleOpen) {
        ConsolePanel(
            lines = linesState.value,
            preserveState = preserveState,
            onClear = onClearLines,
            onOpenFile = onOpenFile,
            onClose = { onConsoleOpen(false) },
        )
    }
}

/** The user agent the page thinks it is on. The width does the rest: with overview mode
 * the breakpoints follow the layout width, so the stock agent is the honest one everywhere
 * except desktop, which names itself. */
private fun applyDeviceSettings(view: WebView, device: PreviewDevice) {
    view.settings.userAgentString = when (device) {
        PreviewDevice.Desktop -> DESKTOP_UA
        PreviewDevice.Phone, PreviewDevice.Tablet ->
            WebSettings.getDefaultUserAgent(view.context)
    }
}



/** Static preview for images, markdown, JSON, XML, CSV, Kotlin files */
@Composable
private fun StaticPreview(
    fileName: String,
    fileKind: FileKind?,
    projectPath: String,
    loader: (String) -> String?,
    binaryLoader: (String) -> ByteArray?,
    refreshToken: Int = 0,
) {
    val c = LocalPageColors.current
    val extension = remember(fileName) { extensionOf(fileName) }
    val isImage = extension in ImageExtensions

    // Off the main thread: reading is IO, and JSON/XML formatting is CPU bound.
    val text by produceState<String?>(initialValue = null, fileName, projectPath, refreshToken) {
        value = if (isImage) {
            null
        } else {
            withContext(Dispatchers.IO) { loader(projectPath) }
        }
    }
    val image by produceState<PreviewImage?>(initialValue = null, fileName, projectPath, refreshToken) {
        value = if (!isImage) {
            null
        } else {
            withContext(Dispatchers.IO) {
                binaryLoader(projectPath)?.let { bytes ->
                    runCatching { decodeScaled(bytes) }.getOrNull()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize().background(c.bg)) {
        if (fileName.isBlank()) {
            PreviewMessage("Open a file to preview it.")
            return@StaticPreview
        }

        if (isImage) {
            when {
                image == null -> PreviewMessage("No preview available for this image.")
                else -> {
                    val bmp = image!!.bitmap
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Sized to the picture, so the board sits exactly under the pixels and not
                        // in the letterbox around them.
                        Box(
                            Modifier
                                .padding(12.dp)
                                .aspectRatio(
                                    bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f),
                                )
                                .drawBehind { checkerboard(c.bg, c.panel) },
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = fileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            if (image?.note != null) {
                PreviewMessage(image!!.note!!)
            }
            return@StaticPreview
        }

        when {
            text == null -> PreviewMessage("Loading…")
            fileKind == FileKind.Markdown -> MarkdownPreview(text!!)
            fileKind == FileKind.Json -> PrettyPreview(text!!, isJson = true)
            fileKind == FileKind.Xml -> PrettyPreview(text!!, isJson = false)
            fileKind == FileKind.Csv -> CsvPreview(text!!)
            else -> PrettyPreview(text!!, isJson = false, plain = true)
        }
    }
}

/**
 * The preview's own chrome, and the whole of it: a re-read and — when the preview is a live page —
 * a way out to the device's browser, both at the top right.
 *
 * No filename here. The path bar and the tab strip already say which file this is, and repeating it
 * spent a third of the strip's width on something nobody had to be told twice; what is left is a
 * slim header whose left side is simply empty.
 */
@Composable
private fun PreviewToolbar(
    loading: Boolean,
    canOpenInBrowser: Boolean,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PreviewAction(CcIcons.Refresh, "Refresh preview", onClick = onRefresh)
            if (canOpenInBrowser) {
                PreviewAction(CcIcons.External, "Open in browser", onClick = onOpenInBrowser)
            }
        }
        // The wait belongs to the header rather than to a second strip beneath it: one slim bar at
        // the top of the preview, not two stacked ones eating the viewport.
        if (loading) PreviewLoadingBar()
    }
}

@Composable
private fun PreviewAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, c.mu, 17.dp)
    }
}

/**
 * Shown while the local preview server comes up. A bar rather than a message: the wait is short
 * and happens every time the panel opens, so it should read as progress, not as text to dismiss.
 *
 * It hangs off the bottom of the header rather than sitting as a strip of its own, so the wait
 * costs the viewport nothing and there is still only one thing at the top of the preview.
 */
@Composable
private fun PreviewLoadingBar() {
    val c = LocalPageColors.current
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = c.ac,
        trackColor = c.ln,
    )
}

@Composable
private fun PreviewMessage(text: String) {
    val c = LocalPageColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = c.mu,
            fontSize = 13.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ColumnScope.PrettyPreview(source: String, isJson: Boolean, plain: Boolean = false) {
    val c = LocalPageColors.current
    val formatted by produceState<String?>(initialValue = null, source, isJson, plain) {
        value = withContext(Dispatchers.Default) {
            if (plain) source else prettyFormat(source, isJson)
        }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(
            text = formatted.orEmpty(),
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Mono,
        )
    }
}

@Composable
private fun ColumnScope.MarkdownPreview(source: String) {
    val c = LocalPageColors.current
    val parsed by produceState<List<MarkdownBlock>>(initialValue = emptyList(), source) {
        value = withContext(Dispatchers.Default) { parseMarkdown(source) }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (block in parsed) {
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    color = c.tx,
                    fontSize = (20 - block.level * 2).coerceAtLeast(13).sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )

                is MarkdownBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.panel2)
                        .padding(10.dp),
                ) {
                    Text(
                        text = block.text,
                        color = c.string,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                    )
                }

                is MarkdownBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = c.mu, fontSize = 13.sp, fontFamily = Mono)
                    Text(
                        text = block.text,
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )

                is MarkdownBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(c.ln)
                        .height(1.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CsvPreview(source: String) {
    val c = LocalPageColors.current
    val rows by produceState<List<List<String>>>(initialValue = emptyList(), source) {
        value = withContext(Dispatchers.Default) { parseCsv(source) }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (cell in row) {
                    Text(
                        text = cell,
                        color = if (rowIndex == 0) c.tx else c.mu,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp).padding(end = 8.dp),
                    )
                }
            }
            if (rowIndex == 0) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.ln))
            }
        }
    }
}

// -- formatting helpers, all pure so they can be unit tested without Compose -------------------

/** Indents JSON or XML. JSON keeps its key order and string contents untouched. */
fun prettyFormat(source: String, isJson: Boolean): String =
    if (isJson) prettyJson(source) else prettyXml(source)

private const val INDENT = "  "

/**
 * JSON formatter that walks the source rather than rebuilding it from a map, so duplicate keys,
 * key order and unusual number formatting all survive a round trip.
 */
private fun prettyJson(source: String): String {
    val out = StringBuilder()
    var i = 0

    fun skipWhitespace() {
        while (i < source.length && source[i].isWhitespace()) i++
    }

    fun readString(): String {
        val sb = StringBuilder()
        sb.append(source[i]) // opening quote
        i++
        while (i < source.length) {
            val ch = source[i]
            sb.append(ch)
            i++
            if (ch == '\\' && i < source.length) {
                sb.append(source[i])
                i++
                continue
            }
            if (ch == '"') break
        }
        return sb.toString()
    }

    fun readValue(depth: Int) {
        skipWhitespace()
        if (i >= source.length) return
        val ch = source[i]
        if (ch == '"') {
            out.append(readString())
            return
        }
        if (ch != '{' && ch != '[') {
            val start = i
            while (i < source.length && !source[i].isWhitespace() && source[i] !in ",}]") i++
            out.append(source, start, i)
            return
        }
        val close = if (ch == '{') '}' else ']'
        out.append(ch)
        i++
        skipWhitespace()
        // An empty object or array stays on one line.
        if (i < source.length && source[i] == close) {
            out.append(close)
            i++
            return
        }
        out.append('\n')
        var first = true
        while (i < source.length && source[i] != close) {
            skipWhitespace()
            if (!first) {
                if (source[i] == ',') i++ else break
                out.append(',')
                skipWhitespace()
                out.append('\n')
            }
            out.append(INDENT.repeat(depth + 1))
            first = false
            if (i < source.length && source[i] == '"') {
                out.append(readString())
                skipWhitespace()
                if (i < source.length && source[i] == ':') {
                    out.append(": ")
                    i++
                }
            }
            readValue(depth + 1)
        }
        skipWhitespace()
        out.append('\n').append(INDENT.repeat(depth)).append(close)
        if (i < source.length && source[i] == close) i++
    }

    readValue(0)
    return out.toString().trim()
}

/** XML formatter driven by tag boundaries, so nesting follows the document rather than whitespace. */
private fun prettyXml(source: String): String {
    val out = StringBuilder()
    var depth = 0
    var i = 0
    fun breakLine() {
        if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
    }
    while (i < source.length) {
        if (source[i] == '<') {
            val close = source.indexOf('>', i)
            if (close < 0) {
                out.append(source.substring(i))
                break
            }
            val tag = source.substring(i, close + 1)
            val isClosing = tag.startsWith("</")
            val isSelfClosing = tag.endsWith("/>")
            val isDeclaration = tag.startsWith("<?") || tag.startsWith("<!")
            if (isClosing) depth--
            breakLine()
            out.append(INDENT.repeat(depth.coerceAtLeast(0))).append(tag)
            if (!isClosing && !isSelfClosing && !isDeclaration) depth++
            i = close + 1
            continue
        }
        val next = source.indexOf('<', i)
        val text = if (next < 0) source.substring(i) else source.substring(i, next)
        if (text.isNotBlank()) {
            if (out.isNotEmpty() && !out.endsWith("\n") && !out.endsWith(" ")) out.append(' ')
            out.append(text.trim())
        }
        i = if (next < 0) source.length else next
    }
    return out.toString().trim()
}

/** Markdown blocks Preview knows how to draw. Anything unsupported falls back to a paragraph. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/**
 * A deliberately small Markdown reader: headings, bullets, fenced code, horizontal rules and
 * paragraphs. Anything else is kept as text rather than guessed at.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = ArrayList<MarkdownBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    val code = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MarkdownBlock.Paragraph(paragraph.toString().trim()))
        paragraph.clear()
    }

    for (raw in source.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(raw).append('\n')
            continue
        }
        val trimmed = line.trimStart()
        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Rule)
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks.add(MarkdownBlock.Heading(level, trimmed.drop(level).trim()))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                {
                    flushParagraph()
                    blocks.add(MarkdownBlock.Bullet(trimmed.drop(2).trim()))
                }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (inCode && code.isNotBlank()) blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
    flushParagraph()
    return blocks
}

/** Splits CSV rows on commas, honouring double quoted cells. */
fun parseCsv(source: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < source.length) {
        val ch = source[index]
        when {
            inQuotes && ch == '"' && source.getOrNull(index + 1) == '"' -> {
                cell.append('"')
                index++
            }

            ch == '"' -> inQuotes = !inQuotes

            inQuotes -> cell.append(ch)

            ch == ',' -> {
                row.add(cell.toString())
                cell.clear()
            }

            ch == '\n' -> {
                row.add(cell.toString())
                cell.clear()
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = ArrayList()
            }

            ch != '\r' -> cell.append(ch)
        }
        index++
    }
    row.add(cell.toString())
    if (row.any { it.isNotBlank() }) rows.add(row)
    return rows
}
/** What the website printed, in three severities. Errors badge the toolbar. */
internal enum class ConsoleLevel { Log, Warning, Error }

/** One console row: what was printed, and where a tap should land. */
internal data class ConsoleLine(
    val level: ConsoleLevel,
    val text: String,
    val source: String? = null,
    val line: Int = 0,
)

/** The console never grows past this; the oldest rows leave first. */
internal const val CONSOLE_MAX_LINES = 500

/** Appends one row, dropping the oldest past the cap. Skips blank text: noise, not signal. */
internal fun appendConsole(lines: List<ConsoleLine>, line: ConsoleLine): List<ConsoleLine> {
    if (line.text.isBlank()) return lines
    val grown = lines + line
    return if (grown.size > CONSOLE_MAX_LINES) grown.drop(grown.size - CONSOLE_MAX_LINES) else grown
}

/** Errors, warnings and everything, in that order: the badge and the filter chips read these. */
internal fun consoleCounts(lines: List<ConsoleLine>): Triple<Int, Int, Int> {
    var errors = 0
    var warnings = 0
    for (line in lines) {
        when (line.level) {
            ConsoleLevel.Error -> errors++
            ConsoleLevel.Warning -> warnings++
            ConsoleLevel.Log -> Unit
        }
    }
    return Triple(errors, warnings, lines.size)
}

/** The line stamped after every load, so a quiet page still answers. */
internal fun loadStampLine(millis: Long): ConsoleLine =
    ConsoleLine(ConsoleLevel.Log, "Page loaded in $millis ms")

/** Preview widths: full, three quarters, and a desktop laid out wide then scaled to fit. */
internal enum class PreviewDevice(val label: String, val size: String) {
    Phone("Phone", "375 × 812"),
    Tablet("Tablet", "768 × 1024"),
    Desktop("Desktop", "1280 × 800"),
}

/**
 * Offset of the first character of 1-based [line]. Past-the-end lines clamp to the text end,
 * so a link to a line that no longer exists still lands somewhere honest.
 */
internal fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 1) return 0
    var offset = 0
    var current = 1
    while (offset < text.length && current < line) {
        if (text[offset] == '\n') current++
        offset++
    }
    return offset.coerceIn(0, text.length)
}

/**
 * A page URL back to its project path: the console's source links tap through to the file,
 * not to the local server address. Pure, so the mapping itself is tested.
 */
internal fun relativeSource(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val noScheme = url.substringAfter("://", url)
    return noScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
}

/**
 * The website toolbar: the address pill, refresh, open-in-browser and the console button
 * with its error badge. The pill names the file and the local address on one line.
 */
@Composable
private fun WebToolbar(
    fileName: String,
    address: String,
    errorCount: Int,
    loading: Boolean,
    canOpenInBrowser: Boolean,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onConsole: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(c.chipBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.success),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = c.text,
                                fontSize = 12.sp,
                            ),
                        )
                        append(fileName)
                        pop()
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = c.textFaint,
                                fontSize = 11.sp,
                            ),
                        )
                        append("  $address")
                    },
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(4.dp))
            PreviewAction(CcIcons.Refresh, "Refresh preview", onClick = onRefresh)
            if (canOpenInBrowser) {
                PreviewAction(CcIcons.External, "Open in browser", onClick = onOpenInBrowser)
            }
            // The bug button: no badge with no errors, a red count the moment there are.
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onConsole)
                    .semantics { contentDescription = "Open console" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Bug, c.textMuted, 18.dp)
                if (errorCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .height(15.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.error)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (errorCount > 99) "99+" else errorCount.toString(),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 9.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (loading) PreviewLoadingBar()
    }
}

/** Phone, Tablet, Desktop: the widths the page thinks it has, and the size it renders at. */
@Composable
private fun DeviceRow(device: PreviewDevice, onDevice: (PreviewDevice) -> Unit) {
    val c = LocalPageColors.current
    val current = PreviewDevice.entries
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .border(1.dp, c.divider, RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        current.forEach { option ->
            val on = option == device
            Box(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.popupSelected else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onDevice(option) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(
                        when (option) {
                            PreviewDevice.Phone -> CcIcons.Phone
                            PreviewDevice.Tablet -> CcIcons.Tablet
                            PreviewDevice.Desktop -> CcIcons.Monitor
                        },
                        if (on) c.text else c.textMuted,
                        13.dp,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = option.label,
                        color = if (on) c.text else c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = device.size,
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Mono,
        )
    }
}

/** The 40 dp strip that opens the console: icon, word, red count, chevron. */
@Composable
private fun ConsoleBar(errorCount: Int, open: Boolean, onToggle: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(c.panel)
            .border(1.dp, c.divider, RoundedCornerShape(0.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcIcon(CcIcons.Terminal, c.textMuted, 15.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Console",
            color = c.text,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        if (errorCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = errorCount.toString(),
                color = c.error,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        CcIcon(
            if (open) CcIcons.ChevronUp else CcIcons.Down,
            c.textMuted,
            15.dp,
        )
    }
}

private enum class ConsoleFilter { All, Errors, Warnings, Logs }

/**
 * The console itself: 230 dp that resize by their handle, filter chips with a trash can,
 * and rows a long press can select. A new error never opens it — the badge and the red
 * count say enough while it stays shut.
 */
@Composable
private fun ConsolePanel(
    lines: List<ConsoleLine>,
    preserveState: androidx.compose.runtime.MutableState<Boolean>,
    onClear: () -> Unit,
    onOpenFile: (String, Int) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalPageColors.current
    var filter by remember { mutableStateOf(ConsoleFilter.All) }
    var panelH by remember { mutableStateOf(230.dp) }
    var menuOpen by remember { mutableStateOf(false) }
    val shown = remember(lines, filter) {
        when (filter) {
            ConsoleFilter.All -> lines
            ConsoleFilter.Errors -> lines.filter { it.level == ConsoleLevel.Error }
            ConsoleFilter.Warnings -> lines.filter { it.level == ConsoleLevel.Warning }
            ConsoleFilter.Logs -> lines.filter { it.level == ConsoleLevel.Log }
        }
    }
    val (errors, warnings, _) = consoleCounts(lines)
    Column(
        Modifier
            .fillMaxWidth()
            .height(panelH)
            .background(c.fieldBg)
            .border(1.dp, c.divider, RoundedCornerShape(0.dp)),
    ) {
        // The drag handle resizes between a readable minimum and a usable maximum; dragged
        // past the minimum it closes instead, which is the swipe-down gesture for free.
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy ->
                        val next = panelH - with(density) { dy.toDp() }
                        if (next < 120.dp && dy > 0) {
                            onClose()
                        } else {
                            panelH = next.coerceIn(120.dp, 480.dp)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.divider),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConsoleFilterChip("All", null, filter == ConsoleFilter.All) {
                filter = ConsoleFilter.All
            }
            ConsoleFilterChip("Errors", errors, filter == ConsoleFilter.Errors, error = true) {
                filter = ConsoleFilter.Errors
            }
            ConsoleFilterChip("Warnings", warnings, filter == ConsoleFilter.Warnings) {
                filter = ConsoleFilter.Warnings
            }
            ConsoleFilterChip("Logs", null, filter == ConsoleFilter.Logs) {
                filter = ConsoleFilter.Logs
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "Clear console" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Trash, c.textMuted, 15.dp)
            }
            Box {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .semantics { contentDescription = "Console options" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.More, c.textMuted, 15.dp)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = if (preserveState.value) "✓ Preserve log" else "Preserve log",
                                fontFamily = Sans,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            preserveState.value = !preserveState.value
                        },
                    )
                }
            }
        }
        androidx.compose.foundation.text.selection.SelectionContainer {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(shown.size) { index ->
                    ConsoleRow(shown[index], onOpenFile)
                }
            }
        }
    }
}

@Composable
private fun ConsoleFilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    error: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) c.popupSelected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count != null && count > 0) "$label $count" else label,
            color = when {
                error && (count ?: 0) > 0 -> c.error
                selected -> c.text
                else -> c.textMuted
            },
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ConsoleRow(line: ConsoleLine, onOpenFile: (String, Int) -> Unit) {
    val c = LocalPageColors.current
    // Console-only colors, stated here because they belong to no shared surface: a dark red
    // wash for errors, a dark amber one for warnings, plain text for logs.
    val bg = when (line.level) {
        ConsoleLevel.Error -> Color(0xFF1D1215)
        ConsoleLevel.Warning -> Color(0xFF1D1A10)
        ConsoleLevel.Log -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when (line.level) {
        ConsoleLevel.Error -> Color(0xFFF2A0A0)
        ConsoleLevel.Warning -> c.warning
        ConsoleLevel.Log -> c.popupText
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .border(1.dp, Color(0xFF12161F), RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = line.text,
            color = fg,
            fontSize = 11.sp,
            fontFamily = Mono,
            lineHeight = 17.sp,
        )
        if (line.source != null) {
            val name = line.source.substringAfterLast('/')
            val label = if (line.line > 0) {
                "$name:${line.line} · tap to open"
            } else {
                "$name · tap to open"
            }
            Text(
                text = label,
                color = c.link,
                fontSize = 10.5.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { onOpenFile(line.source, line.line.coerceAtLeast(1)) },
            )
        }
    }
}

