package com.closecode.ui.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.closecode.ui.theme.PageColors

private fun kotlin(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)
    val fn = SpanStyle(color = c.fn)



    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(cm) { append("// Closecode · MainActivity.kt") }
        },
        line {
            withStyle(kw) { append("package") }
            append(" com.closecode.app")
        },
        line { append("") },
        line {
            withStyle(kw) { append("import") }
            append(" android.os.Bundle")
        },
        line {
            withStyle(kw) { append("import") }
            append(" androidx.activity.ComponentActivity")
        },
        line {
            withStyle(kw) { append("import") }
            append(" androidx.activity.compose.setContent")
        },
        line {
            withStyle(kw) { append("import") }
            append(" androidx.compose.material3.Text")
        },
        line { append("") },
        line {
            withStyle(kw) { append("class") }
            append(' ')
            withStyle(ty) { append("MainActivity") }
            append(" : ")
            withStyle(ty) { append("ComponentActivity") }
            append("() {")
        },
        line {
            withStyle(kw) { append("private") }
            append(' ')
            withStyle(kw) { append("var") }
            append(' ')
            withStyle(ty) { append("cache") }
            append(": ")
            withStyle(ty) { append("List") }
            append("<")
            withStyle(ty) { append("String") }
            append(">? = ")
            withStyle(kw) { append("null") }
        },
        line { append("") },
        line {
            withStyle(kw) { append("fun") }
            append(' ')
            withStyle(fn) { append("titles") }
            append("(): ")
            withStyle(ty) { append("List") }
            append("<")
            withStyle(ty) { append("String") }
            append("> {")
        },
        line {
            append("    ")
            withStyle(kw) { append("return") }
            append(" cache!!.")
            withStyle(fn) { append("map") }
            append(" { it }")
        },
        line {
            append("}")
        },
        line { append("") },
        line {
            withStyle(kw) { append("override fun") }
            append(' ')
            withStyle(fn) { append("onCreate") }
            append("(savedInstanceState: ")
            withStyle(ty) { append("Bundle") }
            append("?) {")
        },
        line {
            withStyle(fn) { append("setContent") }
            append(" {")
        },
        line {
            withStyle(ty) { append("ClosecodeTheme") }
            append(" {")
        },
        line {
            append("    ")
            withStyle(fn) { append("Text") }
            append('(')
        },
        line {
            append("        text = ")
            withStyle(st) { append("\"Build Android apps, anywhere.\"") }
        },
        line {
            append("    )")
        },
        line {
            append("}")
        },
        line {
            append("}")
        },
        line {
            append("}")
        },
        line {
            append("}")
        },
    )
}

private fun xml(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)



    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(cm) { append("<!-- AndroidManifest.xml -->") }
        },
        line {
            withStyle(kw) { append("manifest") }
            append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        },
        line { append("") },
        line {
            withStyle(kw) { append("application") }
            append(" android:label=")
            withStyle(st) { append("\"@string/app_name\"") }
        },
        line {
            withStyle(kw) { append("    activity") }
            append(" android:name=")
            withStyle(st) { append("\".MainActivity\"") }
        },
        line {
            append("        android:exported=")
            withStyle(st) { append("\"true\"") }
        },
        line {
            withStyle(kw) { append("        intent-filter") }
            append(">")
        },
        line {
            append("            action android:name=")
            withStyle(st) { append("\"android.intent.action.MAIN\"") }
        },
        line {
            withStyle(kw) { append("        </intent-filter") }
            append(">")
        },
        line {
            withStyle(kw) { append("    </application") }
            append(">")
        },
        line {
            withStyle(kw) { append("</manifest") }
            append(">")
        },
    )
}

private fun html(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)



    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(cm) { append("<!-- index.html -->") }
        },
        line {
            withStyle(kw) { append("<!DOCTYPE html>") }
        },
        line {
            withStyle(kw) { append("<html lang=\"en\">") }
        },
        line {
            append("  ")
            withStyle(kw) { append("<head>") }
        },
        line {
            append("    ")
            withStyle(ty) { append("<meta charset=\"utf-8\">") }
        },
        line {
            append("    ")
            withStyle(ty) { append("<title>") }
            append("Your project")
            withStyle(ty) { append("</title>") }
        },
        line {
            append("    ")
            withStyle(ty) { append("<link rel=\"stylesheet\" href=\"styles.css\">") }
        },
        line {
            append("  ")
            withStyle(kw) { append("</head>") }
        },
        line {
            append("  ")
            withStyle(kw) { append("<body>") }
        },
        line {
            append("    ")
            withStyle(ty) { append("<main class=\"hero\">") }
        },
        line {
            append("      ")
            withStyle(ty) { append("<h1>") }
            append("Your project")
            withStyle(ty) { append("</h1>") }
        },
        line {
            append("      ")
            withStyle(ty) { append("<p>") }
            append("Edit this page and watch it change.")
            withStyle(ty) { append("</p>") }
        },
        line {
            append("      ")
            withStyle(ty) { append("<button type=\"button\" class=\"primary\">") }
            append("Run")
            withStyle(ty) { append("</button>") }
        },
        line {
            append("      ")
            withStyle(ty) { append("<hr>") }
        },
        line {
            append("      ")
            withStyle(ty) { append("<small>") }
            append("Created by Closecode")
            withStyle(ty) { append("</small>") }
        },
        line {
            append("    ")
            withStyle(ty) { append("</main>") }
        },
        line {
            append("  ")
            withStyle(kw) { append("</body>") }
        },
        line {
            withStyle(kw) { append("</html>") }
        },
    )
}

private fun js(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)


    val fn = SpanStyle(color = c.fn)

    val num = SpanStyle(color = c.number)
    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(cm) { append("// main.js") }
        },
        line {
            withStyle(kw) { append("const") }
            append(' ')
            withStyle(fn) { append("root") }
            append(" = document.querySelector(")
            withStyle(st) { append("'.hero'") }
            append(')')
        },
        line { append("") },
        line {
            withStyle(kw) { append("async function") }
            append(' ')
            withStyle(fn) { append("load") }
            append("() {")
        },
        line {
            append("  ")
            withStyle(kw) { append("const") }
            append(" res = ")
            withStyle(kw) { append("await") }
            append(' ')
            withStyle(fn) { append("fetch") }
            append('(')
            withStyle(st) { append("'/data.json'") }
            append(')')
        },
        line {
            append("  ")
            withStyle(kw) { append("return") }
            append(" res.")
            withStyle(fn) { append("json") }
            append("()")
        },
        line {
            append("}")
        },
        line { append("") },
        line {
            withStyle(fn) { append("load") }
            append("().")
            withStyle(fn) { append("then") }
            append("(data => {")
        },
        line {
            append("  console.")
            withStyle(fn) { append("log") }
            append('(')
            withStyle(num) { append("1") }
            append(" item)")
        },
        line {
            append("})")
        },
    )
}

private fun css(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)


    val fn = SpanStyle(color = c.fn)
    val num = SpanStyle(color = c.number)
    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(cm) { append("/* styles.css */") }
        },
        line { append("") },
        line {
            withStyle(fn) { append(":root") }
            append(" {")
        },
        line {
            append("  --bg: ")
            withStyle(st) { append("#f8f7f3") }
            append(";")
        },
        line {
            append("  --tx: ")
            withStyle(st) { append("#12151c") }
            append(";")
        },
        line {
            append("}")
        },
        line { append("") },
        line {
            append(".hero {")
        },
        line {
            append("  display: grid;")
        },
        line {
            append("  grid-template-columns: ")
            withStyle(kw) { append("repeat") }
            append("(")
            withStyle(num) { append("2") }
            append(", ")
            withStyle(num) { append("1fr") }
            append(");")
        },
        line {
            append("  gap: ")
            withStyle(num) { append("12") }
            append("px;")
        },
        line {
            append("}")
        },
    )
}

private fun gradle(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)


    val fn = SpanStyle(color = c.fn)

    val num = SpanStyle(color = c.number)
    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(kw) { append("plugins") }
            append(" {")
        },
        line {
            append("    id(")
            withStyle(st) { append("\"com.android.application\"") }
            append(")")
        },
        line {
            append("    id(")
            withStyle(st) { append("\"org.jetbrains.kotlin.android\"") }
            append(")")
        },
        line {
            append("}")
        },
        line { append("") },
        line {
            withStyle(kw) { append("android") }
            append(" {")
        },
        line {
            append("    namespace = ")
            withStyle(st) { append("\"com.closecode.app\"") }
        },
        line {
            append("    compileSdk = ")
            withStyle(num) { append("35") }
        },
        line {
            append("}")
        },
        line { append("") },
        line {
            withStyle(kw) { append("dependencies") }
            append(" {")
        },
        line {
            append("    ")
            withStyle(fn) { append("implementation") }
            append("(")
            withStyle(ty) { append("platform") }
            append("(")
            withStyle(st) { append("\"androidx.compose:compose-bom\"") }
            append("))")
        },
        line {
            append("}")
        },
    )
}

private fun json(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)
    val fn = SpanStyle(color = c.fn)
    val num = SpanStyle(color = c.number)
    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(fn) { append("[") }
        },
        line {
            append("  {")
        },
        line {
            append("    ")
            withStyle(st) { append("\"title\"") }
            append(": ")
            withStyle(st) { append("\"First item\"") }
            append(",")
        },
        line {
            append("    ")
            withStyle(st) { append("\"count\"") }
            append(": ")
            withStyle(num) { append("12") }
        },
        line {
            append("  }")
        },
        line {
            withStyle(fn) { append("]") }
        },
    )
}

private fun markdown(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val ty = SpanStyle(color = c.type)


    fun line(b: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(b)
    return listOf(
        line {
            withStyle(kw) { append("# Your project") }
        },
        line { append("") },
        line {
            withStyle(cm) { append("Created with Closecode.") }
        },
        line { append("") },
        line {
            withStyle(kw) { append("## Getting started") }
        },
        line { append("") },
        line {
            append("Open the project, edit a file, and build.")
        },
    )
}

/**
 * Starter snippet for a language, or nothing when the language is not recognised. An unknown
 * language must never fall back to a different language's template: an empty file with no
 * recognised extension would otherwise be filled with the Kotlin starter.
 */
fun snippetFor(language: String, c: PageColors): List<AnnotatedString> = when {
    // "Gradle Kotlin" contains "Kotlin", so the Gradle test has to come first or every
    // build script would be given the plain Kotlin template.
    language.contains("Gradle", true) -> gradle(c)
    language.contains("Kotlin", true) -> kotlin(c)
    language.contains("XML", true) -> xml(c)
    language.contains("HTML", true) -> html(c)
    language.contains("JavaScript", true) -> js(c)
    language.contains("CSS", true) -> css(c)
    language.contains("JSON", true) -> json(c)
    language.contains("Markdown", true) -> markdown(c)
    else -> emptyList()
}
