package com.closecode.lang
import com.closecode.lang.DiagnosticCodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val testIndex = AndroidApiIndex.parse(
    """
    C|android.widget.TextView|TextView|android.view.View
    C|android.view.View|View|java.lang.Object
    C|java.lang.Object|Object|
    C|java.util.ArrayList|ArrayList|java.lang.Object
    M|android.widget.TextView|setText|1|Unit|CharSequence
    M|android.widget.TextView|setVisibility|1|Unit|Int
    M|android.widget.TextView|getText|0|CharSequence|
    M|android.view.View|getWidth|0|Int|
    M|android.view.View|setEnabled|1|Unit|Boolean
    F|android.view.View|tag|Object|
    """.trimIndent(),
)

private fun analyze(source: String, api: AndroidApiIndex = AndroidApiIndex.EMPTY) =
    KotlinAnalyzer.analyze(source, api)

private fun caret(source: String, marker: String): Int = source.indexOf(marker) + marker.length

private fun realIndex(): AndroidApiIndex {
    val path = System.getProperty("closecode.androidApiIndex") ?: return AndroidApiIndex.EMPTY
    val file = File(path)
    return if (file.isFile) AndroidApiIndex.parse(file.readText()) else AndroidApiIndex.EMPTY
}

class KotlinLexerTest {
    @Test
    fun lexesNestedBlockComments() {
        val src = "/* outer /* inner */ still outer */\nval a = 1"
        val lex = KotlinLexer.lex(src)
        assertTrue(lex.issues.isEmpty())
        assertEquals(1, lex.tokens.count { it.kind == TokenKind.BLOCK_COMMENT })
        assertEquals("/* outer /* inner */ still outer */", lex.tokens.first { it.kind == TokenKind.BLOCK_COMMENT }.text)
    }

    @Test
    fun lexesIdentifiersInsideStringTemplates() {
        val src = "val greeting = \"hello \$name and \${firstName} \$lastName\""
        val lex = KotlinLexer.lex(src)
        val identifiers = lex.significant.filter { it.isIdentifierLike }.map { it.text }
        assertTrue(identifiers.containsAll(listOf("greeting", "name", "firstName", "lastName")))
    }

    @Test
    fun rawStringsKeepTheirInnerDollars() {
        val src = "val s = \"\"\"a \$ b \$ c\"\"\""
        val lex = KotlinLexer.lex(src)
        assertTrue(lex.issues.isEmpty())
        assertEquals(listOf("a \$ b \$ c"), lex.significant.filter { it.kind == TokenKind.STRING }.map { it.text })
        assertEquals(listOf("s"), lex.significant.filter { it.isIdentifierLike }.map { it.text })
    }

    @Test
    fun rawStringTemplatesStillResolveNames() {
        val src = "val s = \"\"\"raw \${notTemplate}\"\"\""
        val ids = KotlinLexer.lex(src).significant.filter { it.isIdentifierLike }.map { it.text }
        assertTrue("template name missing in $ids", ids.contains("notTemplate"))
    }

    @Test
    fun reportsUnterminatedString() {
        val lex = KotlinLexer.lex("val s = \"never closed")
        assertEquals(1, lex.issues.size)
        assertTrue(lex.issues.first().message.contains("never closed"))
    }

    @Test
    fun reportsUnterminatedBlockComment() {
        val lex = KotlinLexer.lex("/* never closed")
        assertEquals(1, lex.issues.size)
    }

    @Test
    fun lexesNumberShapes() {
        val src = "val a = 0xFF\nval b = 1_000\nval c = 3.14\nval d = 1e-9\nval e = 5L"
        val numbers = KotlinLexer.lex(src).significant.filter { it.kind == TokenKind.NUMBER }.map { it.text }
        assertEquals(listOf("0xFF", "1_000", "3.14", "1e-9", "5L"), numbers)
    }

    @Test
    fun lexesBacktickIdentifiers() {
        val src = "fun `my test name`() {}"
        val lex = KotlinLexer.lex(src)
        assertEquals("`my test name`", lex.significant.first { it.kind == TokenKind.BACKTICK_IDENT }.text)
    }

    @Test
    fun tracksLineAndColumn() {
        val src = "val a = 1\nval b = 2"
        val token = KotlinLexer.lex(src).significant.last { it.text == "b" }
        assertEquals(2, token.line)
        assertEquals(5, token.column)
    }

    @Test
    fun doesNotTreatCommentBodiesAsCode() {
        val src = "// val fake = fakeName\nval real = 1"
        val words = KotlinLexer.lex(src).significant.filter { it.isIdentifierLike }.map { it.text }
        assertEquals(listOf("real"), words)
    }
}

class SymbolTableTest {
    @Test
    fun findsTopLevelDeclarations() {
        val src = """
            class Repo(val id: Int) {
                fun find(name: String): Boolean = false
            }
            val version = "1.0"
            fun helper(count: Int) {}
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val names = table.root.symbols.map { it.name }
        assertTrue(names.containsAll(listOf("Repo", "version", "helper")))
    }

    @Test
    fun capturesFunctionParameters() {
        val src = "fun greet(name: String, times: Int) {}"
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val params = table.allSymbols().filter { it.kind == SymbolKind.PARAMETER }
        assertEquals(listOf("name", "times"), params.map { it.name })
        assertEquals("String", params[0].declaredType)
        assertEquals("Int", params[1].declaredType)
    }

    @Test
    fun capturesLambdaParameters() {
        val src = """
            fun run() {
                listOf(1, 2).forEach { item -> println(item) }
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        assertTrue(table.allSymbols().any { it.name == "item" && it.kind == SymbolKind.PARAMETER })
    }

    @Test
    fun capturesForLoopVariables() {
        val src = """
            fun run() {
                for (index in 0 until 10) {
                    println(index)
                }
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        assertTrue(table.allSymbols().any { it.name == "index" })
    }

    @Test
    fun localsAreNotVisibleBeforeTheirDeclaration() {
        val src = """
            fun run() {
                println(later)
                val later = 1
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val beforeDecl = src.indexOf("println(later)") + "println(".length
        assertTrue(table.visibleAt(beforeDecl).none { it.name == "later" })
        val afterDecl = src.indexOf("val later") + "val later".length
        assertTrue(table.visibleAt(afterDecl).any { it.name == "later" })
    }

    @Test
    fun classMembersAreVisibleRegardlessOfOrder() {
        val src = """
            class A {
                fun call() { helper() }
                fun helper() {}
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val atCall = src.indexOf("helper()")
        assertTrue(table.visibleAt(atCall).any { it.name == "helper" })
    }

    @Test
    fun recordsImports() {
        val src = "import android.widget.TextView\nimport java.util.UUID as Uid\nimport kotlin.math.*"
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        assertEquals(listOf("TextView", "Uid", "math"), table.imports.map { it.simpleName })
        assertTrue(table.hasWildcardImport)
    }

    @Test
    fun recordsPropertyTypes() {
        val src = """
            class A {
                val title: String = "x"
                var count = 0
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val title = table.allSymbols().first { it.name == "title" }
        val count = table.allSymbols().first { it.name == "count" }
        assertEquals("String", title.declaredType)
        assertNull(count.declaredType)
    }

    @Test
    fun enumEntriesBecomeSymbols() {
        val src = """
            enum class Color {
                RED, GREEN, BLUE;

                fun hex(): Int = 0
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        val entries = table.allSymbols().filter { it.kind == SymbolKind.ENUM_ENTRY }.map { it.name }
        assertEquals(listOf("RED", "GREEN", "BLUE"), entries)
    }

    @Test
    fun objectAndCompanionAreScoped() {
        val src = """
            class A {
                companion object {
                    const val TAG = "A"
                }
                fun use() = TAG
            }
        """.trimIndent()
        val table = SymbolTable.build(src, KotlinLexer.lex(src))
        assertTrue(table.allSymbols().any { it.name == "Companion" })
        assertTrue(table.allSymbols().any { it.name == "TAG" })
    }
}

class DiagnosticsTest {
    @Test
    fun reportsUnresolvedReference() {
        val result = analyze("val x = notDefinedAnywhere")
        val d = result.errors.firstOrNull { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertNotNull(d)
        assertTrue(d!!.message.contains("notDefinedAnywhere"))
    }

    @Test
    fun doesNotReportResolvedReferences() {
        val src = """
            import android.widget.TextView

            class A {
                val label = "hi"
                fun build(): TextView {
                    val local = 1
                    println(label + local)
                    return TextView(null)
                }
            }
        """.trimIndent()
        val result = analyze(src, testIndex)
        assertEquals(
            emptyList<Diagnostic>(),
            result.errors.filter { it.code == DiagnosticCodes.E0101_UNRESOLVED },
        )
    }

    @Test
    fun memberAccessIsNeverReportedAsUnresolved() {
        val src = """
            class Holder { val someField = 1 }
            fun otherMethod() = 2
            fun run() {
                val someObject = Holder()
                val v = someObject.someField + otherMethod()
            }
        """.trimIndent()
        val result = analyze(src)
        assertEquals(emptyList<Diagnostic>(), result.errors.filter { it.code == DiagnosticCodes.E0101_UNRESOLVED })
    }

    @Test
    fun wildcardImportsSuppressUnresolved() {
        val src = "import kotlin.math.*\nval x = sqrt(4.0)"
        val result = analyze(src)
        assertEquals(0, result.errors.count { it.code == DiagnosticCodes.E0101_UNRESOLVED })
    }

    @Test
    fun namedArgumentsAreNotReported() {
        val src = "fun f(a: Int, b: Int) {}\nfun g() { f(a = 1, b = 2) }"
        val result = analyze(src)
        assertEquals(0, result.errors.count { it.code == DiagnosticCodes.E0101_UNRESOLVED })
    }

    @Test
    fun reportsUnclosedBrace() {
        val result = analyze("fun f() {\n    val a = 1\n")
        assertTrue(result.errors.any { it.code == DiagnosticCodes.E0103_UNCLOSED && it.message.contains("never closed") })
    }

    @Test
    fun reportsStrayClosingBrace() {
        val result = analyze("fun f() {}\n}")
        assertTrue(result.errors.any { it.code == DiagnosticCodes.E0104_UNEXPECTED_CLOSE && it.message.contains("Unexpected") })
    }

    @Test
    fun reportsDuplicateDeclaration() {
        val src = "val a = 1\nval a = 2"
        val result = analyze(src)
        assertTrue(result.errors.any { it.code == DiagnosticCodes.E0107_DUPLICATE })
    }

    @Test
    fun allowsFunctionOverloads() {
        val src = "fun f(a: Int) {}\nfun f(a: String) {}"
        val result = analyze(src)
        assertEquals(0, result.errors.count { it.code == DiagnosticCodes.E0107_DUPLICATE })
    }

    @Test
    fun reportsLiteralTypeMismatch() {
        val src = "val x: Int = \"hello\""
        val result = analyze(src)
        val d = result.errors.firstOrNull { it.code == DiagnosticCodes.E0106_TYPE_MISMATCH }
        assertNotNull(d)
        assertTrue(d!!.message.contains("expected 'Int'"))
    }

    @Test
    fun acceptsCompatibleLiteralTypes() {
        val src = "val a: Int = 5\nval b: String = \"ok\"\nval c: Boolean = true\nval d: Double = 1.5"
        val result = analyze(src)
        assertEquals(0, result.errors.count { it.code == DiagnosticCodes.E0106_TYPE_MISMATCH })
    }

    @Test
    fun reportsUnusedImport() {
        val src = "import java.util.UUID\nval x = 1"
        val result = analyze(src)
        assertTrue(result.warnings.any { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT && it.message.contains("UUID") })
    }

    @Test
    fun usedImportIsNotReported() {
        val src = "import java.util.UUID\nval x = UUID.randomUUID()"
        val result = analyze(src)
        assertEquals(0, result.warnings.count { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT })
    }

    @Test
    fun cleanAndroidFileHasNoErrors() {
        val src = """
            package com.example

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView

            class MainActivity : Activity() {
                private var counter: Int = 0

                override fun onCreate(state: Bundle?) {
                    super.onCreate(state)
                    val view = TextView(this)
                    view.text = "Count: " + counter
                    counter = counter + 1
                    setContentView(view)
                }
            }
        """.trimIndent()
        val result = analyze(src, realIndex())
        assertEquals(emptyList<Diagnostic>(), result.errors)
    }
}

class CompletionTest {
    @Test
    fun offersVisibleLocalsAndTypes() {
        val src = """
            class Repo {
                fun find(query: String) {
                    val results = listOf(1)

                }
            }
        """.trimIndent()
        val result = analyze(src)
        val labels = result.completionsAt(caret(src, "listOf(1)\n")).map { it.label }
        assertTrue("locals missing in $labels", labels.contains("results"))
        assertTrue("params missing in $labels", labels.contains("query"))
        assertTrue("types missing in $labels", labels.contains("Repo"))
    }

    @Test
    fun offersAndroidMembersWithInheritance() {
        val src = """
            fun render() {
                val tv: TextView = TextView(null)
                tv.
            }
        """.trimIndent()
        val result = analyze(src, testIndex)
        val items = result.completionsAt(caret(src, "tv."))
        val labels = items.map { it.label }
        assertTrue("expected setText in $labels", labels.contains("setText"))
        assertTrue("expected inherited getWidth in $labels", labels.contains("getWidth"))
        assertTrue("expected field tag in $labels", labels.contains("tag"))
    }

    @Test
    fun memberDetailShowsSignature() {
        val src = "val tv: TextView = TextView(null)\ntv."
        val result = analyze(src, testIndex)
        val setText = result.completionsAt(caret(src, "tv.")).first { it.label == "setText" }
        assertEquals("setText(CharSequence): Unit", setText.detail)
    }

    @Test
    fun filtersByTypedPrefix() {
        val src = """
            fun render() {
                val tv: TextView = TextView(null)
                tv.setT
            }
        """.trimIndent()
        val result = analyze(src, testIndex)
        val labels = result.completionsAt(caret(src, "tv.setT")).map { it.label }
        assertEquals(listOf("setText"), labels)
    }

    @Test
    fun infersTypeFromConstructorCall() {
        val src = """
            fun render() {
                val tv = TextView(null)
                tv.
            }
        """.trimIndent()
        val result = analyze(src, testIndex)
        val labels = result.completionsAt(caret(src, "tv.")).map { it.label }
        assertTrue(labels.contains("setText"))
    }

    @Test
    fun resolvesMembersOnRealAndroidIndex() {
        val src = """
            fun render() {
                val button: Button = Button(null)
                button.setT
            }
        """.trimIndent()
        val result = analyze(src, realIndex())
        val labels = result.completionsAt(caret(src, "button.setT")).map { it.label }
        assertTrue("Button members missing: $labels", labels.contains("setText"))
    }

    @Test
    fun returnsNothingWhenReceiverTypeIsUnknown() {
        val src = """
            fun render() {
                mystery().
            }
        """.trimIndent()
        val result = analyze(src, testIndex)
        assertTrue(result.completionsAt(caret(src, "mystery().")).isEmpty())
    }

    @Test
    fun offersUserClassMembers() {
        val src = """
            class Repo {
                fun refresh() {}
            }

            fun run() {
                val repo = Repo()
                repo.
            }
        """.trimIndent()
        val result = analyze(src)
        val labels = result.completionsAt(caret(src, "repo.")).map { it.label }
        assertTrue(labels.contains("refresh"))
    }

    @Test
    fun completesInsideStringTemplate() {
        val src = """
            class Report {
                private var counter: Int = 0
                fun render() {
                    val title = "Total: ${'$'}cou"
                }
            }
        """.trimIndent()
        val result = analyze(src)
        val labels = result.completionsAt(caret(src, "${'$'}cou")).map { it.label }
        assertTrue("counter missing in $labels", labels.contains("counter"))
    }

    @Test
    fun doesNotOfferLocalsFromOtherFunctions() {
        val src = """
            fun a() {
                val hidden = 1
            }
            fun b() {

            }
        """.trimIndent()
        val result = analyze(src)
        val labels = result.completionsAt(caret(src, "fun b() {\n\n")).map { it.label }
        assertTrue(labels.contains("a"))
        assertTrue(labels.contains("b"))
        assertFalse("leaked local in $labels", labels.contains("hidden"))
    }

    @Test
    fun recognizesMemberAccessContext() {
        val src = "val tv: TextView = TextView(null)\ntv."
        val result = analyze(src, testIndex)
        assertTrue(result.isMemberAccess(caret(src, "tv.")))
        assertFalse(result.isMemberAccess(caret(src, "tv")))
    }
}

class AndroidApiIndexTest {
    @Test
    fun parsesClassesAndMembers() {
        assertEquals(4, testIndex.classCount)
        assertTrue(testIndex.hasClass("TextView"))
        assertEquals("android.view.View", testIndex.classesNamed("TextView").first().superFqn)
    }

    @Test
    fun walksSuperClassesForMembers() {
        val members = testIndex.membersOf("TextView")
        assertTrue(members.any { it.name == "getWidth" })
        assertTrue(members.any { it.name == "setText" })
    }

    @Test
    fun emptyIndexIsSafe() {
        val src = "val tv: TextView = TextView(null)\ntv."
        val result = analyze(src)
        assertTrue(result.completionsAt(caret(src, "tv.")).isEmpty())
        assertEquals(0, result.errors.count { it.code == DiagnosticCodes.E0101_UNRESOLVED })
    }

    @Test
    fun realIndexIsGeneratedWithUsableContent() {
        val index = realIndex()
        if (index === AndroidApiIndex.EMPTY || index.classCount == 0) return
        assertTrue("expected >500 classes, got ${index.classCount}", index.classCount > 500)
        assertTrue("expected >10000 members, got ${index.memberCount}", index.memberCount > 10000)
        assertTrue(index.hasClass("TextView"))
        assertTrue(index.hasClass("Intent"))
        assertTrue(index.hasClass("Bundle"))
        assertTrue(index.membersOf("TextView").any { it.name == "setText" })
        assertTrue(index.membersOf("Activity").any { it.name == "setContentView" })
    }
}

class JvmTypesTest {
    @Test
    fun mapsKotlinPrimitivesAndCollections() {
        assertEquals("java.lang.String", JvmTypes.toFqName("String"))
        assertEquals("int", JvmTypes.toFqName("Int"))
        assertEquals("java.lang.Object", JvmTypes.toFqName("Any"))
        assertEquals("java.util.ArrayList", JvmTypes.toFqName("MutableList"))
        assertEquals("java.lang.String", JvmTypes.toFqName("String?"))
    }

    @Test
    fun stripsGenericsAndReadsArguments() {
        assertEquals("List", JvmTypes.stripGenerics("List<String>"))
        assertEquals("TextView", JvmTypes.firstTypeArgument("List<TextView>"))
        assertEquals("String", JvmTypes.firstTypeArgument("Map<String, Int>"))
        assertEquals("TextView", JvmTypes.simpleNameOf("android.widget.TextView"))
    }

    @Test
    fun resolvesAgainstIndex() {
        assertEquals("android.widget.TextView", JvmTypes.resolve("TextView", testIndex))
        assertEquals("java.util.ArrayList", JvmTypes.resolve("MutableList<TextView>", testIndex))
        assertEquals("android.widget.TextView", JvmTypes.resolve("android.widget.TextView", testIndex))
    }
}