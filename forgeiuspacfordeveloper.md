# Closecode IDE: Kotlin (Jetpack Compose) Reference Implementation

This document contains the complete Kotlin source for the Closecode IDE screen, written to match `closecode-ui-spec-for-developer.md` (version 2).

**Status: not compiled and not run.** I wrote this without an Android toolchain, so expect some compile errors and API-version differences to fix. Treat it as a detailed, spec-faithful starting point, not a finished module. If your app already has code for any piece (for example the AI panel), keep yours and use this as the reference for layout, sizes and behavior.

---

## 1. What is real and what is simulated

| Real in this code | Simulated or placeholder |
|---|---|
| Editor with gutter, tabs, undo/redo, save, auto-indent | Analyzer rules (`Analyzer.kt`) are simple regex checks. Replace with your real compiler or LSP diagnostics, keeping the `code` field and the E/W code format. |
| Underlines, pills, lightbulb and autocomplete positioned from the real text layout | Autocomplete list is the words in the file plus a small fixed list of Android classes. |
| Problems bottom sheet, file drawer, command palette, header menu | Run, Build and Logcat are simulated and labeled as such. |
| Bottom bar: Code, Preview, AI, Logcat, Build | AI panel replies with a canned message. Preview shows a simple mock. |

Not built (needs your decision first): code folding, "Fix with AI" on problems, bottom-bar variants other than the decided one.

---

## 2. Setup

**Dependencies** (`app/build.gradle.kts`). No icon library is used on purpose (works with offline builds); icons are drawn from the SVG paths in the spec.

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
}
```

Use any Compose BOM from mid-2024 or newer. `minSdk` 24 or higher is fine.

**Manifest**: the editor needs the keyboard to resize the window.

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:windowSoftInputMode="adjustResize" />
```

**Fonts**: the spec uses Manrope (UI) and Fira Code (code). The code below uses `FontFamily.SansSerif` and `FontFamily.Monospace` so it compiles. To match the spec, add the two fonts under `res/font/` and change `UiFont` and `CodeFont` in `Theme.kt`.

**Package layout** (all under `com.closecode.ide`):

```
ui/Theme.kt            colors, sizes, fonts, theme
ui/Icons.kt            vector icons from the spec's SVG paths
model/Model.kt         data classes and enums
analysis/Analyzer.kt   diagnostics (placeholder rules)
editor/Highlight.kt    syntax coloring
editor/History.kt      undo and redo
IdeViewModel.kt        all state and editing actions
editor/CodeEditor.kt   gutter, text field, underlines, pills, bulb, popup
ui/Chrome.kt           header, tabs, tool row, status bar, bottom bar
ui/Sheets.kt           problems sheet, file drawer, command palette
ui/Panels.kt           preview, AI, logcat, build panels
ui/IdeScreen.kt        puts it all together
MainActivity.kt
```

---

## 3. `ui/Theme.kt`

```kotlin
package com.closecode.ide.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Replace with Manrope and Fira Code from res/font to match the spec exactly.
val UiFont: FontFamily = FontFamily.SansSerif
val CodeFont: FontFamily = FontFamily.Monospace

/** Fixed sizes from the spec. Do not change line height: every overlay depends on it. */
object Dim {
    val LineHeight = 20.dp
    val CodePadTop = 12.dp
    val CodePadStart = 14.dp
    val GutterWidth = 62.dp
    val PopupRow = 53.dp
}

@Immutable
data class CcColors(
    val background: Color, val panel: Color, val panel2: Color, val line: Color,
    val text: Color, val muted: Color, val accent: Color, val accentSoft: Color,
    val editorBg: Color, val error: Color, val warning: Color, val success: Color,
    val purple: Color,
    val errorPillBg: Color, val errorPillText: Color,
    val warnPillBg: Color, val warnPillText: Color,
    val keyword: Color, val string: Color, val comment: Color, val function: Color,
    val number: Color, val annotation: Color, val type: Color,
    val bulbIcon: Color, val bulbBg: Color, val bulbBorder: Color,
    val isDark: Boolean,
)

val LightColors = CcColors(
    background = Color(0xFFECEFF4), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFF3F5F9),
    line = Color(0xFFDDE2EA), text = Color(0xFF101826), muted = Color(0xFF66728A),
    accent = Color(0xFF2457FF), accentSoft = Color(0xFFE3EAFF), editorBg = Color(0xFFFBFCFE),
    error = Color(0xFFD1344B), warning = Color(0xFFA86A00), success = Color(0xFF12805C),
    purple = Color(0xFF5B3FD1),
    errorPillBg = Color(0xFFFDE3E6), errorPillText = Color(0xFFA1232F),
    warnPillBg = Color(0xFFFFF0CF), warnPillText = Color(0xFF8A5A00),
    keyword = Color(0xFF7B3FE4), string = Color(0xFF0D7A55), comment = Color(0xFF8793A8),
    function = Color(0xFF2457FF), number = Color(0xFFC2410C), annotation = Color(0xFFB45309),
    type = Color(0xFF946200),
    bulbIcon = Color(0xFFC98A00), bulbBg = Color(0xFFFFFFFF), bulbBorder = Color(0xFF8A93A6),
    isDark = false,
)

val DarkColors = CcColors(
    background = Color(0xFF0B0F16), panel = Color(0xFF131923), panel2 = Color(0xFF1A2230),
    line = Color(0xFF263043), text = Color(0xFFE7ECF5), muted = Color(0xFF8B98B0),
    accent = Color(0xFF7B9BFF), accentSoft = Color(0xFF1C2A52), editorBg = Color(0xFF0F141D),
    error = Color(0xFFFF6B81), warning = Color(0xFFF0B429), success = Color(0xFF3ECF9B),
    purple = Color(0xFF5B3FD1),
    errorPillBg = Color(0xFF4A2A31), errorPillText = Color(0xFFFFB4BD),
    warnPillBg = Color(0xFF473A1C), warnPillText = Color(0xFFF5C26B),
    keyword = Color(0xFFB794FF), string = Color(0xFF6FE0B0), comment = Color(0xFF5D6A82),
    function = Color(0xFF7FB2FF), number = Color(0xFFFFA86B), annotation = Color(0xFFF5C26B),
    type = Color(0xFFE5C07B),
    bulbIcon = Color(0xFFF5DC8A), bulbBg = Color(0xFF1F1F26), bulbBorder = Color(0xFF9C9CAB),
    isDark = true,
)

val LocalCc = staticCompositionLocalOf { LightColors }

@Composable
fun ClosecodeTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, background = c.background, surface = c.panel, onSurface = c.text,
        onBackground = c.text, surfaceVariant = c.panel2, outline = c.line,
    ) else lightColorScheme(
        primary = c.accent, background = c.background, surface = c.panel, onSurface = c.text,
        onBackground = c.text, surfaceVariant = c.panel2, outline = c.line,
    )
    CompositionLocalProvider(LocalCc provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
```

---

## 4. `ui/Icons.kt`

```kotlin
package com.closecode.ide.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun icon(name: String, path: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()

/** Paths are the same SVG paths used in the HTML prototype (24 x 24 grid, 2 dp strokes). */
object CcIcons {
    val Logo = icon("logo", "M8 8l-4 4 4 4M16 8l4 4-4 4")
    val Save = icon("save", "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6")
    val Undo = icon("undo", "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = icon("redo", "M15 14l5-5-5-5M20 9H10a6 6 0 0 0 0 12h3")
    val Play = icon("play", "M7 4l13 8-13 8z", filled = true)
    val More = icon(
        "more",
        "M10.5 5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0zM10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z" +
            "M10.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
        filled = true,
    )
    val Chevron = icon("chevron", "M9 6l6 6-6 6")
    val Up = icon("up", "M6 15l6-6 6 6")
    val Down = icon("down", "M6 9l6 6 6-6")
    val Copy = icon("copy", "M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM5 15V6a2 2 0 0 1 2-2h8")
    val Warning = icon("warning", "M12 3l10 18H2zM12 10v5M12 18h.01")
    val ErrorCircle = icon("error", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0zM9 9l6 6M15 9l-6 6")
    val Stack = icon("stack", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val Bulb = icon("bulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
}

@Composable
fun CcIcon(icon: ImageVector, tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}
```

---

## 5. `model/Model.kt`

```kotlin
package com.closecode.ide.model

enum class Severity { Error, Warning }   // Error sorts first

data class Diagnostic(
    val file: String,
    val line: Int,        // 0-based
    val col: Int,         // 0-based
    val length: Int,
    val severity: Severity,
    val code: String,     // E0101, W0201 ...
    val message: String,
)

enum class Panel(val label: String) {
    Code("Code"), Preview("Preview"), Ai("AI"), Logcat("Logcat"), Build("Build"),
}

data class LogLine(val time: String, val level: Char, val tag: String, val message: String)
data class ChatMsg(val fromUser: Boolean, val text: String)
enum class TaskState { Pending, Running, Done, Failed }
data class BuildTask(val name: String, val state: TaskState)
data class Suggestion(val name: String, val where: String)
```

---

## 6. `analysis/Analyzer.kt`

Placeholder rules ported from the HTML prototype. Keep the **code format** (E0101 ...) when you swap in real diagnostics.

| Code | Meaning |
|---|---|
| E0101 | Cannot resolve symbol / Unresolved reference |
| E0102 | Syntax error: ';' expected (Java) |
| E0103 | Unclosed bracket |
| E0104 | Unexpected bracket |
| E0105 | Unterminated string literal |
| W0201 | Unused import |
| W0202 | TODO |
| W0203 | Variable never used |

```kotlin
package com.closecode.ide.analysis

import com.closecode.ide.model.Diagnostic
import com.closecode.ide.model.Severity

object Analyzer {
    private val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val notSymbols = setOf(
        "else", "try", "catch", "finally", "do", "break", "continue", "return", "this", "super",
        "true", "false", "null", "companion", "init",
    )

    fun analyze(file: String, text: String): List<Diagnostic> {
        if (file.endsWith(".xml")) return emptyList()
        val out = mutableListOf<Diagnostic>()
        val lines = text.split('\n')
        val isJava = file.endsWith(".java")
        fun add(l: Int, c: Int, n: Int, s: Severity, code: String, m: String) {
            out.add(Diagnostic(file, l, c, n, s, code, m))
        }

        // brackets, strings, TODO
        val stack = ArrayDeque<Triple<Char, Int, Int>>()
        var inBlock = false
        lines.forEachIndexed { i, raw ->
            var line = raw
            if (inBlock) {
                val e = line.indexOf("*/")
                if (e < 0) return@forEachIndexed
                line = line.substring(e + 2); inBlock = false
            }
            line = line.replace(Regex("/\\*.*?\\*/"), "")
            val b = line.indexOf("/*")
            if (b >= 0) { line = line.substring(0, b); inBlock = true }
            if (line.replace("\\\"", "").count { it == '"' } % 2 == 1) {
                add(i, line.indexOf('"').coerceAtLeast(0), 1, Severity.Error, "E0105", "Unterminated string literal")
            }
            val bare = line.replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"").replace(Regex("//.*$"), "")
            bare.forEachIndexed { j, ch ->
                when (ch) {
                    '(', '[', '{' -> stack.addLast(Triple(ch, i, j))
                    ')', ']', '}' -> {
                        val open = stack.removeLastOrNull()
                        if (open == null || open.first != pairs[ch]) {
                            add(i, j, 1, Severity.Error, "E0104", "Unexpected '$ch'")
                        }
                    }
                }
            }
            val todo = Regex("//\\s*TODO:?\\s*(.*)").find(raw)
            if (todo != null) {
                add(i, todo.range.first, todo.value.length, Severity.Warning, "W0202", "TODO: ${todo.groupValues[1].trim()}")
            }
        }
        stack.forEach { add(it.second, it.third, 1, Severity.Error, "E0103", "Unclosed '${it.first}'") }

        // stray symbols and Java semicolons
        lines.forEachIndexed { i, raw ->
            val t = raw.trim()
            val stray = Regex("^\\}\\s*([A-Za-z_]\\w*)$").find(t)
            when {
                stray != null -> {
                    val name = stray.groupValues[1]
                    add(i, raw.lastIndexOf(name), name.length, Severity.Error, "E0101",
                        if (isJava) "Cannot resolve symbol '$name'" else "Unresolved reference '$name'")
                }
                !isJava && Regex("^[a-z_]\\w*$").matches(t) && t !in notSymbols ->
                    add(i, raw.indexOf(t), t.length, Severity.Error, "E0101", "Unresolved reference '$t'")
                isJava && t.isNotEmpty() &&
                    !Regex("^(//|/\\*|\\*|@|\\}|\\{)").containsMatchIn(t) &&
                    !Regex("[;{},(:>+\\-*/=&|.?]$").containsMatchIn(t) && !t.endsWith("*/") ->
                    add(i, (raw.length - 1).coerceAtLeast(0), 1, Severity.Error, "E0102", "Syntax error: ';' expected")
            }
        }

        // Kotlin: unused imports and variables
        if (file.endsWith(".kt")) {
            val body = lines.filterNot { it.startsWith("import ") }.joinToString("\n")
            lines.forEachIndexed { i, raw ->
                Regex("^import\\s+[\\w.]+\\.(\\w+)$").find(raw)?.let { m ->
                    val n = m.groupValues[1]
                    if (!Regex("\\b$n\\b").containsMatchIn(body)) {
                        add(i, raw.lastIndexOf(n), n.length, Severity.Warning, "W0201", "Unused import '$n'")
                    }
                }
                Regex("\\b(?:val|var)\\s+(\\w+)").find(raw)?.let { m ->
                    val n = m.groupValues[1]
                    if (Regex("\\b$n\\b").findAll(text).count() == 1) {
                        add(i, raw.indexOf(n, m.range.first), n.length, Severity.Warning, "W0203", "Variable '$n' is never used")
                    }
                }
            }
        }
        return out
    }
}
```

---

## 7. `editor/Highlight.kt`

```kotlin
package com.closecode.ide.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.closecode.ide.ui.CcColors

private const val KEYWORDS =
    "package|import|class|fun|val|var|override|private|object|return|if|else|when|for|while|is|in|" +
        "null|true|false|this|super|data|interface|suspend|public|protected|void|extends|implements|" +
        "new|int|static|final|boolean"

// groups: 1 comment, 2 string, 3 annotation, 4 keyword, 5 number, 6 call, 7 type
private val CODE_RE = Regex(
    """(//[^\n]*|/\*[\s\S]*?\*/)|("(?:\\.|[^"\\\n])*")|(@\w+)|(\b(?:$KEYWORDS)\b)|(\b\d+(?:\.\d+)?\w*\b)|(\b[A-Za-z_]\w*(?=\())|(\b[A-Z]\w*\b)"""
)

// groups: 1 comment, 2 string, 3 tag, 4 attribute
private val XML_RE = Regex("""(<!--[\s\S]*?-->)|("[^"]*")|(</?[\w.-]+)|(\b[\w-]+:[\w-]+)""")

fun highlight(text: String, xml: Boolean, c: CcColors): AnnotatedString = buildAnnotatedString {
    append(text)
    if (xml) {
        XML_RE.findAll(text).forEach { m ->
            val g = (1..4).first { m.groups[it] != null }
            val color = when (g) { 1 -> c.comment; 2 -> c.string; 3 -> c.keyword; else -> c.function }
            addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }
    } else {
        CODE_RE.findAll(text).forEach { m ->
            val g = (1..7).first { m.groups[it] != null }
            val color = when (g) {
                1 -> c.comment; 2 -> c.string; 3 -> c.annotation; 4 -> c.keyword
                5 -> c.number; 6 -> c.function; else -> c.type
            }
            val style = if (g == 1) SpanStyle(color = color, fontStyle = FontStyle.Italic) else SpanStyle(color = color)
            addStyle(style, m.range.first, m.range.last + 1)
        }
    }
}

class SyntaxTransformation(private val xml: Boolean, private val c: CcColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, xml, c), OffsetMapping.Identity)
}
```

---

## 8. `editor/History.kt`

```kotlin
package com.closecode.ide.editor

import androidx.compose.ui.text.input.TextFieldValue

/** Simple undo/redo for one file. BasicTextField has no history of its own. */
class History(private val limit: Int = 300) {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    fun record(previous: TextFieldValue) {
        undoStack.addLast(previous)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }
}
```

---

## 9. `IdeViewModel.kt`

All state and editing actions live here, so the UI stays thin.

```kotlin
package com.closecode.ide

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closecode.ide.analysis.Analyzer
import com.closecode.ide.editor.History
import com.closecode.ide.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val KNOWN: Map<String, String> = linkedMapOf(
    "Bundle" to "android.os", "BuildConfig" to "com.closecode.blankapp", "Boolean" to "kotlin",
    "ComponentActivity" to "androidx.activity", "setContent" to "androidx.activity.compose",
    "Text" to "androidx.compose.material3", "Button" to "androidx.compose.material3",
    "Modifier" to "androidx.compose.ui", "remember" to "androidx.compose.runtime",
    "mutableStateOf" to "androidx.compose.runtime", "hashMapOf" to "kotlin.collections",
    "hashSetOf" to "kotlin.collections", "hashCode" to "kotlin", "height" to "androidx.compose",
)

private val SAMPLE = linkedMapOf(
    "MainActivity.kt" to """package com.closecode.blankapp

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
nfjjt""",
    "Theme.kt" to """package com.closecode.blankapp

// TODO: add dark colors
fun ClosecodeTheme() {
}""",
    "strings.xml" to """<resources>
    <string name="app_name">Blank app</string>
</resources>""",
)

class IdeViewModel : ViewModel() {
    val projectName = "Blank app"

    private val sources = mutableStateMapOf<String, TextFieldValue>()
    private val saved = mutableStateMapOf<String, String>()
    private val histories = HashMap<String, History>()

    val openTabs = mutableStateListOf("MainActivity.kt", "Theme.kt", "strings.xml")
    var current by mutableStateOf("MainActivity.kt"); private set
    var panel by mutableStateOf(Panel.Code); private set
    var drawerOpen by mutableStateOf(false)
    var paletteOpen by mutableStateOf(false)
    var problemsOpen by mutableStateOf(false); private set
    var highlightLine by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null)
    var darkOverride by mutableStateOf<Boolean?>(null)

    /** Published by the editor so the Tab button in the tool row can accept the first suggestion. */
    var suggestionState by mutableStateOf<Pair<String, List<Suggestion>>>("" to emptyList())

    val logs = mutableStateListOf<LogLine>()
    val chat = mutableStateListOf(ChatMsg(false, "Hi. Ask me to explain, fix or write code in this project."))
    val tasks = mutableStateListOf<BuildTask>()
    var buildResult by mutableStateOf<String?>(null); private set
    private var building = false

    init {
        SAMPLE.forEach { (name, text) ->
            sources[name] = TextFieldValue(text, TextRange(text.length))
            saved[name] = text
        }
    }

    // ---------- files and diagnostics ----------
    val fileNames: List<String> get() = sources.keys.sorted()
    fun valueOf(file: String): TextFieldValue = sources[file] ?: TextFieldValue("")
    fun isDirty(file: String): Boolean = sources[file]?.text != saved[file]

    val diagnostics: List<Diagnostic> by derivedStateOf {
        sources.entries.flatMap { (name, v) -> Analyzer.analyze(name, v.text) }
            .sortedWith(compareBy({ it.severity }, { it.file }, { it.line }))
    }
    val errorCount: Int get() = diagnostics.count { it.severity == Severity.Error }
    val warningCount: Int get() = diagnostics.count { it.severity == Severity.Warning }

    // ---------- navigation ----------
    fun openFile(name: String) {
        if (name !in openTabs) openTabs.add(name)
        current = name; panel = Panel.Code; drawerOpen = false
    }

    fun closeTab(name: String) {
        if (openTabs.size <= 1) return
        val i = openTabs.indexOf(name)
        openTabs.remove(name)
        if (current == name) current = openTabs[(i - 1).coerceAtLeast(0)]
    }

    fun selectPanel(p: Panel) { panel = p; problemsOpen = false }
    fun openProblems(line: Int? = null) { highlightLine = line; problemsOpen = true }
    fun closeProblems() { problemsOpen = false }

    // ---------- editing ----------
    private fun history(file: String) = histories.getOrPut(file) { History() }

    fun onValueChange(new: TextFieldValue) {
        val old = valueOf(current)
        var next = new
        if (new.text != old.text) {
            next = autoIndent(old, new)
            history(current).record(old)
        }
        sources[current] = next
    }

    /** Enter keeps the indent and adds 2 spaces after { or (. Works even when the popup is open. */
    private fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val pos = new.selection.start
        val typedEnter = new.selection.collapsed && new.text.length == old.text.length + 1 &&
            pos > 0 && new.text[pos - 1] == '\n'
        if (!typedEnter) return new
        val lineStart = new.text.lastIndexOf('\n', pos - 2) + 1
        val prev = new.text.substring(lineStart, pos - 1)
        val extra = if (prev.trimEnd().let { it.endsWith("{") || it.endsWith("(") }) "  " else ""
        val pad = prev.takeWhile { it == ' ' || it == '\t' } + extra
        return TextFieldValue(new.text.substring(0, pos) + pad + new.text.substring(pos), TextRange(pos + pad.length))
    }

    private fun edit(file: String, start: Int, end: Int, text: String, caret: Int = start + text.length) {
        val v = valueOf(file)
        history(file).record(v)
        sources[file] = TextFieldValue(v.text.replaceRange(start, end, text), TextRange(caret))
    }

    fun insert(text: String) {
        val s = valueOf(current).selection
        edit(current, s.min, s.max, text)
    }

    fun undo() { history(current).undo(valueOf(current))?.let { sources[current] = it } }
    fun redo() { history(current).redo(valueOf(current))?.let { sources[current] = it } }

    fun save() {
        saved[current] = valueOf(current).text
        message = "Saved $current"
    }

    fun suggestionsFor(v: TextFieldValue): Pair<String, List<Suggestion>> {
        if (!v.selection.collapsed) return "" to emptyList()
        val before = v.text.substring(0, v.selection.start)
        val word = Regex("[A-Za-z_]\\w*$").find(before)?.value ?: return "" to emptyList()
        val inFile = Regex("[A-Za-z_]\\w{3,}").findAll(v.text).map { it.value }.distinct().toList()
        val names = (inFile + KNOWN.keys).distinct()
            .filter { it != word && it.startsWith(word, ignoreCase = true) }.take(5)
        return word to names.map { Suggestion(it, KNOWN[it] ?: "this file") }
    }

    fun accept(s: Suggestion, word: String) {
        val caret = valueOf(current).selection.start
        edit(current, caret - word.length, caret, s.name)
    }

    /** Tab accepts the first suggestion when the popup is open, otherwise inserts 2 spaces. */
    fun onTab() {
        val (word, list) = suggestionState
        if (list.isNotEmpty()) accept(list.first(), word) else insert("  ")
    }

    fun toggleComment() {
        val v = valueOf(current); val t = v.text
        val a = t.lastIndexOf('\n', v.selection.min - 1) + 1
        val z = t.indexOf('\n', v.selection.max).let { if (it < 0) t.length else it }
        val lines = t.substring(a, z).split('\n')
        val uncomment = lines.all { it.isBlank() || it.trimStart().startsWith("//") }
        val out = lines.joinToString("\n") { l ->
            when {
                l.isBlank() -> l
                uncomment -> l.replaceFirst(Regex("^(\\s*)// ?"), "$1")
                else -> l.replaceFirst(Regex("^(\\s*)"), "$1// ")
            }
        }
        edit(current, a, z, out, caret = a + out.length)
    }

    fun moveCaret(dir: Int) {
        val v = valueOf(current); val t = v.text; val p = v.selection.end
        val ls = t.lastIndexOf('\n', p - 1) + 1
        val col = p - ls
        val np = if (dir < 0) {
            if (ls == 0) return
            val ps = t.lastIndexOf('\n', ls - 2) + 1
            ps + minOf(col, ls - 1 - ps)
        } else {
            val le = t.indexOf('\n', p)
            if (le < 0) return
            val ne = t.indexOf('\n', le + 1).let { if (it < 0) t.length else it }
            le + 1 + minOf(col, ne - le - 1)
        }
        sources[current] = v.copy(selection = TextRange(np))
    }

    fun format() {
        if (current.endsWith(".xml")) { message = "Format supports Kotlin and Java files"; return }
        var depth = 0
        val text = valueOf(current).text
        val out = text.lines().joinToString("\n") { l ->
            val t = l.trim()
            val closes = t.startsWith("}") || t.startsWith(")") || t.startsWith("]")
            if (closes) depth = maxOf(0, depth - 1)
            val line = if (t.isEmpty()) "" else "  ".repeat(depth) + t
            val bare = t.replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "").replace(Regex("//.*"), "")
            depth = maxOf(0, depth + bare.count { it in "({[" } - bare.count { it in ")}]" } + if (closes) 1 else 0)
            line
        }
        edit(current, 0, text.length, out, minOf(valueOf(current).selection.end, out.length))
        message = "Formatted"
    }

    // ---------- problems ----------
    private fun offsetOf(file: String, line: Int, col: Int): Int {
        val t = valueOf(file).text
        var o = 0
        repeat(line) { o = t.indexOf('\n', o) + 1 }
        return o + col
    }

    fun jumpTo(d: Diagnostic) {
        openFile(d.file); problemsOpen = false
        val o = offsetOf(d.file, d.line, d.col).coerceIn(0, valueOf(d.file).text.length)
        sources[d.file] = valueOf(d.file).copy(selection = TextRange(o))
    }

    fun quickFix(d: Diagnostic) {
        val t = valueOf(d.file).text
        val lineStart = offsetOf(d.file, d.line, 0)
        val lineEnd = t.indexOf('\n', lineStart).let { if (it < 0) t.length else it }
        when (d.code) {
            "E0101" -> {
                val a = lineStart + d.col
                val removed = t.substring(a, minOf(a + d.length, t.length))
                edit(d.file, a, a + removed.length, "")
                message = "Quick fix: removed '$removed'"
            }
            "E0102" -> { edit(d.file, lineEnd, lineEnd, ";"); message = "Quick fix: added ';'" }
            "W0201" -> {
                edit(d.file, lineStart, minOf(lineEnd + 1, t.length), "", lineStart)
                message = "Quick fix: removed unused import"
            }
            else -> message = "No quick fix for ${d.code}"
        }
    }

    // ---------- run, build, logcat (simulated) ----------
    private fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private fun log(level: Char, tag: String, text: String) {
        logs.add(LogLine(now(), level, tag, text))
        while (logs.size > 1000) logs.removeAt(0)
    }

    fun run() {
        if (errorCount > 0) {
            openProblems()
            message = "Fix $errorCount error${if (errorCount == 1) "" else "s"} before running"
            return
        }
        logs.clear(); panel = Panel.Logcat
        viewModelScope.launch {
            delay(260); log('I', "ActivityManager", "Start proc com.closecode.blankapp (simulated)")
            delay(260); log('D', "Compose", "Recomposing MainActivity")
            delay(260); log('I', "Closecode", "App running")
        }
    }

    fun build() {
        if (building) return
        building = true; panel = Panel.Build; buildResult = null
        val names = listOf("preBuild", "compileDebugKotlin", "mergeDebugResources", "dexBuilderDebug", "packageDebug", "assembleDebug")
        tasks.clear(); names.forEach { tasks.add(BuildTask(it, TaskState.Pending)) }
        viewModelScope.launch {
            for (i in names.indices) {
                tasks[i] = tasks[i].copy(state = TaskState.Running)
                delay(420)
                if (i == 1 && errorCount > 0) {
                    tasks[i] = tasks[i].copy(state = TaskState.Failed)
                    buildResult = "BUILD FAILED · $errorCount compile error${if (errorCount == 1) "" else "s"}. Open Problems to fix."
                    building = false
                    return@launch
                }
                tasks[i] = tasks[i].copy(state = TaskState.Done)
            }
            buildResult = "BUILD SUCCESSFUL (simulated)"
            building = false
        }
    }

    // ---------- AI (placeholder) ----------
    fun sendChat(text: String) {
        chat.add(ChatMsg(true, text))
        viewModelScope.launch {
            delay(500)
            chat.add(ChatMsg(false, "This prototype does not call a model. Connect your AI provider here."))
        }
    }
}
```

---

## 10. `editor/CodeEditor.kt`

This is the hard part. The rules that prevent the bugs seen in the screenshots:

1. **One line height (20 dp) and one top padding (12 dp)** for the gutter and the text. Gutter rows are placed from the text layout's `getLineTop(i)`, so numbers can never drift from the code.
2. **Every overlay position comes from `TextLayoutResult`** (line top, line bottom, line right, horizontal position of a character), never from assumed numbers.
3. **Overlays never take focus.** They use `pointerInput` taps, not focusable components, so the keyboard stays open.
4. **Enter is never intercepted.** Auto-indent runs inside `onValueChange` (see the ViewModel).

```kotlin
package com.closecode.ide.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ide.IdeViewModel
import com.closecode.ide.model.Diagnostic
import com.closecode.ide.model.Severity
import com.closecode.ide.model.Suggestion
import com.closecode.ide.ui.*
import kotlin.math.max
import kotlin.math.roundToInt

private val UnderlineRed = Color(0xFFEF4444)
private val UnderlineAmber = Color(0xFFE0A100)

@Composable
fun codeTextStyle(c: CcColors): TextStyle {
    val density = LocalDensity.current
    return TextStyle(
        fontFamily = CodeFont, fontSize = 13.sp, color = c.text,
        lineHeight = with(density) { Dim.LineHeight.toSp() },
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** Width of one monospace character, read from the layout. */
private fun TextLayoutResult.charWidth(fallback: Float): Float {
    for (i in 0 until lineCount) {
        val s = getLineStart(i)
        if (getLineEnd(i, visibleEnd = true) - s >= 2) {
            return getHorizontalPosition(s + 1, true) - getHorizontalPosition(s, true)
        }
    }
    return fallback
}

@Composable
fun CodeEditor(vm: IdeViewModel, modifier: Modifier = Modifier) {
    val c = LocalCc.current
    val density = LocalDensity.current
    val file = vm.current
    val value = vm.valueOf(file)
    val diags = vm.diagnostics.filter { it.file == file }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var layout by remember(file) { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val style = codeTextStyle(c)
    val transformation = remember(file, c) { SyntaxTransformation(file.endsWith(".xml"), c) }
    val (word, suggestions) = remember(value.text, value.selection) { vm.suggestionsFor(value) }
    val showPopup = focused && suggestions.isNotEmpty()

    SideEffect { vm.suggestionState = if (showPopup) word to suggestions else "" to emptyList() }

    BoxWithConstraints(modifier.fillMaxSize().background(c.editorBg)) {
        val viewportH = constraints.maxHeight
        val viewportW = constraints.maxWidth
        val padTop = with(density) { Dim.CodePadTop.toPx() }
        val padStart = with(density) { Dim.CodePadStart.toPx() }
        val lineH = with(density) { Dim.LineHeight.toPx() }
        val gutterPx = with(density) { Dim.GutterWidth.toPx() }
        val contentHpx = max((layout?.size?.height ?: 0) + (padTop * 2).roundToInt(), viewportH)
        val contentH = with(density) { contentHpx.toDp() }
        val l = layout
        val charW = l?.charWidth(with(density) { 13.sp.toPx() } * 0.6f) ?: 8f

        // keep the caret visible (vertical and horizontal)
        LaunchedEffect(value.selection, l) {
            val lay = l ?: return@LaunchedEffect
            val off = value.selection.end.coerceIn(0, value.text.length)
            val r = lay.getCursorRect(off)
            val top = r.top + padTop; val bottom = r.bottom + padTop
            if (top < vScroll.value) vScroll.animateScrollTo(top.roundToInt())
            else if (bottom > vScroll.value + viewportH) vScroll.animateScrollTo((bottom - viewportH).roundToInt())
            val codeW = viewportW - gutterPx
            val x = r.left + padStart
            if (x < hScroll.value) hScroll.animateScrollTo(x.roundToInt())
            else if (x > hScroll.value + codeW - padStart) hScroll.animateScrollTo((x - codeW + padStart * 2).roundToInt())
        }

        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Gutter(
                layout = l, lineCount = value.text.count { it == '\n' } + 1, diags = diags, height = contentH,
                firstImport = value.text.lineSequence().indexOfFirst { it.startsWith("import ") },
                style = style, onFold = { vm.message = "Code folding is not available yet" },
            )
            Box(Modifier.weight(1f).height(contentH)) {
                Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                    Box(Modifier.padding(start = Dim.CodePadStart, top = Dim.CodePadTop, end = Dim.CodePadStart, bottom = Dim.CodePadTop)) {
                        BasicTextField(
                            value = value, onValueChange = vm::onValueChange, textStyle = style,
                            cursorBrush = SolidColor(c.accent), visualTransformation = transformation,
                            softWrap = false, onTextLayout = { layout = it },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier
                                .widthIn(min = with(density) { (viewportW - gutterPx - padStart * 2).coerceAtLeast(0f).toDp() })
                                .onFocusChanged { focused = it.isFocused }
                                .onPreviewKeyEvent { handleKey(it, vm) },
                        )
                        if (l != null) {
                            Underlines(l, diags)
                            Pills(l, diags, c, charW, vm)
                        }
                    }
                }

                // lightbulb: bottom edge at the vertical center of the line above the caret line
                if (l != null && !showPopup) {
                    val caretLine = l.getLineForOffset(value.selection.end.coerceIn(0, value.text.length))
                    val d = diags.firstOrNull { it.line == caretLine }
                    if (d != null) {
                        val y = max(
                            with(density) { 2.dp.toPx() },
                            padTop + l.getLineTop(caretLine) - lineH / 2 - with(density) { 33.dp.toPx() },
                        )
                        LightbulbButton(
                            c, Modifier.offset { IntOffset(3.dp.roundToPx(), y.roundToInt()) },
                        ) { vm.quickFix(d) }
                    }
                }

                // autocomplete popup: below the caret line, or above when there is no room
                if (showPopup && l != null) {
                    val caretLine = l.getLineForOffset(value.selection.end.coerceIn(0, value.text.length))
                    val gap = with(density) { 6.dp.toPx() }
                    val popupH = with(density) { Dim.PopupRow.toPx() } * suggestions.size
                    val w = minOf(with(density) { 430.dp.toPx() }, viewportW - gutterPx - with(density) { 16.dp.toPx() })
                    val wordStart = (value.selection.start - word.length).coerceAtLeast(0)
                    val rawX = padStart + l.getHorizontalPosition(wordStart, true) - hScroll.value
                    val maxX = (viewportW - gutterPx - w - gap).coerceAtLeast(gap)
                    val x = rawX.coerceIn(gap, maxX)
                    var y = padTop + l.getLineBottom(caretLine) + gap
                    if (y - vScroll.value + popupH > viewportH - with(density) { 4.dp.toPx() }) {
                        y = padTop + l.getLineTop(caretLine) - gap - popupH
                    }
                    AutocompletePopup(
                        items = suggestions, c = c,
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .width(with(density) { w.toDp() }),
                    ) { vm.accept(it, word) }
                }
            }
        }
    }
}

private fun handleKey(e: KeyEvent, vm: IdeViewModel): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.Tab -> { vm.onTab(); true }
        e.isCtrlPressed && e.key == Key.S -> { vm.save(); true }
        e.isCtrlPressed && e.key == Key.Z -> { if (e.isShiftPressed) vm.redo() else vm.undo(); true }
        else -> false
    }
}

// ---------------------------------------------------------------- gutter
@Composable
private fun Gutter(
    layout: TextLayoutResult?, lineCount: Int, diags: List<Diagnostic>, height: androidx.compose.ui.unit.Dp,
    firstImport: Int, style: TextStyle, onFold: () -> Unit,
) {
    val c = LocalCc.current
    val density = LocalDensity.current
    val padTop = with(density) { Dim.CodePadTop.toPx() }
    val lineH = with(density) { Dim.LineHeight.toPx() }
    Box(
        Modifier.width(Dim.GutterWidth).height(height).background(c.editorBg).drawDivider(c.line),
    ) {
        for (i in 0 until lineCount) {
            val top = layout?.takeIf { i < it.lineCount }?.getLineTop(i) ?: (i * lineH)
            val d = diags.filter { it.line == i }.minByOrNull { it.severity }
            val color = when (d?.severity) {
                Severity.Error -> c.error; Severity.Warning -> c.warning; null -> c.comment
            }
            Box(
                Modifier.offset { IntOffset(0, (top + padTop).roundToInt()) }
                    .fillMaxWidth().height(Dim.LineHeight),
            ) {
                if (d != null) {
                    Box(Modifier.offset(11.dp, 7.dp).size(7.dp).clip(CircleShape).background(color))
                }
                androidx.compose.material3.Text(
                    text = "${i + 1}",
                    style = style.copy(color = color, fontWeight = if (d != null) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 26.dp),
                )
                if (i == firstImport) {
                    androidx.compose.material3.Text(
                        text = "▸", color = c.muted, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp)
                            .pointerInput(Unit) { detectTapGestures { onFold() } },
                    )
                }
            }
        }
    }
}

private fun Modifier.drawDivider(color: Color): Modifier = this.then(
    Modifier.drawBehindLine(color)
)

private fun Modifier.drawBehindLine(color: Color): Modifier =
    this.then(androidx.compose.ui.draw.drawBehind {
        drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
    })

// ---------------------------------------------------------------- underlines
@Composable
private fun BoxScope.Underlines(l: TextLayoutResult, diags: List<Diagnostic>) {
    Canvas(Modifier.matchParentSize()) {
        val amp = 1.5.dp.toPx(); val step = 3.dp.toPx()
        diags.forEach { d ->
            if (d.line >= l.lineCount) return@forEach
            val s = l.getLineStart(d.line)
            val e = l.getLineEnd(d.line, visibleEnd = true)
            val a = (s + d.col).coerceIn(s, e)
            val b = (a + d.length.coerceAtLeast(1)).coerceAtMost(e)
            val x0 = l.getHorizontalPosition(a, true)
            val x1 = if (b > a) l.getHorizontalPosition(b, true) else x0 + 8.dp.toPx()
            val y = l.getLineBottom(d.line) - 2.dp.toPx()
            val path = Path().apply {
                moveTo(x0, y)
                var x = x0
                var up = true
                while (x < x1) {
                    x = minOf(x + step, x1)
                    lineTo(x, y + if (up) -amp else amp)
                    up = !up
                }
            }
            drawPath(
                path, if (d.severity == Severity.Error) UnderlineRed else UnderlineAmber,
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

// ---------------------------------------------------------------- inline pills
@Composable
private fun Pills(l: TextLayoutResult, diags: List<Diagnostic>, c: CcColors, charW: Float, vm: IdeViewModel) {
    val density = LocalDensity.current
    diags.groupBy { it.line }.forEach { (line, ds) ->
        if (line >= l.lineCount) return@forEach
        val worst = ds.sortedBy { it.severity }.first()
        val x = l.getLineRight(line) + 2 * charW          // 2 characters after the end of the text
        val y = l.getLineTop(line) - with(density) { 1.dp.toPx() }
        DiagnosticPill(
            d = worst, count = ds.size, c = c,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .pointerInput(line) { detectTapGestures { vm.openProblems(line) } },
        )
    }
}

@Composable
fun DiagnosticPill(d: Diagnostic, count: Int, c: CcColors, modifier: Modifier = Modifier) {
    val isError = d.severity == Severity.Error
    val bg = if (isError) c.errorPillBg else c.warnPillBg
    val fg = if (isError) c.errorPillText else c.warnPillText
    Row(
        modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(bg).padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CcIcon(if (isError) CcIcons.ErrorCircle else CcIcons.Warning, fg, 15.dp)
        androidx.compose.material3.Text(d.message, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = UiFont, maxLines = 1, softWrap = false)
        androidx.compose.material3.Text(d.code, color = fg.copy(alpha = .75f), fontSize = 11.sp,
            fontFamily = CodeFont, maxLines = 1, softWrap = false)
        if (count > 1) {
            Row(
                Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Gray.copy(alpha = .28f)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CcIcon(CcIcons.Stack, fg, 11.dp)
                androidx.compose.material3.Text("$count", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------- lightbulb
@Composable
private fun LightbulbButton(c: CcColors, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(width = 38.dp, height = 33.dp)
            .clip(RoundedCornerShape(11.dp)).background(c.bulbBg)
            .border(1.5.dp, c.bulbBorder, RoundedCornerShape(11.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .semantics { contentDescription = "Quick fix"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { CcIcon(CcIcons.Bulb, c.bulbIcon, 19.dp) }
}

// ---------------------------------------------------------------- autocomplete popup
@Composable
private fun AutocompletePopup(
    items: List<Suggestion>, c: CcColors, modifier: Modifier, onPick: (Suggestion) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.shadow(12.dp, shape).clip(shape).background(c.panel).border(1.5.dp, c.line, shape),
    ) {
        items.forEachIndexed { i, s ->
            val selected = i == 0
            val onSel = Color.White
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dim.PopupRow)
                    .background(if (selected) c.purple else Color.Transparent)
                    .pointerInput(s) { detectTapGestures { onPick(s) } }   // never takes focus
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color.White.copy(alpha = .22f) else Color(0xFFD9A028).copy(alpha = .22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text("C", color = if (selected) onSel else Color(0xFFD9A028),
                        fontFamily = CodeFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    androidx.compose.material3.Text(s.name, color = if (selected) onSel else c.text,
                        fontFamily = CodeFont, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material3.Text(s.where, color = if (selected) onSel.copy(alpha = .75f) else c.muted,
                        fontFamily = CodeFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
```

---

## 11. `ui/Chrome.kt`

Header, file tabs with the explorer chevron, tool row, status bar and bottom bar. There is **no path bar** and **no menu button** in the header (spec 4.1, 6.2).

```kotlin
package com.closecode.ide.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ide.IdeViewModel
import com.closecode.ide.model.Panel

fun Modifier.borderBottom(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

fun Modifier.borderTop(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

fun Modifier.borderEnd(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
}

/** Tap that never takes focus, so the keyboard stays open (Undo, Redo, tool row, popup, bulb). */
fun Modifier.keepFocusClick(label: String, action: () -> Unit): Modifier =
    this.pointerInput(action) { detectTapGestures { action() } }
        .semantics(mergeDescendants = true) {
            contentDescription = label
            role = Role.Button
            onClick(label) { action(); true }
        }

// ---------------------------------------------------------------- header
@Composable
private fun IconBtn(icon: ImageVector, label: String, tint: Color, bg: Color = Color.Transparent, action: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(bg).keepFocusClick(label, action),
        contentAlignment = Alignment.Center,
    ) { CcIcon(icon, tint, 18.dp) }
}

@Composable
fun Header(vm: IdeViewModel) {
    val c = LocalCc.current
    var menu by remember { mutableStateOf(false) }
    val dirty = vm.isDirty(vm.current)
    Row(
        Modifier.fillMaxWidth().background(c.panel).borderBottom(c.line).statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            CcIcon(CcIcons.Logo, Color(0xFF12151C), 16.dp)
        }
        Column(Modifier.weight(1f)) {
            Text("Closecode", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.32).sp, fontFamily = UiFont, maxLines = 1)
            Text(vm.projectName, color = c.muted, fontSize = 12.sp, fontFamily = UiFont,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconBtn(CcIcons.Save, "Save", if (dirty) c.accent else c.muted, if (dirty) c.accentSoft else Color.Transparent) { vm.save() }
        IconBtn(CcIcons.Undo, "Undo", c.muted) { vm.undo() }
        IconBtn(CcIcons.Redo, "Redo", c.muted) { vm.redo() }
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).background(c.accent)
                .keepFocusClick("Run") { vm.run() }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CcIcon(CcIcons.Play, Color.White, 18.dp)
            Text("Run", color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = UiFont)
        }
        Box {
            IconBtn(CcIcons.More, "More", c.muted) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.panel) {
                DropdownMenuItem(text = { Text("Search and commands", color = c.text) }, onClick = { menu = false; vm.paletteOpen = true })
                DropdownMenuItem(text = { Text("Format file", color = c.text) }, onClick = { menu = false; vm.format() })
                DropdownMenuItem(text = { Text("Toggle theme", color = c.text) }, onClick = {
                    menu = false; vm.darkOverride = !(vm.darkOverride ?: c.isDark)
                })
            }
        }
    }
}

// ---------------------------------------------------------------- file tabs
@Composable
fun FileIcon(name: String) {
    val c = LocalCc.current
    val (letter, color) = when {
        name.endsWith(".java") -> "J" to Color(0xFFE08A1E)
        name.endsWith(".kt") -> "K" to c.keyword
        else -> "‹›" to c.function
    }
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
        Text(letter, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CodeFont)
    }
}

@Composable
private fun FileTab(name: String, active: Boolean, dirty: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val c = LocalCc.current
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) c.panel2 else Color.Transparent)
            .clickable(onClick = onOpen).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FileIcon(name)
        Text(name, color = if (active) c.text else c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = UiFont, maxLines = 1)
        if (dirty) Box(Modifier.size(8.dp).clip(CircleShape).background(c.error))   // unsaved dot
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            CcIcon(CcIcons.Close, c.muted, 12.dp)
        }
    }
}

@Composable
fun TabStrip(vm: IdeViewModel) {
    val c = LocalCc.current
    val rotation by animateFloatAsState(if (vm.drawerOpen) 180f else 0f, label = "chevron")
    Row(Modifier.fillMaxWidth().height(50.dp).background(c.panel).borderBottom(c.line)) {
        // file explorer button: the only way to open the drawer on a phone
        Box(
            Modifier.width(44.dp).fillMaxHeight().borderEnd(c.line)
                .keepFocusClick("Files") { vm.drawerOpen = !vm.drawerOpen },
            contentAlignment = Alignment.Center,
        ) { CcIcon(CcIcons.Chevron, c.muted, 18.dp, Modifier.rotate(rotation)) }
        LazyRow(
            Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.openTabs.toList(), key = { it }) { name ->
                FileTab(name, name == vm.current, vm.isDirty(name), { vm.openFile(name) }, { vm.closeTab(name) })
            }
        }
    }
}

// ---------------------------------------------------------------- tool row
@Composable
private fun ToolText(label: String, action: () -> Unit) {
    val c = LocalCc.current
    Box(Modifier.defaultMinSize(minWidth = 46.dp).fillMaxHeight().keepFocusClick(label, action).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = c.text, fontFamily = CodeFont, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, action: () -> Unit) {
    val c = LocalCc.current
    Box(Modifier.defaultMinSize(minWidth = 46.dp).fillMaxHeight().keepFocusClick(label, action).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) { CcIcon(icon, c.text, 20.dp) }
}

@Composable
fun ToolRow(vm: IdeViewModel) {
    val c = LocalCc.current
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(c.panel).borderTop(c.line)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolText("Tab") { vm.onTab() }
        ToolText("//") { vm.toggleComment() }
        ToolIcon(CcIcons.Up, "Up") { vm.moveCaret(-1) }
        ToolIcon(CcIcons.Down, "Down") { vm.moveCaret(1) }
        ToolIcon(CcIcons.Copy, "Copy") {
            val v = vm.valueOf(vm.current)
            val text = if (v.selection.collapsed) {
                v.text.lines().getOrElse(v.text.substring(0, v.selection.start).count { it == '\n' }) { "" }
            } else v.text.substring(v.selection.min, v.selection.max)
            clipboard.setText(AnnotatedString(text)); vm.message = "Copied"
        }
        ToolIcon(CcIcons.Warning, "Problems") { vm.openProblems() }
        Box(Modifier.width(1.dp).height(28.dp).background(c.line))
        listOf("{", "}", "(", ")", ";", "=").forEach { ch -> ToolText(ch) { vm.insert(ch) } }
    }
}

// ---------------------------------------------------------------- status bar
@Composable
fun StatusBar(vm: IdeViewModel) {
    val c = LocalCc.current
    val v = vm.valueOf(vm.current)
    val before = v.text.substring(0, v.selection.end.coerceIn(0, v.text.length))
    val ln = before.count { it == '\n' } + 1
    val col = before.length - (before.lastIndexOf('\n') + 1) + 1
    val e = vm.errorCount; val w = vm.warningCount
    Row(
        Modifier.fillMaxWidth().background(c.panel).borderTop(c.line).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.success))
            Text("Ready", color = c.success, fontSize = 12.sp, fontFamily = CodeFont)
        }
        Text("Ln $ln, Col $col", color = c.muted, fontSize = 12.sp, fontFamily = CodeFont)
        Text(when { vm.current.endsWith(".java") -> "Java"; vm.current.endsWith(".kt") -> "Kotlin"; else -> "XML" },
            color = c.muted, fontSize = 12.sp, fontFamily = CodeFont)
        Spacer(Modifier.weight(1f))
        Row(Modifier.clickable { vm.openProblems() }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$e ${if (e == 1) "error" else "errors"}", color = c.error, fontSize = 12.sp, fontFamily = CodeFont)
            Text("·", color = c.muted, fontSize = 12.sp)
            Text("$w ${if (w == 1) "warning" else "warnings"}", color = c.warning, fontSize = 12.sp, fontFamily = CodeFont)
        }
    }
}

// ---------------------------------------------------------------- bottom bar
@Composable
fun BottomBar(vm: IdeViewModel) {
    val c = LocalCc.current
    Row(Modifier.fillMaxWidth().background(c.panel).borderTop(c.line).navigationBarsPadding()) {
        Panel.values().forEach { p ->
            Box(
                Modifier.weight(1f).clickable { vm.selectPanel(p) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.label, color = if (vm.panel == p) c.accent else c.muted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = UiFont)   // text only: no icons, no badges
            }
        }
    }
}
```

---

## 12. `ui/Sheets.kt`

```kotlin
package com.closecode.ide.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.closecode.ide.IdeViewModel
import com.closecode.ide.editor.DiagnosticPill
import com.closecode.ide.model.Diagnostic
import com.closecode.ide.model.Panel
import com.closecode.ide.model.Severity

// ---------------------------------------------------------------- problems bottom sheet
@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun ProblemCard(d: Diagnostic, highlighted: Boolean, onClick: () -> Unit) {
    val c = LocalCc.current
    val shape = RoundedCornerShape(10.dp)
    val isError = d.severity == Severity.Error
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape)
            .background(if (highlighted) c.accentSoft else Color.Transparent)
            .border(1.dp, if (highlighted) c.accent else c.line, shape)
            .clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(if (isError) c.error else c.warning))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.message, color = c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                Chip(d.code, if (isError) c.errorPillBg else c.warnPillBg, if (isError) c.errorPillText else c.warnPillText)
            }
            Text("${d.file}:${d.line + 1}", color = c.muted, fontSize = 12.sp)
        }
    }
}

/** Replaces the old Problems tab. Sits above the bottom bar because it is drawn inside the content area. */
@Composable
fun BoxScope.ProblemsSheet(vm: IdeViewModel) {
    val c = LocalCc.current
    AnimatedVisibility(vm.problemsOpen, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxSheet = maxHeight * 0.6f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
                .pointerInput(Unit) { detectTapGestures { vm.closeProblems() } })
            val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = maxSheet)
                    .clip(shape).background(c.panel).border(1.dp, c.line, shape)
                    .pointerInput(Unit) { detectTapGestures { } },    // taps on the sheet do not dismiss
            ) {
                Column(
                    Modifier.pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 70.dp.toPx()) vm.closeProblems() },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    },
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(36.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(c.line))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Problems", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        val e = vm.errorCount; val w = vm.warningCount
                        Chip("$e ${if (e == 1) "error" else "errors"}", c.errorPillBg, c.errorPillText)
                        Chip("$w ${if (w == 1) "warning" else "warnings"}", c.warnPillBg, c.warnPillText)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).clickable { vm.closeProblems() },
                            contentAlignment = Alignment.Center) { CcIcon(CcIcons.Close, c.muted, 16.dp) }
                    }
                }
                if (vm.diagnostics.isEmpty()) {
                    Text("No problems. Code is clean.", color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        items(vm.diagnostics) { d ->
                            ProblemCard(d, d.file == vm.current && d.line == vm.highlightLine) { vm.jumpTo(d) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- file drawer
@Composable
private fun FileRow(name: String, vm: IdeViewModel) {
    val c = LocalCc.current
    val selected = name == vm.current
    val errors = vm.diagnostics.count { it.file == name && it.severity == Severity.Error }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable { vm.openFile(name) }.padding(start = 26.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, color = if (selected) c.accent else c.text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (errors > 0) Text("$errors", color = Color.White, fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(c.error).padding(horizontal = 6.dp))
    }
}

@Composable
fun FileDrawerContent(vm: IdeViewModel) {
    val c = LocalCc.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 6.dp, vertical = 10.dp)) {
        Text("app / src / main", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        Text("▾ java/com/closecode/blankapp", color = c.muted, modifier = Modifier.padding(8.dp))
        vm.fileNames.filter { !it.endsWith(".xml") }.forEach { FileRow(it, vm) }
        Text("▾ res/values", color = c.muted, modifier = Modifier.padding(8.dp))
        vm.fileNames.filter { it.endsWith(".xml") }.forEach { FileRow(it, vm) }
    }
}

// ---------------------------------------------------------------- command palette
private data class Command(val title: String, val shortcut: String = "", val run: () -> Unit)

private fun fuzzy(query: String, text: String): Boolean {
    var i = 0
    val t = text.lowercase()
    for (ch in query.lowercase()) { i = t.indexOf(ch, i); if (i < 0) return false; i++ }
    return true
}

@Composable
fun CommandPalette(vm: IdeViewModel) {
    val c = LocalCc.current
    var query by remember { mutableStateOf("") }
    val commands = remember(vm.fileNames) {
        listOf(
            Command("Save file", "Ctrl S") { vm.save() },
            Command("Run app") { vm.run() },
            Command("Build debug APK") { vm.build() },
            Command("Format file") { vm.format() },
            Command("Show problems") { vm.openProblems() },
            Command("Show logcat") { vm.selectPanel(Panel.Logcat) },
            Command("Toggle theme") { vm.darkOverride = !(vm.darkOverride ?: c.isDark) },
        ) + vm.fileNames.map { f -> Command("Open $f") { vm.openFile(f) } }
    }
    val shown = commands.filter { fuzzy(query, it.title) }
    Dialog(onDismissRequest = { vm.paletteOpen = false }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(14.dp))) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Type a command or file name", color = c.muted, fontSize = 15.sp); inner() },
            )
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(shown) { cmd ->
                    Row(Modifier.fillMaxWidth().clickable { vm.paletteOpen = false; cmd.run() }.padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cmd.title, color = c.text)
                        Text(cmd.shortcut, color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
```

---

## 13. `ui/Panels.kt`

Preview, AI, Logcat and Build. Logcat and Build are simulated and say so on screen.

```kotlin
package com.closecode.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ide.IdeViewModel
import com.closecode.ide.model.TaskState

// ---------------------------------------------------------------- preview (mock)
@Composable
fun PreviewPanel(vm: IdeViewModel) {
    val c = LocalCc.current
    val texts = Regex("Text\\(\"([^\"]*)\"").findAll(vm.valueOf(vm.current).text).map { it.groupValues[1] }.toList()
    Box(Modifier.fillMaxSize().background(c.panel2), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(230.dp).heightIn(min = 440.dp).clip(RoundedCornerShape(32.dp))
                .border(7.dp, Color(0xFF202733), RoundedCornerShape(32.dp)).background(Color(0xFFFFFDF9))
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            texts.ifEmpty { listOf("No Text(...) found in this file") }.forEach { Text(it, color = Color(0xFF18141C)) }
        }
    }
}

// ---------------------------------------------------------------- AI (placeholder)
@Composable
fun AiPanel(vm: IdeViewModel) {
    val c = LocalCc.current
    var input by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    LaunchedEffect(vm.chat.size) { if (vm.chat.isNotEmpty()) state.animateScrollToItem(vm.chat.size - 1) }
    Column(Modifier.fillMaxSize().background(c.panel)) {
        Text("AI assistant", color = c.text, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().borderBottom(c.line).padding(horizontal = 14.dp, vertical = 10.dp))
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(12.dp)) {
            items(vm.chat) { m ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Text(m.text, color = if (m.fromUser) Color.White else c.text,
                        modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (m.fromUser) c.accent else c.panel2).padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().borderTop(c.line).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(c.panel2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner -> if (input.isEmpty()) Text("Ask about your code", color = c.muted); inner() },
            )
            Text("Send", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(c.accent)
                    .clickable { if (input.isNotBlank()) { vm.sendChat(input.trim()); input = "" } }
                    .padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }
}

// ---------------------------------------------------------------- logcat (simulated)
@Composable
fun LogcatPanel(vm: IdeViewModel) {
    val c = LocalCc.current
    var level by remember { mutableStateOf('V') }
    val rank = mapOf('V' to 0, 'D' to 1, 'I' to 2, 'W' to 3, 'E' to 4)
    val shown = vm.logs.filter { (rank[it.level] ?: 0) >= (rank[level] ?: 0) }
    Column(Modifier.fillMaxSize().background(c.panel)) {
        Row(Modifier.fillMaxWidth().borderBottom(c.line).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf('V' to "All", 'D' to "Debug", 'I' to "Info", 'W' to "Warn", 'E' to "Error").forEach { (k, label) ->
                val on = level == k
                Text(label, color = if (on) Color.White else c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) c.accent else Color.Transparent)
                        .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(99.dp))
                        .clickable { level = k }.padding(horizontal = 11.dp, vertical = 3.dp))
            }
        }
        Text("Simulated output", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (shown.isEmpty()) Text("Run the app to see logs.", color = c.muted, modifier = Modifier.padding(40.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(shown) { l ->
                val color = when (l.level) { 'I' -> c.success; 'W' -> c.warning; 'E' -> c.error; 'D' -> c.accent; else -> c.muted }
                Text("${l.time} ${l.level}/${l.tag}: ${l.message}", color = color, fontFamily = CodeFont, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().borderBottom(c.line).padding(vertical = 2.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- build (simulated)
@Composable
fun BuildPanel(vm: IdeViewModel) {
    val c = LocalCc.current
    val done = vm.tasks.count { it.state == TaskState.Done }
    val progress = if (vm.tasks.isEmpty()) 0f else done / vm.tasks.size.toFloat()
    Column(Modifier.fillMaxSize().background(c.panel).padding(12.dp)) {
        Text("Simulated build: nothing is compiled.", color = c.muted, fontSize = 11.sp)
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(6.dp).clip(RoundedCornerShape(4.dp)).background(c.panel2)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.accent))
        }
        Text("Build debug APK", color = c.panel, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(c.text).clickable { vm.build() }
                .padding(horizontal = 16.dp, vertical = 9.dp))
        Spacer(Modifier.height(10.dp))
        vm.tasks.forEach { t ->
            Row(Modifier.fillMaxWidth().borderBottom(c.line).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (t.state) { TaskState.Pending -> "·"; TaskState.Running -> "…"; TaskState.Done -> "✓"; TaskState.Failed -> "✕" },
                    color = when (t.state) { TaskState.Done -> c.success; TaskState.Failed -> c.error; else -> c.muted },
                    modifier = Modifier.width(18.dp))
                Text(":app:${t.name}", color = c.text, fontFamily = CodeFont, fontSize = 13.sp)
            }
        }
        vm.buildResult?.let {
            Text(it, color = if (it.startsWith("BUILD FAILED")) c.error else c.success, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}
```

---

## 14. `ui/IdeScreen.kt`

```kotlin
package com.closecode.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.closecode.ide.IdeViewModel
import com.closecode.ide.editor.CodeEditor
import com.closecode.ide.model.Panel
import kotlinx.coroutines.delay

@Composable
private fun BoxScope.Toast(text: String) {
    val c = LocalCc.current
    Text(
        text, color = c.panel,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            .clip(RoundedCornerShape(10.dp)).background(c.text).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun IdeScreen(vm: IdeViewModel) {
    val c = LocalCc.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(vm.drawerOpen) { if (vm.drawerOpen) drawerState.open() else drawerState.close() }
    LaunchedEffect(drawerState.isClosed) { if (drawerState.isClosed) vm.drawerOpen = false }
    LaunchedEffect(vm.message) { if (vm.message != null) { delay(1800); vm.message = null } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,                       // the chevron in the tab strip is the only way to open it
        drawerContent = {
            ModalDrawerSheet(Modifier.width(250.dp), drawerContainerColor = c.panel) { FileDrawerContent(vm) }
        },
    ) {
        Column(Modifier.fillMaxSize().background(c.panel).imePadding()) {
            Header(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.panel) {
                    Panel.Code -> Column(Modifier.fillMaxSize()) {
                        TabStrip(vm)
                        Box(Modifier.weight(1f).fillMaxWidth()) { CodeEditor(vm) }
                        ToolRow(vm)
                        StatusBar(vm)
                    }
                    Panel.Preview -> PreviewPanel(vm)
                    Panel.Ai -> AiPanel(vm)
                    Panel.Logcat -> LogcatPanel(vm)
                    Panel.Build -> BuildPanel(vm)
                }
                ProblemsSheet(vm)
                vm.message?.let { Toast(it) }
            }
            BottomBar(vm)
        }
        if (vm.paletteOpen) CommandPalette(vm)
    }
}
```

---

## 15. `MainActivity.kt`

```kotlin
package com.closecode.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closecode.ide.ui.ClosecodeTheme
import com.closecode.ide.ui.IdeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: IdeViewModel = viewModel()
            ClosecodeTheme(darkOverride = vm.darkOverride) { IdeScreen(vm) }
        }
    }
}
```

---

## 16. Where each spec item lives

| Spec item | Where |
|---|---|
| 4.1 Header (logo tile, name, project, Save, Undo, Redo, Run, overflow) | `Chrome.kt` `Header` |
| 4.2 Bottom bar (Code, Preview, AI, Logcat, Build, text only) | `Chrome.kt` `BottomBar`, `Model.kt` `Panel` |
| 5 File drawer opened by the chevron | `Chrome.kt` `TabStrip`, `Sheets.kt` `FileDrawerContent`, `IdeScreen.kt` |
| 5 Command palette | `Sheets.kt` `CommandPalette` |
| 6.1 File tabs (icon tile, dirty dot, close) | `Chrome.kt` `FileTab`, `FileIcon` |
| 6.2 No path bar | Nothing is built, by design |
| 6.3 Gutter, 20 dp lines, dot, fold arrow | `CodeEditor.kt` `Gutter` |
| 6.4 Wavy underlines | `CodeEditor.kt` `Underlines` |
| 6.5 Pills with codes and count badge | `CodeEditor.kt` `Pills`, `DiagnosticPill` |
| 6.6 Lightbulb and quick fixes | `CodeEditor.kt` `LightbulbButton`, `IdeViewModel.quickFix` |
| 6.7 Autocomplete popup (53 dp rows, flips above, never covers the caret line) | `CodeEditor.kt` `AutocompletePopup` and placement code |
| 6.8 Tool row | `Chrome.kt` `ToolRow` |
| 6.9 Status bar | `Chrome.kt` `StatusBar` |
| 7 Problems bottom sheet | `Sheets.kt` `ProblemsSheet` |
| 7 AI, Logcat, Build, Preview | `Panels.kt` |
| 1A fixes (alignment, popup placement, pill position, Enter, caret visible) | `CodeEditor.kt` (design notes at the top of section 10) |

---

## 17. Known gaps and things to check first

1. **It has not been compiled.** Start by building and fixing import or API mismatches. APIs most likely to differ by Compose version: `LineHeightStyle`, `KeyboardOptions`, `LocalClipboardManager` (deprecated in newer versions), `Modifier.wrapContentWidth(unbounded = true)`.
2. **Fonts are placeholders.** Add Manrope and Fira Code before comparing against the spec. Character widths change with the font, which is why overlay positions are read from the layout and not hard-coded.
3. **Large font scale.** The line height is fixed at 20 dp but the text is 13 sp. At large system font sizes the text can exceed the line height. Decide whether to cap the editor's font scale.
4. **Performance.** `Analyzer.analyze` runs over every file on each keystroke (inside `derivedStateOf`). That is fine for small projects. For real projects, run it on a background coroutine with a short debounce.
5. **Gutter cost.** The gutter creates one composable per line. For files with thousands of lines, draw it in a single `Canvas` using the same `getLineTop` values.
6. **Taps below the last line** do not focus the editor yet. Add a `heightIn` to the text field or a tap handler on the empty area.
7. **Test the behaviors from the spec checklist on a real device**, especially: Enter with the popup open, Undo and Redo keeping the keyboard open, the popup near the bottom with the keyboard open, and line numbers at the top, middle and bottom of a long file.
8. **Open decisions** still affect this code: the AI panel contents, what Preview should show, and "Fix with AI" on problems. See section 1 of the spec.
